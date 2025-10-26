import unittest
import numpy as np

# テスト対象のクラスや関数をインポート
from agent import BoardSim, SHAPES, TOTAL_BOARD_HEIGHT, BOARD_WIDTH, simulate_rotation, drop_piece 
# (agent.pyに必要なクラスや関数がある前提)

class TestBoardSimulation(unittest.TestCase):

    def test_initialization(self):
        """ボードが正しく空で初期化されるかテスト"""
        board = BoardSim()
        self.assertTrue(np.all(board.grid == 0))
        self.assertEqual(board.grid.shape, (TOTAL_BOARD_HEIGHT, BOARD_WIDTH))

    def test_is_valid_position_empty_board(self):
        """空のボードで基本的な配置が可能かテスト"""
        board = BoardSim()
        t_shape_coords = SHAPES['TShape']['coords'][0] # Tミノの初期形状
        # 中央付近 (内部座標)
        self.assertTrue(board.is_valid_position(t_shape_coords, 5, 25))
        # 左端ギリギリ
        self.assertTrue(board.is_valid_position(t_shape_coords, 1, 25))
        # 右端ギリギリ
        self.assertTrue(board.is_valid_position(t_shape_coords, 8, 25))
        # 上端ギリギリ
        self.assertTrue(board.is_valid_position(t_shape_coords, 5, 1))
         # 下端ギリギリ
        self.assertTrue(board.is_valid_position(t_shape_coords, 5, TOTAL_BOARD_HEIGHT - 1))

    def test_is_valid_position_out_of_bounds(self):
        """盤面外には配置できないことをテスト"""
        board = BoardSim()
        t_shape_coords = SHAPES['TShape']['coords'][0]
        # 左外
        self.assertFalse(board.is_valid_position(t_shape_coords, 0, 25))
        # 右外
        self.assertFalse(board.is_valid_position(t_shape_coords, 9, 25))
        # 上外
        self.assertFalse(board.is_valid_position(t_shape_coords, 5, 0))
         # 下外 (理論上は可能だが通常しない)
        # self.assertFalse(board.is_valid_position(t_shape_coords, 5, TOTAL_BOARD_HEIGHT))

    def test_place_tetromino(self):
        """ミノが正しく盤面に配置されるかテスト"""
        board = BoardSim()
        i_shape_coords = SHAPES['LineShape']['coords'][0] # Iミノ横向き
        board.place_tetromino(i_shape_coords, 5, 38) # 下から2行目に配置

        # 配置された場所が1になっているか確認
        self.assertEqual(board.grid[38, 4], 1.0) # (5-1, 38)
        self.assertEqual(board.grid[38, 5], 1.0) # (5+0, 38)
        self.assertEqual(board.grid[38, 6], 1.0) # (5+1, 38)
        self.assertEqual(board.grid[38, 7], 1.0) # (5+2, 38)
        # 関係ない場所が0のままか確認
        self.assertEqual(board.grid[37, 5], 0.0)
        self.assertEqual(board.grid[39, 5], 0.0)

    def test_collision(self):
        """他のブロックとの衝突判定をテスト"""
        board = BoardSim()
        i_shape_coords = SHAPES['LineShape']['coords'][0]
        board.place_tetromino(i_shape_coords, 5, 38) # 先にIミノを置く

        t_shape_coords = SHAPES['TShape']['coords'][0]
        # Iミノの上に置こうとすると衝突するはず
        self.assertFalse(board.is_valid_position(t_shape_coords, 5, 38))
        # Iミノの隣は置けるはず
        self.assertTrue(board.is_valid_position(t_shape_coords, 5, 37))

    def test_clear_lines_single(self):
        """1ライン消去のテスト"""
        board = BoardSim()
        # 盤面の一番下の行を埋める (穴なし)
        for x in range(BOARD_WIDTH):
            board.grid[TOTAL_BOARD_HEIGHT - 1, x] = 1.0
        # その上に1つブロックを置く
        board.grid[TOTAL_BOARD_HEIGHT - 2, 5] = 1.0

        lines_cleared = board.clear_lines()
        self.assertEqual(lines_cleared, 1)
        # 一番下の行が空になり、上のブロックが落ちてきているか
        self.assertTrue(np.all(board.grid[TOTAL_BOARD_HEIGHT - 1, :] == 0))
        self.assertEqual(board.grid[TOTAL_BOARD_HEIGHT - 1, 5], 1.0) # 落ちてきたブロック
        self.assertEqual(board.grid[TOTAL_BOARD_HEIGHT - 2, 5], 0.0) # 元の位置は空

    def test_clear_lines_multiple(self):
        """複数ライン消去のテスト"""
        board = BoardSim()
        # 下2行を埋める
        for y in range(TOTAL_BOARD_HEIGHT - 2, TOTAL_BOARD_HEIGHT):
            for x in range(BOARD_WIDTH):
                board.grid[y, x] = 1.0
        # その上にブロック
        board.grid[TOTAL_BOARD_HEIGHT - 3, 3] = 1.0

        lines_cleared = board.clear_lines()
        self.assertEqual(lines_cleared, 2)
        # 下2行が空になり、ブロックが2段落ちているか
        self.assertTrue(np.all(board.grid[TOTAL_BOARD_HEIGHT - 1, :] == 0))
        self.assertTrue(np.all(board.grid[TOTAL_BOARD_HEIGHT - 2, :] == 0))
        self.assertEqual(board.grid[TOTAL_BOARD_HEIGHT - 1, 3], 1.0)
        self.assertEqual(board.grid[TOTAL_BOARD_HEIGHT - 3, 3], 0.0)

    # --- 回転とドロップのテスト例 ---
    def test_simulate_rotation_t_spin_kick(self):
        """Tスピンの基本的な壁キックをシミュレートできるか"""
        board = BoardSim()
        # Tスピンダブルのセットアップ例 (下3行)
        # GGGGGGGGG_
        # GXXXXXXXXG
        # GXXXXXXXXG
        board.grid[39, 9] = 0 # 穴
        for x in range(9): board.grid[39, x] = 1.0
        for x in range(1, 9): board.grid[38, x] = 1.0
        for x in range(1, 9): board.grid[37, x] = 1.0
        board.grid[37:40, 0] = 1.0 # 左壁
        board.grid[38:40, 9] = 1.0 # 右壁

        # Tミノ初期位置 (x=8, y=36, rot=0) から右回転 (clockwise=True)
        success, next_x, next_y, next_rot = simulate_rotation(board, 8, 36, 0, 'TShape', True)
        self.assertTrue(success)
        self.assertEqual(next_x, 8) # キックせずに入るはず (壁キックデータによる)
        self.assertEqual(next_y, 37) # 1段落ちて入る
        self.assertEqual(next_rot, 1) # 右向きになる

    def test_drop_piece(self):
        """ピースが正しく接地するか"""
        board = BoardSim()
        # 床を作る
        board.grid[39, :] = 1.0
        i_shape_coords = SHAPES['LineShape']['coords'][0] # 横向きIミノ

        final_y = drop_piece(board, i_shape_coords, 5, 20) # 中央上部からドロップ
        self.assertEqual(final_y, 38) # 床の1つ上に接地するはず

# --- テストの実行 ---
if __name__ == '__main__':
    unittest.main()