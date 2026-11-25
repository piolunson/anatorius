package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.ModelRenderer;
import dev.project.anatorius.utils.graphics.Renderer3D;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.*;

@RegisterModule(name = "Chams", description = "Adds a customizable render on top of the default Minecraft rendering.", category = Module.Category.VISUALS)
public class ChamsModule extends Module {
    public CategorySetting entitiesCategory = new CategorySetting("Entities", "The category for settings related to chams rendered on living entities.");
    public BooleanSetting players = new BooleanSetting("Players", "Renders the chams on player entities.", new CategorySetting.Visibility(entitiesCategory), true);
    public BooleanSetting self = new BooleanSetting("Self", "Renders the chams on yourself in third person.", new CategorySetting.Visibility(entitiesCategory), true);
    public BooleanSetting hostiles = new BooleanSetting("Hostiles", "Renders the chams on hostile entities.", new CategorySetting.Visibility(entitiesCategory), false);
    public BooleanSetting passives = new BooleanSetting("Passives", "Renders the chams on passive entities.", new CategorySetting.Visibility(entitiesCategory), false);
    public NumberSetting entityRange = new NumberSetting("EntityRange", "Range", "Max distance (blocks) entity chams are drawn at. Lower = more FPS.", new CategorySetting.Visibility(entitiesCategory), 128, 8, 256);
    public BooleanSetting entityPulse = new BooleanSetting("EntityPulse", "Pulse", "Adds a pulsing effect to the chams opacity.", new CategorySetting.Visibility(entitiesCategory), false);
    public BooleanSetting entityShine = new BooleanSetting("EntityShine", "Shine", "Adds a shine effect to the chams.", new CategorySetting.Visibility(entitiesCategory), false);
    public ModeSetting entityMode = new ModeSetting("EntityMode", "Mode", "The rendering that will be applied to living entities.", new CategorySetting.Visibility(entitiesCategory), "Both", new String[]{"Fill", "Outline", "Both"});
    public ColorSetting entityFillColor = new ColorSetting("EntityFillColor", "FillColor", "The color that will be used for the fill rendering.", new ModeSetting.Visibility(entityMode, "Fill", "Both"), ColorUtils.palette(ColorUtils.THEME_ACCENT, 70));
    public ColorSetting entityOutlineColor = new ColorSetting("EntityOutlineColor", "OutlineColor", "The color that will be used for the outline rendering.", new ModeSetting.Visibility(entityMode, "Outline", "Both"), ColorUtils.palette(ColorUtils.THEME_ACCENT, 170));
    public ModeSetting friendMode = new ModeSetting("FriendMode",  "The mode for the friend color.", new ModeSetting.Visibility(entityMode, "Fill", "Both"), "Default", new String[]{"Default", "Custom", "Sync"});
    public ColorSetting friendFillColor = new ColorSetting("FriendFillColor", "The color that will be used for the fill rendering on friends.", new ModeSetting.Visibility(entityMode, "Fill", "Both"), ColorUtils.palette(ColorUtils.THEME_SAFE, 70));
    public ColorSetting friendOutlineColor = new ColorSetting("FriendOutlineColor", "The color that will be used for the outline rendering on friends.", new ModeSetting.Visibility(entityMode, "Outline", "Both"), ColorUtils.palette(ColorUtils.THEME_SAFE, 170));
    public BooleanSetting damageModify = new BooleanSetting("DamageModify", "Changes the color of the chams when a player takes damage.", new ModeSetting.Visibility(entityMode, "Fill", "Both"), false);
    public ColorSetting damageColor = new ColorSetting("DamageColor", "The color to apply on chams when a player takes damage.", new BooleanSetting.Visibility(damageModify, true), ColorUtils.palette(ColorUtils.THEME_DANGER, 255));

