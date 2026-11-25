package dev.project.anatorius.gui.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.gui.api.Button;
import dev.project.anatorius.gui.api.Frame;
import dev.project.anatorius.gui.style.GuiStyle;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import dev.project.anatorius.settings.impl.CategorySetting;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;

public class CategoryButton extends Button {
    private final CategorySetting setting;

    public CategoryButton(CategorySetting setting, Frame parent, int height) {
        super(setting, parent, height, setting.getDescription());
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiStyle s = GuiStyle.current();
        if (s.themed()) {
            StyledRows.hoverOverlay(context, this, s, getHeight(), StyledRows.hovered(this, s, getHeight(), mouseX, mouseY));
            StyledRows.text(context, setting.getTag(), StyledRows.textX(this, s), getY(), getHeight(), Color.WHITE);
            String sign = setting.isOpen() ? "-" : "+";
            StyledRows.text(context, sign, StyledRows.valueRight(this, s) - Anatorius.FONT_MANAGER.getWidth(sign),
                    getY(), getHeight(), s == GuiStyle.MINIMAL ? GuiStyle.MINIMAL_MUTED : Color.WHITE);
            return;
        }

        Anatorius.FONT_MANAGER.drawTextWithShadow(context, setting.getTag(), getX() + getTextPadding() + 1, getY() + 2,
                Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class).textColor.getColor());

        String marker = setting.isOpen() ? "-" : "+";
        Anatorius.FONT_MANAGER.drawTextWithShadow(context, marker,
                getX() + getWidth() - getTextPadding() - 1
                        - Anatorius.FONT_MANAGER.getWidth(marker),
                getY() + 2, Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class).textColor.getColor());
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovering(mouseX, mouseY) && button == 1) {
            setting.setOpen(!setting.isOpen());
            playClickSound();
        }
    }
}
