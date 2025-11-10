# train_dql.py
# (GA版 train.py を DQL (Sarsaアプローチ) に書き換えたもの)

import glob
import os
import traceback
import numpy as np
import random
import multiprocessing
import copy
import collections
import math
from tqdm import tqdm # 進捗バー
import time

import tetris_simulator # C++モジュール
from model import TetrisCNN_v2 # あなたのモデル (model.pyから)
import torch
import torch.nn as nn
import torch.optim as optim

# --- C++モジュールの確認 ---
try:
    GameState = tetris_simulator.GameState
    LandingSpot = tetris_simulator.LandingSpot
    SpinType = tetris_simulator.SpinType
    print("C++モジュール 'tetris_simulator' のロード成功。")
except Exception as e:
    print(f"C++モジュール 'tetris_simulator' のインポートに失敗しました: {e}")
    exit()

# --- DQL (Sarsa) ハイパーパラメータ ---
GAMMA = 0.99                # 割引率
EPSILON_START = 0.9         # ε-greedy の開始値
EPSILON_END = 0.05          # ε-greedy の最終値
EPSILON_DECAY = 100000      # ε の減衰速度 (ステップ数)
TARGET_UPDATE = 20000         # ターゲットネットワークを更新する頻度 (ステップ数)
MEMORY_CAPACITY = 200000     # リプレイバッファの容量
BATCH_SIZE = 128            # 学習時のバッチサイズ
NUM_EPISODES = 100000        # 学習エピソード数
LEARNING_RATE = 0.0001      # 学習率
STEPS_BEFORE_LEARN = 5000   # 学習を開始する前に溜める経験ステップ数

# --- GA版からの流用設定 ---
device = torch.device("cuda" if torch.cuda.is_available() else "cpu") 
print(f"Using device: {device}")

# --- One-hotエンコーディング用 ---
SHAPE_NAMES = [
    "TShape", "ZShape", "SShape", "LineShape", 
    "SquareShape", "LShape", "MirroredLShape"
]
NUM_SHAPE_TYPES = 7 

# --- 状態変換ユーティリティ (GA版から流用) ---

def state_to_tensor_batch(board_bytes_list):
    """
    盤面bytesのリスト [N] をバッチTensor [N, 1, 40, 10] に変換
    """
    tensors = []
    for b in board_bytes_list:
        arr = np.frombuffer(b, dtype=np.int32)
        try:
            arr = arr.reshape(40, 10)
        except ValueError as e:
            # print(f"[state_to_tensor_batch] ERROR: Reshape失敗。{len(b)}, {e}")
            arr = np.zeros((40, 10), dtype=np.int32)
        tensors.append(torch.from_numpy(arr.astype(np.float32)))

    return torch.stack(tensors, dim=0).unsqueeze(1).to(device)

def decode_queue_bytes_to_one_hot(queue_bytes):
    """
    ネクストキューbytes (5 * int32) を One-hot [35] ベクトルに変換
    """
    try:
        indices = np.frombuffer(queue_bytes, dtype=np.int32)
    except ValueError as e:
        indices = np.full(5, -1, dtype=np.int32)

    if indices.shape != (5,):
        indices = np.full(5, -1, dtype=np.int32)

    one_hot_queue = torch.zeros(5, NUM_SHAPE_TYPES, dtype=torch.float32)
    for i in range(5):
        shape_index = indices[i]
        if 0 <= shape_index < NUM_SHAPE_TYPES:
            one_hot_queue[i, shape_index] = 1.0
            
    return one_hot_queue.flatten().to(device)


# =================================================================
# --- DQL (Sarsa) 用の新しいコンポーネント ---
# =================================================================

# 1. 経験 (Experience) の定義
# V(s') を学習するため、バッファには「行動後の状態 s'」を保存します。
# (s_prime) -> (reward) -> (s_next_prime, done)
Experience = collections.namedtuple(
    'Experience', 
    ('s_prime_board', 's_prime_features', 'reward', 
     's_next_prime_board', 's_next_prime_features', 'done')
)

# 2. リプレイバッファ (ReplayBuffer)
class ReplayBuffer:
    def __init__(self, capacity):
        self.memory = collections.deque([], maxlen=capacity)

    def push(self, s_prime, reward, s_next_prime, done):
        """経験を保存する (s_prime = (board, features))"""
        s_p_board, s_p_features = s_prime
        
        # s_next_prime が None (ゲーム終了時) の場合
        if s_next_prime is None:
            s_np_board = s_p_board # ダミーデータ (使われない)
            s_np_features = s_p_features # ダミーデータ (使われない)
        else:
            s_np_board, s_np_features = s_next_prime
            
        self.memory.append(Experience(
            s_p_board, # CPUに保存してGPUメモリを節約
            s_p_features.cpu(), # CPUに保存
            reward, 
            s_np_board, 
            s_np_features.cpu(), 
            done
        ))

    def sample(self, batch_size):
        """バッチサイズ分ランダムにサンプリングする"""
        transitions = random.sample(self.memory, batch_size)
        return transitions

    def __len__(self):
        return len(self.memory)

