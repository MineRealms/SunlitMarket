package com.society.sunlitmarket.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.society.sunlitmarket.deposit.DepositBinMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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

    private static final String[] LABELS = {"物品", "流体", "能量", "要素", "魔力"};

    private JsonObject state = new JsonObject();
    private int panelW = 340;
    private int panelH = 190;
    private int left;
    private int top;
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
        panelH = 190;
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        footerY = top + panelH - GAP - 18;
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

    private void drawFooter(GuiGraphics g, int mouseX, int mouseY) {
        int bandY = footerY - GAP / 2;
        Ui.rect(g, left + 1, bandY, panelW - 2, top + panelH - bandY - 1, Ui.PANEL_FOOT);
        Ui.hline(g, left + 1, bandY, panelW - 2, Ui.EDGE_SOFT);

        String message = state.has("message") ? state.get("message").getAsString() : "";
        if (!message.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(message, panelW - 90), left + 12, footerY + 1, Ui.TEXT_DIM, false);
        }

        int total = intOf("total");
        String label = "合计 " + total + " spur";
        g.drawString(font, label, left + 12, footerY + 10, total > 0 ? Ui.GOLD : Ui.TEXT_FAINT, false);

        boolean hovered = Ui.hit(mouseX, mouseY, buttonX, buttonY, 64, 18);
        Ui.button(g, font, "回收", buttonX, buttonY, 64, 18, hovered, total > 0, Ui.BTN, Ui.BTN_HOVER, Ui.GOLD);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (Ui.hit(mouseX, mouseY, closeX - 4, closeY - 4, CLOSE + 8, CLOSE + 8)) {
                onClose();
                return true;
            }
            if (Ui.hit(mouseX, mouseY, buttonX, buttonY, 64, 18) && intOf("total") > 0) {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
