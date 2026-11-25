package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.KeyboardTickEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.events.impl.PlayerMoveEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.events.impl.UpdateMovementEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.core.AntiCheatModule;
import dev.project.anatorius.modules.impl.exploit.TimerModule;
import dev.project.anatorius.modules.impl.movement.elytrafly.*;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.FlightInput;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

@RegisterModule(name = "ElytraFly", description = "Flies with an elytra in the way your server lets you.", category = Module.Category.MOVEMENT)
public class ElytraFlyModule extends Module {
    private static ElytraFlyModule INSTANCE;

    public ModeSetting mode = new ModeSetting("Mode", "Control: fly any way with the keys at a set speed. Boost: a normal glide that keeps getting pushed. Packet: flies without opening the elytra. Strict: a level glide at a steady speed (NCP). Bounce: hops along the ground with the elytra open. Grim: rocket flight steered by your look, holds still when idle. Vanilla: climbs and dives on its own so the glide keeps its speed.", "Control", new String[]{"Control", "Boost", "Packet", "Strict", "Bounce", "Grim", "Vanilla"});
    public ModeSetting takeoff = new ModeSetting("Takeoff", "Opens the elytra by itself once you are in the air. Normal: right away. Timer: slows the game down while it opens, for strict servers.", new ModeSetting.Visibility(mode, "Control", "Boost", "Strict", "Grim", "Vanilla"), "Normal", new String[]{"None", "Normal", "Timer"});

    public NumberSetting horizontal = new NumberSetting("Horizontal", "How fast you fly forward and sideways, in blocks per tick (20 ticks = 1 second).", new ModeSetting.Visibility(mode, "Control", "Packet", "Strict"), 2.0, 0.1, 10.0);
    public ModeSetting verticalMode = new ModeSetting("ControlVertical", "UpDown", "How jump and sneak take you up and down. Straight: straight up and down at Vertical. Pitch: by tilting the glide (needs speed). Push: jump also pushes you forward. Angled: climbs at ClimbPitch.", new ModeSetting.Visibility(mode, "Control"), "Straight", new String[]{"Straight", "Pitch", "Push", "Angled"});
    public NumberSetting vertical = new NumberSetting("Vertical", "How fast you fly up (jump) and down (sneak), in blocks per tick.", new ModeSetting.Visibility(mode, "Control"), 1.0, 0.1, 5.0);
    public NumberSetting verticalPitch = new NumberSetting("ControlClimbPitch", "ClimbPitch", "The angle you climb at with UpDown on Angled, in degrees.", new ModeSetting.Visibility(verticalMode, "Angled"), 45.0, 0.0, 90.0);
    public NumberSetting glide = new NumberSetting("Glide", "How fast you sink in blocks per tick while no up or down key is held. 0 holds you in place.", new ModeSetting.Visibility(mode, "Control", "Packet"), 0.0, 0.0, 2.0);
    public BooleanSetting spoofPitch = new BooleanSetting("ControlSpoofPitch", "SpoofPitch", "Tells the server a fixed pitch while you fly (some servers check it).", new ModeSetting.Visibility(mode, "Control"), false);
    public NumberSetting spoofedPitch = new NumberSetting("ControlSpoofedPitch", "Pitch", "The pitch SpoofPitch tells the server, in degrees.", new BooleanSetting.Visibility(spoofPitch, true), 0.5, -90.0, 90.0);
    public BooleanSetting accelerate = new BooleanSetting("Accelerate", "The speed builds up when you start moving instead of being full at once.", new ModeSetting.Visibility(mode, "Control", "Packet"), false);
    public NumberSetting accelMin = new NumberSetting("AccelStart", "StartSpeed", "The horizontal speed it builds up from, in blocks per tick.", new BooleanSetting.Visibility(accelerate, true), 1.0, 0.0, 5.0);
    public NumberSetting verticalAccelMin = new NumberSetting("AccelVerticalStart", "VerticalStart", "The vertical speed it builds up from, in blocks per tick.", new BooleanSetting.Visibility(accelerate, true), 1.0, 0.0, 5.0);
    public NumberSetting accelTime = new NumberSetting("AccelTime", "Time", "Seconds it takes to reach full speed.", new BooleanSetting.Visibility(accelerate, true), 1.0, 0.1, 10.0);
    public BooleanSetting antiAfk = new BooleanSetting("PacketAntiAFK", "AntiAFK", "Drifts in a small circle while you stand still in the air, so you aren't kicked for being idle.", new ModeSetting.Visibility(mode, "Packet"), true);
    public BooleanSetting packetVertical = new BooleanSetting("PacketJumpTakeoff", "JumpTakeoff", "Holding jump opens the elytra and glides normally instead of packet flying.", new ModeSetting.Visibility(mode, "Packet"), false);
    public NumberSetting startSpeed = new NumberSetting("StrictStart", "StartSpeed", "The speed you start at, in blocks per tick; it rises to Horizontal over half a second.", new ModeSetting.Visibility(mode, "Strict"), 1.8, 0.0, 5.0);
    public NumberSetting deployTime = new NumberSetting("StrictOpenDelay", "OpenDelay", "Milliseconds between tries to open the elytra.", new ModeSetting.Visibility(mode, "Strict"), 400, 50, 1000);