# 3. 報酬関数
def calculate_step_reward(move: LandingSpot, garbage_received: int) -> float:
    """その一手で得られた即時の報酬を計算する"""
    if move.is_game_over:
        return -10.0 # ゲームオーバーは最大のペナルティ

    reward = 0.0
    
    # 1. 火力 (最も重要)
    reward += move.attack_power * 1.0
    reward += move.lines_cleared * 0.3
    if move.combo_count_after > 0:
        reward += move.combo_count_after * 0.2
    reward -= garbage_received * 2.0
    # 6. 生存ボーナス (1手生き延びた)
    reward += 0.01 
    
    return reward

# 4. モデル入力 (s') の生成
def get_s_prime(move: LandingSpot, env_other: GameState):
    """
    LandingSpot (行動) から、モデル入力 (s') に必要な情報をタプルで返す
    (board_bytes [bytes], features_tensor [Tensor])
    """
    ai_board_bytes = move.future_board_bytes
    opp_board_bytes = env_other.board_bytes
    
    # 特徴量テンソルの作成
    self_queue_tensor = decode_queue_bytes_to_one_hot(move.future_next_queue_bytes)
    other_queue_tensor = decode_queue_bytes_to_one_hot(env_other.get_next_queue_bytes(0))
    self_garbage = torch.tensor([move.pending_garbage_after], dtype=torch.float32).to(device)
    other_garbage = torch.tensor([env_other.pending_garbage], dtype=torch.float32).to(device)
    
    features_tensor = torch.cat(
        (self_queue_tensor, other_queue_tensor, self_garbage, other_garbage), dim=0
    )
    
    # (bytes, tensor) のタプルとして返す
    return ((ai_board_bytes, opp_board_bytes), features_tensor)

# 5. 行動選択 (ε-greedy)
def select_action(model, env_self, env_other, epsilon):
    """
    ε-greedy 戦略で LandingSpot (行動) を選択する
    
    Returns:
        LandingSpot: 選ばれた手 (取れる手がない場合は None)
        tuple | None: 選ばれた手の s_prime (board_bytes, features_tensor)
    """
    
    possible_moves = env_self.get_all_possible_moves()
    if not possible_moves:
        return None, None # 取れる手がない

    # 1. 探索 (Exploration)
    if random.random() < epsilon:
        chosen_move = random.choice(possible_moves)
        s_prime = get_s_prime(chosen_move, env_other)
        return chosen_move, s_prime
    
    # 2. 活用 (Exploitation) - GAの get_best_move と同じロジック
    else:
        num_moves = len(possible_moves)

        # --- 2a. 相手の固定情報を取得 ---
        opponent_board_bytes = env_other.board_bytes
        other_next_queue_tensor = decode_queue_bytes_to_one_hot(env_other.get_next_queue_bytes(0))
        opponent_garbage = torch.tensor([env_other.pending_garbage], dtype=torch.float32).to(device)
        opponent_boards_list = [opponent_board_bytes] * num_moves
        opponent_boards = state_to_tensor_batch(opponent_boards_list)

        # --- 2b. 自分の候補手情報 (N個) をリスト化 ---
        self_queues_bytes_list = [move.future_next_queue_bytes for move in possible_moves]
        self_queues_list = [decode_queue_bytes_to_one_hot(b) for b in self_queues_bytes_list]
        self_future_board_list = [move.future_board_bytes for move in possible_moves]
        self_future_garbage_list = [move.pending_garbage_after for move in possible_moves]

        # --- 2c. バッチテンソル (N個分) の構築 ---
        
        # board_tensor [N, 2, 40, 10]
        self_boards = state_to_tensor_batch(self_future_board_list) 
        board_tensor = torch.cat((self_boards, opponent_boards), dim=1)

        # feature_tensor [N, 72]
        self_queues = torch.stack(self_queues_list, dim=0).to(device)
        other_queues = other_next_queue_tensor.unsqueeze(0).repeat(num_moves, 1)
        other_garbages = opponent_garbage.unsqueeze(0).repeat(num_moves, 1)
        self_garbages = torch.tensor(self_future_garbage_list, dtype=torch.float32).to(device).unsqueeze(1)
        
        feature_tensor = torch.cat(
            (self_queues, other_queues, self_garbages, other_garbages), dim=1
        )

        # --- 2d. モデルで評価 ---
        with torch.no_grad():
            values = model(board_tensor, feature_tensor) # -> [N, 1]

        best_move_index = values.argmax().item()
        
        chosen_move = possible_moves[best_move_index]
        
        # 評価に使ったテンソル (s_prime) を再利用
        s_prime = get_s_prime(chosen_move, env_other)
        
        return chosen_move, s_prime


