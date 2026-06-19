package cn.ayaka.omnibattery;

public enum BatteryMode {
    BOTH("吸取+供能"),
    CHARGE_ONLY("仅供能"),
    ABSORB_ONLY("仅吸取"),
    OFF("关闭");

    private final String display;

    BatteryMode(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public BatteryMode next() {
        BatteryMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public boolean canCharge() {
        return this == BOTH || this == CHARGE_ONLY;
    }

    public boolean canAbsorb() {
        return this == BOTH || this == ABSORB_ONLY;
    }
}
