package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.*;
import dev.project.anatorius.utils.animations.Easing;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer3D;
import dev.project.anatorius.utils.minecraft.HoleUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RegisterModule(name = "HoleESP", description = "Highlights all holes in a specified radius.", category = Module.Category.VISUALS)
public class HoleESPModule extends Module {
    public NumberSetting range = new NumberSetting("Range", "The maximum range at which holes will be rendered.", 10, 1, 50);
    public BooleanSetting asynchronous = new BooleanSetting("Asynchronous", "Performs calculations on separate threads.", true);
    public BooleanSetting doubleHoles = new BooleanSetting("DoubleHoles", "Whether or not to render the ESP on double holes.", true);
    public BooleanSetting quadHoles = new BooleanSetting("QuadHoles", "Whether or not to render the ESP on quad holes.", true);
    public BooleanSetting fade = new BooleanSetting("Fade", "Fades the holes in and out based on your distance to them.", false);

    public ModeSetting fill = new ModeSetting("Fill", "The mode for the fill rendering on the hole boxes.", "Normal", new String[]{"None", "Normal", "Gradient"});
    public NumberSetting fillHeight = new NumberSetting("FillHeight", "The height of the fill rendering on the holes.", new ModeSetting.Visibility(fill, "Normal", "Gradient"), 1.0, -2.0, 2.0);
    public ModeSetting outline = new ModeSetting("Outline", "The mode for the outline rendering on the hole boxes.", "Normal", new String[]{"None", "Normal", "Gradient"});
    public NumberSetting outlineHeight = new NumberSetting("OutlineHeight", "The height of the outline rendering on the holes.", new ModeSetting.Visibility(outline, "Normal", "Gradient"), 1.0, -2.0, 2.0);

    public CategorySetting bedrockColorsCategory = new CategorySetting("Bedrock", "The category that contains the settings for coloring of bedrock holes.");
    public ColorSetting bedrockFillColor = new ColorSetting("BedrockFillColor", "Fill", "The color for the fill rendering on bedrock holes.", new CategorySetting.Visibility(bedrockColorsCategory), ColorUtils.paletteFill(ColorUtils.THEME_SAFE));
    public ColorSetting bedrockOutlineColor = new ColorSetting("BedrockOutlineColor", "Outline", "The color for the outline rendering on bedrock holes.", new CategorySetting.Visibility(bedrockColorsCategory), ColorUtils.paletteOutline(ColorUtils.THEME_SAFE));

    public CategorySetting mixedColorsCategory = new CategorySetting("Mixed", "The category that contains the settings for coloring of mixed holes.");
    public ColorSetting mixedFillColor = new ColorSetting("MixedFillColor", "Fill", "The color for the fill rendering on mixed holes.", new CategorySetting.Visibility(mixedColorsCategory), ColorUtils.paletteFill(ColorUtils.THEME_CAUTION));
    public ColorSetting mixedOutlineColor = new ColorSetting("MixedOutlineColor", "Outline", "The color for the outline rendering on mixed holes.", new CategorySetting.Visibility(mixedColorsCategory), ColorUtils.paletteOutline(ColorUtils.THEME_CAUTION));

    public CategorySetting obsidianColorsCategory = new CategorySetting("Obsidian", "The category that contains the settings for coloring of obsidian holes.");
    public ColorSetting obsidianFillColor = new ColorSetting("ObsidianFillColor", "Fill", "The color for the fill rendering on obsidian holes.", new CategorySetting.Visibility(obsidianColorsCategory), ColorUtils.paletteFill(ColorUtils.THEME_DANGER));
    public ColorSetting obsidianOutlineColor = new ColorSetting("ObsidianOutlineColor", "Outline", "The color for the outline rendering on obsidian holes.", new CategorySetting.Visibility(obsidianColorsCategory), ColorUtils.paletteOutline(ColorUtils.THEME_DANGER));

