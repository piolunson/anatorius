package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerMoveEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.events.impl.UpdateMovementEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.WhitelistSetting;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer3D;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.MovementUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.rotations.RotationUtils;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.block.Block;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.entity.MovementType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

@RegisterModule(name = "Scaffold", description = "Automatically places blocks beneath you as you walk.", category = Module.Category.MOVEMENT)
public class ScaffoldModule extends Module {
    public final ModeSetting    mode       = new ModeSetting("Mode", "The anti-cheat the scaffold is tuned for.", "Grim", new String[]{"Grim", "NCP"});
    public final ModeSetting    autoSwitch = new ModeSetting("Switch", "How the block is switched to.", "Silent", InventoryUtils.SWITCH_MODES);
    public final BooleanSetting tower      = new BooleanSetting("Tower", "Build straight up while holding jump without moving.", true);
    public final BooleanSetting keepY      = new BooleanSetting("KeepY", "Keep bridging at the height you started on.", false);
    public final BooleanSetting safeWalk   = new BooleanSetting("SafeWalk", "Stop at block edges so you never walk off.", true);
    public final ModeSetting    listMode   = new ModeSetting("List", "Which blocks it may use. Whitelist: only the ones on the list. Blacklist: any but those.", "None", new String[]{"None", "Whitelist", "Blacklist"});
    public final WhitelistSetting blocks   = new WhitelistSetting("Blocks", "The blocks for List (Whitelist: only these, Blacklist: never these).", new ModeSetting.Visibility(listMode, "Whitelist", "Blacklist"), WhitelistSetting.Type.BLOCKS);

    public final CategorySetting renderCategory = new CategorySetting("Render", "Show the block being placed.");
    public final BooleanSetting  render       = new BooleanSetting("Render", "Draw the placed block.", new CategorySetting.Visibility(renderCategory), true);
    public final ColorSetting    fillColor    = new ColorSetting("FillColor", "Box fill.", new CategorySetting.Visibility(renderCategory), ColorUtils.getDefaultFillColor());
    public final ColorSetting    outlineColor = new ColorSetting("OutlineColor", "Box outline.", new CategorySetting.Visibility(renderCategory), ColorUtils.getDefaultOutlineColor());

    private static final int RENDER_TICKS = 6;
    private static final int MAX_MISALIGNED_TICKS = 2;

    private int      lockedY;
    private BlockPos lastPlaced;
    private int      lastPlacedAge;
    private int      misalignedTicks;
    private boolean  rotating;
    private final Timer towerTimer = new Timer();

    @Override
    public void onEnable() {
        if (mc.player != null) lockedY = MathHelper.floor(mc.player.getY()) - 1;
        lastPlaced = null;
        misalignedTicks = 0;
        rotating = false;
        towerTimer.reset();
    }

    @Override
    public void onDisable() {
        lastPlaced = null;
        rotating = false;
    }

    @SubscribeEvent
    public void onUpdate(PlayerUpdateEvent event) {
        rotating = false;
        if (getNull() || mc.player.isGliding() || mc.player.hasVehicle() || findBlock() == -1) return;

        if (isTowering() || mc.player.isOnGround() && !keepY.getValue()) lockedY = MathHelper.floor(mc.player.getY()) - 1;

        if (isTowering() && mode.getValue().equals("Grim") && mc.player.isOnGround()) {
            mc.player.setSprinting(false);
        }
        if (mode.getValue().equals("Grim") && mc.player.isSprinting() && !isTowering()) {
            mc.player.setSprinting(false);
        }

        Vec3d velocity = mc.player.getVelocity();
        Vec3d predicted = mc.player.getEntityPos().add(velocity.x, Math.max(velocity.y, 0.0), velocity.z);
        Target target = findTarget(predicted);
        if (target == null) target = findTarget(mc.player.getEntityPos());
        if (target == null) return;

        Vec3d aim = hitFor(target, findBlock());
        float[] rotations = RotationUtils.getRotations(aim.x, aim.y, aim.z, false);
        Anatorius.ROTATION_MANAGER.rotate(rotations, this);
        rotating = true;
    }

    @SubscribeEvent
    public void onUpdateMovementPost(UpdateMovementEvent.Post event) {
        if (getNull() || mc.player.isGliding() || mc.player.hasVehicle()) return;

        int slot = findBlock();
        if (slot == -1) return;

        Target target = findTarget(mc.player.getEntityPos());
        if (target == null) {
            misalignedTicks = 0;
            return;
        }

        boolean aligned = rotating && WorldUtils.canSeeFace(Anatorius.ROTATION_MANAGER.getServerYaw(), Anatorius.ROTATION_MANAGER.getServerPitch(), target.pos(), target.direction());
        boolean packetRotate = false;
        if (!aligned) {
            if (mode.getValue().equals("Grim") && ++misalignedTicks <= MAX_MISALIGNED_TICKS) return;
            packetRotate = true;
        }
        misalignedTicks = 0;

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        boolean needsSwitch = slot != previousSlot;
        if (needsSwitch) InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);

        boolean placed = WorldUtils.placeBlockAt(target.pos(), target.direction(), Hand.MAIN_HAND, hitFor(target, slot), packetRotate, false,
                Anatorius.ROTATION_MANAGER.getModulePriority(this));

