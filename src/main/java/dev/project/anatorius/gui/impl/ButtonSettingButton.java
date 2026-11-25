package dev.project.anatorius.gui.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.gui.ClickGuiScreen;
import dev.project.anatorius.gui.api.Button;
import dev.project.anatorius.gui.api.Frame;
import dev.project.anatorius.gui.style.GuiStyle;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import dev.project.anatorius.settings.impl.ButtonSetting;
import dev.project.anatorius.utils.graphics.Renderer2D;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;

public class ButtonSettingButton extends Button {
    private final ButtonSetting setting;
    private boolean hovered;

    public ButtonSettingButton(ButtonSetting setting, Frame parent, int height) {
        super(setting, parent, height, setting.getDescription());
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiStyle s = GuiStyle.current();
        if (s.themed()) {
            hovered = isHovering(mouseX, mouseY);
            StyledRows.hoverOverlay(context, this, s, getHeight(), StyledRows.hovered(this, s, getHeight(), mouseX, mouseY));
            int centre = (StyledRows.left(this, s) + StyledRows.right(this, s)) / 2;
            StyledRows.text(context, setting.getTag(), centre - Anatorius.FONT_MANAGER.getWidth(setting.getTag()) / 2,
                    getY(), getHeight(), Color.WHITE);
            return;
        }

        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);
        hovered = isHovering(mouseX, mouseY);

        Color base = ClickGuiScreen.getModuleButtonColor(getY(), hovered ? 150 : 90, false);
        Renderer2D.renderQuad(context, getX() + getPadding() + 1, getY(),
                getX() + getWidth() - getPadding() - 1, getY() + getHeight() - 1, base);

        Color accent = ClickGuiScreen.getButtonColor(getY(), 255);
        Renderer2D.renderQuad(context, getX() + getPadding() + 1, getY(),
                getX() + getPadding() + 2.5f, getY() + getHeight() - 1, accent);

        Anatorius.FONT_MANAGER.drawTextWithShadow(context, setting.getTag(), getX() + getTextPadding() + 1, getY() + 2,
                clickGui.textColor.getColor());
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovering(mouseX, mouseY) && button == 0) {
            playClickSound();
            setting.press();
        }
    }
}
