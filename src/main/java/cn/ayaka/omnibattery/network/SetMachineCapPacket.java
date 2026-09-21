package cn.ayaka.omnibattery.network;

import cn.ayaka.omnibattery.OmniBatteryBlockEntity;
import cn.ayaka.omnibattery.StickerMode;
import cn.ayaka.omnibattery.StickerSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 -> 服务端：在电池 GUI 里设置某台机器的"自定义"灌入上限。 */
public class SetMachineCapPacket {
    private final int x, y, z;
    private final long value;

    public SetMachineCapPacket(int x, int y, int z, long value) {
        this.x = x; this.y = y; this.z = z; this.value = value;
    }

    public SetMachineCapPacket(FriendlyByteBuf buf) {
        this.x = buf.readInt(); this.y = buf.readInt(); this.z = buf.readInt(); this.value = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(x); buf.writeInt(y); buf.writeInt(z); buf.writeLong(value);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;
            BlockPos pos = new BlockPos(x, y, z);
            for (ServerLevel sl : sp.server.getAllLevels()) {
                StickerSavedData data = StickerSavedData.get(sl);
                StickerSavedData.StickerEntry e = data.getEntry(pos);
                if (e == null) continue;
                if (!(sl.getBlockEntity(pos) instanceof OmniBatteryBlockEntity be)) continue;
                if (!be.canManage(sp)) {
                    sp.displayClientMessage(Component.literal("你没有权限操作这个电池")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                    return;
                }
                data.setMode(pos, StickerMode.CUSTOM, e.owner(), e.ownerName(), value);
                sp.displayClientMessage(Component.literal("自定义过载上限已设为 " + value + " FE")
                        .withStyle(net.minecraft.ChatFormatting.GOLD), true);
                return;
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
