package cn.ayaka.omnibattery;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Common Forge event hooks.
 * Omni batteries are placeable blocks: carried item stacks do not auto charge anything.
 */
public class OmniBatteryTicker {
    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        OmniBatteryCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            StickerSavedData.get(level).removeSticker(event.getPos());
        }
    }
}
