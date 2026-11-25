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
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

@RegisterModule(
        name = "PistonCrystal",
        description = "Pushes a crystal into a surrounded target with a piston and detonates it.",
        category = Module.Category.COMBAT
)
public class PistonCrystalModule extends Module {
    private static final double TARGET_RANGE = 10.0;
    private static final double PLACE_RANGE  = 6.0;
    private static final double BREAK_RANGE  = 3.0;
    private static final int    EXTEND_TIMEOUT = 10;
    private static final float  YAW_TOLERANCE = 1.0f;
    private static final float  PISTON_PITCH = 5.0f;
    private static final float  BLAST_PROOF = 600.0f;
    private static final double NUDGE = 1.0 / 16.0;
    private static final double MAX_BLAST_GAP = 1.5;
    private static final int    MIN_CLEAR_RAYS = 2;

    public final NumberSetting range         = new NumberSetting("Range", "The distance at which setups will be placed.", 6.0f, 0.0f, 6.0f);
    public final BooleanSetting strictDirection = new BooleanSetting("StrictDirection", "Skips the setup this tick when the piston look could not be sent (another module owns the tick's look), so the piston never mis-faces.", true);
    public final NumberSetting delay         = new NumberSetting("Delay", "Ticks between setups.", 10.0f, 0.0f, 20.0f);
    public final BooleanSetting autoBase = new BooleanSetting("AutoBase", "Place the obsidian for the crystal to sit on when there is nothing to use.", true);
    public final ModeSetting autoSwitch  = new ModeSetting("Switch", "Inventory switch mode. Alt modes also use items from the main inventory. None cannot build the setup.", "Silent", InventoryUtils.SWITCH_MODES);
    public final BooleanSetting rotate   = new BooleanSetting("Rotate", "Looks at the redstone, base, crystal and the crystal it breaks with one packet right before each (Hachimi). The piston's own look is always sent: its facing depends on it.", true);

    public final CategorySetting renderCategory = new CategorySetting("Render", "Show what was placed.");
    public final BooleanSetting render      = new BooleanSetting("Render", "Draw the blocks we place.", new CategorySetting.Visibility(renderCategory), true);
    public final NumberSetting  fadeTime    = new NumberSetting("FadeTime", "Seconds the boxes take to fade.", new CategorySetting.Visibility(renderCategory), 0.2f, 0.05f, 2.0f);
    public final ColorSetting   fillColor   = new ColorSetting("FillColor", "Box fill.", new CategorySetting.Visibility(renderCategory), ColorUtils.getDefaultFillColor());
    public final ColorSetting   outlineColor = new ColorSetting("OutlineColor", "Box outline.", new CategorySetting.Visibility(renderCategory), ColorUtils.getDefaultOutlineColor());

    private final Map<BlockPos, Integer> renderMap = new HashMap<>();

    private Setup pending;
    private Setup active;
    private int   delayTicks;
    private int   waitTicks;
    private String lastTarget;

