package cn.ayaka.omnibattery.registry;

import cn.ayaka.omnibattery.BatteryTier;
import cn.ayaka.omnibattery.OmniBatteryBlock;
import cn.ayaka.omnibattery.OmniBatteryMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, OmniBatteryMod.MOD_ID);

    public static final RegistryObject<Block> LOW_BATTERY_BLOCK = BLOCKS.register("low_battery_block", () -> new OmniBatteryBlock(BatteryTier.LOW));
    public static final RegistryObject<Block> MEDIUM_BATTERY_BLOCK = BLOCKS.register("medium_battery_block", () -> new OmniBatteryBlock(BatteryTier.MEDIUM));
    public static final RegistryObject<Block> ADVANCED_BATTERY_BLOCK = BLOCKS.register("advanced_battery_block", () -> new OmniBatteryBlock(BatteryTier.ADVANCED));
    public static final RegistryObject<Block> ELITE_BATTERY_BLOCK = BLOCKS.register("elite_battery_block", () -> new OmniBatteryBlock(BatteryTier.ELITE));
    public static final RegistryObject<Block> ULTIMATE_BATTERY_BLOCK = BLOCKS.register("ultimate_battery_block", () -> new OmniBatteryBlock(BatteryTier.ULTIMATE));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
