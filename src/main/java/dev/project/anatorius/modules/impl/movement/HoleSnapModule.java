package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.KeyboardTickEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import dev.project.anatorius.events.impl.PlayerMoveEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.HoleUtils;
import dev.project.anatorius.utils.minecraft.MovementUtils;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RegisterModule(name = "HoleSnap", description = "Pulls you toward a hole once you get close to it.", category = Module.Category.MOVEMENT)
public class HoleSnapModule extends Module {
    public final NumberSetting range   = new NumberSetting("Range",   "How close a hole has to be before it starts pulling you in.", 1.5, 0.5, 4.0);
    public final NumberSetting height  = new NumberSetting("Height",  "Max blocks a hole may be below your feet to be snapped into.", 2.0, 0.0, 6.0);
    public final NumberSetting timeout = new NumberSetting("Timeout", "Cooldown after a snap (or a failed one) before it can fire again (seconds).", 0.5, 0.0, 3.0);
    public final NumberSetting speed   = new NumberSetting("Speed",   "Speed multiplier while snapping.",                              1.0, 0.1, 3.0);

    public final ModeSetting priority = new ModeSetting("Priority", "How the target hole is chosen.", "Distance", new String[]{"Distance", "Angle"});
    public final ModeSetting pitch    = new ModeSetting("Pitch",    "Forces your pitch while you're being pulled in.", "None", new String[]{"None", "Down", "Locked"});

    public final BooleanSetting autoDisable = new BooleanSetting("AutoDisable", "Turns HoleSnap off once you land in the hole (with OnArrival: once you leave it).", true);

    public final CategorySetting shiftCategory     = new CategorySetting("Shift", "Sneaking behaviour while snapping.");
    public final BooleanSetting  shiftWhileSnapping = new BooleanSetting("WhileSnapping", "Sneaks while dropping into the hole (only once you're over it: sneaking at the edge would stop you from falling in).", new CategorySetting.Visibility(shiftCategory), false);
    public final BooleanSetting  shiftOnArrival     = new BooleanSetting("OnArrival",     "Keeps sneaking while you're in the hole.", new CategorySetting.Visibility(shiftCategory), false);

    public final CategorySetting directionalCategory = new CategorySetting("Directional", "Only consider holes roughly in front of you.");
    public final BooleanSetting  directional          = new BooleanSetting("Enabled", "Restrict targets to holes within the FOV below.", new CategorySetting.Visibility(directionalCategory), false);
    public final NumberSetting   directionalFov       = new NumberSetting("FOV",      "Max angle between your view and the hole.",        new CategorySetting.Visibility(directionalCategory), 90.0, 10.0, 180.0);

    public final BooleanSetting align      = new BooleanSetting("Align",    "Centers you on the hole block once you're inside.", true);
    public final BooleanSetting onlyWide   = new BooleanSetting("OnlyWide", "Only targets double/quad holes, ignoring plain 1x1 holes.", false);
    public final ModeSetting    alignPitch = new ModeSetting("AlignPitch", "Pitch to snap to once you're inside the hole.", "Any", new String[]{"Any", "Down", "Up", "Straight"});

    public final BooleanSetting doubleHoles = new BooleanSetting("DoubleHoles", "Whether or not to look for double holes.", true);
    public final BooleanSetting quadHoles   = new BooleanSetting("QuadHoles",   "Whether or not to look for quad holes.",   true);

    private static final double CENTER_EPS = 0.03;
    private static final int MAX_ALIGN_TICKS = 6;
    private static final int MAX_STALL_TICKS = 8;
    private static final long BLACKLIST_MS = 3000L;

    private final Timer cooldownTimer = new Timer();
    private final Map<BlockPos, Long> blacklist = new HashMap<>();

    private HoleUtils.Hole hole = null;
    private BlockPos holeOrigin = null;
    private BlockPos holeAnchor = null;
    private boolean snapping = false;
    private boolean pitchLocked = false;
    private float lockedPitch = 0f;

