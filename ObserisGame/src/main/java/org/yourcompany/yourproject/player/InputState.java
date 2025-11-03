package org.yourcompany.yourproject.player;
public class InputState {
    public boolean left = false;
    public boolean right = false;
    public boolean rotateLeft = false;
    public boolean rotateRight = false;
    public boolean softDrop = false;
    public boolean hardDrop = false;
    public boolean hold = false;

    public void reset() {
        left = false;
        right = false;
        rotateLeft = false;
        rotateRight = false;
        softDrop = false;
        hardDrop = false;
        hold = false;
    }

}