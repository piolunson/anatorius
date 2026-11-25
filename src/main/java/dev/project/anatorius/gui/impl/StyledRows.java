package dev.project.anatorius.gui.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.gui.api.Button;
import dev.project.anatorius.gui.style.GuiStyle;
import dev.project.anatorius.utils.graphics.Renderer2D;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

final class StyledRows {
    static final Color GREY = new Color(0xAA, 0xAA, 0xAA);

    private StyledRows() {
    }

    static int inset(GuiStyle s) {
        return switch (s) {
            case LAVENDER -> 2;
            case MINIMAL -> 4;
            default -> 0;
        };
    }

    static Color outline(GuiStyle s) {
        return switch (s) {
            case LAVENDER -> GuiStyle.LAVENDER_OUTLINE;
            case MINIMAL -> GuiStyle.MINIMAL_TRACK;
            default -> GuiStyle.EDGE;
        };
    }

    static int left(Button b, GuiStyle s) {
        return b.getX() + inset(s);
    }

    static int right(Button b, GuiStyle s) {
        return b.getX() + b.getWidth() - inset(s);
    }

    static int textX(Button b, GuiStyle s) {
        return b.getX() + s.textInset() + (s == GuiStyle.MINIMAL ? 4 : 1);
    }

    static Color label(GuiStyle s) {
        return s == GuiStyle.MINIMAL ? GuiStyle.MINIMAL_LABEL : Color.WHITE;
    }

    static int valueRight(Button b, GuiStyle s) {
        return right(b, s) - (s == GuiStyle.MINIMAL ? 6 : 4);
    }

    static int sliderLeft(Button b, GuiStyle s) {
        return s == GuiStyle.MINIMAL ? textX(b, s) : left(b, s) + 5;
    }

    static int sliderRight(Button b, GuiStyle s) {
        return s == GuiStyle.MINIMAL ? valueRight(b, s) : right(b, s) - 5;
    }

    static boolean hovered(Button b, GuiStyle s, int height, int mouseX, int mouseY) {
        return mouseX >= left(b, s) && mouseX < right(b, s) && mouseY >= b.getY() && mouseY < b.getY() + height;
    }

    static void hoverOverlay(DrawContext context, Button b, GuiStyle s, int height, boolean hovered) {
        if (!hovered) return;
        if (s == GuiStyle.MINIMAL) Renderer2D.renderRoundedQuad(context, left(b, s), b.getY() + 1, right(b, s), b.getY() + height - 1, 3.0f, s.hover());
        else Renderer2D.renderQuad(context, left(b, s), b.getY(), right(b, s), b.getY() + height, s.hover());
    }

    static void text(DrawContext context, String text, int x, int rowY, int height, Color color) {
        Anatorius.FONT_MANAGER.drawTextWithShadow(context, text, x, GuiStyle.textY(rowY, height), color);
    }

    static int labelValue(DrawContext context, Button b, GuiStyle s, int height, String label, String value) {
        return labelValue(context, b, s, height, textX(b, s), label, value);
    }

    static int labelValue(DrawContext context, Button b, GuiStyle s, int height, int x, String label, String value) {
        if (s == GuiStyle.MINIMAL) {
            text(context, label.strip().replaceAll(":$", ""), x, b.getY(), height, label(s));
            int vx = valueRight(b, s) - Anatorius.FONT_MANAGER.getWidth(value);
            text(context, value, vx, b.getY(), height, Color.WHITE);
            return vx + Anatorius.FONT_MANAGER.getWidth(value);
        }
        text(context, label, x, b.getY(), height, Color.WHITE);
        x += Anatorius.FONT_MANAGER.getWidth(label);
        text(context, value, x, b.getY(), height, GREY);
        return x + Anatorius.FONT_MANAGER.getWidth(value);
    }

    static void box(DrawContext context, Button b, GuiStyle s, int height, boolean hovered) {
        if (s == GuiStyle.MINIMAL) {
            hoverOverlay(context, b, s, height, hovered);
            return;
        }
        int x1 = left(b, s) + 4, x2 = right(b, s) - 4, y1 = b.getY() + 1, y2 = b.getY() + height - 1;
        if (hovered) Renderer2D.renderQuad(context, x1, y1, x2, y2, s.hover());
        Renderer2D.renderOutline(context, x1, y1, x2, y2, outline(s));
    }

    static int boxTextX(Button b, GuiStyle s) {
        return s == GuiStyle.MINIMAL ? textX(b, s) : left(b, s) + 4 + 3;
    }

    static void checkbox(DrawContext context, Button b, GuiStyle s, int height, boolean checked, boolean hovered) {
        if (s == GuiStyle.MINIMAL) {
            toggle(context, b, s, height, checked, hovered);
            return;
        }
        int x2 = right(b, s) - 4, x1 = x2 - 9;
        int y1 = b.getY() + (height - 9) / 2, y2 = y1 + 9;
        if (checked) {
            Renderer2D.renderQuad(context, x1, y1, x2, y2, hovered ? s.accentHover() : s.accent());
            Renderer2D.renderQuad(context, x1 + 3, y1 + 3, x1 + 6, y1 + 6, Color.WHITE);
        } else if (hovered) {
            Renderer2D.renderQuad(context, x1, y1, x2, y2, s.hover());
        }
        Renderer2D.renderOutline(context, x1, y1, x2, y2, outline(s));
    }

    static void toggle(DrawContext context, Button b, GuiStyle s, int height, boolean on, boolean hovered) {
        float x2 = valueRight(b, s), x1 = x2 - 14;
        float y1 = b.getY() + (height - 8) / 2.0f, y2 = y1 + 8;
        Color track = on ? s.accentAlpha(hovered ? 255 : 225) : new Color(255, 255, 255, hovered ? 46 : 32);
        Renderer2D.renderRoundedQuad(context, x1, y1, x2, y2, 4.0f, track);
        float knob = on ? x2 - 7 : x1 + 1;
        Renderer2D.renderRoundedQuad(context, knob, y1 + 1, knob + 6, y2 - 1, 3.0f, on ? Color.WHITE : new Color(200, 200, 210));
    }

    static void slider(DrawContext context, Button b, GuiStyle s, int height, double filled, boolean hovered) {
        float x1 = sliderLeft(b, s), x2 = sliderRight(b, s);
        float y2 = b.getY() + height - 1.5f, y1 = y2 - 2;
        Renderer2D.renderRoundedQuad(context, x1, y1, x2, y2, 1.0f, GuiStyle.MINIMAL_TRACK);
        float fx = (float) Math.max(x1, Math.min(x2, x1 + filled));
        if (fx > x1) Renderer2D.renderRoundedQuad(context, x1, y1, fx, y2, 1.0f, s.accentAlpha(255));
        if (hovered) Renderer2D.renderRoundedQuad(context, fx - 2.5f, y1 - 1.5f, fx + 2.5f, y2 + 1.5f, 2.5f, Color.WHITE);
    }
}
