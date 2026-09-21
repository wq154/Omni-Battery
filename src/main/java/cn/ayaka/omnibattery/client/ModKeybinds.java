package cn.ayaka.omnibattery.client;

import cn.ayaka.omnibattery.OmniBatteryMod;
import cn.ayaka.omnibattery.network.OmniBatteryNetwork;
import cn.ayaka.omnibattery.network.OpenBoundBatteryPacket;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 快捷键：默认 \（反斜杠），打开标签工具绑定的电池界面。
 * 用标准 KeyMapping，因此会出现在「选项 -> 控制 -> 按键绑定」的「万能电池」分类下，玩家可随时改键。
 */
public final class ModKeybinds {
    private ModKeybinds() {}

    public static final String CATEGORY = "key.categories.omnibattery";

    public static final KeyMapping OPEN_BATTERY = new KeyMapping(
            "key.omnibattery.open_battery", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, CATEGORY);

    /** MOD 总线：注册按键。 */
    @Mod.EventBusSubscriber(modid = OmniBatteryMod.MOD_ID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_BATTERY);
        }
    }

    /** 游戏总线：每 tick 检查按键。 */
    @Mod.EventBusSubscriber(modid = OmniBatteryMod.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            while (OPEN_BATTERY.consumeClick()) {
                OmniBatteryNetwork.CHANNEL.sendToServer(new OpenBoundBatteryPacket(false, -2));
            }
        }
    }
}
