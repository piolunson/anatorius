package dev.project.anatorius.gui.api;

import dev.project.anatorius.Anatorius;
import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.gui.ClickGuiScreen;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import dev.project.anatorius.utils.graphics.Renderer2D;
import dev.project.anatorius.utils.text.FormattingUtils;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;
import java.util.List;

@Getter @Setter
public class DescriptionFrame {
    private String description = "";
    private int x, y, width, height, dragX = 0, dragY = 0, textPadding = 3;
    private boolean dragging = false;

    public DescriptionFrame(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if(dragging) {
            setX(mouseX - dragX);
            setY(mouseY - dragY);
        }

        ClickGuiModule clickGui = Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class);
        Color headerBase = ClickGuiScreen.getButtonColor(y, 140);
        if (clickGui.gradient.getValue()) {
            Renderer2D.renderSidewaysGradient(context, x, y, x + width, y + height, ClickGuiScreen.darken(headerBase, 0.85f), headerBase);
        } else {
            Renderer2D.renderQuad(context, x, y, x + width, y + height, ClickGuiScreen.getButtonColor(y, 120));
        }

        Renderer2D.renderOutline(context, x, y, x + width, y + height, new Color(0, 0, 0, 90));
        Anatorius.FONT_MANAGER.drawTextWithShadow(context, "Description", x + textPadding, y + 2, Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class).textColor.getColor());
        if(!description.isEmpty()) {
            List<String> wrappedText =FormattingUtils.wrapText(description, width - textPadding*2);

            Color bg = clickGui.background.getColor();
            float bodyBottom = y + height + (wrappedText.size() * Anatorius.FONT_MANAGER.getHeight()) + 4;
            if (clickGui.gradient.getValue()) {
                Renderer2D.renderGradient(context, x, y + height, x + width, bodyBottom, bg, ClickGuiScreen.darken(bg, 0.75f));
            } else {
                Renderer2D.renderQuad(context, x, y + height, x + width, bodyBottom, bg);
            }

            Renderer2D.renderOutline(context, x, y + height, x + width, bodyBottom, new Color(0, 0, 0, 70));
            int i = 0;
            for(String s : wrappedText) {
                Anatorius.FONT_MANAGER.drawTextWithShadow(context, s, x + textPadding, y + height + 2 + (Anatorius.FONT_MANAGER.getHeight()*i), Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class).textColor.getColor());
                i++;
            }
        }
    }

    public void mouseClicked(double mouseX, double mouseY, int button) {
        if(isHovering(mouseX, mouseY)) {
            if(button == 0) {
                dragging = true;
                dragX = (int) (mouseX - getX());
                dragY = (int) (mouseY - getY());
            }
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
    }

    public boolean isHovering(double mouseX, double mouseY) {
        return x <= mouseX && y <= mouseY && x + width > mouseX && y + height > mouseY;
    }
}
