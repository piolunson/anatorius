package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.events.impl.*;
import dev.project.anatorius.settings.impl.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.mixins.accessors.PlayerInteractEntityC2SPacketAccessor;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.core.AntiCheatModule;
import dev.project.anatorius.modules.impl.movement.FakeFlyModule;
import dev.project.anatorius.modules.impl.player.SpeedMineModule;
import dev.project.anatorius.utils.rotations.RotationUtils;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.minecraft.DamageUtils;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.PositionUtils;
import dev.project.anatorius.utils.minecraft.NetworkUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.minecraft.ItemUtils;
import dev.project.anatorius.utils.system.Counter;
import dev.project.anatorius.utils.system.Timer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;

import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@RegisterModule(name = "AutoCrystal", description = "Automatically places and attacks crystals to annihilate your opponents.", category = Module.Category.COMBAT)
public class AutoCrystalModule extends Module {
    public CategorySetting attackCategory      = new CategorySetting("Attack", "The category for settings related to attacking crystals.");
    public BooleanSetting  attack              = new BooleanSetting("Attack", "Enabled", "Automatically attacks crystals that are deemed safe.", new CategorySetting.Visibility(attackCategory), true);
    public NumberSetting   attackSpeed         = new NumberSetting("AttackSpeed", "Speed", "The speed at which crystals will be attacked.", new CategorySetting.Visibility(attackCategory), 20.0f, 0.1f, 20.0f);
    public NumberSetting   attackRange         = new NumberSetting("AttackRange", "Range", "The maximum distance at which crystals will be attacked.", new CategorySetting.Visibility(attackCategory), 4.5, 0.0, 6.0);
    public NumberSetting   attackWallsRange    = new NumberSetting("AttackWallsRange", "WallsRange", "The maximum distance at which crystals will be attacked through walls.", new CategorySetting.Visibility(attackCategory), 4.5, 0.0, 6.0);
    public ModeSetting     antiWeakness        = new ModeSetting("AntiWeakness", "Allows you to attack crystals when weaknessed.", new CategorySetting.Visibility(attackCategory), "None", new String[]{"None", "Normal", "Silent"});
    public BooleanSetting  instant             = new BooleanSetting("Instant", "Instantly attacks crystals once they spawn.", new CategorySetting.Visibility(attackCategory), true);
    public BooleanSetting  inhibit             = new BooleanSetting("Inhibit", "Prevents excessive attacks on crystals by blacklisting crystals when attacking them.", new CategorySetting.Visibility(attackCategory), true);

    public CategorySetting placeCategory       = new CategorySetting("Place", "The category for settings related to placing crystals.");
    public BooleanSetting  place               = new BooleanSetting("Place", "Enabled", "Automatically places crystals on positions that are deemed safe and lethal enough.", new CategorySetting.Visibility(placeCategory), true);
    public NumberSetting   placeSpeed          = new NumberSetting("PlaceSpeed", "Speed", "The speed at which crystals will be placed.", new CategorySetting.Visibility(placeCategory), 20.0f, 0.1f, 20.0f);
    public NumberSetting   placeRange          = new NumberSetting("PlaceRange", "Range", "The maximum distance at which positions will be placed on.", new CategorySetting.Visibility(placeCategory), 4.5, 0.0, 6.0);
    public NumberSetting   placeWallsRange     = new NumberSetting("PlaceWallsRange", "WallsRange", "The maximum distance at which positions will be placed on through walls.", new CategorySetting.Visibility(placeCategory), 4.5, 0.0, 6.0);
    public ModeSetting     placements          = new ModeSetting("Placements", "The version of the game that will be used for crystal placement calculations.", new CategorySetting.Visibility(placeCategory), "Native", new String[]{"Native", "Protocol"});
    public BooleanSetting  blockDestruction    = new BooleanSetting("BlockDestruction", "Places crystals on top of mined blocks in order to damage opponents.", new CategorySetting.Visibility(placeCategory), true);
    public BooleanSetting  mineIgnore          = new BooleanSetting("MineIgnore", "Counts the blocks SpeedMine is breaking (both of them with Double) as already gone a few ticks before they break: the spot they free is picked ahead of time and the crystal goes in right after the block breaks.", new CategorySetting.Visibility(placeCategory), true);
    public NumberSetting   mineThreshold       = new NumberSetting("MineThreshold", "Threshold", "How early MineIgnore counts a block as gone (Zio): 1 = about 2.5 ticks before it breaks, each step half a tick more.", new BooleanSetting.Visibility(mineIgnore, true), 1, 1, 5);
    public ModeSetting     autoSwitch          = new ModeSetting("Switch", "Automatically switches to a crystal if you aren't currently holding one.", new CategorySetting.Visibility(placeCategory), "None", new String[]{"None", "Normal", "Silent", "AltSwap"});
    public BooleanSetting  swapBack            = new BooleanSetting("SwapBack", "Normal switch: goes back to the item you had once AutoCrystal is done (nothing placed or attacked for half a second), not after every crystal. Off: the crystal stays in your hand.", new ModeSetting.Visibility(autoSwitch, "Normal"), false);

    public CategorySetting basePlaceCategory   = new CategorySetting("BasePlace", "The category for settings related to placing obsidian bases for future crystal placements.");
    public BooleanSetting  basePlace           = new BooleanSetting("BasePlace", "Enabled", "Automatically places obsidian bases for future crystal placements.", new CategorySetting.Visibility(basePlaceCategory), true);
    public NumberSetting   basePlaceDelay      = new NumberSetting("BasePlaceDelay", "Delay", "Milliseconds before another obsidian base is placed (Zio): none while the last base still makes a good crystal spot, right away when it is gone or out of reach.", new CategorySetting.Visibility(basePlaceCategory), 500, 0, 1000);
    public BooleanSetting  basePlaceAir        = new BooleanSetting("BasePlaceAir", "Air", "Allows bases with no block around to click at all: those are placed against the air (the server accepts a click on the empty spot itself). Bases with only air or water under them are always allowed when a block beside them can be clicked.",new CategorySetting.Visibility(basePlaceCategory), false);

    public CategorySetting miscellaneousCategory = new CategorySetting("Misc", "The category for all miscellaneous settings.");
    public ModeSetting     sequential           = new ModeSetting("Sequential", "Places the next crystal the moment yours spawns (it gets broken by Instant). Strong: on every spawn. Strict: once a tick. None: only on the tick.", new CategorySetting.Visibility(miscellaneousCategory), "Strong", new String[]{"None", "Strict", "Strong"});
    public ModeSetting     swing               = new ModeSetting("Swing", "The hand that will be used for swinging.", new CategorySetting.Visibility(miscellaneousCategory), "Default", new String[]{"Default", "None", "Packet", "Mainhand", "Offhand", "Both"});
    public BooleanSetting  yawStep             = new BooleanSetting("YawStep", "Performs your rotations over multiple ticks.", new CategorySetting.Visibility(miscellaneousCategory), false);
    public NumberSetting   yawStepThreshold    = new NumberSetting("YawStepThreshold", "Threshold", "The threshold in order for yaw to be modified.", new BooleanSetting.Visibility(yawStep, true), 75, 1, 180);
    public BooleanSetting  raytrace            = new BooleanSetting("Raytrace", "Avoids attacking or placing any crystals through walls.", new CategorySetting.Visibility(miscellaneousCategory), false);
    public CategorySetting extrapolationCategory = new CategorySetting("Extrapolation", "The category for extrapolation settings.");
    public NumberSetting   extrapolation       = new NumberSetting("Extrapolation", "Extrapolates the target's position to calculate positions ahead of time.", new CategorySetting.Visibility(extrapolationCategory), 0, 0, 20);
    public BooleanSetting  drawExtrapolation   = new BooleanSetting("Draw", "Renders the extrapolated position.", new CategorySetting.Visibility(extrapolationCategory), true);
    public NumberSetting   enemyRange          = new NumberSetting("EnemyRange", "The maximum distance at which enemies can be at.", new CategorySetting.Visibility(miscellaneousCategory), 10.0, 0.0, 24.0);
    public BooleanSetting  chestBreak          = new BooleanSetting("ChestBreak", "Prevents other players from getting obsidian from ender chests by destroying the dropped items.", new CategorySetting.Visibility(miscellaneousCategory), false);
    public BooleanSetting  asynchronous        = new BooleanSetting("Asynchronous", "Calculates on a separate thread, renewed every frame (Zio): each tick works with a result about a frame old instead of a tick old. Off: once a tick on the game thread.", new CategorySetting.Visibility(miscellaneousCategory), true);

    public CategorySetting pauseCategory       = new CategorySetting("Pause", "When the module keeps working and when it pauses.");
    public ModeSetting     whileEating         = new ModeSetting("WhileEating", "What keeps working while you eat or use items: None pauses both, Attack only attacks, Place only places.", new CategorySetting.Visibility(pauseCategory), "Both", new String[]{"None", "Attack", "Place", "Both"});
    public ModeSetting     whileMining         = new ModeSetting("WhileMining", "What keeps working while you mine (SpeedMine or normal mining): Attack = no crystal placing while mining.", new CategorySetting.Visibility(pauseCategory), "Both", new String[]{"None", "Attack", "Place", "Both"});

    public CategorySetting rotateCategory      = new CategorySetting("Rotate", "The category for settings related to rotating when placing/attacking.");
    public BooleanSetting  rotatePlace         = new BooleanSetting("RotatePlace", "Place", "Rotates toward the placement position.", new CategorySetting.Visibility(rotateCategory), true);
    public BooleanSetting  rotateAttack        = new BooleanSetting("RotateAttack", "Attack", "Rotates toward the attacked crystal.", new CategorySetting.Visibility(rotateCategory), true);
    public ModeSetting     rotate              = new ModeSetting("RotateMode", "Automatically rotates to the crystal whenever attacking or placing.", new CategorySetting.Visibility(rotateCategory), "Normal", new String[]{"None", "Normal", "Packet"});

