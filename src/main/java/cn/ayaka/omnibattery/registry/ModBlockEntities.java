package cn.ayaka.omnibattery.registry;

import cn.ayaka.omnibattery.OmniBatteryBlockEntity;
import cn.ayaka.omnibattery.OmniBatteryMod;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, OmniBatteryMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<OmniBatteryBlockEntity>> OMNI_BATTERY = BLOCK_ENTITIES.register("omni_battery",
            () -> BlockEntityType.Builder.of(OmniBatteryBlockEntity::new,
                            ModBlocks.LOW_BATTERY_BLOCK.get(),
                            ModBlocks.MEDIUM_BATTERY_BLOCK.get(),
                            ModBlocks.ADVANCED_BATTERY_BLOCK.get(),
                            ModBlocks.ELITE_BATTERY_BLOCK.get(),
                            ModBlocks.ULTIMATE_BATTERY_BLOCK.get())
                    .build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
