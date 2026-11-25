package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.DamageUtils;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.rotations.RotationUtils;
import dev.project.anatorius.utils.system.Timer;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

@RegisterModule(name = "AutoAnchor", description = "Automatically places and detonates respawn anchors.", category = Module.Category.COMBAT)
public class AutoAnchorModule extends Module {
    public final ModeSetting autoSwitch = new ModeSetting("Switch", "Auto-switch mode for selecting items.", "Silent", InventoryUtils.SWITCH_MODES);
    public final ModeSetting rotate = new ModeSetting("Rotate", "Rotation mode during placement.", "Packet", new String[]{"None", "Packet", "Normal"});
    public final BooleanSetting instantExplode = new BooleanSetting("InstantExplode", "Places and charges anchors until they detonate.", true);
    public final BooleanSetting airPlace = new BooleanSetting("AirPlace", "Allows placing anchors in mid-air.", false);

    public final NumberSetting targetRange = new NumberSetting("TargetRange", "Max target distance.", 10.0, 1.0, 15.0);
    public final NumberSetting placeRange = new NumberSetting("PlaceRange", "Max placement range.", 5.0, 1.0, 6.0);
    public final NumberSetting placeDelay = new NumberSetting("PlaceDelay", "Ticks to wait between placements.", 0, 0, 20);
    public final NumberSetting actionDelay = new NumberSetting("ActionDelay", "Delay between charging/detonating in ms.", 50, 0, 1000);

    public final NumberSetting minimumDamage = new NumberSetting("MinimumDamage", "Minimum damage to deal.", 6.0, 2.0, 20.0);
    public final NumberSetting maximumSelfDamage = new NumberSetting("MaximumSelfDamage", "Maximum self damage.", 10.0, 2.0, 20.0);
    public final NumberSetting facePlaceHealth = new NumberSetting("FacePlaceHealth", "Health threshold to ignore damage minimums.", 5.0, 0.0, 20.0);

    @Getter private PlayerEntity target;
    private BlockPos calcPos;
    private BlockPos explodePos;

    private final Timer placeTimer = new Timer();
    private final Timer actionTimer = new Timer();
    private int delayTicks = 0;

    @Override
    public void onEnable() {
        target = null;
        calcPos = null;
        explodePos = null;
        delayTicks = 0;
    }

    @Override
    public void onDisable() {
        target = null;
        calcPos = null;
        explodePos = null;
        delayTicks = 0;
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.world.getRegistryKey() == World.NETHER) return;

