package cn.ayaka.omnibattery;

/**
 * 电池等级参数。
 * rates: 5 档传输速率 (FE/t)，ULTIMATE 最高档为无限（Long.MAX_VALUE）；
 * rangeSteps: 可选范围档位；ULTIMATE 的 -1 表示全维度。
 */
public enum BatteryTier {
    LOW("低级", 10_000_000_000L, 16, new long[]{100_000, 500_000, 1_000_000, 2_500_000, 5_000_000}, new int[]{8, 12, 16}),
    MEDIUM("中级", 100_000_000_000L, 32, new long[]{500_000, 1_000_000, 2_500_000, 5_000_000, 10_000_000}, new int[]{12, 18, 24, 32}),
    ADVANCED("高级", 1_000_000_000_000L, 96, new long[]{1_000_000, 2_500_000, 5_000_000, 10_000_000, 25_000_000}, new int[]{16, 32, 48, 64, 96}),
    ELITE("精英", 100_000_000_000_000L, 256, new long[]{2_500_000, 5_000_000, 10_000_000, 25_000_000, 50_000_000}, new int[]{32, 64, 128, 192, 256}),
    ULTIMATE("终极", Long.MAX_VALUE, 512, new long[]{50_000_000, 100_000_000, 250_000_000, 500_000_000, Long.MAX_VALUE}, new int[]{64, 128, 256, 512, -1});

    private final String display;
    private final long capacity;
    private final int defaultRange;
    private final long[] rates;
    private final int[] rangeSteps;

    BatteryTier(String display, long capacity, int defaultRange, long[] rates, int[] rangeSteps) {
        this.display = display;
        this.capacity = capacity;
        this.defaultRange = defaultRange;
        this.rates = rates;
        this.rangeSteps = rangeSteps;
    }

    public String display() { return display; }
    public long capacity() { return capacity; }

    /** Forge 能量 API 侧暴露的最大容量（int 上限）。 */
    public int forgeCapacity() { return Integer.MAX_VALUE; }

    public int defaultRange() { return defaultRange; }

    public long rate(int index) {
        return rates[Math.max(0, Math.min(index, rates.length - 1))];
    }

    public long[] rates() { return rates; }
    public int[] rangeSteps() { return rangeSteps; }
    public boolean isUltimate() { return this == ULTIMATE; }
}
