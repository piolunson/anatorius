package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.KeyboardTickEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.utils.animations.Easing;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer3D;
import net.minecraft.client.util.math.MatrixStack;
import java.awt.Color;
import dev.project.anatorius.events.impl.UpdateMovementEvent;
import dev.project.anatorius.mixins.accessors.ClientPlayerInteractionManagerAccessor;
import dev.project.anatorius.mixins.accessors.FireworkRocketEntityAccessor;
import net.minecraft.component.type.FireworksComponent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.movement.ElytraFlyModule;
import dev.project.anatorius.modules.impl.movement.FakeFlyModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.EntityUtils;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.NetworkUtils;
import dev.project.anatorius.utils.rotations.Rotation;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@RegisterModule(name = "AutoMace", description = "Dives onto targets with an elytra, lands a mace smash and flies straight back up.", category = Module.Category.COMBAT)
public class AutoMaceModule extends Module {
    public static AutoMaceModule INSTANCE;
    public static volatile boolean debug;

    private static final int PRIORITY = 94;
    private static final int MAX_STRIKE_TICKS = 30;

    public NumberSetting range        = new NumberSetting("Range",        "Max horizontal distance to a target.", 32.0, 8.0, 64.0);
    public BooleanSetting assistant   = new BooleanSetting("Assistant",   "Still flies on its own, but no climb high over the target and no dive onto its head: it comes in from the side a few blocks up and hits level with it. A smash only counts how far you've dropped since you last went up, not how high over the target you are. Works best with FakeFly (chestplate stays on, every bit of the way down counts).", false);
    public NumberSetting minFall      = new NumberSetting("MinFall",      "Assistant: fall (as the server counts it) the hit waits for, and so how far above the target's head the run in starts. A smash needs more than 1.5; each block up to 3 adds 4 damage, up to 8 adds 2, then 1.", new BooleanSetting.Visibility(assistant, true), 2.5, 1.6, 10.0);
    public NumberSetting height       = new NumberSetting("Height",       "Blocks above the target the dive starts from. Higher hits harder but takes longer.", new BooleanSetting.Visibility(assistant, false), 12.0, 5.0, 40.0);
    public BooleanSetting autoSwap    = new BooleanSetting("AutoSwap",     "Works out when to swap on the chestplate so the hit lands above the target and never on the ground. Off: use SwapDistance.", true);
    public NumberSetting swapDistance = new NumberSetting("SwapDistance", "Distance to the target at which the elytra comes off and the chestplate goes on for the hit.", new BooleanSetting.Visibility(autoSwap, false), 5.0, 2.0, 8.0);
    public BooleanSetting smart       = new BooleanSetting("Smart",       "Plans the dive: flies the glide ahead tick by tick (physics, your rocket, where the target walks), takes the way in that hits with the most fall, and swaps the chestplate on by when the hit will land instead of by distance.", true);
    public NumberSetting angle        = new NumberSetting("Angle",        "Smart: how steep it climbs (degrees up). Within 12 blocks of the target it climbs away from it, so it never hangs over its head (crystals) and comes back in at an angle.", new BooleanSetting.Visibility(smart, true), 75.0, 45.0, 90.0);
    public NumberSetting swapLead     =new NumberSetting("SwapLead",     "Ticks the chestplate goes on before the hit. The server only ends the glide on its own tick, and a smash while it still has you gliding is a plain hit. Raise it at high ping (2 at 100 ms and up).", 1, 1, 3);    public NumberSetting attackRange  = new NumberSetting("AttackRange",  "Distance (eye to hitbox, the way the server measures) the hit goes out at.", 3.0, 2.0, 4.5);
    public BooleanSetting wallCheck   = new BooleanSetting("WallCheck",   "Never hits through a block: the hit waits until a straight line from your eyes reaches its hitbox. Off: in reach and looking at it is enough (the server doesn't check walls).", false);
    public BooleanSetting autoBoost   = new BooleanSetting("Firework",    "Uses fireworks to take off and climb back up, the next one as soon as the last burns out.", true);
    public BooleanSetting debugLog    = new BooleanSetting("Debug",       "Writes every stage change (climb, dive, strike...) to latest.log, to look at when it gets stuck.", false);

    public ModeSetting    switchMode  = new ModeSetting("Switch",      "How the mace is switched to for the hit.", "Normal", new String[]{"Normal", "Silent"});
    public BooleanSetting swapBack     = new BooleanSetting("SwapBack",    "Goes back to your old slot a moment after the hit. Off: the mace stays in your hand.", new ModeSetting.Visibility(switchMode, "Normal"), true);
    public BooleanSetting alternative  = new BooleanSetting("Alternative", "Swaps the mace into your hand slot through the inventory (AltSwap), so it can also come from outside the hotbar.", new ModeSetting.Visibility(switchMode, "Silent"), false);

    public CategorySetting targetsCategory = new CategorySetting("Targets", "Which entities should be attacked.");
    public BooleanSetting  targetPlayers   = new BooleanSetting("Players",  "Attack players.",          new CategorySetting.Visibility(targetsCategory), true);
    public BooleanSetting  targetFriends   = new BooleanSetting("Friends",  "Attack friended players.", new CategorySetting.Visibility(targetsCategory), false);
    public BooleanSetting  targetNakeds    = new BooleanSetting("Nakeds",   "Attack players wearing no armor.", new CategorySetting.Visibility(targetsCategory), false);
    public BooleanSetting  targetHostiles  = new BooleanSetting("Hostiles", "Attack hostile mobs.",     new CategorySetting.Visibility(targetsCategory), false);
    public BooleanSetting  targetAnimals   = new BooleanSetting("Animals",  "Attack animals.",          new CategorySetting.Visibility(targetsCategory), false);

    public CategorySetting renderCategory = new CategorySetting("Render", "Shockwaves around the target.");
    public BooleanSetting  renderWaves    = new BooleanSetting("Waves",     "Shockwaves rolling out from the target's feet for as long as it's the target.", new CategorySetting.Visibility(renderCategory), true);
    public NumberSetting   waveCount      = new NumberSetting("Count",      "Waves out at once.", new CategorySetting.Visibility(renderCategory), 3, 1, 5);
    public NumberSetting   waveSize       = new NumberSetting("Size",       "How far out a wave rolls (blocks).", new CategorySetting.Visibility(renderCategory), 2.5, 1.0, 5.0);
    public NumberSetting   waveSpeed      = new NumberSetting("Speed",      "Seconds a wave takes to roll out.", new CategorySetting.Visibility(renderCategory), 1.4, 0.5, 3.0);
    public BooleanSetting  hitBurst       = new BooleanSetting("HitBurst",  "A big double shockwave where each hit lands.", new CategorySetting.Visibility(renderCategory), true);
    public ColorSetting    renderColor    = new ColorSetting("Color",       "A wave's front as it starts out.", new CategorySetting.Visibility(renderCategory), new ColorSetting.Color(new Color(92, 210, 230, 150), false, false));
    public ColorSetting    fadeColor      = new ColorSetting("FadeColor",   "What a wave turns into as it rolls out and fades.", new CategorySetting.Visibility(renderCategory), new ColorSetting.Color(new Color(150, 110, 255, 150), false, false));

