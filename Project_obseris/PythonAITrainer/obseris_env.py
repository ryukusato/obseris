import subprocess
import json
import numpy as np

class TetrisEnv:
    """
    Javaで実行されているテトリス環境と通信するためのPythonラッパー。
    C++ AI からの「高レベル命令」を仲介する。
    """
    def __init__(self, java_path, jar_path, main_class='TrainingEnvironment'):
        self.java_path = java_path
        self.jar_path = jar_path
        self.main_class = main_class
        self.proc = None

    def _start_java_process(self):
        """Javaプロセスを起動します。"""
        self.proc = subprocess.Popen(
            [self.java_path, '-cp', self.jar_path, self.main_class],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            bufsize=1
        )

    def reset(self):
        """
        環境をリセットします。
        Javaプロセスを再起動します。
        """
        if self.proc:
            try:
                self.proc.stdin.close()
                self.proc.terminate()
                self.proc.wait(timeout=2)
            except Exception as e:
                print(f"Javaプロセスの終了中にエラー: {e}")
            
        self._start_java_process()
        
        # Java側が起動時に初期状態をJSONで送ってくるのを待つ
        try:
            initial_state_line = self.proc.stdout.readline()
            if not initial_state_line:
                raise Exception("Javaプロセスから初期状態を受信できませんでした。")
            
            initial_state = json.loads(initial_state_line)
            return initial_state
        
        except Exception as e:
            print(f"リセット失敗: {e}")
            return None

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