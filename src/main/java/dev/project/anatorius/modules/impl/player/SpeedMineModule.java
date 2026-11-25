package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.*;
import dev.project.anatorius.settings.impl.*;
import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.utils.animations.Easing;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer3D;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.modules.impl.core.AntiCheatModule;
import dev.project.anatorius.utils.rotations.RotationUtils;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

@RegisterModule(name = "SpeedMine", description = "Automatically mines blocks at a faster speed using packets.", category = Module.Category.PLAYER)
public class SpeedMineModule extends Module {
    public ModeSetting switchMode = new ModeSetting("Switch", "The mode that will be used for automatically switching to the fastest item.", "Silent", new String[]{"Normal", "Silent"});
    public BooleanSetting swapBack = new BooleanSetting("SwapBack", "Goes back to your old slot a moment after the last break. Off: the tool stays in your hand.", new ModeSetting.Visibility(switchMode, "Normal"), true);
    public BooleanSetting alternative = new BooleanSetting("Alternative", "Swaps the tool into your hand slot through the inventory (AltSwap), so it can also come from outside the hotbar.", new ModeSetting.Visibility(switchMode, "Silent"), false);
    public NumberSetting range = new NumberSetting("Range", "The maximum distance at which blocks will be mined.", 6.0, 0.0, 8.0);
    public NumberSetting speed = new NumberSetting("Speed", "The speed at which the module will mine blocks.", 1.0, 0.7, 1.0);
    public ModeSetting rotate = new ModeSetting("Rotate", "Automatically rotates to the block when mining it.", "Packet", new String[]{"None", "Normal", "Packet"});

    public BooleanSetting switchReset = new BooleanSetting("SwitchReset", "Resets the mining when switching slots.", new BooleanSetting.Visibility(alternative, true), true);
    public BooleanSetting doubleMine = new BooleanSetting("Double", "Allows the mining of 2 blocks at the same time (used by AutoMine). Works with AntiCheat Mining on NCP too: with it on, the digs go the Grim way there.", false);
    public BooleanSetting instant = new BooleanSetting("Instant", "Instantly mines blocks once they have been replaced.", false);
    public BooleanSetting async = new BooleanSetting("Async", "Instant (Zio): keeps finishing the spot even while it looks empty here, so a block put back is broken the moment it lands on the server, not a ping later when we see it. Off: only once the block shows up.", new BooleanSetting.Visibility(instant, true), false);
    public ModeSetting value121 = new ModeSetting("1.21", "1.21+ servers put Efficiency on a tool a tick after it's swapped in, so a finish riding the swap is judged without it and refused. "
            + "Tick (Zio): holds the tool and finishes again the next tick. Slow: counts the dig without Efficiency while the tool isn't in hand, so the first finish is enough. Off (Zio's default): neither.",
            "Off", new String[]{"Off", "Tick", "Slow"});
    public BooleanSetting sync = new BooleanSetting("Sync", "TPS sync (Zio's TPSSync): counts the dig at the server's tick rate. A lagging server digs slower than 20 ticks a second, and a finish sent on our count came too early and was refused.", true);
    public NumberSetting instantDelay = new NumberSetting("InstantDelay", "The amount of time that has to pass before instantly mining blocks.", new BooleanSetting.Visibility(instant, true), 0, 0, 20);
    public NumberSetting instantTimeout = new NumberSetting("InstantTimeout", "The amount of time that cancel instantly mine while no block to mine.", new BooleanSetting.Visibility(instant, true), 60, 0, 100);
    public BooleanSetting grim = new BooleanSetting("Grim", "Adds a bypass catered to the Grim anticheat.", new AntiCheatModule.GrimMiningVisibility(), false);
    public BooleanSetting twob2t = new BooleanSetting("2b2t", "Zio's Grim/2b2t start: a decoy START 1420 blocks up right after every real START (Double too), so Grim never rates the real finish as too fast.", new AntiCheatModule.GrimMiningVisibility(), false);
    private static final int DECOY_START_UP = 1420;
    public BooleanSetting strict = new BooleanSetting("Strict", "Waits for the server to tick you before switching back, even after a finish on a spot that looks empty (Instant + Async). A finish on a block that's there always waits.", false);
    public BooleanSetting whileEating = new BooleanSetting("WhileEating", "Mines blocks while eating.", true);
    public WhitelistSetting whitelist = new WhitelistSetting("Whitelist", "Mines only the blocks that are on this list. If empty, every block will be mined.", WhitelistSetting.Type.BLOCKS);
    public BooleanSetting debug = new BooleanSetting("Debug", "Writes every dig, slot and movement packet SpeedMine sends, and the server's block updates at the blocks being dug, to latest.log (to find out why a server won't break them).", false);

