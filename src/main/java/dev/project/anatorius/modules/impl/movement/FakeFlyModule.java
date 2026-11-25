package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.events.impl.PlayerMoveEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.events.impl.UpdateMovementEvent;
import dev.project.anatorius.mixins.accessors.FireworkRocketEntityAccessor;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.combat.AutoMaceModule;
import dev.project.anatorius.modules.impl.combat.KillAuraModule;
import dev.project.anatorius.modules.impl.movement.fakefly.InfDurability;
import dev.project.anatorius.modules.impl.movement.fakefly.NewGrimHandler;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.MovementUtils;
import dev.project.anatorius.utils.minecraft.NetworkUtils;
import dev.project.anatorius.utils.rotations.Rotation;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ConcurrentLinkedDeque;

@RegisterModule(name = "FakeFly", description = "Elytra flight that keeps your chestplate on.", category = Module.Category.MOVEMENT)
public class FakeFlyModule extends Module {
    public static FakeFlyModule INSTANCE;

    public static boolean hook = false;

    private static final int CHEST_SLOT = 6;
    private static final int FIRST_SLOT = 9;
    private static final int HOTBAR_SLOT = 36;
    private static final int OFFHAND_SLOT = 45;

    public ModeSetting infDurability = new ModeSetting("InfDurability", "When the elytra is swapped in for a glide, exactly like Zio's ElytraFly InfDurability. "
            + "Grim: once the glide closed (Grim servers, hvhtiers). Plain: every tick. 2b2t: every other tick, elytra in the hotbar only. "
            + "Classic: Anatorius' own, every tick with the glide ended right after (crits / mace smash keep working, many more packets).",
            "Grim", new String[]{"Grim", "Plain", "2b2t", "Classic"});
    public ModeSetting stopMotion = new ModeSetting("StopMotion", "Holding still in the air with no key down (Zio). Static: no motion. Jitter: turns back and forth on the spot. Timer: slows the game right down.",
            new ModeSetting.Visibility(infDurability, "Grim", "Plain", "2b2t"), "None", new String[]{"None", "Static", "Jitter", "Timer"});
    public BooleanSetting responsiveDescend = new BooleanSetting("ResponsiveDescend", "Sneak dives the way you look instead of a fixed 45 degrees (Zio).",
            new ModeSetting.Visibility(infDurability, "Grim", "Plain", "2b2t"), false);
    public ModeSetting mode = new ModeSetting("Mode", "How the elytra is put on. Normal clicks the armor slot. Alternative goes through your hand with a use-item.",
            new ModeSetting.Visibility(infDurability, "Classic"), "Normal", new String[]{"Normal", "Alternative"});
    public NumberSetting swapDelay = new NumberSetting("SwapDelay", "Seconds the elytra stays on before the chestplate comes back (0 = the same tick).",
            new ModeSetting.Visibility(infDurability, "Classic"), 0.0, 0.0, 1.0);
    public BooleanSetting autoFirework = new BooleanSetting("Firework", "Uses fireworks while flying: the next one as soon as the last has burnt out (Zio's AutoLaunch, Delay 0).", true);
    public BooleanSetting mute = new BooleanSetting("Mute", "Mutes the elytra swap and firework sounds.", true);
    public BooleanSetting auraSteer = new BooleanSetting("AuraSteer", "Flies you at KillAura's target. Other modules (AutoCrystal, its base place...) never steer the flight. Off: only your keys steer.", false);

    private final InfDurability durability = new InfDurability(this);
    private final NewGrimHandler newGrim = new NewGrimHandler(this);

    public boolean flying;

    private final Timer swapTimer = new Timer();
    private int swapSlot = -1;
    private boolean serverStopped;
    private boolean fireworkRequested;
    private int ticksSinceFirework = 100;
    private float glideYaw, glidePitch;
    private boolean hasGlideLook;

    private static final int CLICK_LIMIT = 71;
    private static final long CLICK_WINDOW_MS = 4000L;
    private static final ConcurrentLinkedDeque<Long> CLICK_TIMES = new ConcurrentLinkedDeque<>();
    private int elytraHome = -1;
    private static final long SETTLE_MS = 1000L;
    private static boolean checkingOverride;
    private static final int ZIO_ROTATION_PRIORITY = 999999;

    public FakeFlyModule() {
        INSTANCE = this;
    }

    private static boolean inventorySettled() {
        Long last = CLICK_TIMES.peekLast();
        return last == null || System.currentTimeMillis() - last >= SETTLE_MS;
    }

    public boolean wearsElytra() {
        return swapSlot == -1 && chestIsElytra() && (inventorySettled() || elytraHome == -1);
    }

