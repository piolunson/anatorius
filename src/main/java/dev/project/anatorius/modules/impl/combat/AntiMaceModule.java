package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.KeyboardTickEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.mixins.accessors.ClientPlayerInteractionManagerAccessor;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RegisterModule(
        name = "AntiMace",
        description = "Stops mace dives from above: cobwebs, obsidian, powder snow or slime in the dive path, a shield raised in time, a pearl away or a step aside.",
        category = Module.Category.COMBAT
)
public class AntiMaceModule extends Module {
    public ModeSetting    mode     = new ModeSetting   ("Mode",     "How the dive is stopped. Web: cobwebs over your head, it sticks and loses the fall. "
            + "Obsidian / Block: it lands on them out of reach. Snow: powder snow, it lands on it. Slime: it bounces back up. "
            + "Shield: raise your shield, facing it. Pearl: throw a pearl away from it (good against manual divers). Dodge: step out from under it. "
            + "Auto: the first of Web, Obsidian, Block, Snow, Slime you have, else Shield, else Dodge.", "Auto",
            new String[]{"Auto", "Web", "Obsidian", "Block", "Snow", "Slime", "Shield", "Pearl", "Dodge"});
    public NumberSetting  range    = new NumberSetting ("Range",    "How close to you a dive has to be heading to count (blocks, where it would come down).", 4.5, 2.0, 8.0);
    public BooleanSetting airPlace = new BooleanSetting("AirPlace", "Place in mid-air (there's rarely anything to place against up there).", true);
    public BooleanSetting grimAir  = new BooleanSetting("GrimAirPlace", "Air places from the offhand, the block swapped there and back around them (Grim lets those through, vanilla doesn't mind).", new BooleanSetting.Visibility(airPlace, true), true);
    public NumberSetting  perTick  = new NumberSetting ("PerTick",  "Blocks placed per tick (each diver needs 1 to 4).", 8, 1, 16);
    public NumberSetting  ticks    = new NumberSetting ("Ticks",    "How many ticks ahead the diver's position is predicted.", 4, 1, 5);
    public NumberSetting  distance = new NumberSetting ("Distance", "How high over you a player has to be before blocks go under it.", 5.0, 4.0, 10.0);
    public NumberSetting  offset   = new NumberSetting ("Offset",   "How many blocks under the diver's feet the blocks go.", 4.0, -1.0, 8.0);
    public ModeSetting    basis    = new ModeSetting   ("Basis",    "Predicted: Offset under where it will be in Ticks. Current: Offset under where it is now (blocks higher up, placed earlier).", "Predicted", new String[]{"Predicted", "Current"});
    public BooleanSetting shield   = new BooleanSetting("ShieldBackup", "Also raise a shield (offhand, or one from the hotbar) in the placing modes, in case something gets through.", true);
    public BooleanSetting rotate   = new BooleanSetting("Rotate",   "Face the attacker while blocking (a shield only blocks what's in front of you).", true);
    public BooleanSetting early    = new BooleanSetting("Early",    "Already react while someone is in the air above you, close enough to dive (other players show up ~3 ticks late, a fast dive is only ~10 ticks).", true);
    public BooleanSetting onlyMace = new BooleanSetting("OnlyMace", "Only react to players holding a mace (silent-swap clients hide it until the hit).", false);
    public BooleanSetting friends  = new BooleanSetting("Friends",  "React to friends too.", false);

    private static final double HORIZON_TICKS = 40;

    public static boolean debug = false;

    public static boolean holdingShield = false;

    private PlayerEntity threat;
    private int holdTicks;
    private boolean usingShield;
    private int previousSlot = -1;
    private Vec3d impact;
    private final Timer pearlTimer = new Timer();
    private final Map<BlockPos, Long> tried = new HashMap<>();

    @Override
    public void onDisable() {
        if (getNull()) return;
        release();
    }

