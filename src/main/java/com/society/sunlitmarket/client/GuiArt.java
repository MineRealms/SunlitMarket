package com.society.sunlitmarket.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Texture helpers for the pawnshop UI. Client-only: this class is never referenced from common
 * code, so a dedicated server never tries to resolve {@code net.minecraft.client.*}.
 *
 * <p>The textures come from the Deep Space Pawnshop art dump and are nine-sliced rather than
 * scaled, so a 1px border stays 1px no matter how large the window is.
 */
public final class GuiArt {
    public static final ResourceLocation PANEL = texture("panel.png");
    public static final ResourceLocation TAB = texture("tab.png");
    public static final ResourceLocation TAB_ACTIVE = texture("tab_active.png");
    public static final ResourceLocation CLOSE = texture("close.png");
    public static final ResourceLocation SCROLL_TRACK = texture("scroll_track.png");
    public static final ResourceLocation SCROLL_HANDLE = texture("scroll_handle.png");
    public static final ResourceLocation SEARCH = texture("search.png");
    public static final ResourceLocation SEARCH_ICON = texture("search_icon.png");
    public static final ResourceLocation MERCHANT = texture("merchant.png");

    /** Source sizes; nine-slicing needs the real texture dimensions. */
    public static final int PANEL_W = 160, PANEL_H = 44, PANEL_SLICE = 4;
    public static final int TAB_W = 129, TAB_H = 31, TAB_SLICE = 12;
    public static final int SEARCH_W = 32, SEARCH_H = 32, SEARCH_SLICE = 6;
    public static final int TRACK_W = 18, TRACK_H = 688, TRACK_SLICE = 6;
    public static final int HANDLE_W = 18, HANDLE_H = 134, HANDLE_SLICE = 6;

    private GuiArt() {
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation("sunlitmarket", "textures/gui/" + name);
    }

    /** Nine-slice with one uniform slice width; vanilla clamps it to half the target size. */
    public static void nineSlice(GuiGraphics g, ResourceLocation tex, int texW, int texH, int slice,
                                 int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        g.blitNineSliced(tex, x, y, w, h, slice, texW, texH, 0, 0);
    }

    /** Draws a texture 1:1 at its native size. */
    public static void icon(GuiGraphics g, ResourceLocation tex, int x, int y) {
        g.blit(tex, x, y, 0, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    /** Draws a 16x16 crop of a texture that is not 16x16. */
    public static void icon(GuiGraphics g, ResourceLocation tex, int texW, int texH, int x, int y) {
        g.blit(tex, x, y, 0, 0.0F, 0.0F, 16, 16, texW, texH);
    }

    /** The shop window frame. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, PANEL, PANEL_W, PANEL_H, PANEL_SLICE, x, y, w, h);
    }

    /** A tab; the 12px left cap keeps the 45-degree cut. */
    public static void tab(GuiGraphics g, int x, int y, int w, int h, boolean active) {
        nineSlice(g, active ? TAB_ACTIVE : TAB, TAB_W, TAB_H, TAB_SLICE, x, y, w, h);
    }

    public static void searchField(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, SEARCH, SEARCH_W, SEARCH_H, SEARCH_SLICE, x, y, w, h);
    }

    public static void scrollTrack(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, SCROLL_TRACK, TRACK_W, TRACK_H, TRACK_SLICE, x, y, w, h);
    }

    public static void scrollHandle(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, SCROLL_HANDLE, HANDLE_W, HANDLE_H, HANDLE_SLICE, x, y, w, h);
    }
}