    private enum Stage { IDLE, TAKEOFF, ESCAPE, CLIMB, DIVE, STRIKE, RECOVER }

    private Stage stage = Stage.IDLE;
    private LivingEntity target;
    private String missing;
    private boolean steering;
    private int strikeTicks;
    private boolean hit;
    private double lastStrikeDistance = Double.MAX_VALUE;
    private int ticksSinceFirework = 100;
    private boolean awaitingRocket;
    private Vec3d diveStart;
    private Vec3d sideStrike;
    private int replanTicks;
    private Vec3d plannedFor;
    private boolean steepDive = true;
    private Vec3d targetMotion = Vec3d.ZERO;
    private int stuckTicks;
    private float escapeYaw;
    private int escapeTicks;
    private boolean walkForward;
    private int noPlanTicks, blockedTicks;
    private final java.util.Map<Integer, Long> skipped = new java.util.HashMap<>();
    private static final long SKIP_MS = 3000L;

    private double serverFall;
    private double lastSentY = Double.NaN;

    private final AutoMaceStrikePlanner planner = new AutoMaceStrikePlanner();

    private boolean modelElytra;
    private int armorSlot = -1;
    private int armorAge = 1000;

    private int glideGrace;

    private boolean rocketAlive;
    private int rocketTicksLeft;

    public AutoMaceModule() {
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        stage = Stage.IDLE;
        target = null;
        steering = false;
        ticksSinceFirework = 100;
        diveStart = null;
        plannedFor = null;
        noPlanTicks = blockedTicks = 0;
        skipped.clear();
        planner.reset();
        armorSlot = -1;
        armorAge = 1000;
        glideGrace = 0;
        serverFall = getNull() ? 0.0 : mc.player.fallDistance;
        lastSentY = getNull() ? Double.NaN : mc.player.getY();
        ElytraFlyModule elytraFly = Anatorius.MODULE_MANAGER.getModule(ElytraFlyModule.class);
        if (elytraFly != null && elytraFly.isToggled()) elytraFly.setToggled(false);
    }

    @Override
    public void onDisable() {
        if (swapBackSlot != -1 && !getNull() && mc.player.getInventory().getSelectedSlot() == swapBackHoldSlot) {
            InventoryUtils.switchBack("Normal", swapBackHoldSlot, swapBackSlot);
        }
        swapBackSlot = -1;
        stage = Stage.IDLE;
        target = null;
        steering = false;
        walkForward = false;
        stuckTicks = 0;
    }

    private boolean viaFakeFly() {
        return FakeFlyModule.INSTANCE != null && FakeFlyModule.INSTANCE.isToggled();
    }

    private boolean gliding() {
        if (viaFakeFly()) return !mc.player.isOnGround() && !mc.player.isTouchingWater();
        return mc.player.isGliding() || (glideGrace > 0 && !mc.player.isOnGround());
    }

    public static boolean ownsFakeFlyFireworks() {
        return INSTANCE != null && INSTANCE.isToggled() && INSTANCE.viaFakeFly() && INSTANCE.target != null && INSTANCE.autoBoost.getValue();
    }

    public boolean hasTarget() {
        return isToggled() && target != null;
    }

    public static boolean busy() {
        return INSTANCE != null && INSTANCE.hasTarget() && INSTANCE.stage != Stage.IDLE;
    }

    public static float[] glideLook(Entity entity) {
        if (INSTANCE == null || !INSTANCE.isToggled() || !INSTANCE.steering || entity != mc.player) return null;
        if (!mc.player.isGliding() && !FakeFlyModule.glides(entity)) return null;
        Rotation head = Anatorius.ROTATION_MANAGER.peek();
        if (head == null || head.getModule() != INSTANCE) return null;
        return new float[]{head.getYaw(), head.getPitch()};
    }

    @Override
    public String getMetaData() {
        if (missing != null) return "No " + missing;
        if (target == null) return "Idle";
        if (stage == Stage.CLIMB && diveStart == null && !climbsAway()) return "No way down " + target.getName().getString();
        String meta = stage.name().charAt(0) + stage.name().substring(1).toLowerCase() + " " + target.getName().getString();
        return assistant.getValue() && (stage == Stage.DIVE || stage == Stage.STRIKE) ? meta + String.format(" %.1f", serverFall) : meta;
    }

    @SubscribeEvent
    public void onKeyboardTick(KeyboardTickEvent event) {
        if (walkForward) event.setForceForward(true);
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        steering = false;
        walkForward = false;
        if (getNull()) return;
        ticksSinceFirework++;
        armorAge++;
        if (glideGrace > 0) glideGrace--;

        tickSwapBack();
        scanRocket();
        updateFallEstimate();

        int elytra = findElytra();
        int chestplate = findChestplate();
        int mace = findMace();
        boolean swapping = !armorSettled() && armorSlot != -1;
        boolean hasElytra = swapping || chestIsElytra() || elytra != -1;
        boolean hasChestplate = swapping || chestIsChestplate() || chestplate != -1;
        missing = !hasElytra ? "Elytra" : !hasChestplate ? "Chestplate" : mace == -1 ? "Mace" : null;

        LivingEntity picked = pickTarget();
        if (picked != null && picked == target) {
            blockedTicks = hasApproachPath(picked) ? 0 : blockedTicks + 1;
            if (blockedTicks > 20 || noPlanTicks > 30) {
                skip(picked);
                if (stage == Stage.STRIKE && hasElytra) recover(elytra);
                picked = pickTarget();
            }
        }
        if (picked != target) {
            noPlanTicks = blockedTicks = 0;
            diveStart = null;
            plannedFor = null;
            targetMotion = Vec3d.ZERO;
            planner.reset();
        }
        target = picked;
        if (target != null) trackTargetMotion();
        if (target == null || missing != null || mc.player.isTouchingWater() || mc.player.hasVehicle()) {
            if (stage == Stage.STRIKE && hasElytra) recover(elytra);
            stage = Stage.IDLE;
            return;
        }

        double dy = mc.player.getY() - target.getY();
        double horizontal = horizontalDistance(target);

        Stage before = stage;
        runStage(dy, horizontal, elytra, chestplate, mace);
        if (stage != Stage.DIVE && stage != Stage.STRIKE) planner.reset();
        if ((debug || debugLog.getValue()) && stage != before) {
            Anatorius.LOGGER.info("[AutoMace] {} -> {} dy={} h={} hit={}", before, stage,
                    String.format("%.1f", dy), String.format("%.1f", horizontal), hit);
        }
    }

