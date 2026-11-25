package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.BlockBudget;
import dev.project.anatorius.utils.minecraft.EntityUtils;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.rotations.RotationUtils;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RegisterModule(name = "CombatPlacer", description = "Places blocks in the way of nearby enemies to block their movement.", category = Module.Category.COMBAT)
public class CombatPlacerModule extends Module {
    private static final int HORIZON = 6;

    public ModeSetting autoSwitch = new ModeSetting("Switch", "The mode that will be used for automatically switching to the blocks.", "Silent", InventoryUtils.SWITCH_MODES);
    public ModeSetting material = new ModeSetting("Material", "What gets placed. Obsidian takes your hardest block, EnderChest only ender chests.", "Obsidian", new String[]{"Obsidian", "EnderChest"});

    public BooleanSetting wall = new BooleanSetting("Wall", "Builds a two-high wall at the spot the target is about to walk into.", true);
    public NumberSetting wallLead = new NumberSetting("WallLead", "From how many ticks ahead of the target the wall is tried. Lower is closer, higher gives more time to place.", new BooleanSetting.Visibility(wall, true), 2, 1, HORIZON);
    public BooleanSetting support = new BooleanSetting("Support", "Places a helper block first when the wall has nothing to be placed against.", new BooleanSetting.Visibility(wall, true), false);

    public BooleanSetting floor = new BooleanSetting("Floor", "Fills the gaps under the target's path so it can't drop down.", false);
    public NumberSetting floorTicks = new NumberSetting("FloorTicks", "How many ticks of the target's path get a floor.", new BooleanSetting.Visibility(floor, true), 1, 1, HORIZON);

    public BooleanSetting exactHeight = new BooleanSetting("ExactHeight", "Follows the target's projected height instead of the height it stands at now.", false);

    public NumberSetting range = new NumberSetting("Range", "The maximum range at which the blocks will be placed at.", 5.0, 0.0, 6.0);
    public NumberSetting enemyRange = new NumberSetting("EnemyRange", "The maximum distance at which targets are considered.", 8.0, 0.0, 16.0);
    public ModeSetting rotate = new ModeSetting("Rotate", "How to face the blocks being placed.", "Packet", new String[]{"None", "Packet", "Normal"});
    public BooleanSetting strictDirection = new BooleanSetting("StrictDirection", "Only places using directions that face you.", false);
    public BooleanSetting crystalDestruction = new BooleanSetting("Attack", "Breaks the crystals in the way before placing.", true);
    public NumberSetting blocksPerTick = new NumberSetting("BlocksPerTick", "Max blocks placed per tick. 0 = unlimited.", 4, 0, 10);
    public BooleanSetting whileEating = new BooleanSetting("WhileEating", "Places blocks normally while eating.", true);
    public BooleanSetting render = new BooleanSetting("Render", "Whether or not to render the place positions.", true);

    private final BlockBudget budget = new BlockBudget();
    private int placedLastTick;
    private final Map<UUID, Vec3d> motions = new HashMap<>();
    private BlockPos pendingTop;
    private int pendingTopTicks;
    private int blockSlot = -1;
    private int previousSlot = -1;
    private boolean switched;

    @Override
    public void onDisable() {
        budget.reset();
        placedLastTick = 0;
        motions.clear();
        pendingTop = null;
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (getNull()) return;
        if (!whileEating.getValue() && mc.player.isUsingItem()) return;
        if (!wall.getValue() && !floor.getValue()) return;

        List<PlayerEntity> targets = findTargets();
        trackMotions(targets);
        if (targets.isEmpty()) {
            placedLastTick = 0;
            return;
        }

        boolean noSwitch = autoSwitch.getValue().equalsIgnoreCase("None");
        blockSlot = noSwitch ? mc.player.getInventory().getSelectedSlot() : findBlock();
        if (blockSlot == -1) return;
        if (noSwitch && !(mc.player.getMainHandStack().getItem() instanceof BlockItem)) return;

        previousSlot = mc.player.getInventory().getSelectedSlot();
        switched = false;
        budget.newTick();

        List<BlockPos> placed = new ArrayList<>();
        if (pendingTop != null) {
            if (--pendingTopTicks <= 0 || !mc.world.getBlockState(pendingTop).isReplaceable() || place(pendingTop, placed)) pendingTop = null;
        }
        for (PlayerEntity target : targets) {
            if (floor.getValue()) buildFloor(target, placed);
            if (wall.getValue()) buildWall(target, placed);
            if (!canPlaceMore()) break;
        }

        if (switched) InventoryUtils.switchBack(autoSwitch.getValue(), blockSlot, previousSlot);
        placedLastTick = placed.size();
    }

