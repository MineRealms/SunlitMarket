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

/**
 * Read-only candle panel. Every number comes from the server through {@link ChartView}; the client
 * never prices anything itself.
 *
 * <p>Colour convention follows the Chinese market: red = up, green = down.
 */
public final class ChartScreen extends Screen {
    private static final int UP = 0xFFE0524C;
    private static final int DOWN = 0xFF3FA45B;
    private static final int GRID = 0x33FFFFFF;
    private static final int AXIS = 0xFF9AA0AA;

    private final Screen parent;
    private ChartView view;
    private int panelW;
    private int panelH;
    private int left;
    private int top;
    private int period = MarketData.PERIODS[0];
    private final List<Rect> indexTabs = new ArrayList<>();
    private Rect closeRect = Rect.EMPTY;
    private Rect dayTab = Rect.EMPTY;
    private Rect hourTab = Rect.EMPTY;
    private int plotX0;
    private int plotY0;
    private int plotX1;
    private int plotY1;
    private int hovered = -1;

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
        panelW = Math.min(392, width - 16);
        panelH = Math.min(252, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        plotX0 = left + 12;
        plotY0 = top + 56;
        plotX1 = left + panelW - 58;
        plotY1 = top + panelH - 34;
        closeRect = new Rect(left + panelW - 24, top + 7, 16, 16);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(0, 0, width, height, 0x66000000);
        GuiArt.panel(g, left, top, panelW, panelH);

        g.drawString(font, "行情 · " + (view.name == null || view.name.isEmpty() ? "—" : view.name),
                left + 10, top + 10, 0xFFE8D9A8, false);
        drawIcon(g, GuiArt.CLOSE, 16, 16, closeRect, mouseX, mouseY);

        buildTabs();
        for (int i = 0; i < indexTabs.size(); i++) {
            ChartView.Entry entry = view.indices.get(i);
            Rect rect = indexTabs.get(i);
            boolean active = entry.id.equals(view.id);
            GuiArt.tab(g, rect.x, rect.y, rect.w, rect.h, active);
            String label = entry.name == null || entry.name.isEmpty() ? entry.id : entry.name;
            label = font.plainSubstrByWidth(label, rect.w - 12);
            g.drawString(font, label, rect.x + 8, rect.y + (rect.h - 8) / 2,
                    active ? 0xFF3A2E1E : 0xFF1F3A3A, false);
        }
        drawPeriodTab(g, dayTab, "1日", period == MarketData.PERIODS[0]);
        drawPeriodTab(g, hourTab, "4时", period == MarketData.PERIODS[1]);

        hovered = drawCandles(g, mouseX, mouseY);
        drawFooter(g);
    }

    private void drawPeriodTab(GuiGraphics g, Rect rect, String label, boolean active) {
        if (rect.w <= 0) return;
        GuiArt.tab(g, rect.x, rect.y, rect.w, rect.h, active);
        g.drawString(font, label, rect.x + (rect.w - font.width(label)) / 2, rect.y + (rect.h - 8) / 2,
                active ? 0xFF3A2E1E : 0xFF1F3A3A, false);
    }