        if (needsSwitch && !autoSwitch.getValue().equalsIgnoreCase("Normal")) InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);

        if (!placed) return;
        lastPlaced = target.pos();
        lastPlacedAge = mc.player.age;

        if (isTowering() && mode.getValue().equals("NCP") && target.direction() == Direction.DOWN) {
            Vec3d velocity = mc.player.getVelocity();
            if (towerTimer.hasTimeElapsed(1500L)) {
                mc.player.setVelocity(velocity.x, -0.28, velocity.z);
                towerTimer.reset();
            } else {
                mc.player.setVelocity(velocity.x, 0.42, velocity.z);
            }
        }
    }

    private boolean isTowering() {
        return tower.getValue() && mc.player.input.playerInput.jump() && !MovementUtils.isMoving();
    }

    private record Target(BlockPos pos, Direction direction, Vec3d hitVec) {}

    private Target findTarget(Vec3d position) {
        if (!isTowering() && !mc.world.getBlockState(BlockPos.ofFloored(position.x, position.y - 0.001, position.z))
                .getCollisionShape(mc.world, BlockPos.ofFloored(position.x, position.y - 0.001, position.z)).isEmpty()) return null;
        int y = keepY.getValue() && !isTowering() ? lockedY : MathHelper.floor(position.y) - 1;
        BlockPos below = BlockPos.ofFloored(position.x, y, position.z);
        if (!WorldUtils.isPlaceable(below)) return null;

        Target direct = targetFor(below);
        if (direct != null) return direct;

        Target best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction side : Direction.Type.HORIZONTAL) {
            BlockPos support = below.offset(side);
            if (!WorldUtils.isPlaceable(support)) continue;
            Target candidate = targetFor(support);
            if (candidate == null) continue;
            double distance = mc.player.squaredDistanceTo(Vec3d.ofCenter(support));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private Target targetFor(BlockPos pos) {
        Direction direction = WorldUtils.getDirection(pos, false);
        if (direction == null) return null;
        Vec3d hitVec = WorldUtils.getHitVector(pos, direction);
        if (mc.player.getEyePos().squaredDistanceTo(hitVec) > MathHelper.square(mc.player.getBlockInteractionRange())) return null;
        return new Target(pos, direction, hitVec);
    }

    private int findBlock() {
        int selected = mc.player.getInventory().getSelectedSlot();
        if (isScaffoldBlock(mc.player.getInventory().getStack(selected))) return selected;
        return InventoryUtils.find(this::isScaffoldBlock, InventoryUtils.HOTBAR_START, InventoryUtils.HOTBAR_END);
    }

    private Vec3d hitFor(Target target, int slot) {
        if (slot == -1 || target.direction().getAxis() == Direction.Axis.Y) return target.hitVec();
        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (!(stack.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof SlabBlock)) return target.hitVec();
        return new Vec3d(target.hitVec().x, target.pos().getY() + 0.75, target.hitVec().z);
    }

    private boolean isScaffoldBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        Block block = blockItem.getBlock();
        if (block instanceof FallingBlock || WorldUtils.RIGHT_CLICKABLE_BLOCKS.contains(block)) return false;
        if (block instanceof SlabBlock) return listAllows(block);
        if (!listAllows(block)) return false;
        return block.getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN);
    }

    private boolean listAllows(Block block) {
        if (listMode.getValue().equals("Whitelist") && !blocks.getWhitelist().isEmpty() && !blocks.isWhitelistContains(block)) return false;
        return !(listMode.getValue().equals("Blacklist") && blocks.isWhitelistContains(block));
    }

    @SubscribeEvent
    public void onMove(PlayerMoveEvent event) {
        if (getNull() || !safeWalk.getValue()) return;
        if (event.getMovementType() != MovementType.SELF) return;
        if (!mc.player.isOnGround() || mc.player.input.playerInput.sneak()) return;

        double increment = 0.05;
        double x = event.getX();
        double z = event.getZ();

        while (x != 0.0 && isOffsetEmpty(x, 0.0)) x = shrink(x, increment);
        while (z != 0.0 && isOffsetEmpty(0.0, z)) z = shrink(z, increment);
        while (x != 0.0 && z != 0.0 && isOffsetEmpty(x, z)) { x = shrink(x, increment); z = shrink(z, increment); }

        if (x == event.getX() && z == event.getZ()) return;
        event.setX(x);
        event.setZ(z);
        event.setCancelled(true);
    }

    private static double shrink(double value, double increment) {
        if (value > -increment && value < increment) return 0.0;
        return value > 0.0 ? value - increment : value + increment;
    }

    private boolean isOffsetEmpty(double x, double z) {
        Box box = mc.player.getBoundingBox().offset(x, -mc.player.getStepHeight(), z);
        return !mc.world.getBlockCollisions(mc.player, box).iterator().hasNext();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (getNull() || !render.getValue() || lastPlaced == null) return;

        int age = mc.player.age - lastPlacedAge;
        if (age > RENDER_TICKS) { lastPlaced = null; return; }

        float remaining = 1.0f - (float) age / RENDER_TICKS;
        Box box = new Box(lastPlaced);
        Renderer3D.renderBox(event.getMatrices(), box, fade(fillColor.getColor(), remaining));
        Renderer3D.renderBoxOutline(event.getMatrices(), box, fade(outlineColor.getColor(), remaining));
    }

    private static Color fade(Color color, float remaining) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                MathHelper.clamp((int) (color.getAlpha() * remaining), 0, 255));
    }
}