    public CategorySetting facePlaceCategory   = new CategorySetting("Faceplace", "The category for settings relating to faceplacing.");
    public ModeSetting     facePlaceMode       = new ModeSetting("FaceplaceMode", "Mode", "The checks that will be done in order to faceplace.", new CategorySetting.Visibility(facePlaceCategory), "Dynamic", new String[]{"None", "Dynamic", "Always"});
    public ModeSetting     facePlaceSpeed      = new ModeSetting("FaceplaceSpeed", "Speed", "The speed that players will be faceplaced at.", new CategorySetting.Visibility(facePlaceCategory), "Normal", new String[]{"Normal", "Custom"});
    public NumberSetting   facePlaceDelay      = new NumberSetting("FaceplaceDelay", "Delay", "The ticks that have to be waited for before faceplacing again.", new ModeSetting.Visibility(facePlaceSpeed, "Custom"), 11, 0, 20);
    public BooleanSetting  healthPlace         = new BooleanSetting("HealthPlace", "Whether or not to faceplace when the target's health is low.", new ModeSetting.Visibility(facePlaceMode, "Dynamic"), true);
    public NumberSetting   health              = new NumberSetting("Health", "The health that the target needs to be at in order for the module to start faceplacing.", new BooleanSetting.Visibility(healthPlace, true), 8.0f, 0.0f, 36.0f);
    public BooleanSetting  armorPlace          = new BooleanSetting("ArmorPlace", "Whether or not to faceplace when the target's armor is low on durability.", new ModeSetting.Visibility(facePlaceMode, "Dynamic"), true);
    public NumberSetting   percentage          = new NumberSetting("Percentage", "The percentage that one of the target's armor pieces need to be at in order to start faceplacing.", new BooleanSetting.Visibility(armorPlace, true), 10, 1, 100);

    public CategorySetting damageCategory      = new CategorySetting("Damage", "The category for settings related to damage calculations.");
    public NumberSetting   minimumDamage       = new NumberSetting("MinimumDamage", "Minimum", "The minimum damage that has to be dealt to enemies.", new CategorySetting.Visibility(damageCategory), 6.0, 0.0, 36.0);
    public NumberSetting   maximumSelfDamage   = new NumberSetting("MaximumSelfDamage", "MaximumSelf", "The maximum damage that can be dealt to you by crystals.", new CategorySetting.Visibility(damageCategory), 10.0, 0.0, 36.0);
    public NumberSetting   lethalMultiplier    = new NumberSetting("LethalMultiplier", "The amount of crystals that the target has to be killed by in order to ignore minimum damage.", new CategorySetting.Visibility(damageCategory), 1.5f, 0.0f, 4.0f);
    public BooleanSetting  antiSuicide         = new BooleanSetting("AntiSuicide", "Prevents crystals from accidentally killing you when you're low on health.", new CategorySetting.Visibility(damageCategory), true);
    public BooleanSetting  ignoreTerrain       = new BooleanSetting("IgnoreTerrain", "Ignores terrain that can be destroyed when calculating damage.", new CategorySetting.Visibility(damageCategory), true);

    public CategorySetting targetsCategory     = new CategorySetting("Targets", "Who the crystals go after.");
    public BooleanSetting  ignoreNaked         = new BooleanSetting("IgnoreNaked", "Doesn't go after players without armor.", new CategorySetting.Visibility(targetsCategory), false);
    public BooleanSetting  forceSuicide        = new BooleanSetting("Suicide", "Targets yourself: crystals go next to you and nothing holds back your own damage. Turns off when you die or join a server.", new CategorySetting.Visibility(targetsCategory), false);
    public BooleanSetting  suicideNoArmor      = new BooleanSetting("SuicideNoArmor", "NoArmor", "Throws your armor away (a piece a tick, not the Curse of Binding ones) so you die faster.", new BooleanSetting.Visibility(forceSuicide, true), false);
    public BooleanSetting  suicideOffhand      = new BooleanSetting("SuicideOffhand", "OffhandOverride", "AutoTotem takes the totem out of your offhand meanwhile.", new BooleanSetting.Visibility(forceSuicide, true), true);

    public CategorySetting renderCategory      = new CategorySetting("Render", "Contains all of the settings relating to position rendering.");
    public ModeSetting     animationMode       = new ModeSetting("Animation", "The animation that will be applied to the rendering.", new CategorySetting.Visibility(renderCategory), "Slide", new String[]{"Static", "Slide"});
    public ModeSetting     mode                = new ModeSetting("Mode", "The mode for the auto crystal render.", new CategorySetting.Visibility(renderCategory), "Shrink", new String[]{"Fade", "Shrink"});
    public NumberSetting   duration            = new NumberSetting("Duration", "The duration for the place render.", new CategorySetting.Visibility(renderCategory), 260, 0, 1000);
    public NumberSetting   slideSmoothness     = new NumberSetting("Smoothness", "The smoothness for the slide while place position is changing.", new CategorySetting.Visibility(renderCategory), 1, 0, 20);
    public ModeSetting     renderMode          = new ModeSetting("RenderMode", "The rendering that will be applied to the target position.", new CategorySetting.Visibility(renderCategory), "Both", new String[]{"None", "Fill", "Outline", "Both"});
    public ColorSetting    fillColorUp         = new ColorSetting("FillColorUp", "The color that will be used for the fill gradiant upper part rendering.", new ModeSetting.Visibility(renderMode, "Fill", "Both"), ColorUtils.getDefaultFillColor());
    public ColorSetting    fillColorDown       = new ColorSetting("FillColorDown", "The color that will be used for the fill gradiant lower part rendering.", new ModeSetting.Visibility(renderMode, "Fill", "Both"), ColorUtils.getDefaultFillColor());
    public ColorSetting    outlineColorUp      = new ColorSetting("OutlineColorUp", "The color that will be used for the outline gradiant upper part rendering.", new ModeSetting.Visibility(renderMode, "Outline", "Both"), ColorUtils.getDefaultOutlineColor());
    public ColorSetting    outlineColorDown    = new ColorSetting("OutlineColorDown", "The color that will be used for the outline gradiant lower part rendering.", new ModeSetting.Visibility(renderMode, "Outline", "Both"), ColorUtils.getDefaultOutlineColor());
    public BooleanSetting  renderDamage        = new BooleanSetting("RenderDamage", "Damage", "Renders the damage that the position will do to the opponent.", new CategorySetting.Visibility(renderCategory), false);
    public BooleanSetting  icon                = new BooleanSetting("Icon", "Renders a customizable crystal icon on the rendered position.", new CategorySetting.Visibility(renderCategory), false);
    public NumberSetting   iconScale           = new NumberSetting("IconScale", "The scaling that will be applied to the crystal icon rendering.", new BooleanSetting.Visibility(icon, true), 3, 1, 5);
    public NumberSetting   iconRadius          = new NumberSetting("IconRadius", "The difference between the outer circle and the inner circle.", new BooleanSetting.Visibility(icon, true), 2.0f, 0.0f, 5.0f);
    public ColorSetting    iconColor           = new ColorSetting("IconColor", "The color that will be used for the crystal icon rendering.", new BooleanSetting.Visibility(icon, true), ColorUtils.getDefaultColor());

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AutoCrystal-Calculation");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean calculationInProgress = new AtomicBoolean(false);

    private final AtomicReference<EndCrystalEntity> attackTargetRef          = new AtomicReference<>(null);
    private final AtomicReference<PlaceTarget>      placeTargetRef           = new AtomicReference<>(null);
    private final AtomicReference<PlaceTarget>      mineTargetRef            = new AtomicReference<>(null);
    private final AtomicReference<BasePlaceTarget>  basePlaceTargetRef       = new AtomicReference<>(null);
    private volatile BlockPos lastObsidianBase;

    private static final Field ENTITY_ID_FIELD;
    static {
        Field field = null;
        try {
            field = PlayerInteractEntityC2SPacket.class.getDeclaredField(FabricLoader.getInstance().isDevelopmentEnvironment() ? "entityId" : "field_12870");
            field.setAccessible(true);
        } catch (Exception ignored) { }
        ENTITY_ID_FIELD = field;
    }

