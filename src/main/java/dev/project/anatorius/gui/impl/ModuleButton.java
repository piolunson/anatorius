package dev.project.anatorius.gui.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.settings.impl.*;
import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.gui.ClickGuiScreen;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.gui.api.Button;
import dev.project.anatorius.gui.api.Frame;
import dev.project.anatorius.gui.style.GuiStyle;
import dev.project.anatorius.settings.Setting;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import dev.project.anatorius.utils.animations.Animation;
import dev.project.anatorius.utils.animations.Easing;
import dev.project.anatorius.utils.graphics.Renderer2D;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.ArrayList;

@Getter
@Setter
public class ModuleButton extends Button {
    private final Module module;
    private boolean open = false;
    private final ArrayList<Button> buttons = new ArrayList<>();
    private static final ArrayList<ModuleButton> openedModules = new ArrayList<>();

    private final Animation openAnimation = new Animation(280, Easing.Method.EASE_OUT_CUBIC);

    public ModuleButton(Module module, Frame parent, int height) {
        super(parent, height, module.getDescription());
        this.module = module;

        for (Setting setting : module.getClickGuiSettings()) {
            if (setting instanceof BooleanSetting s) {
                buttons.add(new BooleanButton(s, parent, height));
            } else if (setting instanceof NumberSetting s) {
                buttons.add(new NumberButton(s, parent, height));
            } else if (setting instanceof CategorySetting s) {
                buttons.add(new CategoryButton(s, parent, height));
            } else if (setting instanceof BindSetting s) {
                buttons.add(new BindButton(s, parent, height));
            } else if (setting instanceof ModeSetting s) {
                buttons.add(new ModeButton(s, parent, height));
            } else if (setting instanceof WhitelistSetting s) {
                buttons.add(new WhitelistButton(s, parent, height));
            } else if (setting instanceof StringSetting s) {
                buttons.add(new StringButton(s, parent, height));
            } else if (setting instanceof ColorSetting s) {
                buttons.add(new ColorButton(s, parent, height));
            } else if (setting instanceof ButtonSetting s) {
                buttons.add(new ButtonSettingButton(s, parent, height));
            }
        }
    }

    public float getOpenProgress() {
        return openAnimation.get(open ? 1.0f : 0.0f);
    }

    public int getExpandedButtonsHeight() {
        int h = 0;
        updateButtonVisibility();
        for (Button b : buttons) {
            if (!b.isVisible())
                continue;
            h += b.getHeight();
        }
        return h;
    }

    public int getAnimatedButtonsHeight() {
        int h = 0;
        updateButtonVisibility();
        for (Button b : buttons) {
            h += getAnimatedButtonHeight(b);
        }
        return h;
    }

    public int getAnimatedButtonHeight(Button button) {
        if (!button.isVisible())
            return 0;

        float progress = getOpenProgress();
        if (button.getSetting() != null && button.getSetting().getVisibility() != null) {
            progress *= button.getSetting().getVisibility().getProgress();
        }

        if (progress <= 0.001f)
            return 0;

        return Math.max(1, (int) Math.ceil(button.getHeight() * progress));
    }

    public void updateButtonVisibility() {
        for (Button b : buttons) {
            if (b.getSetting() == null || b.getSetting().getVisibility() == null) {
                b.setVisible(true);
                continue;
            }

            b.getSetting().getVisibility().update();
            b.setVisible(b.getSetting().getVisibility().isVisible() || b.getSetting().getVisibility().getProgress() > 0.001f);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiStyle style = GuiStyle.current();
        if (style.themed()) {
            renderStyledRow(context, mouseX, mouseY, style);
        } else {
            ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);

            Color base = ClickGuiScreen.getModuleButtonColor(getY(), module.isToggled());
            if (clickGui.gradient.getValue()) {
                Renderer2D.renderSidewaysGradient(context, getX() + getPadding(), getY(),
                        getX() + getWidth() - getPadding(), getY() + getHeight(), ClickGuiScreen.darken(base, 0.85f),
                        base);
            } else {
                Renderer2D.renderQuad(context, getX() + getPadding(), getY(),
                        getX() + getWidth() - getPadding(), getY() + getHeight(), base);
            }

            Anatorius.FONT_MANAGER.drawTextWithShadow(context, module.getName(), getX() + getTextPadding(), getY() + 2,
                    module.isToggled() ? clickGui.enabledTextColor.getColor() : clickGui.textColor.getColor());
        }

        if (getOpenProgress() > 0.01f) {
            updateButtonVisibility();
            for (Button button : buttons) {
                if (!button.isVisible())
                    continue;

                int animatedHeight = getAnimatedButtonHeight(button);
                if (animatedHeight <= 0)
                    continue;

                context.enableScissor(button.getX(), button.getY(), button.getX() + button.getWidth(), button.getY() + animatedHeight);
                button.render(context, mouseX, mouseY, delta);
                context.disableScissor();
            }
            if (style == GuiStyle.MINIMAL) {
                int guideTop = getY() + getHeight() + 2, guideBottom = getY() + getHeight() + getAnimatedButtonsHeight() - 2;
                if (guideBottom > guideTop)
                    Renderer2D.renderRoundedQuad(context, getX() + 9, guideTop, getX() + 10, guideBottom, 0.5f, style.accentAlpha(Math.round(110 * getOpenProgress())));
            }
        }
    }

