from setuptools import setup, Extension
import pybind11


extra_compile_args = ['-std=c++17','-pthread','-O3']
ext_modules = [
    Extension(
        # 1. Pythonでのモジュール名 (PYBIND11_MODULEと一致させる)
        'tetris_simulator', 
        [
            # 2. リンクするソースファイル (実装とバインディングの両方)
            'tetris_simulator.cpp', # 実装ファイル
            'wrapper.cpp'          # バインディングファイル
        ],
        include_dirs=[
            # pybind11 のヘッダーファイルへのパス
            pybind11.get_include(),
        ],
        language='c++',
        extra_compile_args=extra_compile_args, # C++17 と最適化を有効
    ),
]

setup(
    name='tetris_simulator',
    version='1.1.0',
    description='C++ Tetris Simulator for AI Training(onePlay)',
    ext_modules=ext_modules,
    # ビルドに pybind11 が必要であることを指定
    setup_requires=['pybind11>=2.6'],
)