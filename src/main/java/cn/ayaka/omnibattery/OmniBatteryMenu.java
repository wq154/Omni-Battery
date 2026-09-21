package cn.ayaka.omnibattery;

import cn.ayaka.omnibattery.registry.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class OmniBatteryMenu extends AbstractContainerMenu {
    // ---------------- 用电配置页：目标机器同步 ----------------
    public static final int TARGET_COUNT = 6;

    /** 主界面"切换电池"按钮 id。 */
    public static final int SWITCH_BATTERY = 800;
    private static final int TARGET_BASE = 255;
    /** 电池坐标的数据槽（服务端每 tick 写入；客户端据此查找客户端 BE 的同步列表）。 */
    private static final int POS_SLOT = TARGET_BASE + TARGET_COUNT * 4 + 8;
    private static final int EMPTY_SLOT = Integer.MIN_VALUE;


    // 0 energy low, 1 energy high, 2 capacity low, 3 capacity high, 4 tier, 5 mode, 6 rate, 7 range, 8 public access
    private final ContainerData data;
    private final OmniBatteryBlockEntity blockEntity;

    public OmniBatteryMenu(int id, Inventory inv, OmniBatteryBlockEntity be) {
        super(ModMenuTypes.OMNI_BATTERY.get(), id);
        this.blockEntity = be;
        this.data = new SimpleContainerData(15 + OmniBatteryBlockEntity.HISTORY_SIZE * 4 + TARGET_COUNT * 4 + 11);
        addDataSlots(data);
    }

    public OmniBatteryMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, inv.player.level().getBlockEntity(buf.readBlockPos()) instanceof OmniBatteryBlockEntity be ? be : null);
    }

    @Override public boolean stillValid(Player player) { return blockEntity != null && !blockEntity.isRemoved(); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public void broadcastChanges() {
        // 先更新 data slots，再交给 super 发送（顺序反了客户端会慢一帧）
        if (blockEntity != null) {
            syncLong(0, blockEntity.getEnergy());
            syncLong(2, blockEntity.getTier().capacity());
            data.set(4, blockEntity.getTier().ordinal());
            data.set(5, blockEntity.getMode().ordinal());
            data.set(6, blockEntity.getRateIndex());
            data.set(7, blockEntity.getRange());
            data.set(8, blockEntity.getAccess().ordinal());
            // 实时速率：absorbed 占 9-10，supplied 占 11-12
            syncLong(9, blockEntity.getAbsorbedPerSecond());
            syncLong(11, blockEntity.getSuppliedPerSecond());
            data.set(13, blockEntity.isChargeInventory() ? 1 : 0);
            data.set(14, blockEntity.isChargeCurios() ? 1 : 0);
            // 电池坐标（供客户端查找客户端 BE 上的同步列表）
            net.minecraft.core.BlockPos bp = blockEntity.getBlockPos();
            data.set(POS_SLOT, bp.getX());
            data.set(POS_SLOT + 1, bp.getY());
            data.set(POS_SLOT + 2, bp.getZ());
            // 趋势图历史：每点 4 个 int slot（absorb long + supply long）
            for (int i = 0; i < OmniBatteryBlockEntity.HISTORY_SIZE; i++) {
                syncLong(15 + i * 4, blockEntity.getAbsorbHistory(i));
                syncLong(15 + i * 4 + 2, blockEntity.getSupplyHistory(i));
            }
        }
        super.broadcastChanges();
                // 用电配置页：同步本维度已打标签的机器（坐标 + 模式）
            java.util.List<net.minecraft.core.BlockPos> cfgTargets =
                    blockEntity.stickerTargetsHere(TARGET_COUNT);
            for (int ti = 0; ti < TARGET_COUNT; ti++) {
                int b = TARGET_BASE + ti * 4;
                if (ti < cfgTargets.size()) {
                    net.minecraft.core.BlockPos tp = cfgTargets.get(ti);
                    data.set(b, tp.getX());
                    data.set(b + 1, tp.getY());
                    data.set(b + 2, tp.getZ());
                    data.set(b + 3, blockEntity.targetModeOrdinal(tp));
                } else {
                    data.set(b, EMPTY_SLOT);
                    data.set(b + 1, EMPTY_SLOT);
                    data.set(b + 2, EMPTY_SLOT);
                    data.set(b + 3, EMPTY_SLOT);
                }
            }
}

    private void syncLong(int index, long value) {
        data.set(index, (int) (value & 0xFFFFFFFFL));
        data.set(index + 1, (int) ((value >>> 32) & 0xFFFFFFFFL));
    }

    private long readLong(int index) {
        return (Integer.toUnsignedLong(data.get(index + 1)) << 32) | Integer.toUnsignedLong(data.get(index));
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (blockEntity == null) return false;
        // 不能在这里自动认领（否则点一下按钮就能夺走别人的电池）
        if (!blockEntity.canManage(player)) return false;
        switch (button) {
            case 0 -> blockEntity.setMode(blockEntity.getMode().next());
            case 1 -> blockEntity.setRateIndex(Math.min(4, blockEntity.getRateIndex() + 1));
            case 2 -> blockEntity.setRateIndex(Math.max(0, blockEntity.getRateIndex() - 1));
            case 3 -> blockEntity.setRange(cycleRange(blockEntity.getRange(), blockEntity.getTier(), false));
            case 4 -> blockEntity.setRange(cycleRange(blockEntity.getRange(), blockEntity.getTier(), true));
            case 5 -> {
                blockEntity.setAccess(blockEntity.getAccess().next());
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "电池权限：" + blockEntity.getAccess().display()), true);
            }
            case 6 -> blockEntity.setChargeInventory(!blockEntity.isChargeInventory());
            case 7 -> blockEntity.setChargeCurios(!blockEntity.isChargeCurios());
            default -> {
                if (button == SWITCH_BATTERY) {
                    // 切换电池：切到标签工具绑定的下一块电池并打开它。
                    // 推迟到下一 tick —— 在菜单回调里直接 openMenu 会破坏容器状态导致服务端崩溃。
                    if (player instanceof net.minecraft.server.level.ServerPlayer sp2) {
                        sp2.server.execute(() -> cn.ayaka.omnibattery.network.OpenBoundBatteryPacket
                                .openFor(sp2, true, -2));
                    }
                    return true;
                }
                if (button >= 400 && button < 4000) {
                    // 直接指定模式：option 0 吸电 / 1 供电 / 2 过载 / 3 自定义 / 4 清除
                    return applyTargetOption((button - 400) / 5, (button - 400) % 5, player);
                } return false; }
        }
        broadcastChanges();
        return true;
    }

    private int cycleRange(int current, BatteryTier tier, boolean reverse) {
        int[] steps = tier.rangeSteps();
        int idx = 0;
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] == current) { idx = i; break; }
        }
        idx = reverse ? (idx - 1 + steps.length) % steps.length : (idx + 1) % steps.length;
        return steps[idx];
    }

    public long getEnergy() { return readLong(0); }
    public long getMaxEnergy() { return readLong(2); }
    public long getAbsorbedPerSecond() { return readLong(9); }
    public long getSuppliedPerSecond() { return readLong(11); }
    public int getHistorySize() { return OmniBatteryBlockEntity.HISTORY_SIZE; }
    public long getAbsorbHistory(int i) { return readLong(15 + i * 4); }
    public long getSupplyHistory(int i) { return readLong(15 + i * 4 + 2); }
    public boolean isChargeInventory() { return data.get(13) != 0; }
    public boolean isChargeCurios() { return data.get(14) != 0; }

    /** 完整数字（千分位），不加 K/M/B/T 缩写。 */
    public static String fmt(long v) {
        if (v == Long.MAX_VALUE) return "∞";
        return String.format("%,d", v);
    }

    /** 简写数字：K/M/B/T 单位 + 两位小数（用于位数极长的显示）。 */
    public static String fmtShort(long v) {
        if (v >= 1_000_000_000_000L) return String.format("%.2fT", v / 1e12);
        if (v >= 1_000_000_000L) return String.format("%.2fB", v / 1e9);
        if (v >= 1_000_000L) return String.format("%.2fM", v / 1e6);
        if (v >= 1_000L) return String.format("%.2fK", v / 1e3);
        return String.valueOf(v);
    }
    public BatteryTier getTier() { return BatteryTier.values()[Math.max(0, Math.min(data.get(4), BatteryTier.values().length - 1))]; }
    public BatteryMode getMode() { return BatteryMode.values()[Math.max(0, Math.min(data.get(5), BatteryMode.values().length - 1))]; }
    public int getRateIndex() { return data.get(6); }
    public int getRange() { return data.get(7); }
    public BatteryAccess getAccess() {
        return BatteryAccess.values()[Math.max(0, Math.min(data.get(8), BatteryAccess.values().length - 1))];
    }
    public float getEnergyRatio() { return getMaxEnergy() > 0 ? (float) Math.min(1.0, (double) getEnergy() / (double) getMaxEnergy()) : 0; }
    public String getRateDisplay() {
        BatteryTier tier = getTier();
        if (tier.isUltimate() && getRateIndex() >= tier.rates().length - 1) return "无限";
        return fmt(tier.rate(getRateIndex())) + " FE/t";
    }
    public String getRangeDisplay() {
        int r = getRange();
        return r < 0 ? "全维度" : String.format("%,d 格", r);
    }
    public String getAccessDisplay() {
        return getAccess().display();
    }

    // ---------------- 用电配置页读取 ----------------
    /** 本界面所属电池的位置（从数据槽读取，客户端也有效）。 */
    public net.minecraft.core.BlockPos getMenuPos() {
        return new net.minecraft.core.BlockPos(data.get(POS_SLOT), data.get(POS_SLOT + 1), data.get(POS_SLOT + 2));
    }

    public int getTargetCount() { return TARGET_COUNT; }

    public boolean hasTarget(int i) { return data.get(TARGET_BASE + i * 4) != EMPTY_SLOT; }

    public int getTargetX(int i) { return data.get(TARGET_BASE + i * 4); }
    public int getTargetY(int i) { return data.get(TARGET_BASE + i * 4 + 1); }
    public int getTargetZ(int i) { return data.get(TARGET_BASE + i * 4 + 2); }

    /** 0 吸电 / 1 供电 / 2 过载 */
    public int getTargetMode(int i) { return data.get(TARGET_BASE + i * 4 + 3); }

    public String getTargetModeName(int i) {
        return switch (getTargetMode(i)) {
            case 0 -> "吸电";
            case 1 -> "供电";
            case 3 -> "自定义";
            default -> "过载";
        };
    }

    /** 切换第 i 台机器的模式（走服务端按钮）。 */
    /** 把第 i 台机器设为指定模式（option 0 吸电 / 1 供电 / 2 过载 / 3 自定义 / 4 清除）。 */
    public boolean applyTargetOption(int index, int option, Player player) {
        if (blockEntity == null) return false;
        return blockEntity.applyTargetByIndex(index, option, player);
    }

    public boolean cycleTarget(int i, Player player) {
        if (blockEntity == null || !hasTarget(i)) return false;
        return blockEntity.cycleTargetMode(
                new net.minecraft.core.BlockPos(getTargetX(i), getTargetY(i), getTargetZ(i)), player);
    }
}
