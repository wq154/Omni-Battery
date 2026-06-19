package cn.ayaka.omnibattery;

import cn.ayaka.omnibattery.registry.ModBlockEntities;
import cn.ayaka.omnibattery.registry.ModBlocks;
import cn.ayaka.omnibattery.registry.ModItems;
import cn.ayaka.omnibattery.registry.ModMenuTypes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraft.client.gui.screens.MenuScreens;

@Mod(OmniBatteryMod.MOD_ID)
public class OmniBatteryMod {
    public static final String MOD_ID = "omnibattery";

    public OmniBatteryMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenuTypes.register(modBus);
        ModItems.register(modBus);

        MinecraftForge.EVENT_BUS.register(new OmniBatteryTicker());

        modBus.addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.OMNI_BATTERY.get(), OmniBatteryScreen::new);
        });
    }
}
