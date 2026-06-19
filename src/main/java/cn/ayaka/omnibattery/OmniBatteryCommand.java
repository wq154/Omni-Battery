package cn.ayaka.omnibattery;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

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
                .executes(ctx -> info(ctx.getSource())));
    }

    private static int info(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("万能电池：手持电池后用 /omnibattery rate 1-5、/omnibattery range <数字|all>、/omnibattery mode <both|charge|absorb|off>").withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static ItemStack heldBattery(CommandSourceStack source) throws CommandSyntaxException {
        Player player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof OmniBatteryItem)) {
            stack = player.getOffhandItem();
        }
        if (!(stack.getItem() instanceof OmniBatteryItem)) {
            source.sendFailure(Component.literal("请先把万能电池拿在手上。"));
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private static int setMode(CommandSourceStack source, String raw) throws CommandSyntaxException {
        ItemStack stack = heldBattery(source);
        if (stack.isEmpty()) return 0;
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
        BatteryData.setMode(stack, mode);
        source.sendSuccess(() -> Component.translatable("message.omnibattery.mode", mode.display()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        return 1;
    }

    private static int setRange(CommandSourceStack source, String raw) throws CommandSyntaxException {
        ItemStack stack = heldBattery(source);
        if (stack.isEmpty()) return 0;
        BatteryTier tier = ((OmniBatteryItem) stack.getItem()).getTier();
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
        BatteryData.setRange(stack, tier, range);
        source.sendSuccess(() -> Component.translatable("message.omnibattery.range", range < 0 ? "当前维度全部已加载区块" : range + " 格").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static int setRate(CommandSourceStack source, int level) throws CommandSyntaxException {
        ItemStack stack = heldBattery(source);
        if (stack.isEmpty()) return 0;
        BatteryData.setRateIndex(stack, level - 1);
        BatteryTier tier = ((OmniBatteryItem) stack.getItem()).getTier();
        int rate = tier.rate(level - 1);
        source.sendSuccess(() -> Component.translatable("message.omnibattery.rate", String.valueOf(rate)).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
