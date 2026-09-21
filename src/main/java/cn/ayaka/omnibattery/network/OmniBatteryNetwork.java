package cn.ayaka.omnibattery.network;

import cn.ayaka.omnibattery.OmniBatteryMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** 1.20.1 网络通道（SimpleChannel）。 */
public final class OmniBatteryNetwork {
    private OmniBatteryNetwork() {}

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(OmniBatteryMod.MOD_ID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SetCustomCapPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetCustomCapPacket::encode)
                .decoder(SetCustomCapPacket::new)
                .consumerMainThread(SetCustomCapPacket::handle)
                .add();
        CHANNEL.messageBuilder(SetMachineCapPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetMachineCapPacket::encode)
                .decoder(SetMachineCapPacket::new)
                .consumerMainThread(SetMachineCapPacket::handle)
                .add();
        CHANNEL.messageBuilder(OpenBoundBatteryPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenBoundBatteryPacket::encode)
                .decoder(OpenBoundBatteryPacket::new)
                .consumerMainThread(OpenBoundBatteryPacket::handle)
                .add();
    }
}
