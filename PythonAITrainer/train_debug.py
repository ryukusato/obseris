import os
import traceback
import torch
import numpy as np
import random
import multiprocessing
import copy

from model import TetrisCNN_v2 # あなたのCNNモデル定義ファイル

# --- GAハイパーパラメータ ---
POPULATION_SIZE = 32
NUM_GENERATIONS = 2
ELITE_SIZE = 5
MUTATION_RATE = 0.05
MUTATION_STRENGTH = 0.1
SHAPE_NAMES = [
    "TShape", "ZShape", "SShape", "LineShape", 
    "SquareShape", "LShape", "MirroredLShape"
]
NUM_SHAPE_TYPES = 7

device = torch.device("cpu")
print(f"Using device: {device}")

# 2. モデルの入力次元の定数 (model.py と一致)
NEXT_QUEUE_FEATURES = 5 * NUM_SHAPE_TYPES    # 5 * 7 = 35
SELF_GARBAGE_FEATURES = 1
OPPONENT_GARBAGE_FEATURES = 1

FEATURE_INPUT_SIZE = (
    NEXT_QUEUE_FEATURES +       # 35 (自分)
    NEXT_QUEUE_FEATURES +       # 35 (相手)
    SELF_GARBAGE_FEATURES +     # 1 (自分)
    OPPONENT_GARBAGE_FEATURES   # 1 (相手)
) # ★合計 72
# --- 状態変換ユーティリティ ---
# train.py (中腹)

# --- 状態変換ユーティリティ ---

def state_to_tensor_batch(board_bytes_list):
    """
    盤面bytesのリスト [N] をバッチTensor [N, 1, 40, 10] に変換
    (※ C++側が int32_t を送ってくる前提)
    """
    tensors = []
    for b in board_bytes_list:
        # 1. bytes を numpy 配列に (int32 として)
        arr = np.frombuffer(b, dtype=np.int32)
        # 2. 1D (400,) -> 2D (40, 10) に変形
        try:
            arr = arr.reshape(40, 10)
        except ValueError as e:
            print(f"[state_to_tensor_batch] ERROR: Reshape失敗。入力bytesサイズ: {len(b)}, {e}")
            # エラー時はダミーの空盤面を返す
            arr = np.zeros((40, 10), dtype=np.int32)
            
        # 3. float に変換して tensor に
        tensors.append(torch.from_numpy(arr.astype(np.float32)))

    # [N, 40, 10] -> [N, 1, 40, 10]
    return torch.stack(tensors, dim=0).unsqueeze(1).to(device)


def decode_queue_bytes_to_one_hot(queue_bytes):
    """
    ネクストキューbytes (5 * int32) を
    One-hotエンコードされた [35] のベクトルに変換する
    """
    # 1. bytes -> numpy配列 (int32) [5,]
    try:
        indices = np.frombuffer(queue_bytes, dtype=np.int32)
    except ValueError as e:
        print(f"[decode_queue_bytes] ERROR: frombuffer失敗。{e}")
        indices = np.full(5, -1, dtype=np.int32) # ダミー(-1)

    if indices.shape != (5,):
        print(f"[decode_queue_bytes] ERROR: 期待した形状(5,)ではありません。Shape: {indices.shape}")
        indices = np.full(5, -1, dtype=np.int32) # ダミー(-1)

    # 2. One-hotエンコード (5, 7)
    one_hot_queue = torch.zeros(5, NUM_SHAPE_TYPES, dtype=torch.float32)
    
    for i in range(5):
        shape_index = indices[i]
        # 有効なインデックス (0〜6) の場合のみ 1.0 を設定
        if 0 <= shape_index < NUM_SHAPE_TYPES:
            one_hot_queue[i, shape_index] = 1.0
            
    # [5, 7] -> [35] に平坦化
    return one_hot_queue.flatten().to(device)

# --- 個体クラス ---
class TetrisIndividual:
    def __init__(self, model_state_dict=None):
        if model_state_dict:
            self.model_state = copy.deepcopy(model_state_dict)
        else:
            self.model_state = TetrisCNN_v2().to(device).state_dict()
        self.fitness = 0.0

    def get_model_state(self):
        return self.model_state
# train.py (calculate_fitness の「前」あたりに追加)

