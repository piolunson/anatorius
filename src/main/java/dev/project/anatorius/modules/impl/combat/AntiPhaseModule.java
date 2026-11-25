package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.rotations.RotationUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

@RegisterModule(
        name = "AntiPhase",
        description = "Places scaffolding or item frames inside target players to block ender pearl escapes.",
        category = Module.Category.COMBAT
)
public class AntiPhaseModule extends Module {
    public final ModeSetting mode = new ModeSetting(
            "Mode", "Block placement method.", "Scaffolding",
            new String[]{"Scaffolding", "Frames"});

    public final BooleanSetting doubleFill = new BooleanSetting(
            "DoubleFill", "Attempt to fill the item frame twice.", true);

    public final NumberSetting range = new NumberSetting(
            "Range", "Max block placement range.", 6.0, 1.0, 6.0);

    public final NumberSetting delay = new NumberSetting(
            "Delay", "Delay between placement attempts (ms).", 0, 0, 1000);

    public final NumberSetting targetRange = new NumberSetting(
            "TargetRange", "Max target scan range.", 5.0, 1.0, 10.0);

    public final ModeSetting rotate = new ModeSetting(
            "Rotate", "Rotation mode during placement.", "Packet",
            new String[]{"None", "Packet", "Normal"});

    public final BooleanSetting strictDirection = new BooleanSetting(
            "StrictDirection", "Only use directions that face the player.", false);

    public final ModeSetting autoSwitch = new ModeSetting(
            "Switch", "Auto-switch mode for selecting items.", "Silent", InventoryUtils.SWITCH_MODES);

    private final List<BlockPos> toPlace = new ArrayList<>();

    private ItemFrameEntity pendingFrameInteract = null;
    private int             pendingOldSlot       = -1;

    private long lastActionTime = 0L;

    @Override
    public void onEnable() {
        toPlace.clear();
        pendingFrameInteract = null;
        pendingOldSlot       = -1;
        lastActionTime       = 0L;
    }

    @SubscribeEvent
    public void onDisable() {
        toPlace.clear();
        pendingFrameInteract = null;
        pendingOldSlot       = -1;
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        long delayMs = delay.getValue().longValue();
        if (delayMs > 0 && System.currentTimeMillis() - lastActionTime < delayMs) return;

        if (pendingFrameInteract != null) {
            if (mc.player.getMainHandStack().getItem().equals(Items.ITEM_FRAME)) {
                mc.player.networkHandler.sendPacket(
                        PlayerInteractEntityC2SPacket.interact(
                                pendingFrameInteract, mc.player.isSneaking(), Hand.MAIN_HAND));
                mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                InventoryUtils.switchBack(autoSwitch.getValue(), mc.player.getInventory().getSelectedSlot(), pendingOldSlot);
            }
            pendingFrameInteract = null;
            pendingOldSlot       = -1;
        }

        toPlace.clear();

        if (mc.player.isUsingItem()) return;

        int itemSlot = getItemSlot();
        if (itemSlot == -1) return;

        Entity target = findTarget();
        if (target == null) return;

        BlockPos pos = target.getBlockPos();
        boolean framesMode = mode.getValue().equals("Frames");
        if (framesMode) pos = pos.down();

        double rangeSq = MathHelper.square(range.getValue().doubleValue());
        if (mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > rangeSq)
            return;

        if (!framesMode) {
            Direction side = WorldUtils.getDirection(pos, null, strictDirection.getValue());
            if (side == null) return;
            toPlace.add(pos);
        } else {
            BlockPos below = pos;
            boolean spotFree = mc.world.getBlockState(pos.up()).isReplaceable();
            boolean hasSolid = !mc.world.getBlockState(below).isReplaceable();

            if (!spotFree || !hasSolid) {
                if (doubleFill.getValue()) {
                    ItemFrameEntity frame = getItemFrameAt(pos, Direction.UP);
                    if (frame != null && frame.getHeldItemStack().isEmpty()) {
                        int oldSlot = mc.player.getInventory().getSelectedSlot();
                        InventoryUtils.switchSlot(autoSwitch.getValue(), itemSlot, oldSlot);
                        pendingFrameInteract = frame;
                        pendingOldSlot       = oldSlot;
                    }
                }
                return;
            }
            toPlace.add(pos);
        }

        if (toPlace.isEmpty()) return;

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        boolean switched = itemSlot != previousSlot;
        if (switched) InventoryUtils.switchSlot(autoSwitch.getValue(), itemSlot, previousSlot);

        boolean doRotate     = !rotate.getValue().equalsIgnoreCase("None");
        boolean packetRotate = rotate.getValue().equalsIgnoreCase("Packet");
        int     rotPriority  = Anatorius.ROTATION_MANAGER.getModulePriority(this);

        for (BlockPos placePos : toPlace) {
            Direction dir = framesMode
                    ? Direction.UP
                    : WorldUtils.getDirection(placePos, null, strictDirection.getValue());
            if (dir == null) continue;

            if (doRotate) applyRotation(placePos, dir, packetRotate, rotPriority);

            WorldUtils.placeBlock(placePos, dir, Hand.MAIN_HAND, false, false);
        }

        if (switched) InventoryUtils.switchBack(autoSwitch.getValue(), itemSlot, previousSlot);

        lastActionTime = System.currentTimeMillis();
    }

    @Override
    public String getMetaData() {
        return mode.getValue();
    }

    private Entity findTarget() {
        if (mc.world == null || mc.player == null) return null;

        double rangeSq     = MathHelper.square(targetRange.getValue().doubleValue());
        Entity closest     = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            if (!(e instanceof net.minecraft.entity.LivingEntity)) continue;
            if (!e.isAlive()) continue;

            double dist = mc.player.squaredDistanceTo(e);
            if (dist < rangeSq && dist < closestDist) {
                closestDist = dist;
                closest     = e;
            }
        }
        return closest;
    }

    private int getItemSlot() {
        return mode.getValue().equals("Frames")
                ? InventoryUtils.findHotbar(Items.ITEM_FRAME)
                : InventoryUtils.findHotbar(Items.SCAFFOLDING);
    }

    private ItemFrameEntity getItemFrameAt(BlockPos pos, Direction facing) {
        List<Entity> entities = mc.world.getOtherEntities(null, new Box(pos));
        for (Entity e : entities) {
            if (e instanceof ItemFrameEntity frame && frame.getHorizontalFacing() == facing) {
                return frame;
            }
        }
        return null;
    }

    private void applyRotation(BlockPos pos, Direction dir, boolean packetOnly, int priority) {
        double hitX = pos.getX() + 0.5 + dir.getOffsetX() * 0.5;
        double hitY = pos.getY() + 0.5 + dir.getOffsetY() * 0.5;
        double hitZ = pos.getZ() + 0.5 + dir.getOffsetZ() * 0.5;
        float[] rot = RotationUtils.getRotations(hitX, hitY, hitZ, false);
        if (packetOnly) {
            Anatorius.ROTATION_MANAGER.packetRotate(rot, priority);
        } else {
            Anatorius.ROTATION_MANAGER.rotate(rot, priority);
            Anatorius.ROTATION_MANAGER.packetRotate(rot[0], rot[1]);
        }
    }
}
