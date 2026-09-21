package cn.ayaka.omnibattery.network;

import cn.ayaka.omnibattery.MachineStickerItem;
import cn.ayaka.omnibattery.OmniBatteryBlockEntity;
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
 * 客户端 -> 服务端：按快捷键打开"标签工具绑定的电池"界面。
 * 不需要手持：只要身上（主副手 / 物品栏）带着已绑定的标签工具即可。next=true 时切到下一块。
 */
public class OpenBoundBatteryPacket {
    private final boolean next;
    private final int index;   // -2 = 不指定；-1 = 下一个；>=0 = 指定索引

    public OpenBoundBatteryPacket(boolean next, int index) { this.next = next; this.index = index; }

    public OpenBoundBatteryPacket(FriendlyByteBuf buf) {
        this.next = buf.readBoolean(); this.index = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) { buf.writeBoolean(next); buf.writeInt(index); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) openFor(sp, next, index);
        });
        ctx.get().setPacketHandled(true);
    }

    /** 服务端内部/菜单按钮复用：找到身上的贴纸，切换/指定后打开绑定的电池（必须已在服务端线程）。 */
    public static void openFor(ServerPlayer sp, boolean next, int index) {
            ItemStack sticker = ItemStack.EMPTY;
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack st = sp.getItemInHand(hand);
                if (st.getItem() instanceof MachineStickerItem && MachineStickerItem.hasBind(st)) {
                    sticker = st; break;
                }
            }
            if (sticker.isEmpty()) {
                var inv = sp.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack st = inv.getItem(i);
                    if (st.getItem() instanceof MachineStickerItem && MachineStickerItem.hasBind(st)) {
                        sticker = st; break;
                    }
                }
            }
            if (sticker.isEmpty()) {
                sp.displayClientMessage(Component.literal("身上要带着已绑定的标签工具才能快捷打开电池")
                        .withStyle(net.minecraft.ChatFormatting.GRAY), true);
                return;
            }
            if (index >= 0) MachineStickerItem.setBindIndex(sticker, index);
            else if (next) MachineStickerItem.cycleBind(sticker);

            BlockPos pos = MachineStickerItem.getBindPos(sticker);
            String dim = MachineStickerItem.getBindDim(sticker);
            ServerLevel target = null;
            for (ServerLevel sl : sp.server.getAllLevels()) {
                if (sl.dimension().location().toString().equals(dim)) { target = sl; break; }
            }
            if (target == null || pos == null
                    || !(target.getBlockEntity(pos) instanceof OmniBatteryBlockEntity be)) {
                sp.displayClientMessage(Component.literal("绑定的电池不存在了（已拆除或区块未加载）")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
                return;
            }
            if (!be.canManage(sp)) {
                sp.displayClientMessage(Component.literal("你没有权限操作这个电池")
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
                return;
            }
            sp.openMenu(be);
    }
}