    private void drawIcon(GuiGraphics g, net.minecraft.resources.ResourceLocation tex, int tw, int th, Rect rect, int mouseX, int mouseY) {
        if (rect.w <= 0) return;
        g.blit(tex, rect.x, rect.y, 0, 0.0F, 0.0F, rect.w, rect.h, tw, th);
        if (rect.contains(mouseX, mouseY)) {
            g.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, 0x55FFFFFF);
        }
    }

    private void buildTabs() {
        indexTabs.clear();
        int x = left + 10;
        int y = top + 26;
        int limit = left + panelW - 116;
        for (ChartView.Entry entry : view.indices) {
            String label = entry.name == null || entry.name.isEmpty() ? entry.id : entry.name;
            int w = Math.min(96, font.width(label) + 16);
            if (x + w > limit) break;
            indexTabs.add(new Rect(x, y, w, 20));
            x += w + 3;
        }
        dayTab = new Rect(left + panelW - 108, y, 48, 20);
        hourTab = new Rect(left + panelW - 56, y, 48, 20);
    }

    /** Draws the candle body and returns the hovered candle index, or -1. */
    private int drawCandles(GuiGraphics g, int mouseX, int mouseY) {
        List<int[]> candles = view.candles;
        int plotW = plotX1 - plotX0;
        int plotH = plotY1 - plotY0;
        g.fill(plotX0, plotY0, plotX1, plotY1, 0x55000000);
        if (candles.isEmpty() || plotW < 20 || plotH < 20) {
            g.drawString(font, "暂无数据（服务器每 5 秒采样一次）", plotX0 + 6, plotY0 + plotH / 2 - 4, AXIS, false);
            return -1;
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
        int pad = Math.max(2, (max - min) / 20);
        min -= pad;
        max += pad;

        for (int i = 1; i <= 4; i++) {
            int y = plotY0 + plotH * i / 5;
            g.fill(plotX0, y, plotX1, y + 1, GRID);
            int value = max - (max - min) * i / 5;
            g.drawString(font, Integer.toString(value), plotX1 + 4, y - 4, AXIS, false);
        }
        g.fill(plotX0, plotY1, plotX1, plotY1 + 1, GRID);

        int n = candles.size();
        int step = Math.max(1, plotW / n);
        int bodyW = Math.max(1, Math.min(9, step - 1));
        int offset = plotX0 + Math.max(0, (plotW - n * step) / 2);
        int hover = -1;
        for (int i = 0; i < n; i++) {
            int[] c = candles.get(i);
            int cx = offset + i * step;
            int open = yFor(c[1], min, max, plotH);
            int high = yFor(c[2], min, max, plotH);
            int low = yFor(c[3], min, max, plotH);
            int close = yFor(c[4], min, max, plotH);
            int color = c[4] >= c[1] ? UP : DOWN;
            int bodyTop = Math.min(open, close);
            int bodyBottom = Math.max(open, close);
            g.fill(cx, high, cx + bodyW, low + 1, color);
            if (bodyBottom - bodyTop > 1) {
                g.fill(cx, bodyTop, cx + bodyW, bodyBottom, color);
            }
            if (mouseX >= cx && mouseX < cx + step && mouseY >= plotY0 && mouseY <= plotY1) {
                hover = i;
            }
        }
        if (hover >= 0) {
            int[] c = candles.get(hover);
            int cx = offset + hover * step;
            g.fill(cx, plotY0, cx + 1, plotY1, 0x88FFFFFF);
            drawHover(g, c, mouseX, mouseY);
        }
        return hover;
    }

    private void drawHover(GuiGraphics g, int[] candle, int mouseX, int mouseY) {
        String when = bucketLabel(candle[0]);
        String[] lines = {
                when,
                "开 " + candle[1] + "   高 " + candle[2],
                "低 " + candle[3] + "   收 " + candle[4]
        };
        int w = 0;
        for (String line : lines) w = Math.max(w, font.width(line));
        int boxW = w + 8;
        int boxH = lines.length * 10 + 4;
        int x = Math.min(mouseX + 10, left + panelW - boxW - 2);
        int y = Math.max(top + 2, Math.min(mouseY - boxH / 2, top + panelH - boxH - 2));
        g.fill(x - 2, y - 2, x + boxW + 2, y + boxH + 2, 0xE0101014);
        g.fill(x - 2, y - 2, x + boxW + 2, y - 1, 0xFF8A9BB8);
        for (int i = 0; i < lines.length; i++) {
            g.drawString(font, lines[i], x + 2, y + 2 + i * 10, i == 0 ? 0xFFE8D9A8 : 0xFFDDDDDD, false);
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
        int y = top + panelH - 26;
        int change = 0;
        List<int[]> candles = view.candles;
        if (candles.size() >= 2) {
            change = candles.get(candles.size() - 1)[4] - candles.get(candles.size() - 2)[4];
        }
        int value = view.current >= 0 ? view.current : (candles.isEmpty() ? -1 : candles.get(candles.size() - 1)[4]);
        String head = value < 0 ? "现价 —" : "现价 " + value;
        g.drawString(font, head, left + 12, y, 0xFFE8D9A8, false);
        int x = left + 12 + font.width(head) + 10;
        if (candles.size() >= 2 && change != 0) {
            String delta = (change > 0 ? "▲ +" : "▼ ") + change;
            g.drawString(font, delta, x, y, change > 0 ? UP : DOWN, false);
            x += font.width(delta) + 8;
            int previous = candles.get(candles.size() - 2)[4];
            if (previous > 0) {
                String pct = String.format(java.util.Locale.ROOT, "%.2f%%", change * 100.0D / previous);
                g.drawString(font, pct, x, y, change > 0 ? UP : DOWN, false);
            }
        }
        String hint = candles.isEmpty() ? "" : "共 " + candles.size() + " 根 · 红涨绿跌";
        if (!hint.isEmpty()) {
            g.drawString(font, hint, left + panelW - 12 - font.width(hint), y, 0xFF7E8894, false);
        }
    }

    private static int yFor(int value, int min, int max, int plotH) {
        if (max <= min) return 0;
        return plotH - (int) Math.round((value - min) * (double) plotH / (max - min));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (closeRect.contains(mouseX, mouseY)) {
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

    /** Small mutable rectangle, avoids dragging in a widget class for pure hit-testing. */
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
