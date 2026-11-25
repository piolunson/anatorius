package dev.project.anatorius.gui.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.gui.api.Button;
import dev.project.anatorius.gui.api.Frame;
import dev.project.anatorius.gui.style.GuiStyle;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import dev.project.anatorius.settings.impl.BindSetting;
import dev.project.anatorius.utils.input.KeyboardUtils;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public class BindButton extends Button {
    private final BindSetting setting;
    private boolean listening = false;

    public BindButton(BindSetting setting, Frame parent, int height) {
        super(setting, parent, height, setting.getDescription());
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiStyle s = GuiStyle.current();
        if (s.themed()) {
            boolean hovered = StyledRows.hovered(this, s, getHeight(), mouseX, mouseY);
            StyledRows.box(context, this, s, getHeight(), hovered);
            String key = listening ? "..." : KeyboardUtils.getKeyName(setting.getValue());
            StyledRows.labelValue(context, this, s, getHeight(), StyledRows.boxTextX(this, s), setting.getTag() + ": ", key);
            return;
        }

        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);

        int y = getY() + 2;

        String bind = listening ? "..." : KeyboardUtils.getKeyName(setting.getValue());
        String modeName = setting.getMode().getName();

        int pulseAlpha = listening ? (int)(Math.sin(System.currentTimeMillis() / 200.0) * 50 + 205) : 255;

        String modeText = "[" + modeName + "]";
        int modeWidth = Anatorius.FONT_MANAGER.getWidth(modeText);
        int bindWidth = Anatorius.FONT_MANAGER.getWidth(bind);
        int spaceBetween = 12;
        int rightTotalWidth = bindWidth + spaceBetween + modeWidth;

        int rightX = getX() + getWidth() - getTextPadding() - 2;

        Color modeColor = new Color(150, 150, 150, clickGui.textColor.getColor().getAlpha());
        Anatorius.FONT_MANAGER.drawTextWithShadow(context, modeText, rightX - modeWidth, y, modeColor);

        int bindX = rightX - modeWidth - spaceBetween;

        if (listening) {
            Color pulseColor = new Color(130, 202, 255, pulseAlpha);
            Anatorius.FONT_MANAGER.drawTextWithShadow(context, bind, bindX - bindWidth, y, pulseColor);
        } else {
            Color bindColor = new Color(190, 190, 190, clickGui.textColor.getColor().getAlpha());
            Anatorius.FONT_MANAGER.drawTextWithShadow(context, bind, bindX - bindWidth, y, bindColor);
        }

        int leftPadding = getTextPadding() + 1;
        int maxLeftWidth = (bindX - bindWidth) - (getX() + leftPadding) - 20;

        String settingName = setting.getTag();

        if (maxLeftWidth > 30) {
            int textWidth = Anatorius.FONT_MANAGER.getWidth(settingName);

            if (textWidth > maxLeftWidth) {
                String ellipsis = "...";

                while (settingName.length() > 3 && Anatorius.FONT_MANAGER.getWidth(settingName + ellipsis) > maxLeftWidth) {
                    settingName = settingName.substring(0, settingName.length() - 1);
                }

                if (settingName.length() > 3) {
                    settingName = settingName + ellipsis;
                } else {
                    settingName = setting.getTag().substring(0, Math.min(3, setting.getTag().length())) + "...";
                }
            }

            Anatorius.FONT_MANAGER.drawTextWithShadow(context, settingName, getX() + leftPadding, y, clickGui.textColor.getColor());
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if(isHovering(mouseX, mouseY)) {
            if(button == 0) {
                listening = true;
                playClickSound();
            } else if (button == 1) {
                setting.nextMode();
                playClickSound();
            }

            if(listening) {
                if (button == 1 || button == 2 || button == 3 || button == 4) {
                    setting.setValue(-button - 1);
                    listening = false;
                }
            }
        }
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        if(listening) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) {
                setting.setValue(0);
            } else {
                setting.setValue(keyCode);
            }
            listening = false;
        }
    }
}
