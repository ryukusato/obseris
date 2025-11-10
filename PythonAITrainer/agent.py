import numpy as np
import torch
import json
import time
import multiprocessing

# -------------------------------------------------------------------
# --- C++ シミュレーションエンジンのインポート ---
# -------------------------------------------------------------------
try:
    # C++ でコンパイルされたモジュール (経路探索 + 事実 付き)
    # (generate_all_landing_spots 関数をエクスポートする C++ が必要)
    import tetris_simulator
except ImportError:
    print("="*50)
    print("致命的エラー: C++モジュール 'tetris_simulator' が見つかりません。")
    print("C++コード (v0.3 / generate_all_landing_spots 関数を持つもの) をコンパイルしてください。")
    print("="*50)
    exit()
# -------------------------------------------------------------------

# CNNモデルの定義をインポート
from model import TetrisCNN

# --- 定数 ---
BOARD_WIDTH = 10
TOTAL_BOARD_HEIGHT = 40

# --- テンソル変換 ---
def state_to_tensor(board_state_np_array):
    """
    Numpy配列(40, 10) を CNN入力用のTensor(1, 1, 40, 10)に変換
    """
    board_tensor = torch.from_numpy(board_state_np_array.astype(np.float32)).unsqueeze(0).unsqueeze(0)
    return board_tensor

# --- multiprocessing ワーカー関数 ---

def process_one_node(node):
    """
    1つの入力ノード (盤面状態) から、
    C++エンジンを呼び出し、次の深さの全ノードを返す。
    
    (Turn 1 で提供された agent.py と同じロジック)
    """
    
    board_state = node['board_state']
    current_mino = node['minos'][0]
    next_minos = node['minos'][1:]
    hold_mino = node['hold_mino']
    can_hold = node['can_hold']
    
    # このノードにたどり着いた「最初の手」と「事実」を継承
    first_move_command = node['first_move_command']
    first_move_facts = node['first_move_facts']
    
    generated_nodes = []

    # --- アクション1: 現在のミノ (current_mino) を使う ---
    # C++ を呼び出し、LandingSpot オブジェクトのリストを取得
    try:
        # ★ C++はここで「次の盤面候補」と「事実」を計算して返す
        possible_landings = tetris_simulator.generate_all_landing_spots(
            board_state, 
            current_mino
        )
    except Exception as e:
        print(f"[Worker] C++ tetris_simulator.generate_all_landing_spots でエラー: {e}")
        return []

    for landing in possible_landings:
        new_minos_list = next_minos + ['NoShape']
        
        new_node = {
            'board_state': landing.future_board, # C++からNumpyに自動変換
            'minos': new_minos_list,
            'hold_mino': hold_mino,
            'can_hold': True,
            'first_move_command': first_move_command, # 継承
            'first_move_facts': first_move_facts     # 継承
        }
        
        # もしこのノードが「最初の手」なら、命令(path)と事実(facts)を保存
        if first_move_command is None:
            # ★ Javaに送る「キー操作リスト」を命令として保存
            new_node['first_move_command'] = {"path": landing.path, "useHold": False}
            # ★ GAの適応度関数で使う「事実」を保存
            new_node['first_move_facts'] = {
                "lines_cleared": landing.lines_cleared,
                "is_t_spin": landing.is_t_spin,
                "is_mini_t_spin": landing.is_mini_t_spin
            }
        
        generated_nodes.append(new_node)

    # --- アクション2: ホールド (can_hold が True の場合) ---
    if can_hold:
        piece_to_use = hold_mino
        new_hold_mino = current_mino
        
        if piece_to_use == 'NoShape' or piece_to_use is None:
            piece_to_use = next_minos[0]
            if piece_to_use == 'NoShape' or piece_to_use is None:
                 return generated_nodes # 使うピースがない
            new_minos_list = next_minos[1:] + ['NoShape']
        else:
            new_minos_list = next_minos
            
        try:
            # ★ C++はここでも「次の盤面候補」と「事実」を計算して返す
            possible_landings_hold = tetris_simulator.generate_all_landing_spots(
                board_state, 
                piece_to_use
            )
        except Exception as e:
            print(f"[Worker] C++ (Hold) でエラー: {e}")
            return generated_nodes # 少なくともHoldなしの結果は返す

        for landing in possible_landings_hold:
            new_node = {
                'board_state': landing.future_board,
                'minos': new_minos_list,
                'hold_mino': new_hold_mino,
                'can_hold': False,
                'first_move_command': first_move_command,
                'first_move_facts': first_move_facts
            }

            if first_move_command is None:
                # ★ Javaに送る「キー操作リスト」を命令として保存
                new_node['first_move_command'] = {"path": landing.path, "useHold": True}
                # ★ GAの適応度関数で使う「事実」を保存
                new_node['first_move_facts'] = {
                    "lines_cleared": landing.lines_cleared,
                    "is_t_spin": landing.is_t_spin,
                    "is_mini_t_spin": landing.is_mini_t_spin
                }
            
            generated_nodes.append(new_node)
            
    return generated_nodes