    private void renderStyledRow(DrawContext context, int mouseX, int mouseY, GuiStyle style) {
        boolean enabled = module.isToggled();
        boolean hovered = isHovering(mouseX, mouseY);
        int left = getX(), right = getX() + getWidth(), top = getY(), bottom = getY() + getHeight();

        if (style == GuiStyle.MINIMAL) {
            renderMinimalRow(context, enabled, hovered, left, top, right, bottom, style);
            return;
        }

        if (style == GuiStyle.EARTHHACK) {
            if (enabled) {
                Color accent = hovered ? style.accentHover() : style.accent();
                Renderer2D.renderQuad(context, left, top, right, bottom, new Color(accent.getRed(), accent.getGreen(), accent.getBlue()));
            } else if (hovered) {
                Renderer2D.renderQuad(context, left + 1, top, right - 1, bottom, style.hover());
            }
            if (!buttons.isEmpty()) {
                String sign = open ? "-" : "+";
                Anatorius.FONT_MANAGER.drawTextWithShadow(context, sign, right - 4 - Anatorius.FONT_MANAGER.getWidth(sign), GuiStyle.textY(top, getHeight()), style.text(enabled));
            }
        } else {
            if (enabled) {
                Color accent = style.accent();
                Color fade = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.round(accent.getAlpha() * 0.7f));
                Renderer2D.renderSidewaysGradient(context, left + 2, top, right - 2, bottom - 1, accent, fade);
            }
            if (hovered) Renderer2D.renderQuad(context, left + 2, top, right - 2, bottom - 1, style.hover());
        }

        Anatorius.FONT_MANAGER.drawTextWithShadow(context, module.getName(), left + style.textInset(), GuiStyle.textY(top, getHeight()), style.text(enabled));
    }

    private void renderMinimalRow(DrawContext context, boolean enabled, boolean hovered, int left, int top, int right, int bottom, GuiStyle style) {
        int pillLeft = left + 4, pillRight = right - 4, pillTop = top + 1, pillBottom = bottom - 1;
        if (enabled) {
            Renderer2D.renderRoundedQuad(context, pillLeft, pillTop, pillRight, pillBottom, 3.5f, style.accentAlpha(hovered ? 62 : 44));
            Renderer2D.renderRoundedQuad(context, pillLeft + 2, top + 4, pillLeft + 4, bottom - 4, 1.0f, style.accentAlpha(255));
        } else if (hovered) {
            Renderer2D.renderRoundedQuad(context, pillLeft, pillTop, pillRight, pillBottom, 3.5f, style.hover());
        }

        int textY = GuiStyle.textY(top, getHeight());
        Anatorius.FONT_MANAGER.drawTextWithShadow(context, module.getName(), left + style.textInset(), textY, style.text(enabled));

        if (!buttons.isEmpty()) {
            float cy = top + getHeight() / 2.0f, cx = right - 10;
            Color dot = open ? style.accentAlpha(255) : new Color(255, 255, 255, enabled ? 120 : 60);
            Renderer2D.renderRoundedQuad(context, cx - 1.5f, cy - 1.5f, cx + 1.5f, cy + 1.5f, 1.5f, dot);
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovering(mouseX, mouseY)) {
            if (button == 0) {
                module.setToggled(!module.isToggled());
                playClickSound();
            } else if (button == 1) {
                open = !open;
                if (open) {
                    openedModules.add(this);
                    if (openedModules.size() > 3) {
                        while (openedModules.size() > 1) {
                            ModuleButton btn = openedModules.remove(0);
                            btn.setOpen(false);
                        }
                    }
                } else {
                    openedModules.remove(this);
                }
                playClickSound();
            }
        }

        if (getOpenProgress() > 0.01f) {
            updateButtonVisibility();
            for (Button b : buttons) {
                if (!b.isVisible())
                    continue;
                b.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        for (Button b : buttons) {
            b.mouseReleased(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (Button b : buttons) {
            b.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (getOpenProgress() > 0.01f) {
            updateButtonVisibility();
            for (Button b : buttons) {
                if (!b.isVisible())
                    continue;
                b.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        if (getOpenProgress() > 0.01f) {
            updateButtonVisibility();
            for (Button b : buttons) {
                if (!b.isVisible())
                    continue;
                b.charTyped(chr, modifiers);
            }
        }
    }
}
