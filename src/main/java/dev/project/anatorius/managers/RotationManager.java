package dev.project.anatorius.managers;

import dev.project.anatorius.modules.impl.core.AntiCheatModule;
import dev.project.anatorius.modules.impl.core.RotationsModule;
import lombok.Getter;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.*;
import dev.project.anatorius.mixins.accessors.EntityAccessor;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.rotations.Rotation;
import dev.project.anatorius.utils.system.MathUtils;
import dev.project.anatorius.utils.minecraft.NetworkUtils;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.PriorityBlockingQueue;

public class RotationManager implements IMinecraft {
    private final PriorityBlockingQueue<Rotation> queue = new PriorityBlockingQueue<>(11, this::compareRotations);
    @Getter private Rotation rotation = null;

    private float prevFixYaw;

    private float prevYaw;
    private float prevPitch;

    private float lastPacketYaw = Float.NaN;
    private float lastPacketPitch = Float.NaN;

    private float serverYaw;
    private float serverPitch;

    private boolean inWindow;
    private boolean windowInstant;
    private boolean deferring;
    private boolean flushing;
    private final ArrayList<Object> deferred = new ArrayList<>();
    private Rotation tickLook;
    private Rotation tickRotation;
    private boolean appliedHeldLook;

    private record Look(float yaw, float pitch) {}

    private float prevRenderYaw, prevRenderPitch;
    private long lastRenderTime = 0L;

    private static final HashMap<String, Integer> PRIORITIES = new HashMap<>();
    static {
        PRIORITIES.put("FeetTrap", 100);
        PRIORITIES.put("SpeedMine", 95);
        PRIORITIES.put("FakeFly", 92);
        PRIORITIES.put("ElytraFly", 91);
        PRIORITIES.put("Scaffold", 90);
        PRIORITIES.put("SelfTrap", 88);
        PRIORITIES.put("Blocker", 87);
        PRIORITIES.put("SelfFill", 86);
        PRIORITIES.put("HoleFill", 85);
        PRIORITIES.put("AutoTrap", 84);
        PRIORITIES.put("AntiPiston", 83);
        PRIORITIES.put("AirPlace", 82);
        PRIORITIES.put("AutoWeb", 81);
        PRIORITIES.put("FishTrap", 80);
        PRIORITIES.put("AutoDrop", 78);
        PRIORITIES.put("PistonCrystal", 75);
        PRIORITIES.put("AutoTnt", 74);
        PRIORITIES.put("AutoCrystal", 70);
        PRIORITIES.put("CombatPlacer", 69);
        PRIORITIES.put("AutoAnchor", 68);
        PRIORITIES.put("AutoBed", 66);
        PRIORITIES.put("KillAura", 60);
        PRIORITIES.put("AutoMace", 58);
        PRIORITIES.put("AntiPhase", 55);
        PRIORITIES.put("AutoPearl", 50);
        PRIORITIES.put("AntiPearl", 49);
        PRIORITIES.put("SelfBow", 45);
        PRIORITIES.put("AutoXp", 40);
        PRIORITIES.put("AutoPot", 38);
        PRIORITIES.put("TableTroll", 30);
    }

    public RotationManager() {
        Anatorius.EVENT_HANDLER.subscribe(this);
    }

    @SubscribeEvent(priority = Integer.MIN_VALUE)
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        queue.removeIf(rotation -> System.currentTimeMillis() - rotation.getTime() > 100);
        rotation = queue.peek();

        lastPacketYaw = Float.NaN;
        lastPacketPitch = Float.NaN;