# --- Agent クラス (ビームサーチ実行) ---
class Agent:
    def __init__(self, model_path=None, beam_width=5, search_depth=7):
        # ... (デバイス設定) ...
        if torch.backends.mps.is_available():
            self.device = torch.device("mps")
        else:
            self.device = torch.device("cpu")
        
        # ★ AIの「脳」として CNN モデルを使用
        self.model = TetrisCNN().to(self.device)
        if model_path:
            try:
                # GAから渡された重み(.pth)をロード
                self.model.load_state_dict(torch.load(model_path, map_location=self.device))
            except Exception as e:
                pass # ロード失敗でもランダム重みで続行
        
        self.model.eval()
        self.beam_width = beam_width
        self.search_depth = search_depth
        
        self.pool = multiprocessing.Pool(multiprocessing.cpu_count())


    def find_best_move(self, state_json):
        """
        AIのメイン思考ルーチン。
        最善の「コマンド(path)」と「その手の事実(facts)」を返す。
        """
        try:
            state = json.loads(state_json)
        except json.JSONDecodeError:
            print("Error decoding state JSON")
            return None, None 

        if state.get('currentMino') is None or state['currentMino'] == 'NoShape':
            return None, None

        # --- 初期ノードの準備 ---
        current_board_np = np.array(state['board'], dtype=np.float32)
        minos_queue = [state['currentMino']] + state.get('nextMinos', [])
        while len(minos_queue) < self.search_depth:
            minos_queue.append('NoShape')

        initial_node = {
            'board_state': current_board_np,
            'minos': minos_queue,
            'hold_mino': state.get('holdMino', 'NoShape'),
            'can_hold': state.get('canHold', True),
            'first_move_command': None, 
            'first_move_facts': None    
        }
        
        # --- ビームサーチ開始 ---
        # (C++ を呼び出し、最初の深さのノード群を取得)
        current_beam_nodes = process_one_node(initial_node)
        
        if not current_beam_nodes:
            emergency_cmd = {"path": ["HARD_DROP"], "useHold": False}
            emergency_facts = {"lines_cleared": 0, "is_t_spin": False, "is_mini_t_spin": False}
            return emergency_cmd, emergency_facts

        # ★ CNNモデルで評価し、上位K個（ビーム幅）を残す
        beam = self.evaluate_and_prune(current_beam_nodes, self.beam_width)

        # (深さの分だけ C++呼び出し -> CNN評価 を繰り返す)
        for depth in range(1, self.search_depth - 1):
            tasks = [node for score, node in beam]
            if not tasks: break
                
            all_future_nodes_lists = self.pool.map(process_one_node, tasks)
            all_future_nodes = [node for sublist in all_future_nodes_lists for node in sublist]
            
            if not all_future_nodes: break
            beam = self.evaluate_and_prune(all_future_nodes, self.beam_width)

        # --- 最終結果 ---
        if not beam:
            emergency_cmd = {"path": ["HARD_DROP"], "useHold": False}
            emergency_facts = {"lines_cleared": 0, "is_t_spin": False, "is_mini_t_spin": False}
            return emergency_cmd, emergency_facts

        # ビームに残ったものの中で、最もスコアが高いものを選ぶ
        best_score, best_final_node = beam[0]
        
        # ★ Javaに送る「コマンド(path)」と、GAで使う「事実(facts)」を両方返す
        return best_final_node['first_move_command'], best_final_node['first_move_facts']

    def evaluate_and_prune(self, nodes, k):
        """
        C++から受け取った盤面候補(nodes)のリストを
        CNNモデルで一括評価し、スコア上位K個を返す。
        """
        if not nodes: return []
        
        # 1. 盤面データをNumpy配列のリストにする
        board_arrays = [node['board_state'] for node in nodes]
        
        # 2. (N, 40, 10) -> (N, 1, 40, 10) のTensorに変換
        board_tensors = torch.stack(
            [state_to_tensor(board).squeeze(0) for board in board_arrays],
            dim=0
        )
        
        # 3. ★ CNNモデルで全盤面の「価値」を一括推論
        with torch.no_grad():
            values = self.model(board_tensors.to(self.device))
            
        values = values.cpu().numpy().flatten()
        
        # 4. スコアでソート
        scored_nodes = sorted(
            zip(values, nodes), 
            key=lambda x: x[0], 
            reverse=True
        )
        
        # 5. 上位K個を返す
        return scored_nodes[:k]
        
    def __del__(self):
        if hasattr(self, 'pool'):
            self.pool.close()
            self.pool.join()