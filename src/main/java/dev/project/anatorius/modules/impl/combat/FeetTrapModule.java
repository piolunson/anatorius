package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PlayerJumpEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.utils.rotations.RotationUtils;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.core.AntiCheatModule;
import dev.project.anatorius.modules.impl.exploit.HitboxDesyncModule;
import dev.project.anatorius.modules.impl.movement.SpeedModule;
import dev.project.anatorius.modules.impl.movement.StepModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.HoleUtils;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.PositionUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.system.Timer;
import dev.project.anatorius.mixins.accessors.WorldRendererAccessor;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

import java.util.*;

@RegisterModule(
        name = "FeetTrap",
        description = "Automatically places blocks at your feet to prevent crystal damage.",
        category = Module.Category.COMBAT
)
public class FeetTrapModule extends Module {
    public final ModeSetting autoSwitch = new ModeSetting("Switch", "Auto-switch mode for selecting items.", "Silent", InventoryUtils.SWITCH_MODES);
    public final ModeSetting timing = new ModeSetting("Timing", "Timing mode for replacing broken surround blocks.", "Sequential", new String[]{"Vanilla", "Sequential"});
    public final BooleanSetting instant = new BooleanSetting("Instant", "Places the entire surround in a single tick (Mio-style), instead of throttling to a few blocks per packet window. Much faster; strict anticheats may flag the burst.", true);
    public final NumberSetting blocksPerTick = new NumberSetting("BlocksPerTick", "Max blocks placed per tick when Instant is off. 0 = unlimited.", new BooleanSetting.Visibility(instant, false), 4, 0, 10);
    public final NumberSetting delay = new NumberSetting("Delay", "Delay in ms to wait between placements.", 0.0, 0.0, 1000.0);
    public final NumberSetting range = new NumberSetting("Range", "Max placement range.", 5.0, 0.0, 12.0);
    public final BooleanSetting await = new BooleanSetting("Await", "Wait for block registration before placing on top.", false);
    public final ModeSetting rotate = new ModeSetting("Rotate", "Rotation mode during placement.", "Packet", new String[]{"None", "Packet", "Normal"});
    public final BooleanSetting strictDirection = new BooleanSetting("StrictDirection", "Only use directions that face the player.", false);
    public final BooleanSetting airPlace = new BooleanSetting("AirPlace", "Place blocks without requiring a valid direction.", false);
    public final BooleanSetting crystalDestruction = new BooleanSetting("Attack", "Breaks the crystals in the way before placing.", true);
    public final BooleanSetting center = new BooleanSetting("Center", "Center the player on their block when surrounding.", false);
    public final BooleanSetting floor = new BooleanSetting("Floor", "Also place blocks under your feet.", true);
    public final BooleanSetting extension = new BooleanSetting("Extension", "Extend surround when entities block placement.", true);
    public final BooleanSetting firework = new BooleanSetting("Firework", "When a surround gap is covered (a block above it) and under attack — items flying out of it or it is almost mined — launch a firework rocket into the gap instead of obby, to knock the attacker away.", false);
    public final BooleanSetting whileEating = new BooleanSetting("WhileEating", "Continue placing while eating.", true);
    public final CategorySetting autoDisableCategory = new CategorySetting("AutoDisable", "Contains all of the settings relating to automatic disabling.");
    public final BooleanSetting selfDisable = new BooleanSetting("SelfDisable", "Disable once surround is complete.", new CategorySetting.Visibility(autoDisableCategory), false);
    public final BooleanSetting jumpDisable = new BooleanSetting("JumpDisable", "Disable when Y level changes.", new CategorySetting.Visibility(autoDisableCategory), true);
    public final BooleanSetting itemDisable = new BooleanSetting("ItemDisable", "Disable when no placeable items remain.", new CategorySetting.Visibility(autoDisableCategory), true);
    public final BooleanSetting stepToggle = new BooleanSetting("StepToggle", "Toggle Step module off when surrounding.", false);
    public final BooleanSetting speedToggle = new BooleanSetting("SpeedToggle", "Toggle Speed module off when surrounding.", false);
    public final BooleanSetting render = new BooleanSetting("Render", "Render the placement position.", true);

