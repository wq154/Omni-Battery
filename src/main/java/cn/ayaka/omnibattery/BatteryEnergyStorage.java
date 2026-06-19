package cn.ayaka.omnibattery;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

public class BatteryEnergyStorage implements IEnergyStorage {
    private final ItemStack stack;
    private final BatteryTier tier;

    public BatteryEnergyStorage(ItemStack stack, BatteryTier tier) {
        this.stack = stack;
        this.tier = tier;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) return 0;
        long energy = BatteryData.getEnergy(stack);
        long space = tier.capacity() - energy;
        int received = BatteryData.clampToForgeInt(Math.min(space, (long) maxReceive));
        if (!simulate && received > 0) {
            BatteryData.setEnergy(stack, energy + received, tier);
        }
        return received;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (maxExtract <= 0) return 0;
        long energy = BatteryData.getEnergy(stack);
        int extracted = BatteryData.clampToForgeInt(Math.min(energy, (long) maxExtract));
        if (!simulate && extracted > 0) {
            BatteryData.setEnergy(stack, energy - extracted, tier);
        }
        return extracted;
    }

    @Override
    public int getEnergyStored() {
        return BatteryData.clampToForgeInt(BatteryData.getEnergy(stack));
    }

    @Override
    public int getMaxEnergyStored() {
        return tier.forgeCapacity();
    }

    @Override public boolean canExtract() { return true; }
    @Override public boolean canReceive() { return true; }
}
