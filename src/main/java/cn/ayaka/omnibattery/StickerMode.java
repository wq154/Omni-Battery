package cn.ayaka.omnibattery;

public enum StickerMode {
    SUPPLY("供电", "Supply"),
    ABSORB("吸电", "Absorb"),
    OVERLOAD("过载", "Overload"),
    CLEAR("清除", "Clear");

    private final String displayZh;
    private final String displayEn;

    StickerMode(String displayZh, String displayEn) {
        this.displayZh = displayZh;
        this.displayEn = displayEn;
    }

    public String displayZh() { return displayZh; }
    public String displayEn() { return displayEn; }

    public StickerMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean isActiveTransferMode() {
        return this == SUPPLY || this == ABSORB || this == OVERLOAD;
    }
}
