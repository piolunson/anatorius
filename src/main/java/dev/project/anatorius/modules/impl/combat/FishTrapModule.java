package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.core.RotationsModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.rotations.RotationUtils;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RegisterModule(name = "FishTrap", description = "Traps your enemies in the fish (crawling) position.", category = Module.Category.COMBAT)
public class FishTrapModule extends Module {
    public ModeSetting autoSwitch = new ModeSetting("Switch", "The mode that will be used for automatically switching to necessary items.", "Silent", InventoryUtils.SWITCH_MODES);
    public ModeSetting rotate = new ModeSetting("Rotate", "Rotates to the placed blocks.", "Packet", new String[]{"None", "Packet", "Normal"});
    public NumberSetting range = new NumberSetting("Range", "The maximum range at which the blocks will be placed at.", 5.0, 0.0, 12.0);
    public NumberSetting enemyRange = new NumberSetting("EnemyRange", "The maximum distance at which the target should be at.", 8.0, 0.0, 16.0);
    public NumberSetting delay = new NumberSetting("Delay", "Ticks to wait between placements.", 0, 0, 20);
    public BooleanSetting strictDirection = new BooleanSetting("StrictDirection", "Only places using directions that face you.", false);
    public BooleanSetting crystalDestruction = new BooleanSetting("Attack", "Breaks the crystals in the way before placing.", true);
    public BooleanSetting whileEating = new BooleanSetting("WhileEating", "Places blocks normally while eating.", true);
    public BooleanSetting render = new BooleanSetting("Render", "Whether or not to render the place position.", true);

    private List<BlockPos> positions = new ArrayList<>();
    private int ticks;

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!whileEating.getValue() && mc.player.isUsingItem()) return;
        if (ticks < delay.getValue().intValue()) {
            ticks++;
            return;
        }

        positions = findPositions();
        if (positions.isEmpty()) return;

        int slot = InventoryUtils.findHardestBlock(0, autoSwitch.getValue().equalsIgnoreCase("AltSwap") ? 35 : 8);
        if (slot == -1) return;
        int previousSlot = mc.player.getInventory().getSelectedSlot();

        boolean doRotate = !rotate.getValue().equalsIgnoreCase("None");
        boolean packetRotate = rotate.getValue().equalsIgnoreCase("Packet");
        int priority = Anatorius.ROTATION_MANAGER.getModulePriority(this);
        boolean snapBack = doRotate && Anatorius.MODULE_MANAGER.getModule(RotationsModule.class).snapBack.getValue();
        float snapYaw = Anatorius.ROTATION_MANAGER.getServerYaw(), snapPitch = Anatorius.ROTATION_MANAGER.getServerPitch();

        InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos position : positions) {
            Direction direction = WorldUtils.getDirection(position, placed, strictDirection.getValue());
            if (direction == null) {
                for (BlockPos support : supportChain(position, placed)) {
                    Direction supportDirection = WorldUtils.getDirection(support, placed, strictDirection.getValue());
                    if (supportDirection == null) break;
                    rotateTo(support, supportDirection, doRotate, packetRotate, priority);
                    if (!WorldUtils.placeBlock(support, supportDirection, Hand.MAIN_HAND, false, crystalDestruction.getValue(), render.getValue())) break;
                    placed.add(support);
                }
                direction = WorldUtils.getDirection(position, placed, strictDirection.getValue());
                if (direction == null) continue;
            }
            rotateTo(position, direction, doRotate, packetRotate, priority);
            if (WorldUtils.placeBlock(position, direction, Hand.MAIN_HAND, false, crystalDestruction.getValue(), render.getValue())) placed.add(position);
        }
        if (snapBack) Anatorius.ROTATION_MANAGER.packetRotate(snapYaw, snapPitch);
        InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
        ticks = 0;
    }

    private List<BlockPos> supportChain(BlockPos position, List<BlockPos> placed) {
        List<BlockPos> twoStep = null;
        for (Direction side : Direction.Type.HORIZONTAL) {
            BlockPos beside = position.offset(side);
            if (!WorldUtils.isPlaceable(beside) || !inRange(beside)) continue;
            if (WorldUtils.getDirection(beside, placed, strictDirection.getValue()) != null) return List.of(beside);
            BlockPos below = beside.down();
            if (twoStep == null && WorldUtils.isPlaceable(below) && inRange(below) && WorldUtils.getDirection(below, placed, strictDirection.getValue()) != null) {
                twoStep = List.of(below, beside);
            }
        }
        return twoStep == null ? List.of() : twoStep;
    }

    private void rotateTo(BlockPos position, Direction direction, boolean doRotate, boolean packetRotate, int priority) {
        if (!doRotate) return;
        float[] rotations = RotationUtils.getRotations(position.getX() + 0.5 + direction.getOffsetX() * 0.5, position.getY() + 0.5 + direction.getOffsetY() * 0.5, position.getZ() + 0.5 + direction.getOffsetZ() * 0.5, false);
        if (!packetRotate) Anatorius.ROTATION_MANAGER.rotate(rotations, priority);
        Anatorius.ROTATION_MANAGER.packetRotate(rotations, priority);
    }

    private List<BlockPos> findPositions() {
        PlayerEntity best = null;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.getHealth() <= 0.0f) continue;
            if (Anatorius.FRIEND_MANAGER.contains(player.getName().getString())) continue;
            if (!isCrawling(player)) continue;
            if (mc.player.squaredDistanceTo(player) > MathHelper.square(enemyRange.getValue().doubleValue())) continue;
            if (best == null || mc.player.squaredDistanceTo(player) < mc.player.squaredDistanceTo(best)) best = player;
        }
        if (best == null) return new ArrayList<>();

        Box box = best.getBoundingBox();
        int y = MathHelper.floor(box.maxY - 1.0E-4) + 1;
        Set<BlockPos> above = new LinkedHashSet<>();
        for (double x : new double[]{box.minX + 1.0E-4, box.maxX - 1.0E-4}) {
            for (double z : new double[]{box.minZ + 1.0E-4, box.maxZ - 1.0E-4}) {
                above.add(BlockPos.ofFloored(x, y, z));
            }
        }
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos position : above) {
            if (WorldUtils.isPlaceable(position) && inRange(position)) result.add(position);
        }
        return result;
    }

    private boolean inRange(BlockPos position) {
        return mc.player.squaredDistanceTo(Vec3d.ofCenter(position)) <= MathHelper.square(range.getValue().doubleValue());
    }

    private static boolean isCrawling(PlayerEntity player) {
        return player.getPose() == EntityPose.SWIMMING && !player.isTouchingWater() && !player.isGliding();
    }

    @Override
    public void onDisable() {
        positions = new ArrayList<>();
        ticks = 0;
    }

    @Override
    public String getMetaData() {
        return String.valueOf(positions.size());
    }
}
