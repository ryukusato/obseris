import torch
import torch.nn as nn
import torch.nn.functional as F
from collections import OrderedDict

# --- 1. 元のモデル (v2) の定義 ---
# (Javaコードと一致する 72次元 の入力)

OLD_FEATURE_INPUT_SIZE = (5 * 7) + (5 * 7) + 1 + 1 # 72

class TetrisCNN_v2(nn.Module):
    def __init__(self):
        super(TetrisCNN_v2, self).__init__()
        
        # 盤面 (2チャンネル) CNN
        self.conv1 = nn.Conv2d(in_channels=2, out_channels=32, kernel_size=5, stride=1, padding=2)
        self.bn1 = nn.BatchNorm2d(32)
        self.pool1 = nn.MaxPool2d(kernel_size=2, stride=2) # -> [N, 32, 20, 5]
        
        self.conv2 = nn.Conv2d(32, 64, kernel_size=3, stride=1, padding=1)
        self.bn2 = nn.BatchNorm2d(64)
        self.pool2 = nn.MaxPool2d(kernel_size=2, stride=2) # -> [N, 64, 10, 2]
        
        self.conv_output_size = 64 * 10 * 2 # 1280
        
        # 付加情報 (72次元) FC
        self.fc_features1 = nn.Linear(OLD_FEATURE_INPUT_SIZE, 64) # (入力が 72)
        self.fc_features2 = nn.Linear(64, 32)
        
        # 結合層
        self.fc_combined1 = nn.Linear(self.conv_output_size + 32, 256)
        self.fc_combined2 = nn.Linear(256, 1)

    def forward(self, board_tensor, feature_tensor):
        x_board = self.pool1(F.relu(self.bn1(self.conv1(board_tensor))))
        x_board = self.pool2(F.relu(self.bn2(self.conv2(x_board))))
        x_board = x_board.view(-1, self.conv_output_size)
        
        x_features = F.relu(self.fc_features1(feature_tensor))
        x_features = F.relu(self.fc_features2(x_features))
        
        x_combined = torch.cat((x_board, x_features), dim=1)
        
        x = F.relu(self.fc_combined1(x_combined))
        value = self.fc_combined2(x)
        return value

# --- 2. 新しいモデル (v3) の定義 ---
# (ホールドピース 7次元 を追加した 79次元 の入力)

HOLD_FEATURES = 7 # (I,O,T,S,Z,J,L)
NEW_FEATURE_INPUT_SIZE = OLD_FEATURE_INPUT_SIZE + HOLD_FEATURES # 72 + 7 = 79

class TetrisCNN_v3(nn.Module):
    def __init__(self):
        super(TetrisCNN_v3, self).__init__()
        
        # 盤面CNN (v2と全く同じ)
        self.conv1 = nn.Conv2d(in_channels=2, out_channels=32, kernel_size=5, stride=1, padding=2)
        self.bn1 = nn.BatchNorm2d(32)
        self.pool1 = nn.MaxPool2d(kernel_size=2, stride=2)
        self.conv2 = nn.Conv2d(32, 64, kernel_size=3, stride=1, padding=1)
        self.bn2 = nn.BatchNorm2d(64)
        self.pool2 = nn.MaxPool2d(kernel_size=2, stride=2)
        self.conv_output_size = 64 * 10 * 2 # 1280
        
        # ★★★ ここだけがv2と異なる ★★★
        # 付加情報 (79次元) FC
        self.fc_features1 = nn.Linear(NEW_FEATURE_INPUT_SIZE, 64) # (入力が 79 に)
        # ★★★★★★★★★★★★★★★★★
        
        self.fc_features2 = nn.Linear(64, 32) # (v2と同じ)
        
        # 結合層 (v2と同じ)
        self.fc_combined1 = nn.Linear(self.conv_output_size + 32, 256)
        self.fc_combined2 = nn.Linear(256, 1)

    def forward(self, board_tensor, feature_tensor):
        # forwardのロジックはv2と全く同じ
        x_board = self.pool1(F.relu(self.bn1(self.conv1(board_tensor))))
        x_board = self.pool2(F.relu(self.bn2(self.conv2(x_board))))
        x_board = x_board.view(-1, self.conv_output_size)
        
        # feature_tensor が [N, 79] で渡されることを期待
        x_features = F.relu(self.fc_features1(feature_tensor))
        x_features = F.relu(self.fc_features2(x_features))
        
        x_combined = torch.cat((x_board, x_features), dim=1)
        
        x = F.relu(self.fc_combined1(x_combined))
        value = self.fc_combined2(x)
        return value

# --- 3. 重み転移スクリプト ---

OLD_MODEL_PATH = "tetris_model_v2_final.pth"
NEW_MODEL_PATH = "tetris_model_v3_transfer.pth" # 生成するファイル名

def transfer_weights():
    print(f"Loading weights from: {OLD_MODEL_PATH}")
    
    # 1. 古いモデルの重み(state_dict)を読み込む
    try:
        old_state_dict = torch.load(OLD_MODEL_PATH, map_location=torch.device('cpu'))
    except FileNotFoundError:
        print(f"Error: {OLD_MODEL_PATH} not found.")
        print("Please make sure the old .pth file is in the same directory.")
        return
    except Exception as e:
        print(f"Error loading old model weights: {e}")
        return

    # 2. 新しいモデルv3のインスタンスを作成
    model_v3 = TetrisCNN_v3()
    # 新しいモデルのデフォルトの重み(state_dict)を取得
    new_state_dict = model_v3.state_dict()
    
    print("Transferring weights...")
    
    # 3. 新しいstate_dictに古い重みをコピーしていく
    for key in old_state_dict.keys():
        if key in new_state_dict:
            
            # (特別扱い) 形状が異なる 'fc_features1.weight'
            if key == 'fc_features1.weight':
                # 古い重み [64, 72] を取得
                old_weights = old_state_dict[key]
                # 新しい重み [64, 79] (ランダム初期化済み) を取得
                new_weights = new_state_dict[key]
                
                # 新しい重みの [:, 0:72] の部分に古い重みをコピー
                print("  -> Transferring 'fc_features1.weight' (partial copy)")
                with torch.no_grad(): # 勾配計算をオフ
                    new_weights[:, :OLD_FEATURE_INPUT_SIZE] = old_weights
                
                # new_state_dict に上書き
                new_state_dict[key] = new_weights
            
            # (通常) 形状が同じ層はそのままコピー
            else:
                new_state_dict[key] = old_state_dict[key]
                
        else:
            print(f"  (Skipping weight: {key} - not found in new model)")

    # 4. 転移が完了したstate_dictをモデルv3に読み込ませる
    model_v3.load_state_dict(new_state_dict)
    
    # 5. 新しい.pthファイルとして保存
    try:
        torch.save(model_v3.state_dict(), NEW_MODEL_PATH)
        print(f"\nSuccess! Transferred weights saved to: {NEW_MODEL_PATH}")
        print("You can now start fine-tuning with this new .pth file.")
    except Exception as e:
        print(f"\nError saving new model: {e}")

if __name__ == "__main__":
    transfer_weights()