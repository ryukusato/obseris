import glob
import os
import traceback
import numpy as np
import random
import multiprocessing
import copy
from tqdm import tqdm # 進捗バー
import tetris_simulator # C++モジュール
from model import TetrisCNN_v2 # あなたのモデル
import torch

# --- C++モジュールの確認 ---
try:
    GameState = tetris_simulator.GameState
    LandingSpot = tetris_simulator.LandingSpot
    SpinType = tetris_simulator.SpinType
except Exception as e:
    print(f"C++モジュール 'tetris_simulator' のインポートに失敗しました: {e}")
    exit()

# --- GAハイパーパラメータ ---
POPULATION_SIZE = 100       # 集団の個体数
NUM_GENERATIONS = 2000       # 学習を行う総世代数
ELITE_SIZE = 5             # 次世代にそのまま残すエリート個体の数
MUTATION_RATE = 0.05       # 突然変異で重みを変更する確率
MUTATION_STRENGTH = 0.1    # 突然変異の強さ
OPPONENT_POOL_SIZE = 20
OLD_GEN = 3000                # 追加学習時の開始世代数
device = torch.device("cpu") 
print(f"Using device: {device}")

# --- One-hotエンコーディング用 ---
SHAPE_NAMES = [
    "TShape", "ZShape", "SShape", "LineShape", 
    "SquareShape", "LShape", "MirroredLShape"
]
NUM_SHAPE_TYPES = 7 
SHAPE_TO_INDEX = {name: i for i, name in enumerate(SHAPE_NAMES)}
INDEX_TO_SHAPE = {i: name for i, name in enumerate(SHAPE_NAMES)}

NEXT_QUEUE_FEATURES = 5 * NUM_SHAPE_TYPES
SELF_GARBAGE_FEATURES = 1
OPPONENT_GARBAGE_FEATURES = 1
FEATURE_INPUT_SIZE = (
    NEXT_QUEUE_FEATURES +
    NEXT_QUEUE_FEATURES +
    SELF_GARBAGE_FEATURES +
    OPPONENT_GARBAGE_FEATURES
)

# --- 状態変換ユーティリティ ---

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
            print(f"[state_to_tensor_batch] ERROR: Reshape失敗。入力bytesサイズ: {len(b)}, {e}")
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
        print(f"[decode_queue_bytes] ERROR: frombuffer失敗。{e}")
        indices = np.full(5, -1, dtype=np.int32)

    if indices.shape != (5,):
        print(f"[decode_queue_bytes] ERROR: 期待形状(5,)ではありません。Shape: {indices.shape}")
        indices = np.full(5, -1, dtype=np.int32)

    one_hot_queue = torch.zeros(5, NUM_SHAPE_TYPES, dtype=torch.float32)
    for i in range(5):
        shape_index = indices[i]
        if 0 <= shape_index < NUM_SHAPE_TYPES:
            one_hot_queue[i, shape_index] = 1.0
            
    return one_hot_queue.flatten().to(device)

# --- 個体クラス (Individual) ---
class TetrisIndividual:
    """CNNモデルの重みと適応度を持つ「個体」クラス"""
    def __init__(self, model_state_dict=None):
        if model_state_dict:
            self.model_state = copy.deepcopy(model_state_dict)
        else:
            self.model_state = TetrisCNN_v2().to(device).state_dict()
        self.fitness = 0.0

    def get_model_state(self):
        return self.model_state

# --- AI推論 ---

