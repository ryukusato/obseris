import subprocess
import json
import sys
import time
import select

class TetrisEnv:
    """
    Javaで実行されているテトリス環境と通信するためのPythonラッパー。
    C++ AI からの「高レベル命令」を仲介する。
    """
    def __init__(self, java_path, jar_path, main_class='org.yourcompany.yourproject.ai.TrainingEnvironment'):
        self.java_path = java_path
        self.jar_path = jar_path
        self.main_class = main_class
        self.proc = None

    def _start_java_process(self):
        """Javaプロセスを起動します。"""
        # 実行するコマンドをリストとして作成
        command = [self.java_path, '-cp', self.jar_path, self.main_class]
        
        # --- ▼▼▼ デバッグ出力追加 ▼▼▼ ---
        print(f"[DEBUG] Executing Java command: {' '.join(command)}", file=sys.stderr) 
        # --- ▲▲▲ デバッグ出力追加 ▲▲▲ ---
        
        self.proc = subprocess.Popen(
            command, # <--- 変数 command を使用
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,# <--- Javaのエラー出力を受け取るように変更
            text=True,
            encoding='utf-8',
            bufsize=1
        )
        
        # --- ▼▼▼ エラー出力表示を追加 ▼▼▼ ---
        # Javaプロセスがすぐに終了していないか、エラーを出力していないか確認
        try:
            # 短時間待機してエラー出力を読み取る (ノンブロッキング)
            stderr_output = self.proc.stderr.read() 
            if stderr_output:
                print(f"[DEBUG] Java stderr output:\n{stderr_output}", file=sys.stderr)
        except Exception as e:
             print(f"[DEBUG] Error reading stderr: {e}", file=sys.stderr)
        # --- ▲▲▲ エラー出力表示を追加 ▲▲▲ ---



    def reset(self):
        if self.proc:
            try:
                self.proc.stdin.close()
                self.proc.terminate()
                self.proc.wait(timeout=2)
            except Exception as e:
                print(f"Javaプロセスの終了中にエラー: {e}", file=sys.stderr)

        print('ok')
        self._start_java_process()

        print('ok')
        # --- ▼▼▼ 標準出力/エラー出力の読み取り処理を変更 ▼▼▼ ---
        initial_state_line = None
        stderr_output = ""
        start_time = time.time()
        timeout_seconds = 5 # 5秒待っても応答がなければタイムアウト

        try:
            while time.time() - start_time < timeout_seconds:
                # selectを使って標準出力/エラー出力が読み取り可能かチェック
                ready_to_read, _, _ = select.select([self.proc.stdout, self.proc.stderr], [], [], 0.1)

                if self.proc.stderr in ready_to_read:
                    line = self.proc.stderr.readline()
                    if line:
                        stderr_output += line
                    else: # ストリームが閉じられた
                        break

                if self.proc.stdout in ready_to_read:
                    initial_state_line = self.proc.stdout.readline()
                    if initial_state_line:
                        # 最初の行を読み取れたらループを抜ける
                        break 
                    else: # ストリームが閉じられた
                         break

                if self.proc.poll() is not None: # プロセスが終了していたらループを抜ける
                    break

                # CPU負荷軽減のため少し待機
                # time.sleep(0.01) # 不要かもしれない

            # タイムアウト後にもう一度エラー出力を読み取る試み
            try:
                 stderr_output += self.proc.stderr.read()
            except:
                 pass

            if stderr_output:
                print(f"[DEBUG] Java stderr output during reset:\n{stderr_output}", file=sys.stderr)

            if not initial_state_line:
                raise Exception(f"Javaプロセスから初期状態を受信できませんでした (タイムアウト: {timeout_seconds}秒)。")

            initial_state = json.loads(initial_state_line)
            return initial_state

        except Exception as e:
            print(f"リセット失敗: {e}", file=sys.stderr)
             # プロセスがまだ生きていれば終了させる
            if self.proc and self.proc.poll() is None:
                try:
                    self.proc.terminate()
                    self.proc.wait(timeout=1)
                except:
                    pass
            return None
        # --- ▲▲▲ 標準出力/エラー出力の読み取り処理を変更 ▲▲▲ ---

   
    def step(self, agent_command):
        """
        AI (C++) が決定した「高レベル命令」を実行し、(state, done, info) を返します。
        :param agent_command: {'x': 4, 'rot': 1, 'useHold': False} といった辞書
        """
        if self.proc.poll() is not None:
            return None, True, {"error": "Java process terminated."}

        try:
            # 1. JavaにAIの「高レベル命令」をJSONで送信
            action_json = json.dumps(agent_command)
            self.proc.stdin.write(action_json + '\n')
            self.proc.stdin.flush()

            # 2. Javaから「1手進んだ」ゲーム状態をJSONで受信
            response_line = self.proc.stdout.readline()
            if not response_line:
                return None, True, {"error": "No response from Java."}
            
            state = json.loads(response_line)
            
            # 3. 完了フラグをチェック
            done = state['isGameOver']
            
            # 報酬(reward)は、この環境では計算しません。
            # 学習は「最終スコア」で行うためです。
            
            return state, done, {}

        except Exception as e:
            return None, True, {"error": str(e)}
        
    def close(self):
        if self.proc:
            try:
                self.proc.stdin.close()
                self.proc.terminate()
            except Exception as e:
                print(f"終了エラー: {e}")