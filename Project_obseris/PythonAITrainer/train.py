import multiprocessing
import torch
import torch.nn as nn
import numpy as np
import random
import json
import time
import copy
from multiprocessing import Pool, cpu_count

# --- 必要な自作モジュールのインポート ---
from agent import Agent            # ★ 上記の「経路探索対応版」
from obseris_env import TetrisEnv  # (Javaとの通信役、変更不要)
from model import TetrisCNN       # (AIの脳、変更不要)

# --- ユーザー設定 (前回と同じ) ---
JAVA_PATH = "C:\\Program Files\\Java\\jdk-17\\bin\\java.exe" 
JAR_PATH = "out\\artifacts\\Tetris_jar\\Tetris.jar" 
MODEL_SAVE_PATH = "ga_best_tetris_model_v2.pth"

# --- 遺伝的アルゴリズム (GA) 設定 ---
POPULATION_SIZE = 20    
NUM_GENERATIONS = 1000  
GAMES_PER_INDIVIDUAL = 5 
TOP_K_ELITE = 4         
MUTATION_RATE = 0.01    
MUTATION_STRENGTH = 0.1 

# --- ★ 適応度関数 (Fitness Function) の設定 (最重要) ---
# (C++ が返す「事実」に基づいてスコアを付ける)
FITNESS_PER_LINE_CLEAR = {
    1: 100,  # Single
    2: 300,  # Double
    3: 600,  # Triple
    4: 1200, # Tetris
}
FITNESS_PER_T_SPIN = {
    1: 800,   # T-Spin Single
    2: 2500,  # T-Spin Double (非常に高く評価)
    3: 3000,  # T-Spin Triple
}
FITNESS_PER_MINI_T_SPIN = 100 # ミニTスピン
FITNESS_PER_B2B = 500         # Back-to-Back ボーナス (Javaから取得)
FITNESS_PER_COMBO = 50        # 1コンボごとのボーナス (Javaから取得)
FITNESS_SURVIVAL_PER_PIECE = 1 
FITNESS_GAME_OVER_PENALTY = -5000 

# (Agentの初期化設定)
AGENT_BEAM_WIDTH = 5
AGENT_SEARCH_DEPTH = 7

# ===================================================================
# --- GAコア関数 (前回と同じ) ---
# ===================================================================
def create_individual():
    return TetrisCNN()

def crossover(parent1, parent2):
    # ... (前回と同じ) ...
    child = create_individual()
    p1_dict = parent1.state_dict()
    p2_dict = parent2.state_dict()
    child_dict = child.state_dict()
    for key in child_dict.keys():
        if random.random() < 0.5:
            child_dict[key] = p1_dict[key].clone()
        else:
            child_dict[key] = p2_dict[key].clone()
    child.load_state_dict(child_dict)
    return child

def mutate(individual, rate, strength):
    # ... (前回と同じ) ...
    with torch.no_grad():
        for param in individual.parameters():
            mask = torch.rand_like(param.data) < rate
            mutation = (torch.rand_like(param.data) * 2 - 1) * strength
            param.data[mask] += mutation
    return individual

# ===================================================================
# --- 評価関数 (マルチプロセスで実行) ---
# ===================================================================