def get_best_move(model, env_self, env_other):
    """
    model を使って、env_self の最善手 (env_other を考慮) を推論して返す
    """
    possible_moves = env_self.get_all_possible_moves()
    
    num_moves = len(possible_moves)
    if num_moves == 0:
        return None

    # --- 1. 定数情報 (bytes) の取得 ---
    opponent_board_bytes = env_other.board_bytes
    other_next_queue_tensor = decode_queue_bytes_to_one_hot(env_other.get_next_queue_bytes(0))
    opponent_garbage = torch.tensor([env_other.pending_garbage], dtype=torch.float32).to(device)

    # --- 2. 候補手ごとの情報 (N個) をリスト化 ---
    self_queues_bytes_list = [move.future_next_queue_bytes for move in possible_moves]
    self_queues_list = [decode_queue_bytes_to_one_hot(b) for b in self_queues_bytes_list]
    self_future_board_list = [move.future_board_bytes for move in possible_moves]
    self_future_garbage_list = [move.pending_garbage_after for move in possible_moves]

    # --- 3. バッチテンソル (N個分) の構築 ---
    
    # 3a. board_tensor [N, 2, 40, 10]
    self_boards = state_to_tensor_batch(self_future_board_list) 
    opponent_boards_list = [opponent_board_bytes] * num_moves
    opponent_boards = state_to_tensor_batch(opponent_boards_list)
    board_tensor = torch.cat((self_boards, opponent_boards), dim=1)

    # 3b. feature_tensor [N, 72]
    self_queues = torch.stack(self_queues_list, dim=0).to(device)
    other_queues = other_next_queue_tensor.unsqueeze(0).repeat(num_moves, 1)
    other_garbages = opponent_garbage.unsqueeze(0).repeat(num_moves, 1)
    self_garbages = torch.tensor(self_future_garbage_list, dtype=torch.float32).to(device).unsqueeze(1)
    
    feature_tensor = torch.cat(
        (self_queues, other_queues, self_garbages, other_garbages), dim=1
    )

    # --- 4. モデルで評価 ---
    with torch.no_grad():
        values = model(board_tensor, feature_tensor) # -> [N, 1]

    best_move_index = values.argmax().item()
    return possible_moves[best_move_index]

# --- 適応度関数 (マルチプロセスで実行) ---

