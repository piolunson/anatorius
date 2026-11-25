package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerPopEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.animations.Easing;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.ModelRenderer;
import dev.project.anatorius.utils.graphics.Renderer3D;
import dev.project.anatorius.utils.minecraft.StaticPlayerEntity;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@RegisterModule(name = "PopChams", description = "Renders a ghost of the player that rises and fades out when they pop a totem.", category = Module.Category.VISUALS)
public class PopChamsModule extends Module {
    private static final float MS_PER_TICK = 50.0f;
    private static final int BLOOM_LAYERS = 5;

    public NumberSetting duration = new NumberSetting("Duration", "How long the ghost takes to fade out (ms).", 1500, 0, 5000);
    public NumberSetting rise = new NumberSetting("Rise", "How far the ghost floats upwards while fading (blocks).", 1.2, 0.0, 4.0);

    public CategorySetting frameCategory = new CategorySetting("Frame", "The category for settings related to how the ghost is drawn.");
    public ModeSetting mode = new ModeSetting("Mode", "The rendering that will be applied to the pop chams.", new CategorySetting.Visibility(frameCategory), "Both", new String[]{"Fill", "Line", "Both"});
    public ColorSetting fillColor = new ColorSetting("FillColor", "The color used for the fill rendering.", new ModeSetting.Visibility(mode, "Fill", "Both"), ColorUtils.paletteFill(ColorUtils.THEME_ACCENT));
    public ColorSetting outlineColor = new ColorSetting("OutlineColor", "The color used for the line rendering.", new ModeSetting.Visibility(mode, "Line", "Both"), ColorUtils.paletteOutline(ColorUtils.THEME_ACCENT));

    public CategorySetting bloomCategory = new CategorySetting("Bloom", "A soft glow around the ghost.");
    public BooleanSetting bloom = new BooleanSetting("Bloom", "Enabled", "Draws a soft glow around the ghost that fades out with it.", new CategorySetting.Visibility(bloomCategory), true);
    public NumberSetting bloomRadius = new NumberSetting("BloomRadius", "Radius", "How far the glow reaches out from the ghost (blocks).", new BooleanSetting.Visibility(bloom, true), 0.18, 0.02, 0.6);
    public NumberSetting bloomStrength = new NumberSetting("BloomStrength", "Strength", "How bright the glow is (%).", new BooleanSetting.Visibility(bloom, true), 55, 1, 100);
    public ColorSetting bloomColor = new ColorSetting("BloomColor", "The glow's color.", new BooleanSetting.Visibility(bloom, true), ColorUtils.palette(ColorUtils.THEME_ACCENT, 200));

    private final List<Pop> pops = new ArrayList<>();
    private int ticks;

    @Override
    public void onDisable() {
        pops.clear();
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        ticks++;
        pops.removeIf(pop -> progress(pop, 0.0f) >= 1.0f);
    }

    @SubscribeEvent
    public void onPlayerPop(PlayerPopEvent event) {
        if (getNull() || event.getPlayer() == mc.player) return;
        var player = event.getPlayer();
        mc.execute(() -> {
            if (getNull() || !isToggled()) return;
            pops.add(new Pop(new StaticPlayerEntity(player), ticks));
            if (pops.size() > 12) pops.removeFirst();
        });
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (getNull() || pops.isEmpty()) return;

        boolean fill = !mode.getValue().equals("Line");
        boolean line = !mode.getValue().equals("Fill");

        for (Pop pop : pops) {
            float t = progress(pop, event.getTickDelta());
            if (t >= 1.0f) continue;

            double lift = rise.getValue().doubleValue() * Easing.ease(t, Easing.Method.EASE_OUT_CUBIC);
            if (!Renderer3D.isFrustumVisible(pop.model.getBoundingBox().offset(0.0, lift, 0.0))) continue;

            float fade = 1.0f - Easing.ease(t, Easing.Method.EASE_IN_QUAD);
            Color fillCol = ColorUtils.getColor(fillColor.getColor(), Math.round(fillColor.getColor().getAlpha() * fade));
            Color lineCol = ColorUtils.getColor(outlineColor.getColor(), Math.round(outlineColor.getColor().getAlpha() * fade));

            ModelRenderer.Bloom glow = null;
            if (bloom.getValue()) {
                Color base = bloomColor.getColor();
                int alpha = Math.round(255 * bloomStrength.getValue().floatValue() / 100.0f * fade);
                glow = new ModelRenderer.Bloom(bloomRadius.getValue().floatValue(), ColorUtils.getColor(base, Math.min(255, alpha)), BLOOM_LAYERS);
            }
            ModelRenderer.renderModel(pop.model, true, 1.0f, event.getTickDelta(), new ModelRenderer.Render(fill, fillCol, line, lineCol, false), lift, glow);
        }
    }

    private float progress(Pop pop, float tickDelta) {
        float length = Math.max(1.0f, duration.getValue().floatValue() / MS_PER_TICK);
        return Math.clamp((ticks - pop.startTick + tickDelta) / length, 0.0f, 1.0f);
    }

    private record Pop(StaticPlayerEntity model, int startTick) { }
}
