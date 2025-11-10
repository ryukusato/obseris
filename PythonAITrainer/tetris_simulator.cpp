#include "tetris_simulator.h"
#include <map>
#include <string>
#include <vector>
#include <tuple>
#include <algorithm>
#include <set>
#include <queue>
#include <random>
#include <chrono>
#include <thread>
#include <functional>
#include <iterator>
#include <iostream>
// --- グローバルデータの定義 (前回と同じ) ---
std::map<std::string, ShapeData> SHAPES;
std::map<std::string, KickData> KICK_DATA;
bool g_data_initialized = false;

const std::vector<std::string> SHAPE_NAMES = {
    "TShape", "ZShape", "SShape", "LineShape", 
    "SquareShape", "LShape", "MirroredLShape"
};


void initialize_shape_data() { 
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
void initialize_kick_data() {
    KICK_DATA["JLSTZ"] = {
        {{0, 0}, {-1, 0}, {-1, -1}, {0, 2}, {-1, 2}}, 
        {{0, 0}, {1, 0}, {1, 1}, {0, -2}, {1, -2}},   
        {{0, 0}, {1, 0}, {1, 1}, {0, -2}, {1, -2}},   
        {{0, 0}, {-1, 0}, {-1, -1}, {0, 2}, {-1, 2}}, 
        {{0, 0}, {1, 0}, {1, -1}, {0, 2}, {1, 2}},    
        {{0, 0}, {-1, 0}, {-1, 1}, {0, -2}, {-1, -2}},
        {{0, 0}, {-1, 0}, {-1, 1}, {0, -2}, {-1, -2}},  
        {{0, 0}, {1, 0}, {1, -1}, {0, 2}, {1, 2}}   
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

// =================================================================
// --- GameState クラスの実装 ---
// =================================================================
// ★ RNG初期化関数 (get_rng() の中身をここに移動)
void GameState::initialize_rng() {
    auto now_ns = std::chrono::high_resolution_clock::now().time_since_epoch().count();
    std::thread::id thread_id = std::this_thread::get_id(); // このスレッド(プロセス)のID
    std::size_t thread_hash = std::hash<std::thread::id>{}(thread_id);

    // PythonのspawnではプロセスIDも異なるため、よりユニークなシードとしてPIDも加える（推奨）
    // (Windows/Linux共通で取得する方法は少し複雑なため、ここではthread_hashと時刻のみで十分機能します)
    
    std::seed_seq seq{
        static_cast<unsigned int>(now_ns & 0xFFFFFFFF),
        static_cast<unsigned int>(now_ns >> 32),
        static_cast<unsigned int>(thread_hash & 0xFFFFFFFF),
        static_cast<unsigned int>(thread_hash >> 32),
    };
    
    this->m_rng = std::mt19937(seq);
}

GameState::GameState() {
    initialize_all_data();
    initialize_rng();
    reset();
}


void GameState::reset() {
    grid.assign(TOTAL_BOARD_HEIGHT, std::vector<int>(BOARD_WIDTH, 0));
    holdTetromino = "NoShape";
    currentTetromino = "NoShape";
    
    // キューをクリア
    std::queue<std::string> empty_queue;
    std::swap(nextShapesQueue, empty_queue);
    nextQueueView.clear();

    canHold = true;
    score = 0;
    isGameOver = false;
    isB2BActive = false;
    comboCount = -1; // Javaロジック: -1 がコンボなし
    pendingGarbage = 0;

    // ★ 7-bagを2回充填 (Javaロジック)
    fillNextShapesQueue();
    fillNextShapesQueue();

    // ★ ネクストキュー(5個)を初期化
    for (int i = 0; i < 5; i++) {
        nextQueueView.push_back(createNewPieceFromQueue());
    }

    spawnNewTetromino();
}

// --- Python公開関数 ---

std::vector<LandingSpot> GameState::get_all_possible_moves() {
    std::vector<LandingSpot> all_moves;
    if (isGameOver) {
        return all_moves;
    }

    // 1. 現行ミノでの手をすべて計算
    std::vector<LandingSpot> current_piece_moves = generate_moves_for_piece(this->currentTetromino, false);
    all_moves.insert(all_moves.end(), current_piece_moves.begin(), current_piece_moves.end());

    // 2. ホールド可能な場合、ホールドした後の手をすべて計算
    if (this->canHold) {
        std::string piece_to_check;
        if (this->holdTetromino == "NoShape") {
            // ホールドが空 -> 次のミノ(ネクストの先頭)で計算
            piece_to_check = this->nextQueueView[0];
        } else {
            // ホールドに既存 -> ホールドミノで計算
            piece_to_check = this->holdTetromino;
        }
        
        std::vector<LandingSpot> hold_moves = generate_moves_for_piece(piece_to_check, true);
        all_moves.insert(all_moves.end(), hold_moves.begin(), hold_moves.end());
    }
    
    return all_moves;
}

void GameState::execute_move(const LandingSpot& move) {
    if (isGameOver) return;
    
    // 1. ゲームオーバー判定
    if (move.is_game_over) {
        this->isGameOver = true;
        return;
    }

    // 2. この手による状態の「結果」をコミット
    this->grid = move.future_board;
    this->score += move.score_delta;
    this->comboCount = move.combo_count_after;
    this->isB2BActive = move.b2b_active_after;
    this->pendingGarbage = move.pending_garbage_after;

    // 3. ホールドの処理
    if (move.used_hold) {
        this->canHold = false; // ホールドは1回まで
        if (this->holdTetromino == "NoShape") {
            // 初回ホールド (current -> hold, next[0] -> current)
            this->holdTetromino = this->currentTetromino;
            this->currentTetromino = "NoShape";
            // spawnNewTetromino が nextQueueView[0] を使うので、ここで currentTetromino を設定する必要はない
        } else {
            // ホールド交換 (current <-> hold)
            std::string temp = this->currentTetromino;
            this->currentTetromino = this->holdTetromino;
            this->holdTetromino = temp;
        }
    } else {
        // ホールドしなかった -> 次の手でホールド可能
        this->canHold = true;
    }

    if (this->pendingGarbage > 0) {
        bool garbageGameOver = applyGarbage();
        if (garbageGameOver) {
            this->isGameOver = true;
            return; // お邪魔によってゲームオーバー
        }
    }

    // 4. 次のミノをスポーンさせる
    // (ホールドした場合、currentTetrominoは既に設定済みか、
    //  初回ホールドなら spawnNewTetromino が next[0] を使う)
    spawnNewTetromino();
}

// --- ゲッター ---

std::vector<std::string> GameState::get_next_queue() const {
    return this->nextQueueView;
}

py::bytes board_to_bytes(const Board& board) {
    // 盤面 (40x10) を平らな配列 (400要素) にコピー
    std::vector<int> flat_board;
    flat_board.reserve(TOTAL_BOARD_HEIGHT * BOARD_WIDTH);
    for (int y = 0; y < TOTAL_BOARD_HEIGHT; ++y) {
        if (y < (int)board.size() && board[y].size() == BOARD_WIDTH) {
            flat_board.insert(flat_board.end(), board[y].begin(), board[y].end());
        } else {
            // 壊れた盤面の場合、ゼロで埋める
            std::vector<int> zeros(BOARD_WIDTH, 0);
            flat_board.insert(flat_board.end(), zeros.begin(), zeros.end());
        }
    }
    
    // C++の int ベクターを Python の bytes オブジェクトに変換
    return py::bytes(
        reinterpret_cast<const char*>(flat_board.data()), 
        flat_board.size() * sizeof(int)
    );
}
py::bytes GameState::get_board_bytes() const {
    return board_to_bytes(this->grid);
}
py::bytes GameState::get_next_queue_bytes(int offset) const {
    // ["TShape", "ZShape", ...] を [0, 1, ...] の整数インデックスに変換
    std::vector<int> queue_indices;
    queue_indices.reserve(5);

    // C++側の SHAPE_NAMES を使ってインデックスを検索
    // (Python側の SHAPE_TO_INDEX と同じ役割)
    std::map<std::string, int> shape_to_index;
    for (size_t i = 0; i < SHAPE_NAMES.size(); ++i) {
        shape_to_index[SHAPE_NAMES[i]] = static_cast<int>(i);
    }
    // "NoShape" やその他も定義
    shape_to_index["NoShape"] = -1; // -1 や 7 などマッピング外の数値

    for (int i = 0; i < 5; ++i) {
        int queue_index = i + offset; // 例: offset=1 -> [1, 2, 3, 4, 5]
        
        std::string name = "NoShape";
        bool find = false;

        if (queue_index >= 0 && queue_index < this->nextQueueView.size()) {
            // ★ ケース1: [0]～[4]番目のミノは nextQueueView から取得
            name = this->nextQueueView[queue_index];
            find = true;
        } 
        else if (queue_index == this->nextQueueView.size()) {
            // ★ ケース2: [5]番目のミノ (index 5) が要求された場合
            // nextQueueView の次、つまり nextShapesQueue の先頭がそれにあたる
            if (!this->nextShapesQueue.empty()) {
                name = this->nextShapesQueue.front();
                find = true;
            }
        }

        if (find && shape_to_index.count(name)) {
            queue_indices.push_back(shape_to_index.at(name));
        } else {
            queue_indices.push_back(shape_to_index.count(name) ? shape_to_index.at(name) : -1);
        }
    }
    
    // int32_t のベクトルを bytes に変換
    return py::bytes(
        reinterpret_cast<const char*>(queue_indices.data()),
        queue_indices.size() * sizeof(int)
    );
}


// --- 内部ヘルパー (BFS) ---

// tetris_simulator.cpp (または .h) の L290

/**
 * @brief (★ 修正版) 4次元BFSによる全着地点の探索
 * * 空中状態 (is_grounded=false) と接地状態 (is_grounded=true) を
 * シームレスに探索 (BFS) し、タッキング (接地後の移動/回転) を含む
 * 「すべての接地状態」を LandingSpot としてリストアップする。
 */
std::vector<LandingSpot> GameState::generate_moves_for_piece(const std::string& mino_name, bool is_hold_move) {
    
    std::vector<LandingSpot> results;
    
    // (★) 訪問済みセット (x, y, rot, is_grounded)
    std::set<State> visited;
    
    // (★) BFS (幅優先探索) のためのデータ構造
    std::queue<State> queue;
    std::map<State, State> parent_map; // Key:子, Value:親
    std::map<State, std::string> action_map; // Key:子, Value:親->子へのアクション
    std::set<std::tuple<int, int, int>> found_landings;

    if (SHAPES.find(mino_name) == SHAPES.end()) {
        throw std::runtime_error("Invalid mino name in generate_moves: " + mino_name);
    }
    const auto& shape_rotations = SHAPES.at(mino_name);
    
    // (★) スタート状態は「空中」(is_grounded = false)
    auto [spawn_x, spawn_y] = get_spawn_position(mino_name); // y=20 が入る
    int start_rot = 0;
    const auto& start_coords = shape_rotations[start_rot];

    if (!is_valid_position(start_coords, spawn_x, spawn_y)) {
        spawn_y--;
        if (!is_valid_position(start_coords, spawn_x, spawn_y)) {
        LandingSpot game_over_spot = {};
        game_over_spot.is_game_over = true;
        game_over_spot.used_hold = is_hold_move;
        game_over_spot.mino_name = mino_name;
        game_over_spot.future_board_bytes = board_to_bytes(this->grid);
        if (is_hold_move && this->holdTetromino == "NoShape") {
            game_over_spot.future_next_queue_bytes = get_next_queue_bytes(1);
        } else {
            game_over_spot.future_next_queue_bytes = get_next_queue_bytes(0);
        }
        results.push_back(game_over_spot);
        return results;
    }
    }
    State start_state = {spawn_x, spawn_y, start_rot, false};
    queue.push(start_state);
    visited.insert(start_state);
    parent_map[start_state] = start_state; 
    action_map[start_state] = "SPAWN";

    // (★) 探索するアクション (元のまま)
    const std::vector<std::string> ACTIONS = {
        "MOVE_LEFT", "MOVE_RIGHT", "SOFT_DROP", "ROTATE_LEFT", "ROTATE_RIGHT"
    };


    while (!queue.empty()) {
        State current_state = queue.front();
        queue.pop();

        const auto& current_coords = shape_rotations[current_state.rot];
        const std::string last_action = action_map.at(current_state);
        // --- 1. (★) この状態が「接地状態」なら、結果 (LandingSpot) に追加 ---
        // (元の drop_piece(...) / found_landings ロジックはすべて削除)
        if (current_state.is_grounded) {
            LandingSpot spot = calculate_landing_result(
                this->grid, mino_name,
                current_state.x, current_state.y, current_state.rot,
                last_action, is_hold_move
            );
            
            try {
                // (★) パスを復元
                std::tie(spot.path, spot.used_soft_drop) = reconstruct_path(
                    current_state, start_state, parent_map, action_map
                );
            } catch (const std::exception& e) {
                spot.path = {"ERROR_PATH"};
                spot.used_soft_drop = false;
            }
            results.push_back(spot);
        }
        if (!current_state.is_grounded) {
            int final_y = drop_piece(current_coords, current_state.x, current_state.y);
            std::tuple<int, int, int> landing_key = {current_state.x, final_y, current_state.rot};

            if (found_landings.find(landing_key) == found_landings.end()) { 
                found_landings.insert(landing_key); 

                LandingSpot spot = calculate_landing_result(
                    this->grid, mino_name,
                    current_state.x, final_y, current_state.rot, // (x, finalY)
                    last_action, is_hold_move
                );
                
                try {
                    std::tie(spot.path, spot.used_soft_drop) = reconstruct_path(
                        current_state, start_state, parent_map, action_map
                    );
                } catch (...) {
                    spot.path = {"ERROR_PATH"};
                    spot.used_soft_drop = false;
                }
                results.push_back(spot);
            }
        }


        // --- 2. (★) この状態から次のアクションを探索 ---
        
        for (const std::string& action_name : ACTIONS) {
            
            // (★) 接地中 (is_grounded) は SOFT_DROP をスキップ
            if (current_state.is_grounded && action_name == "SOFT_DROP") {
                continue;
            }

            State next_state = current_state; 
            bool success = false;
            
            if (action_name == "MOVE_LEFT") {
                int nx = current_state.x - 1;
                if (is_valid_position(current_coords, nx, current_state.y)) {
                    // (★) is_grounded は後でチェックするため、ここではまだ仮のまま
                    next_state = {nx, current_state.y, current_state.rot, current_state.is_grounded};
                    success = true;
                }
            } else if (action_name == "MOVE_RIGHT") {
                int nx = current_state.x + 1;
                if (is_valid_position(current_coords, nx, current_state.y)) {
                    next_state = {nx, current_state.y, current_state.rot, current_state.is_grounded};
                    success = true;
                }
            } else if (action_name == "SOFT_DROP") {
                // (★) このアクションは is_grounded == false の時のみ試行される
                int ny = current_state.y + 1;
                if (is_valid_position(current_coords, current_state.x, ny)) {
                    next_state = {current_state.x, ny, current_state.rot, false}; // まだ空中
                    success = true;
                }
            } else if (action_name == "ROTATE_LEFT") {
                auto [res_s, res_x, res_y, res_rot] = simulate_rotation(
                    mino_name, current_state.x, current_state.y, current_state.rot, false
                );
                if (res_s) {
                    next_state = {res_x, res_y, res_rot, current_state.is_grounded};
                    success = true;
                }
            } else if (action_name == "ROTATE_RIGHT") {
                auto [res_s, res_x, res_y, res_rot] = simulate_rotation(
                    mino_name, current_state.x, current_state.y, current_state.rot, true
                );
                if (res_s) {
                    next_state = {res_x, res_y, res_rot, current_state.is_grounded};
                    success = true;
                }
            }
            // (★) L352-L387 ロジックここまで

            if (!success) continue; // このアクションは失敗

            // --- 3. (★) 接地状態の「遷移」をチェック (ロジックの核) ---
            
            const auto& next_coords = shape_rotations[next_state.rot];
            
            // (★) アクション実行後の「次の状態」は、接地しているか？
            // (注: SOFT_DROP で (y+1) に移動した結果、接地することもある)
            bool is_now_grounded = !is_valid_position(next_coords, next_state.x, next_state.y + 1);
            
            // (★) 最終的な「次の状態」を決定
            next_state.is_grounded = is_now_grounded;


            // --- 4. (★) 訪問済みかチェックし、キューに追加 ---
            if (visited.find(next_state) == visited.end()) {
                visited.insert(next_state);
                parent_map[next_state] = current_state;
                action_map[next_state] = action_name;
                
                // (★) 接地状態 (タッキングの途中) であっても、
                // (★) 空中状態 (次のSOFT_DROP) であっても、
                // (★) 探索を続けるためにキューに追加する
                queue.push(next_state);
            }
        }
    } 

    return results;
}

// ★★★ Javaの placeAndStartDelay のロジックを移植 ★★★
LandingSpot GameState::calculate_landing_result(
    const Board& board_before_place,
    const std::string& mino_name,
    int final_x, int final_y, int final_rot,
    const std::string& last_action,
    bool is_hold_move
) {
    LandingSpot spot = {}; // ゼロ初期化
    spot.mino_name = mino_name;
    spot.final_x = final_x;
    spot.final_y = final_y;
    spot.final_rot = final_rot;
    spot.used_hold = is_hold_move;
    spot.future_board = board_before_place;
    spot.future_board_bytes = board_to_bytes(board_before_place);

    if (is_hold_move && this->holdTetromino == "NoShape") {
        // "空のHold" を使った場合 -> キューが1つズレる
        spot.future_next_queue_bytes = get_next_queue_bytes(1); // offset = 1
    } else {
        // それ以外 (Holdしない / Hold交換) の場合 -> 現在のキューと同じ
        spot.future_next_queue_bytes = get_next_queue_bytes(0); // offset = 0
    }

    const auto& coords = SHAPES.at(mino_name)[final_rot];

    // 1. ロックアウト (Game Over) 判定
    if (isLockedOut(coords, final_y)) {
        spot.is_game_over = true;
        return spot;
    }
    
    // 2. ミノを置く
    Board board_after_place = place_tetromino(coords, final_x, final_y);

    // 3. Tスピン判定 (ライン消去「前」のボードと最終位置で行う)
    spot.spin_type = check_t_spin(board_after_place, mino_name, final_x, final_y, final_rot, last_action);

    // 4. ライン消去
    auto [board_after_clear, lines] = clear_lines(board_after_place);
    spot.lines_cleared = lines;
    spot.future_board = board_after_clear;
    spot.future_board_bytes = board_to_bytes(board_after_clear); // ★ 結果の盤面を保存

    // 5. 評価 (Javaロジック)
    bool isDifficultClear = (spot.spin_type != SpinType::NONE) || (spot.lines_cleared == 4);

    if (spot.lines_cleared > 0) {
        spot.combo_count_after = this->comboCount + 1;
        bool b2bBonusApplied = this->isB2BActive && isDifficultClear;
        
        // 5a. 火力計算
        int base_attack = calculateAttack(spot.lines_cleared, spot.spin_type, b2bBonusApplied, spot.combo_count_after);

        // 5b. お邪魔の相殺 (Java: offsetGarbage)
        int attack_to_send = 0;
        int pending_garbage_after_move = this->pendingGarbage;
        
        if (base_attack > 0) {
            int remaining_garbage = pending_garbage_after_move - base_attack;
            if (remaining_garbage < 0) {
                attack_to_send = -remaining_garbage; // 相手に送る火力
                pending_garbage_after_move = 0;      // 残りお邪魔は0
            } else {
                pending_garbage_after_move = remaining_garbage; // お邪魔が残る
                attack_to_send = 0;                             // 火力はすべて相殺
            }
        }
        spot.attack_power = attack_to_send;
        spot.pending_garbage_after = pending_garbage_after_move;

        // 5c. スコア計算
        long long baseScore = calculateScore(spot.lines_cleared, spot.spin_type);
        if (b2bBonusApplied) baseScore = static_cast<long long>(baseScore * 1.5);
        long long comboBonus = (spot.combo_count_after > 0) ? 50LL * spot.combo_count_after : 0;
        spot.score_delta = baseScore + comboBonus;
        
        // 5d. B2B状態の更新
        spot.b2b_active_after = isDifficultClear;

        // 5e. パーフェクトクリア (ボーナスはJava側で処理)
        // (ここでは attackPower に 10 を追加するロジックを移植)
        bool isPerfectClear = true;
        for (int y = 0; y < TOTAL_BOARD_HEIGHT; ++y) {
            for (int x = 0; x < BOARD_WIDTH; ++x) {
                if (board_after_clear[y][x] != 0) {
                    isPerfectClear = false;
                    break;
                }
            }
            if (!isPerfectClear) break;
        }
        if (isPerfectClear) {
            spot.attack_power += 10;
            spot.score_delta += 3000; // Javaのロジック
        }

    } else {
        // ライン消去なし
        spot.combo_count_after = -1; // コンボリセット
        spot.b2b_active_after = this->isB2BActive; // B2Bは維持
        spot.attack_power = 0;
        spot.pending_garbage_after = this->pendingGarbage; // お邪魔はそのまま
        spot.score_delta = 0;
    }
    
    // 6. ハードドロップ/ソフトドロップのスコア (ここでは単純化)
    //    AIは主に火力や盤面評価で学習するため、
    //    `generate_moves_for_piece` でのドロップによる 1pt/2pt の加算は省略。
    //    (必要なら `reconstruct_path` の結果から計算可能)

    return spot;
}

void GameState::add_pending_garbage(int amount) {
    if (amount <= 0) return;
    this->pendingGarbage += amount;
}

// --- 内部ヘルパー (Javaロジック移植) ---

void GameState::spawnNewTetromino() {
    
    // 2. ホールドを使った/使わなかった場合のミノの決定
    if (!canHold) {
        if (this->currentTetromino == "NoShape") {
            // "空のホールド" だったので、ネクストから取る
            this->currentTetromino = this->nextQueueView[0];
            this->nextQueueView.erase(this->nextQueueView.begin());
            this->nextQueueView.push_back(createNewPieceFromQueue());
        } else {
            // "ホールド交換" だったので、currentTetromino は execute_move で設定済み
        }
    } else {
        // ホールドしなかった -> ネクストの先頭
        this->currentTetromino = this->nextQueueView[0];
        this->nextQueueView.erase(this->nextQueueView.begin());
        this->nextQueueView.push_back(createNewPieceFromQueue());
    }

    // 3. スポーン位置の決定 (Java: resetPositionAndState)
    auto [spawn_x, spawn_y] = get_spawn_position(this->currentTetromino);
    const auto& coords = SHAPES.at(this->currentTetromino)[0];
    
    // 4. スポーン位置で即ゲームオーバーかチェック
    if (!is_valid_position(coords, spawn_x, spawn_y)) {
        isGameOver = true;
    }
}

bool GameState::applyGarbage() {
    if (this->pendingGarbage <= 0) return false;
    
    int lineCount = std::min(this->pendingGarbage, 10);
    this->pendingGarbage -= lineCount;

    // 1. せり上がりでブロックが盤面外に押し出されるかチェック (Java: Board.java)
    for (int y = 0; y < lineCount; y++) {
        for (int x = 0; x < BOARD_WIDTH; x++) {
            if (grid[y][x] != 0) {
                return true; // ゲームオーバー
            }
        }
    }

    // 2. 既存の行を上にずらす
    for (int y = 0; y < TOTAL_BOARD_HEIGHT - lineCount; y++) {
        grid[y] = grid[y + lineCount];
    }

    
    
    std::uniform_int_distribution<> distrib(0, BOARD_WIDTH - 1);
    int holePosition = distrib(this ->m_rng);

    for (int y = TOTAL_BOARD_HEIGHT - lineCount; y < TOTAL_BOARD_HEIGHT; y++) {
        std::vector<int> garbageLine(BOARD_WIDTH, 1); // 1 = 灰色ブロック
        garbageLine[holePosition] = 0; // 0 = 穴
        grid[y] = garbageLine;
    }
    return false; // ゲームオーバーではない
}

void GameState::fillNextShapesQueue() {
    std::vector<std::string> shapes = SHAPE_NAMES;
    std::shuffle(shapes.begin(), shapes.end(), this ->m_rng);
    for (const auto& shape : shapes) {
        nextShapesQueue.push(shape);
    }
}

std::string GameState::createNewPieceFromQueue() {
    if (nextShapesQueue.size() <= 7) {
        fillNextShapesQueue();
    }
    std::string shape = nextShapesQueue.front();
    nextShapesQueue.pop();
    return shape;
}

// Java: isLockedOut
bool GameState::isLockedOut(const Coords& coords, int piece_y) const {
    for (const auto& p : coords) {
        // VISIBLE_BOARD_HEIGHT (20) の「上」= 隠し行 (hiddenRows)
        // 隠し行 (0-19) に Y があれば OK
        if (piece_y + p.second >= HIDDEN_ROWS) { 
            return false; // 可視領域またはそのすぐ上
        }
    }
    return true; // 全ブロックが可視領域より上 (ロックアウト)
}

// Java: calculateAttack
int GameState::calculateAttack(int linesCleared, SpinType spinType, bool isB2B, int combo) const {
    int attack = 0;
    if (spinType == SpinType::T_SPIN) {
        switch (linesCleared) {
            case 1: attack = 2; break;
            case 2: attack = 4; break; // Java 4
            case 3: attack = 6; break; // Java 6
            default: attack = 0;
        }
    } else if (spinType == SpinType::T_SPIN_MINI) {
        attack = (linesCleared == 2) ? 2 : 1;
    } else {
        switch (linesCleared) {
            case 1: attack = 0; break;
            case 2: attack = 1; break;
            case 3: attack = 2; break;
            case 4: attack = 4; break;
        }
    }
    if (isB2B && attack > 0) attack += 1; // B2Bボーナス
    
    // コンボボーナス (Javaの配列を移植)
    if (combo >= 1) {
        int comboBonus[] = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 4, 5};
        int bonus = (combo < 12) ? comboBonus[combo] : 5;
        attack += bonus;
    }
    return attack;
}

// Java: calculateScore
long long GameState::calculateScore(int linesCleared, SpinType spinType) const {
    switch (spinType) {
        case SpinType::T_SPIN:
            switch (linesCleared) {
                case 1: return 800;
                case 2: return 1200;
                case 3: return 1600;
                default: return 400; // T-Spin 0 line
            }
        case SpinType::T_SPIN_MINI:
            switch (linesCleared) {
                case 1: return 200;
                case 2: return 400; // T-Spin Mini Double
                default: return 100; // T-Spin Mini 0 line
            }
        case SpinType::NONE:
            switch (linesCleared) {
                case 1: return 100; // Single
                case 2: return 300; // Double
                case 3: return 500; // Triple
                case 4: return 800; // Quad
                default: return 0;  // No lines
            }
    }
    return 0;
}

// --- 既存ヘルパー (クラスメソッド化) ---

std::pair<int, int> GameState::get_spawn_position(const std::string& mino_name) const {
    int x = 4;
    const auto& coords = SHAPES.at(mino_name)[0];
    int min_y_offset = 0;
    for(const auto& p : coords) {
        if(p.second < min_y_offset) min_y_offset = p.second;
    }
    int y = HIDDEN_ROWS;
    return {x, y};
}

bool GameState::is_valid_position(const Coords& coords, int piece_x, int piece_y) const {
    for (const auto& p : coords) {
        int board_x = piece_x + p.first;
        int board_y = piece_y + p.second;
        if (board_x < 0 || board_x >= BOARD_WIDTH || board_y < 0 || board_y >= TOTAL_BOARD_HEIGHT) {
            return false;
        }
        if (this->grid[board_y][board_x] != 0) {
            return false;
        }
    }
    return true;
}

int GameState::drop_piece(const Coords& coords, int start_x, int start_y) const {
    int y = start_y;
    while (is_valid_position(coords, start_x, y + 1)) {
        y++;
    }
    return y;
}

Board GameState::place_tetromino(const Coords& coords, int piece_x, int piece_y) const {
    Board new_grid = this->grid;
    for (const auto& p : coords) {
        int board_x = piece_x + p.first;
        int board_y = piece_y + p.second;
        if (board_y >= 0 && board_y < TOTAL_BOARD_HEIGHT && board_x >= 0 && board_x < BOARD_WIDTH) {
            new_grid[board_y][board_x] = 1; // 1 = 置かれたブロック
        }
    }

    return new_grid;
}

std::pair<Board, int> GameState::clear_lines(const Board& board_to_clear) const {
    Board new_grid(TOTAL_BOARD_HEIGHT, std::vector<int>(BOARD_WIDTH, 0));
    int lines_cleared = 0;
    int new_row = TOTAL_BOARD_HEIGHT - 1;

    for (int y = TOTAL_BOARD_HEIGHT - 1; y >= 0; --y) {
        if (y >= board_to_clear.size()) {
            continue; 
       }

        bool line_is_full = true;
        for (int x = 0; x < BOARD_WIDTH; ++x) {
            if (x >= board_to_clear[y].size()) {
                line_is_full = false;
                break;
            }
            if (board_to_clear[y][x] == 0) {
                line_is_full = false;
                break;
            }
        }
        if (line_is_full) {
            lines_cleared++;
        } else {
            if (new_row >= 0) { // 盤面外への書き込みを防ぐ
                 new_grid[new_row] = board_to_clear[y];
            }
            new_row--;
        }
    }
    return {new_grid, lines_cleared};
}

const Coords& GameState::get_wall_kick_tests(const std::string& shape_name, int current_rot, int next_rot) const {
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

    if (idx != -1) return kick_map.at(idx);
    static Coords no_kick = {{0, 0}};
    return no_kick;
}

std::tuple<bool, int, int, int> GameState::simulate_rotation(
    const std::string& shape_name, int x, int y, int rot, bool clockwise
) const {
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
        if (is_valid_position(rotated_coords, test_x, test_y)) {
            return {true, test_x, test_y, next_rot};
        }
    }
    return {false, 0, 0, 0};
}

// ★ Tスピンチェック (Javaとロジックを統一済み)
SpinType GameState::check_t_spin(
    const Board& grid_before_clear, const std::string& mino_name, 
    int piece_x, int piece_y, int rot, 
    const std::string& last_action 
) const {
    bool last_move_was_rotation = (last_action == "ROTATE_LEFT" || last_action == "ROTATE_RIGHT");
    
    if (mino_name != "TShape" || !last_move_was_rotation) {
        return SpinType::NONE;
    }
    
    // 1. 4隅のチェック
    int corners_occupied = 0;
    Coords corner_offsets = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
    Coords front_corners;

    switch (rot) {
        case 0: front_corners = {{-1, -1}, {1, -1}}; break; // 上
        case 1: front_corners = {{1, -1}, {1, 1}}; break; // 右
        case 2: front_corners = {{-1, 1}, {1, 1}}; break; // 下
        case 3: front_corners = {{-1, -1}, {-1, 1}}; break; // 左
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

    if (corners_occupied < 3) return SpinType::NONE;
    
    // 2. 前面2隅のチェック
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

    if (front_corners_occupied == 2) return SpinType::T_SPIN; // T-Spin (Full)
    else return SpinType::T_SPIN_MINI; // T-Spin Mini
}

// ★ 経路復元関数 (変更なし)
std::pair<std::vector<std::string>, bool> GameState::reconstruct_path(
    State current_state,
    const State& start_state,
    const std::map<State, State>& parent_map,
    const std::map<State, std::string>& action_map
) const {
    std::vector<std::string> path;
    bool used_soft_drop = false;
    if (action_map.find(current_state) == action_map.end()) {
        // スタート状態に到達できない場合 (通常は発生しない)
        return {{"ERROR_PATH"}, false};
    }

    while (!(current_state == start_state)) {
        path.push_back(action_map.at(current_state));
        if (action_map.at(current_state) == "SOFT_DROP") {
            used_soft_drop = true;
        }
        current_state = parent_map.at(current_state);
    }
    std::reverse(path.begin(), path.end());
    return {path, used_soft_drop};
}