package com.society.sunlitmarket.client;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.market.ChartView;
import com.society.sunlitmarket.market.MarketData;
import com.society.sunlitmarket.net.ChartRequestMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only candle panel. Every number comes from the server through {@link ChartView}; the client
 * never prices anything itself.
 *
 * <p>Colour convention follows the Chinese market: red = up, green = down. All chrome is drawn by
 * {@link Ui} so the panel matches the shop window exactly.
 *
 * <p>Client-only.
 */
public final class ChartScreen extends Screen {
    private static final int PAD = 8;
    private static final int HEADER_H = 20;
    private static final int TAB_H = 16;
    private static final int FOOTER_H = 18;
    private static final int GAP = 6;
    private static final int CLOSE = 9;
    private static final int GUTTER = 36;
    private static final int PERIOD_W = 44;

    private final Screen parent;
    private ChartView view;
    private int panelW;
    private int panelH;
    private int left;
    private int top;
    private int contentLeft;
    private int contentRight;
    private int tabY;
    private int plotX0;
    private int plotY0;
    private int plotX1;
    private int plotY1;
    private int footerY;
    private int closeX;
    private int closeY;
    private int period = MarketData.PERIODS[0];
    private final List<Rect> indexTabs = new ArrayList<>();
    private Rect dayTab = Rect.EMPTY;
    private Rect hourTab = Rect.EMPTY;

    public ChartScreen(ChartView view, Screen parent) {
        super(Component.literal("行情"));
        this.view = view == null ? new ChartView() : view;
        this.parent = parent;
        this.period = this.view.period > 0 ? this.view.period : MarketData.PERIODS[0];
    }

    /** Called when a newer payload arrives for the index/period already on screen. */
    public void update(ChartView next) {
        if (next == null) return;
        this.view = next;
        if (next.period > 0) this.period = next.period;
    }

    @Override
    protected void init() {
        panelW = Math.min(380, width - 20);
        panelH = Math.min(240, Math.max(180, height - 20));
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;

        contentLeft = left + PAD;
        contentRight = left + panelW - PAD;
        tabY = top + HEADER_H + GAP;
        plotX0 = contentLeft;
        plotX1 = contentRight - GUTTER;
        plotY0 = tabY + TAB_H + GAP;
        footerY = top + panelH - GAP - FOOTER_H;
        plotY1 = footerY - GAP;

        closeX = left + panelW - PAD - CLOSE;
        closeY = top + (HEADER_H - CLOSE) / 2;
        buildTabs();
    }

    // ---------------------------------------------------------------- tabs

    private void buildTabs() {
        indexTabs.clear();
        int x = contentLeft;
        int limit = contentRight - PERIOD_W * 2 - GAP - GAP;
        for (ChartView.Entry entry : view.indices) {
            String label = label(entry);
            int w = Math.min(96, font.width(label) + 18);
            if (x + w > limit) break;
            indexTabs.add(new Rect(x, tabY, w, TAB_H));
            x += w + 4;
        }
        dayTab = new Rect(contentRight - PERIOD_W * 2 - GAP, tabY, PERIOD_W, TAB_H);
        hourTab = new Rect(contentRight - PERIOD_W, tabY, PERIOD_W, TAB_H);
    }

    private static String label(ChartView.Entry entry) {
        return entry.name == null || entry.name.isEmpty() ? entry.id : entry.name;
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(0, 0, width, height, Ui.SCRIM);

        Ui.frame(g, left, top, panelW, panelH);
        Ui.rect(g, left + 1, top + 1, panelW - 2, HEADER_H - 1, Ui.PANEL_HEAD);
        Ui.rect(g, left + 1, top + 1, 2, HEADER_H - 1, Ui.GOLD);
        Ui.hline(g, left + 1, top + HEADER_H, panelW - 2, Ui.EDGE_SOFT);

        String head = "行情 · " + (view.name == null || view.name.isEmpty() ? "—" : view.name);
        g.drawString(font, font.plainSubstrByWidth(head, panelW - 2 * PAD - CLOSE - 12),
                left + PAD + 2, top + (HEADER_H - 8) / 2, Ui.TEXT, false);
        boolean hoverClose = Ui.hit(mouseX, mouseY, closeX - 4, closeY - 4, CLOSE + 8, CLOSE + 8);
        Ui.closeButton(g, closeX, closeY, CLOSE, hoverClose);

        buildTabs();
        for (int i = 0; i < indexTabs.size(); i++) {
            Rect rect = indexTabs.get(i);
            ChartView.Entry entry = view.indices.get(i);
            boolean active = entry.id != null && entry.id.equals(view.id);
            Ui.chip(g, font, label(entry), rect.x, rect.y, rect.w, rect.h, active, rect.contains(mouseX, mouseY));
        }
        drawPeriodTab(g, dayTab, "1日", period == MarketData.PERIODS[0], mouseX, mouseY);
        drawPeriodTab(g, hourTab, "4时", period == MarketData.PERIODS[1], mouseX, mouseY);

        drawPlot(g, mouseX, mouseY);
        drawFooter(g);
    }

