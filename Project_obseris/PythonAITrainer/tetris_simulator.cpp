#include "tetris_simulator.h"
#include <map>
#include <string>
#include <vector>
#include <tuple>
#include <algorithm>
#include <set>
#include <queue>

// --- グローバルデータの定義 (前回と同じ) ---
std::map<std::string, ShapeData> SHAPES;
std::map<std::string, KickData> KICK_DATA;
bool g_data_initialized = false;

// --- データ初期化 (前回と同じ) ---
void initialize_shape_data() { /* ... (前回と同じコード) ... */ 
    SHAPES["TShape"] = {
        {{-1, 0}, {0, 0}, {1, 0}, {0, -1}}, {{0, -1}, {0, 0}, {0, 1}, {1, 0}},
        {{-1, 0}, {0, 0}, {1, 0}, {0, 1}}, {{0, -1}, {0, 0}, {0, 1}, {-1, 0}}
    };
    SHAPES["ZShape"] = {
        {{-1,-1}, {0,-1}, {0, 0}, {1, 0}}, {{1,-1}, {1, 0}, {0, 0}, {0, 1}},
        {{-1, 0}, {0, 0}, {0, 1}, {1, 1}}, {{-1,1}, {-1, 0}, {0, 0}, {0,-1}}
    };
    SHAPES["SShape"] = {
        {{-1, 0}, {0, 0}, {0,-1}, {1,-1}}, {{0,-1}, {0, 0}, {1, 0}, {1, 1}},
        {{-1, 1}, {0, 1}, {0, 0}, {1, 0}}, {{-1,-1}, {-1, 0}, {0, 0}, {0, 1}}
    };
    SHAPES["LineShape"] = {
        {{-1, 0}, {0, 0}, {1, 0}, {2, 0}}, {{1,-1}, {1, 0}, {1, 1}, {1, 2}},
        {{-1, 1}, {0, 1}, {1, 1}, {2, 1}}, {{0,-1}, {0, 0}, {0, 1}, {0, 2}}
    };
    SHAPES["SquareShape"] = {
        {{0, 0}, {1, 0}, {0, 1}, {1, 1}}, {{0, 0}, {1, 0}, {0, 1}, {1, 1}},
        {{0, 0}, {1, 0}, {0, 1}, {1, 1}}, {{0, 0}, {1, 0}, {0, 1}, {1, 1}}
    };
    SHAPES["LShape"] = {
        {{-1, 0}, {0, 0}, {1, 0}, {1,-1}}, {{0,-1}, {0, 0}, {0, 1}, {1, 1}},
        {{-1, 1}, {-1, 0}, {0, 0}, {1, 0}}, {{-1,-1}, {0,-1}, {0, 0}, {0, 1}}
    };
    SHAPES["MirroredLShape"] = {
        {{-1,-1}, {-1, 0}, {0, 0}, {1, 0}}, {{1,-1}, {0,-1}, {0, 0}, {0, 1}},
        {{-1, 0}, {0, 0}, {1, 0}, {1, 1}}, {{-1, 1}, {0, 1}, {0, 0}, {0,-1}}
    };
    SHAPES["NoShape"] = { {{0,0}} };
}
void initialize_kick_data() { /* ... (前回と同じコード) ... */ 
    KICK_DATA["JLSTZ"] = {
        {{0, 0}, {-1, 0}, {-1, -1}, {0, 2}, {-1, 2}}, 
        {{0, 0}, {1, 0}, {1, 1}, {0, -2}, {1, -2}},   
        {{0, 0}, {1, 0}, {1, 1}, {0, -2}, {1, -2}},   
        {{0, 0}, {-1, 0}, {-1, -1}, {0, 2}, {-1, 2}}, 
        {{0, 0}, {1, 0}, {1, -1}, {0, 2}, {1, 2}},    
        {{0, 0}, {-1, 0}, {-1, 1}, {0, -2}, {-1, -2}},
        {{0, 0}, {1, 0}, {-2, 0}, {1, 2}, {-2, -1}},  
        {{0, 0}, {-1, 0}, {2, 0}, {-1, -2}, {2, 1}}   
    };
    KICK_DATA["I"] = {
        {{0, 0}, {-2, 0}, {1, 0}, {-2, 1}, {1, -2}},  
        {{0, 0}, {2, 0}, {-1, 0}, {2, -1}, {-1, 2}},  
        {{0, 0}, {-1, 0}, {2, 0}, {-1, -2}, {2, 1}},  
        {{0, 0}, {1, 0}, {-2, 0}, {1, 2}, {-2, -1}},  
        {{0, 0}, {2, 0}, {-1, 0}, {2, -1}, {-1, 2}},  
        {{0, 0}, {-2, 0}, {1, 0}, {-2, 1}, {1, -2}},  
        {{0, 0}, {1, 0}, {-2, 0}, {1, 2}, {-2, -1}},  
        {{0, 0}, {-1, 0}, {2, 0}, {-1, -2}, {2, 1}}   
    };
    KICK_DATA["O"] = { {{0, 0}} };
}
void initialize_all_data() {
    if (!g_data_initialized) {
        initialize_shape_data();
        initialize_kick_data();
        g_data_initialized = true;
    }
}