        prepare();
        interact();
    }

    private void prepare() {
        target = findTarget();
        if (target == null) {
            calcPos = null;
            explodePos = null;
            return;
        }

        explodePos = findBestExplode();
        if (explodePos == null) {
            calcPos = findBestPlacement();
        } else {
            calcPos = null;
        }

        if (rotate.getValue().equalsIgnoreCase("Packet") || rotate.getValue().equalsIgnoreCase("Normal")) {
            BlockPos rotPos = explodePos != null ? explodePos : calcPos;
            if (rotPos != null) {
                applyRotation(rotPos.toCenterPos());
            }
        }
    }

    private void interact() {
        if (placeDelay.getValue().intValue() > 0 && delayTicks < placeDelay.getValue().intValue()) {
            delayTicks++;
            return;
        }

        if (calcPos != null && placeTimer.hasTimeElapsed(50L)) {
            placeAnchor(calcPos);
            delayTicks = 0;
        }

        if (explodePos != null && actionTimer.hasTimeElapsed(actionDelay.getValue().longValue())) {
            advanceAnchor(explodePos);
        }
    }

    private PlayerEntity findTarget() {
        PlayerEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        double rangeSq = targetRange.getValue().doubleValue() * targetRange.getValue().doubleValue();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.isDead() || player.getHealth() <= 0) continue;
            if (Anatorius.FRIEND_MANAGER.contains(player.getName().getString())) continue;

            double distance = mc.player.squaredDistanceTo(player);
            if (distance > rangeSq) continue;

            if (distance < closestDistance) {
                closestDistance = distance;
                closest = player;
            }
        }
        return closest;
    }

    private BlockPos findBestExplode() {
        BlockPos best = null;
        float bestDamage = -1f;
        double range = placeRange.getValue().doubleValue();
        double rangeSq = range * range;
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = (int) Math.floor(playerPos.getX() - range); x <= Math.ceil(playerPos.getX() + range); x++) {
            for (int y = (int) Math.floor(playerPos.getY() - range); y <= Math.ceil(playerPos.getY() + range); y++) {
                for (int z = (int) Math.floor(playerPos.getZ() - range); z <= Math.ceil(playerPos.getZ() + range); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (mc.player.squaredDistanceTo(pos.toCenterPos()) > rangeSq) continue;

                    BlockState state = mc.world.getBlockState(pos);
                    if (!(state.getBlock() instanceof RespawnAnchorBlock)) continue;

                    int charges = state.get(RespawnAnchorBlock.CHARGES);
                    int requiredCharges = RespawnAnchorBlock.MAX_CHARGES;

                    if (charges < requiredCharges) {
                        float score = (float) -mc.player.squaredDistanceTo(pos.toCenterPos());
                        if (best == null || score > bestDamage) {
                            bestDamage = score;
                            best = pos;
                        }
                        continue;
                    }

                    float damage = DamageUtils.calculateAnchorDamage(pos.toCenterPos(), target);
                    float selfDamage = DamageUtils.calculateAnchorDamage(pos.toCenterPos(), mc.player);

                    if (selfDamage > maximumSelfDamage.getValue().floatValue()) continue;

                    if (best == null || damage > bestDamage) {
                        bestDamage = damage;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private BlockPos findBestPlacement() {
        BlockPos best = null;
        float bestDamage = 0f;
        double range = placeRange.getValue().doubleValue();
        double rangeSq = range * range;
        BlockPos playerPos = mc.player.getBlockPos();

        for (int x = (int) Math.floor(playerPos.getX() - range); x <= Math.ceil(playerPos.getX() + range); x++) {
            for (int y = (int) Math.floor(playerPos.getY() - range); y <= Math.ceil(playerPos.getY() + range); y++) {
                for (int z = (int) Math.floor(playerPos.getZ() - range); z <= Math.ceil(playerPos.getZ() + range); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (mc.player.squaredDistanceTo(pos.toCenterPos()) > rangeSq) continue;
                    if (!canPlaceAnchor(pos)) continue;

                    float damage = DamageUtils.calculateAnchorDamage(pos.toCenterPos(), target);
                    float selfDamage = DamageUtils.calculateAnchorDamage(pos.toCenterPos(), mc.player);

                    if (selfDamage > maximumSelfDamage.getValue().floatValue()) continue;
                    if (damage < getMinDamage()) continue;

                    if (damage > bestDamage) {
                        bestDamage = damage;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private boolean canPlaceAnchor(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (!mc.world.canPlace(Blocks.RESPAWN_ANCHOR.getDefaultState(), pos, ShapeContext.absent())) return false;

        if (airPlace.getValue()) return true;

        boolean hasSupport = false;
        for (Direction direction : Direction.values()) {
            if (!mc.world.getBlockState(pos.offset(direction)).isReplaceable()) {
                hasSupport = true;
                break;
            }
        }
        return hasSupport;
    }

    private float getMinDamage() {
        if (target == null) return minimumDamage.getValue().floatValue();
        if (target.getHealth() + target.getAbsorptionAmount() <= facePlaceHealth.getValue().floatValue()) {
            return 0.5f;
        }
        return minimumDamage.getValue().floatValue();
    }

    private void placeAnchor(BlockPos pos) {
        int anchorSlot = InventoryUtils.findHotbar(Items.RESPAWN_ANCHOR);
        boolean offhand = mc.player.getOffHandStack().getItem() == Items.RESPAWN_ANCHOR;

        if (anchorSlot == -1 && !offhand) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        boolean silent = autoSwitch.getValue().equalsIgnoreCase("Silent");
        boolean switched = false;

        if (!offhand && anchorSlot != prevSlot) {
            if (autoSwitch.getValue().equalsIgnoreCase("None")) return;
            InventoryUtils.switchSlot(autoSwitch.getValue(), anchorSlot, prevSlot);
            switched = true;
        }

        Direction side = getPlaceableSide(pos);
        if (side == null) return;

        Vec3d hitVec = pos.toCenterPos().add(new Vec3d(side.getVector()).multiply(0.5));
        BlockHitResult hitResult = new BlockHitResult(hitVec, side, pos, false);

        mc.interactionManager.interactBlock(mc.player, offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND);
        placeTimer.reset();

        if (switched && silent) {
            InventoryUtils.switchSlot("Normal", prevSlot, mc.player.getInventory().getSelectedSlot());
        }
    }

    private void advanceAnchor(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (!(state.getBlock() instanceof RespawnAnchorBlock)) return;

        int charges = state.get(RespawnAnchorBlock.CHARGES);
        BlockHitResult hitResult = new BlockHitResult(pos.toCenterPos(), Direction.UP, pos, false);

        int requiredCharges = RespawnAnchorBlock.MAX_CHARGES;

        if (instantExplode.getValue()) {
            for (int i = charges; i < requiredCharges; i++) {
                doAction(hitResult, true);
            }
            doAction(hitResult, false);
            actionTimer.reset();
            return;
        }

        if (charges < requiredCharges) {
            doAction(hitResult, true);
        } else {
            doAction(hitResult, false);
        }
        actionTimer.reset();
    }

    private void doAction(BlockHitResult hitResult, boolean isCharging) {
        int targetSlot = -1;
        boolean offhand = false;

        if (isCharging) {
            offhand = mc.player.getOffHandStack().getItem() == Items.GLOWSTONE;
            targetSlot = InventoryUtils.findHotbar(Items.GLOWSTONE);
        } else {
            targetSlot = findDetonateSlot();
        }

        if (targetSlot == -1 && !offhand) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        boolean silent = autoSwitch.getValue().equalsIgnoreCase("Silent");
        boolean switched = false;

        if (!offhand && targetSlot != prevSlot) {
            if (autoSwitch.getValue().equalsIgnoreCase("None")) return;
            InventoryUtils.switchSlot(autoSwitch.getValue(), targetSlot, prevSlot);
            switched = true;
        }

        boolean wasSneaking = mc.player.isSneaking();
        if (wasSneaking) mc.player.setSneaking(false);

        mc.interactionManager.interactBlock(mc.player, offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND);

        if (wasSneaking) mc.player.setSneaking(true);

        if (switched && silent) {
            InventoryUtils.switchSlot("Normal", prevSlot, mc.player.getInventory().getSelectedSlot());
        }
    }

    private int findDetonateSlot() {
        int prevSlot = mc.player.getInventory().getSelectedSlot();
        ItemStack prevStack = mc.player.getInventory().getStack(prevSlot);

        if (prevStack.getItem() != Items.GLOWSTONE && !(prevStack.getItem() instanceof BlockItem)) {
            return prevSlot;
        }
        if (prevStack.getItem() != Items.GLOWSTONE) {
            return prevSlot;
        }

        int totemSlot = InventoryUtils.findHotbar(Items.TOTEM_OF_UNDYING);
        if (totemSlot != -1) return totemSlot;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!(stack.getItem() instanceof BlockItem) && stack.getItem() != Items.GLOWSTONE) {
                return i;
            }
        }

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() != Items.GLOWSTONE) {
                return i;
            }
        }
        return -1;
    }

    private Direction getPlaceableSide(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!mc.world.getBlockState(pos.offset(direction)).isReplaceable()) {
                return direction;
            }
        }
        return airPlace.getValue() ? Direction.UP : null;
    }

    private void applyRotation(Vec3d target) {
        boolean packetOnly = rotate.getValue().equalsIgnoreCase("Packet");
        int priority = Anatorius.ROTATION_MANAGER.getModulePriority(this);
        float[] rotations = RotationUtils.getRotations(target.x, target.y, target.z, false);

        if (packetOnly) {
            Anatorius.ROTATION_MANAGER.packetRotate(rotations, priority);
        } else {
            Anatorius.ROTATION_MANAGER.rotate(rotations, priority);
            Anatorius.ROTATION_MANAGER.packetRotate(rotations[0], rotations[1]);
        }
    }

    @Override
    public String getMetaData() {
        return target == null ? "None" : target.getName().getString();
    }
}