    public CategorySetting crystalsCategory = new CategorySetting("Crystals", "The category for settings related to crystal chams.");
    public BooleanSetting crystals = new BooleanSetting("Crystals", "Enabled", "Renders the chams on crystal entities.", new CategorySetting.Visibility(crystalsCategory), true);
    public NumberSetting crystalRange = new NumberSetting("CrystalRange", "Range", "Max distance (blocks) crystal chams are drawn at. Lower = more FPS.", new CategorySetting.Visibility(crystalsCategory), 128, 8, 256);
    public BooleanSetting crystalPulse = new BooleanSetting("CrystalPulse", "Pulse", "Adds a pulsing effect to the chams opacity.", new CategorySetting.Visibility(crystalsCategory), false);
    public BooleanSetting crystalShine = new BooleanSetting("CrystalShine", "Shine", "Adds a shine effect on crystal chams.", new CategorySetting.Visibility(crystalsCategory), false);
    public ModeSetting crystalMode = new ModeSetting("CrystalMode", "Mode", "The rendering that will be applied to crystal entities.", new CategorySetting.Visibility(crystalsCategory), "Both", new String[]{"Fill", "Outline", "Both"});
    public ColorSetting crystalFillColor = new ColorSetting("CrystalFillColor", "FillColor", "The color that will be used for the fill rendering.", new ModeSetting.Visibility(crystalMode, "Fill", "Both"), ColorUtils.palette(ColorUtils.THEME_SKY, 60));
    public ColorSetting crystalOutlineColor = new ColorSetting("CrystalOutlineColor", "OutlineColor", "The color that will be used for the outline rendering.", new ModeSetting.Visibility(crystalMode, "Outline", "Both"), ColorUtils.palette(ColorUtils.THEME_SKY, 180));

    public CategorySetting renderCategory = new CategorySetting("Render", "How the chams are drawn (entities and crystals).");
    public BooleanSetting throughWalls = new BooleanSetting("ThroughWalls", "Shows the chams through blocks. Off: only the parts you can see.", new CategorySetting.Visibility(renderCategory), true);
    public NumberSetting lineWidth = new NumberSetting("LineWidth", "Width of the outline lines.", new CategorySetting.Visibility(renderCategory), 1.0f, 0.5f, 5.0f);
    public BooleanSetting distanceFade = new BooleanSetting("DistanceFade", "Fades the chams out toward their max range.", new CategorySetting.Visibility(renderCategory), false);
    public BooleanSetting glow = new BooleanSetting("Glow", "Draws a soft glow around the model in the outline color.", new CategorySetting.Visibility(renderCategory), false);
    public NumberSetting glowRadius = new NumberSetting("GlowRadius", "How far the glow reaches (blocks).", new BooleanSetting.Visibility(glow, true), 0.12f, 0.02f, 0.5f);

