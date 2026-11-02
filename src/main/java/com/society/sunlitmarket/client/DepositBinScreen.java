package com.society.sunlitmarket.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.society.sunlitmarket.deposit.DepositBinMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * The acceptance bin screen. Flat {@link Ui} chrome, same as the shop and chart windows.
 *
 * <p>Client-only; the server owns every number shown here.
 */
public final class DepositBinScreen extends AbstractContainerScreen<DepositBinMenu> {
    private static final int PAD = 8;
    private static final int HEADER_H = 20;
    private static final int ROW_H = 22;
    private static final int GAP = 6;
    private static final int CLOSE = 9;
    private static final int ROWS = 5;
    private static final int WALLET_H = 24;
    private static final int WALLET_BTN_W = 40;
    private static final int WALLET_BTN_H = 16;

    private static final String[] LABELS = {"物品", "流体", "能量", "要素", "魔力"};

    private JsonObject state = new JsonObject();
    private int panelW = 340;
    private int panelH = 198;
    private int left;
    private int top;
    private int walletY;
    private int depositX;
    private int withdrawX;
    private int walletButtonY;
    private int footerY;
    private int closeX;
    private int closeY;
    private int buttonX;
    private int buttonY;

    public DepositBinScreen(DepositBinMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /** Called from the packet handler on the client thread. */
    public void update(String json) {
        try {
            state = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException ignored) {
            state = new JsonObject();
        }
    }

    @Override
    protected void init() {
        super.init();
        panelW = Math.min(340, width - 20);
        panelH = 198;
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        walletY = top + HEADER_H + GAP + ROWS * ROW_H + GAP;
        walletButtonY = walletY + (WALLET_H - WALLET_BTN_H) / 2;
        withdrawX = left + panelW - PAD - WALLET_BTN_W;
        depositX = withdrawX - GAP - WALLET_BTN_W;
        footerY = walletY + WALLET_H + GAP;
        closeX = left + panelW - PAD - CLOSE;
        closeY = top + (HEADER_H - CLOSE) / 2;
        buttonX = left + panelW - PAD - 64;
        buttonY = footerY;
        this.leftPos = left;
        this.topPos = top;
        this.imageWidth = panelW;
        this.imageHeight = panelH;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(0, 0, width, height, Ui.SCRIM);
        drawChrome(g, mouseX, mouseY);
        drawRows(g, mouseX, mouseY);
        drawWallet(g, mouseX, mouseY);
        drawFooter(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawChrome(GuiGraphics g, int mouseX, int mouseY) {
        Ui.frame(g, left, top, panelW, panelH);
        Ui.rect(g, left + 1, top + 1, panelW - 2, HEADER_H - 1, Ui.PANEL_HEAD);
        Ui.rect(g, left + 1, top + 1, 2, HEADER_H - 1, Ui.GOLD);
        Ui.hline(g, left + 1, top + HEADER_H, panelW - 2, Ui.EDGE_SOFT);

        String head = font.plainSubstrByWidth(Component.translatable("block.sunlitmarket.deposit_bin").getString(),
                panelW - 2 * PAD - CLOSE - 12);
        g.drawString(font, head, left + PAD + 2, top + (HEADER_H - 8) / 2, Ui.TEXT, false);

        boolean hovered = Ui.hit(mouseX, mouseY, closeX - 4, closeY - 4, CLOSE + 8, CLOSE + 8);
        Ui.closeButton(g, closeX, closeY, CLOSE, hovered);
    }

    private void drawRows(GuiGraphics g, int mouseX, int mouseY) {
        int rowTop = top + HEADER_H + GAP;
        for (int i = 0; i < ROWS; i++) {
            int y = rowTop + i * ROW_H;
            boolean hovered = Ui.hit(mouseX, mouseY, left + 1, y, panelW - 2, ROW_H - 1);
            if (hovered) Ui.rect(g, left + 1, y, panelW - 2, ROW_H - 1, Ui.ROW_HOVER);
            else if ((i & 1) == 1) Ui.rect(g, left + 1, y, panelW - 2, ROW_H - 1, Ui.ROW_ALT);

            g.drawString(font, LABELS[i], left + 12, y + 4, Ui.TEXT, false);
            drawDetail(g, i, left + 52, y + 4);
            String value = valueText(i);
            g.drawString(font, value, left + panelW - PAD - font.width(value), y + 4, Ui.GOLD, false);
            Ui.hline(g, left + 1, y + ROW_H - 1, panelW - 2, Ui.ROW_LINE);
        }
    }

    private void drawDetail(GuiGraphics g, int row, int x, int y) {
        int width = panelW - 52 - PAD - 70;
        switch (row) {
            case 0 -> {
                if (state.has("itemId")) {
                    ItemStack stack = stackOf(state.get("itemId").getAsString(), state.get("itemCount").getAsInt());
                    if (!stack.isEmpty()) {
                        g.renderItem(stack, x, y - 4);
                        g.drawString(font, font.plainSubstrByWidth(
                                stack.getHoverName().getString() + " ×" + stack.getCount(), width - 20),
                                x + 20, y, Ui.TEXT_DIM, false);
                        return;
                    }
                }
                hint(g, "右键放入物品", x, y, width);
            }
            case 1 -> {
                if (state.has("fluidId")) {
                    String text = state.get("fluidName").getAsString() + " " + state.get("fluidAmount").getAsInt() + " mB";
                    g.drawString(font, font.plainSubstrByWidth(text, width), x, y, Ui.TEXT_DIM, false);
                    return;
                }
                hint(g, "接入流体管道", x, y, width);
            }
            case 2 -> {
                int stored = intOf("energy");
                int cap = intOf("energyCapacity");
                if (stored > 0) {
                    g.drawString(font, String.format(Locale.ROOT, "%,d / %,d FE", stored, cap), x, y, Ui.TEXT_DIM, false);
                    return;
                }
                hint(g, "接入 FE / GT 能量转换器", x, y, width);
            }
            case 3 -> {
                if (!boolOf("essentiaReady")) {
                    hint(g, "未安装神秘时代", x, y, width);
                    return;
                }
                int total = intOf("essentia");
                if (total > 0) {
                    String text = total + " 点";
                    if (state.has("aspects") && state.getAsJsonObject("aspects").size() > 0) {
                        var aspects = state.getAsJsonObject("aspects");
                        StringBuilder kinds = new StringBuilder();
                        for (String key : aspects.keySet()) {
                            if (kinds.length() > 0) kinds.append(' ');
                            kinds.append(key.substring(key.indexOf(':') + 1)).append('×').append(aspects.get(key).getAsInt());
                        }
                        text = text + "  " + kinds;
                    }
                    g.drawString(font, font.plainSubstrByWidth(text, width), x, y, Ui.TEXT_DIM, false);
                    return;
                }
                hint(g, "接入源质管道", x, y, width);
            }
            default -> {
                if (!boolOf("manaReady")) {
                    hint(g, "未安装植物魔法", x, y, width);
                    return;
                }
                int stored = intOf("mana");
                if (stored > 0) {
                    g.drawString(font, String.format(Locale.ROOT, "%,d / %,d mana", stored, intOf("manaCapacity")),
                            x, y, Ui.TEXT_DIM, false);
                    return;
                }
                hint(g, "接入魔力发射器", x, y, width);
            }
        }
    }

    private void hint(GuiGraphics g, String text, int x, int y, int width) {
        g.drawString(font, font.plainSubstrByWidth(text, width), x, y, Ui.TEXT_FAINT, false);
    }

    private String valueText(int row) {
        int value = switch (row) {
            case 0 -> intOf("itemValue");
            case 1 -> intOf("fluidValue");
            case 2 -> intOf("energyValue");
            case 3 -> intOf("essentiaValue");
            default -> intOf("manaValue");
        };
        return value > 0 ? value + " spur" : "—";
    }

    /** Wallet band: balance on the left, cash <-> wallet conversion on the right. */
    private void drawWallet(GuiGraphics g, int mouseX, int mouseY) {
        boolean enabled = boolOf("walletEnabled");
        Ui.rect(g, left + 1, walletY, panelW - 2, WALLET_H, Ui.SUNKEN);
        Ui.hline(g, left + 1, walletY, panelW - 2, Ui.EDGE_SOFT);
        Ui.hline(g, left + 1, walletY + WALLET_H - 1, panelW - 2, Ui.EDGE_SOFT);

        String balance = enabled
                ? String.format(Locale.ROOT, "钱包 %,d spur", longOf("wallet"))
                : "钱包未启用";
        g.drawString(font, balance, left + 12, walletY + 3, enabled ? Ui.GOLD : Ui.TEXT_FAINT, false);

        String note = enabled
                ? "兑换手续费 " + intOf("feePercent") + "% · 最低取出 " + intOf("minWithdraw")
                : "在 deposit_values.json 中开启 wallet.enabled";
        g.drawString(font, font.plainSubstrByWidth(note, withdrawX - left - 24),
                left + 12, walletY + 13, Ui.TEXT_FAINT, false);

        boolean hoverDeposit = Ui.hit(mouseX, mouseY, depositX, walletButtonY, WALLET_BTN_W, WALLET_BTN_H);
        boolean hoverWithdraw = Ui.hit(mouseX, mouseY, withdrawX, walletButtonY, WALLET_BTN_W, WALLET_BTN_H);
        boolean canWithdraw = enabled && longOf("wallet") >= intOf("minWithdraw");
        Ui.button(g, font, "存入", depositX, walletButtonY, WALLET_BTN_W, WALLET_BTN_H,
                hoverDeposit, enabled, Ui.BTN, Ui.BTN_HOVER, Ui.TEXT);
        Ui.button(g, font, "取出", withdrawX, walletButtonY, WALLET_BTN_W, WALLET_BTN_H,
                hoverWithdraw, canWithdraw, Ui.BTN, Ui.BTN_HOVER, Ui.TEXT);
    }

    private void drawFooter(GuiGraphics g, int mouseX, int mouseY) {
        int bandY = footerY - GAP / 2;
        Ui.rect(g, left + 1, bandY, panelW - 2, top + panelH - bandY - 1, Ui.PANEL_FOOT);
        Ui.hline(g, left + 1, bandY, panelW - 2, Ui.EDGE_SOFT);

        String message = state.has("walletMessage") ? state.get("walletMessage").getAsString()
                : state.has("message") ? state.get("message").getAsString() : "";
        if (!message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, panelW - 90), left + 12, footerY + 1, Ui.TEXT_DIM, false);
        }

        int total = intOf("total");
        String label = "合计 " + total + " spur";
        g.drawString(font, label, left + 12, footerY + 10, total > 0 ? Ui.GOLD : Ui.TEXT_FAINT, false);

        // Daily buyback budget: absent when the ledger is off or uncapped.
        if (state.has("buybackRemaining")) {
            long left = longOf("buybackRemaining");
            String budget = left <= 0 ? "今日额度已用尽" : String.format(Locale.ROOT, "今日剩 %,d", left);
            int color = left <= 0 ? Ui.UP : total > left ? Ui.GOLD : Ui.TEXT_FAINT;
            g.drawString(font, budget, buttonX - 8 - font.width(budget), footerY + 10, color, false);
        }

        boolean affordable = total > 0 && (!state.has("buybackRemaining") || total <= longOf("buybackRemaining"));
        boolean hovered = Ui.hit(mouseX, mouseY, buttonX, buttonY, 64, 18);
        Ui.button(g, font, "回收", buttonX, buttonY, 64, 18, hovered, affordable, Ui.BTN, Ui.BTN_HOVER, Ui.GOLD);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (Ui.hit(mouseX, mouseY, closeX - 4, closeY - 4, CLOSE + 8, CLOSE + 8)) {
                onClose();
                return true;
            }
            if (Ui.hit(mouseX, mouseY, buttonX, buttonY, 64, 18) && intOf("total") > 0
                    && (!state.has("buybackRemaining") || intOf("total") <= longOf("buybackRemaining"))) {
                press(0);
                return true;
            }
            if (boolOf("walletEnabled") && Ui.hit(mouseX, mouseY, depositX, walletButtonY, WALLET_BTN_W, WALLET_BTN_H)) {
                press(1);
                return true;
            }
            if (boolOf("walletEnabled") && Ui.hit(mouseX, mouseY, withdrawX, walletButtonY, WALLET_BTN_W, WALLET_BTN_H)
                    && longOf("wallet") >= intOf("minWithdraw")) {
                press(2);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        // Everything is painted in render(); nothing to blit.
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Suppressed: the header and footer are drawn by hand.
    }

    private int intOf(String key) {
        return state.has(key) && state.get(key).isJsonPrimitive() ? state.get(key).getAsInt() : 0;
    }

    private long longOf(String key) {
        return state.has(key) && state.get(key).isJsonPrimitive() ? state.get(key).getAsLong() : 0L;
    }

    private boolean boolOf(String key) {
        return state.has(key) && state.get(key).getAsBoolean();
    }

    private static ItemStack stackOf(String id, int count) {
        try {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
            return new ItemStack(item, Math.max(1, count));
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }
}
