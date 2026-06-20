package cn.ayaka.omnibattery;

import cn.ayaka.omnibattery.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import javax.annotation.Nullable;
import java.text.NumberFormat;
import java.util.List;

public class OmniBatteryItem extends BlockItem {
    private final BatteryTier tier;

    public OmniBatteryItem(BatteryTier tier, Properties properties) {
        super(switch (tier) {
            case LOW -> ModBlocks.LOW_BATTERY_BLOCK.get();
            case MEDIUM -> ModBlocks.MEDIUM_BATTERY_BLOCK.get();
            case ADVANCED -> ModBlocks.ADVANCED_BATTERY_BLOCK.get();
            case ELITE -> ModBlocks.ELITE_BATTERY_BLOCK.get();
            case ULTIMATE -> ModBlocks.ULTIMATE_BATTERY_BLOCK.get();
        }, properties.stacksTo(1));
        this.tier = tier;
    }

    public BatteryTier getTier() {
        return tier;
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, net.minecraft.nbt.CompoundTag nbt) {
        return new BatteryCapabilityProvider(stack, tier);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        if (player != null) BatteryData.ensureOwner(stack, player);
        long energy = BatteryData.getEnergy(stack);
        BatteryMode mode = BatteryData.getMode(stack);
        int rateIndex = BatteryData.getRateIndex(stack);
        int range = BatteryData.getRange(stack, tier);
        boolean publicAccess = BatteryData.isPublicAccess(stack);

        InteractionResult result = super.place(context);
        if (result.consumesAction() && !context.getLevel().isClientSide) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            if (!(level.getBlockEntity(pos) instanceof OmniBatteryBlockEntity)) {
                pos = context.getClickedPos();
            }
            if (level.getBlockEntity(pos) instanceof OmniBatteryBlockEntity be) {
                be.setEnergy(energy);
                be.setMode(mode);
                be.setRateIndex(rateIndex);
                be.setRange(range);
                be.setPublicAccess(publicAccess);
                be.setOwner(BatteryData.getOwnerUUID(stack), BatteryData.getOwnerName(stack));
                be.setTrustedPlayers(BatteryData.getTrustedPlayers(stack));
            }
        }
        return result;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return BatteryData.getEnergy(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * (float) Math.min(1.0, (double) BatteryData.getEnergy(stack) / (double) tier.capacity()));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x44D7FF;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        long energy = BatteryData.getEnergy(stack);
        int rateIndex = BatteryData.getRateIndex(stack);
        int range = BatteryData.getRange(stack, tier);
        BatteryMode mode = BatteryData.getMode(stack);
        String owner = BatteryData.getOwnerName(stack);

        tooltip.add(Component.translatable("tooltip.omnibattery.tier", tier.display()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.omnibattery.energy", fmt(energy), fmt(tier.capacity())).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.omnibattery.mode", mode.display()).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.omnibattery.range", formatRange(range)).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.omnibattery.rate", formatRate(rateIndex)).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal("权限：" + BatteryData.accessDisplay(stack)).withStyle(BatteryData.isPublicAccess(stack) ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (!owner.isBlank()) tooltip.add(Component.literal("主人：" + owner).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal(" "));
        tooltip.add(Component.literal("放置后右键方块：打开设置界面").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("物品形态不会自动工作；机器传电必须放下电池").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("命令：/omnibattery access private/public，trust/untrust 授权").withStyle(ChatFormatting.DARK_GRAY));
        if (tier.isUltimate()) {
            tooltip.add(Component.literal("终极：最大范围全维度，最大速度无限").withStyle(ChatFormatting.RED));
        }
    }

    private String formatRate(int rateIndex) {
        if (tier.isUltimate() && rateIndex >= tier.rates().length - 1) return "无限";
        return fmt(tier.rate(rateIndex)) + " FE/t";
    }

    private static String fmt(long value) {
        return NumberFormat.getIntegerInstance().format(value);
    }

    private static Component formatRange(int range) {
        if (range < 0) return Component.literal("全维度");
        return Component.literal(fmt(range) + " 格");
    }
}
