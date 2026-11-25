package dev.project.anatorius.gui.api;

import dev.project.anatorius.Anatorius;
import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.modules.impl.core.ClickGuiModule;
import dev.project.anatorius.settings.Setting;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

@Getter @Setter
public class Button implements IMinecraft {
    private final Setting setting;
    private final Frame parent;
    private int x, y, height, padding = 0, textPadding = 3;
    private final String description;
    private boolean visible = true;

    public Button(Frame parent, int height, String description) {
        this.setting = null;
        this.parent = parent;
        this.height = height;
        this.description = description;
    }

    public Button(Setting setting, Frame parent, int height, String description) {
        this.setting = setting;
        this.parent = parent;
        this.height = height;
        this.description = description;
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {}
    public void mouseClicked(double mouseX, double mouseY, int button) {}
    public void mouseReleased(double mouseX, double mouseY, int button) {}
    public void keyPressed(int keyCode, int scanCode, int modifiers) {}
    public void mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {}
    public void mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {}
    public void charTyped(char chr, int modifiers) {}

    protected static String fitLabel(String label, int labelX, int valueX) {
        int max = valueX - labelX - 3;
        if (dev.project.anatorius.Anatorius.FONT_MANAGER.getWidth(label) <= max) return label;
        int end = label.length();
        while (end > 1 && dev.project.anatorius.Anatorius.FONT_MANAGER.getWidth(label.substring(0, end) + "..") > max) end--;
        return label.substring(0, end) + "..";
    }

    public int getWidth() {
        return parent.getWidth();
    }

    public boolean isHovering(double mouseX, double mouseY) {
        return x + padding <= mouseX && y <= mouseY && x + getWidth() - padding > mouseX && y + getHeight() > mouseY;
    }

    public void playClickSound() {
        ClickGuiModule.playSound();
    }
}
