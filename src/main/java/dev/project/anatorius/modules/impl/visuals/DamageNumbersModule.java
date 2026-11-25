package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PlayerPopEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.graphics.ChunkOverlayRenderer;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

@RegisterModule(name = "DamageNumbers", description = "Shows floating damage numbers when entities get hurt.", category = Module.Category.VISUALS)
public class DamageNumbersModule extends Module {
    public final NumberSetting range = new NumberSetting("Range", "Only entities within this distance are tracked.", 24, 4, 64);
    public final BooleanSetting players = new BooleanSetting("Players", "Show damage on other players.", true);
    public final BooleanSetting mobs = new BooleanSetting("Mobs", "Show damage on non-player living entities.", true);
    public final BooleanSetting self = new BooleanSetting("Self", "Show damage on yourself.", false);
    public final BooleanSetting heals = new BooleanSetting("Heals", "Also show health gains with a '+' prefix.", false);
    public final BooleanSetting totems = new BooleanSetting("Totems", "Show \"TOTEM\" when a player pops a totem.", true);
    public final NumberSetting duration = new NumberSetting("Duration", "How long each number stays visible (ms).", 1000, 300, 3000);
    public final NumberSetting scale = new NumberSetting("Scale", "Text size of the numbers.", 30, 10, 100);
    public final NumberSetting depth = new NumberSetting("Depth", "How thick the 3D numbers are, in blocks.", 0.12, 0.0, 0.4);
    public final ColorSetting damageColor = new ColorSetting("DamageColor", "Color of damage numbers.", new ColorSetting.Color(new Color(255, 107, 122, 255), false, false));
    public final ColorSetting critColor = new ColorSetting("CritColor", "Color of numbers from crit hits.", new ColorSetting.Color(new Color(255, 214, 107, 255), false, false));
    public final ColorSetting healColor = new ColorSetting("HealColor", "Color of health gain numbers.", new ColorSetting.Color(new Color(110, 231, 160, 255), false, false));
    public final ColorSetting totemColor = new ColorSetting("TotemColor", "Color of the totem pop text.", new ColorSetting.Color(new Color(196, 140, 255, 255), false, false));

    private static final long CRIT_WINDOW_MS = 500L;
    private static final int MAX_FLOATERS = 64;
    private static final int MAX_PENDING_CRITS = 256;
    private static final float MIN_CHANGE = 0.05f;

    private static final class Floater {
        final double x, y, z;
        final String text;
        final Color color;
        final long start;
        final double dx, dz;