    @Override
    public String getMetaData() {
        return threat == null ? mode.getValue() : threat.getName().getString();
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (getNull()) return;
        int fill = fillSlot();
        boolean mainHandShield = usingShield && mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND;
        int left = fill != -1 && !mainHandShield ? placeUnderDivers(fill) : 0;

        PlayerEntity found = findThreat();
        if (found != null) {
            threat = found;
            holdTicks = 8;
        } else if (threat != null && --holdTicks <= 0) {
            release();
            return;
        }
        if (threat == null) return;

        String m = mode.getValue();
        boolean wantShield = m.equals("Shield") || (m.equals("Auto") && fill == -1)
                || (shield.getValue() && !m.equals("Dodge") && !m.equals("Pearl"));
        if (rotate.getValue() && wantShield) {
            double dx = threat.getX() - mc.player.getX(), dz = threat.getZ() - mc.player.getZ();
            double dy = threat.getEyeY() - mc.player.getEyeY();
            float yaw = dx * dx + dz * dz < 1.0e-4 ? mc.player.getYaw() : (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))), -90.0, 90.0);
            Anatorius.ROTATION_MANAGER.rotate(yaw, pitch, this, 99);
        }
        if (m.equals("Pearl")) throwPearl();
        if (wantShield && (left == 0 || mc.player.getOffHandStack().isOf(Items.SHIELD))) raiseShield();
    }

    private int fillSlot() {
        return switch (mode.getValue()) {
            case "Web" -> InventoryUtils.find(Items.COBWEB, 0, 8);
            case "Obsidian" -> obsidianSlot();
            case "Block" -> solidSlot();
            case "Snow" -> InventoryUtils.find(Items.POWDER_SNOW_BUCKET, 0, 8);
            case "Slime" -> InventoryUtils.find(Items.SLIME_BLOCK, 0, 8);
            case "Auto" -> {
                int[] order = {InventoryUtils.find(Items.COBWEB, 0, 8), obsidianSlot(), solidSlot(),
                        InventoryUtils.find(Items.POWDER_SNOW_BUCKET, 0, 8), InventoryUtils.find(Items.SLIME_BLOCK, 0, 8)};
                int found = -1;
                for (int slot : order) if (slot != -1) { found = slot; break; }
                yield found;
            }
            default -> -1;
        };
    }

    private int obsidianSlot() {
        int slot = InventoryUtils.find(Items.OBSIDIAN, 0, 8);
        return slot != -1 ? slot : InventoryUtils.find(Items.CRYING_OBSIDIAN, 0, 8);
    }

    private int solidSlot() {
        return InventoryUtils.find(stack -> stack.getItem() instanceof net.minecraft.item.BlockItem bi
                && bi.getBlock().getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN)
                && !(bi.getBlock() instanceof net.minecraft.block.FallingBlock), 0, 8);
    }

    private boolean soft(int slot) {
        ItemStack stack = mc.player.getInventory().getStack(slot);
        return stack.isOf(Items.COBWEB) || stack.isOf(Items.POWDER_SNOW_BUCKET);
    }

    private int placeUnderDivers(int fillSlot) {
        long now = System.currentTimeMillis();
        long cooldown = Math.max(250L, Anatorius.SERVER_MANAGER.getPing() * 2L + 100L);
        tried.values().removeIf(time -> now - time > cooldown);

        List<BlockPos> plan = underDivers();
        if (plan.isEmpty()) return 0;

        ItemStack stack = mc.player.getInventory().getStack(fillSlot);
        boolean soft = soft(fillSlot);
        boolean bucket = stack.isOf(Items.POWDER_SNOW_BUCKET);
        int budget = bucket ? 1 : perTick.getValue().intValue();
        if (!mc.player.isCreative()) budget = Math.min(budget, stack.getCount());

        Set<BlockPos> placedNow = new HashSet<>();
        List<BlockPos> spots = new ArrayList<>();
        List<Direction> sides = new ArrayList<>();
        boolean anyAir = false;
        int left = 0;
        for (BlockPos pos : plan) {
            if (tried.containsKey(pos) || !replaceable(pos) || !inReach(pos)) continue;
            if (!soft && !WorldUtils.isPlaceable(pos)) continue;
            Direction side = supportSide(pos, placedNow);
            if (side == null && !airPlace.getValue()) continue;
            if (budget <= 0) { left++; continue; }
            spots.add(pos);
            sides.add(side);
            placedNow.add(pos);
            anyAir |= side == null;
            budget--;
        }
        if (spots.isEmpty()) return left;

        boolean swap = anyAir && grimAir.getValue();
        int previous = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.switchSlot("Silent", fillSlot, previous);
        if (swap) {
            if (mc.player.isUsingItem()) mc.interactionManager.stopUsingItem(mc.player);
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        }
        Hand hand = swap ? Hand.OFF_HAND : Hand.MAIN_HAND;
        for (int i = 0; i < spots.size(); i++) {
            BlockPos pos = spots.get(i);
            boolean ok = WorldUtils.placeBlock(pos, sides.get(i), hand, rotate.getValue(), false, true, 99);
            if (ok) tried.put(pos, now);
            if (debug) System.out.println("[AntiMace] place " + pos.toShortString() + " side=" + sides.get(i) + " hand=" + hand
                    + " item=" + stack.getItem() + " ok=" + ok);
        }
        if (swap) mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        InventoryUtils.switchBack("Silent", fillSlot, previous);
        return left;
    }

    private List<BlockPos> underDivers() {
        double selfY = mc.player.getY();
        int lowest = MathHelper.floor(selfY) + 3;
        int ahead = ticks.getValue().intValue();
        List<PlayerEntity> divers = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator()) continue;
            if (!friends.getValue() && Anatorius.FRIEND_MANAGER.contains(p.getName().getString())) continue;
            if (onlyMace.getValue() && !p.getMainHandStack().isOf(Items.MACE)) continue;
            if (mc.player.squaredDistanceTo(p) > 40.0 * 40.0) continue;
            if (p.getY() - selfY < distance.getValue().doubleValue()) continue;
            if (p.getY() - p.lastY >= 0.0) continue;
            divers.add(p);
        }
        divers.sort(Comparator.comparingDouble(PlayerEntity::getY));

        List<BlockPos> plan = new ArrayList<>();
        for (PlayerEntity p : divers) {
            double vx = p.getX() - p.lastX, vy = p.getY() - p.lastY, vz = p.getZ() - p.lastZ;
            double x = p.getX() + vx * ahead, y = p.getY() + vy * ahead, z = p.getZ() + vz * ahead;
            double base = basis.getValue().equals("Current") ? p.getY() : y;
            int layer = Math.max(MathHelper.floor(base - offset.getValue().doubleValue()), lowest);
            if (layer + 1.0 > p.getY()) continue;
            double half = p.getWidth() / 2.0;
            for (int bx = MathHelper.floor(x - half); bx <= MathHelper.floor(x + half - 1.0e-4); bx++) {
                for (int bz = MathHelper.floor(z - half); bz <= MathHelper.floor(z + half - 1.0e-4); bz++) {
                    BlockPos pos = new BlockPos(bx, layer, bz);
                    if (!plan.contains(pos)) plan.add(pos);
                }
            }
        }
        return plan;
    }

    private boolean inReach(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        double dx = eye.x - MathHelper.clamp(eye.x, pos.getX(), pos.getX() + 1.0);
        double dy = eye.y - MathHelper.clamp(eye.y, pos.getY(), pos.getY() + 1.0);
        double dz = eye.z - MathHelper.clamp(eye.z, pos.getZ(), pos.getZ() + 1.0);
        double range = mc.player.getBlockInteractionRange();
        return dx * dx + dy * dy + dz * dz <= range * range;
    }

    private boolean replaceable(BlockPos pos) {
        return mc.world.getBlockState(pos).isReplaceable();
    }

    private Direction supportSide(BlockPos pos, Set<BlockPos> placedNow) {
        for (Direction d : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP}) {
            BlockPos n = pos.offset(d);
            if (placedNow.contains(n) || tried.containsKey(n) || !replaceable(n)) return d;
        }
        return null;
    }

    private void throwPearl() {
        double dy = threat.getY() - mc.player.getY();
        if (dy > 22.0 || !pearlTimer.hasTimeElapsed(1500)) return;
        int slot = InventoryUtils.find(Items.ENDER_PEARL, 0, 8);
        if (slot == -1 || mc.player.getItemCooldownManager().isCoolingDown(mc.player.getInventory().getStack(slot))) return;
        double ax = mc.player.getX() - threat.getX(), az = mc.player.getZ() - threat.getZ();
        float yaw = ax * ax + az * az < 0.01 ? mc.player.getYaw() + 180f : (float) Math.toDegrees(Math.atan2(az, ax)) - 90f;
        float oldYaw = mc.player.getYaw(), oldPitch = mc.player.getPitch();
        int previous = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.switchSlot("Silent", slot, previous);
        mc.player.setYaw(yaw);
        mc.player.setPitch(10f);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.setYaw(oldYaw);
        mc.player.setPitch(oldPitch);
        InventoryUtils.switchBack("Silent", slot, previous);
        pearlTimer.reset();
        if (debug) System.out.println("[AntiMace] pearl yaw=" + (int) yaw + " dy=" + String.format("%.1f", dy));
    }

    private PlayerEntity findThreat() {
        PlayerEntity best = null;
        double bestTicks = Double.MAX_VALUE;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive() || p.isSpectator()) continue;
            if (!friends.getValue() && Anatorius.FRIEND_MANAGER.contains(p.getName().getString())) continue;
            if (onlyMace.getValue() && !p.getMainHandStack().isOf(Items.MACE)) continue;
            double dy = p.getY() - mc.player.getY();
            if (dy < 1.0 || dy > 64.0) continue;
            Vec3d vel = new Vec3d(p.getX() - p.lastX, p.getY() - p.lastY, p.getZ() - p.lastZ);
            double r = range.getValue().doubleValue();
            double hx = p.getX() - mc.player.getX(), hz = p.getZ() - mc.player.getZ();
            double ticks;
            double px = hx, pz = hz;
            boolean hovering = early.getValue() && !p.isOnGround() && dy >= 2.5 && dy <= 40.0
                    && hx * hx + hz * hz <= MathHelper.square(r + dy * 0.5);
            if (hovering) {
                ticks = dy / Math.max(0.3, -vel.y);
            } else {
                boolean coming = vel.y < -0.3 || (dy < 5.0 && vel.y < 0.0);
                if (!coming) continue;
                ticks = Math.max(0.0, (dy - 2.0) / Math.max(0.3, -vel.y));
                if (ticks > HORIZON_TICKS) continue;
                px += vel.x * ticks;
                pz += vel.z * ticks;
                if (px * px + pz * pz > r * r) continue;
            }
            if (ticks < bestTicks) {
                bestTicks = ticks;
                best = p;
                impact = new Vec3d(mc.player.getX() + px, mc.player.getY(), mc.player.getZ() + pz);
            }
        }
        return best;
    }

    private void raiseShield() {
        if (usingShield && mc.player.isUsingItem() && mc.player.getActiveItem().isOf(Items.SHIELD)) {
            holdingShield = true;
            return;
        }
        Hand hand;
        if (mc.player.getOffHandStack().isOf(Items.SHIELD)) {
            hand = Hand.OFF_HAND;
        } else if (mc.player.getMainHandStack().isOf(Items.SHIELD)) {
            hand = Hand.MAIN_HAND;
        } else {
            int slot = InventoryUtils.find(Items.SHIELD, 0, 8);
            if (slot == -1) return;
            previousSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(slot);
            ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).invokeSyncSelectedSlot();
            hand = Hand.MAIN_HAND;
        }
        if (mc.player.isUsingItem()) mc.interactionManager.stopUsingItem(mc.player);
        mc.interactionManager.interactItem(mc.player, hand);
        usingShield = true;
        holdingShield = true;
    }

    private boolean hasShield() {
        return mc.player.getOffHandStack().isOf(Items.SHIELD) || InventoryUtils.find(Items.SHIELD, 0, 8) != -1;
    }

    @SubscribeEvent
    public void onKeyboardTick(KeyboardTickEvent event) {
        if (getNull() || threat == null || impact == null) return;
        String m = mode.getValue();
        if (!m.equals("Dodge") && !(m.equals("Auto") && fillSlot() == -1 && !hasShield())) return;
        double dx = mc.player.getX() - impact.x, dz = mc.player.getZ() - impact.z;
        if (dx * dx + dz * dz < 0.01) {
            double yaw = Math.toRadians(threat.getYaw());
            dx = -Math.sin(yaw);
            dz = Math.cos(yaw);
        }
        float wanted = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float delta = (mc.player.getYaw() - wanted) * MathHelper.RADIANS_PER_DEGREE;
        event.setMovementForward(Math.round(MathHelper.cos(delta)));
        event.setMovementSideways(Math.round(MathHelper.sin(delta)));
        event.setCancelled(true);
    }

    private void release() {
        if (usingShield && mc.player.isUsingItem() && mc.player.getActiveItem().isOf(Items.SHIELD) && !mc.options.useKey.isPressed()) {
            mc.interactionManager.stopUsingItem(mc.player);
        }
        if (previousSlot != -1) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
            ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).invokeSyncSelectedSlot();
            previousSlot = -1;
        }
        usingShield = false;
        holdingShield = false;
        threat = null;
        impact = null;
        holdTicks = 0;
        tried.clear();
    }
}