    private void runStage(double dy, double horizontal, int elytra, int chestplate, int mace) {
        if (!mc.player.isOnGround()) stuckTicks = 0;
        switch (stage) {
            case IDLE -> stage = mc.player.isGliding() ? Stage.CLIMB : Stage.TAKEOFF;
            case TAKEOFF -> takeoff(elytra);
            case ESCAPE -> escape(elytra);
            case CLIMB -> climb(dy, horizontal);
            case DIVE -> dive(dy, horizontal, chestplate, mace);
            case STRIKE -> strike(dy, elytra);
            case RECOVER -> recover(elytra);
        }
    }

    @SubscribeEvent
    public void onUpdateMovementPost(UpdateMovementEvent.Post event) {
        if (getNull() || stage != Stage.STRIKE || hit || target == null || mc.interactionManager == null) return;
        if (!viaFakeFly() && strikeTicks < swapLeadTicks()) return;

        if (assistant.getValue() && serverFall <= minFall.getValue().doubleValue()) return;
        if (!inReachAndLooking(target)) return;
        if (wallCheck.getValue() && !visible(target)) return;

        int mace = findMace();
        if (mace == -1) return;

        landHit(mace);
        hit = true;
        burstAt = System.currentTimeMillis();
        burstPos = new Vec3d(target.getX(), target.getY(), target.getZ());
        if (debug || debugLog.getValue()) {
            Anatorius.LOGGER.info("[AutoMace] hit {} fall={}", target.getName().getString(), String.format("%.1f", serverFall));
        }
        serverFall = 0.0;
    }

    private boolean inReachAndLooking(LivingEntity entity) {
        Vec3d eye = mc.player.getEyePos();
        if (Math.sqrt(entity.getBoundingBox().squaredMagnitude(eye)) > attackRange.getValue().doubleValue()) return false;
        float[] serverLook = {Anatorius.ROTATION_MANAGER.getServerYaw(), Anatorius.ROTATION_MANAGER.getServerPitch()};
        return lookingAt(entity, eye, serverLook, attackRange.getValue().doubleValue() + 1.0);
    }

