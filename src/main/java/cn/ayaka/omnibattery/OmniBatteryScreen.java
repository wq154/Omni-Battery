package cn.ayaka.omnibattery;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class OmniBatteryScreen extends AbstractContainerScreen<OmniBatteryMenu> {
    private Button modeBtn;
    private Button accessBtn;

    public OmniBatteryScreen(OmniBatteryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 248;
        this.imageHeight = 206;
        this.inventoryLabelY = 9999;
        this.titleLabelY = 9999;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;

        modeBtn = addRenderableWidget(Button.builder(
                Component.literal(menu.getMode().display()),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0)
        ).bounds(x + 126, y + 72, 92, 20).build());

        addRenderableWidget(Button.builder(Component.literal("−"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 2))
                .bounds(x + 126, y + 103, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1))
                .bounds(x + 196, y + 103, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("−"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 4))
                .bounds(x + 126, y + 134, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 3))
                .bounds(x + 196, y + 134, 22, 20).build());

        accessBtn = addRenderableWidget(Button.builder(
                Component.literal(menu.getAccessDisplay()),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 5)
        ).bounds(x + 126, y + 165, 92, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xF0050710, 0xF0142336);
        graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xEE101522);
        graphics.fill(x + 6, y + 6, x + imageWidth - 6, y + 30, 0xCC173F69);
        graphics.fillGradient(x + 6, y + 30, x + imageWidth - 6, y + 32, 0xFF4EEBFF, 0xFF7B5CFF);

        Component titleText = Component.literal("OMNI BATTERY CONTROL");
        graphics.drawString(font, titleText, x + (imageWidth - font.width(titleText)) / 2, y + 12, 0xFFFFFF, false);

        int bx = x + 20, by = y + 48, bw = 68, bh = 100;
        graphics.fill(bx + 20, by - 8, bx + 48, by, 0xFFCAD6E6);
        graphics.fill(bx + 14, by, bx + bw - 14, by + bh, 0xFF05070B);
        graphics.fill(bx + 17, by + 3, bx + bw - 17, by + bh - 3, 0xFF18202E);
        int innerH = bh - 12;
        int fillH = (int) (innerH * menu.getEnergyRatio());
        int fy1 = by + bh - 6 - fillH;
        graphics.fillGradient(bx + 22, fy1, bx + bw - 22, by + bh - 6, tierColorDark(), tierColorBright());
        for (int i = 0; i < 4; i++) {
            int yy = by + 16 + i * 18;
            graphics.fill(bx + 24, yy, bx + bw - 24, yy + 2, 0x5528E6FF);
        }
        graphics.drawString(font, menu.getTier().display(), bx + (bw - font.width(menu.getTier().display())) / 2, by + bh + 6, 0xFFFFE08A, false);

        String energyStr = String.format("%,d / %,d FE", menu.getEnergy(), menu.getMaxEnergy());
        graphics.drawString(font, energyStr, x + 106, y + 48, 0xFFBDF7FF, false);
        drawMiniBar(graphics, x + 106, y + 60, 116, 8, menu.getEnergyRatio());

        drawPanelRow(graphics, x, y, 72, "模式", menu.getMode().display(), 0xFFBDF7FF);
        if (modeBtn != null) modeBtn.setMessage(Component.literal(menu.getMode().display()));

        drawPanelRow(graphics, x, y, 103, "速度", menu.getRateDisplay(), 0xFF4EEBFF);
        drawPanelRow(graphics, x, y, 134, "范围", menu.getRangeDisplay(), 0xFFFFE878);
        drawPanelRow(graphics, x, y, 165, "权限", menu.getAccessDisplay(), menu.isPublicAccess() ? 0xFF7DFF99 : 0xFFFF8A8A);
        if (accessBtn != null) accessBtn.setMessage(Component.literal(menu.getAccessDisplay()));

        String hint = "机器传电必须放置电池｜私有电仅主人/授权标签可用";
        graphics.drawString(font, hint, x + (imageWidth - font.width(hint)) / 2, y + 191, 0xFF8FA6BA, false);
    }

    private void drawPanelRow(GuiGraphics graphics, int x, int y, int rowY, String label, String value, int color) {
        int rx = x + 104, ry = y + rowY;
        graphics.fill(rx, ry, rx + 120, ry + 22, 0xAA07101B);
        graphics.fill(rx + 1, ry + 1, rx + 119, ry + 21, 0x5530526E);
        graphics.drawString(font, label + ":", rx + 8, ry + 7, 0xFFD6E6F2, false);
        graphics.drawString(font, value, rx + 46, ry + 7, color, false);
    }

    private void drawMiniBar(GuiGraphics graphics, int x, int y, int w, int h, double ratio) {
        graphics.fill(x, y, x + w, y + h, 0xFF05080D);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF1D2B3E);
        int fw = (int) ((w - 2) * ratio);
        graphics.fillGradient(x + 1, y + 1, x + 1 + fw, y + h - 1, tierColorDark(), tierColorBright());
    }

    private int tierColorDark() {
        return switch (menu.getTier()) {
            case LOW -> 0xFF256DFF;
            case MEDIUM -> 0xFF28B86A;
            case ADVANCED -> 0xFFE69A28;
            case ELITE -> 0xFF9F45FF;
            case ULTIMATE -> 0xFF24E8FF;
        };
    }

    private int tierColorBright() {
        return switch (menu.getTier()) {
            case LOW -> 0xFF62E9FF;
            case MEDIUM -> 0xFF7DFF99;
            case ADVANCED -> 0xFFFFEF75;
            case ELITE -> 0xFFFF83FF;
            case ULTIMATE -> 0xFFFFFFFF;
        };
    }
}
