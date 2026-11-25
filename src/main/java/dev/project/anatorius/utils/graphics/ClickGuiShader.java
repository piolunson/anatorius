package dev.project.anatorius.utils.graphics;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.awt.Color;

public class ClickGuiShader implements IMinecraft {
    private static ClickGuiShader INSTANCE;

    private static final Identifier VERTEX = Identifier.of("anatorius", "core/clickgui_bg");

    private static final RenderPipeline FLUID = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                    .withLocation("pipeline/anatorius_clickgui_fluid")
                    .withVertexShader(VERTEX)
                    .withFragmentShader(Identifier.of("anatorius", "core/clickgui_fluid"))
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .build());

    private static final RenderPipeline PLASMA = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                    .withLocation("pipeline/anatorius_clickgui_plasma")
                    .withVertexShader(VERTEX)
                    .withFragmentShader(Identifier.of("anatorius", "core/clickgui_plasma"))
                    .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                    .build());

    private ClickGuiShader() {}

    public static ClickGuiShader getInstance() {
        if (INSTANCE == null) INSTANCE = new ClickGuiShader();
        return INSTANCE;
    }

    public void render(DrawContext context, String mode, int x, int y, int width, int height, Color color, float delta) {
        if (width <= 0 || height <= 0) return;
        RenderPipeline pipeline = switch (mode) {
            case "Fluid" -> FLUID;
            case "Colorful Plasma" -> PLASMA;
            default -> null;
        };
        if (pipeline == null) return;
        context.fill(pipeline, x, y, x + width, y + height, color.getRGB());
    }
}
