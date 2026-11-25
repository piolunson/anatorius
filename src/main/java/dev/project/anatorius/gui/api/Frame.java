package dev.project.anatorius.gui.api;

import dev.project.anatorius.Anatorius;
import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.gui.ClickGuiScreen;
import dev.project.anatorius.gui.impl.WhitelistButton;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.gui.impl.ModuleButton;
import dev.project.anatorius.gui.style.GuiStyle;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import dev.project.anatorius.utils.animations.Animation;
import dev.project.anatorius.utils.animations.Easing;
import dev.project.anatorius.utils.graphics.Renderer2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Frame {
    private final Module.Category category;
    private int x, y, width, height, totalHeight, dragX = 0, dragY = 0, textPadding = 3;
    public boolean open = true, dragging = false;
    private final ArrayList<Button> buttons = new ArrayList<>();
    private float scrollOffset = 0.0f;
    private float rawTargetScrollOffset = 0.0f;
    private float scrollVelocity = 0.0f;
    private long lastScrollInputMs = 0L;
    private int visibleBodyHeight = 0;

    private final Animation openAnimation = new Animation(280, Easing.Method.EASE_OUT_CUBIC);

    public Frame(Module.Category category, int x, int y, int width, int height) {
        this(category, x, y, width, height, height);
    }

    public Frame(Module.Category category, int x, int y, int width, int height, int rowHeight) {
        this.category = category;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        for (Module module : Anatorius.MODULE_MANAGER.getModules(category))
            buttons.add(new ModuleButton(module, this, rowHeight));
    }

    public float getOpenProgress() {
        return openAnimation.get(open ? 1.0f : 0.0f);
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (dragging) {
            setX(mouseX - dragX);
            setY(mouseY - dragY);
        }

        this.totalHeight = height;

        float openProgress = getOpenProgress();

        int contentHeight = 0;

        if (openProgress > 0.001f) {
            int layoutY = y + height + (int) scrollOffset + 1;
            int unscrolledLayoutY = y + height + 1;

            for (Button button : buttons) {
                button.setX(x);
                button.setY(layoutY);
                layoutY += button.getHeight();
                unscrolledLayoutY += button.getHeight();

                if (button instanceof ModuleButton moduleButton) {
                    moduleButton.updateButtonVisibility();
                    int animatedButtonsHeight = moduleButton.getAnimatedButtonsHeight();

                    for (Button b : moduleButton.getButtons()) {
                        if (!b.isVisible())
                            continue;

                        int nextHeight = moduleButton.getAnimatedButtonHeight(b);
                        if (nextHeight <= 0)
                            continue;

                        b.setX(x);
                        b.setY(layoutY);
                        layoutY += nextHeight;
                        unscrolledLayoutY += nextHeight;
                    }
                }
            }

            contentHeight = Math.max(0, unscrolledLayoutY - (y + height + 1));
            this.totalHeight = height + 1 + contentHeight;
        }

        int screenHeight = (int) (MinecraftClient.getInstance().getWindow().getScaledHeight() / dev.project.anatorius.gui.ClickGuiScreen.getScale());
        int maxAvailableBody = Math.max(0, (screenHeight - 6) - (y + height));
        int fullBodyHeight = openProgress > 0.001f ? Math.min(contentHeight + 1, maxAvailableBody) : 0;
        visibleBodyHeight = (int) (fullBodyHeight * openProgress);

        int maxScroll = Math.max(0, (contentHeight + 1) - fullBodyHeight);
        float minScrollOffset = -maxScroll;
        float maxScrollOffset = 0.0f;

        if (openProgress < 0.01f || visibleBodyHeight <= 0 || maxScroll <= 0) {
            rawTargetScrollOffset = 0.0f;
        }

        long now = System.currentTimeMillis();
        if (now - lastScrollInputMs > 180L) {
            if (rawTargetScrollOffset > maxScrollOffset)
                rawTargetScrollOffset = maxScrollOffset;
            if (rawTargetScrollOffset < minScrollOffset)
                rawTargetScrollOffset = minScrollOffset;
        }

        float target;
        if (rawTargetScrollOffset > maxScrollOffset) {
            float over = rawTargetScrollOffset - maxScrollOffset;
            target = maxScrollOffset + over * 0.25f;
        } else if (rawTargetScrollOffset < minScrollOffset) {
            float over = rawTargetScrollOffset - minScrollOffset;
            target = minScrollOffset + over * 0.25f;
        } else {
            target = rawTargetScrollOffset;
        }

        float stiffness = 0.20f;
        float damping = 0.70f;

        float force = (target - scrollOffset) * stiffness;
        scrollVelocity += force;
        scrollVelocity *= damping;
        scrollOffset += scrollVelocity;

        if (Math.abs(scrollVelocity) < 0.01f && Math.abs(target - scrollOffset) < 0.05f) {
            scrollVelocity = 0.0f;
            scrollOffset = target;
        }

        GuiStyle style = GuiStyle.current();
        if (style.themed()) {
            renderStyled(context, mouseX, mouseY, delta, style, openProgress);
            return;
        }

        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);

        String headerShaderMode = clickGui.headerShader.getValue();
        boolean useHeaderShader = headerShaderMode.equals("Fluid") || headerShaderMode.equals("Colorful Plasma");

        if (useHeaderShader) {
            Color headerBase = clickGui.isRainbow()
                ? dev.project.anatorius.utils.color.ColorUtils.getOffsetRainbow(y * 10L)
                : clickGui.color.getColor();

            Color headerShaderColor = new Color(
                headerBase.getRed(),
                headerBase.getGreen(),
                headerBase.getBlue(),
                headerBase.getAlpha());

            dev.project.anatorius.utils.graphics.ClickGuiShader.getInstance().render(
                context,
                headerShaderMode,
                x,
                y,
                width,
                height,
                headerShaderColor,
                delta);
        } else {
            Color headerBase = clickGui.isRainbow()
                ? dev.project.anatorius.utils.color.ColorUtils.getOffsetRainbow(y * 10L)
                : clickGui.color.getColor();

            if (clickGui.gradient.getValue()) {
                Renderer2D.renderSidewaysGradient(context, x, y, x + width, y + height,
                        ClickGuiScreen.darken(headerBase, 0.85f), headerBase);
            } else {
                Renderer2D.renderQuad(context, x, y, x + width, y + height, headerBase);
            }
        }

        Anatorius.FONT_MANAGER.drawTextWithShadow(context, category.getName(), x + textPadding, y + 2,
                clickGui.textColor.getColor());

        if (openProgress > 0.001f) {
            int bodyAlpha = (int) (openProgress * clickGui.background.getColor().getAlpha());
            Color bg = new Color(
                    clickGui.background.getColor().getRed(),
                    clickGui.background.getColor().getGreen(),
                    clickGui.background.getColor().getBlue(),
                    bodyAlpha);

            String shaderMode = clickGui.backgroundShader.getValue();
            boolean useShader = shaderMode.equals("Fluid") || shaderMode.equals("Colorful Plasma");

            if (useShader && visibleBodyHeight > 0) {
                Color shaderColor = new Color(
                    bg.getRed(),
                    bg.getGreen(),
                    bg.getBlue(),
                    bodyAlpha);
                dev.project.anatorius.utils.graphics.ClickGuiShader.getInstance().render(
                    context,
                    shaderMode,
                    x,
                    y + height,
                    width,
                    visibleBodyHeight,
                    shaderColor,
                    delta);
            } else if (clickGui.gradient.getValue()) {
                Color bgDark = ClickGuiScreen.darken(bg, 0.75f);
                bgDark = new Color(bgDark.getRed(), bgDark.getGreen(), bgDark.getBlue(), bodyAlpha);
                Renderer2D.renderGradient(context, x, y + height, x + width,
                        y + height + visibleBodyHeight, bg, bgDark);
            } else {
                Renderer2D.renderQuad(context, x, y + height, x + width, y + height + visibleBodyHeight,
                        bg);
            }

            renderButtons(context, mouseX, mouseY, delta);
        }

        Color baseAccent = clickGui.isRainbow() ? dev.project.anatorius.utils.color.ColorUtils.getOffsetRainbow(y * 10L) : clickGui.color.getColor();
        int outlineAlphaVal = (int) (openProgress * baseAccent.getAlpha());
        Color outlineColor = new Color(baseAccent.getRed(), baseAccent.getGreen(), baseAccent.getBlue(), Math.max(10, outlineAlphaVal));
        Color headerOutlineColor = new Color(baseAccent.getRed(), baseAccent.getGreen(), baseAccent.getBlue(), baseAccent.getAlpha());

        if (openProgress > 0.001f) {
            Renderer2D.renderOutline(context, x, y, x + width, y + height + visibleBodyHeight, outlineColor);
            Renderer2D.renderQuad(context, x, y + height - 1, x + width, y + height, outlineColor);
        } else {
            Renderer2D.renderOutline(context, x, y, x + width, y + height, headerOutlineColor);
        }
    }

    private void renderButtons(DrawContext context, int mouseX, int mouseY, float delta) {
        int bodyTop = y + height;
        int bodyBottom = bodyTop + visibleBodyHeight;
        context.enableScissor(x, bodyTop, x + width, bodyBottom);
        for (Button button : buttons) {
            int blockBottom = button.getY() + button.getHeight();
            if (button instanceof ModuleButton moduleButton) blockBottom += moduleButton.getAnimatedButtonsHeight();

            if (button.getY() < bodyBottom && blockBottom > bodyTop) {
                button.render(context, mouseX, mouseY, delta);
            }
        }
        context.disableScissor();
    }

    private void renderStyled(DrawContext context, int mouseX, int mouseY, float delta, GuiStyle style, float openProgress) {
        int bodyTop = y + height;
        int bodyBottom = bodyTop + visibleBodyHeight;
        int textY = GuiStyle.textY(y, height);

        if (style == GuiStyle.MINIMAL) {
            renderMinimal(context, mouseX, mouseY, delta, style, openProgress);
            return;
        }

        if (style == GuiStyle.EARTHHACK) {
            Color accent = style.accent();
            if (openProgress > 0.001f && visibleBodyHeight > 0) {
                Renderer2D.renderQuad(context, x, bodyTop + 1, x + width, bodyBottom, withAlpha(GuiStyle.EARTH_BODY, openProgress));
            }
            Renderer2D.renderQuad(context, x, y, x + width, y + height, new Color(accent.getRed(), accent.getGreen(), accent.getBlue()));
            Renderer2D.renderOutline(context, x, y, x + width, y + height, GuiStyle.EDGE);
            Anatorius.FONT_MANAGER.drawTextWithShadow(context, category.getName(), x + 3, textY, Color.WHITE);
            String count = "[" + buttons.size() + "]";
            Anatorius.FONT_MANAGER.drawTextWithShadow(context, count, x + width - 3 - Anatorius.FONT_MANAGER.getWidth(count), textY, Color.WHITE);
        } else {
            Renderer2D.renderQuad(context, x, y, x + width, bodyBottom, withAlpha(GuiStyle.LAVENDER_BODY, openProgress > 0.001f ? 1.0f : 0.0f));
            Renderer2D.renderQuad(context, x, y, x + width, y + height, GuiStyle.LAVENDER_HEADER);
            Anatorius.FONT_MANAGER.drawTextWithShadow(context, category.getName(), x + style.textInset(), textY, Color.WHITE);
        }

        if (openProgress > 0.001f) renderButtons(context, mouseX, mouseY, delta);

        if (style == GuiStyle.LAVENDER) {
            Renderer2D.renderOutline(context, x, y, x + width, Math.max(bodyBottom, y + height), GuiStyle.LAVENDER_OUTLINE);
        }
    }

    private void renderMinimal(DrawContext context, int mouseX, int mouseY, float delta, GuiStyle style, float openProgress) {
        boolean open = openProgress > 0.001f && visibleBodyHeight > 0;
        int bottom = y + height + (open ? visibleBodyHeight + 4 : 0);
        float radius = GuiStyle.MINIMAL_RADIUS;

        Renderer2D.renderRoundedShadow(context, x, y, x + width, bottom, radius, 6, 70);
        Renderer2D.renderRoundedQuad(context, x, y, x + width, bottom, radius, GuiStyle.MINIMAL_PANEL);

        int textY = GuiStyle.textY(y, height);
        Anatorius.FONT_MANAGER.drawTextWithShadow(context, category.getName(), x + style.textInset(), textY, Color.WHITE);
        String count = String.valueOf(buttons.size());
        Anatorius.FONT_MANAGER.drawTextWithShadow(context, count, x + width - style.textInset() - Anatorius.FONT_MANAGER.getWidth(count), textY, GuiStyle.MINIMAL_MUTED);

        if (open) {
            Renderer2D.renderQuad(context, x + 6, y + height - 1, x + width - 6, y + height, withAlpha(GuiStyle.MINIMAL_DIVIDER, openProgress));
            renderButtons(context, mouseX, mouseY, delta);
        }
    }

    private static Color withAlpha(Color color, float factor) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * Math.max(0f, Math.min(1f, factor))));
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovering(mouseX, mouseY)) {
            if (button == 0) {
                dragging = true;
                dragX = (int) (mouseX - getX());
                dragY = (int) (mouseY - getY());
            } else if (button == 1) {
                open = !open;
            }
        }

        if (getOpenProgress() > 0.01f && mouseY >= y + height && mouseY < y + height + visibleBodyHeight) {
            for (Button b : buttons)
                b.mouseClicked(mouseX, mouseY, button);
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }

        for (Button b : buttons)
            b.mouseReleased(mouseX, mouseY, button);
    }

    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (x <= mouseX && x + width > mouseX) {
            boolean whitelistHandling = false;
            for (Button b : buttons) {
                if (b instanceof ModuleButton moduleButton && moduleButton.isOpen()) {
                    List<Button> wbButtons = moduleButton.getButtons().stream()
                            .filter(button -> button instanceof WhitelistButton).toList();
                    for (Button whitelistButton : wbButtons) {
                        if (whitelistButton instanceof WhitelistButton wb) {
                            if (wb.isHandlingScroll(mouseX, mouseY)) {
                                wb.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
                                whitelistHandling = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (!whitelistHandling) {
                if (open && mouseY >= y + height && mouseY <= y + height + visibleBodyHeight) {
                    int speed = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class).scrollSpeed.getValue()
                            .intValue();
                    if (verticalAmount < 0) {
                        rawTargetScrollOffset -= speed;
                    } else if (verticalAmount > 0) {
                        rawTargetScrollOffset += speed;
                    }
                    lastScrollInputMs = System.currentTimeMillis();
                }
            }
        }
    }

    public void mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (Button b : buttons) {
            b.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
    }

    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getOpenProgress() > 0.01f) {
            for (Button button : buttons)
                button.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    public void charTyped(char chr, int modifiers) {
        if (getOpenProgress() > 0.01f) {
            for (Button button : buttons)
                button.charTyped(chr, modifiers);
        }
    }

    public boolean isHovering(double mouseX, double mouseY) {
        return x <= mouseX && y <= mouseY && x + width > mouseX && y + height > mouseY;
    }
}
