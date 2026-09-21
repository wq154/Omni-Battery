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
        this.imageHeight = 312;
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
                .bounds(x + 126, y + 98, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1))
                .bounds(x + 196, y + 98, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("−"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 4))
                .bounds(x + 126, y + 124, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 3))
                .bounds(x + 196, y + 124, 22, 20).build());

        accessBtn = addRenderableWidget(Button.builder(
                Component.literal(menu.getAccessDisplay()),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 5)
        ).bounds(x + 126, y + 150, 92, 20).build());

        invChargeBtn = addRenderableWidget(Button.builder(
                Component.literal(chargeLabel(true, menu.isChargeInventory())),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 6)
        ).bounds(x + 152, y + 228, 46, 20).build());
        curChargeBtn = addRenderableWidget(Button.builder(
                Component.literal(chargeLabel(false, menu.isChargeCurios())),
                b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 7)
        ).bounds(x + 202, y + 228, 46, 20).build());
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
        if (page == 1) {
            drawConfigPage(graphics, x, y, mouseX, mouseY);
            return;
        }

        drawBindDropdown(graphics, x, y, mouseX, mouseY);
        chip(graphics, x, y + 22, 22, 18, "配", mouseX, mouseY, -1, "用电配置（全部打标签机器）", false);

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

        drawPanelRow(graphics, x, y, 98, "速度", menu.getRateDisplay(), 0xFF4EEBFF);
        drawPanelRow(graphics, x, y, 124, "范围", menu.getRangeDisplay(), 0xFFFFE878);
        drawPanelRow(graphics, x, y, 150, "权限", menu.getAccessDisplay(), accessColor());
        if (accessBtn != null) accessBtn.setMessage(Component.literal(menu.getAccessDisplay()));

        // ---- 实时速率：吸电 / 供电（各自独立计算）----
        long absorbed = menu.getAbsorbedPerSecond();
        long supplied = menu.getSuppliedPerSecond();
        drawPanelRow(graphics, x, y, 176, "吸电", fmtPerTick(absorbed), absorbed > 0 ? 0xFFFFA640 : 0xFF7A8698);
        drawPanelRow(graphics, x, y, 202, "供电", fmtPerTick(supplied), supplied > 0 ? 0xFF4EE8D8 : 0xFF7A8698);

        // ---- 玩家供电开关（物品栏 / 饰品栏）----
        drawPanelRow(graphics, x, y, 228, "玩家供电", "", 0xFF8FA6BA);
        if (invChargeBtn != null) invChargeBtn.setMessage(Component.literal(chargeLabel(true, menu.isChargeInventory())));
        if (curChargeBtn != null) curChargeBtn.setMessage(Component.literal(chargeLabel(false, menu.isChargeCurios())));

        // ---- 趋势图（最近 60 秒吸电/供电）----
        drawTrend(graphics, x, y);

        String hint = "机器传电必须放置电池｜权限决定谁可用";
        graphics.drawString(font, hint, x + (imageWidth - font.width(hint)) / 2, y + 299, 0xFF8FA6BA, false);
    }

    /** 每秒累计值 -> 每 tick 速率显示（与"速度"设置同单位）。 */
    private String fmtPerTick(long perSecond) {
        return OmniBatteryMenu.fmt(perSecond / 20) + " FE/t";
    }

    /** 绘制最近 60 秒的吸电（橙）/供电（青）趋势折线。 */
    private void drawTrend(GuiGraphics graphics, int x, int y) {
        int tx = x + 10, ty = y + 256, tw = imageWidth - 20, th = 36;
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

    // ================= 用电配置子页（1.20.1 深色风格版）=================
    private int page = 0;              // 0 = 主界面，1 = 用电配置
    private int cfgPage = 0;
    private int openDropdown = -1;
    private boolean bindOpen = false;
    private String hint = null;
    private static final int CFG_PER_PAGE = 8;
    private static final int CFG_ROW0 = 74;
    private static final int CFG_ROW_H = 20;
    private static final int CFG_FILTER_Y = 32;
    private static final int CFG_TOOL_Y = 50;
    private static final int CFG_HEAD_Y = 68;
    private static final int BIND_DW = 76;
    private static final int BIND_DY = 8;
    private static final int BIND_DH = 16;
    private int cfgFilter = 0;
    private int cfgSortKey = 0;
    private boolean cfgDesc = true;
    private final java.util.Map<Integer, String> NAMES = new java.util.HashMap<>();

    private java.util.List<cn.ayaka.omnibattery.OmniBatteryBlockEntity.TargetInfo> cfgList() {
        if (minecraft == null || minecraft.level == null) return java.util.List.of();
        if (minecraft.level.getBlockEntity(menu.getMenuPos())
                instanceof cn.ayaka.omnibattery.OmniBatteryBlockEntity cbe) {
            return cbe.getCfgTargets();
        }
        return java.util.List.of();
    }

    private java.util.List<int[]> cfgView() {
        var all = cfgList();
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            var t = all.get(i);
            int fm = t.mode() == 3 ? 2 : t.mode();          // 自定义归入"过载"筛选
            if (cfgFilter != 0 && fm != cfgFilter - 1) continue;
            out.add(new int[]{i, t.x(), t.y(), t.z(), t.mode(),
                    (int) t.absorb(), (int) (t.absorb() >>> 32),
                    (int) t.supply(), (int) (t.supply() >>> 32)});
            NAMES.put(i, t.name() == null ? "" : t.name());
        }
        out.sort((u, v) -> {
            long pu = cfgSortKey == 0 ? ((long) u[6] << 32 | (u[5] & 0xFFFFFFFFL))
                                      : ((long) u[8] << 32 | (u[7] & 0xFFFFFFFFL));
            long pv = cfgSortKey == 0 ? ((long) v[6] << 32 | (v[5] & 0xFFFFFFFFL))
                                      : ((long) v[8] << 32 | (v[7] & 0xFFFFFFFFL));
            return cfgDesc ? Long.compare(pv, pu) : Long.compare(pu, pv);
        });
        return out;
    }

    private long capOf(int idx) {
        var all = cfgList();
        return idx >= 0 && idx < all.size() ? all.get(idx).cap() : 1_000_000L;
    }

    private static long cfgRate(int[] row, boolean supply) {
        int lo = supply ? row[7] : row[5];
        int hi = supply ? row[8] : row[6];
        return (long) hi << 32 | (lo & 0xFFFFFFFFL);
    }

    /** 面板按钮（深色风格）。 */
    private void chip(GuiGraphics g, int bx, int by, int bw, int bh, String label,
                      int mouseX, int mouseY, int id, String tip, boolean on) {
        boolean hov = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
        g.fill(bx, by, bx + bw, by + bh, 0xFF05070A);
        g.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, hov ? 0xFF2E4A6A : 0xFF173F69);
        if (on) g.fill(bx + 1, by + bh - 3, bx + bw - 1, by + bh - 1, 0xFF4EEBFF);
        g.drawString(font, label, bx + (bw - font.width(label)) / 2, by + (bh - 8) / 2 + 1,
                hov ? 0xFFFFFFFF : 0xFFCFE4F5, false);
        if (hov && hint == null) hint = tip;
    }

    private net.minecraft.world.item.ItemStack clientSticker() {
        if (minecraft == null || minecraft.player == null) return null;
        for (net.minecraft.world.InteractionHand h : net.minecraft.world.InteractionHand.values()) {
            net.minecraft.world.item.ItemStack st = minecraft.player.getItemInHand(h);
            if (st.getItem() instanceof cn.ayaka.omnibattery.MachineStickerItem
                    && cn.ayaka.omnibattery.MachineStickerItem.hasBind(st)) return st;
        }
        var inv = minecraft.player.getInventory();
        for (int ci = 0; ci < inv.getContainerSize(); ci++) {
            net.minecraft.world.item.ItemStack st = inv.getItem(ci);
            if (st.getItem() instanceof cn.ayaka.omnibattery.MachineStickerItem
                    && cn.ayaka.omnibattery.MachineStickerItem.hasBind(st)) return st;
        }
        return null;
    }

    private java.util.List<Object[]> clientBinds() {
        var st = clientSticker();
        return st == null ? java.util.List.of() : cn.ayaka.omnibattery.MachineStickerItem.getBinds(st);
    }

    private int clientBindIndex() {
        var st = clientSticker();
        return st == null ? -1 : cn.ayaka.omnibattery.MachineStickerItem.getBindIndex(st);
    }

    private int bindDropdownX() {
        int badgeW = font.width(String.valueOf(menu.getTier().ordinal() + 1)) + 40;
        return leftPos + imageWidth - 8 - badgeW - 4 - BIND_DW;
    }

    /** 用电配置子页。 */
    private void drawConfigPage(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        hint = null;
        var view = cfgView();
        int pages = Math.max(1, (view.size() + CFG_PER_PAGE - 1) / CFG_PER_PAGE);
        if (cfgPage >= pages) cfgPage = pages - 1;
        if (cfgPage < 0) cfgPage = 0;
        int start = cfgPage * CFG_PER_PAGE;

        g.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xF0050710, 0xF0142336);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xEE101522);
        g.fill(x + 6, y + 6, x + imageWidth - 6, y + 30, 0xCC173F69);
        g.fillGradient(x + 6, y + 30, x + imageWidth - 6, y + 32, 0xFF4EEBFF, 0xFF7B5CFF);
        g.drawCenteredString(font, "用电配置 · 共 " + view.size() + " 台", x + imageWidth / 2, y + 12, 0xFFFFFF);
        chip(g, x + imageWidth - 56, y + 7, 48, 16, "返回", mouseX, mouseY, -1, "返回主界面", false);

        String[] filters = {"全部", "吸电", "供电", "过载", "自定义"};
        for (int f = 0; f < filters.length; f++) {
            int bx = x + 8 + f * 40;
            chip(g, bx, y + CFG_FILTER_Y, 36, 16, filters[f], mouseX, mouseY, 500 + f,
                    "只显示：" + filters[f], cfgFilter == f);
        }
        chip(g, x + 8, y + CFG_TOOL_Y, 58, 16,
                (cfgSortKey == 0 ? "按吸电" : "按供电") + (cfgDesc ? "↓" : "↑"),
                mouseX, mouseY, 510, "切换排序字段", false);
        chip(g, x + 70, y + CFG_TOOL_Y, 42, 16, cfgDesc ? "降序" : "升序",
                mouseX, mouseY, 511, "切换正序/反序", false);
        chip(g, x + imageWidth - 96, y + CFG_TOOL_Y, 42, 16, "◀ 上页", mouseX, mouseY, 520, "上一页", false);
        chip(g, x + imageWidth - 50, y + CFG_TOOL_Y, 42, 16, "下页 ▶", mouseX, mouseY, 521, "下一页", false);

        g.drawString(font, "机器", x + 12, y + CFG_HEAD_Y, 0xFF9FB3C8, false);
        g.drawString(font, "吸电 / 供电 (FE/t)", x + 78, y + CFG_HEAD_Y, 0xFF9FB3C8, false);

        for (int row = 0; row < CFG_PER_PAGE && start + row < view.size(); row++) {
            int[] r = view.get(start + row);
            int ry = y + CFG_ROW0 + row * CFG_ROW_H;
            g.fill(x + 6, ry, x + imageWidth - 6, ry + 18, 0xCC0A0F18);
            g.fill(x + 7, ry + 1, x + imageWidth - 7, ry + 17, 0xCC173F69);

            String key = NAMES.getOrDefault(r[0], "");
            String name = key == null || key.isEmpty()
                    ? "机器" : net.minecraft.network.chat.Component.translatable(key).getString();
            if (name.length() > 7) name = name.substring(0, 7) + "…";
            g.drawString(font, name, x + 10, ry + 5, 0xFFFFFFFF, false);

            long ab = cfgRate(r, false), su = cfgRate(r, true);
            g.drawString(font,
                    cn.ayaka.omnibattery.OmniBatteryMenu.fmtShort(ab / 20L) + " / "
                            + cn.ayaka.omnibattery.OmniBatteryMenu.fmtShort(su / 20L),
                    x + 78, ry + 5, (ab > 0 ? 0xFFFFA640 : (su > 0 ? 0xFF6AE8E0 : 0xFF6E7076)), false);

            String[] modes = {"吸电", "供电", "过载", "自定义"};
            String modeName = r[4] >= 0 && r[4] < modes.length ? modes[r[4]] : "?";
            chip(g, x + imageWidth - 52, ry + 1, 44, 16, modeName + (openDropdown == r[0] ? "▲" : "▼"),
                    mouseX, mouseY, 600 + r[0], "点击选择模式", false);
        }
        if (view.isEmpty()) {
            g.drawString(font, "（本维度还没有打标签的机器）", x + 12, y + CFG_ROW0 + 4, 0xFF7C8DA3, false);
        }

        if (openDropdown >= 0) {
            for (int row = 0; row < CFG_PER_PAGE && start + row < view.size(); row++) {
                int[] r = view.get(start + row);
                if (r[0] != openDropdown) continue;
                int ry = y + CFG_ROW0 + row * CFG_ROW_H;
                String[] opts = {"吸电", "供电", "过载", "自定义", "清除标签"};
                int h = opts.length * 14;
                boolean up = (ry + 18 + h) > (y + imageHeight - 20);
                int top = up ? (ry - h) : (ry + 18);
                g.fill(x + imageWidth - 74, top - 1, x + imageWidth - 4, top + h + 1, 0xFF05070A);
                for (int k = 0; k < opts.length; k++) {
                    int oy = top + k * 14;
                    boolean hov = mouseX >= x + imageWidth - 74 && mouseX < x + imageWidth - 4
                            && mouseY >= oy && mouseY < oy + 13;
                    g.fill(x + imageWidth - 73, oy, x + imageWidth - 5, oy + 13, hov ? 0xFF2E4A6A : 0xFF173F69);
                    g.drawString(font, opts[k], x + imageWidth - 68, oy + 3,
                            hov ? 0xFFFFFFFF : 0xFFCFE4F5, false);
                }
                break;
            }
        }
        String foot = hint != null ? hint : "点模式按钮改标签 · 下拉可切电池";
        g.drawString(font, foot, x + 10, y + imageHeight - 12, 0xFF7C8DA3, false);
    }

    /** 顶部"切换电池"下拉（徽章左侧，不遮挡徽章）。 */
    private void drawBindDropdown(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        var binds = clientBinds();
        int bdx = bindDropdownX();
        int bcur = clientBindIndex();
        String label = binds.isEmpty() ? "未绑定" : ("电池 " + (bcur + 1) + "/" + binds.size());
        chip(g, bdx, y + BIND_DY, BIND_DW, BIND_DH, label + (bindOpen ? " ▲" : " ▼"),
                mouseX, mouseY, -5, binds.isEmpty() ? "先在电池上右键绑定" : "切换要打开的电池", false);
        if (bindOpen && !binds.isEmpty()) {
            for (int i = 0; i < binds.size(); i++) {
                Object[] bo = binds.get(i);
                int oy = y + BIND_DY + BIND_DH + 2 + i * 14;
                boolean hov = mouseX >= bdx && mouseX < bdx + BIND_DW && mouseY >= oy && mouseY < oy + 14;
                g.fill(bdx, oy, bdx + BIND_DW, oy + 14, 0xFF05070A);
                g.fill(bdx + 1, oy + 1, bdx + BIND_DW - 1, oy + 13, hov ? 0xFF2E4A6A : 0xFF173F69);
                String lbl = "#" + (i + 1) + " " + bo[0] + "," + bo[1] + "," + bo[2];
                if (font.width(lbl) > BIND_DW - 8) lbl = "#" + (i + 1);
                g.drawString(font, lbl, bdx + 4, oy + 3, hov ? 0xFFFFFFFF : 0xFFCFE4F5, false);
            }
        }
    }

    /** 读取光标下控件 id（-99 = 无）。 */
    private int hitTest(double mx0, double my0) {
        int mx = (int) mx0, my = (int) my0;
        int x = leftPos, y = topPos;
        if (page == 1) {
            if (mx >= x + imageWidth - 56 && mx < x + imageWidth - 8 && my >= y + 7 && my < y + 23) return -1;
            for (int f = 0; f < 5; f++) {
                int bx = x + 8 + f * 40;
                if (mx >= bx && mx < bx + 36 && my >= y + CFG_FILTER_Y && my < y + CFG_FILTER_Y + 16) return 500 + f;
            }
            if (mx >= x + 8 && mx < x + 66 && my >= y + CFG_TOOL_Y && my < y + CFG_TOOL_Y + 16) return 510;
            if (mx >= x + 70 && mx < x + 112 && my >= y + CFG_TOOL_Y && my < y + CFG_TOOL_Y + 16) return 511;
            if (mx >= x + imageWidth - 96 && mx < x + imageWidth - 54 && my >= y + CFG_TOOL_Y && my < y + CFG_TOOL_Y + 16) return 520;
            if (mx >= x + imageWidth - 50 && mx < x + imageWidth - 8 && my >= y + CFG_TOOL_Y && my < y + CFG_TOOL_Y + 16) return 521;
            var view = cfgView();
            int start = cfgPage * CFG_PER_PAGE;
            if (openDropdown >= 0) {
                for (int row = 0; row < CFG_PER_PAGE && start + row < view.size(); row++) {
                    int[] r = view.get(start + row);
                    if (r[0] != openDropdown) continue;
                    int ry = y + CFG_ROW0 + row * CFG_ROW_H;
                    int h = 5 * 14;
                    boolean up = (ry + 18 + h) > (y + imageHeight - 20);
                    int top = up ? (ry - h) : (ry + 18);
                    for (int k = 0; k < 5; k++) {
                        int oy = top + k * 14;
                        if (mx >= x + imageWidth - 74 && mx < x + imageWidth - 4 && my >= oy && my < oy + 13) {
                            return 400 + r[0] * 5 + k;
                        }
                    }
                    break;
                }
            }
            for (int row = 0; row < CFG_PER_PAGE && start + row < view.size(); row++) {
                int[] r = view.get(start + row);
                int ry = y + CFG_ROW0 + row * CFG_ROW_H;
                if (mx >= x + imageWidth - 52 && mx < x + imageWidth - 8 && my >= ry + 1 && my < ry + 17) {
                    return 600 + r[0];
                }
            }
            return -99;
        }
        if (mx >= x && mx < x + 22 && my >= y + 22 && my < y + 40) return -1;   // 主界面"用电配置"入口
        int bdx = bindDropdownX();
        if (mx >= bdx && mx < bdx + BIND_DW && my >= y + BIND_DY && my < y + BIND_DY + BIND_DH) return -5;
        if (bindOpen) {
            var binds = clientBinds();
            for (int i = 0; i < binds.size(); i++) {
                int oy = y + BIND_DY + BIND_DH + 2 + i * 14;
                if (mx >= bdx && mx < bdx + BIND_DW && my >= oy && my < oy + 14) return 900 + i;
            }
        }
        return -99;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int id = hitTest(mx, my);
        if (id == -99) return super.mouseClicked(mx, my, button);
        if (id == -1) {                     // 返回 / 打开用电配置
            if (page == 1) { page = 0; openDropdown = -1; bindOpen = false; }
            else { page = 1; cfgPage = 0; openDropdown = -1; bindOpen = false; }
            return true;
        }
        if (id == -5) { bindOpen = !bindOpen; return true; }
        if (id >= 900) { bindOpen = false; return press(900 + (id - 900)); }
        if (id == 510) { cfgSortKey = 1 - cfgSortKey; openDropdown = -1; return true; }
        if (id == 511) { cfgDesc = !cfgDesc; openDropdown = -1; return true; }
        if (id == 520) { cfgPage = Math.max(0, cfgPage - 1); openDropdown = -1; return true; }
        if (id == 521) { cfgPage++; openDropdown = -1; return true; }
        if (id >= 500 && id < 505) { cfgFilter = id - 500; cfgPage = 0; openDropdown = -1; return true; }
        if (id >= 600) {                    // 行内模式按钮：展开/收起下拉
            int idx = id - 600;
            openDropdown = (openDropdown == idx) ? -1 : idx;
            return true;
        }
        if (id >= 400) {                    // 下拉选项：0 吸电 / 1 供电 / 2 过载 / 3 自定义 / 4 清除
            int snap = (id - 400) / 5, opt = (id - 400) % 5;
            openDropdown = -1;
            if (opt == 3) {                 // 自定义 → 弹数值输入框
                cn.ayaka.omnibattery.client.ClientHooks.openMachineCap(
                        0, 0, 0, capOf(snap));   // 坐标从下面补正
                var all = cfgList();
                if (snap >= 0 && snap < all.size()) {
                    var t = all.get(snap);
                    cn.ayaka.omnibattery.client.ClientHooks.openMachineCap(t.x(), t.y(), t.z(), t.cap());
                }
                return true;
            }
            return press(400 + snap * 5 + opt);
        }
        return true;
    }

    /** 菜单按钮回传（主界面"切换电池"等）。 */
    private boolean press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            return true;
        }
        return false;
    }
}