    public NumberSetting boost = new NumberSetting("BoostStrength", "Strength", "How hard every tick pushes you along. Max: the value Grim (V3) accepts.", new ModeSetting.Visibility(mode, "Boost"), 1.5, 0.0, 5.0).withMaxLabel("GrimV3");
    public NumberSetting speedLimit = new NumberSetting("BoostMaxSpeed", "MaxSpeed", "The fastest it lets you go, in km/h. Max: no limit.", new ModeSetting.Visibility(mode, "Boost"), 500, 20, 500).withMaxLabel("Unlimited");
    public BooleanSetting responsive = new BooleanSetting("BoostResponsive", "Responsive", "Flies the way your keys point, and tilts up for jump and down for sneak.", new ModeSetting.Visibility(mode, "Boost"), true);
    public BooleanSetting always = new BooleanSetting("BoostAlways", "Always", "Pushes you forward without holding W.", new ModeSetting.Visibility(mode, "Boost"), false);
    public BooleanSetting factorize = new BooleanSetting("BoostDiveOnly", "DiveOnly", "Pushes only while you are tilted down, like a real glide picks up speed.", new ModeSetting.Visibility(mode, "Boost"), false);
    public BooleanSetting ncpBoost = new BooleanSetting("BoostNCP", "NCP", "A strong push right after opening and a pitch rhythm NoCheatPlus accepts.", new ModeSetting.Visibility(mode, "Boost"), false);
    public BooleanSetting autoJump = new BooleanSetting("BoostAutoJump", "AutoJump", "Holds jump for you while you glide.", new ModeSetting.Visibility(mode, "Boost"), false);
    public BooleanSetting inLava = new BooleanSetting("BoostInLava", "InLava", "Keeps pushing you through lava.", new ModeSetting.Visibility(mode, "Boost"), true);
    public NumberSetting lavaSpeed = new NumberSetting("BoostLavaSpeed", "LavaSpeed", "How hard it pushes you in lava.", new BooleanSetting.Visibility(inLava, true), 1.0, 0.0, 2.0);

    public BooleanSetting bouncePacket = new BooleanSetting("BouncePacket", "Packet", "The packet bounce: uses no elytra durability and keeps your speed when you land.", new ModeSetting.Visibility(mode, "Bounce"), false);
    public BooleanSetting diagonal = new BooleanSetting("BounceDiagonal", "Diagonal", "Bounces diagonally, lined up to the block grid first.", new BooleanSetting.Visibility(bouncePacket, true), false);
    public NumberSetting maxBoost = new NumberSetting("BounceMaxSpeed", "MaxSpeed", "The speed it keeps you under when you land, in km/h.", new BooleanSetting.Visibility(bouncePacket, true), 300, 40, 999);
    public BooleanSetting pitchLock = new BooleanSetting("BouncePitchLock", "PitchLock", "Flies at a fixed pitch instead of your own.", new ModeSetting.Visibility(mode, "Bounce"), false);
    public BooleanSetting autoPitch = new BooleanSetting("BounceAutoPitch", "AutoPitch", "Picks the pitch itself: steep while rising, flat while falling.", new BooleanSetting.Visibility(pitchLock, true), false);
    public NumberSetting pitch = new NumberSetting("BouncePitch", "Pitch", "The fixed pitch, in degrees.", new BooleanSetting.Visibility(pitchLock, true), 90.0, -90.0, 90.0);
    public BooleanSetting silent = new BooleanSetting("BounceSilent", "Silent", "Only the server gets the fixed pitch; your camera stays yours.", new BooleanSetting.Visibility(pitchLock, true), true);
    public BooleanSetting compensate = new BooleanSetting("BounceCompensate", "Compensate", "Holds your packets back for a moment after each landing, the way the server expects a bounce.", new ModeSetting.Visibility(mode, "Bounce"), true);

