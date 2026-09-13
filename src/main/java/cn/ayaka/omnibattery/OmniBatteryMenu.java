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
    // 0 energy low, 1 energy high, 2 capacity low, 3 capacity high, 4 tier, 5 mode, 6 rate, 7 range, 8 public access
    private final ContainerData data;
    private final OmniBatteryBlockEntity blockEntity;

    public OmniBatteryMenu(int id, Inventory inv, OmniBatteryBlockEntity be) {
        super(ModMenuTypes.OMNI_BATTERY.get(), id);
        this.blockEntity = be;
        this.data = new SimpleContainerData(13 + OmniBatteryBlockEntity.HISTORY_SIZE * 4);
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
            data.set(8, blockEntity.isPublicAccess() ? 1 : 0);
            // 实时速率：absorbed 占 9-10，supplied 占 11-12
            syncLong(9, blockEntity.getAbsorbedPerSecond());
            syncLong(11, blockEntity.getSuppliedPerSecond());
            // 趋势图历史：每点 4 个 int slot（absorb long + supply long）
            for (int i = 0; i < OmniBatteryBlockEntity.HISTORY_SIZE; i++) {
                syncLong(13 + i * 4, blockEntity.getAbsorbHistory(i));
                syncLong(13 + i * 4 + 2, blockEntity.getSupplyHistory(i));
            }
        }
        super.broadcastChanges();
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
        blockEntity.ensureOwner(player);
        if (!blockEntity.canManage(player)) return false;
        switch (button) {
            case 0 -> blockEntity.setMode(blockEntity.getMode().next());
            case 1 -> blockEntity.setRateIndex(Math.min(4, blockEntity.getRateIndex() + 1));
            case 2 -> blockEntity.setRateIndex(Math.max(0, blockEntity.getRateIndex() - 1));
            case 3 -> blockEntity.setRange(cycleRange(blockEntity.getRange(), blockEntity.getTier(), false));
            case 4 -> blockEntity.setRange(cycleRange(blockEntity.getRange(), blockEntity.getTier(), true));
            case 5 -> blockEntity.setPublicAccess(!blockEntity.isPublicAccess());
            default -> { return false; }
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
    public long getAbsorbHistory(int i) { return readLong(13 + i * 4); }
    public long getSupplyHistory(int i) { return readLong(13 + i * 4 + 2); }

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
    public boolean isPublicAccess() { return data.get(8) != 0; }
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
        return isPublicAccess() ? "公开电" : "私有电";
    }
}
