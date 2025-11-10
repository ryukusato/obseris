#pragma once

#include <vector>
#include <string>
#include <map>
#include <set>
#include <utility> // std::pair
#include <tuple>
#include <queue>
#include <memory> // std::shared_ptr
#include <random>
#include <pybind11/pybind11.h>
#include <pybind11/pytypes.h>
namespace py = pybind11;
// --- 定数 ---
constexpr int BOARD_WIDTH = 10;
constexpr int VISIBLE_BOARD_HEIGHT = 20;
constexpr int TOTAL_BOARD_HEIGHT = 40;
constexpr int HIDDEN_ROWS = TOTAL_BOARD_HEIGHT - VISIBLE_BOARD_HEIGHT;

// --- 型定義 ---
using Board = std::vector<std::vector<int>>;
using Coords = std::vector<std::pair<int, int>>;
using ShapeData = std::vector<Coords>;
using KickData = std::vector<Coords>;

// ★ JavaのSpinTypeに対応
enum class SpinType { NONE, T_SPIN, T_SPIN_MINI };

// --- BFS (パスファインダー) で使う状態 ---
struct State {
    int x;
    int y;
    int rot;
    bool is_grounded;
    // visited チェック (std::set, std::map のキーとして使うため)
    bool operator<(const State& other) const {
        if (x != other.x) return x < other.x;
        if (y != other.y) return y < other.y;
        return rot < other.rot;
        return is_grounded < other.is_grounded;
    }
    bool operator==(const State& other) const {
        return x == other.x && y == other.y && rot == other.rot && is_grounded == other.is_grounded;
    }
};

// ★★★ AIが評価するために拡張された「着地結果」 ★★★
struct LandingSpot {
    // --- 実行後の状態 ---
    Board future_board;
    py::bytes future_board_bytes; // この手を打った「後」の盤面 (ライン消去済み)
    py::bytes future_next_queue_bytes;
    // --- AIがJavaに送る「キー操作のリスト」---
    std::vector<std::string> path; 

    // --- このムーブの「事実」 (GAやRLの適応度関数用) ---
    int lines_cleared;       // 消したライン数
    SpinType spin_type;      // T-Spin, Mini, None
    
    // --- ★ JavaのGameLogicから移植した評価指標 ★ ---
    long long score_delta;          // この手で得られるスコア
    int attack_power;        // この手で「相手に送る」火力 (お邪魔相殺後)
    int pending_garbage_after; // この手を打った後の残りお邪魔
    int combo_count_after;     // この手を打った後のコンボ数 (-1でリセット)
    bool b2b_active_after;     // この手を打った後のB2B状態
    bool is_game_over;         // この手でゲームオーバーになるか
    bool used_hold;            // この手が「ホールド」を使った手か
    bool used_soft_drop;       // この手が「ソフトドロップ」を使った手か
    
    // --- 内部計算用 (デバッグや経路復元に使う) ---
    int final_x;
    int final_y;
    int final_rot;
    std::string mino_name;     // この手で使ったミノ
};


// ★★★ JavaのGameLogicに相当する「ゲーム状態」クラス ★★★
class GameState {
public:
    // --- コンストラクタ / リセット ---
    GameState();
    void reset();

    // --- Pythonから呼ぶメインの関数群 ---
    std::vector<LandingSpot> get_all_possible_moves();
    void execute_move(const LandingSpot& move);
    void add_pending_garbage(int amount);
    // --- ゲッター (Python公開用) ---
    py::bytes get_board_bytes() const;
    py::bytes get_next_queue_bytes(int offset = 0) const;
    Board get_board() const { return grid; }
    std::string get_current_piece() const { return currentTetromino; }
    std::string get_hold_piece() const { return holdTetromino; }
    std::vector<std::string> get_next_queue() const;
    bool is_game_over() const { return isGameOver; }
    long long get_score() const { return score; }
    int get_combo_count() const { return comboCount; }
    bool is_b2b_active() const { return isB2BActive; }
    int get_pending_garbage() const { return pendingGarbage; }


private:
    // --- JavaのGameLogicから移植した「状態」 ---
    Board grid;
    std::string currentTetromino;
    std::string holdTetromino;
    std::queue<std::string> nextShapesQueue; // 7-bag (内部データ)
    std::vector<std::string> nextQueueView;   // 5-bag (表示用)
    bool canHold;
    
    long long score;
    bool isB2BActive;
    int comboCount; // Javaに合わせて「-1」を「コンボなし」とする
    int pendingGarbage;
    bool isGameOver;

    // --- 内部ヘルパー (BFS) ---
    std::vector<LandingSpot> generate_moves_for_piece(const std::string& mino_name, bool is_hold_move);
    
    // ★ 内部ヘルパー (評価)
    LandingSpot calculate_landing_result(
        const Board& board_before_place,
        const std::string& mino_name,
        int final_x, int final_y, int final_rot,
        const std::string& last_action,
        bool is_hold_move
    );
    
    // ★ 内部ヘルパー (Javaロジック移植) ---
    std::mt19937 m_rng;
    void initialize_rng();
    void spawnNewTetromino();
    bool applyGarbage();
    void fillNextShapesQueue();
    std::string createNewPieceFromQueue();
    bool isLockedOut(const Coords& coords, int piece_y) const;
    int calculateAttack(int linesCleared, SpinType spinType, bool isB2B, int combo) const;
    long long calculateScore(int linesCleared, SpinType spinType) const;
    
    // --- 既存のヘルパー関数 (クラスのprivateメソッドに変更) ---
    bool is_valid_position(const Coords& coords, int piece_x, int piece_y) const;
    int drop_piece(const Coords& coords, int start_x, int start_y) const;
    Board place_tetromino(const Coords& coords, int piece_x, int piece_y) const;
    std::pair<Board, int> clear_lines(const Board& board_to_clear) const;
    std::pair<int, int> get_spawn_position(const std::string& mino_name) const;
    SpinType check_t_spin(
        const Board& grid_before_clear, const std::string& mino_name, 
        int piece_x, int piece_y, int rot, 
        const std::string& last_action 
    ) const;
    std::tuple<bool, int, int, int> simulate_rotation(
        const std::string& shape_name, int x, int y, int rot, bool clockwise
    ) const;
    const Coords& get_wall_kick_tests(const std::string& shape_name, int current_rot, int next_rot) const;
    std::pair<std::vector<std::string>, bool> reconstruct_path(
        State current_state,
        const State& start_state,
        const std::map<State, State>& parent_map,
        const std::map<State, std::string>& action_map
    ) const;
};

// --- グローバルな形状・キックデータ (外部公開不要) ---
void initialize_all_data();