        Floater(double x, double y, double z, String text, Color color, long start, double dx, double dz) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.text = text;
            this.color = color;
            this.start = start;
            this.dx = dx;
            this.dz = dz;
        }
    }

    private final Int2FloatOpenHashMap lastHp = new Int2FloatOpenHashMap();
    private final IntOpenHashSet seen = new IntOpenHashSet();
    private final ArrayDeque<Floater> floaters = new ArrayDeque<>();
    private ClientWorld lastWorld;

    private final ConcurrentHashMap<Integer, Long> pendingCrits = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<PlayerEntity> pendingPops = new ConcurrentLinkedQueue<>();

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (getNull()) return;

        if (mc.world != lastWorld) {
            clearAll();
            lastWorld = mc.world;
        }

        long now = System.currentTimeMillis();
        pendingCrits.values().removeIf(t -> now - t > CRIT_WINDOW_MS);
        drainPops();

        seen.clear();
        double maxSq = range.getValue().doubleValue() * range.getValue().doubleValue();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (mc.player.squaredDistanceTo(living) > maxSq) continue;
            if (!isTracked(living)) continue;

            int id = living.getId();
            seen.add(id);
            float hp = living.getHealth() + living.getAbsorptionAmount();

            if (lastHp.containsKey(id)) {
                float delta = hp - lastHp.get(id);
                if (delta <= -MIN_CHANGE) {
                    Color color = isCrit(id, now) ? critColor.getColor() : damageColor.getColor();
                    spawnAbove(living, format(-delta), color);
                } else if (delta >= MIN_CHANGE && heals.getValue()) {
                    spawnAbove(living, "+" + format(delta), healColor.getColor());
                }
            }

            if (living.isDead()) lastHp.remove(id);
            else lastHp.put(id, hp);
        }

        IntIterator it = lastHp.keySet().iterator();
        while (it.hasNext()) {
            if (!seen.contains(it.nextInt())) it.remove();
        }
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPacket() instanceof EntityAnimationS2CPacket packet)) return;
        int animation = packet.getAnimationId();
        if (animation != EntityAnimationS2CPacket.CRIT && animation != EntityAnimationS2CPacket.ENCHANTED_HIT) return;

        if (pendingCrits.size() > MAX_PENDING_CRITS) pendingCrits.clear();
        pendingCrits.put(packet.getEntityId(), System.currentTimeMillis());
    }

    @SubscribeEvent
    public void onPlayerPop(PlayerPopEvent event) {
        pendingPops.add(event.getPlayer());
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (getNull() || floaters.isEmpty()) return;

        long now = System.currentTimeMillis();
        double life = duration.getValue().doubleValue();
        floaters.removeIf(f -> now - f.start > life);

        int size = scale.getValue().intValue();
        int layers = depth.getValue().floatValue() <= 0f ? 0 : ChunkOverlayRenderer.gpuTier() == ChunkOverlayRenderer.GpuTier.HIGH ? 8 : 4;
        for (Floater f : floaters) {
            double t = Math.min(1.0, (now - f.start) / life);

            double rise = 0.9 * (1.0 - Math.pow(1.0 - t, 3));
            double fade = t < 0.6 ? 1.0 : 1.0 - (t - 0.6) / 0.4;
            int alpha = (int) Math.max(0, Math.min(255, 255 * fade));
            if (alpha < 8) continue;

            render3D(event.getMatrices(), f, f.x + f.dx * t, f.y + rise, f.z + f.dz * t, t, size, alpha, layers);
        }
    }

    private void render3D(MatrixStack matrices, Floater f, double x, double y, double z, double t, int size, int alpha, int layers) {
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();
        float distance = (float) Math.sqrt(cam.squaredDistanceTo(x, y, z));
        float scaling = distance <= 8.0f ? 0.0245f : 0.0018f + (size / 10000.0f) * distance;

        double p = Math.min(1.0, t / 0.25);
        double pop = 1.0 + 2.70158 * Math.pow(p - 1.0, 3) + 1.70158 * Math.pow(p - 1.0, 2);
        float s = scaling * (float) Math.max(0.05, pop);
        double settle = 1.0 - Math.pow(1.0 - Math.min(1.0, t / 0.35), 3);
        float yaw = (float) ((f.dx >= 0 ? 1 : -1) * (18.0 + 52.0 * (1.0 - settle)));

        int width = Anatorius.FONT_MANAGER.getWidth(f.text);
        int height = Anatorius.FONT_MANAGER.getHeight();
        float step = layers == 0 ? 0f : depth.getValue().floatValue() / layers;

        matrices.push();
        matrices.translate(x - cam.x, y - cam.y, z - cam.z);
        matrices.multiply(camera.getRotation());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        for (int i = layers; i >= 0; i--) {
            matrices.push();
            matrices.translate(0.0, 0.0, -i * step);
            matrices.scale(s, -s, s);
            float shade = i == 0 ? 1f : 0.62f - 0.3f * i / layers;
            Color base = f.color;
            int a = i == 0 ? alpha : alpha * alpha / 255;
            if (a < 8) { matrices.pop(); continue; }
            Color c = new Color(Math.round(base.getRed() * shade), Math.round(base.getGreen() * shade), Math.round(base.getBlue() * shade), a);
            Anatorius.FONT_MANAGER.drawTextWithShadow(matrices, f.text, -width / 2, -height, mc.getBufferBuilders().getEntityVertexConsumers(), c);
            matrices.pop();
        }
        matrices.pop();
    }

    @Override
    public void onDisable() {
        clearAll();
        lastWorld = null;
    }

    @Override
    public String getMetaData() {
        return String.valueOf(floaters.size());
    }

    private boolean isTracked(LivingEntity entity) {
        if (entity instanceof ArmorStandEntity) return false;
        if (entity == mc.player) return self.getValue();
        if (entity instanceof PlayerEntity) return players.getValue();
        return mobs.getValue();
    }

    private boolean isCrit(int id, long now) {
        Long time = pendingCrits.remove(id);
        return time != null && now - time <= CRIT_WINDOW_MS;
    }

    private void drainPops() {
        PlayerEntity popped;
        while ((popped = pendingPops.poll()) != null) {
            if (!totems.getValue()) continue;
            if (popped == mc.player && !self.getValue()) continue;
            addFloater(popped.getX(), popped.getY() + popped.getHeight() + 0.25, popped.getZ(), "TOTEM", totemColor.getColor());
        }
    }

    private void spawnAbove(LivingEntity entity, String text, Color color) {
        addFloater(entity.getX(), entity.getY() + entity.getHeight() + 0.25, entity.getZ(), text, color);
    }

    private void addFloater(double x, double y, double z, String text, Color color) {
        if (floaters.size() >= MAX_FLOATERS) floaters.pollFirst();
        double dx = ThreadLocalRandom.current().nextDouble(-0.35, 0.35);
        double dz = ThreadLocalRandom.current().nextDouble(-0.35, 0.35);
        floaters.addLast(new Floater(x, y, z, text, color, System.currentTimeMillis(), dx, dz));
    }

    private static String format(float value) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    private void clearAll() {
        lastHp.clear();
        seen.clear();
        floaters.clear();
        pendingCrits.clear();
        pendingPops.clear();
    }
}
