package cn.ayaka.omnibattery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class BatteryData {
    private BatteryData() {}

    public static final String ENERGY = "OmniEnergy";
    public static final String RANGE = "OmniRange";
    public static final String RATE = "OmniRate";
    public static final String MODE = "OmniMode";
    public static final String WORK = "OmniWorking";

    public static long getEnergy(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0L;
        if (!tag.contains(ENERGY)) return 0L;
        return Math.max(0L, tag.getLong(ENERGY));
    }

    public static void setEnergy(ItemStack stack, long energy, BatteryTier tier) {
        stack.getOrCreateTag().putLong(ENERGY, Math.max(0L, Math.min(tier.capacity(), energy)));
    }

    public static int getRateIndex(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0;
        return Math.max(0, Math.min(4, tag.getInt(RATE)));
    }

    public static void setRateIndex(ItemStack stack, int index) {
        stack.getOrCreateTag().putInt(RATE, Math.max(0, Math.min(4, index)));
    }

    public static int cycleRate(ItemStack stack) {
        int next = (getRateIndex(stack) + 1) % 5;
        setRateIndex(stack, next);
        return next;
    }

    public static BatteryMode getMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return BatteryMode.BOTH;
        int id = tag.getInt(MODE);
        BatteryMode[] modes = BatteryMode.values();
        if (id < 0 || id >= modes.length) return BatteryMode.BOTH;
        return modes[id];
    }

    public static void setMode(ItemStack stack, BatteryMode mode) {
        stack.getOrCreateTag().putInt(MODE, mode.ordinal());
    }

    public static BatteryMode cycleMode(ItemStack stack) {
        BatteryMode next = getMode(stack).next();
        setMode(stack, next);
        return next;
    }

    public static int getRange(ItemStack stack, BatteryTier tier) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(RANGE)) return tier.defaultRange();
        int range = tag.getInt(RANGE);
        if (tier.isUltimate() && range < 0) return -1;
        return Math.max(1, range);
    }

    public static void setRange(ItemStack stack, BatteryTier tier, int range) {
        if (tier.isUltimate() && range < 0) {
            stack.getOrCreateTag().putInt(RANGE, -1);
            return;
        }
        int max = Math.max(1, tier.rangeSteps()[tier.rangeSteps().length - 1]);
        stack.getOrCreateTag().putInt(RANGE, Math.max(1, Math.min(max, range)));
    }

    public static int cycleRange(ItemStack stack, BatteryTier tier, boolean reverse) {
        int[] steps = tier.rangeSteps();
        int current = getRange(stack, tier);
        int idx = 0;
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] == current) { idx = i; break; }
        }
        idx = reverse ? (idx - 1 + steps.length) % steps.length : (idx + 1) % steps.length;
        setRange(stack, tier, steps[idx]);
        return steps[idx];
    }

    public static boolean isWorking(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(WORK) || tag.getBoolean(WORK);
    }

    public static void setWorking(ItemStack stack, boolean working) {
        stack.getOrCreateTag().putBoolean(WORK, working);
    }

    public static int clampToForgeInt(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }
}
