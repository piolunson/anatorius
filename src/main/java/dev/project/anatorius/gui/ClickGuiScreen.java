package dev.project.anatorius.gui;

import dev.project.anatorius.Anatorius;
import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import dev.project.anatorius.gui.api.Button;
import dev.project.anatorius.gui.api.Frame;
import dev.project.anatorius.gui.style.GuiStyle;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer2D;
import dev.project.anatorius.utils.graphics.winter.WinterTheme;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.awt.*;
import java.util.ArrayList;

@Getter
@Setter
public class ClickGuiScreen extends Screen {
    private final ArrayList<Frame> frames = new ArrayList<>();
    private final ArrayList<Button> buttons = new ArrayList<>();

    private final Timer lineTimer = new Timer();
    private boolean showLine = false;
    private Color colorClipboard = null;

    private GuiStyle builtStyle;
    private int builtWidth;

    private static float scale = 1.0f;
    private float builtScale = 1.0f;

    public static float getScale() {
        return scale;
    }

    public ClickGuiScreen() {
        super(Text.literal(Anatorius.MOD_ID + "-click-gui"));
        buildFrames(GuiStyle.current());
    }

    private void buildFrames(GuiStyle style) {
        frames.clear();
        int screenWidth = (int) (net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledWidth() / scale);
        Module.Category[] categories = Module.Category.values();
        int step = style.frameWidth + style.gap;
        int startX = switch (style) {
            case EARTHHACK -> 2;
            case LAVENDER, MINIMAL -> Math.max(2, (screenWidth - (categories.length * step - style.gap)) / 2);
            default -> 6;
        };
        int x = startX;
        int y = switch (style) {
            case EARTHHACK -> 2;
            case LAVENDER -> 12;
            case MINIMAL -> 22;
            default -> 5;
        };
        for (Module.Category category : categories) {
            frames.add(new Frame(category, x, y, style.frameWidth, style.headerHeight, style.rowHeight));
            x += step;
        }
        builtStyle = style;
        builtWidth = this.width;
        builtScale = scale;
    }

    @Override
    protected void init() {
        super.init();
        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);
        scale = clickGui == null ? 1.0f : Math.max(0.5f, Math.min(1.5f, clickGui.scale.getValue().floatValue()));
        if (scale != builtScale) buildFrames(GuiStyle.current());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiStyle style = GuiStyle.current();
        if (style != builtStyle || ((style == GuiStyle.LAVENDER || style == GuiStyle.MINIMAL) && this.width != builtWidth)) buildFrames(style);
        super.render(context, mouseX, mouseY, delta);
        if (lineTimer.hasTimeElapsed(400L)) {
            showLine = !showLine;
            lineTimer.reset();
        }
        context.getMatrices().pushMatrix();
        context.getMatrices().scale(scale, scale);
        int scaledX = (int) (mouseX / scale), scaledY = (int) (mouseY / scale);
        for (Frame frame : frames)
            frame.render(context, scaledX, scaledY, delta);
        if (WinterTheme.clickGuiActive()) {
            for (int i = 0; i < frames.size(); i++) {
                Frame frame = frames.get(i);
                WinterTheme.renderFrameCap(context, frame.getX(), frame.getY(), frame.getWidth(), i + 1);
            }
        }
        context.getMatrices().popMatrix();
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double deltaX, double deltaY) {
        for (Frame frame : frames)
            frame.mouseDragged(click.x() / scale, click.y() / scale, click.button(), deltaX / scale, deltaY / scale);
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        for (Frame frame : frames) {
            frame.mouseClicked(click.x() / scale, click.y() / scale, click.button());
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        for (Frame frame : frames) {
            frame.mouseReleased(click.x() / scale, click.y() / scale, click.button());
        }

        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (Frame frame : frames) {
            frame.mouseScrolled(mouseX / scale, mouseY / scale, horizontalAmount, verticalAmount);
        }

        return this.hoveredElement(mouseX, mouseY)
                .filter(element -> element.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)).isPresent();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        for (Frame frame : frames) {
            frame.keyPressed(input.key(), input.scancode(), input.modifiers());
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharInput input) {
        for (Frame frame : frames) {
            frame.charTyped((char) input.codepoint(), input.modifiers());
        }
        return super.charTyped(input);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (dev.project.anatorius.modules.impl.visuals.BlurModule.active()) applyBlur(context);
        GuiStyle style = GuiStyle.current();
        if (style == GuiStyle.MINIMAL) Renderer2D.renderGradient(context, 0, 0, this.width, this.height, new Color(6, 6, 10, 90), new Color(6, 6, 10, 150));
        else if (style != GuiStyle.EARTHHACK) Renderer2D.renderQuad(context, 0, 0, this.width, this.height, new Color(0, 0, 0, 100));
        if (WinterTheme.clickGuiActive()) WinterTheme.renderClickGuiSnow(context, this.width, this.height);
    }

    @Override
    public void close() {
        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);
        if (clickGui.isToggled()) clickGui.setToggled(false);
        else super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public static Color getButtonColor(int index, int alpha) {
        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);
        Color base = clickGui.isRainbow() ? ColorUtils.getOffsetRainbow(index * 10L) : clickGui.color.getColor();
        return ColorUtils.getColor(base, alpha);
    }

    public static Color getModuleButtonColor(int index, boolean enabled) {
        return getModuleButtonColor(index, -1, enabled);
    }

    public static Color getModuleButtonColor(int index, int alphaOverride, boolean enabled) {
        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);

        Color base;
        if (enabled) {
            base = clickGui.enabled.getColor();
        } else {
            base = clickGui.button.getColor();
        }

        if (clickGui.tint.getValue()) {
            Color accent = clickGui.isRainbow() ? ColorUtils.getOffsetRainbow(index * 10L) : clickGui.color.getColor();
            base = mix(base, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), base.getAlpha()), 0.35f);
        }

        if (alphaOverride >= 0) {
            return ColorUtils.getColor(base, alphaOverride);
        }
        return base;
    }

    public static Color darken(Color color, float factor) {
        factor = Math.max(0.0f, Math.min(1.0f, factor));
        return new Color(
                (int) (color.getRed() * factor),
                (int) (color.getGreen() * factor),
                (int) (color.getBlue() * factor),
                color.getAlpha());
    }

    public static Color mix(Color a, Color b, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(r, g, bl, al);
    }
}
