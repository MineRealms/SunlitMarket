package com.society.sunlitmarket.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Flat, hand-drawn widget toolkit for the pawnshop UI.
 *
 * <p>Everything here is a {@link GuiGraphics#fill} call: no textures, no {@code Button}, no
 * nine-slicing. That keeps the layout predictable at every GUI scale and means the window has no
 * dependency on art assets that may not match.
 *
 * <p>Client-only: never referenced from common code.
 */
public final class Ui {
    private Ui() {
    }

    // ------------------------------------------------------------------ palette

    public static final int SCRIM = 0x8C000000;

    public static final int PANEL = 0xFF12151A;
    public static final int PANEL_HEAD = 0xFF1A1F27;
    public static final int PANEL_FOOT = 0xFF171B22;
    public static final int SUNKEN = 0xFF0B0D11;
    public static final int SUNKEN_EDGE = 0xFF06070A;

    public static final int EDGE = 0xFF2A313C;
    public static final int EDGE_SOFT = 0xFF1E242C;

    public static final int TEXT = 0xFFE3E7EE;
    public static final int TEXT_DIM = 0xFF8B94A2;
    public static final int TEXT_FAINT = 0xFF5C6470;
    public static final int GOLD = 0xFFD6A64A;
    public static final int UP = 0xFFD6554F;
    public static final int DOWN = 0xFF4CA36A;

    public static final int ROW_ALT = 0x08FFFFFF;
    public static final int ROW_HOVER = 0x16FFFFFF;
    public static final int ROW_LINE = 0x14FFFFFF;

    public static final int BTN = 0xFF232A34;
    public static final int BTN_HOVER = 0xFF2F3844;
    public static final int BTN_OFF = 0xFF191D23;
    public static final int BUY = 0xFF2A4A34;
    public static final int BUY_HOVER = 0xFF37603F;
    public static final int SELL = 0xFF2A3F4E;
    public static final int SELL_HOVER = 0xFF365064;
    public static final int TAB = 0xFF1E242C;
    public static final int TAB_ON = 0xFF3A3222;

    public static final int TRACK = 0xFF0A0C10;
    public static final int THUMB = 0xFF39424F;
    public static final int THUMB_HOVER = 0xFF4C5868;

    // ------------------------------------------------------------------ primitives

    public static boolean hit(double mx, double my, int x, int y, int w, int h) {
        return w > 0 && h > 0 && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, color);
    }

    public static void hline(GuiGraphics g, int x, int y, int w, int color) {
        rect(g, x, y, w, 1, color);
    }

    public static void vline(GuiGraphics g, int x, int y, int h, int color) {
        rect(g, x, y, 1, h, color);
    }

    /** Outer frame: body, 1px edge, and a slightly darker bottom/right so it reads as raised. */
    public static void frame(GuiGraphics g, int x, int y, int w, int h) {
        rect(g, x, y, w, h, PANEL);
        hline(g, x, y, w, EDGE);
        hline(g, x, y + h - 1, w, 0xFF0A0C10);
        vline(g, x, y, h, EDGE);
        vline(g, x + w - 1, y, h, 0xFF0A0C10);
    }

    /** Recessed well for the list and the chart plot. */
    public static void sunken(GuiGraphics g, int x, int y, int w, int h) {
        rect(g, x, y, w, h, SUNKEN);
        hline(g, x, y, w, SUNKEN_EDGE);
        vline(g, x, y, h, SUNKEN_EDGE);
        hline(g, x, y + h - 1, w, EDGE_SOFT);
        vline(g, x + w - 1, y, h, EDGE_SOFT);
    }

    // ------------------------------------------------------------------ widgets

    /**
     * Flat button. {@code base}/{@code hover} are the body colours; the label is centred and
     * clipped to the button width so a long price never spills over its neighbour.
     */
    public static void button(GuiGraphics g, Font font, String label, int x, int y, int w, int h,
                              boolean hovered, boolean enabled, int base, int hover, int textColor) {
        if (w <= 0 || h <= 0) return;
        int body = !enabled ? BTN_OFF : (hovered ? hover : base);
        rect(g, x, y, w, h, body);
        hline(g, x, y, w, enabled ? 0x24FFFFFF : 0x0AFFFFFF);
        hline(g, x, y + h - 1, w, 0x50000000);
        vline(g, x, y, h, 0x14FFFFFF);
        vline(g, x + w - 1, y, h, 0x50000000);
        if (label == null || label.isEmpty()) return;
        String text = font.plainSubstrByWidth(label, w - 6);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2,
                enabled ? textColor : TEXT_FAINT, false);
    }

    /** Chip used for index and period tabs. */
    public static void chip(GuiGraphics g, Font font, String label, int x, int y, int w, int h,
                            boolean active, boolean hovered) {
        if (w <= 0 || h <= 0) return;
        rect(g, x, y, w, h, active ? TAB_ON : (hovered ? BTN_HOVER : TAB));
        hline(g, x, y, w, active ? 0x40D6A64A : 0x18FFFFFF);
        hline(g, x, y + h - 1, w, 0x50000000);
        vline(g, x, y, h, active ? 0x40D6A64A : 0x10FFFFFF);
        vline(g, x + w - 1, y, h, 0x50000000);
        if (active) rect(g, x, y + h, w, 1, GOLD);
        String text = font.plainSubstrByWidth(label, w - 8);
        g.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2,
                active ? GOLD : (hovered ? TEXT : TEXT_DIM), false);
    }

    /** 9px "X" glyph built from two 1px diagonals. */
    public static void closeGlyph(GuiGraphics g, int x, int y, int size, int color) {
        for (int i = 0; i < size; i++) {
            rect(g, x + i, y + i, 1, 1, color);
            rect(g, x + size - 1 - i, y + i, 1, 1, color);
        }
    }

    public static void closeButton(GuiGraphics g, int x, int y, int size, boolean hovered) {
        if (hovered) {
            rect(g, x - 3, y - 3, size + 6, size + 6, 0x30FFFFFF);
            rect(g, x - 3, y - 3, size + 6, 1, 0x30FFFFFF);
        }
        closeGlyph(g, x, y, size, hovered ? TEXT : TEXT_DIM);
    }

    /** Track + thumb. {@code thumbY}/{@code thumbH} are already in screen space. */
    public static void scrollbar(GuiGraphics g, int x, int y, int w, int h,
                                 int thumbY, int thumbH, boolean hovered) {
        rect(g, x, y, w, h, TRACK);
        vline(g, x, y, h, 0xFF000000);
        if (thumbH >= h) return;
        rect(g, x, thumbY, w, thumbH, hovered ? THUMB_HOVER : THUMB);
        hline(g, x, thumbY, w, 0x24FFFFFF);
    }
}
