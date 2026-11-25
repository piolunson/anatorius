package dev.project.anatorius.gui.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.gui.ClickGuiScreen;
import dev.project.anatorius.gui.WhitelistScreen;
import dev.project.anatorius.gui.api.Button;
import dev.project.anatorius.gui.api.Frame;
import dev.project.anatorius.gui.style.GuiStyle;
import dev.project.anatorius.settings.impl.WhitelistSetting;
import dev.project.anatorius.utils.graphics.Renderer2D;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;

public class WhitelistButton extends Button {
    private final WhitelistSetting setting;

    public WhitelistButton(WhitelistSetting setting, Frame parent, int height) {
        super(setting, parent, height, setting.getDescription());
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        String value;
        if (setting.getType() == WhitelistSetting.Type.SOUNDS) {
            value = "›";
        } else {
            value = setting.getWhitelist().size() + " ›";
        }
        GuiStyle style = GuiStyle.current();
        if (style.themed()) {
            int rowH = getParent().getHeight();
            boolean hovered = StyledRows.hovered(this, style, rowH, mouseX, mouseY);
            StyledRows.hoverOverlay(context, this, style, rowH, hovered);
            StyledRows.text(context, fitLabel(setting.getTag(), StyledRows.textX(this, style), StyledRows.valueRight(this, style) - Anatorius.FONT_MANAGER.getWidth(value)), StyledRows.textX(this, style), getY(), rowH, StyledRows.label(style));
            StyledRows.text(context, value, StyledRows.valueRight(this, style) - Anatorius.FONT_MANAGER.getWidth(value),
                    getY(), rowH, hovered ? Color.WHITE : StyledRows.GREY);
            return;
        }

        boolean hovered = isHovering(mouseX, mouseY);
        Renderer2D.renderQuad(context, getX() + getPadding() + 1, getY(), getX() + getWidth() - getPadding() - 1, getY() + getHeight() - 1, ClickGuiScreen.getButtonColor(getY(), hovered ? 130 : 100));
        Renderer2D.renderQuad(context, getX() + getPadding() + 1, getY(), getX() + getPadding() + 2, getY() + getHeight() - 1, ClickGuiScreen.getButtonColor(getY(), 30));
        Anatorius.FONT_MANAGER.drawTextWithShadow(context, fitLabel(setting.getTag(), getX() + getTextPadding() + 1, getX() + getWidth() - getTextPadding() - 1 - Anatorius.FONT_MANAGER.getWidth(value)), getX() + getTextPadding() + 1, getY() + 2, Color.WHITE);
        Anatorius.FONT_MANAGER.drawTextWithShadow(context, value, getX() + getWidth() - getTextPadding() - 1 - Anatorius.FONT_MANAGER.getWidth(value), getY() + 2, hovered ? Color.WHITE : Color.GRAY);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovering(mouseX, mouseY) && (button == 0 || button == 1)) {
            playClickSound();
            mc.setScreen(new WhitelistScreen(setting, mc.currentScreen));
        }
    }

    @Override
    public int getHeight() {
        return getParent().getHeight();
    }

    @Override
    public boolean isHovering(double mouseX, double mouseY) {
        return getX() + getPadding() <= mouseX && getY() <= mouseY && getX() + getWidth() - getPadding() > mouseX && getY() + getParent().getHeight() > mouseY;
    }

    public boolean isHandlingScroll(double mouseX, double mouseY) {
        return false;
    }
}
