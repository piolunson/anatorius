package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.RenderOverlayEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer2D;
import dev.project.anatorius.utils.minecraft.MovementUtils;
import net.minecraft.client.gui.DrawContext;

import java.awt.*;

@RegisterModule(name = "Crosshair", description = "Renders a custom crosshair.", category = Module.Category.VISUALS)
public class CrosshairModule extends Module {
    public NumberSetting width = new NumberSetting("Width", "The width of the crosshair.", 1.5f, 0.0f, 5.0f);
    public NumberSetting height = new NumberSetting("Height", "The height of the crosshair.", 4.0f, 0.0f, 10.0f);
    public NumberSetting gap = new NumberSetting("Gap", "The gap of the crosshair.", 2.0f, 0.0f, 10.0f);
    public BooleanSetting dynamic = new BooleanSetting("Dynamic", "Makes the crosshair dynamic.", false);
    public BooleanSetting outline = new BooleanSetting("Outline", "Whether or not to render an outline for the crosshair.", true);
    public ColorSetting color = new ColorSetting("Color", "The color used for the rendering.", ColorUtils.getDefaultColor());

    @SubscribeEvent
    public void onRenderOverlay(RenderOverlayEvent event) {
        if(getNull()) return;

        DrawContext context = event.getContext();

        float x = mc.getWindow().getScaledWidth()/2f;
        float y = mc.getWindow().getScaledHeight()/2f;

        float w = width.getValue().floatValue()/2f;
        float h = height.getValue().floatValue();
        float g = gap.getValue().floatValue() + (moving() ? 2 : 0);

        Renderer2D.renderQuad(context, x - w, y - h - g, x + w, y - g, color.getColor());
        Renderer2D.renderQuad(context, x + g, y - w, x + h + g, y + w, color.getColor());
        Renderer2D.renderQuad(context, x - w, y + g, x + w, y + h + g, color.getColor());
        Renderer2D.renderQuad(context, x - h - g, y - w, x - g, y + w, color.getColor());

        if (outline.getValue()) {
            Renderer2D.renderOutline(context, x - w, y - h - g, x + w, y - g, Color.BLACK);
            Renderer2D.renderOutline(context, x + g, y - w, x + h + g, y + w, Color.BLACK);
            Renderer2D.renderOutline(context, x - w, y + g, x + w, y + h + g, Color.BLACK);
            Renderer2D.renderOutline(context, x - h - g, y - w, x - g, y + w, Color.BLACK);
        }
    }

    private boolean moving() {
        return (mc.player.isSneaking() || MovementUtils.isMoving() || !mc.player.isOnGround()) && dynamic.getValue();
    }
}