    private void drawPeriodTab(GuiGraphics g, Rect rect, String text, boolean active, int mouseX, int mouseY) {
        if (rect.w <= 0) return;
        Ui.chip(g, font, text, rect.x, rect.y, rect.w, rect.h, active, rect.contains(mouseX, mouseY));
    }

    private void drawPlot(GuiGraphics g, int mouseX, int mouseY) {
        Ui.sunken(g, plotX0, plotY0, plotX1 - plotX0, plotY1 - plotY0);

        List<int[]> candles = view.candles;
        int plotW = plotX1 - plotX0;
        int plotH = plotY1 - plotY0;
        if (candles.isEmpty() || plotW < 40 || plotH < 30) {
            String empty = "暂无数据 · 服务器每 5 秒采样一次";
            g.drawString(font, empty, plotX0 + (plotW - font.width(empty)) / 2, plotY0 + plotH / 2 - 4,
                    Ui.TEXT_FAINT, false);
            return;
        }

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int[] c : candles) {
            min = Math.min(min, c[3]);
            max = Math.max(max, c[2]);
        }
        if (max - min < 8) {
            int mid = (max + min) / 2;
            min = mid - 4;
            max = mid + 4;
        }
        int pad = Math.max(2, (max - min) / 16);
        min -= pad;
        max += pad;

        for (int i = 1; i <= 4; i++) {
            int y = plotY0 + plotH * i / 5;
            Ui.hline(g, plotX0, y, plotW, 0x18FFFFFF);
            String text = Integer.toString(max - (max - min) * i / 5);
            g.drawString(font, text, plotX1 + 6, y - 4, Ui.TEXT_FAINT, false);
        }
        Ui.hline(g, plotX0, plotY0, plotW, 0x18FFFFFF);
        Ui.vline(g, plotX1, plotY0, plotH, 0x18FFFFFF);

        int n = candles.size();
        int step = Math.max(3, plotW / n);
        int bodyW = Math.max(1, Math.min(11, step - 2));
        int startX = plotX0 + Math.max(0, (plotW - n * step) / 2);

        int hover = -1;
        int lastCloseY = 0;
        int lastColor = Ui.TEXT_DIM;
        for (int i = 0; i < n; i++) {
            int[] c = candles.get(i);
            int slot = startX + i * step;
            int center = slot + step / 2;
            int bx = center - bodyW / 2;
            int open = yFor(c[1], min, max, plotH);
            int high = yFor(c[2], min, max, plotH);
            int low = yFor(c[3], min, max, plotH);
            int close = yFor(c[4], min, max, plotH);
            int color = c[4] >= c[1] ? Ui.UP : Ui.DOWN;

            Ui.rect(g, center, high, 1, Math.max(1, low - high + 1), color);
            Ui.rect(g, bx, Math.min(open, close), bodyW, Math.max(2, Math.abs(close - open)), color);

            lastCloseY = close;
            lastColor = color;
            if (mouseX >= slot && mouseX < slot + step && mouseY >= plotY0 && mouseY <= plotY1) {
                hover = i;
            }
        }

        int tag = candles.get(n - 1)[4];
        String tagText = font.plainSubstrByWidth(Integer.toString(tag), GUTTER - 12);
        int tagW = Math.min(GUTTER - 2, font.width(tagText) + 8);
        int tagY = Math.max(plotY0, Math.min(plotY1 - 10, lastCloseY - 5));
        Ui.rect(g, plotX1 + 2, tagY, tagW, 10, lastColor);
        g.drawString(font, tagText, plotX1 + 6, tagY + 1, 0xFF0B0D11, false);