def calculate_fitness(individual_model_state_tuple):
    """
    1v1 バトルシミュレータ (ログ出力なし)
    """
    worker_pid = os.getpid()
    individual_model_state, opponent_model_path = individual_model_state_tuple
    
    try:
        # --- 1. モデルとゲーム環境を作成 ---
        model_ai = TetrisCNN_v2().to(device)
        model_ai.load_state_dict(individual_model_state)
        model_ai.eval()
        
        model_opponent = TetrisCNN_v2().to(device)
        if opponent_model_path and os.path.exists(opponent_model_path):
            model_opponent.load_state_dict(
                torch.load(opponent_model_path, map_location=device)
            )
        model_opponent.eval()
        
        env_opponent = GameState()
        env_opponent.reset()
        env_ai = GameState()
        env_ai.reset()

        # --- 2. 統計情報 ---
        total_ai_attack = 0
        total_steps = 0
        total_ai_pieces_placed = 0
        total_ai_soft_drops = 0
        # --- 3. 1v1 バトルループ ---
        while total_steps < 3000: 
            
            # --- 4. AI (評価対象) のターン ---
            if not env_ai.is_game_over:
                chosen_move_ai = get_best_move(model_ai, env_ai, env_opponent)
                if chosen_move_ai.used_soft_drop:
                    total_ai_soft_drops += 1

                env_ai.execute_move(chosen_move_ai)
                total_ai_attack += chosen_move_ai.attack_power
                total_ai_pieces_placed += 1
                    # ★ 修正: お邪魔送信は execute_move の後
                if chosen_move_ai.attack_power > 0:
                    env_opponent.add_pending_garbage(chosen_move_ai.attack_power)

            # --- 5. 相手 (固定) のターン ---
            if not env_opponent.is_game_over:
                chosen_move_opp = get_best_move(model_opponent, env_opponent, env_ai) 
                
                if chosen_move_opp is None:
                    env_opponent.is_game_over = True
                else:
                    env_opponent.execute_move(chosen_move_opp)
                    
                    # ★ 修正: お邪魔送信は execute_move の後
                    if chosen_move_opp.attack_power > 0:
                        env_ai.add_pending_garbage(chosen_move_opp.attack_power)

            total_steps += 1
                
            # --- 6. 終了判定 ---
            if env_ai.is_game_over or env_opponent.is_game_over:
                break
        
        # --- 7. 適応度の計算 (★ 修正: ループの外に移動) ---
        fitness = 0.0
        if env_ai.is_game_over and not env_opponent.is_game_over:
            fitness = -100.0 # 負け
        elif not env_ai.is_game_over and env_opponent.is_game_over:
            fitness = 100.0 # 勝ち
        else:
            fitness = 0.0 # 引き分け
        fitness -= (total_ai_soft_drops * 0.01)
        fitness += (total_steps * 0.01) 
        fitness += (total_ai_attack * 0.2)
        if total_ai_pieces_placed > 0:    
            attack_efficiency = total_ai_attack / total_ai_pieces_placed
            fitness += (attack_efficiency * 2.0)
        
        # 標準出力へのログは残す
        print(f"[Worker {worker_pid}] 対戦完了。 Fitness: {fitness:.2f} (Pieces: {total_ai_pieces_placed}, Softdrop:{total_ai_soft_drops}, Attack: {total_ai_attack}, APP: {attack_efficiency:.2f})")
        
        del model_ai, env_ai, model_opponent, env_opponent
        return fitness

    except Exception as e:
        print(f"\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
        print(f"!!!! [Worker {worker_pid}] でPythonエラーが発生 !!!!")
        print(traceback.format_exc())
        print(f"!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n")
        return -999.0


# --- GAの操作 (選択・交叉・突然変異) ---

def selection(population):
    """
    エリート選択 + トーナメント選択
    """
    sorted_population = sorted(population, key=lambda ind: ind.fitness, reverse=True)
    
    next_generation = sorted_population[:ELITE_SIZE]
    
    parents_pool = []
    tournament_size = 5
    required = POPULATION_SIZE - ELITE_SIZE
    
    for _ in range(required * 2):
        tournament = random.sample(population, tournament_size)
        winner = max(tournament, key=lambda ind: ind.fitness)
        parents_pool.append(winner)
        
    return next_generation, parents_pool

def crossover(parent1, parent2):
    """
    2つの親モデルの重みを平均化 (一様交叉)
    """
    p1_state = parent1.get_model_state()
    p2_state = parent2.get_model_state()
    child_state = copy.deepcopy(p1_state)
    
    for key in child_state:
        # Float型かつ50%の確率で交叉
        if child_state[key].dtype.is_floating_point and random.random() < 0.5:
            child_state[key] = (p1_state[key] + p2_state[key]) / 2.0
            
    return TetrisIndividual(model_state_dict=child_state)

def mutation(individual):
    """
    個体（モデルの重み）にランダムなノイズ（突然変異）を加える
    """
    mutant_state = individual.get_model_state()
    
    for key in mutant_state:
        # Float型のみ突然変異
        if mutant_state[key].dtype.is_floating_point:
            if random.random() < MUTATION_RATE:
                noise = torch.randn_like(mutant_state[key]) * MUTATION_STRENGTH
                mutant_state[key] += noise.to(device)
            
    return TetrisIndividual(model_state_dict=mutant_state)


# --- メイン学習ループ (★ マルチプロセス版) ---
def main():
    print(f"--- GA学習開始 (1v1 / 対戦相手プール) ---")
    opponent_pool_dir = "opponent_pool"
    milestone_dir = "milestone_models"
    old_gen = OLD_GEN
    os.makedirs(opponent_pool_dir, exist_ok=True)
    os.makedirs(milestone_dir, exist_ok=True)
    opponent_pool_paths = sorted(
    glob.glob(os.path.join(opponent_pool_dir, "opponent_gen_*.pth")),
    key=lambda x: int(os.path.basename(x).split('_')[-1].split('.')[0])
    )
    print(f"既存の対戦相手プールをロード: {len(opponent_pool_paths)} 体")


    RESUME_MODEL_PATH = "tetris_model_v4_ga1done_3k.pth"
    opponent_model_path = "opponent_model_v1.pth"
    population = []
    if os.path.exists(RESUME_MODEL_PATH):
        # --- (A) 追加学習 (Resume) モード ---
        print(f"--- '{RESUME_MODEL_PATH}' から追加学習を開始 ---")
        
        # 1a. ベースとなる重みをロード
        base_state_dict = torch.load(RESUME_MODEL_PATH, map_location=device)
        
        # 1b. 親モデル（エリート0）をそのまま集団に追加
        base_individual = TetrisIndividual(model_state_dict=base_state_dict)
        population.append(base_individual)
        
        # 1c. 残りの集団は、親モデルを「突然変異」させて生成
        print(f"'{RESUME_MODEL_PATH}' の変異個体を {POPULATION_SIZE - 1} 体生成します...")
        for _ in range(POPULATION_SIZE - 1):
            # mutation() 関数を使って、親モデルに軽いノイズを加えた個体を作る
            mutant_child = mutation(base_individual) 
            population.append(mutant_child)
            
    else:
        # --- (B) 新規学習 (New) モード ---
        print(f"--- '{RESUME_MODEL_PATH}' が見つかりません。新規学習を開始します ---")
        
        # 従来通り、ランダムな個体で集団を初期化
        population = [TetrisIndividual() for _ in range(POPULATION_SIZE)]

    # ★ 評価に使うワーカー数 (CPUコア数と集団サイズの小さい方)
    num_workers = min(POPULATION_SIZE, os.cpu_count())
    print(f"集団サイズ: {POPULATION_SIZE}, 使用ワーカー数: {num_workers}")

    for gen in range(NUM_GENERATIONS):
        
        print(f"\n--- 世代 {gen+old_gen}/{NUM_GENERATIONS+old_gen} : 評価準備... ---")
        print(f"    (現在の対戦相手プール: {len(opponent_pool_paths)} 体)")
        
        tasks = []
        for ind in population:
            opponent_model_path = None



            if opponent_pool_paths: # プールが空でない場合
                # プールからランダムに対戦相手を選ぶ
                opponent_model_path = random.choice(opponent_pool_paths)
                tasks.append((ind.get_model_state(), opponent_model_path))
        
        final_results = []
        
        # ★★★ マルチプロセスプールで評価を実行 ★★★
        try:
            with multiprocessing.Pool(processes=num_workers) as pool:
                # pool.map を tqdm でラップして進捗を表示
                result_iter = pool.map(calculate_fitness, tasks)
                
                final_results = list(tqdm(
                    result_iter, 
                    total=len(tasks), 
                    desc=f"Gen {gen+old_gen}/{NUM_GENERATIONS} 評価中"
                ))

        except Exception as e:
            print(f"\n!!!!!!!!!!!!!! プール実行中に致命的なエラー !!!!!!!!!!!!!!")
            print(f"{e}")
            print(traceback.format_exc())
            final_results = [-999.0] * len(tasks) # 全て失敗として処理
        # ★★★ 評価完了 ★★★

        # --- 統計情報の表示 ---
        for ind, fitness_score in zip(population, final_results):
            ind.fitness = fitness_score
            
        best_individual = max(population, key=lambda ind: ind.fitness)
        # 失敗(-999)を除外して平均を計算
        valid_scores = [f for f in final_results if f > -990.0]
        avg_fitness = np.mean(valid_scores) if valid_scores else -999.0
        
        print(f"世代 {gen + old_gen} 完了: ベスト適応度: {best_individual.fitness:.2f}, 平均適応度: {avg_fitness:.2f}")

        # --- 新しい相手モデルの保存 ---
    
        new_opponent_path = os.path.join(opponent_pool_dir, f"opponent_gen_{gen + old_gen}.pth")
        torch.save(best_individual.get_model_state(), new_opponent_path)
        opponent_pool_paths.append(new_opponent_path)
        print(f"世代 {gen + old_gen} のエリートを '{new_opponent_path}' としてプールに追加しました。")

        if len(opponent_pool_paths) > OPPONENT_POOL_SIZE:
            oldest_opponent_path = opponent_pool_paths.pop(0) # 0番目 (一番古い) を削除
            try:
                os.remove(oldest_opponent_path)
                print(f"古くなった対戦相手 '{os.path.basename(oldest_opponent_path)}' をプールから削除しました。")
            except OSError as e:
                print(f"古い対戦相手 '{oldest_opponent_path}' の削除に失敗: {e}")

        if (gen + 1) % 100 == 0:
            milestone_path = os.path.join(milestone_dir, f"milestone_gen_{gen + old_gen + 1}.pth")
            torch.save(best_individual.get_model_state(), milestone_path)
            print(f"★★★ マイルストーンモデルを '{milestone_path}' として永久保存しました ★★★")

        # --- 選択 ---
        elites, parents_pool = selection(population)
        
        # --- 交叉と突然変異 ---
        next_population = elites 
        children_needed = POPULATION_SIZE - ELITE_SIZE
        
        # 進捗バーのためにtqdmを使用
        for i in tqdm(range(children_needed), desc=f"Gen {gen + old_gen +1} 子の生成中"):
            parent1, parent2 = random.sample(parents_pool, 2)
            child = crossover(parent1, parent2)
            mutant_child = mutation(child)
            next_population.append(mutant_child)
            
        population = next_population
        
    # --- 学習完了 ---
    print("\n--- GA学習完了 ---")
    
    final_best = max(population, key=lambda ind: ind.fitness)
    final_model_path = "tetris_model_v4_ga1done_5k.pth"
    torch.save(final_best.get_model_state(), final_model_path)
    print(f"最も優秀なモデルを '{final_model_path}' として保存しました (適応度: {final_best.fitness:.2f})")


if __name__ == "__main__":
    multiprocessing.set_start_method('spawn', force=True)
    
    main()