    public CategorySetting renderCategory = new CategorySetting("Render", "The category containing all settings related to rendering.");
    public ModeSetting render = new ModeSetting("Render", "Mode", "The rendering that will be applied to the blocks highlighted.", new CategorySetting.Visibility(renderCategory), "Both", new String[]{"None", "Fill", "Outline", "Both"});
    public ModeSetting animation = new ModeSetting("Animation", "The animation that will be used when rendering the block mining progress.", new ModeSetting.Visibility(render, "Fill", "Outline", "Both"), "Expand", new String[]{"None", "Expand", "Rise"});
    public ModeSetting color = new ModeSetting("Color", "The color that will be used when rendering the block mining.", new ModeSetting.Visibility(render, "Fill", "Outline", "Both"), "Smooth", new String[]{"Static", "Smooth", "Custom"});
    public ColorSetting fillColor = new ColorSetting("FillColor", "The color used for the fill rendering.", new ModeSetting.Visibility(render, "Fill", "Both"), ColorUtils.getDefaultFillColor());
    public ColorSetting outlineColor = new ColorSetting("OutlineColor", "The color used for the outline rendering.", new ModeSetting.Visibility(render, "Outline", "Both"), ColorUtils.getDefaultOutlineColor());
    public ModeSetting instantRender = new ModeSetting("InstantRender", "Instant", "The color that will be used for rendering instantly mined blocks.", new CategorySetting.Visibility(renderCategory), "None", new String[]{"None", "Default", "Custom"});
    public ColorSetting instantColor = new ColorSetting("InstantColor", "The custom color used for instantly mined blocks.", new ModeSetting.Visibility(instantRender, "Custom"), ColorUtils.palette(ColorUtils.THEME_ACCENT, 210));

    @Getter private volatile Action primary = null;
    @Getter private volatile Action secondary = null;

    private SwitchAction switchAction = null;

    private static final long FADE_MS = 320L;
    private List<Action> drawnLastFrame = new ArrayList<>(2);
    private final List<Fade> fades = new ArrayList<>();

    private final Timer instantTimer = new Timer();
    private final Timer mineTimer = new Timer();
    private final Timer attemptTimer = new Timer();
    private PendingStop pendingStop;

    private boolean lastSlotPacketSilent;

