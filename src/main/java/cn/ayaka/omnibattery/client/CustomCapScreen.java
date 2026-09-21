package cn.ayaka.omnibattery.client;

import cn.ayaka.omnibattery.MachineStickerItem;
import cn.ayaka.omnibattery.network.OmniBatteryNetwork;
import cn.ayaka.omnibattery.network.SetCustomCapPacket;
import cn.ayaka.omnibattery.network.SetMachineCapPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 「自定义」灌入上限输入框。两种用法：
 * 手持标签潜行右键空气（自定义模式） → 改标签工具上的值；
 * 电池 GUI → 用电配置 → 某台机器选"自定义" → 改那台机器的值。
 */
public class CustomCapScreen extends Screen {
    private final ItemStack stack;
    private final BlockPos machinePos;
    private final long initial;

    public CustomCapScreen(ItemStack stack) {
        super(Component.literal("自定义灌入上限"));
        this.stack = stack;
        this.machinePos = null;
        this.initial = MachineStickerItem.getCustomCap(stack);
    }

    public CustomCapScreen(BlockPos machinePos, long initial) {
        super(Component.literal("自定义灌入上限"));
        this.stack = null;
        this.machinePos = machinePos;
        this.initial = initial > 0L ? initial : 1_000_000L;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        EditBox box = new EditBox(this.font, cx - 90, cy - 8, 180, 20, Component.literal("上限"));
        box.setMaxLength(18);
        box.setValue(String.valueOf(this.initial));
        addRenderableWidget(box);
        setInitialFocus(box);

        addRenderableWidget(Button.builder(Component.literal("保存"), b -> {
            long v;
            try {
                v = Long.parseLong(box.getValue().trim().replace("_", "").replace(",", ""));
            } catch (Exception e) {
                v = 1_000_000L;
            }
            if (machinePos != null) {
                OmniBatteryNetwork.CHANNEL.sendToServer(new SetMachineCapPacket(
                        machinePos.getX(), machinePos.getY(), machinePos.getZ(), v));
            } else if (stack != null) {
                OmniBatteryNetwork.CHANNEL.sendToServer(new SetCustomCapPacket(v));
            }
            onClose();
        }).bounds(cx - 90, cy + 20, 86, 20).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(cx + 4, cy + 20, 86, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.drawCenteredString(this.font, "自定义灌入上限（FE）", cx, cy - 40, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                machinePos != null
                        ? "应用在这台机器：机器电量达到此值就不再灌"
                        : "自定义 = 过载，但灌到该数值就停，不会无限吃电",
                cx, cy - 26, 0xA0A6B0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
