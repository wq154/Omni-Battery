package cn.ayaka.omnibattery;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class OmniBatteryScreen extends AbstractContainerScreen<OmniBatteryMenu> {
    private Button modeBtn;
    private Button accessBtn;
    private Button invChargeBtn;
    private Button curChargeBtn;

    public OmniBatteryScreen(OmniBatteryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 276;
        this.imageHeight = 344;
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

        invChargeBtn = addRenderableWidget(Button.builder(
                Component.literal(chargeLabel(true, menu.isChargeInventory())),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 6)
        ).bounds(x + 152, y + 258, 46, 20).build());
        curChargeBtn = addRenderableWidget(Button.builder(
                Component.literal(chargeLabel(false, menu.isChargeCurios())),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 7)
        ).bounds(x + 202, y + 258, 46, 20).build());
    }

    private static String chargeLabel(boolean inventory, boolean on) {
        return (inventory ? "物品:" : "饰品:") + (on ? "开" : "关");
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
        drawPanelRow(graphics, x, y, 165, "权限", menu.getAccessDisplay(), accessColor());
        if (accessBtn != null) accessBtn.setMessage(Component.literal(menu.getAccessDisplay()));

        // ---- 实时速率：吸电 / 供电（各自独立计算）----
        long absorbed = menu.getAbsorbedPerSecond();
        long supplied = menu.getSuppliedPerSecond();
        drawPanelRow(graphics, x, y, 196, "吸电", fmtPerTick(absorbed), absorbed > 0 ? 0xFFFFA640 : 0xFF7A8698);
        drawPanelRow(graphics, x, y, 227, "供电", fmtPerTick(supplied), supplied > 0 ? 0xFF4EE8D8 : 0xFF7A8698);

        // ---- 玩家供电开关（物品栏 / 饰品栏）----
        drawPanelRow(graphics, x, y, 258, "玩家供电", "", 0xFF8FA6BA);
        if (invChargeBtn != null) invChargeBtn.setMessage(Component.literal(chargeLabel(true, menu.isChargeInventory())));
        if (curChargeBtn != null) curChargeBtn.setMessage(Component.literal(chargeLabel(false, menu.isChargeCurios())));

        // ---- 趋势图（最近 60 秒吸电/供电）----
        drawTrend(graphics, x, y);

        String hint = "机器传电必须放置电池｜私有电仅主人/授权标签可用";
        graphics.drawString(font, hint, x + (imageWidth - font.width(hint)) / 2, y + 331, 0xFF8FA6BA, false);
    }

    /** 每秒累计值 -> 每 tick 速率显示（与"速度"设置同单位）。 */
    private String fmtPerTick(long perSecond) {
        return OmniBatteryMenu.fmt(perSecond / 20) + " FE/t";
    }

    /** 绘制最近 60 秒的吸电（橙）/供电（青）趋势折线。 */
    private void drawTrend(GuiGraphics graphics, int x, int y) {
        int tx = x + 10, ty = y + 288, tw = imageWidth - 20, th = 36;
        graphics.fill(tx, ty, tx + tw, ty + th, 0xFF05080D);
        graphics.fill(tx + 1, ty + 1, tx + tw - 1, ty + th - 1, 0xFF111A26);
        graphics.drawString(font, "每秒趋势", tx + 2, ty - 10, 0xFF9FB4C8, false);
        graphics.fill(tx + 46, ty - 9, tx + 50, ty - 5, 0xFFFFA640);
        graphics.drawString(font, "吸电", tx + 52, ty - 10, 0xFFFFA640, false);
        graphics.fill(tx + 74, ty - 9, tx + 78, ty - 5, 0xFF4EE8D8);
        graphics.drawString(font, "供电", tx + 80, ty - 10, 0xFF4EE8D8, false);

        int size = menu.getHistorySize();
        if (size <= 1) return;
        long maxV = 1L;
        for (int i = 0; i < size; i++) {
            maxV = Math.max(maxV, Math.max(menu.getAbsorbHistory(i), menu.getSupplyHistory(i)));
        }
        int px0 = tx + 3, py0 = ty + 3, pw = tw - 6, ph = th - 6;
        for (int i = 0; i < size - 1; i++) {
            int ax1 = px0 + (int) ((long) i * pw / (size - 1));
            int ax2 = px0 + (int) ((long) (i + 1) * pw / (size - 1));
            int ay1 = py0 + ph - (int) (menu.getAbsorbHistory(i) * ph / maxV);
            int ay2 = py0 + ph - (int) (menu.getAbsorbHistory(i + 1) * ph / maxV);
            drawLine(graphics, ax1, ay1, ax2, ay2, 0xFFFFA640);
            int sy1 = py0 + ph - (int) (menu.getSupplyHistory(i) * ph / maxV);
            int sy2 = py0 + ph - (int) (menu.getSupplyHistory(i + 1) * ph / maxV);
            drawLine(graphics, ax1, sy1, ax2, sy2, 0xFF4EE8D8);
        }
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy, guard = 0;
        while (guard++ < 10000) {
            graphics.fill(x1, y1, x1 + 1, y1 + 1, color);
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 < dx) { err += dx; y1 += sy; }
        }
    }

    /** 权限颜色：私人=红，队伍=蓝，公开=绿。 */
    private int accessColor() {
        return switch (menu.getAccess()) {
            case PRIVATE -> 0xFFFF8A8A;
            case TEAM -> 0xFF8AC8FF;
            case PUBLIC -> 0xFF7DFF99;
        };
    }

    private void drawPanelRow(GuiGraphics graphics, int x, int y, int rowY, String label, String value, int color) {
        int rx = x + 104, ry = y + rowY;
        graphics.fill(rx, ry, rx + 148, ry + 22, 0xAA07101B);
        graphics.fill(rx + 1, ry + 1, rx + 147, ry + 21, 0x5530526E);
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
