#pragma once

#include <vector>
#include <string>
#include <map>
#include <set>
#include <utility>
#include <tuple>
#include <queue>

// --- 定数 ---
constexpr int BOARD_WIDTH = 10;
constexpr int VISIBLE_BOARD_HEIGHT = 20;
constexpr int TOTAL_BOARD_HEIGHT = 40;
constexpr int HIDDEN_ROWS = TOTAL_BOARD_HEIGHT - VISIBLE_BOARD_HEIGHT;

// --- 型定義 ---
using Board = std::vector<std::vector<int>>;
using Coords = std::vector<std::pair<int, int>>;
using ShapeData = std::vector<Coords>;
using KickData = std::vector<std::vector<Coords>>;

// --- BFS (パスファインダー) で使う状態 ---
struct State {
    int x;
    int y;
    int rot;
    
    // visited チェック (std::set, std::map のキーとして使うため)
    bool operator<(const State& other) const {
        if (x != other.x) return x < other.x;
        if (y != other.y) return y < other.y;
        return rot < other.rot;
    }
    // (比較演算子を追加)
    bool operator==(const State& other) const {
        return x == other.x && y == other.y && rot == other.rot;
    }
};

// --- Pythonに返す「着地結果」 ---
struct LandingSpot {
    Board future_board;   // この手を打った「後」の盤面 (CNNの評価対象)
    
    // ★★★ AIがJavaに送る「キー操作のリスト」★★★
    std::vector<std::string> path; 

    // --- このムーブの「事実」 (GAの適応度関数用) ---
    int lines_cleared;    // 消したライン数
    bool is_t_spin;       // Tスピンだったか
    bool is_mini_t_spin;  // ミニTスピンだったか
};

// --- グローバルな形状・キックデータ ---
extern std::map<std::string, ShapeData> SHAPES;
extern std::map<std::string, KickData> KICK_DATA;

// --- Pythonから呼び出すメイン関数 ---
std::vector<LandingSpot> generate_all_landing_spots(
    const Board& board_state,
    const std::string& mino_name
);

// --- 内部ヘルパー関数 ---
// (前回から変更なしの関数は省略)
void initialize_all_data();
std::pair<int, int> get_spawn_position(const std::string& mino_name);
bool is_valid_position(const Board& grid, const Coords& coords, int piece_x, int piece_y);
int drop_piece(const Board& grid, const Coords& coords, int start_x, int start_y);
Board place_tetromino(const Board& grid, const Coords& coords, int piece_x, int piece_y);
std::pair<Board, int> clear_lines(const Board& grid);
const Coords& get_wall_kick_tests(const std::string& shape_name, int current_rot, int next_rot);
std::tuple<bool, int, int, int> simulate_rotation(
    const Board& grid, const std::string& shape_name, int x, int y, int rot, bool clockwise
);
std::tuple<bool, bool> check_t_spin(
    const Board& grid_before_clear, const std::string& mino_name, int piece_x, int piece_y, int rot, 
    const std::string& last_action // "ROTATE_LEFT", "MOVE_LEFT" など
);
std::vector<std::string> reconstruct_path(
    const State& end_state, 
    const std::map<State, State>& parent_map,
    const std::map<State, std::string>& action_map
);