    private void buildFloor(PlayerEntity target, List<BlockPos> placed) {
        for (int tick = 1; tick <= floorTicks.getValue().intValue() && canPlaceMore(); tick++) {
            BlockPos below = feetAt(target, tick).down();
            if (mc.world.getBlockState(below).isReplaceable()) place(below, placed);
        }
    }

    private void buildWall(PlayerEntity target, List<BlockPos> placed) {
        Set<BlockPos> path = new HashSet<>();
        Set<BlockPos> occupied = cellsOf(target);
        path.addAll(occupied);

        for (int tick = wallLead.getValue().intValue(); tick <= HORIZON && canPlaceMore(); tick++) {
            BlockPos feet = feetAt(target, tick);
            path.add(feet);
            path.add(feet.up());

            BlockPos head = feet.up();
            if (!mc.world.getBlockState(head).isReplaceable()) continue;
            if (occupied.contains(feet)) continue;

            if (mc.world.getBlockState(feet).isReplaceable() && place(feet, placed)) {
                if (!place(head, placed)) {
                    pendingTop = head;
                    pendingTopTicks = 4;
                }
                return;
            }

            if (place(head, placed)) {
                place(head.up(), placed);
                return;
            }

            if (support.getValue() && WorldUtils.isPlaceable(head) && placeSupport(head, path, placed) && place(head, placed)) {
                place(head.up(), placed);
                return;
            }
        }
    }

    private boolean placeSupport(BlockPos position, Set<BlockPos> path, List<BlockPos> placed) {
        Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
        for (Direction side : sides) {
            BlockPos helper = position.offset(side);
            if (path.contains(helper)) continue;
            if (place(helper, placed)) return true;
        }
        return false;
    }

    private boolean place(BlockPos position, List<BlockPos> placed) {
        if (!canPlaceMore()) return false;
        if (mc.player.squaredDistanceTo(Vec3d.ofCenter(position)) > MathHelper.square(range.getValue().doubleValue())) return false;
        if (!WorldUtils.isPlaceable(position)) return false;
        if (crystalSpot(position)) return false;

        Direction direction = WorldUtils.getDirection(position, placed, strictDirection.getValue());
        if (direction == null) return false;

        if (!switched) {
            InventoryUtils.switchSlot(autoSwitch.getValue(), blockSlot, previousSlot);
            switched = true;
        }

        boolean doRotate = !rotate.getValue().equalsIgnoreCase("None");
        int priority = Anatorius.ROTATION_MANAGER.getModulePriority(this);
        if (rotate.getValue().equalsIgnoreCase("Normal")) {
            Vec3d hit = Vec3d.ofCenter(position).add(direction.getOffsetX() * 0.5, direction.getOffsetY() * 0.5, direction.getOffsetZ() * 0.5);
            Anatorius.ROTATION_MANAGER.rotate(RotationUtils.getRotations(hit, false), priority);
        }

        if (!WorldUtils.placeBlock(position, direction, Hand.MAIN_HAND, null, doRotate, crystalDestruction.getValue() && !autoCrystalRunning(), render.getValue(), priority)) {
            return false;
        }
        placed.add(position);
        budget.consume();
        return true;
    }

    private boolean crystalSpot(BlockPos position) {
        if (!autoCrystalRunning()) return false;
        AutoCrystalModule autoCrystal = Anatorius.MODULE_MANAGER.getModule(AutoCrystalModule.class);
        BlockPos base = autoCrystal.getPlacePosition();
        if (base != null && (position.equals(base.up()) || position.equals(base.up(2)))) return true;
        return !mc.world.getEntitiesByClass(EndCrystalEntity.class, new Box(position), crystal -> crystal.isAlive()).isEmpty();
    }