def get_best_move(model, env_self, env_other):
    """
    model を使って、env_self の最善手 (env_other を考慮) を推論して返す
    
    引数:
    - model: TetrisCNN_v2 モデル
    - env_self: AI自身 (GameState)
    - env_other: 相手 (GameState)
    """
    
    # 0. AIの取りうる手を取得
    possible_moves = env_self.get_all_possible_moves()
    
    num_moves = len(possible_moves)
    if num_moves == 0:
        return None # 手がない

    # --- 1. 定数となる情報 (bytes) を先に取得 ---
    
    # 1a. 相手の「現在」の盤面 (bytes)
    opponent_board_bytes = env_other.board_bytes
    
    # 1b. 自分のネクストキュー (bytes) -> One-hot [35]
    self_next_queue_tensor = decode_queue_bytes_to_one_hot(
        env_self.next_queue_bytes
    ) # -> [35]

    # 1c. 相手のネクストキュー (bytes) -> One-hot [35]
    other_next_queue_tensor = decode_queue_bytes_to_one_hot(
        env_other.next_queue_bytes
    ) # -> [35]
    
    # 1d. 相手の「現在」のお邪魔 (スカラー)
    opponent_garbage = torch.tensor(
        [env_other.pending_garbage], dtype=torch.float32
    ).to(device) # -> [1]


    # --- 2. 候補手ごとに異なる情報 (N個) をリスト化 ---
    
    # 2a. 自分の「未来」の盤面 (bytes のリスト)
    self_future_board_list = [move.future_board_bytes for move in possible_moves]
    
    # 2b. 自分の「未来」のお邪魔 (スカラーのリスト)
    self_future_garbage_list = [move.pending_garbage_after for move in possible_moves]


    # --- 3. バッチテンソル (N個分) を構築 ---

    # 3a. board_tensor [N, 2, 40, 10]
    
    # (N, 1, 40, 10)
    self_boards = state_to_tensor_batch(self_future_board_list) 
    
    # 相手の盤面(bytes)をリストにN個詰める (非効率だが安全)
    # ※ state_to_tensor_batch がリスト[N]を期待するため
    opponent_boards_list = [opponent_board_bytes] * num_moves
    opponent_boards = state_to_tensor_batch(opponent_boards_list) # -> (N, 1, 40, 10)
    
    # チャンネル次元(dim=1)で結合
    board_tensor = torch.cat((self_boards, opponent_boards), dim=1) # -> [N, 2, 40, 10]

    
    # 3b. feature_tensor [N, 72]
    
    # (1, 35) -> (N, 35) に複製
    self_queues = self_next_queue_tensor.unsqueeze(0).repeat(num_moves, 1)
    other_queues = other_next_queue_tensor.unsqueeze(0).repeat(num_moves, 1)
    
    # (1, 1) -> (N, 1) に複製
    other_garbages = opponent_garbage.unsqueeze(0).repeat(num_moves, 1)
    
    # (N,) -> (N, 1) に変形
    self_garbages = torch.tensor(
        self_future_garbage_list, dtype=torch.float32
    ).to(device).unsqueeze(1)
    
    # (N, 35), (N, 35), (N, 1), (N, 1) を次元1で結合
    feature_tensor = torch.cat(
        (self_queues, other_queues, self_garbages, other_garbages), dim=1
    ) # -> [N, 72]

    # --- 4. モデルで評価 ---
    with torch.no_grad():
        values = model(board_tensor, feature_tensor) # -> [N, 1]

    # 評価値が最大の「手」を返す
    best_move_index = values.argmax().item()
    return possible_moves[best_move_index]

# train.py
# (古い calculate_fitness を削除し、以下に差し替える)

