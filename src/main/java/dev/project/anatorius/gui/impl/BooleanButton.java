package dev.project.anatorius.gui.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.gui.ClickGuiScreen;
import dev.project.anatorius.gui.api.Button;
import dev.project.anatorius.gui.api.Frame;
import dev.project.anatorius.gui.style.GuiStyle;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.utils.graphics.Renderer2D;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;

public class BooleanButton extends Button {
    private final BooleanSetting setting;

    public BooleanButton(BooleanSetting setting, Frame parent, int height) {
        super(setting, parent, height, setting.getDescription());
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiStyle s = GuiStyle.current();
        if (s.themed()) {
            boolean on = setting.getValue();
            boolean hovered = StyledRows.hovered(this, s, getHeight(), mouseX, mouseY);
            if (on && s == GuiStyle.LAVENDER)
                Renderer2D.renderQuad(context, StyledRows.left(this, s), getY(), StyledRows.right(this, s),
                        getY() + getHeight() - 1, s.accent());
            if (s == GuiStyle.MINIMAL) StyledRows.hoverOverlay(context, this, s, getHeight(), hovered);
            StyledRows.text(context, setting.getTag(), StyledRows.textX(this, s), getY(), getHeight(),
                    s == GuiStyle.MINIMAL ? (on ? Color.WHITE : StyledRows.label(s)) : s.text(on));
            StyledRows.checkbox(context, this, s, getHeight(), on, hovered);
            return;
        }

        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);
        if (setting.getValue())
            Renderer2D.renderQuad(context, getX() + getPadding() + 1, getY(),
                    getX() + getWidth() - getPadding() - 1, getY() + getHeight() - 1,
                    ClickGuiScreen.getModuleButtonColor(getY(), 100, true));
        Anatorius.FONT_MANAGER.drawTextWithShadow(context, setting.getTag(), getX() + getTextPadding() + 1, getY() + 2,
                setting.getValue() ? clickGui.enabledTextColor.getColor() : clickGui.textColor.getColor());
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovering(mouseX, mouseY) && button == 0) {
            setting.setValue(!setting.getValue());
            playClickSound();
        }
    }
}
