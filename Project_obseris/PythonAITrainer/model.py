import torch
import torch.nn as nn
import torch.nn.functional as F

class TetrisCNN(nn.Module):
    """
    盤面の状態を評価し、その「価値（Value）」をスカラー値で出力するCNNモデル。
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