    private double closestDistance = Double.MAX_VALUE;
    private int stallTicks = 0;
    private int alignTicks = 0;
    private boolean arrived = false;
    private boolean sneakNextTick = false;
    private volatile long lastSetbackAt;

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) lastSetbackAt = System.currentTimeMillis();
    }

    private boolean paused() {
        if (System.currentTimeMillis() - lastSetbackAt < 250L) return true;
        if (mc.player.isSpectator() || mc.player.isGliding()) return true;
        return insideBlocks();
    }

    private boolean insideBlocks() {
        Box box = mc.player.getBoundingBox().contract(0.01, 0.0, 0.01).withMinY(mc.player.getY() + 0.01);
        return mc.world.getBlockCollisions(mc.player, box).iterator().hasNext();
    }

    private boolean reachable(BlockPos cell) {
        Vec3d feet = mc.player.getEntityPos().add(0.0, 0.01, 0.0);
        Vec3d head = feet.add(0.0, mc.player.getHeight(), 0.0);
        Vec3d top = new Vec3d(cell.getX() + 0.5, cell.getY() + 1.0, cell.getZ() + 0.5);
        return clear(feet, new Vec3d(top.x, feet.y, top.z))
                && clear(head, new Vec3d(top.x, head.y, top.z))
                && clear(top, new Vec3d(top.x, head.y, top.z));
    }

    private boolean clear(Vec3d from, Vec3d to) {
        if (from.squaredDistanceTo(to) < 1.0E-6) return true;
        return mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    @Override
    public void onEnable() {
        resetTarget();
        snapping = false;
        pitchLocked = false;
        arrived = false;
        sneakNextTick = false;
        blacklist.clear();
        cooldownTimer.setMs((long) (timeout.getValue().doubleValue() * 1000));
    }

    @Override
    public void onDisable() {
        stopSnapping();
        arrived = false;
        sneakNextTick = false;
    }

    @Override
    public String getMetaData() {
        if (getNull()) return "";
        return snapping ? "Snapping" : arrived ? "Holed" : "Idle";
    }

    public Box getHoleBox() {
        return hole != null ? hole.box() : null;
    }

    @SubscribeEvent
    public void onKeyboardTick(KeyboardTickEvent event) {
        if (sneakNextTick) event.setForceSneak(true);
    }

    @SubscribeEvent
    public void onPlayerMove(PlayerMoveEvent event) {
        if (getNull()) return;
        sneakNextTick = false;

        if (Anatorius.MODULE_MANAGER.getModule(SpeedModule.class).isToggled()) {
            if (snapping) stopSnapping();
            return;
        }

        if (paused()) {
            if (snapping) stopSnapping();
            return;
        }

        BlockPos inHole = cellOfHoleYouAreIn();
        if (inHole != null) {
            if (snapping && align.getValue() && alignTicks < MAX_ALIGN_TICKS && !centeredOn(inHole)) {
                alignTicks++;
                MovementUtils.moveTowards(event, Vec3d.ofBottomCenter(inHole), MovementUtils.getPotionSpeed(MovementUtils.DEFAULT_SPEED));
                return;
            }
            if (snapping) onArrive();
            if (arrived && shiftOnArrival.getValue()) sneakNextTick = true;
            cooldownTimer.reset();
            return;
        }

        if (arrived) {
            arrived = false;
            if (autoDisable.getValue()) {
                setToggled(false);
                return;
            }
        }

        if (!cooldownTimer.hasTimeElapsed((long) (timeout.getValue().doubleValue() * 1000))) return;

        if (snapping && !targetStillValid()) {
            fail();
            if (!cooldownTimer.hasTimeElapsed((long) (timeout.getValue().doubleValue() * 1000))) return;
        }

        if (!snapping) {
            Candidate best = findBestHole();
            if (best == null) return;
            hole = best.hole();
            holeOrigin = originOf(best.hole());
            holeAnchor = best.anchor();
            snapping = true;
            closestDistance = Double.MAX_VALUE;
            stallTicks = 0;
            alignTicks = 0;
            if ("Locked".equals(pitch.getValue())) {
                lockedPitch = mc.player.getPitch();
                pitchLocked = true;
            }
        } else {
            holeAnchor = closestCell(hole, holeAnchor);
        }

        double targetX = holeAnchor.getX() + 0.5;
        double targetZ = holeAnchor.getZ() + 0.5;
        double distance = Math.sqrt(mc.player.squaredDistanceTo(targetX, mc.player.getY(), targetZ));

        if (distance < closestDistance - 0.01 || distance < 0.1) {
            closestDistance = Math.min(closestDistance, distance);
            stallTicks = 0;
        } else if (++stallTicks > MAX_STALL_TICKS) {
            fail();
            return;
        }

        if (shiftWhileSnapping.getValue() && !mc.player.isOnGround() && isOver(hole.box())) sneakNextTick = true;
        applyPitch();

        double appliedSpeed = MovementUtils.DEFAULT_SPEED * speed.getValue().doubleValue();
        MovementUtils.moveTowards(event, new Vec3d(targetX, mc.player.getY(), targetZ), MovementUtils.getPotionSpeed(appliedSpeed));
    }

    private void onArrive() {
        String ap = alignPitch.getValue();
        if ("Down".equals(ap)) mc.player.setPitch(90f);
        else if ("Up".equals(ap)) mc.player.setPitch(-90f);
        else if ("Straight".equals(ap)) mc.player.setPitch(0f);

        stopSnapping();
        arrived = true;

        if (autoDisable.getValue() && !shiftOnArrival.getValue()) {
            arrived = false;
            setToggled(false);
        }
    }

    private void fail() {
        if (holeOrigin != null) blacklist.put(holeOrigin, System.currentTimeMillis() + BLACKLIST_MS);
        stopSnapping();
    }

    private void stopSnapping() {
        snapping = false;
        pitchLocked = false;
        resetTarget();
        cooldownTimer.reset();
    }

    private void resetTarget() {
        hole = null;
        holeOrigin = null;
        holeAnchor = null;
        closestDistance = Double.MAX_VALUE;
        stallTicks = 0;
        alignTicks = 0;
    }

    private boolean targetStillValid() {
        if (hole == null || holeOrigin == null) return false;
        HoleUtils.Hole current = holeAtOrigin(holeOrigin, hole.type());
        if (current == null) return false;
        hole = current;

        double leash = range.getValue().doubleValue() + 3.0;
        Vec3d center = hole.box().getCenter();
        if (mc.player.squaredDistanceTo(center.x, mc.player.getY(), center.z) > leash * leash) return false;
        if (!hasClearDrop(hole)) return false;
        for (BlockPos cell : cellsOf(hole)) if (hasClearColumn(cell) && reachable(cell)) return true;
        return false;
    }

    private HoleUtils.Hole holeAtOrigin(BlockPos origin, HoleUtils.HoleType type) {
        return switch (type) {
            case SINGLE -> HoleUtils.getSingleHole(origin, 1);
            case DOUBLE -> HoleUtils.getDoubleHole(origin, 1);
            case QUAD -> HoleUtils.getQuadHole(origin, 1);
        };
    }

    private static BlockPos originOf(HoleUtils.Hole hole) {
        Box box = hole.box();
        return BlockPos.ofFloored(box.minX, box.minY, box.minZ);
    }

    private static List<BlockPos> cellsOf(HoleUtils.Hole hole) {
        Box box = hole.box();
        List<BlockPos> cells = new ArrayList<>();
        int y = MathHelper.floor(box.minY);
        for (int x = MathHelper.floor(box.minX); x < MathHelper.floor(box.maxX); x++) {
            for (int z = MathHelper.floor(box.minZ); z < MathHelper.floor(box.maxZ); z++) {
                cells.add(new BlockPos(x, y, z));
            }
        }
        return cells;
    }

    private BlockPos closestCell(HoleUtils.Hole hole, BlockPos fallback) {
        BlockPos best = fallback;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos cell : cellsOf(hole)) {
            if (!hasClearColumn(cell) || !reachable(cell)) continue;
            double distance = mc.player.squaredDistanceTo(cell.getX() + 0.5, mc.player.getY(), cell.getZ() + 0.5);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = cell;
            }
        }
        return best;
    }

    private boolean hasClearDrop(HoleUtils.Hole hole) {
        for (BlockPos cell : cellsOf(hole)) {
            if (hasClearColumn(cell)) return true;
        }
        return false;
    }

    private boolean hasClearColumn(BlockPos cell) {
        int top = Math.max(mc.player.getBlockY(), cell.getY());
        for (int y = top; y >= cell.getY(); y--) {
            BlockPos pos = new BlockPos(cell.getX(), y, cell.getZ());
            if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        }
        return true;
    }

    private boolean centeredOn(BlockPos cell) {
        return Math.abs(mc.player.getX() - (cell.getX() + 0.5)) < CENTER_EPS
                && Math.abs(mc.player.getZ() - (cell.getZ() + 0.5)) < CENTER_EPS;
    }

    private boolean isOver(Box box) {
        return mc.player.getX() > box.minX && mc.player.getX() < box.maxX
                && mc.player.getZ() > box.minZ && mc.player.getZ() < box.maxZ;
    }

    private BlockPos cellOfHoleYouAreIn() {
        BlockPos feet = mc.player.getBlockPos();
        double py = mc.player.getY();
        if (py - feet.getY() > 0.2) return null;

        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                BlockPos origin = feet.add(dx, 0, dz);
                HoleUtils.Hole found = null;
                if (dx == 0 && dz == 0) found = HoleUtils.getSingleHole(origin, 1);
                if (found == null) found = HoleUtils.getDoubleHole(origin, 1);
                if (found == null) found = HoleUtils.getQuadHole(origin, 1);
                if (found != null && cellsOf(found).contains(feet)) return feet;
            }
        }
        return null;
    }

    private void applyPitch() {
        String mode = pitch.getValue();
        if ("Down".equals(mode)) {
            mc.player.setPitch(90f);
        } else if ("Locked".equals(mode) && pitchLocked) {
            mc.player.setPitch(lockedPitch);
        }
    }

    private record Candidate(HoleUtils.Hole hole, BlockPos anchor) {}

    private Candidate findBestHole() {
        long now = System.currentTimeMillis();
        blacklist.values().removeIf(until -> until <= now);

        double searchRangeSq = range.getValue().doubleValue() * range.getValue().doubleValue();
        double maxDrop = height.getValue().doubleValue();
        List<Candidate> candidates = new ArrayList<>();

        for (Candidate c : getHoles()) {
            if (blacklist.containsKey(originOf(c.hole()))) continue;
            double cx = c.anchor().getX() + 0.5;
            double cz = c.anchor().getZ() + 0.5;
            if (mc.player.squaredDistanceTo(cx, mc.player.getY(), cz) > searchRangeSq) continue;
            if (mc.player.getY() - c.hole().box().minY > maxDrop + 0.01) continue;
            if (directional.getValue() && !withinFov(c.anchor())) continue;
            if (!hasClearColumn(c.anchor())) continue;
            if (!reachable(c.anchor())) continue;
            candidates.add(c);
        }
        if (candidates.isEmpty()) return null;

        if ("Angle".equals(priority.getValue())) {
            candidates.sort(Comparator.comparingDouble(c -> angleTo(c.anchor())));
        } else {
            candidates.sort(Comparator.comparingDouble(c -> mc.player.squaredDistanceTo(
                    c.anchor().getX() + 0.5, c.hole().box().minY, c.anchor().getZ() + 0.5)));
        }
        return candidates.get(0);
    }

    private boolean withinFov(BlockPos anchor) {
        return angleTo(anchor) <= directionalFov.getValue().doubleValue() / 2.0;
    }

    private double angleTo(BlockPos anchor) {
        Vec3d toHole = new Vec3d(anchor.getX() + 0.5 - mc.player.getX(), 0, anchor.getZ() + 0.5 - mc.player.getZ());
        if (toHole.lengthSquared() < 1.0E-6) return 0.0;
        double targetYaw = Math.toDegrees(Math.atan2(-toHole.x, toHole.z));
        return Math.abs(MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()));
    }

    private List<Candidate> getHoles() {
        List<Candidate> holes = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        double searchRadius = range.getValue().doubleValue() + 3.0;

        for (int i = 0; i < Anatorius.WORLD_MANAGER.getRadius(searchRadius); i++) {
            BlockPos position = mc.player.getBlockPos().add(Anatorius.WORLD_MANAGER.getOffset(i));
            if (position.getY() >= mc.player.getY() - 0.5) continue;
            if (seen.contains(position)) continue;

            HoleUtils.Hole found = null;
            if (!onlyWide.getValue()) found = HoleUtils.getSingleHole(position, 1);
            if (found == null && doubleHoles.getValue()) found = HoleUtils.getDoubleHole(position, 1);
            if (found == null && quadHoles.getValue()) found = HoleUtils.getQuadHole(position, 1);
            if (found == null) continue;

            for (BlockPos cell : cellsOf(found)) {
                if (seen.add(cell)) holes.add(new Candidate(found, cell));
            }
        }
        return holes;
    }
}