def evaluate_fitness(individual_state_dict):
    """
    1体のAI (の重み) を受け取り、指定回数ゲームをプレイさせ、
    平均の「適応度 (Fitness)」を計算して返す。
    """
    
    # 1. このプロセス専用のEnvとAgentを初期化
    env = TetrisEnv(java_path=JAVA_PATH, jar_path=JAR_PATH)
    agent = Agent(model_path=None, 
                  beam_width=AGENT_BEAM_WIDTH, 
                  search_depth=AGENT_SEARCH_DEPTH)
    
    model_to_test = create_individual()
    model_to_test.load_state_dict(individual_state_dict)
    agent.model = model_to_test.to(agent.device)
    
    total_fitness = 0.0

    for _ in range(GAMES_PER_INDIVIDUAL):
        game_fitness = 0
        state = env.reset()
        if state is None:
            env.close(); return -float('inf') 

        done = False

        while not done:
            state_json = json.dumps(state)
            
            # 2. ★ C++ AI に最善手(command)と、その「事実(facts)」を計算させる
            command, facts = agent.find_best_move(state_json)
            
            if command is None or facts is None:
                game_fitness += FITNESS_GAME_OVER_PENALTY # 手詰まり
                break
                
            # 3. ★「適応度」の計算 (Javaを呼び出す「前」に、C++の事実を使う)
            
            lines = facts['lines_cleared']
            if facts['is_t_spin']:
                # Tスピン (Tスピン・シングル/ダブル/トリプル)
                game_fitness += FITNESS_PER_T_SPIN.get(lines, 0)
            elif facts['is_mini_t_spin']:
                # ミニTスピン
                game_fitness += FITNESS_PER_MINI_T_SPIN
            else:
                # 通常のラインクリア
                game_fitness += FITNESS_PER_LINE_CLEAR.get(lines, 0)

            # 4. Java環境でその手 (command) を実行
            next_state, done, info = env.step(command)

            if done or next_state is None:
                game_fitness += FITNESS_GAME_OVER_PENALTY
                break
            
            # 5. ★ Javaから返ってきた「追加の事実」で適応度を加算
            
            # B2Bボーナス (Javaが判定)
            if next_state.get('isB2BActive', False):
                if lines > 0 or facts['is_t_spin'] or facts['is_mini_t_spin']:
                     game_fitness += FITNESS_PER_B2B
            
            # コンボボーナス (Javaが判定)
            game_fitness += next_state.get('comboCount', 0) * FITNESS_PER_COMBO
                
            # 生存ボーナス
            game_fitness += FITNESS_SURVIVAL_PER_PIECE
                
            state = next_state
            
            if info.get("error"): break
        
        total_fitness += game_fitness

    env.close()
    return total_fitness / GAMES_PER_INDIVIDUAL

# ===================================================================
# --- メインのGAループ ---
# ===================================================================

if __name__ == "__main__":
    try:
        multiprocessing.set_start_method('spawn')
    except RuntimeError:
        pass

    print("--- 遺伝的アルゴリズム (GA) 学習開始 (v3 - C++経路探索対応) ---")
    print(f"世代あたり個体数: {POPULATION_SIZE}")
    print(f"エリート数: {TOP_K_ELITE}")
    print(f"並列ワーカー数: {cpu_count()}")
    print("="*40)

    # ステップ1: 第1世代のAI(個体群)をランダムに生成
    population = [create_individual() for _ in range(POPULATION_SIZE)]

    # ステップ2: 世代交代ループ
    for generation in range(NUM_GENERATIONS):
        gen_start_time = time.time()
        print(f"\n--- 世代 {generation + 1} / {NUM_GENERATIONS} ---")

        # ステップ3: 評価 (並列実行)
        population_weights = [ind.state_dict() for ind in population]
        print(f"[{generation+1}] 評価中...")
        
        fitness_scores = []
        try:
            with Pool(cpu_count()) as pool:
                fitness_scores = pool.map(evaluate_fitness, population_weights)
        except Exception as e:
            print(f"!!! 並列評価中にエラー: {e}")
            break

        # ステップ4: 選択 (Selection)
        scored_population = sorted(
            zip(fitness_scores, population), 
            key=lambda x: x[0], 
            reverse=True
        )

        elite = [ind for score, ind in scored_population[:TOP_K_ELITE]]
        
        best_fitness = scored_population[0][0]
        avg_fitness = sum(fitness_scores) / POPULATION_SIZE
        print(f"[{generation+1}] 評価完了。 Best: {best_fitness:.2f}, Avg: {avg_fitness:.2f}")

        torch.save(elite[0].state_dict(), MODEL_SAVE_PATH)
        print(f"    -> 最強モデルを '{MODEL_SAVE_PATH}' に保存しました。")

        # ステップ5: 交叉 & 突然変異
        next_generation = [copy.deepcopy(ind) for ind in elite]
        
        while len(next_generation) < POPULATION_SIZE:
            parent1, parent2 = random.sample(elite, 2)
            child = crossover(parent1, parent2)
            child = mutate(child, MUTATION_RATE, MUTATION_STRENGTH)
            next_generation.append(child)
            
        population = next_generation
        gen_end_time = time.time()
        print(f"    世代処理時間: {gen_end_time - gen_start_time:.2f} 秒")

    print("--- GA学習が完了しました ---")