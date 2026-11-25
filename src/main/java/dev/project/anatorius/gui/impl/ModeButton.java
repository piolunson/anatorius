package dev.project.anatorius.gui.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.gui.ClickGuiScreen;
import dev.project.anatorius.gui.api.Button;
import dev.project.anatorius.gui.api.Frame;
import dev.project.anatorius.gui.style.GuiStyle;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.utils.graphics.Renderer2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.awt.*;

public class ModeButton extends Button {
    private final ModeSetting setting;
    private boolean open = false;

    public ModeButton(ModeSetting setting, Frame parent, int height) {
        super(setting, parent, height, setting.getDescription());
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiStyle st = GuiStyle.current();
        if (st.themed()) {
            int rowH = getParent().getHeight();
            StyledRows.hoverOverlay(context, this, st, rowH, StyledRows.hovered(this, st, rowH, mouseX, mouseY));
            if (st == GuiStyle.MINIMAL) {
                int valueX = StyledRows.valueRight(this, st) - Anatorius.FONT_MANAGER.getWidth(setting.getValue());
                StyledRows.text(context, fitLabel(setting.getTag(), StyledRows.textX(this, st), valueX), StyledRows.textX(this, st), getY(), rowH, StyledRows.label(st));
                String value = setting.getValue();
                StyledRows.text(context, value, valueX, getY(), rowH, st.accentAlpha(255));
                if (open) {
                    int i = 0;
                    for (String mode : setting.getModes()) {
                        boolean active = setting.getValue().equals(mode);
                        StyledRows.text(context, mode, StyledRows.textX(this, st) + 4, getY() + rowH + i, rowH,
                                active ? st.accentAlpha(255) : GuiStyle.MINIMAL_MUTED);
                        i += rowH;
                    }
                }
                return;
            }
            StyledRows.labelValue(context, this, st, rowH, setting.getTag() + ": ", setting.getValue());
            if (open) {
                int i = 0;
                for (String mode : setting.getModes()) {
                    StyledRows.text(context, mode, StyledRows.textX(this, st) + 1, getY() + rowH + i, rowH,
                            st.text(setting.getValue().equals(mode)));
                    i += rowH;
                }
            }
            return;
        }

        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);

        Renderer2D.renderQuad(context, getX() + getPadding() + 1, getY(),
                getX() + getWidth() - getPadding() - 1, getY() + getParent().getHeight() - 1,
                ClickGuiScreen.getModuleButtonColor(getY(), 100, true));
        Renderer2D.renderQuad(context, getX() + getPadding() + 1, getY(), getX() + getPadding() + 2,
                getY() + getHeight() - 1, ClickGuiScreen.getModuleButtonColor(getY(), 255, true));

        int valueX = getX() + getWidth() - getTextPadding() - 1 - Anatorius.FONT_MANAGER.getWidth(setting.getValue());
        Anatorius.FONT_MANAGER.drawTextWithShadow(context, fitLabel(setting.getTag(), getX() + getTextPadding() + 1, valueX), getX() + getTextPadding() + 1, getY() + 2,
                clickGui.textColor.getColor());

        Color grayColor = new Color(170, 170, 170, clickGui.textColor.getColor().getAlpha());
        Anatorius.FONT_MANAGER.drawTextWithShadow(context, setting.getValue(),
                valueX, getY() + 2, grayColor);

        if (open) {
            int i = 0;
            for (String s : setting.getModes()) {
                Color textColor = setting.getValue().equals(s) ? clickGui.textColor.getColor() : grayColor;
                Anatorius.FONT_MANAGER.drawTextWithShadow(context, s, getX() + getTextPadding() + 2,
                        getY() + getParent().getHeight() + i + 2, textColor);
                i += getParent().getHeight();
            }
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovering(mouseX, mouseY)) {
            if (button == 0) {
                int choice = setting.getModes().indexOf(setting.getValue());
                choice++;
                if (choice > setting.getModes().size() - 1)
                    choice = 0;

                setting.setValue(setting.getModes().get(choice));
                playClickSound();
            } else if (button == 1) {
                open = !open;
            }
        }

        if (open && isHoveringModes(mouseX, mouseY)) {
            int choice = MathHelper.clamp((int) (mouseY - getY() - getParent().getHeight()) / getParent().getHeight(),
                    0, setting.getModes().size() - 1);
            setting.setValue(setting.getModes().get(choice));
        }
    }

    @Override
    public int getHeight() {
        return getParent().getHeight() + (open ? getParent().getHeight() * setting.getModes().size() : 0);
    }

    @Override
    public boolean isHovering(double mouseX, double mouseY) {
        return getX() + getPadding() <= mouseX && getY() <= mouseY && getX() + getWidth() - getPadding() > mouseX
                && getY() + getParent().getHeight() > mouseY;
    }

    public boolean isHoveringModes(double mouseX, double mouseY) {
        int modesHeight = getParent().getHeight() * setting.getModes().size();
        return getX() + getPadding() <= mouseX && getY() + getParent().getHeight() <= mouseY
                && getX() + getWidth() - getPadding() > mouseX
                && getY() + getParent().getHeight() + modesHeight > mouseY;
    }
}
