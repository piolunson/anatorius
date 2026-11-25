package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.EntitySpawnEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.HoleUtils;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import dev.project.anatorius.utils.rotations.RotationUtils;
import net.minecraft.util.math.Vec3d;

@RegisterModule(name = "AntiPearl", description = "Places obsidian in the air to stop players pearls from reaching their destination.", category = Module.Category.COMBAT)
public class AntiPearlModule extends Module {
    public ModeSetting autoSwitch = new ModeSetting("Switch", "The mode that will be used for automatically switching to necessary items.", "Silent", InventoryUtils.SWITCH_MODES);
    public BooleanSetting rotate = new BooleanSetting("Rotate", "Sends a packet rotation whenever placing a block.", true);

    public NumberSetting range = new NumberSetting("Range", "The maximum range at which the blocks will be placed at.", 5.0, 0.0, 12.0);
    public NumberSetting enemyRange = new NumberSetting("EnemyRange", "The maximum distance at which the target should be at.", 8.0f, 0.0f, 16.0f);
    public NumberSetting distance = new NumberSetting("Distance", "The distance at which the obisidan block will be placed from the player.", 4.0f, 3.0f, 6.0f);

    public BooleanSetting holeCheck = new BooleanSetting("HoleCheck", "Only self traps whenever you are in a hole.", false);
    public BooleanSetting whileEating = new BooleanSetting("WhileEating", "Places blocks normally while eating.", true);
    public BooleanSetting itemDisable = new BooleanSetting("ItemDisable", "Toggles off the module whenever you run out of items to place with.", true);

    public BooleanSetting render = new BooleanSetting("Render", "Whether or not to render the place position.", true);

    @SubscribeEvent
    public void onEntitySpawn(EntitySpawnEvent event) {
        if(!(event.getEntity() instanceof EnderPearlEntity pearl) || (!whileEating.getValue() && mc.player.isUsingItem())) return;

        if(!(pearl.getOwner() instanceof PlayerEntity owner) || !validTarget(owner)) return;

        catchPearl(pearl);
    }

    private void catchPearl(EnderPearlEntity pearl) {
        if (autoSwitch.getValue().equalsIgnoreCase("None") && !(mc.player.getMainHandStack().getItem() instanceof BlockItem)) {
            if (itemDisable.getValue()) {
                Anatorius.CHAT_MANAGER.tagged("You are currently not holding any blocks.", getName());
                setToggled(false);
            }
            return;
        }

        int slot = InventoryUtils.findHardestBlock(0, autoSwitch.getValue().equalsIgnoreCase("AltSwap") ? 35 : 8);
        int previousSlot = mc.player.getInventory().getSelectedSlot();

        if (slot == -1) {
            if (itemDisable.getValue()) {
                Anatorius.CHAT_MANAGER.tagged("No blocks could be found in your hotbar.", getName());
                setToggled(false);
            }
            return;
        }

        BlockPos pos = findBlockOnPath(pearl);
        if (pos == null) return;

        Direction direction = WorldUtils.getDirection(pos, false);
        if (rotate.getValue()) {
            Vec3d aim = direction == null ? Vec3d.ofCenter(pos) : Vec3d.ofCenter(pos).add(direction.getOffsetX() * 0.5, direction.getOffsetY() * 0.5, direction.getOffsetZ() * 0.5);
            float[] r = RotationUtils.getRotations(aim.x, aim.y, aim.z, false);
            Anatorius.ROTATION_MANAGER.packetRotate(r[0], r[1]);
        }
        InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);
        WorldUtils.placeBlock(pos, direction, Hand.MAIN_HAND, false, false, render.getValue());
        InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
    }

    private BlockPos findBlockOnPath(EnderPearlEntity pearl) {
        Vec3d pos = new Vec3d(pearl.getX(), pearl.getY(), pearl.getZ());
        Vec3d vel = pearl.getVelocity();
        if (vel.lengthSquared() < 1.0E-4) return null;
        int lead = Math.max(1, (int) Math.ceil(Anatorius.SERVER_MANAGER.getPing() / 50.0) + 1);
        double rangeSq = MathHelper.square(range.getValue().doubleValue());
        BlockPos last = null;
        for (int tick = 0; tick < 60; tick++) {
            Vec3d next = pos.add(vel);
            vel = vel.multiply(0.99).add(0.0, -0.03, 0.0);
            if (mc.world.raycast(new net.minecraft.world.RaycastContext(pos, next, net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, pearl)).getType() != HitResult.Type.MISS) return null;
            pos = next;
            if (tick < lead) continue;
            BlockPos block = BlockPos.ofFloored(pos);
            if (block.equals(last)) continue;
            last = block;
            if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(block)) > rangeSq) continue;
            if (!mc.world.getBlockState(block).isReplaceable() || !WorldUtils.isPlaceable(block)) continue;
            return block;
        }
        return null;
    }

    private boolean validTarget(PlayerEntity player) {
        if(player == mc.player) return false;
        if (mc.player.squaredDistanceTo(player) > MathHelper.square(enemyRange.getValue().doubleValue())) return false;
        if (Anatorius.FRIEND_MANAGER.contains(player.getName().getString())) return false;
        if (holeCheck.getValue() && !HoleUtils.isPlayerInHole(player)) return false;
        return true;
    }
}