    private boolean visible(LivingEntity entity) {
        Vec3d eye = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        Vec3d nearest = new Vec3d(MathHelper.clamp(eye.x, box.minX, box.maxX), MathHelper.clamp(eye.y, box.minY, box.maxY), MathHelper.clamp(eye.z, box.minZ, box.maxZ));
        for (Vec3d point : new Vec3d[]{nearest, box.getCenter(), headPoint(entity).add(0.0, -0.1, 0.0)}) {
            HitResult result = mc.world.raycast(new RaycastContext(eye, point, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (result.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    private void landHit(int mace) {
        String type = maceSwitchType();
        int prev = mc.player.getInventory().getSelectedSlot();
        boolean tempSwitch = false;
        if (type.equals("Normal")) {
            if (prev != mace) InventoryUtils.switchSlot("Normal", mace, prev);
        } else if (!(type.equals("Silent") && prev == mace)) {
            InventoryUtils.switchSlot(type, mace, prev);
            tempSwitch = true;
        }

        boolean sprinting = mc.player.isSprinting() || Anatorius.POSITION_MANAGER.isServerSprinting();
        if (sprinting) mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        if (tempSwitch) InventoryUtils.switchBack(type, mace, prev);
        else if (type.equals("Normal") && prev != mace) scheduleSwapBack(mace, prev);
    }

    private void updateFallEstimate() {
        if (mc.player.isTouchingWater() || mc.player.isClimbing() || mc.player.hasVehicle()
                || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING) || mc.player.hasStatusEffect(StatusEffects.LEVITATION)) {
            serverFall = 0.0;
        }
        if (mc.player.isInLava()) serverFall *= 0.5;
        if (mc.player.isGliding() && !viaFakeFly() && chestIsElytra() && mc.player.getVelocity().y > -0.5) serverFall = Math.min(serverFall, 1.0);
    }

    @SubscribeEvent
    public void onPacketSendPost(PacketSendEvent.Post event) {
        if (!(event.getPacket() instanceof PlayerMoveC2SPacket packet) || mc.player == null) return;
        double y = packet.changesPosition() ? packet.getY(lastSentY) : lastSentY;
        if (packet.isOnGround() || Double.isNaN(lastSentY) || Double.isNaN(y)) {
            serverFall = packet.isOnGround() ? 0.0 : serverFall;
        } else if (y > lastSentY) {
            serverFall = 0.0;
        } else if (y < lastSentY && lastSentY - y < 5.0) {
            serverFall += lastSentY - y;
        }
        lastSentY = y;
    }

    private void takeoff(int elytra) {
        if (viaFakeFly()) {
            if (!mc.player.isOnGround()) {
                stuckTicks = 0;
                steer(yawTo(target), -90.0f);
                boost(true);
                stage = Stage.CLIMB;
                return;
            }
            if (ceilingAbove(3.5) || ++stuckTicks > 6) {
                beginEscape();
                return;
            }
            mc.player.jump();
            return;
        }
        if (mc.player.isGliding()) {
            stuckTicks = 0;
            stage = Stage.CLIMB;
            return;
        }
        equipChest(true);
        if (mc.player.isOnGround()) {
            if (ceilingAbove(3.5) || ++stuckTicks > 6) {
                beginEscape();
                return;
            }
            mc.player.jump();
            return;
        }
        if (mc.player.getVelocity().y < 0.1 && startGlide()) {
            stuckTicks = 0;
            steer(yawTo(target), -90.0f);
            boost(true);
            stage = Stage.CLIMB;
        }
    }

    private void beginEscape() {
        escapeTicks = 40;
        escapeYaw = openSkyYaw();
        stage = Stage.ESCAPE;
    }

    private void escape(int elytra) {
        if (!mc.player.isOnGround()) {
            stage = Stage.TAKEOFF;
            return;
        }
        if (--escapeTicks <= 0) {
            stuckTicks = 0;
            stage = Stage.IDLE;
            return;
        }
        if (!ceilingAbove(4.5)) {
            stuckTicks = 0;
            stage = Stage.TAKEOFF;
            return;
        }
        if (escapeTicks % 5 == 0) escapeYaw = openSkyYaw();
        mc.player.setYaw(escapeYaw);
        walkForward = true;
    }

    private float openSkyYaw() {
        double bestClearance = -1.0;
        float bestYaw = yawTo(target);
        for (int i = 0; i < 8; i++) {
            float yaw = i * 45.0f;
            double rad = Math.toRadians(yaw);
            double dx = -Math.sin(rad), dz = Math.cos(rad);
            Vec3d spot = mc.player.getEntityPos().add(dx * 1.5, 0.0, dz * 1.5);
            if (!walkableStep(spot)) continue;
            double clearance = openAbove(spot);
            if (clearance > bestClearance) {
                bestClearance = clearance;
                bestYaw = yaw;
            }
        }
        return bestYaw;
    }

    private double openAbove(Vec3d spot) {
        for (int up = 1; up <= 6; up++) {
            Box box = new Box(spot.x - 0.3, spot.y + up, spot.z - 0.3, spot.x + 0.3, spot.y + up + 1.0, spot.z + 0.3);
            if (!mc.world.isSpaceEmpty(box)) return up - 1;
        }
        return 6;
    }

    private boolean walkableStep(Vec3d spot) {
        Box body = new Box(spot.x - 0.3, spot.y + 0.1, spot.z - 0.3, spot.x + 0.3, spot.y + 1.8, spot.z + 0.3);
        if (!mc.world.isSpaceEmpty(body)) return false;
        Box under = new Box(spot.x - 0.3, spot.y - 1.1, spot.z - 0.3, spot.x + 0.3, spot.y, spot.z + 0.3);
        return !mc.world.isSpaceEmpty(under);
    }

    private boolean ceilingAbove(double blocks) {
        Box above = mc.player.getBoundingBox().stretch(0.0, blocks, 0.0);
        return !mc.world.isSpaceEmpty(mc.player, above);
    }

    private void climb(double dy, double horizontal) {
        if (!gliding()) {
            stage = mc.player.isOnGround() ? Stage.TAKEOFF : Stage.RECOVER;
            return;
        }
        if (climbsAway()) {
            climbAway(horizontal);
            return;
        }
        plan();
        noPlanTicks = diveStart == null ? noPlanTicks + 1 : 0;

        Vec3d goal = diveStart != null ? diveStart : roofed(target) ? standOff() : headPoint(target).add(0.0, diveHeight(), 0.0);
        Vec3d me = gliderCenter();
        double toGoalFlat = Math.hypot(goal.x - me.x, goal.z - me.z);
        double toGoalUp = goal.y - me.y;

        if (diveStart != null && ((toGoalFlat <= 2.0 && toGoalUp <= 1.0) || (toGoalUp <= 0.0 && canDiveFromHere(dy, horizontal)))) {
            stage = Stage.DIVE;
            return;
        }

        float pitch;
        if (toGoalUp > 1.0) {
            float lean = (float) MathHelper.clamp((toGoalFlat - 1.0) / 5.0, 0.0, 1.0);
            pitch = MathHelper.lerp(lean, -90.0f, -35.0f);
        } else if (toGoalFlat > 2.0) {
            pitch = toGoalUp < -3.0 ? 20.0f : 0.0f;
        } else {
            pitch = diveStart != null ? 60.0f : 0.0f;
        }
        if (blockedAhead()) pitch = Math.min(pitch, -60.0f);
        if (ceilingAbove(2.5)) pitch = Math.max(pitch, 0.0f);
        else if (pitch > -30.0f && groundAhead()) pitch = -35.0f;
        steer(yawTo(goal), pitch);

        if (toGoalUp > -1.0 || toGoalFlat > 2.0) boost(false);
    }

    private boolean climbsAway() {
        return smart.getValue() && !assistant.getValue();
    }

    private static final double AWAY_DISTANCE = 12.0;
    private int climbAwayTicks;

    private void climbAway(double horizontal) {
        noPlanTicks = 0;
        double above = mc.player.getY() - target.getBoundingBox().maxY;
        if (climbAwayTicks > 0) climbAwayTicks--;
        else if (above > height.getValue().doubleValue() || ceilingAbove(2.5)) {
            stage = Stage.DIVE;
            return;
        }
        steer(awayYaw(horizontal), -angle.getValue().floatValue());
        boost(false);
    }

    private float awayYaw(double horizontal) {
        float yaw = yawTo(target);
        return horizontal < AWAY_DISTANCE ? MathHelper.wrapDegrees(yaw + 180.0f) : yaw;
    }

    private void dive(double dy, double horizontal, int chestplate, int mace) {
        if (!gliding()) {
            stage = Stage.RECOVER;
            return;
        }
        if (smartPlan()) {
            if (planner.hitTicks() <= swapLeadTicks() + 1) {
                beginStrike(chestplate, mace);
                return;
            }
            steer(planner.yaw(), planner.pitch());
            return;
        }
        double swap = effectiveSwapDistance();
        double distance = Math.sqrt(target.getBoundingBox().squaredMagnitude(mc.player.getEyePos()));
        double nextDistance = Math.sqrt(target.getBoundingBox().squaredMagnitude(predictedEye()));
        if (distance <= swap || nextDistance <= swap) {
            beginStrike(chestplate, mace);
            return;
        }
        double ledHorizontal = horizontalDistance(ledTargetPos(ticksToReach(distance)));
        boolean ranOut = steepDive && distance > swap * 2.0 && ledHorizontal > dy * 1.2 + 2.0;
        boolean low = assistant.getValue();
        if (dy < (low ? -2.0 : -1.0) || ranOut || !pathClear(gliderCenter(), diveEnd())) {
            diveStart = null;
            replanTicks = 0;
            climbAwayTicks = 10;
            stage = Stage.CLIMB;
            return;
        }
        double groundRoom = low ? Math.max(1.0, -mc.player.getVelocity().y) : Math.max(1.5, -mc.player.getVelocity().y * 2.0);
        if (distance > swap + 1.0 && groundWithin(groundRoom)) {
            stage = Stage.RECOVER;
            return;
        }

        Vec3d aim = sideStrike != null && gliderCenter().distanceTo(sideStrike) > 1.5 ? sideStrike : aimPoint(target);
        float[] look = rotationsFrom(predictedEye(), aim);
        look[1] = Math.max(look[1], low ? 15.0f : steepDive ? 40.0f : 5.0f);
        steer(look[0], look[1]);
    }

    private static final double[] PLAN_RADII = {0.0, 2.5, 4.0, 6.0, 8.0};
    private static final double[] ASSIST_RADII = {4.0, 3.0, 5.5, 2.0, 0.0};
    private static final int PLAN_DIRECTIONS = 12;

    private void plan() {
        double lead = ticksToReach(mc.player.getEyePos().distanceTo(target.getEyePos()));
        Vec3d at = ledTargetPos(lead);
        if (diveStart != null && plannedFor != null && plannedFor.squaredDistanceTo(at) < 9.0 && --replanTicks > 0) return;
        replanTicks = 10;
        plannedFor = at;
        diveStart = findDiveStart(at.subtract(target.getEntityPos()));
    }

    private Vec3d findDiveStart(Vec3d lead) {
        sideStrike = null;
        if (roofed(target)) return findSideStart(lead);
        Vec3d head = headPoint(target).add(lead.x, 0.0, lead.z);
        Vec3d me = gliderCenter();
        boolean low = assistant.getValue();
        double full = diveHeight();
        double[] heights = low ? new double[]{full, full + 1.5, full - 0.8} : new double[]{full, full * 0.75, full * 0.5, 5.0, 3.0};
        double[] radii = low ? ASSIST_RADII : PLAN_RADII;
        double lowest = low ? minFall.getValue().doubleValue() + 0.5 : 3.0;
        for (double h : heights) {
            if (h < lowest) continue;
            for (double r : radii) {
                Vec3d best = null;
                double bestDistance = Double.MAX_VALUE;
                int directions = r == 0.0 ? 1 : PLAN_DIRECTIONS;
                for (int i = 0; i < directions; i++) {
                    double angle = Math.PI * 2.0 * i / directions;
                    Vec3d start = head.add(Math.cos(angle) * r, h, Math.sin(angle) * r);
                    if (!pathClear(start, head)) continue;
                    double distance = me.squaredDistanceTo(start);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = start;
                    }
                }
                if (best != null) {
                    steepDive = h >= r * 0.84;
                    return best;
                }
            }
        }
        return null;
    }

    private static final double ROOF_ROOM = 4.0;

    private double roomOverHead(LivingEntity entity) {
        Box box = entity.getBoundingBox();
        for (int up = 0; up < 8; up++) {
            if (!mc.world.isSpaceEmpty(new Box(box.minX, box.maxY + up, box.minZ, box.maxX, box.maxY + up + 1.0, box.maxZ))) return up;
        }
        return 8.0;
    }

    private boolean roofed(LivingEntity entity) {
        return roomOverHead(entity) < ROOF_ROOM;
    }

    private Vec3d findSideStart(Vec3d lead) {
        Vec3d feet = target.getEntityPos().add(lead.x, 0.0, lead.z);
        Vec3d me = gliderCenter();
        double out = Math.max(1.2, Math.min(attackRange.getValue().doubleValue(), 3.0) - 0.8);
        double strikeY = feet.y + target.getHeight() * 0.6;
        for (double h : new double[]{5.0, 3.5, 2.0}) {
            for (double r : new double[]{3.0, 5.0, 7.0}) {
                Vec3d best = null, bestStrike = null;
                double bestDistance = Double.MAX_VALUE;
                for (int i = 0; i < PLAN_DIRECTIONS; i++) {
                    double angle = Math.PI * 2.0 * i / PLAN_DIRECTIONS;
                    double cx = Math.cos(angle), cz = Math.sin(angle);
                    Vec3d strike = new Vec3d(feet.x + cx * out, strikeY, feet.z + cz * out);
                    if (!mc.world.isSpaceEmpty(new Box(strike.x - 0.3, strike.y - 0.9, strike.z - 0.3, strike.x + 0.3, strike.y + 0.9, strike.z + 0.3))) continue;
                    Vec3d start = strike.add(cx * r, h, cz * r);
                    if (!pathClear(start, strike)) continue;
                    double distance = me.squaredDistanceTo(start);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = start;
                        bestStrike = strike;
                    }
                }
                if (best != null) {
                    steepDive = h >= r * 0.84;
                    sideStrike = bestStrike;
                    return best;
                }
            }
        }
        return null;
    }

    private Vec3d diveEnd() {
        return sideStrike != null ? sideStrike : headPoint(target);
    }

    private Vec3d standOff() {
        Vec3d at = target.getEntityPos(), me = gliderCenter();
        double dx = me.x - at.x, dz = me.z - at.z, length = Math.hypot(dx, dz);
        if (length < 0.1) { dx = 1.0; dz = 0.0; length = 1.0; }
        return new Vec3d(at.x + dx / length * 6.0, headPoint(target).y + 4.0, at.z + dz / length * 6.0);
    }

    private boolean canDiveFromHere(double dy, double horizontal) {
        if (sideStrike != null) return false;
        if (assistant.getValue()) {
            if (dy < minFall.getValue().doubleValue() + 0.5 || horizontal > Math.max(8.0, dy * 3.5)) return false;
            return pathClear(gliderCenter(), headPoint(target));
        }
        if (dy < 3.0) return false;
        if (steepDive && horizontal > dy * 0.84) return false;
        return pathClear(gliderCenter(), headPoint(target));
    }

    private double diveHeight() {
        return assistant.getValue() ? minFall.getValue().doubleValue() + 1.5 : height.getValue().doubleValue();
    }

    private boolean pathClear(Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        double length = delta.length();
        if (length < 1.0E-4) return true;
        Vec3d step = delta.multiply(1.0 / length);
        for (double t = 0.0; t <= length; t += 0.5) {
            Vec3d p = from.add(step.multiply(t));
            if (!mc.world.isSpaceEmpty(new Box(p.x - 0.35, p.y - 0.3, p.z - 0.35, p.x + 0.35, p.y + 0.4, p.z + 0.35))) return false;
        }
        return true;
    }

    private boolean blockedAhead() {
        Vec3d velocity = mc.player.getVelocity();
        if (velocity.lengthSquared() < 0.01) return false;
        Vec3d from = gliderCenter();
        return !pathClear(from, from.add(velocity.normalize().multiply(4.0)));
    }

    private Vec3d gliderCenter() {
        return mc.player.getEntityPos().add(0.0, 0.3, 0.0);
    }

    private static Vec3d headPoint(LivingEntity entity) {
        Box box = entity.getBoundingBox();
        return new Vec3d((box.minX + box.maxX) / 2.0, box.maxY, (box.minZ + box.maxZ) / 2.0);
    }

    private void beginStrike(int chestplate, int mace) {
        if (viaFakeFly()) {
            stage = Stage.STRIKE;
            strikeTicks = 0;
            hit = false;
            lastStrikeDistance = Double.MAX_VALUE;
            steerAt(target);
            return;
        }
        equipChest(false);
        stage = Stage.STRIKE;
        strikeTicks = 0;
        hit = false;
        lastStrikeDistance = Double.MAX_VALUE;
        if (gliding()) steerAt(target);
        else aimAt(target);
    }

    private boolean smartPlan() {
        if (!smart.getValue() || !gliding()) return false;
        planner.update(target, targetMotion, attackRange.getValue().doubleValue(), rocketTicksLeft, !viaFakeFly() && chestIsElytra(), serverFall);
        return planner.isActive();
    }

    private int swapLeadTicks() {
        return swapLead.getValue().intValue();
    }

    private int pingTicks() {
        return (Anatorius.SERVER_MANAGER.getPing() + 49) / 50;
    }

    private double effectiveSwapDistance() {
        if (!autoSwap.getValue()) return swapDistance.getValue().doubleValue();
        double speed = mc.player.getVelocity().length();
        int lead = swapLeadTicks();
        return MathHelper.clamp(attackRange.getValue().doubleValue() + 0.5 + speed * lead, 3.0, 4.0 + 2.0 * lead);
    }

    private void strike(double dy, int elytra) {
        strikeTicks++;
        double distance = Math.sqrt(target.getBoundingBox().squaredMagnitude(mc.player.getEyePos()));
        boolean outOfReach = distance > attackRange.getValue().doubleValue();
        boolean passing = outOfReach && distance > lastStrikeDistance + 0.05;
        boolean ground = outOfReach && groundWithin(Math.max(1.0, -mc.player.getVelocity().y * 1.2));
        lastStrikeDistance = distance;
        boolean missed = strikeTicks > MAX_STRIKE_TICKS || dy < (assistant.getValue() ? -2.0 : -0.5) || mc.player.isOnGround() || passing || ground;
        if (hit || missed) {
            recover(elytra);
            return;
        }
        if (!gliding()) {
            aimAt(target);
            return;
        }
        if (smartPlan() && planner.hitTicks() > 1) steer(planner.yaw(), planner.pitch());
        else steerAt(target);
    }

    private void recover(int elytra) {
        if (mc.player.isOnGround()) {
            stage = Stage.TAKEOFF;
            return;
        }
        if (viaFakeFly()) {
            steerRecover();
            boost(true);
            stage = Stage.CLIMB;
            return;
        }
        boolean swapBack = !chestIsElytra();
        boolean restart = swapBack && armorAge >= 2;
        equipChest(true);
        if (restart && chestIsElytra()) {
            forceGlide();
            steerRecover();
            boost(true);
            stage = Stage.CLIMB;
        } else if (startGlide()) {
            steerRecover();
            boost(true);
            stage = Stage.CLIMB;
        } else {
            stage = Stage.RECOVER;
        }
    }

    private void forceGlide() {
        mc.player.startGliding();
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        glideGrace = pingTicks() + 3;
    }

    private boolean startGlide() {
        if (mc.player.isGliding() || glideGrace > 0) return true;
        if (mc.player.isOnGround() || !chestIsElytra()) return false;
        mc.player.startGliding();
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        return true;
    }

    private void steer(float yaw, float pitch) {
        Anatorius.ROTATION_MANAGER.rotate(yaw, MathHelper.clamp(pitch, -90.0f, 90.0f), this, PRIORITY);
        steering = gliding();
    }

    private void steerAt(LivingEntity entity) {
        float[] look = rotationsFrom(predictedEye(), aimPoint(entity));
        if (assistant.getValue()) look[1] = Math.max(look[1], 10.0f);
        steer(look[0], look[1]);
    }

    private void steerRecover() {
        float yaw = yawTo(target != null ? target : mc.player);
        if (ceilingAbove(4.0)) steer(MathHelper.wrapDegrees(yaw + 180.0f), 0.0f);
        else if (climbsAway() && target != null) steer(awayYaw(horizontalDistance(target)), -angle.getValue().floatValue());
        else steer(yaw, recoverPitch());
    }

    private float recoverPitch() {
        return assistant.getValue() ? -50.0f : -90.0f;
    }

    private void aimAt(LivingEntity entity) {
        float[] look = rotationsFrom(predictedEye(), aimPoint(entity));
        Anatorius.ROTATION_MANAGER.rotate(look[0], look[1], this, PRIORITY);
    }

    private void boost(boolean force) {
        if (!autoBoost.getValue() || !gliding()) return;
        boolean burning = rocketAlive;
        if (burning) awaitingRocket = false;
        if (force) {
            if (ticksSinceFirework < 2) return;
        } else if (burning || (awaitingRocket && ticksSinceFirework < 10)) {
            return;
        }
        if (useFirework()) {
            ticksSinceFirework = 0;
            awaitingRocket = true;
        }
    }

    private void scanRocket() {
        rocketAlive = false;
        rocketTicksLeft = 0;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof FireworkRocketEntity rocket) || !rocket.isAlive() || rocket.getOwner() != mc.player) continue;
            rocketAlive = true;
            FireworksComponent fireworks = rocket.getStack().get(DataComponentTypes.FIREWORKS);
            int flight = fireworks == null ? 1 : fireworks.flightDuration();
            int left = 10 * (1 + flight) + 5 - ((FireworkRocketEntityAccessor) rocket).anatorius$getLife();
            rocketTicksLeft = Math.max(rocketTicksLeft, Math.max(1, left));
        }
    }

    private boolean useFirework() {
        if (viaFakeFly()) return FakeFlyModule.INSTANCE.requestFirework();
        if (mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) {
            useItemPacket(Hand.OFF_HAND);
            return true;
        }
        int held = mc.player.getInventory().getSelectedSlot();
        int hotbar = InventoryUtils.findHotbar(Items.FIREWORK_ROCKET);
        if (hotbar != -1) {
            InventoryUtils.switchSlot("Silent", hotbar, held);
            useItemPacket(Hand.MAIN_HAND);
            InventoryUtils.switchBack("Silent", hotbar, held);
            return true;
        }
        int index = InventoryUtils.find(Items.FIREWORK_ROCKET);
        if (index == -1) return false;
        int syncId = mc.player.playerScreenHandler.syncId;
        int slot = InventoryUtils.indexToSlot(index);
        mc.interactionManager.clickSlot(syncId, slot, held, SlotActionType.SWAP, mc.player);
        useItemPacket(Hand.MAIN_HAND);
        mc.interactionManager.clickSlot(syncId, slot, held, SlotActionType.SWAP, mc.player);
        return true;
    }

    private void useItemPacket(Hand hand) {
        float yaw = Anatorius.ROTATION_MANAGER.getServerYaw();
        float pitch = Anatorius.ROTATION_MANAGER.getServerPitch();
        NetworkUtils.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(hand, id, yaw, pitch));
    }

    private void equip(int index) {
        if (index == -1 || mc.interactionManager == null) return;
        int held = mc.player.getInventory().getSelectedSlot();
        if (index <= InventoryUtils.HOTBAR_END) {
            if (index != held) selectSlot(index);
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            if (index != held) selectSlot(held);
            return;
        }
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
        int syncId = mc.player.playerScreenHandler.syncId;
        int slot = InventoryUtils.indexToSlot(index);
        mc.interactionManager.clickSlot(syncId, slot, held, SlotActionType.SWAP, mc.player);
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.interactionManager.clickSlot(syncId, slot, held, SlotActionType.SWAP, mc.player);
    }

    private void selectSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).invokeSyncSelectedSlot();
    }

    private boolean armorSettled() {
        return armorAge >= Math.max(10, pingTicks() * 2 + 6);
    }

    private boolean chestIsElytra() {
        if (!armorSettled()) return modelElytra;
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }

    private boolean chestIsChestplate() {
        if (!armorSettled()) return !modelElytra;
        return isChestplate(mc.player.getEquippedStack(EquipmentSlot.CHEST));
    }

    private void equipChest(boolean elytra) {
        if (elytra ? chestIsElytra() : chestIsChestplate()) return;
        boolean settled = armorSettled();
        int index = !settled && armorSlot != -1 ? armorSlot : elytra ? findElytra() : findChestplate();
        if (index == -1) return;
        boolean chestEmpty = settled && mc.player.getEquippedStack(EquipmentSlot.CHEST).isEmpty();
        equip(index);
        armorSlot = chestEmpty ? -1 : index;
        modelElytra = elytra;
        armorAge = 0;
    }

    private static boolean isChestplate(ItemStack stack) {
        if (stack.isEmpty() || stack.isOf(Items.ELYTRA)) return false;
        EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    private int findElytra() {
        java.util.function.Predicate<ItemStack> usable = stack -> stack.isOf(Items.ELYTRA) && LivingEntity.canGlideWith(stack, EquipmentSlot.CHEST);
        int hotbar = InventoryUtils.find(usable, InventoryUtils.HOTBAR_START, InventoryUtils.HOTBAR_END);
        return hotbar != -1 ? hotbar : InventoryUtils.find(usable, InventoryUtils.INVENTORY_START, InventoryUtils.INVENTORY_END);
    }

    private int findChestplate() {
        int hotbar = InventoryUtils.find(AutoMaceModule::isChestplate, InventoryUtils.HOTBAR_START, InventoryUtils.HOTBAR_END);
        return hotbar != -1 ? hotbar : InventoryUtils.find(AutoMaceModule::isChestplate, InventoryUtils.INVENTORY_START, InventoryUtils.INVENTORY_END);
    }

    private int findMace() {
        return alternativeSwap()
                ? InventoryUtils.find(Items.MACE, InventoryUtils.HOTBAR_START, InventoryUtils.INVENTORY_END)
                : InventoryUtils.findHotbar(Items.MACE);
    }

    private boolean alternativeSwap() {
        return switchMode.getValue().equalsIgnoreCase("Silent") && alternative.getValue();
    }

    private String maceSwitchType() {
        if (switchMode.getValue().equalsIgnoreCase("Silent")) return alternative.getValue() ? "AltSwap" : "Silent";
        return "Normal";
    }

    private static final int SWAP_BACK_TICKS = 4;
    private int swapBackSlot = -1, swapBackHoldSlot = -1, swapBackTicks;

    private void scheduleSwapBack(int maceSlot, int previousSlot) {
        if (!swapBack.getValue() || previousSlot == -1 || maceSlot == previousSlot) return;
        swapBackSlot = previousSlot;
        swapBackHoldSlot = maceSlot;
        swapBackTicks = SWAP_BACK_TICKS;
    }

    private void tickSwapBack() {
        if (swapBackSlot == -1) return;
        if (mc.player.getInventory().getSelectedSlot() != swapBackHoldSlot) {
            swapBackSlot = -1;
            return;
        }
        if (--swapBackTicks > 0) return;
        InventoryUtils.switchBack("Normal", swapBackHoldSlot, swapBackSlot);
        swapBackSlot = -1;
    }

    private static final long BURST_MS = 900L;
    private long burstAt;
    private Vec3d burstPos;

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (getNull()) return;
        MatrixStack matrices = event.getMatrices();
        long now = System.currentTimeMillis();

        if (renderWaves.getValue() && target != null && target.isAlive() && stage != Stage.IDLE) {
            Vec3d feet = EntityUtils.getRenderPos(target, event.getTickDelta()).add(0.0, 0.03, 0.0);
            double width = target.getWidth();
            float breath = 0.5f + 0.5f * MathHelper.sin(now / 260.0f);
            Color core = renderColor.getColor();
            Renderer3D.renderFilledRing(matrices, feet, 0.0, width * 0.9 + 0.1 * breath, 48, shade(core, 0.45f + 0.4f * breath), shade(core, 0.0f));
            long period = Math.max(200L, (long) (waveSpeed.getValue().doubleValue() * 1000.0));
            int count = waveCount.getValue().intValue();
            double size = waveSize.getValue().doubleValue() + width * 0.5;
            for (int i = 0; i < count; i++) {
                float t = ((now + (long) i * period / count) % period) / (float) period;
                wave(matrices, feet, t, size, 1.0f);
            }
        }

        if (burstPos != null) {
            long age = now - burstAt;
            if (age > BURST_MS || !hitBurst.getValue()) {
                burstPos = null;
            } else {
                for (int i = 0; i < 2; i++) {
                    float t = (age - i * 140L) / (float) (BURST_MS - 140L);
                    if (t > 0.0f && t < 1.0f) wave(matrices, burstPos.add(0.0, 0.05, 0.0), t, waveSize.getValue().doubleValue() * 2.2, 1.6f);
                }
            }
        }
    }

    private void wave(MatrixStack matrices, Vec3d center, float t, double size, float strength) {
        float e = Easing.ease(t, Easing.Method.EASE_OUT_CUBIC);
        float life = 1.0f - t;
        float fade = life * life * strength;
        double radius = 0.35 + e * size;
        double front = 0.06 + 0.12 * life;
        double wake = Math.min(radius - front, 0.2 + 0.7 * e);
        Color c = lerp(renderColor.getColor(), fadeColor.getColor(), e);
        if (wake > 0.0) Renderer3D.renderFilledRing(matrices, center, radius - front - wake, radius - front, 64, shade(c, 0.0f), shade(c, 0.6f * fade));
        Renderer3D.renderFilledRing(matrices, center, radius - front, radius, 64, shade(c, 2.4f * fade), shade(c, 0.3f * fade));
    }

    private static Color lerp(Color a, Color b, float k) {
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * k),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * k),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * k),
                (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * k));
    }

    private static Color shade(Color c, float k) {
        return ColorUtils.getColor(c, MathHelper.clamp((int) (c.getAlpha() * k), 0, 255));
    }

    private Vec3d predictedEye() {
        return mc.player.getEyePos().add(mc.player.getVelocity());
    }

    private Vec3d aimPoint(LivingEntity entity) {
        Box box = entity.getBoundingBox();
        Vec3d head = new Vec3d((box.minX + box.maxX) / 2.0, box.maxY - 0.1, (box.minZ + box.maxZ) / 2.0);
        return head.add(targetMotion.multiply(ticksToReach(mc.player.getEyePos().distanceTo(head))));
    }

    private double ticksToReach(double distance) {
        double speed = Math.max(0.6, mc.player.getVelocity().length());
        return Math.min(12.0, distance / speed);
    }

    private Vec3d ledTargetPos(double ticks) {
        return target.getEntityPos().add(targetMotion.multiply(ticks));
    }

    private void trackTargetMotion() {
        Vec3d step = new Vec3d(target.getX() - target.lastX, 0.0, target.getZ() - target.lastZ);
        if (step.lengthSquared() > 4.0) step = Vec3d.ZERO;
        targetMotion = targetMotion.multiply(0.6).add(step.multiply(0.4));
    }

    private static float[] rotationsFrom(Vec3d eye, Vec3d point) {
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
                (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0),
                (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(dy, flat)), -90.0, 90.0)
        };
    }

    private float yawTo(Entity entity) {
        return yawTo(entity.getEntityPos());
    }

    private float yawTo(Vec3d point) {
        return rotationsFrom(mc.player.getEyePos(), point)[0];
    }

    private static boolean lookingAt(Entity entity, Vec3d eye, float[] look, double reach) {
        Box box = entity.getBoundingBox();
        if (box.contains(eye)) return true;
        float yaw = look[0] * MathHelper.RADIANS_PER_DEGREE, pitch = look[1] * MathHelper.RADIANS_PER_DEGREE;
        Vec3d direction = new Vec3d(-MathHelper.sin(yaw) * MathHelper.cos(pitch), -MathHelper.sin(pitch), MathHelper.cos(yaw) * MathHelper.cos(pitch));
        return box.raycast(eye, eye.add(direction.multiply(reach))).isPresent();
    }

    private boolean groundAhead() {
        return groundWithin(Math.max(2.5, -mc.player.getVelocity().y * 3.0));
    }

    private boolean groundWithin(double blocks) {
        return !mc.world.isSpaceEmpty(mc.player, mc.player.getBoundingBox().stretch(0.0, -blocks, 0.0));
    }

    private double horizontalDistance(Entity entity) {
        return horizontalDistance(entity.getEntityPos());
    }

    private double horizontalDistance(Vec3d point) {
        double dx = point.x - mc.player.getX(), dz = point.z - mc.player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private LivingEntity pickTarget() {
        double limit = range.getValue().doubleValue();
        long now = System.currentTimeMillis();
        skipped.values().removeIf(until -> until <= now);
        if (target != null && isValidTarget(target) && !skipped.containsKey(target.getId()) && horizontalDistance(target) <= limit * 1.5) return target;

        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !isValidTarget(living)) continue;
            if (skipped.containsKey(living.getId())) continue;
            if (horizontalDistance(living) > limit) continue;
            if (!hasApproachPath(living)) continue;
            double distance = mc.player.squaredDistanceTo(living);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    private void skip(LivingEntity entity) {
        skipped.put(entity.getId(), System.currentTimeMillis() + SKIP_MS);
        if ((debug || debugLog.getValue())) Anatorius.LOGGER.info("[AutoMace] skip {}: no way to it", entity.getName().getString());
        diveStart = null;
        plannedFor = null;
        noPlanTicks = blockedTicks = 0;
    }

    private boolean hasApproachPath(LivingEntity entity) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d feet = entity.getEntityPos();
        Box around = entity.getBoundingBox().expand(0.5);
        int[] solid = {0};
        Boolean blocked = BlockView.raycast(eye, feet, null, (context, pos) -> {
            if (around.intersects(new Box(pos))) return null;
            if (mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) return null;
            if (++solid[0] > 1) return true;
            return airAround(pos) ? null : true;
        }, context -> false);
        return !Boolean.TRUE.equals(blocked);
    }

    private boolean airAround(BlockPos pos) {
        int air = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos neighbor = pos.add(x, 0, z);
                if (mc.world.getBlockState(neighbor).getCollisionShape(mc.world, neighbor).isEmpty()) air++;
            }
        }
        return air > 2;
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static boolean naked(PlayerEntity player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!player.getEquippedStack(slot).isEmpty()) return false;
        }
        return true;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player || !entity.isAlive() || entity.getHealth() <= 0.0f || entity.isRemoved()) return false;
        if (entity instanceof PlayerEntity player) {
            if (!targetPlayers.getValue() || player.isSpectator() || player.isCreative() || EntityUtils.isBot(player)) return false;
            if (!targetNakeds.getValue() && naked(player)) return false;
            return targetFriends.getValue() || !Anatorius.FRIEND_MANAGER.contains(player.getName().getString());
        }
        SpawnGroup group = entity.getType().getSpawnGroup();
        if (group == SpawnGroup.MONSTER) return targetHostiles.getValue();
        if (group == SpawnGroup.CREATURE) return targetAnimals.getValue();
        return false;
    }
}
