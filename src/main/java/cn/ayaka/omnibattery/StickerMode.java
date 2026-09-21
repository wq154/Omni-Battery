package cn.ayaka.omnibattery;

public enum StickerMode {
    SUPPLY("供电", "Supply"),
    ABSORB("吸电", "Absorb"),
    OVERLOAD("过载", "Overload"),
    // 注意：**新常量只能追加到末尾**。插在中间会让所有 ordinal 偏移，
    // 旧存档里已存的 mode 序号会被解析成别的模式（曾导致机器被误当自定义而损坏）。
    CLEAR("清除", "Clear"),
    CUSTOM("自定义", "Custom");

    private final String displayZh;
    private final String displayEn;

    StickerMode(String displayZh, String displayEn) {
        this.displayZh = displayZh;
        this.displayEn = displayEn;
    }

    public String displayZh() { return displayZh; }
    public String displayEn() { return displayEn; }

    /** 循环顺序（按界面显示顺序，与 ordinal 声明顺序无关）：供电→吸电→过载→自定义→清除。 */
    public StickerMode next() {
        return switch (this) {
            case SUPPLY -> ABSORB;
            case ABSORB -> OVERLOAD;
            case OVERLOAD -> CUSTOM;
            case CUSTOM -> CLEAR;
            case CLEAR -> SUPPLY;
        };
    }

    public boolean isActiveTransferMode() {
        return this == SUPPLY || this == ABSORB || this == OVERLOAD || this == CUSTOM;
    }
}
