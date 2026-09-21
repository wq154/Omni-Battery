package cn.ayaka.omnibattery;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import javax.annotation.Nullable;
import java.util.List;

public class MachineStickerItem extends Item {
    private static final String TAG_MODE = "StickerMode";
    // 快捷键绑定目标（可绑多个电池）
    private static final String TAG_BINDS = "StickerBinds";   // ListTag: {x,y,z,d}
    private static final String TAG_BIDX = "StickerBindIdx";
    private static final String TAG_CAP = "StickerCustomCap"; // 自定义灌入上限

    public MachineStickerItem(Properties props) {
        super(props.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            // 客户端：手持"自定义"且未潜行时，右键空气 = 打开数值输入框。
            // 必须走反射桥：公共类直接引用 net.minecraft.client.* 会让专用服务器崩溃。
            if (!player.isShiftKeyDown() && getSelectedMode(stack) == StickerMode.CUSTOM) {
                cn.ayaka.omnibattery.client.ClientHooks.openCustomCap(stack);
                return InteractionResultHolder.success(stack);
            }
            return InteractionResultHolder.pass(stack);
        }

        if (!player.isShiftKeyDown()) {
            if (getSelectedMode(stack) == StickerMode.CUSTOM) {
                return InteractionResultHolder.success(stack);   // 客户端已弹输入框
            }
            player.displayClientMessage(Component.literal("潜行右键空气：切换标签模式")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }

        // 潜行右键空气 = 循环切换模式（自定义模式下也能切走）
        StickerMode next = getSelectedMode(stack).next();
        setSelectedMode(stack, next);
        player.displayClientMessage(
                Component.literal("当前标签模式: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(next.displayZh()).withStyle(colorOf(next))), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (level.isClientSide || player == null) return InteractionResult.SUCCESS;

        if (!player.isShiftKeyDown()) {
            player.displayClientMessage(Component.literal("请潜行右键机器贴标签，潜行右键空气切换模式").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.PASS;
        }

        // 右键的是电池 → 顺便绑定为"快捷键目标"（不需手持即可快捷打开）
        if (level.getBlockEntity(pos) instanceof OmniBatteryBlockEntity obe && obe.canManage(player)) {
            int bound = addBind(stack, pos, level.dimension().location().toString());
            player.displayClientMessage(Component.literal("已绑定（共 " + bound + " 个）：按快捷键打开，界面内可切换: ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                            .withStyle(ChatFormatting.WHITE)), true);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            player.displayClientMessage(Component.literal("这里没有机器，无法贴标签").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        boolean hasEnergy = be.getCapability(ForgeCapabilities.ENERGY).isPresent();
        if (!hasEnergy) {
            player.displayClientMessage(Component.literal("这个方块不支持 FE 能量，无法贴标签").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        StickerSavedData data = StickerSavedData.get(serverLevel);
        StickerMode mode = getSelectedMode(stack);
        if (mode == StickerMode.CLEAR) {
            data.removeSticker(pos);
            player.displayClientMessage(Component.literal("已清除机器标签").withStyle(ChatFormatting.GRAY), true);
        } else {
            data.setMode(pos, mode, player.getUUID(), player.getGameProfile().getName());
            player.displayClientMessage(
                    Component.literal("已贴标签: ").withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(mode.displayZh()).withStyle(colorOf(mode))),
                    true
            );
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        StickerMode mode = getSelectedMode(stack);
        tooltip.add(Component.literal("当前模式：").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(mode.displayZh()).withStyle(colorOf(mode))));
        tooltip.add(Component.literal("潜行右键空气：切换模式").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("潜行右键机器：贴上当前模式标签").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("模式：供电 → 吸电 → 过载 → 清除").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("未贴标签的机器不会自动吸/供，过载会尝试绕过机器上限").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static StickerMode getSelectedMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_MODE)) return StickerMode.SUPPLY;
        int idx = tag.getInt(TAG_MODE);
        StickerMode[] values = StickerMode.values();
        return idx >= 0 && idx < values.length ? values[idx] : StickerMode.SUPPLY;
    }

    private static void setSelectedMode(ItemStack stack, StickerMode mode) {
        stack.getOrCreateTag().putInt(TAG_MODE, mode.ordinal());
    }

    // ---------------- 快捷键绑定目标（可绑定多个电池）----------------

    /** 绑定一个电池（已绑定过则只切换选中项）。返回绑定总数。 */
    public static int addBind(ItemStack stack, BlockPos pos, String dim) {
        var tag = stack.getOrCreateTag();
        var list = tag.getList(TAG_BINDS, 10);
        for (int i = 0; i < list.size(); i++) {
            var e = list.getCompound(i);
            if (e.getInt("x") == pos.getX() && e.getInt("y") == pos.getY()
                    && e.getInt("z") == pos.getZ() && e.getString("d").equals(dim)) {
                tag.putInt(TAG_BIDX, i);
                return list.size();
            }
        }
        var e = new net.minecraft.nbt.CompoundTag();
        e.putInt("x", pos.getX());
        e.putInt("y", pos.getY());
        e.putInt("z", pos.getZ());
        e.putString("d", dim);
        list.add(e);
        tag.put(TAG_BINDS, list);
        tag.putInt(TAG_BIDX, list.size() - 1);
        return list.size();
    }

    /** 全部绑定（每项：x,y,z,dim）。 */
    public static java.util.List<Object[]> getBinds(ItemStack stack) {
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        var list = stack.getOrCreateTag().getList(TAG_BINDS, 10);
        for (int i = 0; i < list.size(); i++) {
            var e = list.getCompound(i);
            out.add(new Object[]{e.getInt("x"), e.getInt("y"), e.getInt("z"), e.getString("d")});
        }
        return out;
    }

    public static int getBindCount(ItemStack stack) { return getBinds(stack).size(); }

    /** 当前选中的绑定索引（越界自动回绕；无绑定返回 -1）。 */
    public static int getBindIndex(ItemStack stack) {
        int n = getBindCount(stack);
        if (n == 0) return -1;
        int i = stack.getOrCreateTag().getInt(TAG_BIDX);
        return (i % n + n) % n;
    }

    /** 直接选中指定索引。 */
    public static boolean setBindIndex(ItemStack stack, int idx) {
        int n = getBindCount(stack);
        if (n == 0) return false;
        stack.getOrCreateTag().putInt(TAG_BIDX, ((idx % n) + n) % n);
        return true;
    }

    /** 切到下一个绑定。 */
    public static Object[] cycleBind(ItemStack stack) {
        int n = getBindCount(stack);
        if (n == 0) return null;
        setBindIndex(stack, getBindIndex(stack) + 1);
        return getBinds(stack).get(getBindIndex(stack));
    }

    public static boolean hasBind(ItemStack stack) { return getBindCount(stack) > 0; }

    public static BlockPos getBindPos(ItemStack stack) {
        java.util.List<Object[]> all = getBinds(stack);
        if (all.isEmpty()) return null;
        Object[] e = all.get(Math.max(0, getBindIndex(stack)));
        return new BlockPos((int) e[0], (int) e[1], (int) e[2]);
    }

    public static String getBindDim(ItemStack stack) {
        java.util.List<Object[]> all = getBinds(stack);
        return all.isEmpty() ? "" : (String) all.get(Math.max(0, getBindIndex(stack)))[3];
    }

    // ---------------- 自定义模式的灌入上限 ----------------

    /** 自定义灌入上限（FE）。默认 100 万。 */
    public static long getCustomCap(ItemStack stack) {
        long v = stack.getOrCreateTag().getLong(TAG_CAP);
        return v > 0L ? v : 1_000_000L;
    }

    public static void setCustomCap(ItemStack stack, long value) {
        stack.getOrCreateTag().putLong(TAG_CAP, Math.max(1L, Math.min(Long.MAX_VALUE / 4, value)));
    }

    private static ChatFormatting colorOf(StickerMode mode) {
        return switch (mode) {
            case SUPPLY -> ChatFormatting.GREEN;
            case ABSORB -> ChatFormatting.YELLOW;
            case OVERLOAD -> ChatFormatting.RED;
            case CUSTOM -> ChatFormatting.GOLD;
            case CLEAR -> ChatFormatting.GRAY;
        };
    }
}