    private static final int SLICES = 3;
    private static final long WAVE_MS_PER_BLOCK = 35L, WAVE_MAX_MS = 700L;
    private static final long IN_MS = 400L, OUT_MS = 250L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Anatorius HoleESP");
        thread.setDaemon(true);
        return thread;
    });

    private static final class Entry {
        volatile HoleUtils.Hole hole;
        final BlockPos origin;
        volatile long born;
        volatile long gone = -1L;
        volatile int seenPass;

        Entry(HoleUtils.Hole hole, BlockPos origin, long born, int pass) {
            this.hole = hole;
            this.origin = origin;
            this.born = born;
            this.seenPass = pass;
        }
    }

    private final Map<Box, Entry> entries = new ConcurrentHashMap<>();
    private int cursor, pass;

    @Override
    public void onEnable() {
        entries.clear();
        cursor = 0;
        pass = 0;
    }

    @Override
    public void onDisable() {
        entries.clear();
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        int total = Anatorius.WORLD_MANAGER.getRadius(range.getValue().doubleValue());
        if (cursor >= total) cursor = 0;
        int sliceSize = Math.max(1, (total + SLICES - 1) / SLICES);
        int end = Math.min(total, cursor + sliceSize);
        BlockPos center = mc.player.getBlockPos();
        List<BlockPos> slice = new ArrayList<>(end - cursor);
        for (int i = cursor; i < end; i++) slice.add(center.add(Anatorius.WORLD_MANAGER.getOffset(i)));
        boolean passDone = end >= total;
        int thisPass = pass;
        boolean opening = thisPass == 0;
        cursor = passDone ? 0 : end;
        if (passDone) pass++;

        List<Entry> known = new ArrayList<>(entries.values());
        double eyeX = mc.player.getX(), eyeY = mc.player.getY(), eyeZ = mc.player.getZ();

        Runnable job = () -> {
            long now = System.currentTimeMillis();
            for (Entry entry : known) {
                if (entry.gone >= 0) continue;
                HoleUtils.Hole again = find(entry.origin, entry.hole.type());
                if (again == null || !again.box().equals(entry.hole.box())) entry.gone = now;
                else entry.hole = again;
            }
            for (BlockPos position : slice) {
                HoleUtils.Hole hole = findAny(position);
                if (hole == null) continue;
                Entry entry = entries.get(hole.box());
                if (entry == null) {
                    long delay = 0L;
                    if (opening) {
                        double distance = Math.sqrt(hole.box().getCenter().squaredDistanceTo(eyeX, eyeY, eyeZ));
                        delay = Math.min(WAVE_MAX_MS, (long) (distance * WAVE_MS_PER_BLOCK));
                    }
                    entries.put(hole.box(), new Entry(hole, position, now + delay, thisPass));
                } else {
                    if (entry.gone >= 0) {
                        entry.gone = -1L;
                        entry.born = now;
                    }
                    entry.hole = hole;
                    entry.seenPass = thisPass;
                }
            }
            if (passDone) {
                for (Entry entry : entries.values()) {
                    if (entry.gone < 0 && entry.seenPass < thisPass) entry.gone = now;
                }
            }
        };

        if (asynchronous.getValue()) executor.submit(() -> {
            try {
                job.run();
            } catch (Throwable t) {
                System.err.println("[Anatorius] HoleESP calculation failed:");
                t.printStackTrace();
            }
        });
        else job.run();
    }

    private HoleUtils.Hole findAny(BlockPos position) {
        HoleUtils.Hole single = HoleUtils.getSingleHole(position, 0);
        if (single != null) return single;
        if (doubleHoles.getValue()) {
            HoleUtils.Hole doubleHole = HoleUtils.getDoubleHole(position, 0);
            if (doubleHole != null) return doubleHole;
        }
        return quadHoles.getValue() ? HoleUtils.getQuadHole(position, 0) : null;
    }

    private HoleUtils.Hole find(BlockPos origin, HoleUtils.HoleType type) {
        return switch (type) {
            case SINGLE -> HoleUtils.getSingleHole(origin, 0);
            case DOUBLE -> doubleHoles.getValue() ? HoleUtils.getDoubleHole(origin, 0) : null;
            case QUAD -> quadHoles.getValue() ? HoleUtils.getQuadHole(origin, 0) : null;
        };
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (mc.world == null || mc.player == null || entries.isEmpty()) return;
        long now = System.currentTimeMillis();
        float inTime = IN_MS;

        var iterator = entries.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            float scale;
            if (entry.gone >= 0) {
                float out = (now - entry.gone) / (float) OUT_MS;
                if (out >= 1.0f) {
                    iterator.remove();
                    continue;
                }
                scale = Easing.ease(1.0f - out, Easing.Method.EASE_IN_CUBIC) * appear(entry, now, inTime);
            } else {
                if (now < entry.born) continue;
                scale = appear(entry, now, inTime);
            }
            if (scale <= 0.01f) continue;
            render(event, entry.hole, scale, MathHelper.clamp((now - entry.born) / inTime, 0.0f, 1.0f));
        }
    }

    private static float appear(Entry entry, long now, float inTime) {
        float t = MathHelper.clamp((now - entry.born) / inTime, 0.0f, 1.0f);
        return Easing.ease(t, Easing.Method.EASE_OUT_BACK);
    }

    private void render(RenderWorldEvent event, HoleUtils.Hole hole, float heightScale, float fadeIn) {
        Box box = hole.box();
        float alpha = MathHelper.clamp(Math.min(heightScale, Easing.ease(fadeIn, Easing.Method.EASE_OUT_CUBIC)), 0.0f, 1.0f);
        double fillH = fillHeight.getValue().doubleValue() * heightScale;
        double outlineH = outlineHeight.getValue().doubleValue() * heightScale;
        Box filledBox = new Box(box.minX, box.minY, box.minZ, box.maxX, box.minY + fillH, box.maxZ);
        Box outlinedBox = new Box(box.minX, box.minY, box.minZ, box.maxX, box.minY + outlineH, box.maxZ);
        Color fillColor = getFillColor(hole, alpha);
        Color outlineColor = getOutlineColor(hole, alpha);
        Color clear = new Color(0, 0, 0, 0);

        if (fill.getValue().equalsIgnoreCase("Normal")) Renderer3D.renderBox(event.getMatrices(), filledBox, fillColor);
        if (fill.getValue().equalsIgnoreCase("Gradient")) Renderer3D.renderGradientBox(event.getMatrices(), filledBox, fillH < 0.0 ? fillColor : clear, fillH < 0.0 ? clear : fillColor);

        if (outline.getValue().equalsIgnoreCase("Normal")) Renderer3D.renderBoxOutline(event.getMatrices(), outlinedBox, outlineColor);
        if (outline.getValue().equalsIgnoreCase("Gradient")) Renderer3D.renderGradientBoxOutline(event.getMatrices(), outlinedBox, outlineH < 0.0 ? outlineColor : clear, outlineH < 0.0 ? clear : outlineColor);
    }

    @Override
    public String getMetaData() {
        int count = 0;
        for (Entry entry : entries.values()) if (entry.gone < 0) count++;
        return String.valueOf(count);
    }

    private Color getFillColor(HoleUtils.Hole hole, float alpha) {
        Color color = switch (hole.safety()) {
            case BEDROCK -> bedrockFillColor.getColor();
            case MIXED -> mixedFillColor.getColor();
            default -> obsidianFillColor.getColor();
        };
        return withAlpha(color, hole, alpha);
    }

    private Color getOutlineColor(HoleUtils.Hole hole, float alpha) {
        Color color = switch (hole.safety()) {
            case BEDROCK -> bedrockOutlineColor.getColor();
            case MIXED -> mixedOutlineColor.getColor();
            default -> obsidianOutlineColor.getColor();
        };
        return withAlpha(color, hole, alpha);
    }

    private Color withAlpha(Color color, HoleUtils.Hole hole, float alpha) {
        if (fade.getValue()) alpha *= getEasing(hole);
        if (alpha >= 1.0f) return color;
        return ColorUtils.getColor(color, (int) (color.getAlpha() * alpha));
    }

    private float getEasing(HoleUtils.Hole hole) {
        float scale = (float) (1.0f - MathHelper.clamp(Math.sqrt(mc.player.squaredDistanceTo(hole.box().getCenter())) / range.getValue().doubleValue(), 0.0f, 1.0f));
        return Easing.ease(scale, Easing.Method.EASE_OUT_CUBIC);
    }
}
