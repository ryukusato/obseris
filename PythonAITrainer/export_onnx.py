import os
import traceback
import torch
from model import TetrisCNN_v2

# --- モデルの入力次元 (model.py / train.py と一致) ---
# ※エクスポートスクリプト内でも定義しておくと安全
NUM_SHAPE_TYPES = 7 
NEXT_QUEUE_FEATURES = 5 * NUM_SHAPE_TYPES    # 35
SELF_GARBAGE_FEATURES = 1
OPPONENT_GARBAGE_FEATURES = 1
FEATURE_INPUT_SIZE = (
    NEXT_QUEUE_FEATURES +  # 35
    NEXT_QUEUE_FEATURES +  # 35
    SELF_GARBAGE_FEATURES +   # 1
    OPPONENT_GARBAGE_FEATURES # 1
) # 合計 72


def export_to_onnx(model_path, output_path, batch_size=1, height=40, width=10):
    """
    学習済みの .pth ファイルを .onnx 形式に変換する
    (★ TetrisCNN_v2 のマルチ入力に対応)
    """
    print(f"'{model_path}' を '{output_path}' にエクスポートします...")
    
    # 1. モデルをロード
    model = TetrisCNN_v2()
    try:
        model.load_state_dict(torch.load(model_path, map_location="cpu"))
    except FileNotFoundError:
        print(f"エラー: モデルファイル '{model_path}' が見つかりません。")
        print("学習が完了していない可能性があります。エクスポートを中止します。")
        return
    except Exception as e:
        print(f"モデル '{model_path}' のロード中にエラーが発生しました: {e}")
        return
        
    model.eval() # 評価モードにする

    # 2. ダミーの入力データを作成 (★ 2つの入力)
    
    # 入力1: 盤面テンソル
    # [Batch, Channel=2, Height, Width]
    dummy_board_tensor = torch.randn(
        batch_size, 2, height, width, 
        device="cpu"
    )
    
    # 入力2: 特徴量テンソル
    # [Batch, Feature_Size=72]
    dummy_feature_tensor = torch.randn(
        batch_size, FEATURE_INPUT_SIZE, 
        device="cpu"
    )
    
    # ★ モデルへの入力引数をタプルで渡す
    dummy_inputs = (dummy_board_tensor, dummy_feature_tensor)

    # 3. ONNXエクスポートを実行
    try:
        torch.onnx.export(
            model,                   # 実行するモデル
            dummy_inputs,            # ★ モデルの入力 (タプル)
            output_path,             # 出力先パス
            export_params=True,      # 重みを一緒に保存
            opset_version=15,
            do_constant_folding=True,# 最適化
            input_names = ['board_tensor_input', 'feature_tensor_input'], # ★ 入力名 (2つ)
            output_names = ['output_value'], # 出力名
            
            # 動的バッチサイズに対応 (オプション)
            dynamic_axes={
                'board_tensor_input' : {0 : 'batch_size'},
                'feature_tensor_input': {0 : 'batch_size'},
                'output_value' : {0 : 'batch_size'}
            }
        )
        print(f"ONNXエクスポート成功。 ('{output_path}')")
    except Exception as e:
        print(f"ONNXエクスポート失敗: {e}")
        traceback.print_exc()

# --- main() 関数の呼び出し部分 ---
if __name__ == "__main__":
    print("\n--- ONNXエクスポート開始 (変換のみ) ---")
    
    # ★ 変換したい学習済みモデルのパス
    final_model_path = "tetris_model_v4_ga1done_3k.pth"
    onnx_output_path = "tetris_model_v4_ga1done_3k.onnx"
    
    # ★ final_model_path が存在することを確認してください
    if not os.path.exists(final_model_path):
        print(f"エラー: 変換元のモデル '{final_model_path}' が見つかりません。")
        print("パスが正しいか、学習が完了しているか確認してください。")
    else:
        export_to_onnx(final_model_path, onnx_output_path)