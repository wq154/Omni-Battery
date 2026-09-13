package cn.ayaka.omnibattery;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class OmniBatteryCommand {
    private OmniBatteryCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("omnibattery")
                .then(Commands.literal("mode")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("both");
                                    builder.suggest("charge");
                                    builder.suggest("absorb");
                                    builder.suggest("off");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> setMode(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
                .then(Commands.literal("range")
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("16"); builder.suggest("32"); builder.suggest("64"); builder.suggest("128"); builder.suggest("256"); builder.suggest("512"); builder.suggest("all");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> setRange(ctx.getSource(), StringArgumentType.getString(ctx, "value")))))
                .then(Commands.literal("rate")
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                .executes(ctx -> setRate(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "level")))))
                .then(Commands.literal("access")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("public"); builder.suggest("private"); builder.suggest("公开"); builder.suggest("私有");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> setAccess(ctx.getSource(), StringArgumentType.getString(ctx, "mode")))))
                .then(Commands.literal("trust")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> trust(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), true))))
                .then(Commands.literal("untrust")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> trust(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), false))))
                .then(Commands.literal("trustlist")
                        .executes(ctx -> trustList(ctx.getSource())))
                .executes(ctx -> info(ctx.getSource())));
    }

    private static int info(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("万能电池：手持电池或看向地上电池后用 /omnibattery rate 1-5、range <数字|all>、mode <both|charge|absorb|off>、access <public|private>、trust/untrust <玩家>").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static BatteryTarget selectedBattery(CommandSourceStack source) throws CommandSyntaxException {
        Player player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof OmniBatteryItem) return BatteryTarget.ofStack(stack);
        stack = player.getOffhandItem();
        if (stack.getItem() instanceof OmniBatteryItem) return BatteryTarget.ofStack(stack);

        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(8.0D));
        HitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit instanceof BlockHitResult blockHit && player.level().getBlockEntity(blockHit.getBlockPos()) instanceof OmniBatteryBlockEntity be) {
            return BatteryTarget.ofBlock(be);
        }

        source.sendFailure(Component.literal("请先把万能电池拿在手上，或看向 8 格内已放置的万能电池。"));
        return null;
    }

    private static boolean prepareManage(CommandSourceStack source, BatteryTarget target) throws CommandSyntaxException {
        if (target == null) return false;
        Player player = source.getPlayerOrException();
        target.ensureOwner(player);
        if (!target.canManage(player)) {
            source.sendFailure(Component.literal("只有电池主人可以修改这个电池的设置。"));
            return false;
        }
        return true;
    }

    private static int setMode(CommandSourceStack source, String raw) throws CommandSyntaxException {
        BatteryTarget target = selectedBattery(source);
        if (!prepareManage(source, target)) return 0;
        BatteryMode mode = switch (raw.toLowerCase()) {
            case "both", "all", "双向", "吸取供能" -> BatteryMode.BOTH;
            case "charge", "send", "供能" -> BatteryMode.CHARGE_ONLY;
            case "absorb", "input", "吸取" -> BatteryMode.ABSORB_ONLY;
            case "off", "关闭" -> BatteryMode.OFF;
            default -> null;
        };
        if (mode == null) {
            source.sendFailure(Component.literal("模式只能是 both / charge / absorb / off"));
            return 0;
        }
        target.setMode(mode);
        source.sendSuccess(() -> Component.translatable("message.omnibattery.mode", mode.display()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    private static int setRange(CommandSourceStack source, String raw) throws CommandSyntaxException {
        BatteryTarget target = selectedBattery(source);
        if (!prepareManage(source, target)) return 0;
        BatteryTier tier = target.getTier();
        int range;
        if (raw.equalsIgnoreCase("all") || raw.equals("全维度") || raw.equals("全部")) {
            if (!tier.isUltimate()) {
                source.sendFailure(Component.literal("只有终极万能电池可以设置为 all。"));
                return 0;
            }
            range = -1;
        } else {
            try {
                range = Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                source.sendFailure(Component.literal("范围请输入数字或 all。"));
                return 0;
            }
        }
        target.setRange(range);
        int actual = target.getRange();
        source.sendSuccess(() -> Component.translatable("message.omnibattery.range", actual < 0 ? "当前维度全部已加载区块" : actual + " 格").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int setRate(CommandSourceStack source, int level) throws CommandSyntaxException {
        BatteryTarget target = selectedBattery(source);
        if (!prepareManage(source, target)) return 0;
        target.setRateIndex(level - 1);
        long rate = target.getTier().rate(level - 1);
        source.sendSuccess(() -> Component.translatable("message.omnibattery.rate", String.valueOf(rate)).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int setAccess(CommandSourceStack source, String raw) throws CommandSyntaxException {
        BatteryTarget target = selectedBattery(source);
        if (!prepareManage(source, target)) return 0;
        String text = raw.toLowerCase();
        BatteryAccess access = switch (text) {
            case "public", "open", "公开", "公开电" -> BatteryAccess.PUBLIC;
            case "team", "队伍", "队伍电" -> BatteryAccess.TEAM;
            case "private", "owner", "私有", "私有电" -> BatteryAccess.PRIVATE;
            default -> null;
        };
        if (access == null) {
            source.sendFailure(Component.literal("权限只能是 public / team / private。"));
            return 0;
        }
        target.setAccess(access);
        source.sendSuccess(() -> Component.literal("电池权限已设为：" + access.display())
                .withStyle(switch (access) {
                    case PRIVATE -> ChatFormatting.RED;
                    case TEAM -> ChatFormatting.AQUA;
                    case PUBLIC -> ChatFormatting.GREEN;
                }), false);
        return 1;
    }

    private static int trust(CommandSourceStack source, ServerPlayer targetPlayer, boolean add) throws CommandSyntaxException {
        BatteryTarget target = selectedBattery(source);
        if (!prepareManage(source, target)) return 0;
        Player owner = source.getPlayerOrException();
        if (targetPlayer.getUUID().equals(owner.getUUID())) {
            source.sendFailure(Component.literal("主人自己不需要授权。"));
            return 0;
        }
        if (add) {
            target.addTrusted(targetPlayer);
            source.sendSuccess(() -> Component.literal("已授权玩家使用本电池：" + targetPlayer.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), false);
        } else {
            target.removeTrusted(targetPlayer.getUUID());
            source.sendSuccess(() -> Component.literal("已移除授权：" + targetPlayer.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    private static int trustList(CommandSourceStack source) throws CommandSyntaxException {
        BatteryTarget target = selectedBattery(source);
        if (!prepareManage(source, target)) return 0;
        source.sendSuccess(() -> Component.literal("授权名单：" + target.trustedListDisplay()).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static class BatteryTarget {
        private final ItemStack stack;
        private final OmniBatteryBlockEntity blockEntity;

        private BatteryTarget(ItemStack stack, OmniBatteryBlockEntity blockEntity) {
            this.stack = stack;
            this.blockEntity = blockEntity;
        }

        static BatteryTarget ofStack(ItemStack stack) { return new BatteryTarget(stack, null); }
        static BatteryTarget ofBlock(OmniBatteryBlockEntity be) { return new BatteryTarget(ItemStack.EMPTY, be); }

        BatteryTier getTier() {
            if (blockEntity != null) return blockEntity.getTier();
            return ((OmniBatteryItem) stack.getItem()).getTier();
        }

        int getRange() {
            if (blockEntity != null) return blockEntity.getRange();
            return BatteryData.getRange(stack, getTier());
        }

        void ensureOwner(Player player) {
            if (blockEntity != null) blockEntity.ensureOwner(player);
            else BatteryData.ensureOwner(stack, player);
        }

        boolean canManage(Player player) {
            if (blockEntity != null) return blockEntity.canManage(player);
            return BatteryData.isOwner(stack, player);
        }

        void setMode(BatteryMode mode) {
            if (blockEntity != null) blockEntity.setMode(mode);
            else BatteryData.setMode(stack, mode);
        }

        void setRange(int range) {
            if (blockEntity != null) blockEntity.setRange(range);
            else BatteryData.setRange(stack, getTier(), range);
        }

        void setRateIndex(int rateIndex) {
            if (blockEntity != null) blockEntity.setRateIndex(rateIndex);
            else BatteryData.setRateIndex(stack, rateIndex);
        }

        void setAccess(BatteryAccess access) {
            if (blockEntity != null) blockEntity.setAccess(access);
            else BatteryData.setAccess(stack, access);
        }

        void addTrusted(Player player) {
            if (blockEntity != null) blockEntity.addTrusted(player);
            else BatteryData.addTrusted(stack, player);
        }

        void removeTrusted(UUID uuid) {
            if (blockEntity != null) blockEntity.removeTrusted(uuid);
            else BatteryData.removeTrusted(stack, uuid);
        }

        String trustedListDisplay() {
            if (blockEntity != null) return blockEntity.trustedListDisplay();
            return BatteryData.trustedListDisplay(stack);
        }
    }
}
