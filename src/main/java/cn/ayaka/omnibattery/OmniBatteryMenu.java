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
    // 0 energy low, 1 energy high, 2 capacity low, 3 capacity high, 4 tier, 5 mode, 6 rate, 7 range
    private final ContainerData data;
    private final OmniBatteryBlockEntity blockEntity;

    public OmniBatteryMenu(int id, Inventory inv, OmniBatteryBlockEntity be) {
        super(ModMenuTypes.OMNI_BATTERY.get(), id);
        this.blockEntity = be;
        this.data = new SimpleContainerData(8);
        addDataSlots(data);
    }

    public OmniBatteryMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, inv.player.level().getBlockEntity(buf.readBlockPos()) instanceof OmniBatteryBlockEntity be ? be : null);
    }

    @Override public boolean stillValid(Player player) { return blockEntity != null && !blockEntity.isRemoved(); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (blockEntity != null) {
            syncLong(0, blockEntity.getEnergy());
            syncLong(2, blockEntity.getTier().capacity());
            data.set(4, blockEntity.getTier().ordinal());
            data.set(5, blockEntity.getMode().ordinal());
            data.set(6, blockEntity.getRateIndex());
            data.set(7, blockEntity.getRange());
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
        switch (button) {
            case 0 -> blockEntity.setMode(blockEntity.getMode().next());
            case 1 -> blockEntity.setRateIndex(Math.min(4, blockEntity.getRateIndex() + 1));
            case 2 -> blockEntity.setRateIndex(Math.max(0, blockEntity.getRateIndex() - 1));
            case 3 -> blockEntity.setRange(cycleRange(blockEntity.getRange(), blockEntity.getTier(), false));
            case 4 -> blockEntity.setRange(cycleRange(blockEntity.getRange(), blockEntity.getTier(), true));
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
    public BatteryTier getTier() { return BatteryTier.values()[Math.max(0, Math.min(data.get(4), BatteryTier.values().length - 1))]; }
    public BatteryMode getMode() { return BatteryMode.values()[Math.max(0, Math.min(data.get(5), BatteryMode.values().length - 1))]; }
    public int getRateIndex() { return data.get(6); }
    public int getRange() { return data.get(7); }
    public float getEnergyRatio() { return getMaxEnergy() > 0 ? (float) Math.min(1.0, (double) getEnergy() / (double) getMaxEnergy()) : 0; }
    public String getRateDisplay() {
        BatteryTier tier = getTier();
        if (tier.isUltimate() && getRateIndex() >= tier.rates().length - 1) return "无限";
        return String.format("%,d FE/t", tier.rate(getRateIndex()));
    }
    public String getRangeDisplay() {
        int r = getRange();
        return r < 0 ? "全维度" : String.format("%,d 格", r);
    }
}