// --- ヘルパー関数の実装 (前回とほぼ同じ) ---

    bool is_valid_position(const Board& grid, const Coords& coords, int piece_x, int piece_y) { /* ... (前回と同じ) ... */ 
        for (const auto& p : coords) {
            int board_x = piece_x + p.first;
            int board_y = piece_y + p.second;
            if (board_x < 0 || board_x >= BOARD_WIDTH || board_y < 0 || board_y >= TOTAL_BOARD_HEIGHT) {
                return false;
            }
            if (grid[board_y][board_x] != 0) {
                return false;
            }
        }
        return true;
    }
    int drop_piece(const Board& grid, const Coords& coords, int start_x, int start_y) { /* ... (前回と同じ) ... */ 
        int y = start_y;
        while (is_valid_position(grid, coords, start_x, y + 1)) {
            y++;
        }
        return y;
    }
    Board place_tetromino(const Board& grid, const Coords& coords, int piece_x, int piece_y) { /* ... (前回と同じ) ... */ 
        Board new_grid = grid;
        for (const auto& p : coords) {
            int board_x = piece_x + p.first;
            int board_y = piece_y + p.second;
            if (board_y >= 0 && board_y < TOTAL_BOARD_HEIGHT && board_x >= 0 && board_x < BOARD_WIDTH) {
                new_grid[board_y][board_x] = 1;
            }
        }
        return new_grid;
    }
    std::pair<Board, int> clear_lines(const Board& grid) { /* ... (前回と同じ) ... */ 
        Board new_grid(TOTAL_BOARD_HEIGHT, std::vector<int>(BOARD_WIDTH, 0));
        int lines_cleared = 0;
        int new_row = TOTAL_BOARD_HEIGHT - 1;

        for (int y = TOTAL_BOARD_HEIGHT - 1; y >= 0; --y) {
            bool line_is_full = true;
            for (int x = 0; x < BOARD_WIDTH; ++x) {
                if (grid[y][x] == 0) {
                    line_is_full = false;
                    break;
                }
            }
            if (line_is_full) {
                lines_cleared++;
            } else {
                new_grid[new_row] = grid[y];
                new_row--;
            }
        }
        return {new_grid, lines_cleared};
    }
    std::pair<int, int> get_spawn_position(const std::string& mino_name) { /* ... (前回と同じ) ... */ 
        int x = (mino_name == "SquareShape") ? 4 : 3;
        const auto& coords = SHAPES.at(mino_name)[0];
        int min_y_offset = 0;
        for(const auto& p : coords) {
            if(p.second < min_y_offset) min_y_offset = p.second;
        }
        int y = HIDDEN_ROWS - min_y_offset;
        return {x, y};
    }
    const Coords& get_wall_kick_tests(const std::string& shape_name, int current_rot, int next_rot) { /* ... (前回と同じ) ... */ 
        const auto& kick_map = (shape_name == "LineShape") ? KICK_DATA.at("I") : KICK_DATA.at("JLSTZ");
    
        std::pair<int, int> key = {current_rot, next_rot};
        int idx = -1;
        if (key == std::pair<int,int>{0, 1}) idx = 0;
        else if (key == std::pair<int,int>{1, 0}) idx = 1;
        else if (key == std::pair<int,int>{1, 2}) idx = 2;
        else if (key == std::pair<int,int>{2, 1}) idx = 3;
        else if (key == std::pair<int,int>{2, 3}) idx = 4;
        else if (key == std::pair<int,int>{3, 2}) idx = 5;
        else if (key == std::pair<int,int>{3, 0}) idx = 6;
        else if (key == std::pair<int,int>{0, 3}) idx = 7;

        if (idx != -1) {
            return kick_map[idx];
        }
        static Coords no_kick = {{0, 0}};
        return no_kick;
    }
    std::tuple<bool, int, int, int> simulate_rotation(
        const Board& grid, const std::string& shape_name, int x, int y, int rot, bool clockwise
    ) { /* ... (前回と同じ) ... */ 
        if (shape_name == "SquareShape") {
            return {true, x, y, rot};
        }

        const auto& shape_rotations = SHAPES.at(shape_name);
        int next_rot = (rot + (clockwise ? 1 : 3)) % 4;
        const auto& rotated_coords = shape_rotations[next_rot];
        const auto& kick_tests = get_wall_kick_tests(shape_name, rot, next_rot);

        for (const auto& kick : kick_tests) {
            int test_x = x + kick.first;
            int test_y = y + kick.second;
            if (is_valid_position(grid, rotated_coords, test_x, test_y)) {
                return {true, test_x, test_y, next_rot};
            }
        }
        return {false, 0, 0, 0};
    }

    // ★ Tスピンチェック (last_action 文字列を受け取るよう変更)
    std::tuple<bool, bool> check_t_spin(
        const Board& grid_before_clear, const std::string& mino_name, 
        int piece_x, int piece_y, int rot, 
        const std::string& last_action 
    ) {
        bool last_move_was_rotation = (last_action == "ROTATE_LEFT" || last_action == "ROTATE_RIGHT");
        
        if (mino_name != "TShape" || !last_move_was_rotation) {
            return {false, false}; // T-Spinではない
        }
        
        // ... (Tスピンの角チェックロジックは前回と全く同じ) ...
        int corners_occupied = 0;
        Coords corner_offsets = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        Coords front_corners;

        switch (rot) {
            case 0: front_corners = {{-1, -1}, {1, -1}}; break;
            case 1: front_corners = {{1, -1}, {1, 1}}; break;
            case 2: front_corners = {{-1, 1}, {1, 1}}; break;
            case 3: front_corners = {{-1, -1}, {-1, 1}}; break;
        }

        for(const auto& offset : corner_offsets) {
            int cx = piece_x + offset.first;
            int cy = piece_y + offset.second;
            if (cx < 0 || cx >= BOARD_WIDTH || cy < 0 || cy >= TOTAL_BOARD_HEIGHT) {
                corners_occupied++;
            } else if (grid_before_clear[cy][cx] != 0) {
                corners_occupied++;
            }
        }

        if (corners_occupied < 3) return {false, false};
        
        int front_corners_occupied = 0;
        for(const auto& offset : front_corners) {
            int cx = piece_x + offset.first;
            int cy = piece_y + offset.second;
            if (cx < 0 || cx >= BOARD_WIDTH || cy < 0 || cy >= TOTAL_BOARD_HEIGHT) {
                front_corners_occupied++;
            } else if (grid_before_clear[cy][cx] != 0) {
                front_corners_occupied++;
            }
        }

        if (front_corners_occupied == 2) return {true, false}; // T-Spin (Full)
        else return {false, true}; // T-Spin Mini
    }

    // ★ 経路復元関数 (新規追加)
    std::vector<std::string> reconstruct_path(
        State current_state, // ゴール (ドロップ前の状態)
        const State& start_state,     // スタート (スポーン状態)
        const std::map<State, State>& parent_map,
        const std::map<State, std::string>& action_map
    ) {
        std::vector<std::string> path;
        while (!(current_state == start_state)) {
            path.push_back(action_map.at(current_state));
            current_state = parent_map.at(current_state);
        }
        std::reverse(path.begin(), path.end());
        return path;
    }