    public ModeSetting stopMotion = new ModeSetting("GrimHover", "Hover", "Holding still in the air while no key is down. Freeze: no motion at all. Jitter: turns back and forth on the spot. Timer: slows the game right down.", new ModeSetting.Visibility(mode, "Grim"), "None", new String[]{"None", "Freeze", "Jitter", "Timer"});
    public BooleanSetting responsiveDescend = new BooleanSetting("GrimLookDescend", "LookDescend", "Sneak dives the way you look instead of straight down.", new ModeSetting.Visibility(mode, "Grim"), false);

    public NumberSetting minY = new NumberSetting("VanillaMinY", "MinY", "Dives down to this height, then climbs again.", new ModeSetting.Visibility(mode, "Vanilla"), 100, 0, 500);
    public NumberSetting height = new NumberSetting("VanillaHeight", "Height", "Climbs this many blocks above MinY, then dives again.", new ModeSetting.Visibility(mode, "Vanilla"), 35, 20, 150);
    public BooleanSetting useFirework = new BooleanSetting("VanillaFirework", "Firework", "Fires a rocket when a climb starts too low to make it on its own.", new ModeSetting.Visibility(mode, "Vanilla"), false);

    public ModeSetting infiniteDurability = new ModeSetting("InfiniteDurability", "InfDurability", "Keeps a chestplate on and puts the elytra on only for the moment it opens, so the elytra never wears out. Normal: every tick. Grim: only when the glide closed. 2b2t: in the rhythm 2b2t accepts.", new ModeSetting.Visibility(mode, "Control", "Boost", "Bounce", "Grim", "Vanilla"), "None", new String[]{"None", "Normal", "Grim", "2b2t"});
    public BooleanSetting muteElytra = new BooleanSetting("Mute", "Mutes the elytra wind and the armor swap sounds.", false);

    private final Map<String, ElytraFlyMode> modes = new LinkedHashMap<>();
    private ElytraFlyMode active;

    private long enabledAt, takeoffAt, durabilitySwapAt, elytraSeenAt, lastSetbackAt;
    private boolean takeoffRequested, timerHeld;
    private boolean jumpPressed, sneakPressed;
    private int groundedTicks, airborneTicks;
    private double lastX = Double.NaN, lastZ, speedKmh;

    private boolean inputJump, inputReleaseJump, inputForward, inputSprint;

    private boolean lookApplied;
    private float savedYaw, savedPitch;

    private boolean flushing;

    public ElytraFlyModule() {
        INSTANCE = this;
        modes.put("Control", new ControlMode(this));
        modes.put("Boost", new BoostMode(this));
        modes.put("Packet", new PacketMode(this));
        modes.put("Strict", new StrictMode(this));
        modes.put("Bounce", new BounceMode(this));
        modes.put("Grim", new GrimMode(this));
        modes.put("Vanilla", new VanillaMode(this));
    }

    private ElytraFlyMode mode() {
        ElytraFlyMode m = modes.get(mode.getValue());
        return m == null ? modes.get("Control") : m;
    }

    @Override
    public void onEnable() {
        enabledAt = System.currentTimeMillis();
        groundedTicks = airborneTicks = 0;
        lastX = Double.NaN;
        active = mode();
        if (mc.player != null) active.onEnable();
    }

