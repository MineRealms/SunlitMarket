package com.society.sunlitmarket.client;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.market.MarketData;
import com.society.sunlitmarket.market.MarketView;
import com.society.sunlitmarket.net.ChartRequestMessage;
import com.society.sunlitmarket.net.MarketActionMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The pawnshop window: searchable offer list with a real scrollbar.
 *
 * <p>Every pixel is drawn by {@link Ui}; the only vanilla widget is the {@link EditBox} that
 * receives keystrokes (borderless, so it inherits our own search frame). All prices, stock and
 * unlock state come from {@link MarketView} — this screen never decides what a trade costs.
 *
 * <p>Client-only.
 */
public final class MarketScreen extends Screen {
    // Vertical rhythm: header / toolbar / list / footer, 6px gutters everywhere.
    private static final int PAD = 8;
    private static final int HEADER_H = 20;
    private static final int TOOLBAR_H = 18;
    private static final int ROW_H = 22;
    private static final int FOOTER_H = 18;
    private static final int GAP = 6;
    private static final int MAX_ROWS = 7;
    private static final int MIN_ROWS = 3;

    // Horizontal rhythm inside a row.
    private static final int ICON = 16;
    private static final int BTN_W = 52;
    private static final int BTN_H = 16;
    private static final int BTN_GAP = 4;
    private static final int TRACK_W = 6;
    private static final int CHART_W = 56;
    private static final int CLOSE = 9;

    private static final int BUY = 0;
    private static final int SELL = 1;

    private MarketView view;
    private String error;
    private String query = "";
    private final List<Integer> filtered = new ArrayList<>();
    private int offset;
    private int rows;
    private boolean pending;
    private EditBox search;

    private int panelW;
    private int panelH;
    private int left;
    private int top;
    private int contentLeft;
    private int contentRight;
    private int toolbarY;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private int footerY;
    private int searchX;
    private int searchY;
    private int searchW;
    private int chartX;
    private int trackX;
    private int closeX;
    private int closeY;

    private boolean dragging;
    private double dragGrab;

    public MarketScreen(MarketView view, String error) {
        super(Component.literal(view == null || view.merchantName == null || view.merchantName.isBlank()
                ? "Sunlit Market" : view.merchantName));
        this.view = view == null ? new MarketView() : view;
        this.error = error == null ? "" : error;
        rebuildFilter();
    }

    /** A fresh quote for the merchant already on screen; keeps the search text and scroll offset. */
    public void update(MarketView view, String error) {
        this.view = view == null ? new MarketView() : view;
        this.error = error == null ? "" : error;
        pending = false;
        rebuildFilter();
        clampOffset();
    }

    // ---------------------------------------------------------------- layout

