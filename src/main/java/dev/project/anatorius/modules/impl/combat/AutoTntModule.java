package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer3D;
import dev.project.anatorius.utils.minecraft.DamageUtils;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.NetworkUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.rotations.RotationUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@RegisterModule(
        name = "AutoTnt",
        description = "Fully traps a target in obsidian and pushes a primed TNT into them.",
        category = Module.Category.COMBAT
)
public class AutoTntModule extends Module {
    private static final double TARGET_RANGE = 8.0;
    private static final float  TNT_POWER = 4.0f;

    public final NumberSetting  minDamage     = new NumberSetting("MinDamage", "Minimum", "Skip a setup that deals less than this to the target.", 6.0f, 0.0f, 36.0f);
    public final NumberSetting  maxSelfDamage = new NumberSetting("MaxSelfDamage", "MaximumSelf", "Skip a setup that would deal more than this to you.", 12.0f, 0.0f, 36.0f);
    public final NumberSetting  range         = new NumberSetting("Range", "The distance at which blocks will be placed.", 5.0f, 0.0f, 6.0f);
    public final NumberSetting  delay         = new NumberSetting("Delay", "Ticks between setups.", 5.0f, 0.0f, 20.0f);
    public final NumberSetting  blocksPerTick = new NumberSetting("BlocksPerTick", "Obsidian blocks placed per tick while sealing the box.", 4, 1, 8);
    public final BooleanSetting floor         = new BooleanSetting("Floor", "Also seal the block under the target's feet.", false);
    public final BooleanSetting ignite        = new BooleanSetting("Ignite", "Light the TNT with flint and steel once the box is sealed.", true);
    public final BooleanSetting rotate        = new BooleanSetting("Rotate", "Rotate towards the block being placed or lit.", true);
    public final ModeSetting    autoSwitch    = new ModeSetting("Switch", "Inventory switch mode.", "Silent", InventoryUtils.SWITCH_MODES);

    public final CategorySetting renderCategory = new CategorySetting("Render", "Show what was placed.");
    public final BooleanSetting  render       = new BooleanSetting("Render", "Draw the blocks we place.", new CategorySetting.Visibility(renderCategory), true);
    public final NumberSetting   fadeTime     = new NumberSetting("FadeTime", "Seconds the boxes take to fade.", new CategorySetting.Visibility(renderCategory), 0.3f, 0.05f, 2.0f);
    public final ColorSetting    fillColor    = new ColorSetting("FillColor", "Box fill.", new CategorySetting.Visibility(renderCategory), ColorUtils.getDefaultFillColor());
    public final ColorSetting    outlineColor = new ColorSetting("OutlineColor", "Box outline.", new CategorySetting.Visibility(renderCategory), ColorUtils.getDefaultOutlineColor());

    private final Map<BlockPos, Integer> renderMap = new HashMap<>();

    private BlockPos pendingIgnite;
    private int   delayTicks;
    private float lastDamage;

    @Override
    public void onEnable() {
        resetState();
        delayTicks = (int) delay.getValue().floatValue();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        pendingIgnite = null;
        delayTicks    = 0;
        lastDamage    = 0;
        renderMap.clear();
    }

    @Override
    public String getMetaData() {
        return lastDamage > 0 ? String.format("%.1f", lastDamage) : "None";
    }

    @SubscribeEvent
    public void onUpdate(PlayerUpdateEvent event) {
        if (getNull() || mc.player.isDead()) return;

        int fadeTicks = fadeTicks();
        renderMap.entrySet().removeIf(entry -> mc.player.age - entry.getValue() > fadeTicks);

        if (pendingIgnite != null) {
            if (mc.world.getBlockState(pendingIgnite).isOf(Blocks.TNT)) igniteTnt(pendingIgnite);
            pendingIgnite = null;
            delayTicks = 0;
            return;
        }

        if (delayTicks < delay.getValue().floatValue()) { delayTicks++; return; }

        int tntSlot = InventoryUtils.findHotbar(Items.TNT);
        if (tntSlot < 0) { lastDamage = 0; return; }
        if (InventoryUtils.findHotbar(Items.OBSIDIAN) < 0) { lastDamage = 0; return; }
        if (ignite.getValue() && InventoryUtils.findHotbar(Items.FLINT_AND_STEEL) < 0) { lastDamage = 0; return; }

        PlayerEntity target = findTarget();
        if (target == null) { lastDamage = 0; return; }

        Setup setup = findSetup(target);
        if (setup == null) return;

        if (buildSeal(setup)) return;

        if (!isSealComplete(setup)) return;

        if (!mc.world.getBlockState(setup.tnt).isAir()) return;
        Direction direction = WorldUtils.getDirection(setup.tnt, false);
        if (direction == null) return;

        placeTnt(setup.tnt, direction, tntSlot);
    }

