package cn.ayaka.omnibattery.registry;

import cn.ayaka.omnibattery.BatteryTier;
import cn.ayaka.omnibattery.MachineStickerItem;
import cn.ayaka.omnibattery.OmniBatteryItem;
import cn.ayaka.omnibattery.OmniBatteryMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, OmniBatteryMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OmniBatteryMod.MOD_ID);

    public static final RegistryObject<Item> LOW_BATTERY = ITEMS.register("low_battery", () -> new OmniBatteryItem(BatteryTier.LOW, new Item.Properties()));
    public static final RegistryObject<Item> MEDIUM_BATTERY = ITEMS.register("medium_battery", () -> new OmniBatteryItem(BatteryTier.MEDIUM, new Item.Properties()));
    public static final RegistryObject<Item> ADVANCED_BATTERY = ITEMS.register("advanced_battery", () -> new OmniBatteryItem(BatteryTier.ADVANCED, new Item.Properties()));
    public static final RegistryObject<Item> ELITE_BATTERY = ITEMS.register("elite_battery", () -> new OmniBatteryItem(BatteryTier.ELITE, new Item.Properties()));
    public static final RegistryObject<Item> ULTIMATE_BATTERY = ITEMS.register("ultimate_battery", () -> new OmniBatteryItem(BatteryTier.ULTIMATE, new Item.Properties()));

    public static final RegistryObject<Item> MACHINE_STICKER = ITEMS.register("machine_sticker", () -> new MachineStickerItem(new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.omnibattery.main"))
            .icon(() -> new ItemStack(ULTIMATE_BATTERY.get()))
            .displayItems((parameters, output) -> {
                output.accept(LOW_BATTERY.get());
                output.accept(MEDIUM_BATTERY.get());
                output.accept(ADVANCED_BATTERY.get());
                output.accept(ELITE_BATTERY.get());
                output.accept(ULTIMATE_BATTERY.get());
                output.accept(MACHINE_STICKER.get());
            }).build());

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
