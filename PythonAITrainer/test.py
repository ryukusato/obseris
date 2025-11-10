import tetris_simulator
import random
import time

print("--- C++直列テスト開始 ---")

# ★ C++モジュールが正しくロックを実装しているか確認するため
#    前回提案した C++ 側の std::mutex 修正が
#    正しくコンパイルされているか確認してください。

env = tetris_simulator.GameState()
env.reset()
steps = 0
MAX_STEPS = 5000 # 無限ループ防止

start_time = time.time()

while not env.is_game_over:
    if steps > MAX_STEPS:
        print("警告: 5000ステップ到達。強制終了。")
        break
        
    # 1. C++から手を取得
    possible_moves = env.get_all_possible_moves()
    if not possible_moves:
        break # ゲームオーバー
        
    # 2. AIの代わりに「ランダム」に手を選ぶ
    chosen_move = possible_moves[random.randint(0, len(possible_moves) - 1)]
    
    # 3. C++で手を実行
    env.execute_move(chosen_move)
    steps += 1
    
    if steps % 500 == 0:
        print(f"ステップ {steps} 経過...")

end_time = time.time()

print("--- C++直列テスト完了 ---")
print(f"ゲームオーバー: {env.is_game_over}")
print(f"総ステップ数: {steps}")
print(f"実行時間: {end_time - start_time:.2f} 秒")