# 6. 学習ステップ
def optimize_model(policy_net, target_net, memory, optimizer, criterion):
    
    if len(memory) < BATCH_SIZE:
        return # バッファが溜まっていない

    transitions = memory.sample(BATCH_SIZE)
    batch = Experience(*zip(*transitions))

    # (a) V(s') のためのバッチを作成
    s_prime_ai_boards_list = [boards_tuple[0] for boards_tuple in batch.s_prime_board]
    s_prime_opp_boards_list = [boards_tuple[1] for boards_tuple in batch.s_prime_board]
    s_prime_ai_tensors = state_to_tensor_batch(s_prime_ai_boards_list)
    s_prime_opp_tensors = state_to_tensor_batch(s_prime_opp_boards_list)
    s_prime_boards = torch.cat((s_prime_ai_tensors, s_prime_opp_tensors), dim=1) # [N, 2, 40, 10]
    s_prime_features = torch.stack(batch.s_prime_features, dim=0).to(device)

    # (b) V(s_next_prime) のためのバッチを作成
    s_next_prime_ai_boards_list = [boards_tuple[0] for boards_tuple in batch.s_next_prime_board]
    s_next_prime_opp_boards_list = [boards_tuple[1] for boards_tuple in batch.s_next_prime_board]
    s_next_prime_ai_tensors = state_to_tensor_batch(s_next_prime_ai_boards_list)
    s_next_prime_opp_tensors = state_to_tensor_batch(s_next_prime_opp_boards_list)
    s_next_prime_boards = torch.cat((s_next_prime_ai_tensors, s_next_prime_opp_tensors), dim=1) # [N, 2, 40, 10]
    s_next_prime_features = torch.stack(batch.s_next_prime_features, dim=0).to(device)
    
    rewards = torch.tensor(batch.reward, dtype=torch.float32).to(device)
    dones = torch.tensor(batch.done, dtype=torch.bool).to(device)

    # (c) V(s') を計算 (モデルの現在の予測)
    # [N, 1] -> [N]
    current_V_values = policy_net(s_prime_boards, s_prime_features).squeeze()

    # (d) V_target = r + gamma * V_target_net(s_next_prime)
    next_V_values = torch.zeros(BATCH_SIZE, device=device)
    
    # ゲームが終了していない状態の V(s_next_prime) のみ計算
    non_final_mask = ~dones
    if non_final_mask.any():
        with torch.no_grad(): # ターゲットネットは勾配計算しない
            next_V_values[non_final_mask] = target_net(
                s_next_prime_boards[non_final_mask], 
                s_next_prime_features[non_final_mask]
            ).squeeze()

    # r + γ * V(s')
    V_target = rewards + (GAMMA * next_V_values)

    # (e) 損失計算と更新
    loss = criterion(current_V_values, V_target)
    optimizer.zero_grad()
    loss.backward()
    # (オプション: 勾配クリッピング)
    # torch.nn.utils.clip_grad_value_(policy_net.parameters(), 100)
    optimizer.step()


