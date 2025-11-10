import torch
import torch.nn as nn
import torch.nn.functional as F

# --- v2モデル (ホールドなし) の特徴量定義 ---
NEXT_QUEUE_FEATURES = 5 * 7
OPPONENT_NEXT_QUEUE_FEATURES = 5 * 7
SELF_GARBAGE_FEATURES = 1
OPPONENT_GARBAGE_FEATURES = 1

# v2 の入力サイズ = 35 + 35 + 1 + 1 = 72
FEATURE_INPUT_SIZE_V2 = NEXT_QUEUE_FEATURES + OPPONENT_NEXT_QUEUE_FEATURES + SELF_GARBAGE_FEATURES + OPPONENT_GARBAGE_FEATURES



class TetrisCNN_v2(nn.Module):
    """
    v2モデル: 盤面(2ch) + 特徴量(72次元)
    """
    def __init__(self):
        super(TetrisCNN_v2, self).__init__()
        
        # --- 1. 盤面 (2チャンネル) を処理するCNN ---
        self.conv1 = nn.Conv2d(in_channels=2, out_channels=32, kernel_size=5, stride=1, padding=2)
        self.bn1 = nn.BatchNorm2d(32)
        self.pool1 = nn.MaxPool2d(kernel_size=2, stride=2) # -> [N, 32, 20, 5]
        
        self.conv2 = nn.Conv2d(32, 64, kernel_size=3, stride=1, padding=1)
        self.bn2 = nn.BatchNorm2d(64)
        self.pool2 = nn.MaxPool2d(kernel_size=2, stride=2) # -> [N, 64, 10, 2]
        
        self.conv_output_size = 64 * 10 * 2 # 1280
        
        # --- 2. 付加情報 (72次元) を処理する全結合層 ---
        self.fc_features1 = nn.Linear(FEATURE_INPUT_SIZE_V2, 64) # (入力が 72 に)
        self.fc_features2 = nn.Linear(64, 32) # 特徴量を32次元に圧縮
        
        # --- 3. 結合層 ---
        self.fc_combined1 = nn.Linear(self.conv_output_size + 32, 256)
        self.fc_combined2 = nn.Linear(256, 1)

    def forward(self, board_tensor, feature_tensor):
        # board_tensor は [N, 2, 40, 10] を想定
        # feature_tensor は [N, 72] を想定
        
        # 1. 盤面 (CNN) の処理
        x_board = self.pool1(F.relu(self.bn1(self.conv1(board_tensor))))
        x_board = self.pool2(F.relu(self.bn2(self.conv2(x_board))))
        x_board = x_board.view(-1, self.conv_output_size) # -> [N, 1280]
        
        # 2. 付加情報 (FC) の処理
        x_features = F.relu(self.fc_features1(feature_tensor))
        x_features = F.relu(self.fc_features2(x_features)) # -> [N, 32]
        
        # 3. 2つの結果を結合
        x_combined = torch.cat((x_board, x_features), dim=1) # -> [N, 1312]
        
        # 4. 最終評価値
        x = F.relu(self.fc_combined1(x_combined))
        value = self.fc_combined2(x) # -> [N, 1]
        
        return value




class TetrisCNN(nn.Module):
    """
    旧モデル (v1): 盤面(1ch) のみ評価
    """
    def __init__(self, board_height=40, board_width=10):
        super().__init__()
        self.board_height = board_height
        self.board_width = board_width
        
        # 盤面用のCNN層
        # 入力: (Batch, 1, 40, 10)
        self.conv1 = nn.Conv2d(1, 16, kernel_size=3, padding=1)
        self.conv2 = nn.Conv2d(16, 32, kernel_size=3, padding=1)
        self.conv3 = nn.Conv2d(32, 64, kernel_size=3, padding=1)
        
        # CNNからの出力をフラットにしたサイズを計算
        conv_out_size = 64 * board_height * board_width
        
        # 全結合層 (Fully Connected)
        self.fc1 = nn.Linear(conv_out_size, 256)
        self.fc2 = nn.Linear(256, 1) # 最終的な出力は1つの「価値」

    def forward(self, board_tensor):
        """
        :param board_tensor: (Batch, 1, 40, 10) の盤面データ
        """
        x = F.relu(self.conv1(board_tensor))
        x = F.relu(self.conv2(x))
        x = F.relu(self.conv3(x))
        
        # Flatten
        x = x.view(x.size(0), -1) 
        
        x = F.relu(self.fc1(x))
        value = self.fc2(x)
        
        return value