package com.society.sunlitmarket.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.society.sunlitmarket.SunlitMarketMod;
import com.society.sunlitmarket.market.MarketView;
import com.society.sunlitmarket.net.MarketActionMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class MarketScreen extends Screen {
    private MarketView view;
    private String error;
    private int offset;
    private boolean pending;
    private int visibleRows() { return Math.max(1, Math.min(8, (height - 120) / 26)); }

    public MarketScreen(MarketView view, String error) {
        super(Component.literal(view == null || view.merchantName == null || view.merchantName.isBlank()
                ? "Sunlit Market" : view.merchantName));
        this.view = view == null ? new MarketView() : view;
        this.error = error == null ? "" : error;
    }

    public void update(MarketView view, String error) {
        this.view = view == null ? new MarketView() : view;
        this.error = error == null ? "" : error;
        pending = false;
        offset = Math.min(offset, Math.max(0, this.view.offers.size() - visibleRows()));
        this.clearWidgets();
        this.init();
    }

    @Override
    protected void init() {
        int rows = Math.min(view.offers.size() - offset, visibleRows());
        int left = 20;
        int top = 50;
        for (int i = 0; i < rows; i++) {
            final int index = i + offset;
            MarketView.Line line = view.offers.get(i + offset);
            int y = top + i * 26;
            if (line.buy > 0 && line.stock != 0 && !line.locked) {
                addRenderableWidget(Button.builder(Component.literal("买 " + line.buy), b -> send(index, MarketServiceAction.BUY))
                        .bounds(width - 150, y, 60, 18).build());
            }
            if (line.sell > 0 && !line.locked) {
                addRenderableWidget(Button.builder(Component.literal("卖 " + line.sell), b -> send(index, MarketServiceAction.SELL))
                        .bounds(width - 84, y, 60, 18).build());
            }
        }
        if (offset > 0) addRenderableWidget(Button.builder(Component.literal("上一页"), b -> { offset = Math.max(0, offset - visibleRows()); clearWidgets(); init(); }).bounds(20, height - 30, 65, 20).build());
        if (offset + visibleRows() < view.offers.size()) addRenderableWidget(Button.builder(Component.literal("下一页"), b -> { offset += visibleRows(); clearWidgets(); init(); }).bounds(width - 85, height - 30, 65, 20).build());
        addRenderableWidget(Button.builder(Component.literal("关闭"), b -> this.onClose())
                .bounds(width / 2 - 30, height - 30, 60, 20).build());
    }

    private void send(int index, int action) {
        if (pending || view.session.isEmpty()) return;
        pending = true;
        MarketView.Line line = view.offers.get(index);
        SunlitMarketMod.CHANNEL.sendToServer(new MarketActionMessage(view.merchantId, index, action, 1, view.session, view.sequence, line.offerId, line.buy, line.sell));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, title, 20, 14, 0xFFD37F, false);
        String info = "余额 " + view.balance + " spur   |   " + view.faction + " " + view.reputation
                + (view.title == null || view.title.isEmpty() ? "" : " (" + view.title + ")");
        graphics.drawString(font, info, 20, 30, 0xCCCCCC, false);
        int rows = Math.min(view.offers.size() - offset, visibleRows());
        for (int i = 0; i < rows; i++) {
            MarketView.Line line = view.offers.get(i + offset);
            int y = 50 + i * 26;
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(line.item));
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                ItemStack stack = new ItemStack(item);
                graphics.renderItem(stack, 20, y + 2);
                graphics.drawString(font, stack.getHoverName().getString() + " x" + line.count, 42, y + 6, 0xFFFFFF, false);
            } else {
                graphics.drawString(font, line.item, 42, y + 6, 0xFF5555, false);
            }
            StringBuilder status = new StringBuilder();
            if (line.stock >= 0) status.append("库存 ").append(line.stock);
            if (line.limit >= 0) {
                if (status.length() > 0) status.append("  ");
                status.append("限购 ").append(line.used).append('/').append(line.limit);
            }
            if (line.hasReputation) {
                if (status.length() > 0) status.append("  ");
                status.append("需声望 ").append(line.reputation);
            }
            if (line.locked) {
                if (status.length() > 0) status.append("  ");
                status.append("未解锁");
            }
            if (status.length() > 0) {
                String text = status.toString();
                int color = line.locked || (line.limit >= 0 && line.used >= line.limit) ? 0xFFAA55 : 0xAAAAAA;
                graphics.drawString(font, text, width - 170 - font.width(text), y + 6, color, false);
            }
        }
        if (!error.isEmpty()) {
            graphics.drawString(font, error, 20, height - 50, 0xFF5555, false);
        }
    }

    @Override
    public void onClose() {
        SunlitMarketMod.CHANNEL.sendToServer(new MarketActionMessage(view.merchantId, 0, -1, 0, view.session, view.sequence, "", 0, 0));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class MarketServiceAction {
        static final int BUY = 0;
        static final int SELL = 1;
    }
}
