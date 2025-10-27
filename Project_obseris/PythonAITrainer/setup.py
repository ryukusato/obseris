from setuptools import setup, Extension
import pybind11

# コンパイラに C++17 を使うよう指示
cpp_args = ['-std=c++17']

ext_modules = [
    Extension(
        'tetris_simulator', # Pythonでのモジュール名
        [
            'tetris_simulator.cpp',
            'wrapper.cpp'
        ],
        include_dirs=[pybind11.get_include()],
        language='c++',
        extra_compile_args=cpp_args,
    ),
]

setup(
    name='tetris_simulator',
    version='0.3', # バージョンアップ (経路探索対応)
    description='Tetris simulator C++ module with BFS pathfinding and path export',
    ext_modules=ext_modules,
)