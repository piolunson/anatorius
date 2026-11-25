package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.EntityUtils;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.rotations.RotationUtils;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

@RegisterModule(name = "AutoDrop", description = "Drops sand, gravel, anvils and other falling blocks onto nearby enemies.", category = Module.Category.COMBAT)
public class AutoDropModule extends Module {
    public ModeSetting autoSwitch = new ModeSetting("Switch", "The mode that will be used for automatically switching to the falling blocks.", "Silent", InventoryUtils.SWITCH_MODES);
    public ModeSetting material = new ModeSetting("Material", "What gets dropped. Any uses every falling block (sand, gravel, concrete powder, anvils...), Anvil only anvils.", "Any", new String[]{"Any", "Anvil"});
    public ModeSetting placement = new ModeSetting("Placement", "Where above the target the block goes. Lowest takes the lowest spot that works, Fixed always DropHeight above its feet.", "Lowest", new String[]{"Lowest", "Fixed"});
    public NumberSetting dropHeight = new NumberSetting("DropHeight", "How many blocks above the target's feet the block is placed.", new ModeSetting.Visibility(placement, "Fixed"), 2, 2, 7);
    public BooleanSetting skipFalling = new BooleanSetting("SkipFalling", "Waits while something is already falling onto the target instead of dropping another one.", true);

    public NumberSetting range = new NumberSetting("Range", "The maximum range at which the blocks will be placed at.", 5.0, 0.0, 6.0);
    public NumberSetting enemyRange = new NumberSetting("EnemyRange", "The maximum horizontal distance at which targets are considered.", 8.0, 0.0, 16.0);
    public NumberSetting delay = new NumberSetting("Delay", "The amount of ticks waited between drops.", 0, 0, 20);
    public ModeSetting rotate = new ModeSetting("Rotate", "How to face the blocks being placed.", "Packet", new String[]{"None", "Packet", "Normal"});
    public BooleanSetting strictDirection = new BooleanSetting("StrictDirection", "Only places using directions that face you.", false);
    public BooleanSetting render = new BooleanSetting("Render", "Whether or not to render the place positions.", true);

    private int ticks;
    private int dropped;

    @Override
    public void onDisable() {
        ticks = 0;
        dropped = 0;
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (getNull()) return;
        if (ticks < delay.getValue().intValue()) {
            ticks++;
            return;
        }

        int slot = findFallingBlock();
        if (slot == -1) return;

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        boolean switched = false;
        List<BlockPos> placed = new ArrayList<>();

        for (PlayerEntity target : findTargets()) {
            BlockPos spot = dropSpot(target);
            if (spot == null) continue;

            Direction direction = WorldUtils.getDirection(spot, placed, strictDirection.getValue());
            if (direction == null) continue;

            if (!switched) {
                InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);
                switched = true;
            }
            if (place(spot, direction)) {
                placed.add(spot);
                dropped++;
            }
        }

        if (switched) {
            InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
            ticks = 0;
        }
    }

    private BlockPos dropSpot(PlayerEntity target) {
        BlockPos feet = BlockPos.ofFloored(target.getEntityPos());
        if (!mc.world.getBlockState(feet).isReplaceable() || !mc.world.getBlockState(feet.up()).isReplaceable()) return null;
        if (skipFalling.getValue() && somethingFallingOn(target)) return null;

        if (placement.getValue().equalsIgnoreCase("Fixed")) {
            BlockPos spot = feet.up(dropHeight.getValue().intValue());
            for (BlockPos between = feet.up(2); between.getY() < spot.getY(); between = between.up()) {
                if (!mc.world.getBlockState(between).isReplaceable()) return null;
            }
            return placeable(spot) ? spot : null;
        }

        int top = Math.min(mc.world.getTopYInclusive(), MathHelper.floor(mc.player.getEyeY() + range.getValue().doubleValue()));
        for (BlockPos spot = feet.up(2); spot.getY() <= top; spot = spot.up()) {
            BlockState state = mc.world.getBlockState(spot);
            if (state.getBlock() instanceof FallingBlock && FallingBlock.canFallThrough(mc.world.getBlockState(spot.down()))) return null;
            if (!state.isReplaceable()) return null;
            if (placeable(spot) && WorldUtils.getDirection(spot, strictDirection.getValue()) != null) return spot;
        }
        return null;
    }

    private boolean placeable(BlockPos spot) {
        return mc.player.squaredDistanceTo(Vec3d.ofCenter(spot)) <= MathHelper.square(range.getValue().doubleValue())
                && WorldUtils.isPlaceable(spot);
    }

    private boolean somethingFallingOn(PlayerEntity target) {
        Box column = target.getBoundingBox().stretch(0.0, mc.world.getTopYInclusive() - target.getBoundingBox().maxY, 0.0);
        for (Entity entity : mc.world.getOtherEntities(null, column)) {
            if (entity instanceof FallingBlockEntity) return true;
        }
        return false;
    }

    private boolean place(BlockPos position, Direction direction) {
        boolean doRotate = !rotate.getValue().equalsIgnoreCase("None");
        int priority = Anatorius.ROTATION_MANAGER.getModulePriority(this);
        if (rotate.getValue().equalsIgnoreCase("Normal")) {
            Vec3d hit = Vec3d.ofCenter(position).add(direction.getOffsetX() * 0.5, direction.getOffsetY() * 0.5, direction.getOffsetZ() * 0.5);
            Anatorius.ROTATION_MANAGER.rotate(RotationUtils.getRotations(hit, false), priority);
        }
        return WorldUtils.placeBlock(position, direction, Hand.MAIN_HAND, null, doRotate, false, render.getValue(), priority);
    }

    private List<PlayerEntity> findTargets() {
        double maxDistance = MathHelper.square(enemyRange.getValue().doubleValue());
        List<PlayerEntity> targets = new ArrayList<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.getHealth() <= 0.0f) continue;
            if (player.isCreative() || player.isSpectator() || EntityUtils.isBot(player)) continue;
            if (Anatorius.FRIEND_MANAGER.contains(player.getName().getString())) continue;
            double dx = player.getX() - mc.player.getX();
            double dz = player.getZ() - mc.player.getZ();
            if (dx * dx + dz * dz > maxDistance) continue;
            targets.add(player);
        }
        targets.sort((a, b) -> Double.compare(mc.player.squaredDistanceTo(a), mc.player.squaredDistanceTo(b)));
        return targets;
    }

    private int findFallingBlock() {
        boolean noSwitch = autoSwitch.getValue().equalsIgnoreCase("None");
        if (noSwitch) return isDroppable(mc.player.getMainHandStack()) ? mc.player.getInventory().getSelectedSlot() : -1;

        int end = autoSwitch.getValue().equalsIgnoreCase("AltSwap") ? InventoryUtils.INVENTORY_END : InventoryUtils.HOTBAR_END;
        return InventoryUtils.find(this::isDroppable, InventoryUtils.HOTBAR_START, end);
    }

    private boolean isDroppable(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return false;
        if (material.getValue().equalsIgnoreCase("Anvil")) return item.getBlock() instanceof AnvilBlock;
        return item.getBlock() instanceof FallingBlock;
    }

    @Override
    public String getMetaData() {
        return String.valueOf(dropped);
    }
}