// --- Pythonから呼び出すメイン関数 (BFSパスファインダー + 経路記録) ---
std::vector<LandingSpot> generate_all_landing_spots(
    const Board& board_state,
    const std::string& mino_name
) {
    initialize_all_data();

    std::vector<LandingSpot> results;
    // (x, final_y, rot) が既に見つかったかを記録 (ハードドロップの重複排除)
    std::set<std::tuple<int, int, int>> found_landings;
    
    // BFS (幅優先探索) のためのデータ構造
    std::queue<State> queue;
    std::set<State> visited;
    std::map<State, State> parent_map; // Key:子, Value:親
    std::map<State, std::string> action_map; // Key:子, Value:親->子へのアクション

    const auto& shape_rotations = SHAPES.at(mino_name);
    auto [spawn_x, spawn_y] = get_spawn_position(mino_name);
    State start_state = {spawn_x, spawn_y, 0};

    if (!is_valid_position(board_state, shape_rotations[0], spawn_x, spawn_y)) {
        return results; // スポーン不可
    }
    
    queue.push(start_state);
    visited.insert(start_state);
    parent_map[start_state] = start_state; // スタートの親は自分自身(ダミー)
    action_map[start_state] = "SPAWN";

    // アクションのリスト
    const std::vector<std::string> ACTIONS = {
        "MOVE_LEFT", "MOVE_RIGHT", "SOFT_DROP", "ROTATE_LEFT", "ROTATE_RIGHT"
    };

    while (!queue.empty()) {
        State current_state = queue.front();
        queue.pop();

        const auto& current_coords = shape_rotations[current_state.rot];
        const std::string last_action = action_map.at(current_state);

        // --- 1. この (x, y, rot) 状態からハードドロップした場合を計算 ---
        int final_y = drop_piece(board_state, current_coords, current_state.x, current_state.y);
        std::tuple<int, int, int> landing_key = {current_state.x, final_y, current_state.rot};

        if (found_landings.find(landing_key) == found_landings.end()) {
            found_landings.insert(landing_key); 

            // このムーブの「事実」を計算
            Board board_after_place = place_tetromino(board_state, current_coords, current_state.x, final_y);
            
            auto [is_tspin, is_mini] = check_t_spin(
                board_after_place, mino_name, current_state.x, final_y, current_state.rot, last_action
            );
            
            auto [board_after_clear, lines] = clear_lines(board_after_place);
            
            // ★ 経路を復元し、"HARD_DROP" を追加
            std::vector<std::string> path = reconstruct_path(
                current_state, start_state, parent_map, action_map
            );
            path.push_back("HARD_DROP"); // Java側で実行する最終アクション
            
            results.push_back({
                board_after_clear, std::move(path),
                lines, is_tspin, is_mini
            });
        }

        // --- 2. この (x, y, rot) 状態から次のアクションを探索 ---
        for (const std::string& action_name : ACTIONS) {
            State next_state = current_state; // ダミー初期化
            bool success = false;
            
            if (action_name == "MOVE_LEFT") {
                int nx = current_state.x - 1;
                if (is_valid_position(board_state, current_coords, nx, current_state.y)) {
                    next_state = {nx, current_state.y, current_state.rot};
                    success = true;
                }
            } else if (action_name == "MOVE_RIGHT") {
                int nx = current_state.x + 1;
                if (is_valid_position(board_state, current_coords, nx, current_state.y)) {
                    next_state = {nx, current_state.y, current_state.rot};
                    success = true;
                }
            } else if (action_name == "SOFT_DROP") {
                int ny = current_state.y + 1;
                if (is_valid_position(board_state, current_coords, current_state.x, ny)) {
                    next_state = {current_state.x, ny, current_state.rot};
                    success = true;
                }
            } else if (action_name == "ROTATE_LEFT") {
                auto [res_s, res_x, res_y, res_rot] = simulate_rotation(
                    board_state, mino_name, current_state.x, current_state.y, current_state.rot, false
                );
                if (res_s) {
                    next_state = {res_x, res_y, res_rot};
                    success = true;
                }
            } else if (action_name == "ROTATE_RIGHT") {
                auto [res_s, res_x, res_y, res_rot] = simulate_rotation(
                    board_state, mino_name, current_state.x, current_state.y, current_state.rot, true
                );
                if (res_s) {
                    next_state = {res_x, res_y, res_rot};
                    success = true;
                }
            }

            // 訪問済みでなければキューに追加
            if (success && visited.find(next_state) == visited.end()) {
                visited.insert(next_state);
                parent_map[next_state] = current_state;
                action_map[next_state] = action_name;
                queue.push(next_state);
            }
        }
    }
    
    return results;
}