    public int elytraHome() {
        if (elytraHome != -1 && inventorySettled()) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(elytraHome).getStack();
            if (!stack.isOf(Items.ELYTRA) || !LivingEntity.canGlideWith(stack, EquipmentSlot.CHEST)) elytraHome = -1;
        }
        if (elytraHome == -1) elytraHome = findElytra();
        return elytraHome;
    }

    public static void recordClick() {
        CLICK_TIMES.addLast(System.currentTimeMillis());
    }

    public boolean wouldReachClickLimit(int clicksNeeded) {
        long now = System.currentTimeMillis();
        while (!CLICK_TIMES.isEmpty() && now - CLICK_TIMES.peekFirst() >= CLICK_WINDOW_MS) CLICK_TIMES.pollFirst();
        if (CLICK_TIMES.size() + clicksNeeded >= CLICK_LIMIT) return true;
        int lastSecond = 0;
        for (long time : CLICK_TIMES) if (now - time < 1000L) lastSecond++;
        return lastSecond + clicksNeeded > CLICK_LIMIT / 4;
    }

    public boolean zio() {
        return !infDurability.getValue().equals("Classic");
    }

    public boolean requestFirework() {
        if (mc.player == null) return false;
        if (!mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET) && InventoryUtils.find(Items.FIREWORK_ROCKET) == -1) return false;
        fireworkRequested = true;
        return true;
    }

    public boolean takeFireworkRequest() {
        boolean requested = fireworkRequested;
        fireworkRequested = false;
        return requested;
    }

    @Override
    public void onEnable() {
        swapTimer.reset();
        swapSlot = -1;
        flying = false;
        elytraHome = -1;
        ticksSinceFirework = 100;
        hook = false;
        durability.onEnable();
    }

    @Override
    public void onDisable() {
        if (flying && serverStopped && !getNull() && !mc.player.isOnGround()) {
            if (swapSlot == -1 && !wearsElytra()) {
                int elytra = elytraHome();
                if (elytra != -1) {
                    swapChest(elytra, mode.getValue().equals("Alternative") && !longJumpOn());
                    swapSlot = elytra;
                }
            }
            if (swapSlot != -1 || wearsElytra()) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }
        flying = false;
        fireworkRequested = false;
        hook = false;
        restoreChestplate();
        durability.onDisable();
        newGrim.onDisable();
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (getNull() || !zio()) return;
        newGrim.onClientTick();
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (getNull()) {
            setToggled(false);
            return;
        }

        hook = false;
        flying = false;
        hasGlideLook = false;
        ticksSinceFirework++;

        ElytraFlyModule elytraFly = Anatorius.MODULE_MANAGER.getModule(ElytraFlyModule.class);
        if (elytraFly != null && elytraFly.isToggled()) elytraFly.setToggled(false);

        if (zio()) {
            restoreChestplate();
            if (InfDurability.collisionBelow()) elytraHome = -1;
            durability.preTick();
            newGrim.rotationTick();
            return;
        }

        if (mc.player.isOnGround() || mc.player.isTouchingWater() || mc.player.hasVehicle()) {
            restoreChestplate();
            elytraHome = -1;
            return;
        }

        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;

        boolean alternative = mode.getValue().equals("Alternative") && !longJumpOn();
        boolean elytraOn = swapSlot != -1 || wearsElytra();
        if (!elytraOn) {
            int elytra = elytraHome();
            if (elytra == -1) return;
            swapChest(elytra, alternative);
            swapSlot = elytra;
            swapTimer.reset();
        }

        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        flying = true;
        serverStopped = false;

        fireIfDue();
        steer();
    }

    private void fireIfDue() {
        boolean due = autoFirework.getValue() && !AutoMaceModule.ownsFakeFlyFireworks()
                && ticksSinceFirework >= 10 && !ownRocketAlive();
        if (fireworkRequested || due) {
            if (useFirework()) ticksSinceFirework = 0;
            fireworkRequested = false;
        }
    }

    private void steer() {
        float pitch = mc.player.getPitch();
        if (mc.options.jumpKey.isPressed()) pitch = -60.0f;
        if (mc.options.sneakKey.isPressed()) pitch = 60.0f;
        if (longJumpOn()) pitch = 0.0f;
        rotateGlide(MovementUtils.getMoveYaw(mc.player.getYaw()), pitch);
    }

    public void rotateGlide(float yaw, float pitch) {
        if (AutoMaceModule.busy()) return;
        if (auraSteer.getValue()) {
            KillAuraModule killAura = Anatorius.MODULE_MANAGER.getModule(KillAuraModule.class);
            Entity target = killAura != null && killAura.isToggled() ? killAura.getTarget() : null;
            if (target instanceof LivingEntity && target.isAlive()) {
                Vec3d eye = mc.player.getEyePos().add(mc.player.getVelocity());
                Vec3d aim = target.getBoundingBox().getCenter();
                double dx = aim.x - eye.x, dy = aim.y - eye.y, dz = aim.z - eye.z;
                yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
                pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            }
        }
        int priority = zio() ? ZIO_ROTATION_PRIORITY : Anatorius.ROTATION_MANAGER.getModulePriority(this);
        Anatorius.ROTATION_MANAGER.rotate(yaw, pitch, this, priority);
        glideYaw = yaw;
        glidePitch = pitch;
        hasGlideLook = true;
    }

    @SubscribeEvent(priority = -200)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (getNull() || !zio()) return;
        newGrim.onMove(event);
    }

    @SubscribeEvent(priority = Integer.MAX_VALUE)
    public void onPacketSend(PacketSendEvent event) {
        if (flying && !serverStopped && event.getPacket() instanceof PlayerInteractEntityC2SPacket) stopServerGlide();
        if (zio() && event.getPacket() instanceof PlayerActionC2SPacket packet
                && packet.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) {
            durability.onReleaseUseItem();
        }
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (getNull() || !zio()) return;
        if (event.getPacket() instanceof EntitySpawnS2CPacket packet && packet.getEntityType() == EntityType.FIREWORK_ROCKET
                && mc.player.squaredDistanceTo(packet.getX(), packet.getY(), packet.getZ()) <= 36.0) {
            durability.onRocketSpawn();
        }
    }

    @SubscribeEvent(priority = Integer.MIN_VALUE + 1)
    public void onUpdateMovementPost(UpdateMovementEvent.Post event) {
        if (getNull() || zio() || !flying) return;

        if (!serverStopped && !(landingSoon() && !inCombat())) stopServerGlide();

        if (swapTimer.hasTimeElapsed((long) (swapDelay.getValue().doubleValue() * 1000.0))) {
            restoreChestplate();
        }

        if (hasGlideLook && !AutoMaceModule.busy() && ownRocketAlive()) {
            Anatorius.ROTATION_MANAGER.packetRotate(glideYaw, glidePitch);
        }
    }

    private void stopServerGlide() {
        serverStopped = true;
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
    }

    private boolean landingSoon() {
        Vec3d velocity = mc.player.getVelocity();
        Box ahead = mc.player.getBoundingBox().stretch(velocity.x * 3.0, Math.min(velocity.y, 0.0) * 3.0 - 2.5, velocity.z * 3.0);
        return !mc.world.isSpaceEmpty(mc.player, ahead);
    }

    private boolean inCombat() {
        if (AutoMaceModule.busy()) return true;
        KillAuraModule killAura = Anatorius.MODULE_MANAGER.getModule(KillAuraModule.class);
        return killAura != null && killAura.isToggled() && killAura.getTarget() != null;
    }

    public static boolean overridesGliding() {
        if (INSTANCE == null || checkingOverride || hook || !INSTANCE.isToggled() || !INSTANCE.zio()) return false;
        if (AutoMaceModule.busy()) return false;
        checkingOverride = true;
        try {
            return INSTANCE.durability.isDurabilityBypassActive();
        } finally {
            checkingOverride = false;
        }
    }

    public static boolean holdsRocketUse(Hand hand) {
        return INSTANCE != null && INSTANCE.isToggled() && INSTANCE.zio() && mc.player != null && INSTANCE.durability.onUseItem(hand);
    }

    public static boolean glides(Entity entity) {
        return INSTANCE != null && INSTANCE.flying && INSTANCE.isToggled() && entity == mc.player;
    }

    public static float[] glideLook(Entity entity) {
        boolean infGlide = INSTANCE != null && INSTANCE.isToggled() && INSTANCE.zio()
                && entity == mc.player && InfDurability.isFallFlying();
        if (!glides(entity) && !infGlide) return null;
        Rotation rotation = Anatorius.ROTATION_MANAGER.peek();
        return rotation == null ? null : new float[]{rotation.getYaw(), rotation.getPitch()};
    }

    public static float[] rocketLook() {
        if (INSTANCE == null || !INSTANCE.isToggled() || !INSTANCE.zio() || AutoMaceModule.busy()) return null;
        Rotation rotation = Anatorius.ROTATION_MANAGER.peek();
        return rotation == null ? null : new float[]{rotation.getYaw(), rotation.getPitch()};
    }

    public static boolean stands(Entity entity) {
        if (INSTANCE == null || mc.player == null || entity != mc.player || !INSTANCE.isToggled()) return false;
        return (INSTANCE.flying || mc.player.isGliding()) && !mc.player.isTouchingWater();
    }

    public static boolean isSwapSound(Identifier id) {
        return id != null && (id.getPath().startsWith("item.armor.equip") || id.getPath().equals("item.elytra.flying"));
    }

    public static boolean muted() {
        return INSTANCE != null && INSTANCE.isToggled() && INSTANCE.mute.getValue();
    }

    private void restoreChestplate() {
        if (swapSlot == -1 || mc.player == null || mc.interactionManager == null) return;
        swapChest(swapSlot, mode.getValue().equals("Alternative") && !longJumpOn());
        swapSlot = -1;
    }

    private void swapChest(int slot, boolean alternative) {
        int syncId = mc.player.playerScreenHandler.syncId;
        int held = mc.player.getInventory().getSelectedSlot();

        if (alternative) {
            if (slot == OFFHAND_SLOT) {
                useItem(Hand.OFF_HAND);
                return;
            }
            if (slot >= HOTBAR_SLOT && slot < OFFHAND_SLOT) {
                int hotbar = slot - HOTBAR_SLOT;
                if (hotbar != held) InventoryUtils.switchSlot("Silent", hotbar, held);
                useItem(Hand.MAIN_HAND);
                if (hotbar != held) InventoryUtils.switchBack("Silent", hotbar, held);
                return;
            }
            boolean inHand = slot == HOTBAR_SLOT + held;
            if (!inHand) mc.interactionManager.clickSlot(syncId, slot, held, SlotActionType.SWAP, mc.player);
            useItem(Hand.MAIN_HAND);
            if (!inHand) mc.interactionManager.clickSlot(syncId, slot, held, SlotActionType.SWAP, mc.player);
            return;
        }

        if (slot >= HOTBAR_SLOT && slot < OFFHAND_SLOT) {
            mc.interactionManager.clickSlot(syncId, CHEST_SLOT, slot - HOTBAR_SLOT, SlotActionType.SWAP, mc.player);
        } else if (slot == OFFHAND_SLOT) {
            mc.interactionManager.clickSlot(syncId, CHEST_SLOT, 40, SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, CHEST_SLOT, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, slot, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private int findElytra() {
        for (int slot = FIRST_SLOT; slot <= OFFHAND_SLOT; slot++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(slot).getStack();
            if (stack.isOf(Items.ELYTRA) && LivingEntity.canGlideWith(stack, EquipmentSlot.CHEST)) return slot;
        }
        return -1;
    }

    private boolean chestIsElytra() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    public boolean useFirework() {
        if (mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            useItem(Hand.OFF_HAND);
            return true;
        }
        if (mc.player.getMainHandStack().isOf(Items.FIREWORK_ROCKET)) {
            useItem(Hand.MAIN_HAND);
            return true;
        }

        int held = mc.player.getInventory().getSelectedSlot();
        int hotbar = InventoryUtils.findHotbar(Items.FIREWORK_ROCKET);
        if (hotbar != -1) {
            InventoryUtils.switchSlot("Silent", hotbar, held);
            useItem(Hand.MAIN_HAND);
            InventoryUtils.switchBack("Silent", hotbar, held);
            return true;
        }

        int index = InventoryUtils.find(Items.FIREWORK_ROCKET);
        if (index == -1) return false;
        int syncId = mc.player.playerScreenHandler.syncId;
        int slot = InventoryUtils.indexToSlot(index);
        mc.interactionManager.clickSlot(syncId, slot, held, SlotActionType.SWAP, mc.player);
        useItem(Hand.MAIN_HAND);
        mc.interactionManager.clickSlot(syncId, slot, held, SlotActionType.SWAP, mc.player);
        return true;
    }

    public boolean ownRocketAlive() {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity rocket && rocket.isAlive()
                    && ((FireworkRocketEntityAccessor) rocket).anatorius$getShooter() == mc.player) return true;
        }
        return false;
    }

    private void useItem(Hand hand) {
        float yaw = Anatorius.ROTATION_MANAGER.getServerYaw();
        float pitch = Anatorius.ROTATION_MANAGER.getServerPitch();
        NetworkUtils.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(hand, id, yaw, pitch));
    }

    private boolean longJumpOn() {
        LongJumpModule longJump = Anatorius.MODULE_MANAGER.getModule(LongJumpModule.class);
        return longJump != null && longJump.isToggled();
    }

    public double tickSpeed() {
        return autoFirework.getValue() ? 1.5 : 0.55;
    }

    @Override
    public String getMetaData() {
        return infDurability.getValue();
    }
}
