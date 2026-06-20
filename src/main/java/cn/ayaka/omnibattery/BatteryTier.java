package cn.ayaka.omnibattery;

public enum BatteryTier {
    LOW("低级", 10_000_000_000L, 16, new int[]{1_000_000, 5_000_000, 10_000_000, 25_000_000, 50_000_000}, new int[]{8, 12, 16}),
    MEDIUM("中级", 100_000_000_000L, 32, new int[]{5_000_000, 10_000_000, 25_000_000, 50_000_000, 100_000_000}, new int[]{12, 18, 24, 32}),
    ADVANCED("高级", 1_000_000_000_000L, 96, new int[]{10_000_000, 50_000_000, 100_000_000, 250_000_000, 500_000_000}, new int[]{16, 32, 48, 64, 96}),
    ELITE("精英", 100_000_000_000_000L, 256, new int[]{50_000_000, 100_000_000, 250_000_000, 500_000_000, 1_000_000_000}, new int[]{32, 64, 128, 192, 256}),
    ULTIMATE("终极", Long.MAX_VALUE, 512, new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, new int[]{64, 128, 256, 512, -1});

    private final String display;
    private final long capacity;
    private final int defaultRange;
    private final int[] rates;
    private final int[] rangeSteps;

    BatteryTier(String display, long capacity, int defaultRange, int[] rates, int[] rangeSteps) {
        this.display = display;
        this.capacity = capacity;
        this.defaultRange = defaultRange;
        this.rates = rates;
        this.rangeSteps = rangeSteps;
    }

    public String display() { return display; }
    public long capacity() { return capacity; }
    public int forgeCapacity() { return Integer.MAX_VALUE; }
    public int defaultRange() { return defaultRange; }

    public int rate(int index) {
        int i = Math.max(0, Math.min(index, rates.length - 1));
        return rates[i];
    }

    public int[] rates() { return rates; }
    public int[] rangeSteps() { return rangeSteps; }
    public boolean isUltimate() { return this == ULTIMATE; }
}
