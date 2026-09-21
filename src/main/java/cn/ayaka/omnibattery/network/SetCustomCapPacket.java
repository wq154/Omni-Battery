package cn.ayaka.omnibattery.network;

import cn.ayaka.omnibattery.MachineStickerItem;
import cn.ayaka.omnibattery.OmniBatteryMod;
import cn.ayaka.omnibattery.StickerMode;
import cn.ayaka.omnibattery.StickerSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 -> 服务端：保存"自定义"的灌入上限数值。
 * 除更新手上的标签工具外，还会**同步到该玩家名下所有"自定义"标签的机器**
 * （否则机器上留的是旧数值，表现为"打了自定义没反应"）。
 */
public class SetCustomCapPacket {
    private final long value;

    public SetCustomCapPacket(long value) { this.value = value; }

    public SetCustomCapPacket(FriendlyByteBuf buf) { this.value = buf.readLong(); }

    public void encode(FriendlyByteBuf buf) { buf.writeLong(value); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null) return;

            // 1) 手上的标签工具
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack st = sp.getItemInHand(hand);
                if (st.getItem() instanceof MachineStickerItem) {
                    MachineStickerItem.setCustomCap(st, value);
                    break;
                }
            }

            // 2) 同步到该玩家所有 CUSTOM 标签的机器
            int updated = 0;
            for (ServerLevel sl : sp.server.getAllLevels()) {
                StickerSavedData data = StickerSavedData.get(sl);
                for (BlockPos pos : data.positions()) {
                    StickerSavedData.StickerEntry e = data.getEntry(pos);
                    if (e == null || e.mode() != StickerMode.CUSTOM) continue;
                    if (e.owner() != null && !e.owner().equals(sp.getUUID())) continue;
                    data.setMode(pos, StickerMode.CUSTOM, e.owner(), e.ownerName(), value);
                    updated++;
                }
            }
            sp.displayClientMessage(Component.literal(
                            "自定义过载上限已设为 " + value + " FE"
                                    + (updated > 0 ? "（已同步 " + updated + " 台机器）" : ""))
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
        });
        ctx.get().setPacketHandled(true);
    }
}