    private static final float FADE_START = 0.7f;
    private static final int GLOW_LAYERS = 3;

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (mc.world == null) return;

        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player && (mc.options.getPerspective().isFirstPerson() || !self.getValue())) continue;

            if (entity instanceof EndCrystalEntity) {
                if (!crystals.getValue()) continue;
                float fade = rangeAlpha(entity, cameraPos, crystalRange.getValue().doubleValue());
                if (fade > 0.01f && Renderer3D.isFrustumVisible(entity.getBoundingBox())) renderCrystal(entity, event.getTickDelta(), fade);
            } else if (entity instanceof LivingEntity livingEntity) {
                if (!isValidEntity(livingEntity)) continue;
                float fade = rangeAlpha(entity, cameraPos, entityRange.getValue().doubleValue());
                if (fade > 0.01f && Renderer3D.isFrustumVisible(entity.getBoundingBox())) renderLiving(livingEntity, event.getTickDelta(), fade);
            }
        }
    }

    private void renderLiving(LivingEntity entity, float tickDelta, float fade) {
        boolean friend = entity instanceof PlayerEntity player && !friendMode.getValue().equals("Sync") && Anatorius.FRIEND_MANAGER.contains(player.getName().getString());
        boolean hurt = damageModify.getValue() && entity.hurtTime > 0;

        Color fill = faded(entityColor(entityFillColor, friendFillColor, friend, hurt), fade);
        Color outline = faded(entityColor(entityOutlineColor, friendOutlineColor, friend, hurt), fade);
        boolean drawFill = !entityMode.getValue().equals("Outline");
        boolean drawOutline = !entityMode.getValue().equals("Fill");
        if (entityPulse.getValue()) {
            fill = ColorUtils.getPulse(fill);
            outline = ColorUtils.getPulse(outline);
        }

        draw(entity, tickDelta, new ModelRenderer.Render(drawFill, fill, drawOutline, outline, entityShine.getValue()), drawOutline ? outline : fill);
    }

    private Color entityColor(ColorSetting base, ColorSetting friendSetting, boolean friend, boolean hurt) {
        int alpha = (friend ? friendSetting : base).getColor().getAlpha();
        if (hurt) return ColorUtils.getColor(damageColor.getColor(), alpha);
        if (friend && friendMode.getValue().equals("Default")) return Anatorius.FRIEND_MANAGER.getDefaultFriendColor(alpha);
        return friend ? friendSetting.getColor() : base.getColor();
    }

    private void renderCrystal(Entity crystal, float tickDelta, float fade) {
        Color fill = faded(crystalFillColor.getColor(), fade);
        Color outline = faded(crystalOutlineColor.getColor(), fade);
        boolean drawFill = !crystalMode.getValue().equals("Outline");
        boolean drawOutline = !crystalMode.getValue().equals("Fill");
        if (crystalPulse.getValue()) {
            fill = ColorUtils.getPulse(fill);
            outline = ColorUtils.getPulse(outline);
        }

        draw(crystal, tickDelta, new ModelRenderer.Render(drawFill, fill, drawOutline, outline, crystalShine.getValue()), drawOutline ? outline : fill);
    }

    private void draw(Entity entity, float tickDelta, ModelRenderer.Render render, Color glowColor) {
        ModelRenderer.Bloom bloom = glow.getValue() ? new ModelRenderer.Bloom(glowRadius.getValue().floatValue(), glowColor, GLOW_LAYERS) : null;
        ModelRenderer.renderModel(entity, 1.0f, tickDelta, render, bloom, lineWidth.getValue().floatValue(), !throughWalls.getValue());
    }

    private float rangeAlpha(Entity entity, Vec3d cameraPos, double range) {
        double distanceSq = entity.squaredDistanceTo(cameraPos);
        if (distanceSq > range * range) return 0.0f;
        if (!distanceFade.getValue()) return 1.0f;
        float ratio = (float) (Math.sqrt(distanceSq) / Math.max(range, 0.001));
        if (ratio <= FADE_START) return 1.0f;
        return 1.0f - MathHelper.clamp((ratio - FADE_START) / (1.0f - FADE_START), 0.0f, 1.0f);
    }

    private static Color faded(Color color, float fade) {
        if (fade >= 1.0f) return color;
        return ColorUtils.getColor(color, MathHelper.clamp(Math.round(color.getAlpha() * fade), 0, 255));
    }

    private boolean isValidEntity(Entity entity) {
        if (players.getValue() && entity.getType() == EntityType.PLAYER) return true;
        if (hostiles.getValue() && entity.getType().getSpawnGroup() == SpawnGroup.MONSTER) return true;
        return passives.getValue() && (entity.getType().getSpawnGroup() == SpawnGroup.CREATURE || entity.getType().getSpawnGroup() == SpawnGroup.WATER_CREATURE || entity.getType().getSpawnGroup() == SpawnGroup.WATER_AMBIENT || entity.getType().getSpawnGroup() == SpawnGroup.UNDERGROUND_WATER_CREATURE || entity.getType().getSpawnGroup() == SpawnGroup.AXOLOTLS);
    }
}
