#include <pybind11/pybind11.h>
#include <pybind11/stl.h>       // std::vector, std::string, std::map 変換
#include <pybind11/numpy.h>    // Numpy配列

#include "tetris_simulator.h"  // 上で作成したヘッダー

namespace py = pybind11;

// --- Numpy <-> C++ Board 変換ヘルパー ---
// (int 0/1 を float 0.0/1.0 に変換)
Board numpy_to_board(py::array_t<float, py::array::c_style | py::array::forcecast> arr) {
    if (arr.ndim() != 2 || arr.shape(0) != TOTAL_BOARD_HEIGHT || arr.shape(1) != BOARD_WIDTH) {
        throw std::runtime_error("Numpy array must have shape (40, 10)");
    }
    Board board(TOTAL_BOARD_HEIGHT, std::vector<int>(BOARD_WIDTH));
    auto r = arr.unchecked<2>();
    for (ssize_t i = 0; i < TOTAL_BOARD_HEIGHT; i++) {
        for (ssize_t j = 0; j < BOARD_WIDTH; j++) {
            board[i][j] = static_cast<int>(r(i, j));
        }
    }
    return board;
}

py::array_t<float> board_to_numpy(const Board& board) {
    py::array_t<float> arr({TOTAL_BOARD_HEIGHT, BOARD_WIDTH});
    auto r = arr.mutable_unchecked<2>();
    for (ssize_t i = 0; i < TOTAL_BOARD_HEIGHT; i++) {
        for (ssize_t j = 0; j < BOARD_WIDTH; j++) {
            r(i, j) = static_cast<float>(board[i][j]);
        }
    }
    return arr;
}

// --- Pythonモジュールの定義 ---
PYBIND11_MODULE(tetris_simulator, m) {
    m.doc() = "C++ Tetris GameState Simulator for AI Training";

    // ★ SpinType Enum をPythonに公開
    py::enum_<SpinType>(m, "SpinType")
        .value("NONE", SpinType::NONE)
        .value("T_SPIN", SpinType::T_SPIN)
        .value("T_SPIN_MINI", SpinType::T_SPIN_MINI)
        .export_values();

    // ★ 拡張された LandingSpot 構造体を Python のクラスとして公開
    py::class_<LandingSpot>(m, "LandingSpot")
        .def_readonly("future_board_bytes",&LandingSpot::future_board_bytes)
    
        .def_readonly("path", &LandingSpot::path)
        .def_readonly("lines_cleared", &LandingSpot::lines_cleared)
        .def_readonly("spin_type", &LandingSpot::spin_type)
        
        // ★★★ AI評価用の追加指標 ★★★
        .def_readonly("score_delta", &LandingSpot::score_delta)
        .def_readonly("attack_power", &LandingSpot::attack_power)
        .def_readonly("pending_garbage_after", &LandingSpot::pending_garbage_after)
        .def_readonly("combo_count_after", &LandingSpot::combo_count_after)
        .def_readonly("b2b_active_after", &LandingSpot::b2b_active_after)
        .def_readonly("is_game_over", &LandingSpot::is_game_over)
        .def_readonly("used_hold", &LandingSpot::used_hold)
        .def_readonly("future_next_queue_bytes", &LandingSpot::future_next_queue_bytes)
        .def_readonly("used_soft_drop", &LandingSpot::used_soft_drop,
            "True if the path to this landing spot used SOFT_DROP")
        
        // (デバッグ用)
        .def_readonly("final_x", &LandingSpot::final_x)
        .def_readonly("final_y", &LandingSpot::final_y)
        .def_readonly("final_rot", &LandingSpot::final_rot)
        .def_readonly("mino_name", &LandingSpot::mino_name);


    // ★★★ GameState クラスを Python に公開 ★★★
    py::class_<GameState>(m, "GameState")
        .def(py::init<>()) // コンストラクタ
        .def("reset", &GameState::reset, "Resets the game to the initial state.")
        .def("get_all_possible_moves", &GameState::get_all_possible_moves,
             "Returns a list of all possible LandingSpots from the current state (including hold).")
        .def("execute_move", &GameState::execute_move, 
             "Applies the chosen LandingSpot to the game state and spawns the next piece.", py::arg("move"))
        .def("add_pending_garbage", &GameState::add_pending_garbage,
            "Adds incoming attack (garbage) from the opponent.", py::arg("amount"))
        .def("get_next_queue_bytes", &GameState::get_next_queue_bytes, 
                "Gets the next queue bytes, with an optional offset.", 
                py::arg("offset") = 0)
        // --- ゲッター ---
        .def_property_readonly("board_bytes", &GameState::get_board_bytes,
            "Returns the current board as bytes (40x10 * int32).")
        .def_property_readonly("next_queue_bytes", &GameState::get_next_queue_bytes,
            "Returns the next 5 queue indices as bytes (5 * int32).")
        .def_property_readonly("current_piece", &GameState::get_current_piece)
        .def_property_readonly("hold_piece", &GameState::get_hold_piece)
        .def_property_readonly("next_queue", &GameState::get_next_queue, "Returns the next 5 pieces.")
        .def_property_readonly("is_game_over", &GameState::is_game_over)
        .def_property_readonly("score", &GameState::get_score)
        .def_property_readonly("combo_count", &GameState::get_combo_count)
        .def_property_readonly("is_b2b_active", &GameState::is_b2b_active)
        .def_property_readonly("pending_garbage", &GameState::get_pending_garbage);

    // ★ (旧)グローバル関数 - 互換性のため残すか、削除するか
    // ※ 新しい GameState クラスを使うことを推奨するため、コメントアウト
    /*
    m.def("generate_all_landing_spots", 
        [](py::array_t<float> board_np, const std::string& mino_name) {
            // ... (古い実装はもはや GameState のコンテキストなしでは不十分)
            // この関数は非推奨とする
        },
        py::arg("board_state_np"), py::arg("mino_name")
    );
    */
}