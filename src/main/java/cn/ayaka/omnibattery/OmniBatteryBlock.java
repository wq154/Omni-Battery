package cn.ayaka.omnibattery;

import cn.ayaka.omnibattery.registry.ModBlockEntities;
import cn.ayaka.omnibattery.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

public class OmniBatteryBlock extends BaseEntityBlock {
    private final BatteryTier tier;

    public OmniBatteryBlock(BatteryTier tier) {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 8.0F));
        this.tier = tier;
    }

    public BatteryTier getTier() {
        return tier;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OmniBatteryBlockEntity(pos, state, tier);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.OMNI_BATTERY.get(), OmniBatteryBlockEntity::tick);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof OmniBatteryBlockEntity batteryBE && player instanceof ServerPlayer serverPlayer) {
            batteryBE.ensureOwner(player);
            NetworkHooks.openScreen(serverPlayer, batteryBE, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, BlockEntity be, net.minecraft.world.item.ItemStack tool) {
        if (!level.isClientSide && !player.isCreative() && be instanceof OmniBatteryBlockEntity batteryBE) {
            ItemStack drop = new ItemStack(getBatteryItem(batteryBE.getTier()));
            BatteryData.setEnergy(drop, batteryBE.getEnergy(), batteryBE.getTier());
            BatteryData.setMode(drop, batteryBE.getMode());
            BatteryData.setRateIndex(drop, batteryBE.getRateIndex());
            BatteryData.setRange(drop, batteryBE.getTier(), batteryBE.getRange());
            BatteryData.setPublicAccess(drop, batteryBE.isPublicAccess());
            BatteryData.setOwner(drop, batteryBE.getOwnerUuid(), batteryBE.getOwnerName());
            BatteryData.setTrustedPlayers(drop, batteryBE.getTrustedPlayers());
            popResource(level, pos, drop);
        }
        super.playerDestroy(level, player, pos, state, be, tool);
    }

    private static net.minecraft.world.item.Item getBatteryItem(BatteryTier tier) {
        return switch (tier) {
            case LOW -> ModItems.LOW_BATTERY.get();
            case MEDIUM -> ModItems.MEDIUM_BATTERY.get();
            case ADVANCED -> ModItems.ADVANCED_BATTERY.get();
            case ELITE -> ModItems.ELITE_BATTERY.get();
            case ULTIMATE -> ModItems.ULTIMATE_BATTERY.get();
        };
    }
}
