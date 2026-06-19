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

    public MachineStickerItem(Properties props) {
        super(props.stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        if (!player.isShiftKeyDown()) {
            player.displayClientMessage(Component.literal("潜行右键空气：切换标签模式").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.pass(stack);
        }

        StickerMode next = getSelectedMode(stack).next();
        setSelectedMode(stack, next);
        player.displayClientMessage(
                Component.literal("当前标签模式: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(next.displayZh()).withStyle(colorOf(next))),
                true
        );
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
            data.setMode(pos, mode);
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

    private static ChatFormatting colorOf(StickerMode mode) {
        return switch (mode) {
            case SUPPLY -> ChatFormatting.GREEN;
            case ABSORB -> ChatFormatting.YELLOW;
            case OVERLOAD -> ChatFormatting.RED;
            case CLEAR -> ChatFormatting.GRAY;
        };
    }
}