        if (rotation == null) return;
        lastRenderTime = System.currentTimeMillis();
    }

    @SubscribeEvent(priority = Integer.MAX_VALUE)
    public void onUpdateMovement(UpdateMovementEvent event) {
        queue.removeIf(rot -> System.currentTimeMillis() - rot.getTime() > 100);
        rotation = resolveTickRotation();

        if (rotation == null) return;

        prevYaw = mc.player.getYaw();
        prevPitch = mc.player.getPitch();
        appliedHeldLook = rotation == tickRotation;

        float[] look = toPacketLook(rotation.getYaw(), rotation.getPitch());
        mc.player.setYaw(look[0]);
        mc.player.setPitch(look[1]);
    }

    @SubscribeEvent(priority = Integer.MAX_VALUE)
    public void onUpdateMovement$FLUSH(UpdateMovementEvent.Post event) {
        if (deferring) flushDeferred();
        tickRotation = null;
    }

    @SubscribeEvent(priority = Integer.MIN_VALUE)
    public void onUpdateMovement$POST(UpdateMovementEvent.Post event) {
        if (rotation == null) return;

        if (isMotionMode() && !appliedHeldLook) return;

        mc.player.setYaw(prevYaw);
        mc.player.setPitch(prevPitch);
    }

    private Rotation resolveTickRotation() {
        Rotation head = queue.peek();
        if (tickRotation != null && (head == null || tickRotation.getPriority() >= head.getPriority())) return tickRotation;
        return head;
    }

    public void beginTickWindow() {
        if (deferring) flushDeferred();
        tickRotation = null;
        windowInstant = false;
        inWindow = true;
    }

    public void endTickWindow() {
        inWindow = false;
        if (!deferring) return;

        queue.removeIf(r -> System.currentTimeMillis() - r.getTime() > 100);
        Rotation head = queue.peek();
        boolean same = head != null && sameLook(head.getYaw(), head.getPitch(), tickLook.getYaw(), tickLook.getPitch());
        if (head != null && !same && head.getPriority() >= tickLook.getPriority()) {
            flushDeferred();
            return;
        }
        if (!same) {
            tickRotation = tickLook;
            rotation = tickLook;
            lastRenderTime = System.currentTimeMillis();
        }
    }

    private boolean absorbing() {
        return inWindow && !windowInstant && !flushing && tickRotations() && mc.isOnThread();
    }

    private boolean tickRotations() {
        AntiCheatModule ac = antiCheat();
        return ac == null || !ac.packetRotate.getValue().equalsIgnoreCase("Instant");
    }

    public boolean defer(Packet<?> packet) {
        if (!deferring || !inWindow || flushing || !mc.isOnThread()) return false;
        if (packet instanceof PlayerMoveC2SPacket || packet instanceof VehicleMoveC2SPacket) {
            flushDeferred();
            windowInstant = true;
            return false;
        }
        if (packet instanceof KeepAliveC2SPacket || packet instanceof CommonPongC2SPacket || packet instanceof TeleportConfirmC2SPacket) return false;
        deferred.add(packet);
        return true;
    }

    private void flushDeferred() {
        if (flushing) return;
        flushing = true;
        try {
            if (mc.player == null || mc.getNetworkHandler() == null) return;
            for (Object entry : deferred) {
                if (entry instanceof Look look) sendLookPacket(look.yaw(), look.pitch(), false);
                else if (entry instanceof PlayerInputC2SPacket input) NetworkUtils.sendIgnoredPacket(refreshInput(input));
                else NetworkUtils.sendIgnoredPacket((Packet<?>) entry);
            }
        } finally {
            deferred.clear();
            deferring = false;
            tickLook = null;
            flushing = false;
        }
    }

    private PlayerInputC2SPacket refreshInput(PlayerInputC2SPacket held) {
        PlayerInput cur = mc.player.input.playerInput;
        return new PlayerInputC2SPacket(new PlayerInput(cur.forward(), cur.backward(), cur.left(), cur.right(), cur.jump(), held.input().sneak(), cur.sprint()));
    }

    private Look lastHeldLook() {
        for (int i = deferred.size() - 1; i >= 0; i--) if (deferred.get(i) instanceof Look look) return look;
        return null;
    }

    private static boolean sameLook(float yawA, float pitchA, float yawB, float pitchB) {
        return Math.abs(MathHelper.wrapDegrees(yawA - yawB)) < 1.0E-3f && Math.abs(pitchA - pitchB) < 1.0E-3f;
    }

    public float getServerYaw() {
        Look held = deferring ? lastHeldLook() : null;
        return held != null ? held.yaw() : serverYaw;
    }

    public float getServerPitch() {
        Look held = deferring ? lastHeldLook() : null;
        return held != null ? held.pitch() : serverPitch;
    }

    public float getSentYaw() {
        return serverYaw;
    }

    public float getSentPitch() {
        return serverPitch;
    }

    private boolean isMotionMode() {
        AntiCheatModule ac = antiCheat();
        if (ac == null) return false;
        RotationsModule rotations = Anatorius.MODULE_MANAGER.getModule(RotationsModule.class);
        boolean movementFix = rotations != null && rotations.movementFix.getValue();
        return movementFix && ac.rotations.getValue().equalsIgnoreCase("Motion");
    }

    @SubscribeEvent
    public void onUpdateVelocity(UpdateVelocityEvent event) {
        if (mc.player == null) return;
        if (!Anatorius.MODULE_MANAGER.getModule(RotationsModule.class).movementFix.getValue()) return;
        if (rotation == null) return;

        event.setVelocity(EntityAccessor.invokeMovementInputToVelocity(event.getMovementInput(), event.getSpeed(), rotation.getYaw()));
        event.setCancelled(true);
    }

    @SubscribeEvent
    public void onKeyboardTick(KeyboardTickEvent event) {
        if (mc.player == null || mc.world == null || mc.player.isRiding()) return;
        if (!Anatorius.MODULE_MANAGER.getModule(RotationsModule.class).movementFix.getValue()) return;
        if (rotation == null) return;

        float movementForward = event.getMovementForward();
        float movementSideways = event.getMovementSideways();
        if (Math.abs(movementForward) < 1.0E-5f && Math.abs(movementSideways) < 1.0E-5f) return;

        float moveYaw = mc.player.getYaw() + (float) Math.toDegrees(Math.atan2(-movementSideways, movementForward));
        int sector = Math.floorMod(Math.round(MathHelper.wrapDegrees(moveYaw - rotation.getYaw()) / 45.0f), 8);
        float forward = MOVE_FORWARD[sector];
        float sideways = MOVE_SIDEWAYS[sector];
        float length = MathHelper.sqrt(forward * forward + sideways * sideways);

        event.setMovementForward(forward / length);
        event.setMovementSideways(sideways / length);
        event.setCancelled(true);

        boolean sprintKey = mc.player.isSprinting() && forward > 0.0f;
        PlayerInput keys = mc.player.input.playerInput;
        event.setOverrideInput(new PlayerInput(forward > 0.0f, forward < 0.0f, sideways > 0.0f, sideways < 0.0f, keys.jump(), keys.sneak(), sprintKey));
        if (forward <= 0.0f) mc.player.setSprinting(false);
    }

    private static final float[] MOVE_FORWARD = {1.0f, 1.0f, 0.0f, -1.0f, -1.0f, -1.0f, 0.0f, 1.0f};
    private static final float[] MOVE_SIDEWAYS = {0.0f, -1.0f, -1.0f, -1.0f, 0.0f, 1.0f, 1.0f, 1.0f};

    @SubscribeEvent
    public void onPlayerJump(PlayerJumpEvent event) {
        if (mc.player == null || mc.world == null || mc.player.isRiding()) return;
        if (!Anatorius.MODULE_MANAGER.getModule(RotationsModule.class).movementFix.getValue()) return;
        if (rotation == null) return;

        prevFixYaw = mc.player.getYaw();
        mc.player.setYaw(rotation.getYaw());
    }

    @SubscribeEvent
    public void onPlayerJump$POST(PlayerJumpEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.player.isRiding()) return;
        if (!Anatorius.MODULE_MANAGER.getModule(RotationsModule.class).movementFix.getValue()) return;
        if (rotation == null) return;

        mc.player.setYaw(prevFixYaw);
    }

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent event) {
        if (mc.player == null) return;

        if (event.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (!packet.changesLook()) return;

            serverYaw = packet.getYaw(mc.player.getYaw());
            serverPitch = packet.getPitch(mc.player.getPitch());
        }
    }

    public void rotate(float[] rotations, int priority) {
        rotate(rotations[0], rotations[1], priority);
    }

    public void rotate(float yaw, float pitch, int priority) {
        queue.removeIf(rotation -> rotation.getModule() == null && rotation.getPriority() == priority);
        queue.add(new Rotation(yaw, pitch, priority));
    }

    public void rotate(float[] rotations, Module module) {
        rotate(rotations[0], rotations[1], module);
    }

    public void rotate(float yaw, float pitch, Module module) {
        queue.removeIf(rotation -> rotation.getModule() == module);
        queue.add(new Rotation(yaw, pitch, module, getModulePriority(module)));
    }

    public void rotate(float[] rotations, Module module, int priority) {
        rotate(rotations[0], rotations[1], module, priority);
    }

    public void rotate(float yaw, float pitch, Module module, int priority) {
        queue.removeIf(rotation -> rotation.getModule() == module);
        queue.add(new Rotation(yaw, pitch, module, priority));
    }

    public void cancel(Module module) {
        queue.removeIf(rotation -> rotation.getModule() == module);
    }

    public boolean isHead(Module module) {
        queue.removeIf(r -> System.currentTimeMillis() - r.getTime() > 100);
        Rotation head = queue.peek();
        return head != null && head.getModule() == module;
    }

    public Rotation peek() {
        queue.removeIf(r -> System.currentTimeMillis() - r.getTime() > 100);
        return queue.peek();
    }

    public void packetRotate(float[] rotations, int modulePriority) {
        if (rotation != null && rotation.getPriority() > modulePriority) return;
        packetRotate(rotations[0], rotations[1], modulePriority);
    }

    public void packetRotate(float yaw, float pitch) {
        packetRotate(yaw, pitch, 0);
    }

    private void packetRotate(float yaw, float pitch, int priority) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (absorbing()) {
            if (!deferring) {
                deferring = true;
                tickLook = new Rotation(yaw, pitch, priority);
            }
            Look last = lastHeldLook();
            if (last == null || !sameLook(last.yaw(), last.pitch(), yaw, pitch)) deferred.add(new Look(yaw, pitch));
            return;
        }
        sendLookPacket(yaw, pitch, true);
    }

    private void sendLookPacket(float yaw, float pitch, boolean dedupTick) {
        float[] look = toPacketLook(yaw, pitch);
        yaw = look[0];
        pitch = look[1];

        if (sameLook(serverYaw, serverPitch, yaw, pitch)) return;
        if (dedupTick && yaw == lastPacketYaw && pitch == lastPacketPitch) return;
        lastPacketYaw = yaw;
        lastPacketPitch = pitch;
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(Anatorius.POSITION_MANAGER.getServerX(), Anatorius.POSITION_MANAGER.getServerY(), Anatorius.POSITION_MANAGER.getServerZ(), yaw, pitch, Anatorius.POSITION_MANAGER.isServerOnGround(), mc.player.horizontalCollision));
    }

    private float[] toPacketLook(float yaw, float pitch) {
        yaw = serverYaw + MathHelper.wrapDegrees(yaw - serverYaw);
        pitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        if (shouldApplyGCD()) return applyGCD(yaw, pitch);
        return new float[]{yaw, pitch};
    }

    private AntiCheatModule antiCheat() {
        return Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
    }

    private boolean shouldApplyGCD() {
        AntiCheatModule ac = antiCheat();
        return ac != null && (ac.grimJitter.getValue() || ac.movementPackets.getValue().equalsIgnoreCase("Grim"));
    }

    private float[] applyGCD(float yaw, float pitch) {
        double sensitivity = mc.options.getMouseSensitivity().getValue();
        double f = sensitivity * 0.6 + 0.2;
        double gcd = f * f * f * 1.2;
        if (gcd <= 0.0) return new float[]{yaw, pitch};

        float quantizedYaw = serverYaw + (float) (Math.round((yaw - serverYaw) / gcd) * gcd);
        float quantizedPitch = serverPitch + (float) (Math.round((pitch - serverPitch) / gcd) * gcd);
        return new float[]{quantizedYaw, MathHelper.clamp(quantizedPitch, -90.0f, 90.0f)};
    }

    private static final float RENDER_ROTATION_SMOOTHING_MS = 25.0f;
    private long lastRenderFrame;

    public boolean inRenderTime() {
        return System.currentTimeMillis() - lastRenderTime < 1000;
    }

    public float[] getRenderRotations() {
        float from = MathUtils.wrapAngle(prevRenderYaw), to = MathUtils.wrapAngle(rotation == null ? mc.player.getYaw() : getServerYaw());
        float delta = to - from;
        if (delta > 180) delta -= 360;
        else if (delta < -180) delta += 360;

        long now = System.currentTimeMillis();
        float progress = 1.0f - (float) Math.exp(-(now - lastRenderFrame) / RENDER_ROTATION_SMOOTHING_MS);
        lastRenderFrame = now;
        float yaw = MathHelper.lerp(progress, from, from + delta);
        float pitch = MathHelper.lerp(progress, prevRenderPitch, rotation == null ? mc.player.getPitch() : getServerPitch());
        prevRenderYaw = yaw;
        prevRenderPitch = pitch;

        return new float[]{yaw, pitch};
    }

    public int getModulePriority(Module module) {
        return PRIORITIES.getOrDefault(module.getName(), 0);
    }

    private int compareRotations(Rotation target, Rotation rotation) {
        if (target.getPriority() == rotation.getPriority()) return -Long.compare(target.getTime(), rotation.getTime());
        return -Integer.compare(target.getPriority(), rotation.getPriority());
    }
}