    private Setup findSetup(PlayerEntity target) {
        BlockPos feet = target.getBlockPos();
        BlockPos head = feet.up();
        BlockPos tnt  = feet.up(2);

        if (!mc.world.getBlockState(tnt).isAir()) { lastDamage = 0; return null; }

        double rangeSq = range.getValue().doubleValue() * range.getValue().doubleValue();
        if (mc.player.getEyePos().squaredDistanceTo(tnt.toCenterPos()) > rangeSq) { lastDamage = 0; return null; }

        Vec3d explosion = head.toCenterPos();
        float damage = DamageUtils.getDamage(target, target.getBoundingBox(), explosion, TNT_POWER, null, false);
        lastDamage = damage;
        if (damage < minDamage.getValue().floatValue()) return null;

        float selfDamage = DamageUtils.getDamage(mc.player, mc.player.getBoundingBox(), explosion, TNT_POWER, null, false);
        if (selfDamage > maxSelfDamage.getValue().floatValue()) return null;

        return new Setup(tnt, sealPositions(feet, head, tnt), damage);
    }

    private Set<BlockPos> sealPositions(BlockPos feet, BlockPos head, BlockPos tnt) {
        Set<BlockPos> seal = new LinkedHashSet<>();

        for (Direction d : Direction.Type.HORIZONTAL) {
            seal.add(feet.offset(d));
            seal.add(head.offset(d));
            seal.add(tnt.offset(d));
        }

        seal.add(tnt.up());

        if (floor.getValue()) seal.add(feet.down());

        seal.remove(feet);
        seal.remove(head);
        seal.remove(tnt);
        return seal;
    }

    private boolean buildSeal(Setup setup) {
        int obsidianSlot = InventoryUtils.findHotbar(Items.OBSIDIAN);
        if (obsidianSlot < 0) return false;

        Vec3d eye = mc.player.getEyePos();
        double rangeSq = range.getValue().doubleValue() * range.getValue().doubleValue();
        int limit = blocksPerTick.getValue().intValue();
        int placed = 0;

        for (BlockPos pos : setup.seal) {
            if (!mc.world.getBlockState(pos).isAir() || !WorldUtils.isPlaceable(pos)) continue;
            if (eye.squaredDistanceTo(pos.toCenterPos()) > rangeSq) continue;
            if (intersects(pos, mc.player)) continue;

            if (placeBlock(pos, WorldUtils.getDirection(pos, false), obsidianSlot)) {
                renderMap.put(pos, mc.player.age);
                if (++placed >= limit) break;
            }
        }
        return placed > 0;
    }

    private boolean isSealComplete(Setup setup) {
        for (BlockPos pos : setup.seal) {
            if (WorldUtils.isReplaceable(pos)) return false;
        }
        return true;
    }

    private void placeTnt(BlockPos tntPos, Direction direction, int tntSlot) {
        if (!placeBlock(tntPos, direction, tntSlot)) return;

        renderMap.put(tntPos, mc.player.age);
        pendingIgnite = ignite.getValue() ? tntPos : null;
        delayTicks = 0;
    }

    private boolean placeBlock(BlockPos position, Direction direction, int slot) {
        int previousSlot = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);
        boolean placed = WorldUtils.placeBlock(position, direction, Hand.MAIN_HAND, rotate.getValue(), false, render.getValue());
        InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
        return placed;
    }

    private void igniteTnt(BlockPos pos) {
        int flintSlot = InventoryUtils.findHotbar(Items.FLINT_AND_STEEL);
        if (flintSlot < 0) return;

        if (rotate.getValue()) {
            Vec3d hit = pos.toCenterPos();
            Anatorius.ROTATION_MANAGER.packetRotate(
                    RotationUtils.getRotations(hit.x, hit.y, hit.z, false),
                    Anatorius.ROTATION_MANAGER.getModulePriority(this));
        }

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.switchSlot(autoSwitch.getValue(), flintSlot, previousSlot);

        BlockHitResult hitResult = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        NetworkUtils.sendSequencedPacket(sequence -> new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, hitResult, sequence));
        mc.player.swingHand(Hand.MAIN_HAND);

        InventoryUtils.switchBack(autoSwitch.getValue(), flintSlot, previousSlot);
    }

    private PlayerEntity findTarget() {
        PlayerEntity best = null;
        double bestSq = TARGET_RANGE * TARGET_RANGE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isDead()) continue;
            if (!Anatorius.TARGET_MANAGER.isTarget(player)) continue;

            double distanceSq = mc.player.squaredDistanceTo(player);
            if (distanceSq >= bestSq) continue;

            bestSq = distanceSq;
            best = player;
        }
        return best;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (getNull() || !render.getValue()) return;

        int fadeTicks = fadeTicks();
        for (Map.Entry<BlockPos, Integer> entry : renderMap.entrySet()) {
            int age = mc.player.age - entry.getValue();
            if (age > fadeTicks) continue;

            float remaining = 1.0f - (float) age / fadeTicks;
            Box box = new Box(entry.getKey());

            Renderer3D.renderBox(event.getMatrices(), box, fade(fillColor.getColor(), remaining));
            Renderer3D.renderBoxOutline(event.getMatrices(), box, fade(outlineColor.getColor(), remaining));
        }
    }

    private int fadeTicks() {
        return Math.max(1, (int) (fadeTime.getValue().floatValue() * 20));
    }

    private static Color fade(Color color, float remaining) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                MathHelper.clamp((int) (color.getAlpha() * remaining), 0, 255));
    }

    private boolean intersects(BlockPos pos, PlayerEntity entity) {
        return new Box(pos).intersects(entity.getBoundingBox());
    }

    private record Setup(BlockPos tnt, Set<BlockPos> seal, float damage) {}
}
