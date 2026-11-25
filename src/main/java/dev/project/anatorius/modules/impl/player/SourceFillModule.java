package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.settings.impl.WhitelistSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.block.Block;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RegisterModule(name = "SourceFill", description = "Caps water and lava around you with blocks from your hotbar.", category = Module.Category.PLAYER)
public class SourceFillModule extends Module {
    public NumberSetting range = new NumberSetting("Range", "How far out to look for liquids, in blocks.", 4.5, 1.0, 6.0);
    public NumberSetting wallRange = new NumberSetting("WallRange", "Reach for liquids you can't see directly.", 4.5, 0.0, 6.0);
    public NumberSetting perTick = new NumberSetting("PerTick", "How many blocks to place each tick.", 3, 1, 8);
    public NumberSetting delay = new NumberSetting("Delay", "Milliseconds to wait between placements.", 0, 0, 500);

    public CategorySetting liquidCategory = new CategorySetting("Liquids", "Which liquids to seal off.");
    public BooleanSetting water = new BooleanSetting("Water", "Fills water.", new CategorySetting.Visibility(liquidCategory), true);
    public BooleanSetting lava = new BooleanSetting("Lava", "Fills lava.", new CategorySetting.Visibility(liquidCategory), true);
    public BooleanSetting sourcesOnly = new BooleanSetting("SourcesOnly", "Only fills still source blocks, not the flowing ones.", new CategorySetting.Visibility(liquidCategory), false);

    public WhitelistSetting blocks = new WhitelistSetting("Blocks", "The blocks allowed for filling. Empty: any block in your hotbar.", WhitelistSetting.Type.BLOCKS);
    public BooleanSetting rotate = new BooleanSetting("Rotate", "Faces the block being placed (packet).", true);
    public BooleanSetting nearestFirst = new BooleanSetting("NearestFirst", "Fills the closest liquids first.", true);

    private final Timer timer = new Timer();

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!timer.hasTimeElapsed(delay.getValue().longValue())) return;

        int slot = findBlockSlot();
        if (slot == -1) return;

        List<BlockPos> targets = collectTargets();
        if (targets.isEmpty()) return;

        int previous = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.switchSlot("Silent", slot, previous);

        int placed = 0, limit = perTick.getValue().intValue();
        for (BlockPos pos : targets) {
            if (placed >= limit) break;
            Direction direction = WorldUtils.getDirection(pos, false);
            if (direction == null) continue;
            if (rotate.getValue()) {
                Vec3d aim = Vec3d.ofCenter(pos).add(direction.getOffsetX() * 0.5, direction.getOffsetY() * 0.5, direction.getOffsetZ() * 0.5);
                float[] r = dev.project.anatorius.utils.rotations.RotationUtils.getRotations(aim.x, aim.y, aim.z, false);
                Anatorius.ROTATION_MANAGER.packetRotate(r[0], r[1]);
            }
            if (WorldUtils.placeBlock(pos, direction, Hand.MAIN_HAND, false, false, false)) {
                placed++;
                timer.reset();
            }
        }

        InventoryUtils.switchBack("Silent", slot, previous);
    }

    private List<BlockPos> collectTargets() {
        List<BlockPos> targets = new ArrayList<>();
        Vec3d eye = mc.player.getEyePos();
        double reach = Math.max(range.getValue().doubleValue(), wallRange.getValue().doubleValue());
        int radius = (int) Math.ceil(reach);
        BlockPos origin = mc.player.getBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (!isFillable(pos)) continue;

                    double distance = eye.distanceTo(Vec3d.ofCenter(pos));
                    boolean visible = WorldUtils.canSee(pos);
                    if (distance > (visible ? range.getValue().doubleValue() : wallRange.getValue().doubleValue())) continue;
                    targets.add(pos);
                }
            }
        }

        if (nearestFirst.getValue()) {
            targets.sort(Comparator.comparingDouble(pos -> eye.squaredDistanceTo(Vec3d.ofCenter(pos))));
        }
        return targets;
    }

    private boolean isFillable(BlockPos pos) {
        FluidState fluid = mc.world.getFluidState(pos);
        if (fluid.isEmpty()) return false;
        if (sourcesOnly.getValue() && !fluid.isStill()) return false;
        boolean isLava = fluid.isIn(net.minecraft.registry.tag.FluidTags.LAVA);
        if (isLava ? !lava.getValue() : !water.getValue()) return false;
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        return WorldUtils.isPlaceable(pos, true);
    }

    private int findBlockSlot() {
        boolean filter = !blocks.getWhitelist().isEmpty();
        return InventoryUtils.find(stack -> isUsableBlock(stack, filter), InventoryUtils.HOTBAR_START, InventoryUtils.HOTBAR_END);
    }

    private boolean isUsableBlock(ItemStack stack, boolean filter) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (block.getDefaultState().isReplaceable()) return false;
        return !filter || blocks.isWhitelistContains(block);
    }
}