    private boolean autoCrystalRunning() {
        AutoCrystalModule autoCrystal = Anatorius.MODULE_MANAGER.getModule(AutoCrystalModule.class);
        return autoCrystal != null && autoCrystal.isToggled();
    }

    private boolean canPlaceMore() {
        return budget.canPlace(false, blocksPerTick.getValue().intValue());
    }

    private BlockPos feetAt(PlayerEntity target, int ticks) {
        Vec3d position = project(target, ticks);
        double y = exactHeight.getValue() ? position.y : target.getY();
        return BlockPos.ofFloored(position.x, y, position.z);
    }

    private Vec3d project(PlayerEntity target, int ticks) {
        Vec3d step = motion(target);
        Vec3d result = target.getEntityPos();
        for (int tick = 1; tick <= ticks; tick++) {
            Vec3d offset = step.multiply(tick);
            if (mc.world.canCollide(target, target.getBoundingBox().offset(offset))) break;
            result = target.getEntityPos().add(offset);
        }
        return result;
    }

    private Vec3d motion(PlayerEntity target) {
        Vec3d smoothed = motions.getOrDefault(target.getUuid(), Vec3d.ZERO);
        return new Vec3d(smoothed.x, exactHeight.getValue() ? target.getY() - target.lastY : 0.0, smoothed.z);
    }

    private void trackMotions(List<PlayerEntity> targets) {
        Map<UUID, Vec3d> next = new HashMap<>();
        for (PlayerEntity target : targets) {
            Vec3d step = new Vec3d(target.getX() - target.lastX, 0.0, target.getZ() - target.lastZ);
            if (step.lengthSquared() > 4.0) step = Vec3d.ZERO;
            Vec3d previous = motions.getOrDefault(target.getUuid(), step);
            next.put(target.getUuid(), previous.multiply(0.5).add(step.multiply(0.5)));
        }
        motions.clear();
        motions.putAll(next);
    }

    private Set<BlockPos> cellsOf(PlayerEntity target) {
        Set<BlockPos> cells = new HashSet<>();
        Box box = target.getBoundingBox();
        int y = MathHelper.floor(target.getY());
        for (int x = MathHelper.floor(box.minX); x <= MathHelper.floor(box.maxX - 1.0E-4); x++) {
            for (int z = MathHelper.floor(box.minZ); z <= MathHelper.floor(box.maxZ - 1.0E-4); z++) cells.add(new BlockPos(x, y, z));
        }
        return cells;
    }

    private boolean groundedSoon(PlayerEntity target) {
        if (target.isOnGround()) return true;
        Vec3d step = new Vec3d(target.getX() - target.lastX, target.getY() - target.lastY, target.getZ() - target.lastZ);
        Box next = target.getBoundingBox().offset(step);
        return !mc.world.isSpaceEmpty(target, next.offset(0.0, -0.05, 0.0));
    }

    private List<PlayerEntity> findTargets() {
        double maxDistance = MathHelper.square(enemyRange.getValue().doubleValue());
        List<PlayerEntity> targets = new ArrayList<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.getHealth() <= 0.0f) continue;
            if (player.isCreative() || player.isSpectator() || EntityUtils.isBot(player)) continue;
            if (Anatorius.FRIEND_MANAGER.contains(player.getName().getString())) continue;
            if (mc.player.squaredDistanceTo(player) > maxDistance) continue;
            if (!groundedSoon(player)) continue;
            targets.add(player);
        }
        targets.sort((a, b) -> Double.compare(mc.player.squaredDistanceTo(a), mc.player.squaredDistanceTo(b)));
        return targets;
    }

    private int findBlock() {
        int end = autoSwitch.getValue().equalsIgnoreCase("AltSwap") ? InventoryUtils.INVENTORY_END : InventoryUtils.HOTBAR_END;
        if (material.getValue().equalsIgnoreCase("EnderChest")) {
            return InventoryUtils.find(Items.ENDER_CHEST, InventoryUtils.HOTBAR_START, end);
        }
        return InventoryUtils.findHardestBlock(InventoryUtils.HOTBAR_START, end);
    }

    @Override
    public String getMetaData() {
        return String.valueOf(placedLastTick);
    }
}
