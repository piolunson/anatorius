package dev.project.anatorius.managers;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.project.anatorius.Anatorius;
import lombok.Getter;
import dev.project.anatorius.mixins.accessors.DrawContextAccessor;
import dev.project.anatorius.mixins.accessors.TextRendererAccessor;
import dev.project.anatorius.modules.impl.core.FontModule;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.font.FontRenderer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.awt.*;

@Getter
public class FontManager implements IMinecraft {
    private FontRenderer fontRenderer;

    public void setFontRenderer(FontRenderer renderer) {
        if (fontRenderer != null && fontRenderer != renderer) fontRenderer.close();
        fontRenderer = renderer;
    }

    public boolean customFont() {
        if (fontRenderer == null || Anatorius.MODULE_MANAGER == null) return false;
        FontModule module = Anatorius.MODULE_MANAGER.getModule(FontModule.class);
        return module != null && module.isToggled();
    }

    public boolean globalFont() {
        if (!customFont()) return false;
        FontModule module = module();
        if (!module.global.getValue()) return false;
        return (mc.currentScreen != null ? module.globalMenus : module.globalInGame).getValue();
    }

    private static FontModule module() {
        return Anatorius.MODULE_MANAGER.getModule(FontModule.class);
    }

    private static boolean shadows() {
        return !module().shadowMode.getValue().equalsIgnoreCase("None");
    }

    public void drawText(DrawContext context, String text, int x, int y, Color color) {
        if (customFont()) {
            fontRenderer.drawString(context, text, x, y, color.getRGB(), false);
        } else {
            context.drawText(mc.textRenderer, text, x, y, color.getRGB(), false);
        }
    }

    public void drawTextWithShadow(DrawContext context, String text, int x, int y, Color color) {
        if (customFont()) {
            float fx = x, fy = y;
            fontRenderer.drawStringWithShadow(context, text, fx, fy, color.getRGB(), shadows() ? getShadowOffset() : 0.0f);
        } else {
            context.drawText(mc.textRenderer, text, x, y, color.getRGB(), true);
        }
    }

    public void drawText(DrawContext context, OrderedText text, int x, int y, Color color) {
        if (customFont()) {
            fontRenderer.drawText(context, text, x, y, color.getRGB(), false);
        } else {
            context.drawText(mc.textRenderer, text, x, y, color.getRGB(), false);
        }
    }

    public void drawTextWithShadow(DrawContext context, OrderedText text, int x, int y, Color color) {
        if (customFont()) {
            float fx = x, fy = y;
            fontRenderer.drawTextWithShadow(context, text, fx, fy, color.getRGB(), shadows() ? getShadowOffset() : 0.0f);
        } else {
            context.drawText(mc.textRenderer, text, x, y, color.getRGB(), true);
        }
    }

    public void drawTextWithShadow(MatrixStack matrices, String text, int x, int y, VertexConsumerProvider vertexConsumers, Color color) {
        if (customFont()) {
            VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
            org.joml.Matrix4f matrix = matrices.peek().getPositionMatrix();
            float fx = x, fy = y;
            if (shadows()) fontRenderer.drawString(matrix, immediate, text, fx + getShadowOffset(), fy + getShadowOffset(), color.getRGB(), true);
            fontRenderer.drawString(matrix, immediate, text, fx, fy, color.getRGB(), false);
            immediate.draw();
        } else {
            mc.textRenderer.draw(text, x, y, color.getRGB(), true, matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0);
            mc.getBufferBuilders().getEntityVertexConsumers().draw();
        }
    }

    public void drawTextWithOutline(DrawContext context, String text, int x, int y, Color color, Color outlineColor) {
        if (customFont()) {
            String plain = Formatting.strip(text);
            float fx = x, fy = y;
            int outline = outlineColor.getRGB();
            fontRenderer.drawString(context, plain, fx + 0.5f, fy - 0.5f, outline, false);
            fontRenderer.drawString(context, plain, fx - 0.5f, fy + 0.5f, outline, false);
            fontRenderer.drawString(context, plain, fx + 0.5f, fy + 0.5f, outline, false);
            fontRenderer.drawString(context, plain, fx - 0.5f, fy - 0.5f, outline, false);

            fontRenderer.drawString(context, text, fx, fy, color.getRGB(), false);
        } else {
            net.minecraft.client.render.VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
            mc.textRenderer.drawWithOutline(Text.literal(text).asOrderedText(), x, y, color.getRGB(), outlineColor.getRGB(), new org.joml.Matrix4f(), immediate, 0);
            immediate.draw();
        }
    }

    public void drawRainbowString(DrawContext context, String string, int x, int y, long offset) {
        MutableText builder = Text.empty();

        int[] i = {0};
        Text.literal(string).asOrderedText().accept((index, style, codePoint) -> {
            MutableText text = Text.empty();

            if (style.getColor() == null) {
                long index1 = (long) i[0] * offset;
                Color color = ColorUtils.getOffsetRainbow(index1);
                text.append(Text.literal(String.valueOf(Character.toChars(codePoint))).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color.getRGB()))));
            } else {
                text.append(Text.literal(String.valueOf(Character.toChars(codePoint))).setStyle(style));
            }

            builder.append(text);
            i[0]++;

            return true;
        });

        Anatorius.FONT_MANAGER.drawTextWithShadow(context, builder.asOrderedText(), x, y, Color.WHITE);
    }

    public int getWidth(String text) {
        if (customFont()) {
            return Math.round(fontRenderer.getTextWidth(text));
        } else {
            return mc.textRenderer.getWidth(text);
        }
    }

    public int getWidth(OrderedText text) {
        if (customFont()) {
            return Math.round(fontRenderer.getTextWidth(text));
        } else {
            return mc.textRenderer.getWidth(text);
        }
    }

    public int getHeight() {
        return mc.textRenderer.fontHeight;
    }

    public float getShadowOffset() {
        String mode = module().shadowMode.getValue();
        if (mode.equalsIgnoreCase("None")) return 0.0f;
        if (mode.equalsIgnoreCase("Custom")) return Math.max(-1.0f, Math.min(1.0f, module().shadowOffset.getValue().floatValue()));
        return 0.5f;
    }

    public boolean hasFont(String name) {
        if (name != null && name.equalsIgnoreCase("Quicksand")) return true;
        for (String fontName : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) if (fontName.equalsIgnoreCase(name)) return true;
        return false;
    }
}
