package dev.project.anatorius.utils.minecraft;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.core.AntiCheatModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TrapUtils implements IMinecraft {
    private TrapUtils() {}

    public record Shape(boolean head, boolean body, boolean feet, boolean below, boolean antiStep, boolean antiBomb, boolean airPlace) {
        boolean headOnly() {
            return head && !body;
        }
    }

    private static final Vec3i[] RING = {
            new Vec3i(1, 0, 0), new Vec3i(0, 0, 1), new Vec3i(0, 0, -1), new Vec3i(-1, 0, 0),
            new Vec3i(1, 1, 0), new Vec3i(0, 1, 1), new Vec3i(0, 1, -1), new Vec3i(-1, 1, 0)
    };

    public static boolean strictDirection() {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        return antiCheat != null && antiCheat.strictDirection.getValue();
    }

    public static List<BlockPos> collect(PlayerEntity target, Shape shape) {
        BlockPos origin = BlockPos.ofFloored(target.getX(), target.getY(), target.getZ());
        List<BlockPos> positions = collect(target, shape, new HashSet<>(), origin);

        if (!shape.headOnly() && !shape.feet() && target.getHeight() > 1.0f) {
            int feetY = MathHelper.floor(target.getY());
            positions.removeIf(position -> position.getY() == feetY);
        }

        if (shape.antiStep()) {
            for (Direction side : Direction.Type.HORIZONTAL) positions.add(origin.up(2).offset(side));
        }
        if (shape.antiBomb()) positions.add(origin.up(3));

        return new ArrayList<>(new LinkedHashSet<>(positions));
    }

    private static List<BlockPos> collect(PlayerEntity target, Shape shape, Set<BlockPos> visited, BlockPos origin) {
        List<BlockPos> positions = new ArrayList<>();
        visited.add(origin);
        boolean strict = strictDirection();

        for (Vec3i offset : offsets(target, shape, origin, strict)) {
            BlockPos candidate = origin.add(offset);
            if (occupied(candidate)) continue;
            if (shape.airPlace() && offset.getY() > 0 && Anatorius.WORLD_MANAGER.isPending(candidate)) continue;

            if (offset.getY() == 0 && !visited.contains(candidate) && hasPlayer(candidate)) {
                positions.addAll(collect(target, shape, visited, candidate));
                continue;
            }

            if (!shape.airPlace() && WorldUtils.getDirection(candidate, positions, strict) == null) {
                BlockPos support = supportNeighbour(candidate, positions, strict);
                if (support != null) positions.add(support);
            }
            positions.add(candidate);
        }

        positions.remove(origin);
        return positions;
    }

    private static List<Vec3i> offsets(PlayerEntity target, Shape shape, BlockPos origin, boolean strict) {
        float height = target.getHeight();
        int headY = (int) (height + 1.0f);
        List<Vec3i> offsets = new ArrayList<>();

        if (shape.headOnly()) {
            offsets.add(new Vec3i(0, headY, 0));
            BlockPos headPos = origin.up(headY);
            if (!shape.airPlace() && !occupied(headPos) && WorldUtils.getDirection(headPos, strict) == null) {
                offsets.add(new Vec3i(-1, headY > 0 ? 1 : 0, 0));
            }
        } else if (shape.head()) {
            offsets.add(new Vec3i(0, headY, 0));
        }

        for (Vec3i side : RING) {
            if (side.getY() > height) continue;
            if (!shape.body() && side.getY() > 0) continue;
            offsets.add(side);
        }

        if (shape.below()) offsets.add(new Vec3i(0, -1, 0));
        return offsets;
    }

    private static BlockPos supportNeighbour(BlockPos position, List<BlockPos> planned, boolean strict) {
        List<BlockPos> neighbours = new ArrayList<>();
        for (Direction direction : Direction.values()) neighbours.add(position.offset(direction));
        neighbours.sort(Comparator.comparingInt(BlockPos::getY));

        for (BlockPos neighbour : neighbours) {
            if (planned.contains(neighbour) || occupied(neighbour) || hasPlayer(neighbour)) continue;
            if (!WorldUtils.isPlaceable(neighbour)) continue;
            if (WorldUtils.getDirection(neighbour, planned, strict) != null) return neighbour;
        }
        return null;
    }

    private static boolean occupied(BlockPos position) {
        return !mc.world.getBlockState(position).isReplaceable()
                || (Anatorius.WORLD_MANAGER != null && Anatorius.WORLD_MANAGER.isPlacedThisTick(position));
    }

    private static boolean hasPlayer(BlockPos position) {
        return !mc.world.getEntitiesByClass(PlayerEntity.class, new Box(position), player -> !player.isSpectator()).isEmpty();
    }

    public static List<BlockPos> filter(List<BlockPos> positions, PlayerEntity target, Shape shape, double range) {
        Vec3d eye = mc.player.getEyePos();
        double rangeSq = MathHelper.square(Math.min(range, 6.0));
        int feetY = MathHelper.floor(target.getY());
        List<BlockPos> result = new ArrayList<>();
        for (BlockPos position : positions) {
            if (eye.squaredDistanceTo(position.toCenterPos()) > rangeSq) continue;
            if (!WorldUtils.isPlaceable(position)) continue;
            if (!shape.feet() && target.getHeight() > 1.0f && position.getY() == feetY && occupied(position.up())) continue;
            result.add(position);
        }
        result.sort(Comparator.comparingInt((BlockPos position) -> -position.getY()));
        return result;
    }

    public static int findBlock(String switchMode, boolean obsidianOnly) {
        if (switchMode.equalsIgnoreCase("None")) {
            boolean held = obsidianOnly ? mc.player.getMainHandStack().isOf(Items.OBSIDIAN)
                    : mc.player.getMainHandStack().getItem() instanceof net.minecraft.item.BlockItem;
            return held ? mc.player.getInventory().getSelectedSlot() : -1;
        }
        int end = switchMode.equalsIgnoreCase("AltSwap") ? InventoryUtils.INVENTORY_END : InventoryUtils.HOTBAR_END;
        int obsidian = InventoryUtils.find(Items.OBSIDIAN, InventoryUtils.HOTBAR_START, end);
        if (obsidian != -1 || obsidianOnly) return obsidian;
        return InventoryUtils.findHardestBlock(InventoryUtils.HOTBAR_START, end);
    }

    public record Placing(String switchMode, int slot, int blocksPerTick, boolean rotate, boolean attack, boolean airPlace, boolean render, int priority) {}

    public static List<BlockPos> place(List<BlockPos> positions, Placing placing) {
        List<BlockPos> placed = new ArrayList<>();
        if (positions.isEmpty() || placing.slot() == -1) return placed;

        boolean strict = strictDirection();
        int previousSlot = mc.player.getInventory().getSelectedSlot();
        boolean switched = false;

        boolean progress = true;
        while (progress && placed.size() < placing.blocksPerTick()) {
            progress = false;
            for (BlockPos position : positions) {
                if (placed.size() >= placing.blocksPerTick()) break;
                if (placed.contains(position) || !WorldUtils.isPlaceable(position)) continue;

                Direction direction = WorldUtils.getDirection(position, strict);
                if (direction == null && !placing.airPlace()) continue;

                if (!switched) {
                    InventoryUtils.switchSlot(placing.switchMode(), placing.slot(), previousSlot);
                    switched = true;
                }
                if (WorldUtils.placeBlock(position, direction, Hand.MAIN_HAND, null, placing.rotate(), placing.attack(), placing.render(), placing.priority())) {
                    placed.add(position);
                    progress = true;
                }
            }
        }

        if (switched) InventoryUtils.switchBack(placing.switchMode(), placing.slot(), previousSlot);
        return placed;
    }

    public static boolean hasArmor(PlayerEntity player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!player.getEquippedStack(slot).isEmpty()) return true;
        }
        return false;
    }

    public static boolean isPhased(PlayerEntity player) {
        if (player.isGliding()) return false;
        Box box = player.getBoundingBox();
        Box feet = new Box(box.minX, player.getY() + 0.1, box.minZ, box.maxX, player.getY() + 0.1 + 1.0E-3, box.maxZ).contract(1.0E-3, 0.0, 1.0E-3);
        return !mc.world.isSpaceEmpty(player, feet);
    }
}
