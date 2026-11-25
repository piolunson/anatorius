package dev.project.anatorius.managers;

import lombok.Getter;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.render.OutlineVertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider;

import java.awt.*;

@Getter
public class ShaderManager implements IMinecraft {
    private final OutlineVertexConsumerProvider vertexConsumerProvider = new OutlineVertexConsumerProvider();

    public ShaderManager() {
    }

    public void prepare() {
    }

    public void render(int renderMode, float opacity) {
    }

    public void renderThemed(String theme, ShaderThemeParams params) {
    }

    public void resize(int width, int height) {
    }

    public VertexConsumerProvider create(VertexConsumerProvider parent, Color color) {
        return parent;
    }

    @Getter
    public static class ShaderThemeParams {
        public int width = 3;
        public boolean fastLines = true;
        public int shapeMode = 2;
        public float glowMultiplier = 1.5f;
        public Color fillColor = new Color(255, 0, 0, 128);
        public Color outlineColor = new Color(255, 255, 255, 255);

        public int dotsMode = 0;
        public int dotsRadius = 5;
        public float dotsAlpha = 0.5f;

        public Color fillColor2 = new Color(0, 0, 255, 128);
        public Color outlineColor2 = new Color(0, 255, 255, 255);
        public float time = 0.0f;
        public float gradientStep = 1.0f;

        public float fillOffset = 0.5f;
        public float fillStrength = 1.0f;
        public float outlineOffset = 0.5f;
        public float outlineStrength = 1.0f;

        public int glowQuality = 2;

        public ShaderThemeParams width(int v) { this.width = v; return this; }
        public ShaderThemeParams fastLines(boolean v) { this.fastLines = v; return this; }
        public ShaderThemeParams shapeMode(int v) { this.shapeMode = v; return this; }
        public ShaderThemeParams glowMultiplier(float v) { this.glowMultiplier = v; return this; }
        public ShaderThemeParams fillColor(Color v) { this.fillColor = v; return this; }
        public ShaderThemeParams outlineColor(Color v) { this.outlineColor = v; return this; }
        public ShaderThemeParams dotsMode(int v) { this.dotsMode = v; return this; }
        public ShaderThemeParams dotsRadius(int v) { this.dotsRadius = v; return this; }
        public ShaderThemeParams dotsAlpha(float v) { this.dotsAlpha = v; return this; }
        public ShaderThemeParams fillColor2(Color v) { this.fillColor2 = v; return this; }
        public ShaderThemeParams outlineColor2(Color v) { this.outlineColor2 = v; return this; }
        public ShaderThemeParams time(float v) { this.time = v; return this; }
        public ShaderThemeParams gradientStep(float v) { this.gradientStep = v; return this; }
        public ShaderThemeParams fillOffset(float v) { this.fillOffset = v; return this; }
        public ShaderThemeParams fillStrength(float v) { this.fillStrength = v; return this; }
        public ShaderThemeParams outlineOffset(float v) { this.outlineOffset = v; return this; }
        public ShaderThemeParams outlineStrength(float v) { this.outlineStrength = v; return this; }
        public ShaderThemeParams glowQuality(int v) { this.glowQuality = v; return this; }
    }
}
