package com.society.sunlitmarket.client;

import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.market.MarketData;
import com.society.sunlitmarket.market.MarketView;
import com.society.sunlitmarket.net.ChartRequestMessage;
import com.society.sunlitmarket.net.MarketActionMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
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

/**
 * The pawnshop window: searchable offer list with a real scrollbar.
 *
 * <p>Client-only. All prices, stock and unlock state come from {@link MarketView}; this screen
 * never decides what a trade costs.
 */
public final class MarketScreen extends Screen {
    private static final int ROW_H = 26;
    private static final int BUY = 0;
    private static final int SELL = 1;
    private static final int SCROLL_W = 10;

    private MarketView view;
    private String error;
    private String query = "";
    private final List<Integer> filtered = new ArrayList<>();
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();
    private int offset;
    private boolean pending;
    private EditBox search;

    private int panelW;
    private int panelH;
    private int left;
    private int top;
    private int listLeft;
    private int listRight;
    private int listTop;
    private int listBottom;
    private int searchX;
    private int searchY;
    private int searchW;
    private int trackX;
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
        rebuildRows();
    }

    // ---------------------------------------------------------------- layout

    private void layout() {
        panelW = Math.min(400, width - 16);
        panelH = Math.min(272, height - 16);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        listLeft = left + 10;
        listRight = left + panelW - 18;
        listTop = top + 62;
        listBottom = top + panelH - 30;
        searchX = left + 10;
        searchY = top + 42;
        searchW = panelW - 176;
        trackX = left + panelW - 15;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    private int maxOffset() {
        return Math.max(0, filtered.size() - visibleRows());
    }

    private void clampOffset() {
        offset = Math.max(0, Math.min(offset, maxOffset()));
    }

    @Override
    protected void init() {
        layout();
        clampOffset();
        search = new EditBox(font, searchX + 20, searchY + 4, searchW - 26, 10, Component.literal("搜索"));
        search.setBordered(false);
        search.setMaxLength(32);
        search.setValue(query);
        search.setResponder(this::onQuery);
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("行情"), b -> openChart())
                .bounds(left + panelW - 58, searchY - 1, 48, 18).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> this.onClose())
                .bounds(left + panelW / 2 - 30, top + panelH - 24, 60, 18).build());
        rebuildRows();
    }

    // ---------------------------------------------------------------- filter + rows

    private void onQuery(String text) {
        query = text == null ? "" : text;
        rebuildFilter();
        offset = 0;
        rebuildRows();
    }

    private void rebuildFilter() {
        filtered.clear();
        String needle = query.trim().toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < view.offers.size(); i++) {
            MarketView.Line line = view.offers.get(i);
            if (needle.isEmpty() || matches(line, needle)) {
                filtered.add(i);
            }
        }
    }

    private static boolean matches(MarketView.Line line, String needle) {
        String id = line.item == null ? "" : line.item.toLowerCase(java.util.Locale.ROOT);
        if (id.contains(needle)) return true;
        String name = displayName(line.item);
        return name != null && name.toLowerCase(java.util.Locale.ROOT).contains(needle);
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

    private void rebuildRows() {
        for (AbstractWidget widget : rowWidgets) {
            removeWidget(widget);
        }
        rowWidgets.clear();
        int rows = visibleRows();
        for (int i = 0; i < rows; i++) {
            int position = offset + i;
            if (position >= filtered.size()) break;
            final int offer = filtered.get(position);
            MarketView.Line line = view.offers.get(offer);
            int y = listTop + i * ROW_H + 4;
            if (line.buy > 0 && line.stock != 0 && !line.locked) {
                rowWidgets.add(addRenderableWidget(Button.builder(Component.literal("买 " + line.buy),
                        b -> send(offer, BUY)).bounds(left + panelW - 142, y, 60, 18).build()));
            }
            if (line.sell > 0 && !line.locked) {
                rowWidgets.add(addRenderableWidget(Button.builder(Component.literal("卖 " + line.sell),
                        b -> send(offer, SELL)).bounds(left + panelW - 78, y, 60, 18).build()));
            }
        }
    }

    private void send(int offerIndex, int action) {
        if (pending || view.session.isEmpty()) return;
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, 0x66000000);
        GuiArt.panel(graphics, left, top, panelW, panelH);

        GuiArt.icon(graphics, GuiArt.MERCHANT, 19, 19, left + 8, top + 6);
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), panelW - 60), left + 26, top + 9, 0xFFE8D9A8, false);
        drawClose(graphics, mouseX, mouseY);

        String info = "余额 " + view.balance + " spur   ·   " + view.faction + " " + view.reputation
                + (view.title == null || view.title.isEmpty() ? "" : " (" + view.title + ")");
        graphics.drawString(font, font.plainSubstrByWidth(info, panelW - 24), left + 10, top + 29, 0xCCCCCC, false);

        GuiArt.searchField(graphics, searchX, searchY, searchW, 16);
        GuiArt.icon(graphics, GuiArt.SEARCH_ICON, 32, 32, searchX + 4, searchY + 2);

        String counter = filtered.size() + "/" + view.offers.size() + " 件";
        graphics.drawString(font, counter, left + panelW - 62 - font.width(counter), searchY + 4, 0xFF8A94A0, false);

        graphics.fill(listLeft - 2, listTop - 2, listRight + 2, listBottom + 2, 0x33000000);

        int rows = visibleRows();
        int hoverRow = -1;
        for (int i = 0; i < rows; i++) {
            int position = offset + i;
            if (position >= filtered.size()) break;
            int y = listTop + i * ROW_H;
            if (mouseX >= listLeft && mouseX < listRight && mouseY >= y && mouseY < y + ROW_H) {
                hoverRow = i;
                graphics.fill(listLeft - 2, y, listRight + 2, y + ROW_H - 1, 0x22FFFFFF);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        for (int i = 0; i < rows; i++) {
            int position = offset + i;
            if (position >= filtered.size()) break;
            drawRow(graphics, view.offers.get(filtered.get(position)), listTop + i * ROW_H);
        }
        if (filtered.isEmpty()) {
            String empty = view.offers.isEmpty() ? "这家店暂时没有货物" : "没有匹配的物品";
            graphics.drawString(font, empty, listLeft + 4, listTop + 8, 0xFF8A94A0, false);
        }
        drawScrollbar(graphics, rows);

        if (!error.isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(error, panelW / 2 - 48), left + 10, top + panelH - 19, 0xFF5555, false);
        }
        if (hoverRow >= 0) {
            int position = offset + hoverRow;
            if (position < filtered.size()) {
                MarketView.Line line = view.offers.get(filtered.get(position));
                ItemStack stack = stackOf(line.item);
                if (stack != null) {
                    graphics.renderTooltip(font, stack, mouseX, mouseY);
                }
            }
        }
    }

    private void drawRow(GuiGraphics graphics, MarketView.Line line, int y) {
        ItemStack stack = stackOf(line.item);
        if (stack != null) {
            graphics.renderItem(stack, listLeft, y + 4);
            String name = stack.getHoverName().getString() + " x" + line.count;
            graphics.drawString(font, font.plainSubstrByWidth(name, left + panelW - 210 - listLeft - 24),
                    listLeft + 22, y + 3, 0xFFFFFF, false);
        } else {
            graphics.drawString(font, line.item, listLeft + 22, y + 3, 0xFF5555, false);
        }
        String status = statusOf(line);
        int color = line.locked || (line.limit >= 0 && line.used >= line.limit) ? 0xFFB48A4A : 0xFF9AA0AA;
        graphics.drawString(font, font.plainSubstrByWidth(status, left + panelW - 210 - listLeft - 24),
                listLeft + 22, y + 13, color, false);
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

    private void drawClose(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + panelW - 22;
        int y = top + 7;
        GuiArt.icon(graphics, GuiArt.CLOSE, x, y);
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            graphics.fill(x, y, x + 16, y + 16, 0x55FFFFFF);
        }
    }

    // ---------------------------------------------------------------- scrollbar

    private int trackHeight() {
        return listBottom - listTop;
    }

    private int thumbHeight(int rows) {
        int track = trackHeight();
        if (filtered.size() <= rows || filtered.isEmpty()) return track;
        return Math.max(16, (int) Math.round(track * (double) rows / filtered.size()));
    }

    private int thumbY(int rows) {
        int track = trackHeight();
        int height = thumbHeight(rows);
        int span = Math.max(1, track - height);
        int max = maxOffset();
        if (max <= 0) return listTop;
        return listTop + (int) Math.round(span * (offset / (double) max));
    }

    private void drawScrollbar(GuiGraphics graphics, int rows) {
        GuiArt.scrollTrack(graphics, trackX, listTop, SCROLL_W, trackHeight());
        if (filtered.size() <= rows) return;
        GuiArt.scrollHandle(graphics, trackX, thumbY(rows), SCROLL_W, thumbHeight(rows));
    }

    private boolean insideTrack(double mouseX, double mouseY) {
        return mouseX >= trackX - 2 && mouseX < trackX + SCROLL_W + 2
                && mouseY >= listTop && mouseY <= listBottom;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= left && mouseX < left + panelW && mouseY >= top && mouseY < top + panelH
                && maxOffset() > 0) {
            int step = Math.max(1, (int) Math.round(delta));
            offset = Math.max(0, Math.min(maxOffset(), offset - step));
            rebuildRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= left + panelW - 22 && mouseX < left + panelW - 6
                    && mouseY >= top + 7 && mouseY < top + 23) {
                onClose();
                return true;
            }
            if (insideTrack(mouseX, mouseY) && maxOffset() > 0) {
                int rows = visibleRows();
                int thumb = thumbY(rows);
                if (mouseY >= thumb && mouseY < thumb + thumbHeight(rows)) {
                    dragging = true;
                    dragGrab = mouseY - thumb;
                } else {
                    int span = Math.max(1, trackHeight() - thumbHeight(rows));
                    double ratio = (mouseY - listTop - thumbHeight(rows) / 2.0D) / span;
                    offset = (int) Math.round(Math.max(0, Math.min(1, ratio)) * maxOffset());
                    rebuildRows();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            int rows = visibleRows();
            int span = Math.max(1, trackHeight() - thumbHeight(rows));
            double ratio = (mouseY - listTop - dragGrab) / span;
            offset = (int) Math.round(Math.max(0, Math.min(1, ratio)) * maxOffset());
            rebuildRows();
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