    private static final int SWAP_BACK_TICKS = 10;
    private int swapBackSlot = -1, swapBackHoldSlot = -1, swapBackTicks;

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (pendingStop != null) {
            PendingStop p = pendingStop;
            pendingStop = null;
            if (!isAlternative()) InventoryUtils.switchSlot(switchType(), p.slot(), p.previousSlot());
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, p.position(), p.direction()));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            if (switchAction == null) releaseTool(p.slot(), p.previousSlot());
        }

        if (isDoubleMine() && secondary != null && secondary.process()) secondary = null;
        if (primary != null && primary.process()) primary = null;
    }

    @SubscribeEvent(priority = Integer.MAX_VALUE)
    public void onTick(TickEvent event) {
        tickSwapBack();
        if (switchAction == null) return;
        long held = System.currentTimeMillis() - switchAction.time();
        if (held < 100L) return;
        if (switchAction.position() != null && mc.world != null && held < holdTimeout()
                && !mc.world.getBlockState(switchAction.position()).isReplaceable()) return;

        if (mc.player != null && mc.world != null && (switchAction.slot() != -1 && switchAction.previousSlot() != -1)) {
            releaseTool(switchAction.slot(), switchAction.previousSlot());
        }

        switchAction = null;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (mc.player == null || mc.world == null) return;

        List<Action> drawn = new ArrayList<>(2);
        Action sec = secondary;
        Action pri = primary;
        if (isDoubleMine() && sec != null && sec.render(event.getMatrices())) drawn.add(sec);
        if (pri != null && pri.render(event.getMatrices())) drawn.add(pri);

        long now = System.currentTimeMillis();
        for (Action a : drawnLastFrame) {
            if (!drawn.contains(a) && a.lastBox != null) fades.add(new Fade(a.lastBox, a.lastFill, a.lastOutline, now));
        }
        drawnLastFrame = drawn;

        fades.removeIf(f -> now - f.start() >= FADE_MS);
        for (Fade f : fades) {
            float t = (now - f.start()) / (float) FADE_MS;
            float alpha = 1.0f - Easing.ease(t, Easing.Method.EASE_OUT_CUBIC);
            drawBox(event.getMatrices(), f.box(), withAlpha(f.fill(), alpha), withAlpha(f.outline(), alpha));
        }
    }

    private static Color withAlpha(Color c, float factor) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), MathHelper.clamp((int) (c.getAlpha() * factor), 0, 255));
    }

    private void drawBox(MatrixStack matrices, Box box, Color fill, Color outline) {
        if (render.getValue().equalsIgnoreCase("Fill") || render.getValue().equalsIgnoreCase("Both")) Renderer3D.renderBox(matrices, box, fill);
        if (render.getValue().equalsIgnoreCase("Outline") || render.getValue().equalsIgnoreCase("Both")) Renderer3D.renderBoxOutline(matrices, box, outline);
    }

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (event.getPacket() instanceof UpdateSelectedSlotC2SPacket packet
                && switchReset.getValue()
                && isAlternative()) {
            boolean silent = packet.getSelectedSlot() != mc.player.getInventory().getSelectedSlot();
            boolean switchBack = !silent && lastSlotPacketSilent;
            lastSlotPacketSilent = silent;
            if (silent) return;
            if (switchBack) return;
            if (!isNcpMode()) return;

            if (secondary != null) { secondary.cancel(); secondary.start(); }
            if (primary != null)   { primary.cancel();   primary.start();   }
        }
    }

    @SubscribeEvent
    public void onBlockUpdate(PacketReceiveEvent event) {
        if (event.getPacket() instanceof BlockUpdateS2CPacket packet) {
            onServerBlock(packet.getPos(), packet.getState());
        } else if (event.getPacket() instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates(this::onServerBlock);
        }
    }

    private void onServerBlock(BlockPos pos, BlockState state) {
        for (Action a : new Action[]{primary, secondary}) {
            if (a == null || !pos.equals(a.position)) continue;
            if (state.isAir()) a.rebreakSince = -1L;
            if (debug.getValue()) Anatorius.LOGGER.info("[SpeedMine] server {} -> {}", pos.toShortString(), state.getBlock().getName().getString());
        }
    }

    @SubscribeEvent
    public void onDebugPacket(PacketSendEvent.Post event) {
        if (!debug.getValue() || mc.player == null) return;
        if (event.getPacket() instanceof PlayerActionC2SPacket packet) {
            Anatorius.LOGGER.info("[SpeedMine] sent {} {} slot={}", packet.getAction(), packet.getPos().toShortString(), Anatorius.POSITION_MANAGER.getServerSlot());
        } else if (event.getPacket() instanceof UpdateSelectedSlotC2SPacket packet) {
            Anatorius.LOGGER.info("[SpeedMine] sent SLOT {}", packet.getSelectedSlot());
        } else if (event.getPacket() instanceof net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket && (primary != null || secondary != null)) {
            Anatorius.LOGGER.info("[SpeedMine] sent MOVE");
        }
    }

    @SubscribeEvent
    public void onSlotPacket(PacketSendEvent event) {
        if (!isNcpMode() || mc.player == null) return;
        if (!(event.getPacket() instanceof UpdateSelectedSlotC2SPacket packet)) return;
        Action a = primary;
        if (a == null || !a.mining || a.ncpSelfSwitch || a.ncpStartItem == null) return;
        if (mc.player.getInventory().getStack(packet.getSelectedSlot()).getItem() != a.ncpStartItem) a.ncpDigReset = true;
    }

    private boolean ncpHoldsTool() {
        return isNcpMode() && !isAlternative();
    }

    @SubscribeEvent
    public void onAttackBlock(AttackBlockEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (isRebreakLocked() && primary != null && !primary.getPosition().equals(event.getPosition())) {
            event.setCancelled(true);
            return;
        }

        if (handle(event.getPosition(), 1)) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        fades.clear();
        drawnLastFrame = new ArrayList<>(2);
        if (mc.player == null || mc.getNetworkHandler() == null) {
            switchAction = null;
            pendingStop = null;
            return;
        }
        if (primary != null)   primary.cancel();
        if (secondary != null) secondary.cancel();
        if (pendingStop != null) {
            releaseTool(pendingStop.slot(), pendingStop.previousSlot());
            pendingStop = null;
        }
        if (switchAction != null) {
            if (switchAction.slot() != -1 && switchAction.previousSlot() != -1)
                releaseTool(switchAction.slot(), switchAction.previousSlot());
            switchAction = null;
        }
        primary = null;
        secondary = null;
        if (swapBackSlot != -1) {
            if (mc.player.getInventory().getSelectedSlot() == swapBackHoldSlot) InventoryUtils.switchBack("Normal", swapBackHoldSlot, swapBackSlot);
            swapBackSlot = -1;
        }
    }

    @Override
    public String getMetaData() {
        String primaryProgress = primary == null ? "0.0" : new DecimalFormat("0.0").format(primary.getProgress() / primary.getSpeed());
        String secondaryProgress = secondary == null || !isDoubleMine() ? "" : ", " + new DecimalFormat("0.0").format(secondary.getProgress() / secondary.getSpeed());
        return primaryProgress + secondaryProgress;
    }

    public boolean handle(BlockPos position, int priority) {
        if (mc.interactionManager.getCurrentGameMode() == GameMode.CREATIVE || mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR) return false;
        if (mc.world.getBlockState(position).getBlock().getHardness() == -1) return false;
        if (!whitelist.getWhitelist().isEmpty() && !whitelist.isWhitelistContains(mc.world.getBlockState(position).getBlock())) return false;
        if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(position)) > MathHelper.square(range.getValue().doubleValue()))
            return false;

        if ((primary != null && primary.getPosition().equals(position)) || (secondary != null && secondary.getPosition().equals(position))) return true;
        if (isRebreakLocked()) return true;

        if (switchAction != null && switchAction.position() == null) {
            if (switchAction.slot() != -1 && switchAction.previousSlot() != -1)
                releaseTool(switchAction.slot(), switchAction.previousSlot());
            switchAction = null;
        }

        if (isDoubleMine()) {
            if (secondary != null) {
                primary = new Action(position, priority);
            } else {
                if (primary != null) {
                    if (!primary.isInstantMine()) secondary = primary;
                    primary = new Action(position, priority);
                } else {
                    primary = new Action(position, priority);
                }
            }
        } else {
            if (primary != null) primary.cancel();
            primary = new Action(position, priority);
        }

        return true;
    }

    public boolean isRebreakLocked() {
        return primary != null
                && primary.isInstantMine()
                && primary.getAttempts() != 0
                && !instantTimer.hasTimeElapsed(instantTimeout.getValue().longValue() * 50L);
    }

    public boolean isNcpMode() { return AntiCheatModule.ncpMining() && !doubleMine.getValue(); }

    public boolean isDoubleMine() { return doubleMine.getValue(); }

    public boolean isSwapPending() { return isToggled() && pendingStop != null; }

    public boolean isAlternative() { return switchMode.getValue().equalsIgnoreCase("Silent") && alternative.getValue(); }

    private String switchType() { return isAlternative() ? "AltSwap" : switchMode.getValue(); }

    private void releaseTool(int slot, int previousSlot) {
        if (!switchMode.getValue().equalsIgnoreCase("Normal")) {
            InventoryUtils.switchBack(switchType(), slot, previousSlot);
            return;
        }
        if (!swapBack.getValue() || previousSlot == -1) return;
        if (swapBackSlot == -1) {
            if (previousSlot == slot) return;
            swapBackSlot = previousSlot;
        }
        swapBackHoldSlot = slot;
        swapBackTicks = SWAP_BACK_TICKS;
    }

    private void tickSwapBack() {
        if (swapBackSlot == -1) return;
        if (mc.player == null || mc.player.getInventory().getSelectedSlot() != swapBackHoldSlot) {
            swapBackSlot = -1;
            return;
        }
        if (primary != null && primary.ncpHolding) return;
        if (--swapBackTicks > 0) return;
        InventoryUtils.switchBack("Normal", swapBackHoldSlot, swapBackSlot);
        swapBackSlot = -1;
    }

    public void abort(Action action) {
        if (action == null) return;
        action.cancel();
        if (action == primary) primary = null;
        if (action == secondary) secondary = null;
    }

    public boolean isNcpDigging() {
        return isToggled() && isNcpMode() && primary != null && primary.isMining();
    }

    public boolean isPacketMining() {
        return isToggled() && ((primary != null && primary.isMining()) || (secondary != null && secondary.isMining()));
    }

    public int getDigToolSlot() {
        Action a = primary;
        return a == null ? -1 : a.getToolSlot();
    }

    public boolean isBreakingSoon(BlockPos pos) { return isBreakingSoon(pos, 0.75f); }

    public boolean isBreakingSoon(BlockPos pos, float threshold) {
        if (pos == null) return false;
        if (primary != null && pos.equals(primary.position)) return ratio(primary) >= threshold;
        if (secondary != null && pos.equals(secondary.position)) return ratio(secondary) >= threshold;
        return false;
    }

    public boolean breaksWithin(BlockPos pos, float ticks) {
        if (pos == null || mc.player == null) return false;
        for (Action a : new Action[]{primary, secondary}) {
            if (a == null || !a.mining || !pos.equals(a.position)) continue;
            int slot = a.toolSlot == -1 ? mc.player.getInventory().getSelectedSlot() : a.toolSlot;
            float delta = WorldUtils.getMineSpeed(a.state, slot) / Anatorius.WORLD_MANAGER.getTimerMultiplier();
            return a.progress + delta * ticks >= a.getSpeed();
        }
        return false;
    }

    public boolean isBeingBroken(BlockPos pos) {
        if (pos == null) return false;
        return (primary != null && pos.equals(primary.position))
                || (secondary != null && pos.equals(secondary.position));
    }

    private float ratio(Action a) {
        if (a == null) return 0f;
        float denom = a.getSpeed();
        if (denom <= 0f) return 0f;
        return MathHelper.clamp(a.progress / denom, 0f, 1f);
    }

    private boolean isOutOfRange(BlockPos position) {
        if (position == null) return true;
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(position)) > MathHelper.square(range.getValue().doubleValue());
    }

    @Getter
    public class Action {
        private final BlockPos position;
        private BlockState state;
        private final int priority;

        @Setter private float progress;
        private float prevProgress;
        private int attempts;
        private int ticks;
        private boolean mining;
        private boolean instantMine;
        private int toolSlot = -1;

        private net.minecraft.item.Item ncpStartItem;
        private volatile boolean ncpDigReset;
        private boolean ncpSelfSwitch, ncpHolding;
        private int ncpFullTicks;
        private int ncpWaitTicks;
        private int ncpHoldSlot = -1, ncpPrevSlot = -1;
        private volatile long rebreakSince = -1L;
        private long awaitSince = -1L;
        private Direction awaitDirection;

        private Box lastBox;
        private Color lastFill, lastOutline;

        public Action(BlockPos position, int priority) {
            this.position = position;
            this.state = mc.world.getBlockState(position);
            this.priority = priority;
            cacheToolSlot();
            start();
        }

        public boolean isMining() { return mining; }

        private void cacheToolSlot() {
            int slot = InventoryUtils.findFastestItem(this.state, InventoryUtils.HOTBAR_START,
                    isAlternative() ? InventoryUtils.INVENTORY_END : InventoryUtils.HOTBAR_END);
            if (slot == -1) slot = mc.player.getInventory().getSelectedSlot();
            this.toolSlot = slot;
        }

        public void resetProgress() {
            this.progress = 0.0f;
            this.prevProgress = 0.0f;
            this.attempts = 0;
        }

        public boolean process() {
            if (isOutOfRange(position)) {
                cancel();
                return true;
            }

            Action sec = getSecondary();
            boolean secondary = sec != null && position.equals(sec.getPosition());
            if (secondary) instantMine = false;

            if (secondary && mc.world.getBlockState(position).isReplaceable()) {
                cancel();
                return true;
            }

            if (ncpDigReset && mining) {
                ncpDigReset = false;
                ncpHolding = Anatorius.POSITION_MANAGER.getServerSlot() == ncpHoldSlot && ncpHolding;
                start();
                return false;
            }

            if (awaitSince != -1L) {
                boolean gone = mc.world.getBlockState(position).isReplaceable();
                if (!gone && System.currentTimeMillis() - awaitSince < holdTimeout()) return false;
                awaitSince = -1L;
                if (gone) {
                    this.mining = false;
                    this.progress = 0.0f;
                    this.prevProgress = 0.0f;
                } else {
                    start();
                }
                return false;
            }

            if (instantMine && !secondary && rebreakSince != -1L) {
                if (mc.world.getBlockState(position).isReplaceable()) {
                    rebreakSince = -1L;
                } else if (System.currentTimeMillis() - rebreakSince >= rebreakDelay()) {
                    rebreakSince = -1L;
                    cancel();
                    start();
                    return false;
                }
            }

            AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
            boolean strictDirection = antiCheat != null && antiCheat.strictDirection.getValue();
            Direction direction = WorldUtils.getClosestDirection(position, strictDirection);

            BlockState currentWorldState = mc.world.getBlockState(position);
            if (!instantMine && !currentWorldState.isReplaceable() && currentWorldState.getBlock() != this.state.getBlock()) {
                this.state = currentWorldState;
            }

            cacheToolSlot();

            BlockState state = instantMine ? this.state : currentWorldState;
            boolean worldBlockPresent = !currentWorldState.isReplaceable();

            if (mining) {
                int slot = this.toolSlot;
                if (slot == -1) slot = mc.player.getInventory().getSelectedSlot();

                boolean noEfficiency = value121.getValue().equalsIgnoreCase("Slow") && !secondary && !isNcpMode()
                        && slot != mc.player.getInventory().getSelectedSlot();
                float delta = WorldUtils.getMineSpeed(state, slot, !noEfficiency) / Anatorius.WORLD_MANAGER.getTimerMultiplier();
                if (sync.getValue()) delta *= MathHelper.clamp(Anatorius.SERVER_MANAGER.getTickRate() / 20.0f, 0.05f, 1.0f);
                ticks++;

                prevProgress = progress;
                progress = MathHelper.clamp(progress + delta, 0.0f, getSpeed());

                if (rotate.getValue().equalsIgnoreCase("Normal") && worldBlockPresent && progress + (delta * 2) >= getSpeed()) {
                    Anatorius.ROTATION_MANAGER.rotate(
                            RotationUtils.getRotations(WorldUtils.getHitVector(position, direction), false),
                            Anatorius.ROTATION_MANAGER.getModulePriority(Anatorius.MODULE_MANAGER.getModule(SpeedMineModule.class)));
                }

                boolean blockStillSolid = instantMine ? !this.state.isReplaceable() && (async.getValue() || worldBlockPresent) : worldBlockPresent;

                boolean ready;
                if (isNcpMode()) {
                    ncpFullTicks = progress >= getSpeed() ? ncpFullTicks + 1 : 0;
                    ready = ncpFullTicks >= 2;
                } else {
                    ready = progress + delta >= getSpeed() || progress >= getSpeed();
                }
                boolean usingOk = isNcpMode() ? !mc.player.isUsingItem() : whileEating.getValue() || !mc.player.isUsingItem();
                if (ready && blockStillSolid && usingOk) {
                    long instantWait = instantDelay.getValue().longValue() * 50L;
                    if (!instantMine || (instantTimer.hasTimeElapsed(instantWait) && attemptTimer.hasTimeElapsed(instantWait))) {
                        Anatorius.EVENT_HANDLER.post(new DestroyBlockEvent(position));
                        if (instantMine) attemptTimer.reset();

                        if (rotate.getValue().equalsIgnoreCase("Packet") && worldBlockPresent)
                            Anatorius.ROTATION_MANAGER.packetRotate(
                                    RotationUtils.getRotations(WorldUtils.getHitVector(position, direction), false),
                                    Anatorius.ROTATION_MANAGER.getModulePriority(Anatorius.MODULE_MANAGER.getModule(SpeedMineModule.class)));

                        ItemStack tool = mc.player.getInventory().getStack(slot);
                        boolean toolHeld = ItemStack.areItemsAndComponentsEqual(mc.player.getInventory().getStack(Anatorius.POSITION_MANAGER.getServerSlot()), tool);
                        float plain = WorldUtils.getMineSpeed(state, slot, false);
                        boolean finishAgain = value121.getValue().equalsIgnoreCase("Tick") && !toolHeld && !secondary && !isNcpMode()
                                && worldBlockPresent && plain < WorldUtils.getMineSpeed(state, slot, true)
                                && plain * (ticks + 1) < 0.7f;

                        int previousSlot = mc.player.getInventory().getSelectedSlot();
                        InventoryUtils.switchSlot(switchType(), slot, previousSlot);

                        boolean movementSync = antiCheat != null && antiCheat.movementSync.getValue();
                        if (!isNcpMode() && !instant.getValue() && !movementSync)
                            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.isOnGround(), mc.player.horizontalCollision));

                        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, position, direction));
                        if (instant.getValue() && !secondary && !isNcpMode() && rebreakSince == -1L) rebreakSince = System.currentTimeMillis();
                        if (grim.getValue() && !isNcpMode())
                            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, position.up(500), direction));
                        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

                        ncpSelfSwitch = true;
                        if (ncpHolding) {
                            releaseTool(ncpHoldSlot, ncpPrevSlot);
                            ncpHolding = false;
                        } else if (finishAgain && pendingStop == null) {
                            pendingStop = new PendingStop(position, direction, slot, previousSlot);
                        } else if (worldBlockPresent)
                            switchAction = new SwitchAction(slot, previousSlot, System.currentTimeMillis(), position);
                        else if (strict.getValue() || (isDoubleMine() && secondary))
                            switchAction = new SwitchAction(slot, previousSlot, System.currentTimeMillis(), null);
                        else if (switchAction == null)
                            releaseTool(slot, previousSlot);
                        ncpSelfSwitch = false;

                        if (!instantMine || secondary) mineTimer.reset();
                    }

                    attempts++;
                    if (!secondary) {
                        if (isNcpMode()) {
                            this.progress = 0.0f;
                            this.prevProgress = 0.0f;
                            this.mining = false;
                            ncpWaitTicks = 20;
                        } else if (!instant.getValue()) {
                            awaitSince = System.currentTimeMillis();
                            awaitDirection = direction;
                        } else {
                            this.instantMine = true;
                            instantTimer.reset();
                        }
                    }

                    return isDoubleMine() && secondary;
                }
            } else if (isNcpMode() || worldBlockPresent) {
                start();
            }

            return false;
        }

        public boolean render(MatrixStack matrices) {
            if (mc.world.getBlockState(position).isReplaceable() && (!instantMine || instantRender.getValue().equalsIgnoreCase("None")))
                return false;

            Box box = new Box(position);
            double progress = MathHelper.lerp(mc.getRenderTickCounter().getTickProgress(false), prevProgress / getSpeed(), this.progress / getSpeed());

            if (animation.getValue().equalsIgnoreCase("Expand"))
                box = new Box(position).contract(0.5).expand(MathHelper.clamp(progress / 2.0, 0.0, 0.5));
            if (animation.getValue().equalsIgnoreCase("Rise"))
                box = new Box(position.getX(), position.getY(), position.getZ(), position.getX() + 1.0, position.getY() + progress, position.getZ() + 1.0);

            Color fill = fillColor.getColor();
            Color outline = outlineColor.getColor();

            if (color.getValue().equalsIgnoreCase("Static")) {
                fill    = progress >= 0.9 ? new Color(0, 255, 0, fillColor.getAlpha())    : new Color(255, 0, 0, fillColor.getAlpha());
                outline = progress >= 0.9 ? new Color(0, 255, 0, outlineColor.getAlpha()) : new Color(255, 0, 0, outlineColor.getAlpha());
            } else if (color.getValue().equalsIgnoreCase("Smooth")) {
                int r = 255 - (int) (MathHelper.clamp(progress, 0.0f, 1.0f) * 255);
                int g = (int) (MathHelper.clamp(progress, 0.0f, 1.0f) * 255);
                fill    = new Color(r, g, 0, fillColor.getAlpha());
                outline = new Color(r, g, 0, outlineColor.getAlpha());
            }

            if (progress >= getSpeed() && instantMine && instantRender.getValue().equalsIgnoreCase("Custom")) {
                fill    = ColorUtils.getColor(instantColor.getColor(), fillColor.getAlpha());
                outline = ColorUtils.getColor(instantColor.getColor(), outlineColor.getAlpha());
            }

            lastBox = box;
            lastFill = fill;
            lastOutline = outline;
            drawBox(matrices, box, fill, outline);
            return true;
        }

        public void start() {
            cacheToolSlot();

            AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
            boolean strictDirection = antiCheat != null && antiCheat.strictDirection.getValue();
            Direction direction = WorldUtils.getClosestDirection(position, strictDirection);

            int startPrevSlot = mc.player.getInventory().getSelectedSlot();
            boolean startSwap = !isNcpMode() && toolSlot != -1 && !mc.player.isUsingItem()
                    && !ItemStack.areItemsAndComponentsEqual(mc.player.getInventory().getStack(Anatorius.POSITION_MANAGER.getServerSlot()), mc.player.getInventory().getStack(toolSlot));
            if (startSwap) InventoryUtils.switchSlot(switchType(), toolSlot, startPrevSlot);

            if (isNcpMode()) {
                if (mc.world.getBlockState(position).isReplaceable()) {
                    this.progress = 0.0f;
                    this.prevProgress = 0.0f;
                    this.mining = false;
                    ncpWaitTicks = 0;
                    return;
                }
                if (ncpWaitTicks > 0) {
                    ncpWaitTicks--;
                    this.progress = 0.0f;
                    this.prevProgress = 0.0f;
                    this.mining = false;
                    return;
                }
                if (ncpHoldsTool() && !ncpHolding && mc.player.isUsingItem()) {
                    this.progress = 0.0f;
                    this.prevProgress = 0.0f;
                    this.mining = false;
                    return;
                }
                if (ncpHoldsTool() && !ncpHolding) {
                    int previous = mc.player.getInventory().getSelectedSlot();
                    ncpSelfSwitch = true;
                    InventoryUtils.switchSlot(switchType(), toolSlot, previous);
                    ncpSelfSwitch = false;
                    ncpHolding = true;
                    ncpHoldSlot = toolSlot;
                    ncpPrevSlot = previous;
                }
                ncpStartItem = mc.player.getInventory().getStack(ncpHolding ? ncpHoldSlot : Anatorius.POSITION_MANAGER.getServerSlot()).getItem();
                ncpDigReset = false;
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, position, direction));
            } else if (isDoubleMine()) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, position, direction));
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, position, direction));
                sendDecoyStart(direction);
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, position, direction));
            } else {
                if (grim.getValue())
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, position, direction));
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, position, direction));
                if (twob2t.getValue()) sendDecoyStart(direction);
                else mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, position, direction));
            }

            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            if (startSwap) releaseTool(toolSlot, startPrevSlot);

            this.progress = 0.0f;
            this.prevProgress = 0.0f;
            this.attempts = 0;
            this.ticks = 0;
            this.mining = true;
            this.instantMine = false;
            this.ncpFullTicks = 0;
            this.awaitSince = -1L;
        }

        public void cancel() {
            if (ncpHolding) {
                ncpSelfSwitch = true;
                releaseTool(ncpHoldSlot, ncpPrevSlot);
                ncpSelfSwitch = false;
                ncpHolding = false;
            }
            if (!isDoubleMine()) {
                AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
                boolean strictDirection = antiCheat != null && antiCheat.strictDirection.getValue();
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, position,
                        WorldUtils.getClosestDirection(position, strictDirection)));
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }

            this.progress = 0.0f;
            this.prevProgress = 0.0f;
            this.attempts = 0;
            this.mining = false;
            this.instantMine = false;
            this.rebreakSince = -1L;
            this.awaitSince = -1L;
        }

        private void sendDecoyStart(Direction direction) {
            if (!twob2t.getValue()) return;
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, position.up(DECOY_START_UP), direction));
        }

        private long rebreakDelay() {
            var entry = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            int ping = entry == null ? 0 : entry.getLatency();
            long delay = Math.max((long) Math.ceil(ping * 15.0 / 50.0) * 50L, 750L);
            float tickScale = MathHelper.clamp(Anatorius.SERVER_MANAGER.getTickRate() / 20.0f, 0.05f, 1.0f);
            return (long) (delay / tickScale) + instantDelay.getValue().longValue() * 50L;
        }

        private float getSpeed() {
            return getSecondary() != null && position.equals(getSecondary().getPosition()) ? 1.0f : speed.getValue().floatValue();
        }
    }

    private record Fade(Box box, Color fill, Color outline, long start) { }

    private record SwitchAction(int slot, int previousSlot, long time, BlockPos position) { }

    private long holdTimeout() {
        var entry = mc.getNetworkHandler() == null || mc.player == null ? null : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        int ping = entry == null ? 0 : entry.getLatency();
        return MathHelper.clamp(ping * 2L + 400L, 500L, 1500L);
    }

    private record PendingStop(BlockPos position, Direction direction, int slot, int previousSlot) { }
}
