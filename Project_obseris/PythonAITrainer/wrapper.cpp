#include <pybind11/pybind11.h>
#include <pybind11/stl.h>       // ★ std::vector, std::string 変換に必須
#include <pybind11/numpy.h>    // Numpy配列を扱う

#include "tetris_simulator.h"  // 上で作成したヘッダー

namespace py = pybind11;

// --- Numpy <-> C++ Board 変換ヘルパー (変更なし) ---
Board numpy_to_board(py::array_t<float, py::array::c_style | py::array::forcecast> arr) { /* ... (前回と同じ) ... */ 
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
py::array_t<float> board_to_numpy(const Board& board) { /* ... (前回と同じ) ... */ 
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
    m.doc() = "C++ Tetris Simulator (BFS Pathfinding + Path Export)";

    // ★ LandingSpot 構造体を Python のクラスとして公開 (path を追加)
    py::class_<LandingSpot>(m, "LandingSpot")
        .def_property_readonly("future_board", [](const LandingSpot& s) {
            return board_to_numpy(s.future_board);
        })
        // ★ C++の std::vector<std::string> が Python の [str] リストに自動変換される
        .def_readonly("path", &LandingSpot::path)
        
        // --- GAの適応度関数で使うための「事実」 ---
        .def_readonly("lines_cleared", &LandingSpot::lines_cleared)
        .def_readonly("is_t_spin", &LandingSpot::is_t_spin)
        .def_readonly("is_mini_t_spin", &LandingSpot::is_mini_t_spin);

    // メインのシミュレーション関数 (変更なし)
    m.def("generate_all_landing_spots", 
        [](py::array_t<float> board_np, const std::string& mino_name) {
            Board board = numpy_to_board(board_np); 
            std::vector<LandingSpot> spots = generate_all_landing_spots(board, mino_name);
            return spots;
        },
        py::arg("board_state_np"), py::arg("mino_name")
    );
}