    private static final ThreadLocal<DecimalFormat> DECIMAL_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("0.00"));

    private Runnable attackRunnable = null;
    private Runnable placeRunnable  = null;

    private volatile PendingPlace pending = null;
    public static volatile boolean debug = false;

    private static void debug(String line) {
        if (debug) Anatorius.LOGGER.info("[AutoCrystal] " + line);
    }

    private final Map<Integer, Long>  attackedCrystals = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> placedCrystals   = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> countedCrystals  = new ConcurrentHashMap<>();

    private final Timer attackTimer    = new Timer();
    private final Timer placeTimer     = new Timer();
    private final Timer basePlaceTimer = new Timer();
    private final Timer facePlaceTimer = new Timer();

    private int sequenceTurn = 0;

    private boolean followupThisTick;
    private volatile long sequentialPlaceAt;

    @Getter private volatile PlayerEntity target = null;

    private volatile String calculationTime = "0.00ms";
    private volatile int calculationCount = 0;
    @Getter private volatile String calculationDamage = "0.00";

    private final Counter crystalCounter = new Counter();
    private int crystalsPerSecond = 0;

    private int highestID = -100000;

    private static final int MAX_BURST = 24;
    public static volatile boolean packetLimitOverride = false;

    public int predictionBurst() {
        return burstCooldown > 0 ? 0 : burst;
    }
    private volatile int nextId = Integer.MIN_VALUE;
    private BlockPos burstSpot;
    private final Set<Integer> burstIds = ConcurrentHashMap.newKeySet();
    private BlockPos lastBurstSpot;
    private int lastBurstAge = Integer.MIN_VALUE / 2;

    private boolean burstOwns(BlockPos base) {
        return base != null && base.equals(lastBurstSpot) && mc.player.age - lastBurstAge <= 2;
    }
    private int burst = 1;
    private int burstCooldown;
    private final java.util.concurrent.ConcurrentLinkedDeque<Long> awaiting = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private final java.util.concurrent.atomic.AtomicInteger explosions = new java.util.concurrent.atomic.AtomicInteger();
    private int confirmed;
    private final int[] sentPerTick = new int[20];
    private final int[] placesPerTick = new int[2];
    private int sentIndex;

    private static final int SWAP_BACK_TICKS = 10;
    private int swapBackSlot = -1, swapBackFrom = -1;
    private int ticks, lastActionTick;

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        pending = null;
        ticks++;
        followupThisTick = false;
        if (forceSuicide.getValue() && suicideNoArmor.getValue()) throwArmor();
        tickSwapBack();

        long now = System.currentTimeMillis();
        long pingWindow = Anatorius.SERVER_MANAGER.getPing() * 2L + 200L;
        long placeWindow = pingWindow + (long) ((20 - placeSpeed.getValue().floatValue()) * 50L);

        attackedCrystals.entrySet().removeIf(e -> now - e.getValue() > pingWindow);
        placedCrystals.entrySet().removeIf(e -> now - e.getValue() > placeWindow);
        countedCrystals.entrySet().removeIf(e -> now - e.getValue() > pingWindow);

        crystalsPerSecond = crystalCounter.getCount();
        tickPrediction();
        if (netAttacked.size() > 256) netAttacked.clear();

        if (asynchronous.getValue()) {
            scheduleCalculation();
        } else {
            calculationInProgress.set(true);
            calculate();
        }

        run();
    }

    @SubscribeEvent
    public void onGameLoop(GameLoopEvent event) {
        if (mc.player == null || mc.world == null || !asynchronous.getValue()) return;
        scheduleCalculation();
    }

    private void scheduleCalculation() {
        if (calculationInProgress.compareAndSet(false, true)) executor.submit(this::calculate);
    }

    @SubscribeEvent
    public void onUpdateMovementPost(UpdateMovementEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        Runnable attackTask = attackRunnable;
        attackRunnable = null;
        if (attackTask != null) attackTask.run();

        Runnable placeTask = placeRunnable;
        placeRunnable = null;
        if (placeTask != null) placeTask.run();
    }

    @SubscribeEvent
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!attack.getValue()) return;
        if (!(event.getEntity() instanceof EndCrystalEntity crystal)) return;
        if (burstIds.contains(crystal.getId())) return;
        if (!instant.getValue()) return;
        if (!netAttacked.remove(crystal.getId())) {
            if (shouldPause("Attack")) return;
            if (!attackTimer.hasTimeElapsed(getAttackDelay())) return;
            if (!isValidAttackCrystal(crystal, true, mc.player.getEyePos())) return;
            if (!isSelfSafe(crystal)) return;

            applyRotation(attackAim(crystal), false, getAttackRotationPriority());
            attack(crystal);
        }

        if (debug) debugPackets = 3;
        debug("spawn crystal #" + crystal.getId() + " at " + crystal.getBlockPos().down().toShortString());
        boolean followUp = sequential.getValue().equalsIgnoreCase("Strong")
                || (sequential.getValue().equalsIgnoreCase("Strict") && !followupThisTick);
        if (followUp) {
            followupThisTick = true;
            sequentialPlaceAt = System.currentTimeMillis();
            if (crystal.getId() >= highestID) nextId = crystal.getId() + 1;
            burstSpot = crystal.getBlockPos().down();
            try {
                placeCrystals(true);
            } finally {
                burstSpot = null;
            }
        }
    }

    @SubscribeEvent
    public void onDestroyBlock(DestroyBlockEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (!blockDestruction.getValue() || !place.getValue()) return;
        if (pausedForEating("Place")) return;

        BlockPos mined = event.getPosition();
        if (mined == null) return;
        if (!canUseCrystal()) return;

        PlaceTarget snapshot = mineTargetRef.get();
        PlaceTarget mineTarget = snapshot != null && mined.equals(snapshot.getException()) ? snapshot : null;
        if (mineTarget == null || mineTarget.getPosition() == null) mineTarget = calculatePlacements(mined);

        if (mineTarget == null || mineTarget.getPosition() == null) {
            debug("destroy " + mined.toShortString() + ": no spot");
            Anatorius.RENDER_MANAGER.setRenderPosition(null);
            return;
        }

        BlockPos position = mineTarget.getPosition();
        Anatorius.RENDER_MANAGER.setRenderPosition(position);

        if (!isValidPlacePosition(position, mc.player.getEyePos(), mined)) { debug("destroy " + mined.toShortString() + ": spot " + position.toShortString() + " not valid"); return; }

        pending = new PendingPlace(mined, position);
        debug("destroy " + mined.toShortString() + ": armed " + position.toShortString());
    }

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent.Post event) {
        PendingPlace current = pending;
        if (current == null) return;
        if (mc.player == null || mc.world == null) return;
        if (!(event.getPacket() instanceof PlayerActionC2SPacket packet)) return;
        if (packet.getAction() != PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) return;
        if (!current.mined().equals(packet.getPos())) return;

        pending = null;
        debug("STOP " + packet.getPos().toShortString() + ": placing " + current.position().toShortString());
        placeAfterBreak(current);
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getPacket() instanceof ExplosionS2CPacket explosion) {
            Vec3d c = explosion.center();
            for (BlockPos base : placedCrystals.keySet()) {
                if (c.squaredDistanceTo(base.getX() + 0.5, base.getY() + 1.0, base.getZ() + 0.5) < 0.5) {
                    explosions.incrementAndGet();
                    break;
                }
            }
            return;
        }
        if (!(event.getPacket() instanceof EntitySpawnS2CPacket packet)) return;

        if (packet.getEntityId() > highestID) highestID = packet.getEntityId();
        if (packet.getEntityId() + 1 > nextId) nextId = packet.getEntityId() + 1;

        BlockPos position = BlockPos.ofFloored(packet.getX(), packet.getY(), packet.getZ()).down();
        BlockPos lastBase = lastPlacedBase;
        if (packet.getEntityType() == EntityType.END_CRYSTAL && lastBase != null
                && Vec3d.ofCenter(lastBase).squaredDistanceTo(packet.getX(), packet.getY(), packet.getZ()) <= 9.0) {
            crystalCounter.increment();
        }
        if (packet.getEntityType() == EntityType.END_CRYSTAL) instantFromPacket(packet, position);
        if (packet.getEntityType() == EntityType.END_CRYSTAL && countedCrystals.containsKey(position)) {
            if (facePlaceTimer.hasTimeElapsed(facePlaceDelay.getValue().longValue() * 50L)) facePlaceTimer.reset();
            countedCrystals.remove(position);
        }
    }

    private final Set<Integer> netAttacked = ConcurrentHashMap.newKeySet();

    private void instantFromPacket(EntitySpawnS2CPacket packet, BlockPos base) {
        if (!attack.getValue() || !instant.getValue()) return;
        int id = packet.getEntityId();
        if (burstIds.contains(id)) return;
        if (!placedCrystals.containsKey(base) && !countedCrystals.containsKey(base)) return;
        if (shouldPause("Attack") || !attackTimer.hasTimeElapsed(getAttackDelay())) return;
        if (!antiWeakness.getValue().equalsIgnoreCase("None") && mc.player.hasStatusEffect(StatusEffects.WEAKNESS)) return;
        if (rotationActive(false) && rotate.getValue().equalsIgnoreCase("Normal") && isMovementSyncEnabled()) return;

        Vec3d attackPoint = new Vec3d(packet.getX(), packet.getY() + 1.0, packet.getZ());
        double distanceSq = mc.player.getEyePos().squaredDistanceTo(attackPoint);
        if (distanceSq > MathHelper.square(attackRange.getValue().doubleValue())) return;
        if (!mc.world.getWorldBorder().contains(base.up())) return;
        if (!WorldUtils.canSee(attackPoint) && (raytrace.getValue() || distanceSq > MathHelper.square(attackWallsRange.getValue().doubleValue()))) return;
        if (!forceSuicide.getValue()) {
            float selfDamage = DamageUtils.getCrystalDamage(mc.player, null, base, null, ignoreTerrain.getValue());
            if (selfDamage > maximumSelfDamage.getValue().floatValue()) return;
            if (antiSuicide.getValue() && selfDamage > mc.player.getHealth() + mc.player.getAbsorptionAmount()) return;
        }

        sendRotation(new Vec3d(packet.getX(), packet.getY() + 0.5, packet.getZ()), false, getAttackRotationPriority());
        PlayerInteractEntityC2SPacket attackPacket = PlayerInteractEntityC2SPacket.attack(mc.player, mc.player.isSneaking());
        ((PlayerInteractEntityC2SPacketAccessor) attackPacket).setEntityId(id);
        withStoppedSprint(() -> {
            mc.getNetworkHandler().sendPacket(attackPacket);
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        });

        long now = System.currentTimeMillis();
        netAttacked.add(id);
        attackedCrystals.put(id, now);
        attackTimer.reset();
        lastActionTick = ticks;
        awaiting.add(now);
        debug("packet instant #" + id + " at " + base.toShortString());
    }

    private void throwArmor() {
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
        for (int slot = 5; slot <= 8; slot++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(slot).getStack();
            if (stack.isEmpty() || net.minecraft.enchantment.EnchantmentHelper.hasAnyEnchantmentsWith(stack, net.minecraft.component.EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)) continue;
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, 1, net.minecraft.screen.slot.SlotActionType.THROW, mc.player);
            return;
        }
    }

    @SubscribeEvent
    public void onSuicideDeath(dev.project.anatorius.events.impl.PlayerDeathEvent event) {
        if (event.getPlayer() == mc.player && forceSuicide.getValue()) forceSuicide.setValue(false);
    }

    public static boolean suiciding() {
        AutoCrystalModule module = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AutoCrystalModule.class);
        return module != null && module.isToggled() && module.forceSuicide.getValue();
    }

    public static boolean suicideOffhandOverride() {
        AutoCrystalModule module = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AutoCrystalModule.class);
        return suiciding() && module.suicideOffhand.getValue();
    }

    @SubscribeEvent
    public void onClientConnect(ClientConnectEvent event) {
        forceSuicide.setValue(false);
        highestID = -100000;
        nextId = Integer.MIN_VALUE;
    }

    @SubscribeEvent
    public void onAnyPacketSend(PacketSendEvent event) {
        sentPerTick[sentIndex]++;
        if (debug && debugPackets > 0) {
            Object p = event.getPacket();
            String what = p.getClass().getSimpleName();
            if (p instanceof PlayerInteractBlockC2SPacket b) what += " " + b.getHand() + " " + b.getBlockHitResult().getBlockPos().toShortString() + " seq" + b.getSequence();
            else if (p instanceof PlayerInteractEntityC2SPacket && ENTITY_ID_FIELD != null) {
                try { what += " id" + ENTITY_ID_FIELD.getInt(p); } catch (Exception ignored) { }
            }
            debug("  send " + what + (event.isCancelled() ? " CANCELLED" : ""));
        }
    }

    private int debugPackets;

    @Override
    public void onDisable() {
        if (swapBackSlot != -1 && mc.player != null && mc.getNetworkHandler() != null && mc.player.getInventory().getSelectedSlot() == swapBackFrom) swapBackNow();
        swapBackSlot = -1;
        attackRunnable = null;
        placeRunnable = null;
        pending = null;

        Anatorius.RENDER_MANAGER.setRenderPosition(null);

        attackedCrystals.clear();
        placedCrystals.clear();
        countedCrystals.clear();

        attackTargetRef.set(null);
        placeTargetRef.set(null);
        mineTargetRef.set(null);
        basePlaceTargetRef.set(null);
        lastObsidianBase = null;

        sequenceTurn = 0;
        target = null;

        calculationTime = "0.00ms";
        calculationCount = 0;
        calculationDamage = "0.00";

        crystalCounter.reset();
        highestID = -100000;
        awaiting.clear();
        explosions.set(0);
        confirmed = 0;
        burstIds.clear();
        netAttacked.clear();
    }

    @Override
    public String getMetaData() {
        return calculationTime + ", " + calculationCount + ", " + calculationDamage + ", " + crystalsPerSecond;
    }

    public BlockPos getPlacePosition() {
        PlaceTarget placeTarget = placeTargetRef.get();
        return placeTarget == null ? null : placeTarget.getPosition();
    }

    public int getCrystalsPerSecond() {
        return crystalsPerSecond;
    }

    private void calculate() {
        try {
            long start = System.nanoTime();
            CalculationContext context = createCalculationContext();

            EndCrystalEntity newAttack = calculateCrystals(context);
            PlaceTarget newPlace = calculatePlacements(null, context);
            for (BlockPos ignored : mineIgnoredBlocks()) {
                PlaceTarget early = calculatePlacements(ignored, context);
                if (early != null && early.getPosition() != null
                        && (newPlace == null || newPlace.getPosition() == null || early.getDamage() > newPlace.getDamage())) newPlace = early;
            }
            BasePlaceTarget newBase = newPlace != null && newPlace.getPosition() != null ? null : calculateBasePlacements(context);

            attackTargetRef.set(newAttack);
            placeTargetRef.set(newPlace);
            basePlaceTargetRef.set(newBase);

            DecimalFormat format = DECIMAL_FORMAT.get();
            boolean hasPlace = newPlace != null && newPlace.getPosition() != null;
            boolean hasBase = newBase != null && newBase.getPosition() != null;

            calculationTime = format.format((System.nanoTime() - start) / 1_000_000.0) + "ms";
            calculationCount = hasPlace ? newPlace.getCalculations() : hasBase ? newBase.getCalculations() : 0;
            calculationDamage = hasPlace ? format.format(newPlace.getDamage()) : hasBase ? format.format(newBase.getDamage()) : "0.00";
            target = hasPlace ? newPlace.getPlayer() : hasBase ? newBase.getPlayer() : null;

            if (blockDestruction.getValue()) {
                SpeedMineModule speedMine = Anatorius.MODULE_MANAGER.getModule(SpeedMineModule.class);
                SpeedMineModule.Action primary = speedMine == null ? null : speedMine.getPrimary();
                BlockPos minePosition = primary != null && primary.isMining() ? primary.getPosition() : null;
                mineTargetRef.set(minePosition != null ? calculatePlacements(minePosition, context) : null);
            }
        } catch (Throwable throwable) {
            Anatorius.LOGGER.error("AutoCrystal calculation failed; skipping this cycle", throwable);
        } finally {
            calculationInProgress.set(false);
        }
    }

    private boolean attackRotated;

    private static Vec3d attackAim(EndCrystalEntity crystal) {
        Box box = crystal.getBoundingBox();
        return new Vec3d((box.minX + box.maxX) / 2.0, box.minY + box.getLengthY() * 0.25, (box.minZ + box.maxZ) / 2.0);
    }

    private void run() {
        attackRunnable = null;
        placeRunnable = null;
        attackRotated = false;

        attackCrystals();
        placeCrystals(false);
    }

    private long getAttackDelay() {
        return delayFor(attackSpeed.getValue().floatValue());
    }

    private long getPlaceDelay() {
        return delayFor(placeSpeed.getValue().floatValue());
    }

    private long delayFor(float speed) {
        return (long) Math.max(0, 1000.0f - (speed * 50.0f));
    }

    private int getAttackRotationPriority() {
        return Anatorius.ROTATION_MANAGER.getModulePriority(this);
    }

    private int getPlaceRotationPriority() {
        return Anatorius.ROTATION_MANAGER.getModulePriority(this) + 1;
    }

    private boolean isMovementSyncEnabled() {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        return antiCheat != null && antiCheat.movementSync.getValue();
    }

    private boolean isStrictDirectionEnabled() {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        return antiCheat != null && antiCheat.strictDirection.getValue();
    }

    private boolean rotationActive(boolean isPlace) {
        return (isPlace ? rotatePlace.getValue() : rotateAttack.getValue()) && !rotate.getValue().equalsIgnoreCase("None");
    }

    private void queueRotation(Vec3d aim, boolean isPlace, int priority) {
        if (!rotationActive(isPlace) || !rotate.getValue().equalsIgnoreCase("Normal")) return;
        Anatorius.ROTATION_MANAGER.rotate(calculateRotations(aim), this, priority);
    }

    private void sendRotation(Vec3d aim, boolean isPlace, int priority) {
        if (!rotationActive(isPlace)) return;
        boolean packetMode = rotate.getValue().equalsIgnoreCase("Packet");
        boolean normalMode = rotate.getValue().equalsIgnoreCase("Normal");
        if (packetMode || (normalMode && !isMovementSyncEnabled())) {
            Anatorius.ROTATION_MANAGER.packetRotate(calculateRotations(aim), priority);
        }
    }

    private void applyRotation(Vec3d aim, boolean isPlace, int priority) {
        queueRotation(aim, isPlace, priority);
        sendRotation(aim, isPlace, priority);
    }

    private Vec3d getCrystalAttackPoint(EndCrystalEntity crystal) {
        return crystal.getBoundingBox().getCenter();
    }

    private double getCrystalAttackDistanceSq(EndCrystalEntity crystal, Vec3d eyePos) {
        return eyePos.squaredDistanceTo(getCrystalAttackPoint(crystal));
    }

    private Vec3d getPlaceInteractPoint(BlockPos position) {
        Direction direction = WorldUtils.getClosestDirection(position, isStrictDirectionEnabled());
        return WorldUtils.getHitVector(position, direction);
    }

    private static double placeDistanceSq(Vec3d eyePos, BlockPos position, Vec3d interactPoint) {
        double crystal = eyePos.squaredDistanceTo(position.getX() + 0.5, position.getY() + 2.0, position.getZ() + 0.5);
        return Math.max(eyePos.squaredDistanceTo(interactPoint), crystal);
    }

    private Vec3d getBlockInteractPoint(BlockPos position, Direction direction) {
        if (direction == null) return Vec3d.ofCenter(position);
        return position.toCenterPos().add(direction.getOffsetX() / 2.0, direction.getOffsetY() / 2.0, direction.getOffsetZ() / 2.0);
    }

    private void withStoppedSprint(Runnable action) {
        boolean wasSprinting = Anatorius.POSITION_MANAGER.isServerSprinting() || mc.player.isSprinting();
        if (wasSprinting) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
        }

        try {
            action.run();
        } finally {
            if (wasSprinting) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            }
        }
    }

    private boolean isHoldingCrystal() {
        return mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL
                || mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;
    }

    private boolean canUseCrystal() {
        return isHoldingCrystal()
                || (!autoSwitch.getValue().equalsIgnoreCase("None") && InventoryUtils.findHotbar(Items.END_CRYSTAL) != -1);
    }

    private String getMineSwitchMode() {
        if (autoSwitch.getValue().equalsIgnoreCase("None")) return "None";

        SpeedMineModule speedMine = Anatorius.MODULE_MANAGER.getModule(SpeedMineModule.class);
        boolean altSwap = speedMine != null && speedMine.switchReset.getValue()
                && (speedMine.switchMode.getValue().equalsIgnoreCase("Normal") || speedMine.isAlternative());
        return altSwap ? "AltSwap" : autoSwitch.getValue();
    }

    private boolean isPlacedCrystal(EndCrystalEntity crystal) {
        if (crystal == null) return false;
        BlockPos position = crystal.getBlockPos();
        if (position == null) return false;
        BlockPos below = position.down();
        return placedCrystals.containsKey(position) || placedCrystals.containsKey(below)
                || countedCrystals.containsKey(position) || countedCrystals.containsKey(below);
    }

    private boolean isValidAttackCrystal(EndCrystalEntity crystal, boolean placedOnly, Vec3d eyePos) {
        if (crystal == null || !crystal.isAlive()) return false;
        if (inhibit.getValue() && attackedCrystals.containsKey(crystal.getId())) return false;
        if (placedOnly && !isPlacedCrystal(crystal)) return false;

        Vec3d attackPoint = getCrystalAttackPoint(crystal);
        double distanceSq = getCrystalAttackDistanceSq(crystal, eyePos);
        if (distanceSq > MathHelper.square(attackRange.getValue().doubleValue())) return false;
        if (!mc.world.getWorldBorder().contains(crystal.getBlockPos())) return false;

        return WorldUtils.canSee(attackPoint)
                || (!raytrace.getValue() && distanceSq <= MathHelper.square(attackWallsRange.getValue().doubleValue()));
    }

    private boolean isSelfSafe(EndCrystalEntity crystal) {
        if (forceSuicide.getValue()) return true;

        float selfDamage = DamageUtils.getCrystalDamage(mc.player, null, crystal, ignoreTerrain.getValue());
        if (selfDamage > maximumSelfDamage.getValue().floatValue()) return false;
        return !antiSuicide.getValue() || selfDamage <= mc.player.getHealth() + mc.player.getAbsorptionAmount();
    }

    private boolean isValidPlacePosition(BlockPos position, Vec3d eyePos, BlockPos exception) {
        if (position == null) return false;

        Vec3d interactPoint = getPlaceInteractPoint(position);
        double distanceSq = placeDistanceSq(eyePos, position, interactPoint);
        if (distanceSq > MathHelper.square(placeRange.getValue().doubleValue())) return false;
        if (!mc.world.getWorldBorder().contains(position)) return false;

        BlockState base = mc.world.getBlockState(position);
        if (base.getBlock() != Blocks.OBSIDIAN && base.getBlock() != Blocks.BEDROCK) return false;
        if (!hasCrystalClearance(position, exception)) return false;

        return WorldUtils.canSee(interactPoint)
                || (!raytrace.getValue() && distanceSq <= MathHelper.square(placeWallsRange.getValue().doubleValue()));
    }

    private boolean hasCrystalClearance(BlockPos position, BlockPos exception) {
        BlockPos up = position.up();
        if (!up.equals(exception) && !mc.world.getBlockState(up).isAir()) return false;

        if (placements.getValue().equalsIgnoreCase("Protocol")) {
            BlockPos up2 = position.up(2);
            if (!up2.equals(exception) && !mc.world.getBlockState(up2).isAir()) return false;
        }

        for (Entity entity : otherEntities(WorldUtils.crystalBox(up))) {
            if (!entity.isAlive()) continue;
            if (entity instanceof ExperienceOrbEntity) continue;
            if (entity instanceof EndCrystalEntity) continue;
            return false;
        }
        return true;
    }

    private boolean hasObsidianForBasePlace() {
        return mc.player.getMainHandStack().getItem() == Items.OBSIDIAN
                || mc.player.getOffHandStack().getItem() == Items.OBSIDIAN
                || findObsidianSlot() != -1;
    }

    private int findObsidianSlot() {
        if (autoSwitch.getValue().equalsIgnoreCase("None")) return -1;
        int slot = InventoryUtils.findHotbar(Items.OBSIDIAN);
        if (slot == -1 && autoSwitch.getValue().equalsIgnoreCase("AltSwap")) slot = InventoryUtils.find(Items.OBSIDIAN);
        return slot;
    }

    private boolean fakeFlying() {
        FakeFlyModule fakeFly = FakeFlyModule.INSTANCE;
        return fakeFly != null && fakeFly.isToggled() && fakeFly.flying;
    }

    private Direction findBaseSupport(BlockPos position, Vec3d eyePos) {
        boolean strict = isStrictDirectionEnabled();
        boolean sightOnly = raytrace.getValue() || placeWallsRange.getValue().doubleValue() <= 0.0;
        for (Direction direction : BASE_SUPPORT_ORDER) {
            BlockPos neighbor = position.offset(direction);
            if (mc.world.isOutOfHeightLimit(neighbor) || !mc.world.getWorldBorder().contains(neighbor)) continue;
            boolean solid = !mc.world.getBlockState(neighbor).isReplaceable()
                    || (Anatorius.WORLD_MANAGER != null && Anatorius.WORLD_MANAGER.isPlacedThisTick(neighbor));
            if (!solid) continue;
            if (strict && !WorldUtils.getStrictDirections(eyePos, Vec3d.ofCenter(neighbor)).contains(direction.getOpposite())) continue;
            if (sightOnly && !WorldUtils.canSee(getBlockInteractPoint(position, direction))) continue;
            return direction;
        }
        return null;
    }

    private static final Direction[] BASE_SUPPORT_ORDER = {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP};

    private Vec3d baseHitPoint(BlockPos position, Direction direction, Vec3d eyePos) {
        Vec3d center = getBlockInteractPoint(position, direction);
        double wallsRange = placeWallsRange.getValue().doubleValue();
        if (placeRange.getValue().doubleValue() == wallsRange || eyePos.distanceTo(center) < wallsRange) return center;

        Vec3d best = WorldUtils.canSee(center) ? center : null;
        double bestDistance = best == null ? Double.MAX_VALUE : eyePos.squaredDistanceTo(center);
        for (double a : new double[]{0.05, 0.95}) for (double b : new double[]{0.05, 0.95}) {
            Vec3d point = switch (direction.getAxis()) {
                case X -> new Vec3d(center.x, position.getY() + a, position.getZ() + b);
                case Y -> new Vec3d(position.getX() + a, center.y, position.getZ() + b);
                case Z -> new Vec3d(position.getX() + a, position.getY() + b, center.z);
            };
            double distance = eyePos.squaredDistanceTo(point);
            if (distance < bestDistance && WorldUtils.canSee(point)) {
                best = point;
                bestDistance = distance;
            }
        }
        return best != null ? best : center;
    }

    private boolean isValidBasePlacePosition(BlockPos position, Direction direction, Vec3d eyePos) {
        if (position == null) return false;
        if (direction == null && !basePlaceAir.getValue()) return false;
        if (!mc.world.getWorldBorder().contains(position)) return false;
        if (!mc.world.getBlockState(position).isReplaceable()) return false;
        if (Anatorius.WORLD_MANAGER != null && Anatorius.WORLD_MANAGER.isPending(position)) return false;
        if (!WorldUtils.isPlaceable(position)) return false;
        if (!hasCrystalClearance(position, null)) return false;

        Vec3d interactPoint = getBlockInteractPoint(position, direction);
        double distanceSq = placeDistanceSq(eyePos, position, interactPoint);
        if (distanceSq > MathHelper.square(placeRange.getValue().doubleValue())) return false;

        return WorldUtils.canSee(interactPoint)
                || (!raytrace.getValue() && distanceSq <= MathHelper.square(placeWallsRange.getValue().doubleValue()));
    }

    private void attackCrystals() {
        if (!attack.getValue()) return;
        if (shouldPause("Attack")) return;
        if (!attackTimer.hasTimeElapsed(getAttackDelay())) return;
        if (instant.getValue() && System.currentTimeMillis() - sequentialPlaceAt < Anatorius.SERVER_MANAGER.getPing()) return;

        PlaceTarget placeTarget = placeTargetRef.get();
        List<Entity> obstructions = placeTarget == null ? null : placeTarget.getObstructions();
        boolean hasObstructions = obstructions != null && !obstructions.isEmpty();

        double maxRange = Math.max(attackRange.getValue().doubleValue(), attackWallsRange.getValue().doubleValue());
        Vec3d eyePos = mc.player.getEyePos();
        Iterable<Entity> entities = hasObstructions ? obstructions : otherEntities(mc.player.getBoundingBox().expand(maxRange));

        EndCrystalEntity chosen = null;
        for (Entity entity : entities) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (burstIds.contains(crystal.getId()) || burstOwns(crystal.getBlockPos().down())) continue;
            if (!isValidAttackCrystal(crystal, !hasObstructions, eyePos)) continue;
            chosen = crystal;
            break;
        }

        EndCrystalEntity crystal = chosen != null ? chosen : attackTargetRef.get();
        if (crystal == null) return;

        int priority = getAttackRotationPriority();
        queueRotation(attackAim(crystal), false, priority);
        if (rotationActive(false)) attackRotated = true;

        int crystalId = crystal.getId();
        attackRunnable = () -> {
            Entity fresh = mc.world.getEntityById(crystalId);
            if (!(fresh instanceof EndCrystalEntity freshCrystal)) return;
            if (!isValidAttackCrystal(freshCrystal, false, mc.player.getEyePos())) return;
            if (!isSelfSafe(freshCrystal)) return;

            sendRotation(attackAim(freshCrystal), false, priority);
            attack(freshCrystal);
        };
    }

    private void placeCrystals(boolean sequenced) {
        if (!place.getValue()) return;
        if (pausedForEating("Place")) return;

        PlaceTarget snapshot = placeTargetRef.get();
        if (shouldPause("Place") && (snapshot == null || snapshot.getException() == null)) return;
        if (snapshot == null || snapshot.getPosition() == null) {
            if (placeBase(sequenced)) return;
            Anatorius.RENDER_MANAGER.setRenderPosition(null);
            return;
        }

        BlockPos position = snapshot.getPosition();
        Anatorius.RENDER_MANAGER.setRenderPosition(position);

        if (!canUseCrystal()) return;
        if (!sequenced && burstOwns(position)) return;
        BlockPos ignored = snapshot.getException();
        if (ignored != null && !mc.world.getBlockState(ignored).isAir()
                && (position.up().equals(ignored) || position.up(2).equals(ignored))) {
            if (isValidPlacePosition(position, mc.player.getEyePos(), ignored)) {
                pending = new PendingPlace(ignored, position);
                debug("MineIgnore: armed " + position.toShortString() + " for " + ignored.toShortString());
            } else debug("MineIgnore: " + position.toShortString() + " not valid");
            return;
        }
        if (shouldPause("Place")) return;
        if (!isValidPlacePosition(position, mc.player.getEyePos(), null)) return;
        if (!placeTimer.hasTimeElapsed(getPlaceDelay())) return;

        int priority = getPlaceRotationPriority();
        Vec3d aim = Vec3d.ofCenter(position, 1);
        if (sequenced || !attackRotated) queueRotation(aim, true, priority);

        Runnable task = () -> {
            if (!isValidPlacePosition(position, mc.player.getEyePos(), null)) return;
            if (!stillWorthPlacing(snapshot)) return;

            sendRotation(aim, true, priority);
            performPlace(position, autoSwitch.getValue());
        };

        if (sequenced) task.run();
        else placeRunnable = task;
    }

    private boolean stillWorthPlacing(PlaceTarget placeTarget) {
        return stillWorthPlacing(placeTarget.getPlayer(), placeTarget.getPosition(), placeTarget.getException());
    }

    private boolean stillWorthPlacing(PlayerEntity player, BlockPos position, BlockPos exception) {
        if (forceSuicide.getValue()) return true;
        if (player == null || !player.isAlive() || player.isRemoved()) return false;

        float damage = Math.max(
                DamageUtils.getCrystalDamage(player, PositionUtils.extrapolate(player, extrapolation.getValue().intValue()), position, exception, ignoreTerrain.getValue()),
                DamageUtils.getCrystalDamage(player, null, position, exception, ignoreTerrain.getValue()));
        float health = player.getHealth() + player.getAbsorptionAmount();
        return damage >= getMinimumDamage(player, minimumDamage.getValue().floatValue())
                || damage >= health
                || damage * (1.0f + lethalMultiplier.getValue().floatValue()) >= health;
    }

    private void placeAfterBreak(PendingPlace current) {
        BlockPos position = current.position();

        if (!isValidPlacePosition(position, mc.player.getEyePos(), current.mined())) { debug("after break: " + position.toShortString() + " not valid"); return; }

        if (attack.getValue()) {
            Vec3d spot = Vec3d.ofCenter(current.mined());
            EndCrystalEntity clearer = null;
            double best = 36.0;
            for (Entity entity : otherEntities(new Box(current.mined()).expand(6.0))) {
                if (!(entity instanceof EndCrystalEntity crystal)) continue;
                if (!isValidAttackCrystal(crystal, false, mc.player.getEyePos()) || !isSelfSafe(crystal)) continue;
                double d = crystal.squaredDistanceTo(spot);
                if (d < best) {
                    best = d;
                    clearer = crystal;
                }
            }
            if (clearer != null) {
                attack(clearer);
                debug("after break: blew up the crystal at " + clearer.getBlockPos().toShortString() + " to clear the drop");
            }
        }

        applyRotation(Vec3d.ofCenter(position, 1), true, getPlaceRotationPriority());

        boolean placed = performPlace(position, getMineSwitchMode());
        debug("after break: place " + position.toShortString() + " -> " + placed + " serverSlot=" + Anatorius.POSITION_MANAGER.getServerSlot());
        if (placed) mineTargetRef.set(null);
    }

    private boolean performPlace(BlockPos position, String switchMode) {
        boolean holding = isHoldingCrystal();
        int slot = -1;
        int previousSlot = mc.player.getInventory().getSelectedSlot();
        if (!holding) {
            if (switchMode.equalsIgnoreCase("None")) return false;
            slot = InventoryUtils.findHotbar(Items.END_CRYSTAL);
            if (slot == -1) return false;
            InventoryUtils.switchSlot(switchMode, slot, previousSlot);
            if (switchMode.equalsIgnoreCase("Normal") && swapBack.getValue()) {
                if (swapBackSlot == -1 || previousSlot != swapBackFrom) swapBackSlot = previousSlot;
                swapBackFrom = slot;
            }
        }
        boolean silent = !holding && switchMode.equalsIgnoreCase("Silent");

        if (silent) placeSilent(position);
        else place(position);

        if (!holding && !switchMode.equalsIgnoreCase("Normal")) InventoryUtils.switchBack(switchMode, slot, previousSlot);
        return true;
    }

    private void tickSwapBack() {
        if (swapBackSlot == -1) return;
        if (!swapBack.getValue() || !autoSwitch.getValue().equalsIgnoreCase("Normal") || mc.player.getInventory().getSelectedSlot() != swapBackFrom) {
            swapBackSlot = -1;
            return;
        }
        if (ticks - lastActionTick < SWAP_BACK_TICKS) return;
        swapBackNow();
    }

    private void swapBackNow() {
        int slot = swapBackSlot;
        swapBackSlot = -1;
        if (slot >= 0 && slot != mc.player.getInventory().getSelectedSlot()) InventoryUtils.switchSlot("Normal", slot, mc.player.getInventory().getSelectedSlot());
    }

    private BlockPos safeBase;
    private long safeBaseAge;
    private boolean safeBaseResult;

    private static final double[][] EXPLOSION_RAYS = explosionRays();
    private static final float MAX_EXPLOSION_POWER = 6.0f * 1.3f;

    private static double[][] explosionRays() {
        java.util.List<double[]> rays = new java.util.ArrayList<>();
        for (int j = 0; j < 16; j++) for (int k = 0; k < 16; k++) for (int l = 0; l < 16; l++) {
            if (j != 0 && j != 15 && k != 0 && k != 15 && l != 0 && l != 15) continue;
            double d = j / 15.0 * 2.0 - 1.0, e = k / 15.0 * 2.0 - 1.0, f = l / 15.0 * 2.0 - 1.0;
            double g = Math.sqrt(d * d + e * e + f * f);
            rays.add(new double[]{d / g * 0.3, e / g * 0.3, f / g * 0.3});
        }
        return rays.toArray(new double[0][]);
    }

    private boolean explosionBreaksBlocks(BlockPos base) {
        double cx = base.getX() + 0.5, cy = base.getY() + 1.0, cz = base.getZ() + 0.5;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (double[] ray : EXPLOSION_RAYS) {
            double x = cx, y = cy, z = cz;
            for (float h = MAX_EXPLOSION_POWER; h > 0.0f; h -= 0.22500001f) {
                m.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
                if (mc.world.isOutOfHeightLimit(m)) break;
                BlockState state = mc.world.getBlockState(m);
                FluidState fluid = state.getFluidState();
                if (!state.isAir() || !fluid.isEmpty()) {
                    h -= (Math.max(state.getBlock().getBlastResistance(), fluid.getBlastResistance()) + 0.3f) * 0.3f;
                    if (h > 0.0f && !state.isAir() && fluid.isEmpty()) return true;
                }
                x += ray[0];
                y += ray[1];
                z += ray[2];
            }
        }
        return false;
    }

    private int packetRoom() {
        int sum = 0;
        for (int n : sentPerTick) sum += n;
        int limit = mc.isIntegratedServerRunning() || packetLimitOverride ? 4000 : 420;
        return Math.min(MAX_BURST * 2, limit - sum);
    }

    private void tickPrediction() {
        placesPerTick[1] = placesPerTick[0];
        placesPerTick[0] = 0;
        if (burstIds.size() > 1024) burstIds.removeIf(id -> id < nextId - 512);
        if (debugPackets > 0) debugPackets--;
        sentIndex = (sentIndex + 1) % sentPerTick.length;
        sentPerTick[sentIndex] = 0;
        if (burstCooldown > 0 && --burstCooldown == 0) burst = 1;

        int seen = explosions.getAndSet(0);
        while (seen-- > 0 && !awaiting.isEmpty()) {
            awaiting.poll();
            confirmed++;
        }
        long timeout = Anatorius.SERVER_MANAGER.getPing() * 2L + 300L, now = System.currentTimeMillis();
        boolean missed = false;
        while (!awaiting.isEmpty() && now - awaiting.peek() > timeout) {
            awaiting.poll();
            missed = true;
        }
        if (missed || confirmed > 0) debug("tick: confirmed=" + confirmed + " missed=" + missed + " awaiting=" + awaiting.size() + " burst=" + burst);
        if (missed) {
            burst /= 2;
            if (burst == 0) burstCooldown = 60;
            confirmed = 0;
            nextId = Math.max(highestID, mc.player.getId()) + 1;
            awaiting.clear();
            burstIds.clear();
        } else if (confirmed >= Math.max(8, burst * 2)) {
            burst = Math.min(MAX_BURST, burst + Math.max(1, burst / 2));
            confirmed = 0;
        }
    }

    private boolean placeBase(boolean sequenced) {
        if (!basePlace.getValue()) return false;

        BasePlaceTarget snapshot = basePlaceTargetRef.get();
        if (snapshot == null || snapshot.getPosition() == null) return false;

        int slot = findObsidianSlot();
        boolean holding = mc.player.getMainHandStack().getItem() == Items.OBSIDIAN
                || mc.player.getOffHandStack().getItem() == Items.OBSIDIAN;
        if (!holding && slot == -1) return false;
        if (!canUseCrystal()) return false;

        BlockPos position = snapshot.getPosition();
        Direction direction = snapshot.getDirection();
        if (!isValidBasePlacePosition(position, direction, mc.player.getEyePos())) return false;
        if (!canRetryBasePlace()) return false;

        Anatorius.RENDER_MANAGER.setRenderPosition(position);

        int priority = getPlaceRotationPriority();

        Runnable task = () -> {
            Vec3d eyePos = mc.player.getEyePos();
            if (!isValidBasePlacePosition(position, direction, eyePos)) return;
            if (!stillWorthPlacing(snapshot.getPlayer(), position, null)) return;

            boolean switched = false;
            int previousSlot = mc.player.getInventory().getSelectedSlot();
            Hand hand = mc.player.getOffHandStack().getItem() == Items.OBSIDIAN ? Hand.OFF_HAND : Hand.MAIN_HAND;

            if (mc.player.getMainHandStack().getItem() != Items.OBSIDIAN && mc.player.getOffHandStack().getItem() != Items.OBSIDIAN) {
                if (slot == -1) return;
                InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);
                switched = true;
                hand = Hand.MAIN_HAND;
            }

            Vec3d hit = direction == null ? null : baseHitPoint(position, direction, eyePos);
            float[] look = RotationUtils.getRotations(hit != null ? hit : Vec3d.ofCenter(position), false);
            boolean rotate = rotationActive(true);
            boolean flying = rotate && fakeFlying();

            if (flying) Anatorius.ROTATION_MANAGER.packetRotate(look[0], look[1]);

            boolean placed = hit != null
                    ? WorldUtils.placeBlockAt(position, direction, hand, hit, false, false, priority)
                    : WorldUtils.placeBlock(position, direction, hand, null, false, false, false, priority);

            if (placed) {
                if (rotate && !flying && !attackRotated) Anatorius.ROTATION_MANAGER.rotate(look, priority);
                lastObsidianBase = position;
                basePlaceTimer.reset();
            }

            if (switched) InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
        };

        if (sequenced) task.run();
        else placeRunnable = task;

        return true;
    }

    private CalculationContext createCalculationContext() {
        List<PlayerEntity> players = getPlayers();
        List<TargetInfo> targets = new ArrayList<>(players.size());
        int extraTicks = extrapolation.getValue().intValue();
        float minimum = minimumDamage.getValue().floatValue();

        for (PlayerEntity player : players) {
            targets.add(new TargetInfo(
                    player,
                    PositionUtils.extrapolate(player, extraTicks),
                    player.getHealth() + player.getAbsorptionAmount(),
                    getMinimumDamage(player, minimum)
            ));
        }

        return new CalculationContext(
                targets,
                mc.player.getEyePos(),
                MathHelper.square(attackRange.getValue().doubleValue()),
                MathHelper.square(attackWallsRange.getValue().doubleValue()),
                MathHelper.square(placeRange.getValue().doubleValue()),
                MathHelper.square(placeWallsRange.getValue().doubleValue()),
                Math.max(attackRange.getValue().doubleValue(), attackWallsRange.getValue().doubleValue()),
                Math.max(placeRange.getValue().doubleValue(), placeWallsRange.getValue().doubleValue()),
                !forceSuicide.getValue(),
                mc.player.getHealth() + mc.player.getAbsorptionAmount()
        );
    }

    private EndCrystalEntity calculateCrystals(CalculationContext context) {
        if (!attack.getValue() || shouldPause("Attack") || context.targets().isEmpty()) return null;

        EndCrystalEntity best = null;
        float bestDamage = 0.0f;

        Box attackBox = mc.player.getBoundingBox().expand(context.maxAttackRange());
        for (Entity entity : otherEntities(attackBox)) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (!crystal.isAlive()) continue;
            if (inhibit.getValue() && attackedCrystals.containsKey(entity.getId())) continue;

            double distanceSq = getCrystalAttackDistanceSq(crystal, context.eyePos());
            if (distanceSq > context.attackRangeSq()) continue;
            if (!mc.world.getWorldBorder().contains(crystal.getBlockPos())) continue;
            if (!WorldUtils.canSee(getCrystalAttackPoint(crystal)) && (raytrace.getValue() || distanceSq > context.attackWallsRangeSq())) continue;

            if (context.checkSelfDamage()) {
                float selfDamage = DamageUtils.getCrystalDamage(mc.player, null, crystal, ignoreTerrain.getValue());
                if (selfDamage > maximumSelfDamage.getValue().floatValue()) continue;
                if (antiSuicide.getValue() && selfDamage > context.selfHealth()) continue;
            }

            boolean lethal = false;
            for (TargetInfo info : context.targets()) {
                float damage = DamageUtils.getCrystalDamage(info.player(), info.extrapolatedBox(), crystal, ignoreTerrain.getValue());
                if (damage < info.minimumDamage() && damage < info.health()
                        && !(damage * (1.0f + lethalMultiplier.getValue().floatValue()) >= info.health())) continue;

                if (damage > bestDamage || damage > info.health()) {
                    best = crystal;
                    bestDamage = damage;
                    if (damage > info.health()) {
                        lethal = true;
                        break;
                    }
                }
            }
            if (lethal) break;
        }
        return best;
    }

    private PlaceTarget calculatePlacements(BlockPos exception) {
        return calculatePlacements(exception, createCalculationContext());
    }

    private PlaceTarget calculatePlacements(BlockPos exception, CalculationContext context) {
        if (!place.getValue()) return null;
        if (exception == null ? shouldPause("Place") : pausedForEating("Place")) return null;
        if (!canUseCrystal()) return null;
        if (context.targets().isEmpty()) return null;

        BlockPos bestPosition = null;
        PlayerEntity bestPlayer = null;
        List<Entity> obstructions = new ArrayList<>();
        float bestDamage = 0.0f;
        float bestSelfDamage = Float.MAX_VALUE;
        boolean bestLethal = false;
        int calculations = 0;
        int minimumCrystalAge = (20 - attackSpeed.getValue().intValue()) + 15;
        boolean protocol = placements.getValue().equalsIgnoreCase("Protocol");

        PlaceTarget previous = exception == null ? placeTargetRef.get() : null;
        BlockPos held = previous != null && previous.getException() == null ? previous.getPosition() : null;
        PlayerEntity heldPlayer = null;
        float heldDamage = -1.0f;
        boolean heldLethal = false;

        for (int i = 0; i < Anatorius.WORLD_MANAGER.getRadius(context.maxPlaceRange()); i++) {
            BlockPos position = mc.player.getBlockPos().add(Anatorius.WORLD_MANAGER.getOffset(i));
            Vec3d interactPoint = getPlaceInteractPoint(position);
            double placeDistSq = placeDistanceSq(context.eyePos(), position, interactPoint);

            if (placeDistSq > context.placeRangeSq()) continue;
            if (!mc.world.getWorldBorder().contains(position)) continue;

            BlockState base = mc.world.getBlockState(position);
            if (base.getBlock() != Blocks.OBSIDIAN && base.getBlock() != Blocks.BEDROCK) continue;

            BlockPos up = position.up();
            if (!up.equals(exception) && !mc.world.getBlockState(up).isAir()) continue;

            if (protocol) {
                BlockPos up2 = position.up(2);
                if (!up2.equals(exception) && !mc.world.getBlockState(up2).isAir()) continue;
            }

            if (!WorldUtils.canSee(interactPoint) && (raytrace.getValue() || placeDistSq > context.placeWallsRangeSq())) continue;

            boolean blocked = false;
            EndCrystalEntity blocking = null;
            for (Entity entity : otherEntities(WorldUtils.crystalBox(up))) {
                if (!entity.isAlive()) continue;
                if (entity instanceof ExperienceOrbEntity) continue;
                if (entity instanceof EndCrystalEntity existing) {
                    if (blocking == null && existing.age >= minimumCrystalAge) blocking = existing;
                    continue;
                }
                blocked = true;
                break;
            }
            if (blocked) continue;

            float selfDamage = 0.0f;
            if (context.checkSelfDamage()) {
                selfDamage = DamageUtils.getCrystalDamage(mc.player, null, position, exception, ignoreTerrain.getValue());
                if (selfDamage > maximumSelfDamage.getValue().floatValue()) continue;
                if (antiSuicide.getValue() && selfDamage > context.selfHealth()) continue;
            }

            for (TargetInfo info : context.targets()) {
                calculations++;
                float damage = DamageUtils.getCrystalDamage(info.player(), info.extrapolatedBox(), position, exception, ignoreTerrain.getValue());
                if (damage < info.minimumDamage() && damage < info.health()
                        && !(damage * (1.0f + lethalMultiplier.getValue().floatValue()) >= info.health())) continue;

                if (exception == null && blocking != null) {
                    obstructions.add(blocking);
                    break;
                }

                boolean lethal = damage >= info.health();
                if (position.equals(held) && damage > heldDamage) {
                    heldPlayer = info.player();
                    heldDamage = damage;
                    heldLethal = lethal;
                }
                if (isBetterSpot(lethal, damage, selfDamage, bestLethal, bestDamage, bestSelfDamage)) {
                    bestPosition = position;
                    bestPlayer = info.player();
                    bestDamage = damage;
                    bestSelfDamage = selfDamage;
                    bestLethal = lethal;
                }
            }
        }

        if (heldPlayer != null && bestPosition != null && !bestPosition.equals(held)
                && !(bestLethal && !heldLethal) && Math.round(heldDamage) >= bestDamage) {
            bestPosition = held;
            bestPlayer = heldPlayer;
            bestDamage = heldDamage;
        }

        if (bestPosition == null) return new PlaceTarget(null, null, obstructions, null, 0.0f, calculations);
        return new PlaceTarget(bestPosition, bestPlayer, obstructions, exception, bestDamage, calculations);
    }

    private BasePlaceTarget calculateBasePlacements(CalculationContext context) {
        if (!basePlace.getValue() || !place.getValue()) return null;
        if (shouldPause("Place")) return null;
        if (!hasObsidianForBasePlace()) return null;
        if (context.targets().isEmpty()) return null;
        if (!canRetryBasePlace()) return null;

        BlockPos bestPosition = null;
        Direction bestDirection = null;
        PlayerEntity bestPlayer = null;
        float bestDamage = 0.0f;
        float bestSelfDamage = Float.MAX_VALUE;
        boolean bestLethal = false;
        int calculations = 0;
        Set<BlockPos> checked = new HashSet<>();

        for (TargetInfo info : context.targets()) {
            BlockPos center = getTargetBaseCenter(info);
            if (center == null) continue;
            Box targetBox = info.player().getBoundingBox().union(info.extrapolatedBox());

            for (int i = 0; i < Anatorius.WORLD_MANAGER.getRadius(context.maxPlaceRange()); i++) {
                BlockPos position = center.add(Anatorius.WORLD_MANAGER.getOffset(i));
                if (!checked.add(position)) continue;

                BlockState state = mc.world.getBlockState(position);
                if (state.getBlock() == Blocks.OBSIDIAN || state.getBlock() == Blocks.BEDROCK) continue;
                if (targetBox.intersects(new Box(position))) continue;

                Direction direction = findBaseSupport(position, context.eyePos());
                if (!isValidBasePlacePosition(position, direction, context.eyePos())) continue;

                float selfDamage = 0.0f;
                if (context.checkSelfDamage()) {
                    selfDamage = DamageUtils.getCrystalDamage(mc.player, null, position, null, ignoreTerrain.getValue());
                    if (selfDamage > maximumSelfDamage.getValue().floatValue()) continue;
                    if (antiSuicide.getValue() && selfDamage > context.selfHealth()) continue;
                }

                calculations++;
                float damage = DamageUtils.getCrystalDamage(info.player(), info.extrapolatedBox(), position, null, ignoreTerrain.getValue());
                if (damage < info.minimumDamage() && damage < info.health()
                        && !(damage * (1.0f + lethalMultiplier.getValue().floatValue()) >= info.health())) continue;

                boolean lethal = damage >= info.health();
                if (isBetterSpot(lethal, damage, selfDamage, bestLethal, bestDamage, bestSelfDamage)) {
                    bestPosition = position;
                    bestDirection = direction;
                    bestPlayer = info.player();
                    bestDamage = damage;
                    bestSelfDamage = selfDamage;
                    bestLethal = lethal;
                }
            }
        }

        if (bestPosition == null) return null;
        return new BasePlaceTarget(bestPosition, bestDirection, bestPlayer, bestDamage, calculations);
    }

    private boolean canRetryBasePlace() {
        BlockPos last = lastObsidianBase;
        PlayerEntity player = target;
        if (last != null && Anatorius.WORLD_MANAGER != null && Anatorius.WORLD_MANAGER.isPending(last)) return false;
        if (last != null && player != null && player.isAlive()) {
            if (!isValidPlacePosition(last, mc.player.getEyePos(), null)) return true;
            float damage = DamageUtils.getCrystalDamage(player, PositionUtils.extrapolate(player, extrapolation.getValue().intValue()), last, null, ignoreTerrain.getValue());
            if (damage >= getMinimumDamage(player, minimumDamage.getValue().floatValue())) return false;
        }
        return basePlaceTimer.hasTimeElapsed(Math.max(50L, basePlaceDelay.getValue().longValue()));
    }

    private static boolean isBetterSpot(boolean lethal, float damage, float selfDamage,
                                        boolean bestLethal, float bestDamage, float bestSelfDamage) {
        if (lethal != bestLethal) return lethal;
        if (damage != bestDamage) return damage > bestDamage;
        return selfDamage < bestSelfDamage;
    }

    private BlockPos getTargetBaseCenter(TargetInfo info) {
        if (info == null || info.extrapolatedBox() == null) return null;
        Box box = info.extrapolatedBox();
        return BlockPos.ofFloored(box.getCenter().x, box.minY, box.getCenter().z);
    }

    private void attack(EndCrystalEntity crystal) {
        int previousSlot = mc.player.getInventory().getSelectedSlot();
        boolean switched = false;

        if (!antiWeakness.getValue().equalsIgnoreCase("None") && mc.player.hasStatusEffect(StatusEffects.WEAKNESS)) {
            int slot = InventoryUtils.findBestSword(InventoryUtils.HOTBAR_START, InventoryUtils.HOTBAR_END);
            if (slot != -1) {
                InventoryUtils.switchSlot(antiWeakness.getValue(), slot, previousSlot);
                switched = true;
            }
        }

        withStoppedSprint(() -> {
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        });

        if (switched) InventoryUtils.switchBack(antiWeakness.getValue(), 0, previousSlot);

        attackedCrystals.put(crystal.getId(), System.currentTimeMillis());
        attackTimer.reset();
        lastActionTick = ticks;
        if (isPlacedCrystal(crystal)) awaiting.add(System.currentTimeMillis());
    }

    private void place(BlockPos position) {
        Hand hand;
        if (mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL) hand = Hand.OFF_HAND;
        else if (mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL) hand = Hand.MAIN_HAND;
        else return;

        sendPlacePacket(hand, position, false);
        doSwing(hand);
        markPlaced(position);
    }

    private void placeSilent(BlockPos position) {
        sendPlacePacket(Hand.MAIN_HAND, position, true);
        doSwing(Hand.MAIN_HAND);
        markPlaced(position);
    }

    private volatile BlockPos lastPlacedBase;

    private void markPlaced(BlockPos position) {
        lastPlacedBase = position;
        long now = System.currentTimeMillis();
        placedCrystals.put(position, now);
        countedCrystals.put(position, now);
        placeTimer.reset();
        lastActionTick = ticks;
    }

    private void sendPlacePacket(Hand hand, BlockPos position, boolean forcePacket) {
        sendPlacePacket(hand, position, forcePacket, true);
    }

    private void sendPlacePacket(Hand hand, BlockPos position, boolean forcePacket, boolean stopSprint) {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        boolean strictDirection = antiCheat != null && antiCheat.strictDirection.getValue();
        boolean vanilla = antiCheat != null && antiCheat.placements.getValue().equalsIgnoreCase("Vanilla");

        Direction direction = WorldUtils.getClosestDirection(position, strictDirection);
        BlockHitResult hitResult = new BlockHitResult(WorldUtils.getHitVector(position, direction), direction, position, false);

        Runnable send = () -> NetworkUtils.sendSequencedPacket(sequence -> new PlayerInteractBlockC2SPacket(hand, hitResult, sequence));
        if (stopSprint) withStoppedSprint(send);
        else send.run();
    }

    private void doSwing(Hand hand) {
        switch (swing.getValue()) {
            case "Default" -> mc.player.swingHand(hand);
            case "Packet" -> mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
            case "Mainhand" -> mc.player.swingHand(Hand.MAIN_HAND);
            case "Offhand" -> mc.player.swingHand(Hand.OFF_HAND);
            case "Both" -> {
                mc.player.swingHand(Hand.MAIN_HAND);
                mc.player.swingHand(Hand.OFF_HAND);
            }
            default -> { }
        }
    }

    private float[] calculateRotations(Vec3d vec) {
        Vec3d eyePos = mc.player.getEyePos();
        double deltaX = vec.x - eyePos.x;
        double deltaY = vec.y - eyePos.y;
        double deltaZ = vec.z - eyePos.z;
        double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0);
        float pitch = (float) MathHelper.clamp(MathHelper.wrapDegrees(-Math.toDegrees(Math.atan2(deltaY, distance))), -90.0, 90.0);

        if (yawStep.getValue()) {
            float serverYaw = Anatorius.ROTATION_MANAGER.getServerYaw();
            float diff = serverYaw - yaw;
            if (Math.abs(diff) > 180.0f) diff += diff > 0.0f ? -360.0f : 360.0f;

            float threshold = yawStepThreshold.getValue().floatValue();
            float step = (diff > 0.0f ? -1 : 1) * threshold;
            yaw = Math.abs(diff) > threshold ? serverYaw + step : yaw;
        }

        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0f, 90.0f)};
    }

    private static <T> List<T> safeCopy(Supplier<? extends Collection<? extends T>> supplier) {
        for (int attempt = 0; attempt < 16; attempt++) {
            try {
                return new ArrayList<>(supplier.get());
            } catch (ConcurrentModificationException ignored) { }
        }
        return Collections.emptyList();
    }

    private List<Entity> otherEntities(Box box) {
        return safeCopy(() -> mc.world.getOtherEntities(null, box));
    }

    private List<PlayerEntity> getPlayers() {
        List<PlayerEntity> list = new ArrayList<>();
        if (forceSuicide.getValue()) {
            list.add(mc.player);
            return list;
        }

        double rangeSq = MathHelper.square(enemyRange.getValue().doubleValue());
        for (PlayerEntity player : safeCopy(() -> mc.world.getPlayers())) {
            if (player == mc.player) continue;
            if (!player.isAlive()) continue;
            if (mc.player.squaredDistanceTo(player) > rangeSq) continue;
            if (Anatorius.FRIEND_MANAGER.contains(player.getName().getString())) continue;
            if (ignoreNaked.getValue() && naked(player)) continue;
            list.add(player);
        }
        return list;
    }

    private static boolean naked(PlayerEntity player) {
        for (net.minecraft.entity.EquipmentSlot slot : new net.minecraft.entity.EquipmentSlot[]{net.minecraft.entity.EquipmentSlot.HEAD,
                net.minecraft.entity.EquipmentSlot.CHEST, net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET}) {
            if (!player.getEquippedStack(slot).isEmpty()) return false;
        }
        return true;
    }

    private boolean pausedForEating(String process) {
        return blocks(whileEating.getValue(), process) && mc.player.isUsingItem();
    }

    private boolean shouldPause(String process) {
        return (blocks(whileEating.getValue(), process) && mc.player.isUsingItem())
                || (blocks(whileMining.getValue(), process) && isMining());
    }

    private static boolean blocks(String keepWorking, String process) {
        return keepWorking.equalsIgnoreCase("None")
                || (process.equalsIgnoreCase("Attack") && keepWorking.equalsIgnoreCase("Place"))
                || (process.equalsIgnoreCase("Place") && keepWorking.equalsIgnoreCase("Attack"));
    }

    private List<BlockPos> mineIgnoredBlocks() {
        List<BlockPos> blocks = new ArrayList<>(2);
        if (!mineIgnore.getValue()) return blocks;
        SpeedMineModule speedMine = Anatorius.MODULE_MANAGER.getModule(SpeedMineModule.class);
        if (speedMine == null || !speedMine.isToggled()) return blocks;
        float ticks = 2.0f + 0.5f * mineThreshold.getValue().intValue();
        for (SpeedMineModule.Action action : new SpeedMineModule.Action[]{speedMine.getPrimary(), speedMine.getSecondary()}) {
            if (action == null || !action.isMining()) continue;
            BlockPos pos = action.getPosition();
            if (!mc.world.getBlockState(pos).isAir() && speedMine.breaksWithin(pos, ticks) && !blocks.contains(pos)) blocks.add(pos);
        }
        return blocks;
    }

    private boolean isMining() {
        SpeedMineModule speedMine = Anatorius.MODULE_MANAGER.getModule(SpeedMineModule.class);
        if (speedMine != null && speedMine.isToggled()) {
            for (SpeedMineModule.Action action : new SpeedMineModule.Action[]{speedMine.getPrimary(), speedMine.getSecondary()}) {
                if (action != null && action.isMining() && !mc.world.getBlockState(action.getPosition()).isAir()) return true;
            }
        }
        return mc.interactionManager != null && mc.interactionManager.isBreakingBlock();
    }

    private boolean hasFreshObsidianDrop(BlockPos center, int maxAge) {
        for (Entity entity : otherEntities(new Box(center).expand(1))) {
            if (!(entity instanceof ItemEntity item)) continue;
            ItemStack stack = item.getStack();
            if (stack.getItem() != Items.OBSIDIAN || stack.getCount() < 8 || item.age > maxAge) continue;
            return true;
        }
        return false;
    }

    private float getMinimumDamage(PlayerEntity player, float base) {
        if (player == null) return base;

        if (chestBreak.getValue()) {
            int maxAge = 2 + Anatorius.SERVER_MANAGER.getPingDelay() + (20 - placeSpeed.getValue().intValue());
            if (hasFreshObsidianDrop(player.getBlockPos(), maxAge) && !hasFreshObsidianDrop(mc.player.getBlockPos(), maxAge)) return 2.0f;
        }

        if (facePlaceMode.getValue().equalsIgnoreCase("None")) return base;

        if (facePlaceSpeed.getValue().equalsIgnoreCase("Normal") || facePlaceTimer.hasTimeElapsed(facePlaceDelay.getValue().longValue() * 50L)) {
            if (facePlaceMode.getValue().equalsIgnoreCase("Always")) return Math.min(base, 2.0f);

            if (facePlaceMode.getValue().equalsIgnoreCase("Dynamic")) {
                if (healthPlace.getValue() && (player.getHealth() + player.getAbsorptionAmount()) <= health.getValue().floatValue())
                    return Math.min(base, 2.0f);

                if (armorPlace.getValue()) {
                    for (ItemStack stack : ItemUtils.getArmorItems(player)) {
                        if (stack == null || !ItemUtils.isArmor(stack)) continue;
                        int durability = (int) Math.round(((stack.getMaxDamage() - stack.getDamage()) * 100.0) / stack.getMaxDamage());
                        if (durability <= percentage.getValue().intValue()) return Math.min(base, 2.0f);
                    }
                }
            }
        }

        return base;
    }

    private record PendingPlace(BlockPos mined, BlockPos position) { }

    private record TargetInfo(PlayerEntity player, Box extrapolatedBox, float health, float minimumDamage) { }

    private record CalculationContext(
            List<TargetInfo> targets,
            Vec3d eyePos,
            double attackRangeSq, double attackWallsRangeSq,
            double placeRangeSq, double placeWallsRangeSq,
            double maxAttackRange, double maxPlaceRange,
            boolean checkSelfDamage, float selfHealth
    ) { }

    @Getter @AllArgsConstructor
    public static class PlaceTarget {
        private BlockPos position;
        private PlayerEntity player;
        private List<Entity> obstructions;
        private BlockPos exception;
        private float damage;
        private int calculations;

        public PlaceTarget clone() {
            return new PlaceTarget(position, player, new ArrayList<>(obstructions), exception, damage, calculations);
        }
    }

    @Getter @AllArgsConstructor
    public static class BasePlaceTarget {
        private BlockPos position;
        private Direction direction;
        private PlayerEntity player;
        private float damage;
        private int calculations;
    }
}
