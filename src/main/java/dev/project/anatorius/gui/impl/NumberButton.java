package dev.project.anatorius.gui.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.gui.ClickGuiScreen;
import dev.project.anatorius.gui.api.Button;
import dev.project.anatorius.gui.api.Frame;
import dev.project.anatorius.gui.style.GuiStyle;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.chat.ChatUtils;
import dev.project.anatorius.utils.graphics.Renderer2D;
import dev.project.anatorius.utils.system.MathUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public class NumberButton extends Button {
    private final NumberSetting setting;
    private boolean dragging = false;
    private String currentString = "";
    private boolean listening = false, selecting = false;

    public NumberButton(NumberSetting setting, Frame parent, int height) {
        super(setting, parent, height, setting.getDescription());
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiStyle s = GuiStyle.current();
        double slider, sliderMax = getWidth() - 2 - getPadding() * 2;
        double drag = Math.min(sliderMax, Math.max(0, mouseX - getX() - 1 - getPadding()));
        if (s.themed()) {
            sliderMax = StyledRows.sliderRight(this, s) - StyledRows.sliderLeft(this, s);
            drag = Math.min(sliderMax, Math.max(0, mouseX - StyledRows.sliderLeft(this, s)));
        }

        if (setting.getType() == NumberSetting.Type.INTEGER) {
            slider = sliderMax * (setting.getValue().intValue() - setting.getMinimum().intValue())
                    / (setting.getMaximum().intValue() - setting.getMinimum().intValue());
            if (dragging) {
                int value = (int) MathUtils
                        .round(drag / sliderMax * (setting.getMaximum().intValue() - setting.getMinimum().intValue())
                                + setting.getMinimum().intValue(), 0);
                setting.setValue(value);
            }
        } else if (setting.getType() == NumberSetting.Type.FLOAT) {
            slider = sliderMax * (setting.getValue().floatValue() - setting.getMinimum().floatValue())
                    / (setting.getMaximum().floatValue() - setting.getMinimum().floatValue());
            if (dragging) {
                float value = (float) MathUtils.round(
                        drag / sliderMax * (setting.getMaximum().floatValue() - setting.getMinimum().floatValue())
                                + setting.getMinimum().floatValue(),
                        2);
                setting.setValue(value);
            }
        } else if (setting.getType() == NumberSetting.Type.DOUBLE) {
            slider = sliderMax * (setting.getValue().doubleValue() - setting.getMinimum().doubleValue())
                    / (setting.getMaximum().doubleValue() - setting.getMinimum().doubleValue());
            if (dragging) {
                double value = MathUtils.round(
                        drag / sliderMax * (setting.getMaximum().doubleValue() - setting.getMinimum().doubleValue())
                                + setting.getMinimum().doubleValue(),
                        2);
                setting.setValue(value);
            }
        } else {
            slider = sliderMax * (setting.getValue().longValue() - setting.getMinimum().longValue())
                    / (setting.getMaximum().longValue() - setting.getMinimum().longValue());
            if (dragging) {
                long value = (long) MathUtils
                        .round(drag / sliderMax * (setting.getMaximum().longValue() - setting.getMinimum().longValue())
                                + setting.getMinimum().longValue(), 0);
                setting.setValue(value);
            }
        }

        if (s == GuiStyle.MINIMAL) {
            boolean hovered = StyledRows.hovered(this, s, getHeight(), mouseX, mouseY) || dragging;
            int textRowY = getY() - 2;
            if (listening) {
                StyledRows.text(context, currentString + (selecting ? "" : Anatorius.CLICK_GUI.isShowLine() ? "|" : ""),
                        StyledRows.textX(this, s), textRowY, getHeight(), Color.WHITE);
            } else {
                int valueX = StyledRows.valueRight(this, s) - Anatorius.FONT_MANAGER.getWidth(setting.getDisplayValue());
                StyledRows.text(context, fitLabel(setting.getTag(), StyledRows.textX(this, s), valueX), StyledRows.textX(this, s), textRowY, getHeight(), StyledRows.label(s));
                String value = setting.getDisplayValue();
                StyledRows.text(context, value, valueX, textRowY, getHeight(), Color.WHITE);
            }
            StyledRows.slider(context, this, s, getHeight(), slider, hovered);
            return;
        }

        if (s.themed()) {
            boolean hovered = StyledRows.hovered(this, s, getHeight(), mouseX, mouseY);
            if (listening) {
                StyledRows.text(context, currentString + (selecting ? "" : Anatorius.CLICK_GUI.isShowLine() ? "|" : ""),
                        StyledRows.textX(this, s), getY(), getHeight(), Color.WHITE);
            } else {
                StyledRows.labelValue(context, this, s, getHeight(), setting.getTag() + " ", setting.getDisplayValue());
            }
            int x1 = StyledRows.left(this, s) + 5, x2 = StyledRows.right(this, s) - 5;
            int bottom = getY() + getHeight();
            Renderer2D.renderOutline(context, x1 - 1, bottom - 4, x2 + 1, bottom, StyledRows.outline(s));
            Renderer2D.renderQuad(context, x1, bottom - 3, x1 + (float) slider, bottom - 1,
                    hovered ? s.accentHover() : s.accent());
            return;
        }

        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);

        Color sliderBase = ClickGuiScreen.getModuleButtonColor(getY(), 100, true);
        if (clickGui.gradient.getValue()) {
            Renderer2D.renderSidewaysGradient(context, getX() + getPadding() + 1, getY(),
                    getX() + getPadding() + 1 + (float) slider, getY() + getHeight() - 1,
                    ClickGuiScreen.darken(sliderBase, 0.85f), sliderBase);
        } else {
            Renderer2D.renderQuad(context, getX() + getPadding() + 1, getY(),
                    getX() + getPadding() + 1 + (float) slider, getY() + getHeight() - 1, sliderBase);
        }

        Renderer2D.renderQuad(context, getX() + getPadding() + 1, getY(), getX() + getPadding() + 2,
                getY() + getHeight() - 1, ClickGuiScreen.getModuleButtonColor(getY(), 255, true));

        Anatorius.FONT_MANAGER
                .drawTextWithShadow(context,
                        listening ? (currentString + (selecting ? "" : Anatorius.CLICK_GUI.isShowLine() ? "|" : ""))
                                : fitLabel(setting.getTag(), getX() + getTextPadding() + 1, getX() + getWidth() - getTextPadding() - 1 - Anatorius.FONT_MANAGER.getWidth(setting.getDisplayValue())),
                        getX() + getTextPadding() + 1, getY() + 2, clickGui.textColor.getColor());
        if (!listening) {
            Color grayColor = new Color(170, 170, 170, clickGui.textColor.getColor().getAlpha());
            Anatorius.FONT_MANAGER.drawTextWithShadow(context, setting.getDisplayValue(),
                    getX() + getWidth() - getTextPadding() - 1
                            - Anatorius.FONT_MANAGER.getWidth(setting.getDisplayValue()),
                    getY() + 2, grayColor);
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovering(mouseX, mouseY) && button == 0) {
            dragging = true;
        }

        if (button == 1) {
            if (isHovering(mouseX, mouseY) && !listening) {
                listening = true;
                currentString = "";
            } else {
                listening = false;
                selecting = false;
            }
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (dragging)
                playClickSound();
            dragging = false;
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!listening)
            return;

        if (InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_ESCAPE)) {
            selecting = false;
            return;
        }

        if (InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_ENTER)) {
            try {
                switch (setting.getType()) {
                    case LONG -> setting.setValue(Long.parseLong(currentString));
                    case DOUBLE -> setting.setValue(Double.parseDouble(currentString));
                    case FLOAT -> setting.setValue(Float.parseFloat(currentString));
                    default -> setting.setValue(Integer.parseInt(currentString));
                }
            } catch (NumberFormatException exception) {
                Anatorius.CHAT_MANAGER.warn("Please input a valid " + ChatUtils.getPrimary()
                        + setting.getType().name().toLowerCase() + ChatUtils.getSecondary() + " number.");
            }
            selecting = false;
            listening = false;
            return;
        }

        if (InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_BACKSPACE)) {
            currentString = selecting ? ""
                    : (!currentString.isEmpty() ? currentString.substring(0, currentString.length() - 1)
                            : currentString);
            selecting = false;
            return;
        }

        if (InputUtil.isKeyPressed(mc.getWindow(),
                (net.minecraft.util.Util.getOperatingSystem() == net.minecraft.util.Util.OperatingSystem.OSX) ? GLFW.GLFW_KEY_LEFT_SUPER : GLFW.GLFW_KEY_LEFT_CONTROL)) {
            if (InputUtil.isKeyPressed(mc.getWindow(), GLFW.GLFW_KEY_A)) {
                selecting = true;
            }
        }
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        if (listening) {
            currentString = selecting ? String.valueOf(chr) : currentString + chr;
            if (selecting)
                selecting = false;
        }
    }
}
