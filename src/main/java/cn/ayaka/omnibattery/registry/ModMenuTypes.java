package cn.ayaka.omnibattery.registry;

import cn.ayaka.omnibattery.OmniBatteryMenu;
import cn.ayaka.omnibattery.OmniBatteryMod;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES, OmniBatteryMod.MOD_ID);

    public static final RegistryObject<MenuType<OmniBatteryMenu>> OMNI_BATTERY = MENU_TYPES.register("omni_battery",
            () -> IForgeMenuType.create(OmniBatteryMenu::new));

    public static void register(IEventBus bus) {
        MENU_TYPES.register(bus);
    }
}
