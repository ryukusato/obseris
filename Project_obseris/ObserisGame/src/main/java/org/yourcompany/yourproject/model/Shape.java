package org.yourcompany.yourproject.model;
import java.awt.Color;
import java.util.List;

public final class Shape {
    public enum Tetrominoes {
        NoShape(new int[][] {{0,0},{0,0},{0,0},{0,0}},
                new int[][] {{0,0},{0,0},{0,0},{0,0}},
                new int[][] {{0,0},{0,0},{0,0},{0,0}},
                new int[][] {{0,0},{0,0},{0,0},{0,0}},
                 new Color(0,0,0)
                 ),

        // ★ Tミノの初期形状を上向きに修正済み
        TShape(
            new int[][]{{-1,0},{0,0},{1,0},{0,-1}},  // 0: 上向き
            new int[][]{{0,-1},{0,0},{0,1},{1,0}},   // 1: 右向き
            new int[][]{{-1,0},{0,0},{1,0},{0,1}},   // 2: 下向き
            new int[][]{{0,-1},{0,0},{0,1},{-1,0}},  // 3: 左向き
            new Color(128, 0, 128)
        ),
        // ★ 他すべてのミノに4方向の形状を追加
        ZShape(
            new int[][]{{-1,-1},{0,-1},{0,0},{1,0}},   // 0
            new int[][]{{1,-1},{1,0},{0,0},{0,1}},     // 1
            new int[][]{{-1,0},{0,0},{0,1},{1,1}},     // 2
            new int[][]{{-1,1},{-1,0},{0,0},{0,-1}},   // 3
            new Color(255, 0, 0)
        ),
        SShape(
            new int[][]{{-1,0},{0,0},{0,-1},{1,-1}},   // 0
            new int[][]{{0,-1},{0,0},{1,0},{1,1}},     // 1
            new int[][]{{-1,1},{0,1},{0,0},{1,0}},     // 2
            new int[][]{{-1,-1},{-1,0},{0,0},{0,1}},   // 3
            new Color(0, 255, 0)
        ),
        LineShape(
            new int[][]{{-1,0},{0,0},{1,0},{2,0}},     // 0
            new int[][]{{1,-1},{1,0},{1,1},{1,2}},     // 1
            new int[][]{{-1,1},{0,1},{1,1},{2,1}},     // 2
            new int[][]{{0,-1},{0,0},{0,1},{0,2}},     // 3
            new Color(0, 255, 255)
        ),
        SquareShape(
            new int[][]{{0,0},{1,0},{0,1},{1,1}},      // 0
            new int[][]{{0,0},{1,0},{0,1},{1,1}},      // 1
            new int[][]{{0,0},{1,0},{0,1},{1,1}},      // 2
            new int[][]{{0,0},{1,0},{0,1},{1,1}},      // 3
            new Color(255, 255, 0)
        ),
        LShape(
            new int[][]{{-1,0},{0,0},{1,0},{1,-1}},    // 0
            new int[][]{{0,-1},{0,0},{0,1},{1,1}},     // 1
            new int[][]{{-1,1},{-1,0},{0,0},{1,0}},    // 2
            new int[][]{{-1,-1},{0,-1},{0,0},{0,1}},   // 3
            new Color(255,165,0)
        ),
        MirroredLShape( // J-Shape
            new int[][]{{-1,-1},{-1,0},{0,0},{1,0}},   // 0
            new int[][]{{1,-1},{0,-1},{0,0},{0,1}},    // 1
            new int[][]{{-1,0},{0,0},{1,0},{1,1}},     // 2
            new int[][]{{-1,1},{0,1},{0,0},{0,-1}},    // 3
            new Color(0, 0, 255)
        );


        public final List<int[][]> allCoords;
        public final Color color;
        public final int[][] coordsTemplate; // 初期形状を保持


        Tetrominoes(int[][] c0, int[][] c1, int[][] c2, int[][] c3, Color color) {
            this.allCoords = List.of(c0, c1, c2, c3);
            this.coordsTemplate = c0;
            this.color = color;
        }
    }
}