# =================================================================
# --- メイン学習ループ (DQL/Sarsa版) ---
# =================================================================
def main_dql():
    
    print("--- DQL (Sarsa) 学習開始 ---")
    model_save_dir = "dql_models"
    os.makedirs(model_save_dir, exist_ok=True)
    print(f"モデルは '{model_save_dir}' フォルダに保存されます。")
    
    # 1. モデルと環境の初期化
    policy_net = TetrisCNN_v2().to(device)
    target_net = TetrisCNN_v2().to(device)
    
    # (オプション: GAや前回学習済みの重みをロード)
    RESUME_MODEL_PATH = "tetris_model_dql_final.pth" # GA版の最強モデル
    if os.path.exists(RESUME_MODEL_PATH):
        print(f"'{RESUME_MODEL_PATH}' から重みをロードします。")
        try:
            policy_net.load_state_dict(torch.load(RESUME_MODEL_PATH, map_location=device))
        except Exception as e:
            print(f"重みロード失敗。新規モデルで開始します。エラー: {e}")
    else:
        print(f"'{RESUME_MODEL_PATH}' がないため、新規モデルで開始します。")


    target_net.load_state_dict(policy_net.state_dict())
    target_net.eval() # ターゲットネットは評価モード
    
    optimizer = optim.Adam(policy_net.parameters(), lr=LEARNING_RATE)
    memory = ReplayBuffer(MEMORY_CAPACITY)
    criterion = nn.SmoothL1Loss() # MSELoss() より安定することが多い

    # (対戦相手モデル: ここでは target_net (少し前の自分) を使う)
    opponent_model = TetrisCNN_v2().to(device)
    opponent_model.load_state_dict(target_net.state_dict())
    opponent_model.eval()

    env_ai = GameState()
    env_opponent = GameState()

    total_steps = 0
    
    print("学習ループ開始...")
    for i_episode in range(NUM_EPISODES):
        
        env_ai.reset()
        env_opponent.reset() 
        
        # (エピソード開始時に対戦相手を更新)
        opponent_model.load_state_dict(target_net.state_dict())
        
        epsilon = EPSILON_END + (EPSILON_START - EPSILON_END) * \
                  math.exp(-1. * total_steps / EPSILON_DECAY)

        # --- Sarsaのための初期行動選択 ---
        # (action = LandingSpot, s_prime = (board_bytes, features_tensor))
        action, s_prime = select_action(policy_net, env_ai, env_opponent, epsilon)
        
        if action is None: # 初期手がない (ほぼあり得ない)
            print(f"Episode {i_episode}: 初期手なし。スキップします。")
            continue 

        episode_reward = 0
        episode_steps = 0

        while True:
            
            # --- 相手のターン (先行) ---
            garbage_from_opponent = 0
            opponent_lost_this_turn = False
            if not env_opponent.is_game_over:
                # 相手は常に最善手 (ε=0)
                opp_move, _ = select_action(opponent_model, env_opponent, env_ai, 0.0) 
                
                if opp_move is None:
                    env_opponent.is_game_over = True
                    opponent_lost_this_turn = True
                else:
                    env_opponent.execute_move(opp_move)
                    if opp_move.attack_power > 0:
                        # ★ AIがお邪魔を受け取る
                        env_ai.add_pending_garbage(opp_move.attack_power)
                        garbage_from_opponent = opp_move.attack_power
                        if env_opponent.is_game_over: 
                            opponent_lost_this_turn = True

            # --- AIのターン (行動実行) ---
            
            # 1. 行動 a (前のステップで決定済み) を実行
            env_ai.execute_move(action)
            ai_lost_this_turn = env_ai.is_game_over

            if action.attack_power > 0:
                env_opponent.add_pending_garbage(action.attack_power)
            
            reward_val = calculate_step_reward(action, garbage_from_opponent)

            
            if ai_lost_this_turn:
                reward_val -= 20.0 # 敗北
            elif opponent_lost_this_turn:
                reward_val += 20.0 # 勝利


            reward = torch.tensor([reward_val], dtype=torch.float32)
            episode_reward += reward_val

            # 3. 次の状態 s_next で 次の行動 a_next を選択
            s_next_prime = None
            action_next = None
            
            is_episode_over = ai_lost_this_turn or opponent_lost_this_turn

            if not is_episode_over:
                action_next, s_next_prime = select_action(
                    policy_net, env_ai, env_opponent, epsilon
                )
                if action_next is None or action_next.is_game_over:
                    is_episode_over = True # この状態がエピソードの最後
                    if action_next is not None:
                        s_next_prime = get_s_prime(action_next, env_opponent)

            
            # 4. リプレイバッファに保存
            # (s_prime, r, s_next_prime, done)
            memory.push(s_prime, reward, s_next_prime, is_episode_over)
            
            # 5. 次のループのための状態更新
            action = action_next
            s_prime = s_next_prime
            
            total_steps += 1
            episode_steps += 1

            # 6. モデルの学習 (一定ステップ溜まったら開始)
            if total_steps > STEPS_BEFORE_LEARN:
                optimize_model(policy_net, target_net, memory, optimizer, criterion)
            
            # 7. ターゲットネットワークの更新
            if total_steps % TARGET_UPDATE == 0:
                print(f"--- Step {total_steps}: ターゲットネットワーク更新 ---")
                target_net.load_state_dict(policy_net.state_dict())

            if is_episode_over:
                break # エピソード終了
                
        # --- エピソード終了時のログ ---
        if (i_episode + 1) % 10 == 0:
            print(f"Episode {i_episode+1}/{NUM_EPISODES} 完了. Steps: {episode_steps}, Total Steps: {total_steps}, Epsilon: {epsilon:.4f}, Reward: {episode_reward:.2f}")

        # --- モデルの保存 ---
        if (i_episode + 1) % 1000 == 0:
            filename = f"tetris_model_dql_ep_{i_episode+1}.pth"
            save_path = os.path.join(model_save_dir, filename)
            torch.save(policy_net.state_dict(), save_path)
            print(f"モデルを '{save_path}' に保存しました。")
        
    print("--- DQL学習完了 ---")
    final_path = "tetris_model_dql_final.pth"
    torch.save(policy_net.state_dict(), final_path)
    print(f"最終モデルを '{final_path}' に保存しました。")


if __name__ == "__main__":
    # DQLはマルチプロセスを標準では使わないため、spawn設定は不要
    # multiprocessing.set_start_method('spawn', force=True)
    
    main_dql()