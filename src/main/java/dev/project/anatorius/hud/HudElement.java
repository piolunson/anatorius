package dev.project.anatorius.hud;

import dev.project.anatorius.settings.Setting;
import dev.project.anatorius.settings.impl.NumberSetting;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

@Getter
public class HudElement {
    public static final double AUTO = -99999.0;

    private final String id;
    private final String name;
    private final NumberSetting x;
    private final NumberSetting y;
    private final BooleanSupplier enabled;
    private final List<Setting> settings = new ArrayList<>();

    private float renderX, renderY;
    private int width, height;
    private boolean renderedThisFrame;

    public HudElement(String id, String name, BooleanSupplier enabled) {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
        this.x = new NumberSetting(id + "_X", "The X position of the " + name + " element.", AUTO, -100000.0, 100000.0);
        this.y = new NumberSetting(id + "_Y", "The Y position of the " + name + " element.", AUTO, -100000.0, 100000.0);
    }

    public HudElement with(Setting... editorSettings) {
        for (Setting setting : editorSettings) if (setting != null) settings.add(setting);
        return this;
    }

    public boolean isEnabled() {
        return enabled.getAsBoolean();
    }

    public boolean isCustomized() {
        return x.getValue().doubleValue() != AUTO && y.getValue().doubleValue() != AUTO;
    }

    public float resolveX(float defaultX) {
        if (!isCustomized()) return defaultX;
        return clampX((float) x.getValue().doubleValue());
    }

    public float resolveY(float defaultY) {
        if (!isCustomized()) return defaultY;
        return clampY((float) y.getValue().doubleValue());
    }

    public void updateBounds(float renderX, float renderY, int width, int height) {
        this.renderX = renderX;
        this.renderY = renderY;
        this.width = width;
        this.height = height;
        this.renderedThisFrame = true;
    }

    public void clearFrame() {
        this.renderedThisFrame = false;
    }

    public void setPosition(double nx, double ny) {
        x.setValue(clampX((float) nx));
        y.setValue(clampY((float) ny));
    }

    public void resetPosition() {
        x.setValue(AUTO);
        y.setValue(AUTO);
    }

    private float clampX(float value) {
        int screen = MinecraftClient.getInstance().getWindow().getScaledWidth();
        int w = Math.max(0, width);
        if (w >= screen) return 0;
        return Math.max(0, Math.min(value, screen - w));
    }

    private float clampY(float value) {
        int screen = MinecraftClient.getInstance().getWindow().getScaledHeight();
        int h = Math.max(0, height);
        if (h >= screen) return 0;
        return Math.max(0, Math.min(value, screen - h));
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= renderX && mouseX <= renderX + width && mouseY >= renderY && mouseY <= renderY + height;
    }

    public float left()    { return renderX; }
    public float right()   { return renderX + width; }
    public float centerX() { return renderX + width / 2f; }
    public float top()     { return renderY; }
    public float bottom()  { return renderY + height; }
    public float centerY() { return renderY + height / 2f; }
}