    private final BlockPos.Mutable rayCursor = new BlockPos.Mutable();

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
        pending    = null;
        active     = null;
        delayTicks = 0;
        waitTicks  = 0;
        lastTarget = null;
        renderMap.clear();
    }

    @Override
    public String getMetaData() {
        return lastTarget != null ? lastTarget : "None";
    }

    @SubscribeEvent
    public void onUpdate(PlayerUpdateEvent event) {
        if (getNull() || mc.player.isDead()) return;

        int fadeTicks = fadeTicks();
        renderMap.entrySet().removeIf(entry -> mc.player.age - entry.getValue() > fadeTicks);

        if (active != null) { tickActive(); return; }

        if (delayTicks < delay.getValue().floatValue()) { delayTicks++; return; }

        if (autoSwitch.getValue().equalsIgnoreCase("None")) { clearPending(); return; }
        if (pistonSlot() < 0 || slotOf(Items.END_CRYSTAL) < 0) { clearPending(); return; }

        Setup setup = findSetup();
        if (setup == null || !hasItemsFor(setup)) { clearPending(); return; }

        if (breakCrystalsAround(setup.head)) return;

        pending = setup;

        place(setup);
    }

    private void packetLook(float yaw, float pitch) {
        Anatorius.ROTATION_MANAGER.packetRotate(new float[]{yaw, pitch}, Anatorius.ROTATION_MANAGER.getModulePriority(this));
    }

    private void lookAt(Vec3d point) {
        if (!rotate.getValue()) return;
        float[] look = dev.project.anatorius.utils.rotations.RotationUtils.getRotations(point.x, point.y, point.z, false);
        packetLook(look[0], look[1]);
    }

    private boolean hasItemsFor(Setup setup) {
        if (pistonSlot() < 0 || slotOf(Items.END_CRYSTAL) < 0) return false;
        if (setup.placeRedstone && slotOf(Items.REDSTONE_BLOCK) < 0) return false;
        return !setup.placeBase || slotOf(Items.OBSIDIAN) >= 0;
    }

    private void clearPending() {
        pending    = null;
        lastTarget = null;
    }

    private void place(Setup setup) {
        if (!hasItemsFor(setup)) return;

        float pistonYaw = setup.dir.getPositiveHorizontalDegrees();
        packetLook(pistonYaw, PISTON_PITCH);
        boolean facing = MathHelper.abs(MathHelper.wrapDegrees(Anatorius.ROTATION_MANAGER.getServerYaw() - pistonYaw)) < YAW_TOLERANCE
                && MathHelper.abs(Anatorius.ROTATION_MANAGER.getServerPitch() - PISTON_PITCH) < YAW_TOLERANCE;
        if (!facing && strictDirection.getValue()) return;

        if (!placeBlock(setup.piston, pistonSlot(), false)) return;
        if (setup.placeBase && !placeBlock(setup.base, slotOf(Items.OBSIDIAN), true)) return;
        if (setup.placeRedstone) placeBlock(setup.redstone, slotOf(Items.REDSTONE_BLOCK), true);
        placeCrystal(setup.base, slotOf(Items.END_CRYSTAL));

        int tick = mc.player.age;
        if (setup.placeBase) renderMap.put(setup.base, tick);
        renderMap.put(setup.piston, tick);
        if (setup.placeRedstone) renderMap.put(setup.redstone, tick);
        renderMap.put(setup.crystal, tick);

        pending    = null;
        active     = setup;
        waitTicks  = 0;
        delayTicks = 0;
    }

    private boolean placeBlock(BlockPos position, int slot, boolean look) {
        Direction direction = WorldUtils.getDirection(position, false);
        if (look) lookAt(WorldUtils.getHitVector(position, direction != null ? direction : WorldUtils.airPlaceDirection(position)));

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);
        boolean placed = WorldUtils.placeBlock(position, direction, Hand.MAIN_HAND, false, false, render.getValue());
        InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);

        return placed;
    }

    private void placeCrystal(BlockPos base, int slot) {
        lookAt(WorldUtils.getHitVector(base, Direction.UP));
        int previousSlot = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);
        WorldUtils.placeBlock(base.up(), Direction.DOWN, Hand.MAIN_HAND, false, false, render.getValue());
        InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
    }

    private void tickActive() {
        waitTicks++;

        BlockState pistonState = mc.world.getBlockState(active.piston);
        boolean extended = pistonState.contains(Properties.EXTENDED) && pistonState.get(Properties.EXTENDED);
        if (!extended) {
            BlockState atCrystal = mc.world.getBlockState(active.crystal);
            extended = atCrystal.isOf(Blocks.PISTON_HEAD) || atCrystal.isOf(Blocks.MOVING_PISTON);
        }

        if (extended) {
            breakCrystalsAround(active.head);
            active = null;
            return;
        }

        if (waitTicks > EXTEND_TIMEOUT) active = null;
    }

    private boolean breakCrystalsAround(BlockPos head) {
        Vec3d eye = mc.player.getEyePos();
        Box area = new Box(head).expand(1.0);
        boolean found = false;

        for (Entity entity : mc.world.getOtherEntities(mc.player, area)) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            found = true;
            if (squaredDistanceToBox(eye, crystal.getBoundingBox()) > BREAK_RANGE * BREAK_RANGE) continue;
            breakCrystal(crystal);
        }
        return found;
    }

    private void breakCrystal(EndCrystalEntity crystal) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d hit = closestPointOnBox(eye, crystal.getBoundingBox());
        if (!canHitCrystal(crystal, hit)) return;

        lookAt(hit);
        mc.interactionManager.attackEntity(mc.player, crystal);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean canHitCrystal(EndCrystalEntity crystal, Vec3d hit) {
        Box box = crystal.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();
        if (box.contains(eye)) return true;

        Vec3d direction = hit.subtract(eye);
        if (direction.lengthSquared() < 1.0E-7) return true;

        Vec3d reachEnd = eye.add(direction.normalize().multiply(BREAK_RANGE));
        return box.raycast(eye, reachEnd).isPresent();
    }

    private Setup findSetup() {
        PlayerEntity target = findTarget();
        if (target == null) { lastTarget = null; return null; }

        Vec3d eye = mc.player.getEyePos();
        Box box = target.getBoundingBox();

        int minX = MathHelper.floor(box.minX), maxX = MathHelper.floor(box.maxX - 1.0E-7);
        int minY = MathHelper.floor(box.minY), maxY = MathHelper.floor(box.maxY - 1.0E-7);
        int minZ = MathHelper.floor(box.minZ), maxZ = MathHelper.floor(box.maxZ - 1.0E-7);

        boolean lowPose = target.getHeight() <= 1.0f;
        Setup best = null;

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    BlockPos cell = new BlockPos(bx, by, bz);
                    BlockPos[] heads = lowPose ? new BlockPos[]{cell, cell.up()} : new BlockPos[]{cell.up()};

                    for (BlockPos head : heads) {
                        for (Direction dir : Direction.Type.HORIZONTAL) {
                            Setup side = sideSetup(target, head, dir, eye);
                            if (side != null && (best == null || side.score > best.score)) best = side;

                            Setup top = topSetup(target, head, dir, eye);
                            if (top != null && (best == null || top.score > best.score)) best = top;
                        }
                    }
                }
            }
        }

        lastTarget = best != null ? target.getName().getString() : null;
        return best;
    }

    private Setup sideSetup(PlayerEntity target, BlockPos head, Direction dir, Vec3d eye) {
        BlockPos crystalPos = head.offset(dir);
        BlockPos base       = crystalPos.down();
        BlockPos pistonPos  = head.offset(dir, 2);

        boolean placeBase = needsBase(base);
        if (placeBase && !canAutoBase(base)) return null;
        if (!mc.world.getBlockState(crystalPos).isAir()) return null;
        if (!mc.world.getBlockState(head).isAir()) return null;

        Vec3d explosionPos = new Vec3d(head.getX() + 0.5, head.getY(), head.getZ() + 0.5);
        return buildSetup(target, head, dir, crystalPos, base, pistonPos, explosionPos, eye, placeBase);
    }

    private Setup topSetup(PlayerEntity target, BlockPos head, Direction dir, Vec3d eye) {
        BlockPos base       = head.offset(dir);
        BlockPos crystalPos = base.up();
        BlockPos pistonPos  = crystalPos.offset(dir);

        boolean placeBase = needsBase(base);
        if (placeBase && !canAutoBase(base)) return null;
        if (!mc.world.getBlockState(crystalPos).isAir()) return null;

        BlockPos destination = head.up();
        Vec3d explosionPos = mc.world.getBlockState(destination).isAir()
                ? new Vec3d(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5)
                : new Vec3d(crystalPos.getX() + 0.5 - dir.getOffsetX() * 0.5,
                            crystalPos.getY(),
                            crystalPos.getZ() + 0.5 - dir.getOffsetZ() * 0.5);
        return buildSetup(target, head, dir, crystalPos, base, pistonPos, explosionPos, eye, placeBase);
    }

    private Setup buildSetup(PlayerEntity target, BlockPos head, Direction dir, BlockPos crystalPos,
                             BlockPos base, BlockPos pistonPos, Vec3d explosionPos, Vec3d eye, boolean placeBase) {
        if (!WorldUtils.isPlaceable(pistonPos)) return null;

        double placeRange = range.getValue().doubleValue();
        double rangeSq = placeRange * placeRange;
        if (eye.squaredDistanceTo(pistonPos.toCenterPos()) > rangeSq) return null;
        if (eye.squaredDistanceTo(base.toCenterPos()) > rangeSq) return null;

        Box crystalBox = new Box(explosionPos.x - 1, explosionPos.y, explosionPos.z - 1,
                                 explosionPos.x + 1, explosionPos.y + 2, explosionPos.z + 1);
        if (squaredDistanceToBox(eye, crystalBox) > BREAK_RANGE * BREAK_RANGE) return null;

        float score = exposure(target, explosionPos);
        if (score < 0) return null;

        RedstoneSpot redstone = findRedstoneSpot(pistonPos, dir, explosionPos, eye, rangeSq);
        if (redstone == null) return null;

        return new Setup(dir, pistonPos, redstone.pos, crystalPos, base, head, redstone.place, placeBase, score);
    }

    private float exposure(PlayerEntity target, Vec3d explosionPos) {
        Box box = target.getBoundingBox();
        double gapSq = squaredDistanceToBox(explosionPos, box);
        if (gapSq > MAX_BLAST_GAP * MAX_BLAST_GAP) return -1.0f;

        Vec3d from = explosionPos.add(0, 0.05, 0);
        double cx = (box.minX + box.maxX) * 0.5, cz = (box.minZ + box.maxZ) * 0.5;
        double[] heights = {box.minY + 0.1, (box.minY + box.maxY) * 0.5, box.maxY - 0.1};

        int clear = 0;
        for (double y : heights) {
            Vec3d to = new Vec3d(cx, y, cz);
            if (mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player))
                    .getType() == HitResult.Type.MISS) clear++;
        }
        if (clear < MIN_CLEAR_RAYS) return -1.0f;

        return clear * 10.0f - (float) gapSq;
    }

    private RedstoneSpot findRedstoneSpot(BlockPos pistonPos, Direction dir, Vec3d explosionPos, Vec3d eye, double rangeSq) {
        BlockPos[] spots = {
                pistonPos.up(),
                pistonPos.offset(dir),
                pistonPos.offset(dir.rotateYClockwise()),
                pistonPos.offset(dir.rotateYCounterclockwise()),
                pistonPos.down()
        };

        for (BlockPos pos : spots)
            if (mc.world.getBlockState(pos).isOf(Blocks.REDSTONE_BLOCK)) return new RedstoneSpot(pos, false);

        BlockPos fallback = null;
        for (BlockPos pos : spots) {
            if (!WorldUtils.isPlaceable(pos)) continue;
            if (eye.squaredDistanceTo(pos.toCenterPos()) > rangeSq) continue;
            if (redstoneSafe(pos, explosionPos)) return new RedstoneSpot(pos, true);
            if (fallback == null) fallback = pos;
        }
        return fallback == null ? null : new RedstoneSpot(fallback, true);
    }

    private boolean redstoneSafe(BlockPos pos, Vec3d explosionPos) {
        Vec3d to = pos.toCenterPos();
        if (explosionPos.squaredDistanceTo(to) > 36.0) return true;

        Vec3d diff = to.subtract(explosionPos);
        int steps = (int) Math.ceil(diff.length() / 0.25);
        long last = Long.MIN_VALUE;

        for (int i = 1; i < steps; i++) {
            double s = (double) i / steps;
            rayCursor.set(MathHelper.floor(explosionPos.x + diff.x * s),
                          MathHelper.floor(explosionPos.y + diff.y * s),
                          MathHelper.floor(explosionPos.z + diff.z * s));

            long key = rayCursor.asLong();
            if (key == last) continue;
            last = key;

            if (rayCursor.equals(pos)) break;
            if (mc.world.getBlockState(rayCursor).getBlock().getBlastResistance() >= BLAST_PROOF) return true;
        }
        return false;
    }

    private PlayerEntity findTarget() {
        PlayerEntity best = null;
        double bestSq = TARGET_RANGE * TARGET_RANGE;
        boolean bestTagged = false;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isDead()) continue;
            if (Anatorius.FRIEND_MANAGER.contains(player.getName().getString())) continue;
            if (isPhased(player)) continue;

            boolean tagged = Anatorius.TARGET_MANAGER.isTarget(player);
            if (bestTagged && !tagged) continue;
            double distanceSq = mc.player.squaredDistanceTo(player);
            if (distanceSq >= bestSq && (bestTagged || !tagged)) continue;
            if (distanceSq >= TARGET_RANGE * TARGET_RANGE) continue;

            bestTagged = tagged;

            bestSq = distanceSq;
            best = player;
        }
        return best;
    }

    private boolean isPhased(PlayerEntity target) {
        Box box = target.getBoundingBox().contract(0.001);

        for (int x = MathHelper.floor(box.minX); x <= MathHelper.floor(box.maxX); x++) {
            for (int y = MathHelper.floor(box.minY); y <= MathHelper.floor(box.maxY); y++) {
                for (int z = MathHelper.floor(box.minZ); z <= MathHelper.floor(box.maxZ); z++) {
                    rayCursor.set(x, y, z);
                    BlockState state = mc.world.getBlockState(rayCursor);
                    if (state.isAir()) continue;

                    VoxelShape shape = state.getCollisionShape(mc.world, rayCursor);
                    if (shape.isEmpty()) continue;
                    if (shape.getBoundingBox().offset(x, y, z).intersects(box)) return true;
                }
            }
        }
        return false;
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

    private boolean needsBase(BlockPos base) {
        BlockState state = mc.world.getBlockState(base);
        return !state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK);
    }

    private boolean canAutoBase(BlockPos base) {
        return autoBase.getValue() && slotOf(Items.OBSIDIAN) >= 0 && WorldUtils.isPlaceable(base);
    }

    private int pistonSlot() {
        int slot = slotOf(Items.PISTON);
        return slot >= 0 ? slot : slotOf(Items.STICKY_PISTON);
    }

    private int slotOf(Item item) {
        String mode = autoSwitch.getValue();
        boolean alt = mode.equalsIgnoreCase("AltSwap");
        return InventoryUtils.find(item, InventoryUtils.HOTBAR_START, alt ? InventoryUtils.INVENTORY_END : InventoryUtils.HOTBAR_END);
    }

    private Vec3d closestPointOnBox(Vec3d eye, Box box) {
        double x = MathHelper.clamp(eye.x, box.minX, box.maxX);
        double y = MathHelper.clamp(eye.y, box.minY, box.maxY);
        double z = MathHelper.clamp(eye.z, box.minZ, box.maxZ);

        if (eye.x < box.minX) x += NUDGE;
        else if (eye.x > box.maxX) x -= NUDGE;

        if (eye.z < box.minZ) z += NUDGE;
        else if (eye.z > box.maxZ) z -= NUDGE;

        return new Vec3d(x, y, z);
    }

    private static double squaredDistanceToBox(Vec3d point, Box box) {
        double dx = point.x < box.minX ? box.minX - point.x : (point.x > box.maxX ? point.x - box.maxX : 0);
        double dy = point.y < box.minY ? box.minY - point.y : (point.y > box.maxY ? point.y - box.maxY : 0);
        double dz = point.z < box.minZ ? box.minZ - point.z : (point.z > box.maxZ ? point.z - box.maxZ : 0);
        return dx * dx + dy * dy + dz * dz;
    }

    private record Setup(Direction dir, BlockPos piston, BlockPos redstone, BlockPos crystal,
                         BlockPos base, BlockPos head, boolean placeRedstone, boolean placeBase, float score) {}

    private record RedstoneSpot(BlockPos pos, boolean place) {}
}
