package org.yourcompany.yourproject.config;
public class ClearInfo {
    private final String clearType;
    private final int linesCleared;
    private final SpinType spinType;
    private final boolean isB2B;
    private final int comboCount;
    private final boolean isPerfectClear;

    public ClearInfo(String clearType, int linesCleared, SpinType spinType, boolean isB2B, int comboCount, boolean isPerfectClear) {
        this.clearType = clearType;
        this.linesCleared = linesCleared;
        this.spinType = spinType;
        this.isB2B = isB2B;
        this.comboCount = comboCount;
        this.isPerfectClear = isPerfectClear;
    }

    public String getClearType() { return clearType; }
    public int getLinesCleared() { return linesCleared; }
    public SpinType getSpinType() { return spinType; }
    public boolean isB2B() { return isB2B; }
    public int getComboCount() { return comboCount; }
    public boolean isPerfectClear() { return isPerfectClear; }
}