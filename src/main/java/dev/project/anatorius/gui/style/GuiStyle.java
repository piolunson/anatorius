package dev.project.anatorius.gui.style;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.core.GuiChangerModule;

import java.awt.Color;

public enum GuiStyle {
    ANATORIUS(92, 14, 14, 4),
    EARTHHACK(110, 16, 14, 2),
    LAVENDER(100, 12, 12, 2),
    MINIMAL(112, 20, 15, 8);

    public final int frameWidth, headerHeight, rowHeight, gap;

    public static final Color EARTH_BODY = new Color(0, 0, 0, 0x92);
    public static final Color EARTH_HOVER = new Color(0x33, 0x33, 0x33, 0x66);
    public static final Color EDGE = new Color(0, 0, 0, 255);
    public static final Color LAVENDER_BODY = new Color(2, 2, 4, 235);
    public static final Color LAVENDER_HEADER = new Color(64, 62, 88, 235);
    public static final Color LAVENDER_OUTLINE = new Color(95, 92, 125, 255);
    public static final Color LAVENDER_HOVER = new Color(255, 255, 255, 14);
    public static final Color MINIMAL_PANEL = new Color(15, 15, 19, 236);
    public static final Color MINIMAL_DIVIDER = new Color(255, 255, 255, 16);
    public static final Color MINIMAL_HOVER = new Color(255, 255, 255, 11);
    public static final Color MINIMAL_MUTED = new Color(142, 142, 154);
    public static final Color MINIMAL_LABEL = new Color(190, 190, 200);
    public static final Color MINIMAL_TRACK = new Color(255, 255, 255, 26);
    public static final float MINIMAL_RADIUS = 6.0f;

    GuiStyle(int frameWidth, int headerHeight, int rowHeight, int gap) {
        this.frameWidth = frameWidth;
        this.headerHeight = headerHeight;
        this.rowHeight = rowHeight;
        this.gap = gap;
    }

    public static GuiStyle current() {
        GuiChangerModule module = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(GuiChangerModule.class);
        return module == null ? ANATORIUS : module.style();
    }

    public boolean themed() {
        return this != ANATORIUS;
    }

    public Color accent() {
        GuiChangerModule module = Anatorius.MODULE_MANAGER.getModule(GuiChangerModule.class);
        return switch (this) {
            case EARTHHACK -> module.earthColor.getColor();
            case LAVENDER -> module.lavenderColor.getColor();
            case MINIMAL -> module.minimalColor.getColor();
            default -> Color.WHITE;
        };
    }

    public Color accentHover() {
        Color c = accent();
        return new Color(Math.min(255, c.getRed() + 30), Math.min(255, c.getGreen() + 30), Math.min(255, c.getBlue() + 30), c.getAlpha());
    }

    public Color text(boolean enabled) {
        if (enabled) return Color.WHITE;
        if (this == MINIMAL) return MINIMAL_MUTED;
        return this == LAVENDER ? new Color(0xB1, 0xB1, 0xB1) : new Color(0xAA, 0xAA, 0xAA);
    }

    public int textInset() {
        return switch (this) {
            case LAVENDER -> 5;
            case MINIMAL -> 11;
            default -> 4;
        };
    }

    public Color hover() {
        return switch (this) {
            case LAVENDER -> LAVENDER_HOVER;
            case MINIMAL -> MINIMAL_HOVER;
            default -> EARTH_HOVER;
        };
    }

    public Color accentAlpha(int alpha) {
        Color c = accent();
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static int textY(int y, int height) {
        return y + (height - Anatorius.FONT_MANAGER.getHeight()) / 2 + 1;
    }
}
