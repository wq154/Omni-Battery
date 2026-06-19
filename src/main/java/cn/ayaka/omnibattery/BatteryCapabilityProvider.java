package cn.ayaka.omnibattery;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

public class BatteryCapabilityProvider implements ICapabilitySerializable<CompoundTag> {
    private final ItemStack stack;
    private final BatteryTier tier;
    private final LazyOptional<IEnergyStorage> energy;

    public BatteryCapabilityProvider(ItemStack stack, BatteryTier tier) {
        this.stack = stack;
        this.tier = tier;
        this.energy = LazyOptional.of(() -> new BatteryEnergyStorage(stack, tier));
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return energy.cast();
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(BatteryData.ENERGY, BatteryData.getEnergy(stack));
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt != null && nbt.contains(BatteryData.ENERGY)) {
            BatteryData.setEnergy(stack, nbt.getLong(BatteryData.ENERGY), tier);
        }
    }
}