    private Set<BlockPos> targetPositions = new HashSet<>();
    private Vec3d cachedExactPosition = null;
    private final Map<BlockPos, Integer> pendingPlacements = new HashMap<>();
    private final Map<BlockPos, Long> recentPlacements = new HashMap<>();
    private final Timer delayTimer = new Timer();
    private int placedThisTick = 0;
    private BlockPos lastFloorPosition = null;
    private long packetWindowStart = 0;
    private int packetWindowCount = 0;

    private final Map<BlockPos, Deque<Long>> itemSpawnTimes = new HashMap<>();
    private final Map<BlockPos, Long> fireworkPlaced = new HashMap<>();

    public boolean hasUnfilledPositions() {
        if (mc.player == null || mc.world == null) return false;
        boolean excludeSelf = isHitboxDesyncExcludeSelf();
        double rangeSq = MathHelper.square(range.getValue().doubleValue());
        for (BlockPos pos : getTargetPositions()) {
            if (mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > rangeSq) continue;
            if (WorldUtils.isPlaceable(pos, excludeSelf)) return true;
        }
        return false;
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) return;
        lastFloorPosition = PositionUtils.getFlooredPosition(mc.player);
        cachedExactPosition = null;
        placedThisTick = 0;
        delayTimer.reset();
        if (stepToggle.getValue()) disableIfOn(StepModule.class);
        if (speedToggle.getValue()) disableIfOn(SpeedModule.class);
        if (center.getValue()) mc.player.setPosition(lastFloorPosition.getX() + 0.5, lastFloorPosition.getY(), lastFloorPosition.getZ() + 0.5);
    }

    @SubscribeEvent
    public void onDisable() {
        targetPositions.clear();
        pendingPlacements.clear();
        recentPlacements.clear();
        cachedExactPosition = null;
        lastFloorPosition = null;
        placedThisTick = 0;
        missingBlockAttempts = 0;
        packetWindowStart = 0;
        packetWindowCount = 0;
        itemSpawnTimes.clear();
        fireworkPlaced.clear();
    }

    @SubscribeEvent
    public void onPlayerJump(PlayerJumpEvent event) {
        if (jumpDisable.getValue()) setToggled(false);
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (!Anatorius.SERVER_MANAGER.getSetbackTimer().hasTimeElapsed(150L)) return;

        long now = System.currentTimeMillis();
        if (now - packetWindowStart >= 100L) {
            packetWindowStart = now;
            packetWindowCount = 0;
        }

        if (jumpDisable.getValue()) {
            BlockPos currentFloor = PositionUtils.getFlooredPosition(mc.player);
            boolean steppingOrSpeeding = Anatorius.MODULE_MANAGER.getModule(StepModule.class).isToggled() || Anatorius.MODULE_MANAGER.getModule(SpeedModule.class).isToggled();
            if (mc.player.fallDistance > 2.0f || (steppingOrSpeeding && lastFloorPosition != null && lastFloorPosition.getY() != currentFloor.getY())) {
                setToggled(false);
                return;
            }
            lastFloorPosition = currentFloor;
        }

        if (!whileEating.getValue() && mc.player.isUsingItem()) return;
        if (isSpeedMineSwapPending()) return;

        placedThisTick = 0;
        if (firework.getValue()) processFireworks();
        if (delay.getValue().doubleValue() > 0 && !delayTimer.hasTimeElapsed(delay.getValue())) return;

        if (autoSwitch.getValue().equalsIgnoreCase("None") && !(mc.player.getMainHandStack().getItem() instanceof BlockItem)) {
            handleNoItems();
            return;
        }

        int slot = InventoryUtils.findHardestObsidian(0, 8);
        if (slot == -1) {
            handleNoItems();
            return;
        }
        missingBlockAttempts = 0;

        refreshTargetCache();
        boolean excludeSelf = isHitboxDesyncExcludeSelf();
        List<BlockPos> placeable = buildPlaceableList(excludeSelf);

        if (placeable.isEmpty()) {
            if (selfDisable.getValue() && pendingPlacements.isEmpty()) setToggled(false);
            return;
        }

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        boolean switched = !autoSwitch.getValue().equalsIgnoreCase("None") && slot != previousSlot;
        if (switched) InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);

        boolean doRotate = !rotate.getValue().equalsIgnoreCase("None");
        boolean packetRotate = rotate.getValue().equalsIgnoreCase("Packet");
        int rotPriority = Anatorius.ROTATION_MANAGER.getModulePriority(this);
        int startPlaced = placedThisTick;

        Iterator<Map.Entry<BlockPos, Integer>> pendingIter = pendingPlacements.entrySet().iterator();
        while (pendingIter.hasNext()) {
            if (!canPlaceBlock()) break;
            Map.Entry<BlockPos, Integer> entry = pendingIter.next();
            BlockPos pendingPos = entry.getKey();
            int age = entry.getValue() + 1;

            if (age > 5 || !targetPositions.contains(pendingPos) || !WorldUtils.isPlaceable(pendingPos, excludeSelf)) {
                pendingIter.remove();
                continue;
            }
            entry.setValue(age);

            Direction dir = WorldUtils.getDirection(pendingPos, null, strictDirection.getValue());
            if (dir == null && !airPlace.getValue()) continue;

            if (doRotate && dir != null) {
                Direction chosen = prepareRotation(pendingPos, dir, packetRotate, rotPriority);
                if (chosen == null) continue;
                dir = chosen;
            }

            if (isRecentlyPlaced(pendingPos)) continue;
            if (WorldUtils.placeBlock(pendingPos, dir, Hand.MAIN_HAND, false, crystalDestruction.getValue(), render.getValue())) {
                consumeBlockTick();
                markPlaced(pendingPos);
                if (!WorldUtils.isPlaceable(pendingPos, excludeSelf)) pendingIter.remove();
            }
        }

        for (BlockPos pos : placeable) {
            if (!canPlaceBlock()) break;
            if (pendingPlacements.containsKey(pos)) continue;

            Direction dir = WorldUtils.getDirection(pos, null, strictDirection.getValue());
            if (dir == null) {
                BlockPos support = pos.add(0, -1, 0);
                if (WorldUtils.isPlaceable(support, excludeSelf)) {
                    Direction supportDir = WorldUtils.getDirection(support, null, strictDirection.getValue());
                    if (supportDir != null) {
                        if (doRotate) {
                            Direction chosen = prepareRotation(support, supportDir, packetRotate, rotPriority);
                            if (chosen == null) continue;
                            supportDir = chosen;
                        }
                        if (isRecentlyPlaced(support)) continue;
                        if (WorldUtils.placeBlock(support, supportDir, Hand.MAIN_HAND, false, crystalDestruction.getValue(), render.getValue())) {
                            consumeBlockTick();
                            markPlaced(support);
                        }
                    }
                }

                if (!canPlaceBlock()) break;
                if (await.getValue()) continue;
                dir = WorldUtils.getDirection(pos, null, strictDirection.getValue());
                if (dir == null && !airPlace.getValue()) continue;
            }

            if (doRotate && dir != null) {
                Direction chosen = prepareRotation(pos, dir, packetRotate, rotPriority);
                if (chosen == null) continue;
                dir = chosen;
            }

            if (isRecentlyPlaced(pos)) continue;
            if (WorldUtils.placeBlock(pos, dir, Hand.MAIN_HAND, false, crystalDestruction.getValue(), render.getValue())) {
                consumeBlockTick();
                markPlaced(pos);
                if (WorldUtils.isPlaceable(pos, excludeSelf)) pendingPlacements.putIfAbsent(pos, 0);
                else pendingPlacements.remove(pos);
            }
        }

        if (switched) InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
        if (placedThisTick > startPlaced) delayTimer.reset();
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (firework.getValue() && event.getPacket() instanceof EntitySpawnS2CPacket itemPacket
                && itemPacket.getEntityType().equals(EntityType.ITEM)) {
            long now = System.currentTimeMillis();
            BlockPos pos = BlockPos.ofFloored(itemPacket.getX(), itemPacket.getY(), itemPacket.getZ());
            mc.execute(() -> {
                Deque<Long> times = itemSpawnTimes.computeIfAbsent(pos, k -> new ArrayDeque<>());
                times.addLast(now);
                pruneTimes(times, now);
            });
        }

        if (!timing.getValue().equalsIgnoreCase("Sequential")) return;

        if (event.getPacket() instanceof net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket blockPacket) {
            if (!blockPacket.getState().isReplaceable()) return;
            BlockPos broken = blockPacket.getPos().toImmutable();
            mc.execute(() -> tryReplaceBroken(broken));
            return;
        }

        if (event.getPacket() instanceof net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket deltaPacket) {
            List<BlockPos> broken = new ArrayList<>();
            deltaPacket.visitUpdates((pos, state) -> {
                if (state.isReplaceable()) broken.add(pos.toImmutable());
            });
            if (broken.isEmpty()) return;
            mc.execute(() -> {
                for (BlockPos p : broken) tryReplaceBroken(p);
            });
            return;
        }

        if (!(event.getPacket() instanceof EntitySpawnS2CPacket packet)) return;
        if (!packet.getEntityType().equals(EntityType.END_CRYSTAL)) return;

        mc.execute(() -> {
            if (mc.player == null || mc.world == null) return;
            if (!whileEating.getValue() && mc.player.isUsingItem()) return;
            if (isSpeedMineSwapPending()) return;

            Set<BlockPos> targets = targetPositions.isEmpty() ? HoleUtils.getFeetPositions(mc.player, extension.getValue(), floor.getValue(), false) : targetPositions;
            EndCrystalEntity crystal = new EndCrystalEntity(mc.world, packet.getX(), packet.getY(), packet.getZ());
            crystal.setId(packet.getEntityId());
            Box crystalBox = crystal.getBoundingBox();

            List<BlockPos> covered = new ArrayList<>();
            for (BlockPos pos : targets) {
                if (new Box(pos).intersects(crystalBox) && !isRecentlyPlaced(pos)) covered.add(pos);
            }
            if (covered.isEmpty()) return;

            boolean altSwap = autoSwitch.getValue().equalsIgnoreCase("AltSwap");
            int slot = InventoryUtils.findHardestBlock(0, altSwap ? 35 : 8);
            if (slot == -1) return;

            boolean doRotate = !rotate.getValue().equalsIgnoreCase("None");
            boolean packetRotate = rotate.getValue().equalsIgnoreCase("Packet");
            int rotPriority = Anatorius.ROTATION_MANAGER.getModulePriority(this);

            int previousSlot = mc.player.getInventory().getSelectedSlot();
            InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);

            if (doRotate) {
                float[] rot = RotationUtils.getRotations(packet.getX(), packet.getY(), packet.getZ(), false);
                applyRotationRaw(rot, packetRotate, rotPriority);
            }
            mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
            mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

            boolean excludeSelf = isHitboxDesyncExcludeSelf();
            for (BlockPos pos : covered) {
                Direction dir = WorldUtils.getDirection(pos, strictDirection.getValue());
                if (dir == null && !airPlace.getValue()) continue;
                if (WorldUtils.placeBlock(pos, dir, Hand.MAIN_HAND, false, false, render.getValue())) {
                    consumeBlockTick();
                    markPlaced(pos);
                    if (WorldUtils.isPlaceable(pos, excludeSelf)) pendingPlacements.putIfAbsent(pos, 0);
                    else pendingPlacements.remove(pos);
                }
            }

            InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
        });
    }

    private void tryReplaceBroken(BlockPos pos) {
        if (mc.player == null || mc.world == null) return;
        if (!timing.getValue().equalsIgnoreCase("Sequential")) return;
        if (!whileEating.getValue() && mc.player.isUsingItem()) return;
        if (isSpeedMineSwapPending()) return;
        if (isRecentlyPlaced(pos)) return;

        Set<BlockPos> targets = targetPositions.isEmpty()
                ? HoleUtils.getFeetPositions(mc.player, extension.getValue(), floor.getValue(), false)
                : targetPositions;
        if (!targets.contains(pos)) return;

        boolean altSwap = autoSwitch.getValue().equalsIgnoreCase("AltSwap");
        int slot = InventoryUtils.findHardestBlock(0, altSwap ? 35 : 8);
        if (slot == -1) return;

        Direction dir = WorldUtils.getDirection(pos, strictDirection.getValue());
        if (dir == null && !airPlace.getValue()) return;

        boolean doRotate = !rotate.getValue().equalsIgnoreCase("None");
        boolean packetRotate = rotate.getValue().equalsIgnoreCase("Packet");
        int rotPriority = Anatorius.ROTATION_MANAGER.getModulePriority(this);
        if (doRotate && dir != null) {
            Direction chosen = prepareRotation(pos, dir, packetRotate, rotPriority);
            if (chosen == null) return;
            dir = chosen;
        }

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);
        if (WorldUtils.placeBlock(pos, dir, Hand.MAIN_HAND, false, false, render.getValue())) {
            consumeBlockTick();
            markPlaced(pos);
            boolean excludeSelf = isHitboxDesyncExcludeSelf();
            if (WorldUtils.isPlaceable(pos, excludeSelf)) pendingPlacements.putIfAbsent(pos, 0);
            else pendingPlacements.remove(pos);
        }
        InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
    }

    @Override
    public String getMetaData() {
        if (mc.player == null || mc.world == null) return "0";
        boolean excludeSelf = isHitboxDesyncExcludeSelf();
        int unfilled = 0;
        for (BlockPos pos : targetPositions) {
            if (WorldUtils.isPlaceable(pos, excludeSelf)) unfilled++;
        }
        return String.valueOf(unfilled);
    }

    private Set<BlockPos> getTargetPositions() {
        if (mc.player == null) return Collections.emptySet();
        refreshTargetCache();
        return targetPositions;
    }

    private void refreshTargetCache() {
        if (mc.player == null) return;
        Vec3d currentPos = mc.player.getEntityPos();
        if (cachedExactPosition == null || !currentPos.equals(cachedExactPosition) || targetPositions.isEmpty()) {
            Set<BlockPos> fresh = HoleUtils.getFeetPositions(mc.player, extension.getValue(), floor.getValue(), false);
            pendingPlacements.keySet().removeIf(pos -> !fresh.contains(pos));
            targetPositions = fresh;
            cachedExactPosition = currentPos;
        }
    }

    private List<BlockPos> buildPlaceableList(boolean excludeSelf) {
        double rangeSq = MathHelper.square(range.getValue().doubleValue());
        boolean fw = firework.getValue();
        Set<BlockPos> fireworkEntities = fw ? getFireworkEntityPositions() : null;
        long now = System.currentTimeMillis();
        List<BlockPos> result = new ArrayList<>(targetPositions.size());
        for (BlockPos pos : targetPositions) {
            if (mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > rangeSq) continue;
            if (!WorldUtils.isPlaceable(pos, excludeSelf)) continue;
            if (fw && isFireworkTracked(pos, fireworkEntities, now)) continue;
            result.add(pos);
        }
        result.sort(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
        return result;
    }

    private boolean canPlaceBlock() {
        if (instant.getValue()) {
            if (isGrimMode() && isMoving()) return placedThisTick < 1;
            return true;
        }

        if (isGrimMode() && isMoving() && placedThisTick >= 1) return false;

        int limit = blocksPerTick.getValue().intValue();
        if (limit > 0 && placedThisTick >= limit) return false;

        int maxPackets = isGrimMode() ? 4 : 8;
        if (packetWindowCount >= maxPackets) return false;

        return true;
    }

    private boolean isMoving() {
        if (mc.player == null) return false;
        double vx = mc.player.getVelocity().x;
        double vz = mc.player.getVelocity().z;
        return (vx * vx + vz * vz) > 0.000025;
    }

    private void consumeBlockTick() {
        placedThisTick++;
        packetWindowCount++;
    }

    private Direction prepareRotation(BlockPos pos, Direction preferred, boolean packetOnly, int priority) {
        if (preferred == null) return null;

        boolean validate = strictDirection.getValue() || isGrimMode();

        float[] applied = computeRotation(pos, preferred, priority, validate);
        Direction chosen = preferred;

        if (applied == null && validate) {
            for (Direction d : Direction.values()) {
                if (d == preferred) continue;
                if (mc.world.getBlockState(pos.offset(d)).isReplaceable()) continue;
                float[] alt = computeRotation(pos, d, priority, true);
                if (alt != null) {
                    applied = alt;
                    chosen = d;
                    break;
                }
            }
        }

        if (applied == null) return validate ? null : preferred;

        sendRotation(applied, packetOnly, priority);
        return chosen;
    }

    private float[] computeRotation(BlockPos pos, Direction dir, int priority, boolean validate) {
        double hitX = pos.getX() + 0.5 + dir.getOffsetX() * 0.5;
        double hitY = pos.getY() + 0.5 + dir.getOffsetY() * 0.5;
        double hitZ = pos.getZ() + 0.5 + dir.getOffsetZ() * 0.5;
        float[] raw = RotationUtils.getRotations(hitX, hitY, hitZ, false);

        var active = Anatorius.ROTATION_MANAGER.getRotation();
        boolean weWillRotate = active == null || active.getPriority() <= priority;

        float yaw, pitch;
        if (weWillRotate) {
            float[] snapped = RotationUtils.applyGCD(raw[0], raw[1],
                    Anatorius.ROTATION_MANAGER.getServerYaw(), Anatorius.ROTATION_MANAGER.getServerPitch());
            yaw = snapped[0];
            pitch = snapped[1];
        } else {
            yaw = Anatorius.ROTATION_MANAGER.getServerYaw();
            pitch = Anatorius.ROTATION_MANAGER.getServerPitch();
        }

        if (validate && !WorldUtils.canSeeFace(yaw, pitch, pos, dir)) return null;
        return new float[]{yaw, pitch, weWillRotate ? 1f : 0f};
    }

    private void sendRotation(float[] applied, boolean packetOnly, int priority) {
        if (applied[2] == 0f) return;

        float[] rot = {applied[0], applied[1]};
        if (packetOnly) {
            Anatorius.ROTATION_MANAGER.packetRotate(rot, priority);
        } else {
            Anatorius.ROTATION_MANAGER.rotate(rot, priority);
            Anatorius.ROTATION_MANAGER.packetRotate(rot, priority);
        }
    }

    private void applyRotationRaw(float[] rotations, boolean packetOnly, int priority) {
        var active = Anatorius.ROTATION_MANAGER.getRotation();
        if (active != null && active.getPriority() > priority) return;

        float[] eff = RotationUtils.applyGCD(rotations[0], rotations[1],
                Anatorius.ROTATION_MANAGER.getServerYaw(), Anatorius.ROTATION_MANAGER.getServerPitch());
        if (packetOnly) {
            Anatorius.ROTATION_MANAGER.packetRotate(eff, priority);
        } else {
            Anatorius.ROTATION_MANAGER.rotate(eff[0], eff[1], priority);
            Anatorius.ROTATION_MANAGER.packetRotate(eff, priority);
        }
    }

    private boolean isHitboxDesyncExcludeSelf() {
        HitboxDesyncModule m = Anatorius.MODULE_MANAGER.getModule(HitboxDesyncModule.class);
        return m != null && m.isToggled() && !m.close.getValue();
    }

    private boolean isSpeedMineSwapPending() {
        dev.project.anatorius.modules.impl.player.SpeedMineModule speedMine = Anatorius.MODULE_MANAGER.getModule(dev.project.anatorius.modules.impl.player.SpeedMineModule.class);
        return speedMine != null && speedMine.isSwapPending();
    }

    private boolean isGrimMode() {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        return antiCheat != null && antiCheat.movementSync.getValue();
    }

    private boolean isRecentlyPlaced(BlockPos pos) {
        Long lastPlacement = recentPlacements.get(pos);
        if (lastPlacement == null) return false;
        return System.currentTimeMillis() - lastPlacement < 50;
    }

    private void markPlaced(BlockPos pos) {
        recentPlacements.put(pos, System.currentTimeMillis());
        recentPlacements.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > 1000);
    }

    private void processFireworks() {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isSpectator() || !mc.player.isAlive() || mc.player.isUsingItem()) return;

        long now = System.currentTimeMillis();
        pruneFireworkState(now);

        BlockPos pos = selectFireworkPosition(now);
        if (pos == null) return;

        Direction face = WorldUtils.getDirection(pos, strictDirection.getValue());
        if (face == null && !airPlace.getValue()) return;

        boolean offhand = mc.player.getOffHandStack().getItem() == Items.FIREWORK_ROCKET;
        int slot = InventoryUtils.find(Items.FIREWORK_ROCKET, 0, autoSwitch.getValue().equalsIgnoreCase("AltSwap") ? 35 : 8);
        if (slot == -1 && !offhand) return;

        boolean doRotate = !rotate.getValue().equalsIgnoreCase("None");

        if (slot == -1) {
            WorldUtils.placeBlock(pos, face, Hand.OFF_HAND, doRotate, false, render.getValue());
        } else {
            int previousSlot = mc.player.getInventory().getSelectedSlot();
            InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);
            WorldUtils.placeBlock(pos, face, Hand.MAIN_HAND, doRotate, false, render.getValue());
            InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
        }

        fireworkPlaced.put(pos, now);
    }

    private BlockPos selectFireworkPosition(long now) {
        Set<BlockPos> surround = HoleUtils.getFeetPositions(mc.player, extension.getValue(), false, false);
        Set<BlockPos> fireworkEntities = getFireworkEntityPositions();
        long retryDelay = Anatorius.SERVER_MANAGER.getPing() + 100L;

        for (BlockPos pos : surround) {
            if (!hasCoveredAirGap(pos)) continue;
            if (!isBedrockOrObsidian(pos.down())) continue;
            if (!isThreatenedPosition(pos, now)) continue;
            if (fireworkEntities.contains(pos)) continue;
            Long placed = fireworkPlaced.get(pos);
            if (placed != null && now - placed < retryDelay) continue;
            return pos;
        }
        return null;
    }

    private boolean hasCoveredAirGap(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        return !mc.world.getBlockState(pos.up()).isReplaceable();
    }

    private boolean isBedrockOrObsidian(BlockPos pos) {
        net.minecraft.block.Block block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.BEDROCK || block == Blocks.OBSIDIAN;
    }

    private boolean isThreatenedPosition(BlockPos pos, long now) {
        Deque<Long> times = itemSpawnTimes.get(pos);
        if (times != null) {
            pruneTimes(times, now);
            if (times.size() >= 2) return true;
        }
        return breakStageAt(pos) >= 9;
    }

    private boolean isFireworkTracked(BlockPos pos, Set<BlockPos> fireworkEntities, long now) {
        Long placed = fireworkPlaced.get(pos);
        if (placed != null && now - placed < Anatorius.SERVER_MANAGER.getPing() + 100L) return true;
        return fireworkEntities != null && fireworkEntities.contains(pos);
    }

    private Set<BlockPos> getFireworkEntityPositions() {
        Set<BlockPos> positions = new HashSet<>();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity) positions.add(entity.getBlockPos());
        }
        return positions;
    }

    private int breakStageAt(BlockPos pos) {
        if (mc.worldRenderer == null) return -1;
        SortedSet<BlockBreakingInfo> infos = ((WorldRendererAccessor) mc.worldRenderer).getBlockBreakingProgressions().get(pos.asLong());
        if (infos == null || infos.isEmpty()) return -1;
        int max = -1;
        for (BlockBreakingInfo info : infos) max = Math.max(max, info.getStage());
        return max;
    }

    private void pruneTimes(Deque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst() > 500L) times.pollFirst();
    }

    private void pruneFireworkState(long now) {
        itemSpawnTimes.entrySet().removeIf(entry -> {
            pruneTimes(entry.getValue(), now);
            return entry.getValue().isEmpty();
        });
        fireworkPlaced.entrySet().removeIf(entry -> now - entry.getValue() > 5000L);
    }

    private static final int MISSING_BLOCK_LIMIT = 30;
    private int missingBlockAttempts = 0;

    private void handleNoItems() {
        if (++missingBlockAttempts < MISSING_BLOCK_LIMIT) return;
        missingBlockAttempts = 0;
        if (itemDisable.getValue()) {
            Anatorius.CHAT_MANAGER.tagged("No placeable blocks found in hotbar.", getName());
            setToggled(false);
        }
        targetPositions.clear();
        pendingPlacements.clear();
    }

    private <T extends Module> void disableIfOn(Class<T> clazz) {
        T mod = Anatorius.MODULE_MANAGER.getModule(clazz);
        if (mod != null && mod.isToggled()) mod.setToggled(false);
    }
}