    private void layout() {
        panelW = Math.min(360, width - 20);
        int maxRows = (height - 20 - 80) / ROW_H;
        rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, maxRows));
        panelH = 80 + rows * ROW_H;
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;

        contentLeft = left + PAD;
        contentRight = left + panelW - PAD;
        toolbarY = top + HEADER_H + GAP;
        listTop = toolbarY + TOOLBAR_H + GAP;
        listBottom = listTop + rows * ROW_H;
        footerY = listBottom + GAP;

        trackX = contentRight - TRACK_W;
        listLeft = contentLeft;
        listRight = trackX - 5;

        chartX = contentRight - CHART_W;
        searchX = contentLeft;
        searchY = toolbarY;
        searchW = chartX - GAP - searchX;

        closeX = left + panelW - PAD - CLOSE;
        closeY = top + (HEADER_H - CLOSE) / 2;
    }

    private int maxOffset() {
        return Math.max(0, filtered.size() - rows);
    }

    private void clampOffset() {
        offset = Math.max(0, Math.min(offset, maxOffset()));
    }

    private int rowY(int index) {
        return listTop + index * ROW_H;
    }

    private int buyX() {
        return listRight - BTN_W * 2 - BTN_GAP;
    }

    private int sellX() {
        return listRight - BTN_W;
    }

    @Override
    protected void init() {
        layout();
        clampOffset();
        search = new EditBox(font, searchX + 6, searchY + 5, searchW - 12 - 36, 8, Component.literal("搜索"));
        search.setBordered(false);
        search.setMaxLength(32);
        search.setValue(query);
        search.setTextColor(Ui.TEXT);
        search.setSuggestion("搜索物品…");
        search.setResponder(this::onQuery);
        addRenderableWidget(search);
    }

    // ---------------------------------------------------------------- filter

    private void onQuery(String text) {
        query = text == null ? "" : text;
        rebuildFilter();
        offset = 0;
    }

    private void rebuildFilter() {
        filtered.clear();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < view.offers.size(); i++) {
            MarketView.Line line = view.offers.get(i);
            if (needle.isEmpty() || matches(line, needle)) {
                filtered.add(i);
            }
        }
    }

    private static boolean matches(MarketView.Line line, String needle) {
        String id = line.item == null ? "" : line.item.toLowerCase(Locale.ROOT);
        if (id.contains(needle)) return true;
        String name = displayName(line.item);
        return name != null && name.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String displayName(String id) {
        try {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            if (item == null || item == net.minecraft.world.item.Items.AIR) return null;
            return new ItemStack(item).getHoverName().getString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean canBuy(MarketView.Line line) {
        return line.buy > 0 && line.stock != 0 && !line.locked;
    }

    private static boolean canSell(MarketView.Line line) {
        return line.sell > 0 && !line.locked;
    }

    private void send(int offerIndex, int action) {
        if (pending || view.session.isEmpty()) return;
        if (offerIndex < 0 || offerIndex >= view.offers.size()) return;
        pending = true;
        MarketView.Line line = view.offers.get(offerIndex);
        SunlitMarketMod.CHANNEL.sendToServer(new MarketActionMessage(view.merchantId, offerIndex, action, 1,
                view.session, view.sequence, line.offerId, line.buy, line.sell));
    }

    private void openChart() {
        SunlitMarketMod.CHANNEL.sendToServer(new ChartRequestMessage("", MarketData.PERIODS[0]));
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(0, 0, width, height, Ui.SCRIM);
        drawChrome(g, mouseX, mouseY);
        drawToolbar(g, mouseX, mouseY);
        int hoverRow = drawList(g, mouseX, mouseY);
        drawFooter(g);

        super.render(g, mouseX, mouseY, partialTick);

        if (hoverRow >= 0) {
            int position = offset + hoverRow;
            if (position < filtered.size()) {
                ItemStack stack = stackOf(view.offers.get(filtered.get(position)).item);
                if (stack != null) {
                    g.renderTooltip(font, stack, mouseX, mouseY);
                }
            }
        }
    }

    private void drawChrome(GuiGraphics g, int mouseX, int mouseY) {
        Ui.frame(g, left, top, panelW, panelH);

        Ui.rect(g, left + 1, top + 1, panelW - 2, HEADER_H - 1, Ui.PANEL_HEAD);
        Ui.rect(g, left + 1, top + 1, 2, HEADER_H - 1, Ui.GOLD);
        Ui.hline(g, left + 1, top + HEADER_H, panelW - 2, Ui.EDGE_SOFT);

        String head = font.plainSubstrByWidth(title.getString(), panelW - 2 * PAD - CLOSE - 12);
        g.drawString(font, head, left + PAD + 2, top + (HEADER_H - 8) / 2, Ui.TEXT, false);

        boolean hovered = Ui.hit(mouseX, mouseY, closeX - 4, closeY - 4, CLOSE + 8, CLOSE + 8);
        Ui.closeButton(g, closeX, closeY, CLOSE, hovered);

        int bandY = footerY - GAP / 2;
        Ui.rect(g, left + 1, bandY, panelW - 2, top + panelH - bandY - 1, Ui.PANEL_FOOT);
        Ui.hline(g, left + 1, bandY, panelW - 2, Ui.EDGE_SOFT);
    }

    private void drawToolbar(GuiGraphics g, int mouseX, int mouseY) {
        Ui.sunken(g, searchX, searchY, searchW, TOOLBAR_H);

        String counter = filtered.size() + "/" + view.offers.size();
        g.drawString(font, counter, searchX + searchW - 5 - font.width(counter), searchY + 5, Ui.TEXT_FAINT, false);
        Ui.vline(g, searchX + searchW - 9 - font.width(counter), searchY + 4, TOOLBAR_H - 8, Ui.EDGE_SOFT);

        boolean hovered = Ui.hit(mouseX, mouseY, chartX, searchY, CHART_W, TOOLBAR_H);
        Ui.button(g, font, "行情", chartX, searchY, CHART_W, TOOLBAR_H, hovered, true,
                Ui.BTN, Ui.BTN_HOVER, Ui.GOLD);
    }

    /** Draws the visible rows and returns the hovered row index (0-based on screen), or -1. */
    private int drawList(GuiGraphics g, int mouseX, int mouseY) {
        Ui.sunken(g, listLeft, listTop, listRight - listLeft, listBottom - listTop);

        int hoverRow = -1;
        if (filtered.isEmpty()) {
            String empty = view.offers.isEmpty() ? "这家店暂时没有货物" : "没有匹配的物品";
            g.drawString(font, empty, listLeft + (listRight - listLeft - font.width(empty)) / 2,
                    listTop + (listBottom - listTop) / 2 - 4, Ui.TEXT_FAINT, false);
            return -1;
        }
        for (int i = 0; i < rows; i++) {
            int position = offset + i;
            if (position >= filtered.size()) break;
            int y = rowY(i);
            boolean hovered = Ui.hit(mouseX, mouseY, listLeft, y, listRight - listLeft, ROW_H);
            if (hovered) {
                hoverRow = i;
                Ui.rect(g, listLeft, y, listRight - listLeft, ROW_H - 1, Ui.ROW_HOVER);
            } else if ((position & 1) == 1) {
                Ui.rect(g, listLeft, y, listRight - listLeft, ROW_H - 1, Ui.ROW_ALT);
            }
            drawRow(g, view.offers.get(filtered.get(position)), y, mouseX, mouseY);
            Ui.hline(g, listLeft, y + ROW_H - 1, listRight - listLeft, Ui.ROW_LINE);
        }
        drawScrollbar(g, mouseX, mouseY);
        return hoverRow;
    }

    private void drawRow(GuiGraphics g, MarketView.Line line, int y, int mouseX, int mouseY) {
        int textX = listLeft + 3 + ICON + 4;
        int textW = buyX() - GAP - textX;

        ItemStack stack = stackOf(line.item);
        if (stack != null) {
            g.renderItem(stack, listLeft + 3, y + 3);
            String name = stack.getHoverName().getString() + " ×" + line.count;
            g.drawString(font, font.plainSubstrByWidth(name, textW), textX, y + 4, Ui.TEXT, false);
        } else {
            g.drawString(font, font.plainSubstrByWidth(line.item, textW), textX, y + 4, Ui.UP, false);
        }

        String status = statusOf(line);
        int statusColor = line.locked || (line.limit >= 0 && line.used >= line.limit) ? 0xFFB48A4A : Ui.TEXT_DIM;
        g.drawString(font, font.plainSubstrByWidth(status, textW), textX, y + 13, statusColor, false);

        int by = y + (ROW_H - 1 - BTN_H) / 2;
        boolean hoverBuy = Ui.hit(mouseX, mouseY, buyX(), by, BTN_W, BTN_H);
        boolean hoverSell = Ui.hit(mouseX, mouseY, sellX(), by, BTN_W, BTN_H);

        if (line.buy > 0) {
            Ui.button(g, font, "买 " + line.buy, buyX(), by, BTN_W, BTN_H,
                    hoverBuy && !pending && canBuy(line), !pending && canBuy(line),
                    Ui.BUY, Ui.BUY_HOVER, Ui.TEXT);
        }
        if (line.sell > 0) {
            Ui.button(g, font, "卖 " + line.sell, sellX(), by, BTN_W, BTN_H,
                    hoverSell && !pending && canSell(line), !pending && canSell(line),
                    Ui.SELL, Ui.SELL_HOVER, Ui.TEXT);
        }
    }

    private static String statusOf(MarketView.Line line) {
        StringBuilder status = new StringBuilder();
        if (line.stock >= 0) status.append("库存 ").append(line.stock);
        if (line.limit >= 0) {
            if (status.length() > 0) status.append(" · ");
            status.append("限购 ").append(line.used).append('/').append(line.limit);
        }
        if (line.hasReputation) {
            if (status.length() > 0) status.append(" · ");
            status.append("需声望 ").append(line.reputation);
        }
        if (line.baseBuy > 0 && line.buy > 0 && line.buy != line.baseBuy) {
            int percent = (int) Math.round((line.buy - line.baseBuy) * 100.0D / line.baseBuy);
            if (percent != 0) {
                if (status.length() > 0) status.append(" · ");
                status.append(percent > 0 ? "货架溢价 +" : "货架折价 ").append(percent).append('%');
            }
        }
        if (line.locked) {
            if (status.length() > 0) status.append(" · ");
            status.append("未解锁");
        }
        return status.toString();
    }

    private static ItemStack stackOf(String id) {
        try {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            if (item == null || item == net.minecraft.world.item.Items.AIR) return null;
            return new ItemStack(item);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void drawFooter(GuiGraphics g) {
        int y = footerY + 5;
        String label = "余额 ";
        String balance = view.balance + " spur";
        g.drawString(font, label, contentLeft, y, Ui.TEXT_FAINT, false);
        g.drawString(font, balance, contentLeft + font.width(label), y, Ui.GOLD, false);

        int x = contentLeft + font.width(label + balance) + 10;
        StringBuilder rep = new StringBuilder();
        if (view.faction != null && !view.faction.isEmpty()) rep.append(view.faction).append(' ');
        rep.append(view.reputation);
        if (view.title != null && !view.title.isEmpty()) rep.append(" · ").append(view.title);
        g.drawString(font, font.plainSubstrByWidth(rep.toString(), panelW / 2 - 40), x, y, Ui.TEXT_DIM, false);

        boolean hasError = !error.isEmpty();
        String right = hasError ? error : "滚轮翻页 · Ctrl+F 搜索";
        right = font.plainSubstrByWidth(right, panelW / 2 - 20);
        g.drawString(font, right, contentRight - font.width(right), y, hasError ? Ui.UP : Ui.TEXT_FAINT, false);
    }

    // ---------------------------------------------------------------- scrollbar

    private int trackHeight() {
        return listBottom - listTop;
    }

    private int thumbHeight() {
        int track = trackHeight();
        if (filtered.size() <= rows || filtered.isEmpty()) return track;
        return Math.max(12, (int) Math.round(track * (double) rows / filtered.size()));
    }

    private int thumbY() {
        int track = trackHeight();
        int height = thumbHeight();
        int span = Math.max(1, track - height);
        int max = maxOffset();
        if (max <= 0) return listTop;
        return listTop + (int) Math.round(span * (offset / (double) max));
    }

    private void drawScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        boolean hovered = Ui.hit(mouseX, mouseY, trackX - 1, listTop, TRACK_W + 2, trackHeight());
        Ui.scrollbar(g, trackX, listTop, TRACK_W, trackHeight(), thumbY(), thumbHeight(),
                hovered || dragging);
    }

    private boolean insideTrack(double mouseX, double mouseY) {
        return mouseX >= trackX - 2 && mouseX < trackX + TRACK_W + 2
                && mouseY >= listTop && mouseY <= listBottom;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (Ui.hit(mouseX, mouseY, listLeft, listTop, listRight - listLeft, listBottom - listTop)
                && maxOffset() > 0) {
            int step = delta > 0 ? -1 : 1;
            offset = Math.max(0, Math.min(maxOffset(), offset + step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (Ui.hit(mouseX, mouseY, closeX - 4, closeY - 4, CLOSE + 8, CLOSE + 8)) {
            onClose();
            return true;
        }
        if (Ui.hit(mouseX, mouseY, chartX, searchY, CHART_W, TOOLBAR_H)) {
            openChart();
            return true;
        }
        if (insideTrack(mouseX, mouseY) && maxOffset() > 0) {
            int thumb = thumbY();
            int height = thumbHeight();
            if (mouseY >= thumb && mouseY < thumb + height) {
                dragging = true;
                dragGrab = mouseY - thumb;
            } else {
                int span = Math.max(1, trackHeight() - height);
                double ratio = (mouseY - listTop - height / 2.0D) / span;
                offset = (int) Math.round(Math.max(0, Math.min(1, ratio)) * maxOffset());
            }
            return true;
        }
        for (int i = 0; i < rows; i++) {
            int position = offset + i;
            if (position >= filtered.size()) break;
            int by = rowY(i) + (ROW_H - 1 - BTN_H) / 2;
            MarketView.Line line = view.offers.get(filtered.get(position));
            if (Ui.hit(mouseX, mouseY, buyX(), by, BTN_W, BTN_H)) {
                if (canBuy(line)) send(filtered.get(position), BUY);
                return true;
            }
            if (Ui.hit(mouseX, mouseY, sellX(), by, BTN_W, BTN_H)) {
                if (canSell(line)) send(filtered.get(position), SELL);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            int span = Math.max(1, trackHeight() - thumbHeight());
            double ratio = (mouseY - listTop - dragGrab) / span;
            offset = (int) Math.round(Math.max(0, Math.min(1, ratio)) * maxOffset());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F && Screen.hasControlDown()) {
            search.setFocused(true);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        SunlitMarketMod.CHANNEL.sendToServer(new MarketActionMessage(view.merchantId, 0, -1, 0,
                view.session, view.sequence, "", 0, 0));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