        if (hover >= 0) {
            int[] c = candles.get(hover);
            int slot = startX + hover * step;
            Ui.vline(g, slot + step / 2, plotY0, plotH, 0x66FFFFFF);
            drawHover(g, c, mouseX, mouseY);
        }
    }

    private void drawHover(GuiGraphics g, int[] candle, int mouseX, int mouseY) {
        String[] lines = {
                bucketLabel(candle[0]),
                "开 " + candle[1] + "    高 " + candle[2],
                "低 " + candle[3] + "    收 " + candle[4]
        };
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        int boxW = w + 10;
        int boxH = lines.length * 10 + 6;
        int x = Math.min(mouseX + 12, left + panelW - boxW - 4);
        int y = Math.max(top + 4, Math.min(mouseY - boxH / 2, top + panelH - boxH - 4));
        Ui.rect(g, x - 1, y - 1, boxW + 2, boxH + 2, 0xF00B0D11);
        Ui.hline(g, x - 1, y - 1, boxW + 2, Ui.EDGE);
        Ui.hline(g, x - 1, y + boxH, boxW + 2, Ui.EDGE);
        Ui.vline(g, x - 1, y - 1, boxH + 2, Ui.EDGE);
        Ui.vline(g, x + boxW, y - 1, boxH + 2, Ui.EDGE);
        for (int i = 0; i < lines.length; i++) {
            g.drawString(font, lines[i], x + 3, y + 3 + i * 10, i == 0 ? Ui.GOLD : Ui.TEXT, false);
        }
    }

    private String bucketLabel(int bucket) {
        if (period == MarketData.PERIODS[0]) {
            return "第 " + bucket + " 天";
        }
        int day = bucket / 6;
        int slot = bucket % 6;
        String[] names = {"00-04", "04-08", "08-12", "12-16", "16-20", "20-24"};
        return "第 " + day + " 天 " + names[Math.max(0, Math.min(5, slot))];
    }

    private void drawFooter(GuiGraphics g) {
        int bandY = footerY - GAP / 2;
        Ui.rect(g, left + 1, bandY, panelW - 2, top + panelH - bandY - 1, Ui.PANEL_FOOT);
        Ui.hline(g, left + 1, bandY, panelW - 2, Ui.EDGE_SOFT);

        List<int[]> candles = view.candles;
        int value = view.current >= 0 ? view.current
                : (candles.isEmpty() ? -1 : candles.get(candles.size() - 1)[4]);
        int y = footerY + 5;

        String head = value < 0 ? "现价 —" : "现价 " + value;
        g.drawString(font, head, contentLeft, y, Ui.GOLD, false);
        int x = contentLeft + font.width(head) + 10;

        if (candles.size() >= 2) {
            int previous = candles.get(candles.size() - 2)[4];
            int change = candles.get(candles.size() - 1)[4] - previous;
            int color = change > 0 ? Ui.UP : (change < 0 ? Ui.DOWN : Ui.TEXT_DIM);
            String delta = (change > 0 ? "▲ +" : change < 0 ? "▼ " : "") + change;
            g.drawString(font, delta, x, y, color, false);
            x += font.width(delta) + 8;
            if (previous > 0) {
                String pct = String.format(Locale.ROOT, "%.2f%%", change * 100.0D / previous);
                g.drawString(font, pct, x, y, color, false);
            }
        }

        String hint = candles.isEmpty() ? "" : "共 " + candles.size() + " 根 · 红涨绿跌";
        if (!hint.isEmpty()) {
            g.drawString(font, hint, contentRight - font.width(hint), y, Ui.TEXT_FAINT, false);
        }
    }

    private static int yFor(int value, int min, int max, int plotH) {
        if (max <= min) return 0;
        return plotH - (int) Math.round((value - min) * (double) plotH / (max - min));
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (Ui.hit(mouseX, mouseY, closeX - 4, closeY - 4, CLOSE + 8, CLOSE + 8)) {
                onClose();
                return true;
            }
            if (dayTab.contains(mouseX, mouseY)) {
                request(view.id, MarketData.PERIODS[0]);
                return true;
            }
            if (hourTab.contains(mouseX, mouseY)) {
                request(view.id, MarketData.PERIODS[1]);
                return true;
            }
            for (int i = 0; i < indexTabs.size(); i++) {
                if (indexTabs.get(i).contains(mouseX, mouseY)) {
                    request(view.indices.get(i).id, period);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void request(String id, int newPeriod) {
        period = newPeriod;
        SunlitMarketMod.CHANNEL.sendToServer(new ChartRequestMessage(id == null ? "" : id, newPeriod));
    }

    @Override
    public void onClose() {
        net.minecraft.client.Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Small immutable rectangle for pure hit-testing. */
    private static final class Rect {
        static final Rect EMPTY = new Rect(0, 0, 0, 0);
        final int x;
        final int y;
        final int w;
        final int h;

        Rect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean contains(double px, double py) {
            return w > 0 && h > 0 && px >= x && px < x + w && py >= y && py < y + h;
        }
    }
}