    @Override
    public void onDisable() {
        try {
            if (mc.player != null && active != null) active.onDisable();
        } finally {
            releaseTimer();
            active = null;
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        switchModeIfChanged();
        active.clearRequest();
        active.clientTick();
    }

    @SubscribeEvent(priority = -300)
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        switchModeIfChanged();
        groundedTicks = mc.player.isOnGround() ? groundedTicks + 1 : 0;
        airborneTicks = mc.player.isOnGround() ? 0 : airborneTicks + 1;
        if (!Double.isNaN(lastX)) speedKmh = Math.hypot(mc.player.getX() - lastX, mc.player.getZ() - lastZ) * 20.0 * 3.6;
        lastX = mc.player.getX();
        lastZ = mc.player.getZ();
        updateDurabilitySwap();
        active.preTick();
    }

    @SubscribeEvent
    public void onKeyboardTick(KeyboardTickEvent event) {
        if (mc.player == null || active == null) return;
        if (inputJump) event.setForceJump(true);
        if (inputReleaseJump) event.setReleaseJump(true);
        if (inputForward) event.setForceForward(true);
        if (inputSprint) event.setForceSprint(true);
        inputJump = inputReleaseJump = inputForward = inputSprint = false;

        jumpPressed = mc.options.jumpKey.isPressed() || event.isForceJump();
        sneakPressed = mc.options.sneakKey.isPressed();
        if (!elytraEquipped()) return;
        if (!mode.getValue().equals("Packet") || (packetVertical.getValue() && jumpPressed)) processTakeoff();
        else releaseTimer();
    }