def calculate_fitness(individual_model_state_tuple):
    """
    1v1 バトルシミュレータ。
    引数は (評価対象のstate_dict, 相手モデルのパス) のタプル。
    """
    
    # --- 0. 引数の展開 ---
    individual_model_state, opponent_model_path = individual_model_state_tuple
    
    # C++モジュールはワーカーごとにインポート
    import tetris_simulator
    GameState = tetris_simulator.GameState
    worker_pid = os.getpid()
    
    # ★ このワーカー専用のログファイルを作成
    log_filename = f"debug_worker_{worker_pid}.log"

    try:
        with open(log_filename, "w") as f:
            f.write(f"[Worker {worker_pid}] 起動。1v1シミュレーション開始。\n")
            f.flush()

            # --- 1. AI (評価対象) のセットアップ ---
            model_ai = TetrisCNN_v2().to(device) # ★ v2 モデル
            model_ai.load_state_dict(individual_model_state)
            model_ai.eval()
            env_ai = GameState()
            env_ai.reset()
            f.write(f"[Worker] AI (評価対象) セットアップ完了。\n")

            # --- 2. 相手AI (固定) のセットアップ ---
            model_opponent = TetrisCNN_v2().to(device) # ★ v2 モデル
            opponent_loaded = False
            if os.path.exists(opponent_model_path):
                try:
                    # CPUでロード
                    model_opponent.load_state_dict(
                        torch.load(opponent_model_path, map_location=device)
                    )
                    opponent_loaded = True
                    f.write(f"[Worker] 相手モデル {opponent_model_path} をロード。\n")
                except Exception as e:
                    f.write(f"!! [Worker] 相手モデルのロード失敗: {e}\n")
            
            if not opponent_loaded:
                # (Gen 0 の場合など)
                f.write(f"!! [Worker] 相手モデルが見つからないため、ダミー（初期重み）を使用。\n")
            
            model_opponent.eval()
            env_opponent = GameState()
            env_opponent.reset()
            f.write(f"[Worker] 相手AI (ダミー/ロード済) セットアップ完了。\n")

            total_steps = 0
            total_ai_attack = 0
            
            # --- 3. 1v1 バトルループ ---
            # (最大 3000 ターン (AI 1500, 相手 1500))
            while total_steps < 3000: 
                
                # --- 4. AI (評価対象) のターン ---
                if not env_ai.is_game_over:
                    # ★ ステップ3で実装したヘルパー関数を呼ぶ
                    chosen_move_ai = get_best_move(model_ai, env_ai, env_opponent) 
                    
                    if chosen_move_ai is None: # 手がない
                        f.write(f"[Step {total_steps} AI] 手詰まり。ゲームオーバー。\n")
                        env_ai.is_game_over = True 
                    else:
                        # 手を実行
                        env_ai.execute_move(chosen_move_ai)
                        total_ai_attack += chosen_move_ai.attack_power
                        
                        # 火力を相手に送る
                        if chosen_move_ai.attack_power > 0:
                            env_opponent.add_pending_garbage(chosen_move_ai.attack_power)

                # --- 5. 相手 (固定) のターン ---
                if not env_opponent.is_game_over:
                    # ★ 同じヘルパー関数を、視点を入れ替えて呼ぶ
                    chosen_move_opp = get_best_move(model_opponent, env_opponent, env_ai) 
                    
                    if chosen_move_opp is None:
                        f.write(f"[Step {total_steps} Opp] 手詰まり。ゲームオーバー。\n")
                        env_opponent.is_game_over = True
                    else:
                        # 手を実行
                        env_opponent.execute_move(chosen_move_opp)
                        
                        # 火力をAIに送る
                        if chosen_move_opp.attack_power > 0:
                            env_ai.add_pending_garbage(chosen_move_opp.attack_power)

                total_steps += 1
                
                # --- 6. 終了判定 ---
                if env_ai.is_game_over or env_opponent.is_game_over:
                    break
            
            # --- 7. 適応度の計算 ---
            fitness = 0.0
            if env_ai.is_game_over and not env_opponent.is_game_over:
                fitness = -100.0 # 負け
                f.write(f"[Worker] 決着: AIの負け (Game Over)\n")
            elif not env_ai.is_game_over and env_opponent.is_game_over:
                fitness = 100.0 # 勝ち
                f.write(f"[Worker] 決着: AIの勝ち (Opponent Game Over)\n")
            else:
                # 引き分け (両方ゲームオーバー or タイムアウト)
                fitness = 0.0 
                f.write(f"[Worker] 決着: 引き分け (Timeout or Double KO)\n")

            # 生存ボーナス + 火力ボーナス
            fitness += (total_steps * 0.1) 
            fitness += (total_ai_attack * 0.5)

            f.write(f"[Worker {worker_pid}] Fitness calculated: {fitness:.2f}\n")
            f.flush()
            
            print(f"[Worker {worker_pid}] 対戦完了。 Fitness: {fitness:.2f} (Steps: {total_steps}, Attack: {total_ai_attack}) ログ: {log_filename}")
            
            del model_ai, env_ai, model_opponent, env_opponent
            return fitness

    except Exception as e:
        # Pythonレベルでの予期せぬエラー
        print(f"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        print(f"!!!! [Worker {worker_pid}] でPythonエラーが発生 !!!!")
        print(traceback.format_exc()) # エラー内容を詳細に表示
        print(f"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        with open(log_filename, "a") as f:
            f.write(f"\n!!!! PYTHON EXCEPTION !!!!\n{traceback.format_exc()}\n")
            f.flush()
        return -999.0 # 失敗したことが分かるように
# --- GA操作関数群 ---
def selection(population):
    sorted_population = sorted(population, key=lambda ind: ind.fitness, reverse=True)
    elites = sorted_population[:ELITE_SIZE]

    parents_pool = []
    tournament_size = 3
    required = POPULATION_SIZE - ELITE_SIZE

    for _ in range(required * 2):
        tournament = random.sample(population, tournament_size)
        winner = max(tournament, key=lambda ind: ind.fitness)
        parents_pool.append(winner)

    return elites, parents_pool


def crossover(parent1, parent2):
    p1 = parent1.get_model_state()
    p2 = parent2.get_model_state()
    child = copy.deepcopy(p1)

    for k in child:
        if random.random() < 0.5:
            child[k] = (p1[k] + p2[k]) / 2.0

    return TetrisIndividual(model_state_dict=child)


def mutation(individual):
    state = individual.get_model_state()
    for k in state:
        if state[k].dtype.is_floating_point:
            if random.random() < MUTATION_RATE:
                noise = torch.randn_like(state[k]) * MUTATION_STRENGTH
                state[k] += noise
    return TetrisIndividual(model_state_dict=state)


# --- メインループ ---
# train.py
# (古い main 関数を削除し、以下に差し替える)

# --- 6. メイン学習ループ (1v1 バトルモード) ---
def main():
    
    print(f"--- GA学習開始 (1v1 バトルモード / 逐次実行) ---")

    # ★ 相手モデルのパスを定義
    opponent_model_path = "opponent_model_v1.pth"

    # 1. 初期集団 (★ TetrisIndividual() が v2 モデルを生成する)
    population = [TetrisIndividual() for _ in range(POPULATION_SIZE)]

    for gen in range(NUM_GENERATIONS):
        
        print(f"\n--- 世代 {gen}/{NUM_GENERATIONS} : 評価準備... ---")
        print(f"    (対戦相手モデル: {opponent_model_path})")
        
        # 2a. この世代で評価が必要なタスクリストを作成
        # ★ (state, opponent_path) のタプルを渡すように変更
        tasks = [
            (ind.get_model_state(), opponent_model_path) for ind in population
        ]
        
        # 2b. 世代の結果を格納する場所
        final_results = []
        
        # 2c. 逐次実行ループ (デバッグモード)
        for i in range(len(tasks)):
            task = tasks[i]
            
            print(f"  [Gen {gen+1}] >> 個体 {i+1}/{len(tasks)} の評価を開始...")
            
            try:
                # 2d. タプルを渡して 1v1 バトルを実行
                fitness = calculate_fitness(task) 
                final_results.append(fitness)
                
                print(f"  [Gen {gen+1}] << 個体 {i+1} 完了。 Fitness: {fitness:.2f}")

            except Exception as e:
                print(f"\n!!!!!!!!!!!!!! 致命的なエラー !!!!!!!!!!!!!!")
                print(f"!! [Gen {gen+1}] 個体 {i+1} の評価中にエラー: {e}")
                print(traceback.format_exc())
                print(f"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
                final_results.append(-999.0) # 失敗

        # --- 3. 統計情報の表示 ---
        for ind, fitness_score in zip(population, final_results):
            ind.fitness = fitness_score
            
        best_individual = max(population, key=lambda ind: ind.fitness)
        avg_fitness = np.mean([ind.fitness for ind in population])
        
        print(f"世代 {gen+1} 完了: ベスト適応度: {best_individual.fitness:.2f}, 平均適応度: {avg_fitness:.2f}")

        # ★★★ 4. 新しい相手モデルの保存 ★★★
        # この世代のエリートを「次の世代の」相手として保存
        torch.save(best_individual.get_model_state(), opponent_model_path)
        print(f"世代 {gen+1} のエリートを '{opponent_model_path}' として保存しました。")

        # --- 5. 選択 (変更なし) ---
        elites, parents_pool = selection(population)
        
        # --- 6. 交叉と突然変異 (変更なし) ---
        next_population = elites 
        children_needed = POPULATION_SIZE - ELITE_SIZE
        
        for i in range(children_needed):
            parent1, parent2 = random.sample(parents_pool, 2)
            child = crossover(parent1, parent2)
            mutant_child = mutation(child)
            next_population.append(mutant_child)
            
        population = next_population
        
    # --- 学習完了 ---
    print("\n--- GA学習完了 ---")
    
    final_best = max(population, key=lambda ind: ind.fitness)
    final_model_path = "tetris_model_v2_final.pth" # v2 に変更
    torch.save(final_best.get_model_state(), final_model_path)
    print(f"最も優秀なモデルを '{final_model_path}' として保存しました (適応度: {final_best.fitness:.2f})")


if __name__ == "__main__":
    multiprocessing.set_start_method('spawn', force=True)
    torch.set_num_threads(1) 
    
    main() # 1v1 バトルモードの main() を実行