    @SubscribeEvent(priority = -200)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player == null || active == null) return;
        active.move(new ElytraFlyMode.Move(event));
    }

    @SubscribeEvent
    public void onUpdateMovement(UpdateMovementEvent event) {
        if (mc.player == null || active == null) return;
        active.playerTick();
        float[] look = active.packetLook();
        if (look == null || (Float.isNaN(look[0]) && Float.isNaN(look[1]))) return;
        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();
        lookApplied = true;
        if (!Float.isNaN(look[0])) {
            float serverYaw = Anatorius.ROTATION_MANAGER.getServerYaw();
            mc.player.setYaw(serverYaw + MathHelper.wrapDegrees(look[0] - serverYaw));
        }
        if (!Float.isNaN(look[1])) mc.player.setPitch(MathHelper.clamp(look[1], -90.0f, 90.0f));
    }

    @SubscribeEvent(priority = Integer.MAX_VALUE)
    public void onUpdateMovementPost(UpdateMovementEvent.Post event) {
        if (!lookApplied || mc.player == null) return;
        lookApplied = false;
        mc.player.setYaw(savedYaw);
        mc.player.setPitch(savedPitch);
    }

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent event) {
        if (mc.player == null || active == null || flushing) return;
        if (event.getPacket() instanceof PlayerActionC2SPacket packet && packet.getAction() == PlayerActionC2SPacket.Action.RELEASE_USE_ITEM && is2b2t()) {
            durabilitySwapAt = System.currentTimeMillis();
        }
        active.packetSend(event);
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (mc.player == null || active == null) return;
        if (event.getPacket() instanceof EntitySpawnS2CPacket packet && packet.getEntityType() == EntityType.FIREWORK_ROCKET
                && new Vec3d(packet.getX(), packet.getY(), packet.getZ()).squaredDistanceTo(mc.player.getEntityPos()) <= 36.0) {
            durabilitySwapAt = System.currentTimeMillis();
        }
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket) lastSetbackAt = System.currentTimeMillis();
        active.packetReceive(event);
    }

    private void switchModeIfChanged() {
        ElytraFlyMode wanted = mode();
        if (wanted == active) return;
        if (active != null) active.onDisable();
        releaseTimer();
        active = wanted;
        active.onEnable();
    }

    public void updateTakeoff() {
        takeoffRequested = true;
    }

    private void processTakeoff() {
        if (mode.getValue().equals("Bounce")) return;
        if (!elytraEquipped() || vanillaGliding() || collisionBelow()
                || (takeoff.getValue().equals("None") && !takeoffRequested)) {
            releaseTimer();
            return;
        }
        if (mc.player.isTouchingWater()) return;
        if (takeoff.getValue().equals("Timer")) requestTimer(0.1f);
        long delay = mode.getValue().equals("Strict") ? deployTime.getValue().longValue() : 100L;
        long now = System.currentTimeMillis();
        if (now - takeoffAt < delay) return;
        takeoffAt = now;
        startGliding();
        takeoffRequested = false;
    }

    public boolean tryStartNormalFlight() {
        if (!infiniteDurability.getValue().equals("None") || bouncePacketMode()) return false;
        if (!canStartFlightFromJump()) return false;
        mc.execute(this::startGliding);
        return true;
    }

    public boolean canStartFlightFromJump() {
        return !mc.player.getAbilities().flying && !mc.player.hasVehicle() && !mc.player.isClimbing()
                && elytraEquipped() && !mc.player.isTouchingWater() && !mc.player.hasStatusEffect(StatusEffects.LEVITATION)
                && mc.player.input.playerInput.jump();
    }

    public void startGliding() {
        mc.player.startGliding();
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
    }

    private void updateDurabilitySwap() {
        if (active instanceof BounceMode bounce && bounce.isDiagonalPending()) return;
        int hotbar = findElytra(0, 8);
        boolean fromInventory = hotbar == -1;
        int inventory = fromInventory ? findElytra(9, 35) : -1;
        long now = System.currentTimeMillis();
        if (!shouldSwapForDurability(fromInventory) || now - durabilitySwapAt < 200L || !durabilityBypassActive()) return;
        if (elytraEquipped()) {
            startGliding();
            return;
        }
        if (hotbar == -1 && inventory == -1) return;

        int syncId = mc.player.playerScreenHandler.syncId;
        if (fromInventory) {
            hotbar = 0;
            mc.interactionManager.clickSlot(syncId, inventory, 0, SlotActionType.SWAP, mc.player);
        }
        mc.interactionManager.clickSlot(syncId, 6, hotbar, SlotActionType.SWAP, mc.player);
        try {
            if (now - enabledAt < 500L || infiniteDurabilityVisible() || bouncePacketMode()) startGliding();
        } finally {
            mc.interactionManager.clickSlot(syncId, 6, hotbar, SlotActionType.SWAP, mc.player);
            if (fromInventory) mc.interactionManager.clickSlot(syncId, inventory, 0, SlotActionType.SWAP, mc.player);
        }
    }

    private boolean shouldSwapForDurability(boolean fromInventory) {
        boolean wearing = elytraEquipped();
        if (!wearing && !mc.player.currentScreenHandler.getCursorStack().isEmpty()) return false;
        if (bouncePacketMode() && wearing) return true;
        return switch (infiniteDurability.getValue()) {
            case "Normal" -> true;
            case "Grim" -> !flying();
            case "2b2t" -> {
                if (wearing) yield true;
                if (fromInventory) yield false;
                if (mode.getValue().equals("Bounce") && pitchLock.getValue() && pitch.getValue().doubleValue() >= 90.0) {
                    yield mc.player.isOnGround() || airborneTicks == 0 || airborneTicks == 1 || airborneTicks == 6;
                }
                int tick = mc.player.age;
                yield tick % 2 != 0 && tick % 20 != 1;
            }
            default -> false;
        };
    }

    private boolean infiniteDurabilityVisible() {
        return switch (mode.getValue()) {
            case "Control", "Boost", "Bounce", "Grim", "Vanilla" -> true;
            default -> false;
        };
    }

    public boolean durabilityBypassActive() {
        if (!isToggled() || mc.player == null || mc.world == null) return false;
        String m = mode.getValue();
        if (collisionBelow() && (m.equals("Grim") || m.equals("Control"))) return false;
        long now = System.currentTimeMillis();
        if (findElytra(0, 8) != -1 || findElytra(9, 35) != -1 || elytraEquipped()) elytraSeenAt = now;
        else if (now - elytraSeenAt >= 150L) return false;
        boolean enabled = !infiniteDurability.getValue().equals("None") && infiniteDurabilityVisible();
        return enabled || bouncePacketMode();
    }

    private int findElytra(int start, int end) {
        for (int i = start; i <= end; i++) if (mc.player.getInventory().getStack(i).isOf(Items.ELYTRA)) return i;
        return -1;
    }

    public boolean bouncePacketMode() {
        return mode.getValue().equals("Bounce") && bouncePacket.getValue();
    }

    public boolean elytraEquipped() {
        return mc.player != null && LivingEntity.canGlideWith(mc.player.getEquippedStack(EquipmentSlot.CHEST), EquipmentSlot.CHEST);
    }

    public boolean flying() {
        return mc.player.isGliding() || glides(mc.player);
    }

    public boolean vanillaGliding() {
        return mc.player.isGliding() && !durabilityBypassActive() && !packetFlightActive();
    }

    public boolean packetFlightActive() {
        if (!isToggled() || mc.player == null || mc.player.input == null) return false;
        return !(packetVertical.getValue() && mc.player.input.playerInput.jump()) && !mc.player.isOnGround()
                && mode.getValue().equals("Packet") && elytraEquipped();
    }

    public boolean manualVerticalActive() {
        return (jumpPressed || sneakPressed) && verticalMode.getValue().equals("Push") && vanillaGliding() && elytraEquipped();
    }

    public boolean collisionBelow() {
        return !mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().stretch(0.0, -0.1, 0.0));
    }

    public boolean itemUseSlowing() {
        NoSlowModule noSlow = Anatorius.MODULE_MANAGER.getModule(NoSlowModule.class);
        return !is2b2t() && mc.player.isUsingItem() && (noSlow == null || !noSlow.isToggled() || !noSlow.items.getValue());
    }

    public boolean is2b2t() {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        return antiCheat != null && antiCheat.preset.getValue().equalsIgnoreCase("2b2t");
    }

    public boolean movementSync() {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        return antiCheat != null && antiCheat.movementSync.getValue();
    }

    public boolean up() {
        return FlightInput.up();
    }

    public boolean down() {
        return FlightInput.down();
    }

    public int groundedTicks() {
        return groundedTicks;
    }

    public double speedKmh() {
        return speedKmh;
    }

    public boolean setbackWithin(long ms) {
        return System.currentTimeMillis() - lastSetbackAt < ms;
    }

    public boolean enabledFor(long ms) {
        return System.currentTimeMillis() - enabledAt >= ms;
    }

    public void holdJump() { inputJump = true; }

    public void releaseJump() { inputReleaseJump = true; }

    public void holdForward() { inputForward = true; }

    public void holdSprint() { inputSprint = true; }

    public void requestTimer(float multiplier) {
        timerHeld = true;
        Anatorius.WORLD_MANAGER.setTimerMultiplier(multiplier);
    }

    public void releaseTimer() {
        if (!timerHeld) return;
        timerHeld = false;
        TimerModule timer = Anatorius.MODULE_MANAGER.getModule(TimerModule.class);
        Anatorius.WORLD_MANAGER.setTimerMultiplier(timer != null && timer.isToggled() ? timer.multiplier.getValue().floatValue() : 1.0f);
    }

    public void sendUncaught(net.minecraft.network.packet.Packet<?> packet) {
        flushing = true;
        try {
            mc.getNetworkHandler().sendPacket(packet);
        } finally {
            flushing = false;
        }
    }

    public static boolean glides(Entity entity) {
        if (INSTANCE == null || entity != mc.player || !INSTANCE.isToggled() || mc.player == null) return false;
        return INSTANCE.durabilityBypassActive() && !mc.player.isOnGround() && !mc.player.isTouchingWater();
    }

    public static float[] glideLook(Entity entity) {
        if (INSTANCE == null || entity != mc.player || !INSTANCE.isToggled() || INSTANCE.active == null) return null;
        float[] look = INSTANCE.active.travelLook();
        if (look == null) return null;
        return new float[]{Float.isNaN(look[0]) ? mc.player.getYaw() : look[0], Float.isNaN(look[1]) ? mc.player.getPitch() : look[1]};
    }

    public static boolean stands(Entity entity) {
        if (INSTANCE == null || entity != mc.player || !INSTANCE.isToggled()) return false;
        return INSTANCE.packetFlightActive() || INSTANCE.durabilityBypassActive();
    }

    public static boolean rocketsAlongServerLook() {
        return INSTANCE != null && INSTANCE.isToggled() && INSTANCE.mode.getValue().equals("Grim");
    }

    public static boolean mutes(Identifier id) {
        if (INSTANCE == null || !INSTANCE.isToggled() || !INSTANCE.muteElytra.getValue() || id == null) return false;
        String path = id.getPath();
        return path.startsWith("item.armor.equip") || path.contains("elytra");
    }

    @Override
    public String getMetaData() {
        return mode.getValue();
    }
}
