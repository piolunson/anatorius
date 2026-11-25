package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.core.AntiCheatModule;
import dev.project.anatorius.modules.impl.player.SpeedMineModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.settings.impl.WhitelistSetting;
import dev.project.anatorius.utils.chat.ChatUtils;
import dev.project.anatorius.utils.minecraft.DamageUtils;
import dev.project.anatorius.utils.minecraft.HoleUtils;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.rotations.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockBreakingProgressS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@RegisterModule(name = "AutoMine", description = "Automatically mines blocks around enemies. Requires SpeedMine to be enabled.", category = Module.Category.COMBAT)
public class AutoMineModule extends Module {
    public final ModeSetting      sequence    = new ModeSetting("Sequence", "Which block goes first when SpeedMine's Double is enabled.", new AntiCheatModule.GrimMiningVisibility(), "Surround", new String[]{"Surround", "Phase"});
    public final WhitelistSetting whitelist   = new WhitelistSetting("Whitelist", "Mines only the blocks that are on this list. If empty, every block will be mined.", WhitelistSetting.Type.BLOCKS);
    public final NumberSetting    targetRange = new NumberSetting("TargetRange", "Range", "The maximum range at which players are targeted.", 6.0, 1.0, 12.0);
    public final ModeSetting      targetMode  = new ModeSetting("Target", "Priority", "Which nearby enemy to prefer. Angle: the one closest to where you look.", "Closest", new String[]{"Closest", "Health", "Angle"});
    public final BooleanSetting   raytrace    = new BooleanSetting("Raytrace", "Only mines blocks you can see.", false);
    public final BooleanSetting   rotate      = new BooleanSetting("Rotate", "Looks at the block when AutoMine starts digging it (Zio's Rotate; at a visible point with Raytrace).", false);
    public final BooleanSetting   ignoreNaked = new BooleanSetting("IgnoreNaked", "Doesn't go after players without armor.", false);
    public final BooleanSetting   assistant   = new BooleanSetting("Assistant", "Leaves alone the blocks another player is already breaking (Zio).", false);

    public final CategorySetting targetCategory = new CategorySetting("Target", "The blocks AutoMine goes after.");
    public final BooleanSetting burrow      = new BooleanSetting("Burrow", "Breaks the hard block the target is standing inside.", new CategorySetting.Visibility(targetCategory), true);
    public final BooleanSetting web         = new BooleanSetting("Web", "Also breaks a cobweb the target is standing in.", new BooleanSetting.Visibility(burrow, true), false);
    public final BooleanSetting fish        = new BooleanSetting("Fish", "The target stuck in a block that can't be broken: breaks the block under them so they drop out.", new CategorySetting.Visibility(targetCategory), false);
    public final BooleanSetting feet        = new BooleanSetting("Feet", "Breaks the target's surround (city) blocks a crystal can hit them through.", new CategorySetting.Visibility(targetCategory), true);
    public final BooleanSetting face        = new BooleanSetting("Face", "Also considers the blocks next to the target's head, not just the feet.", new CategorySetting.Visibility(targetCategory), true);
    public final BooleanSetting cityOnly    = new BooleanSetting("CityOnly", "Only mines the feet (city) positions, never the head.", new CategorySetting.Visibility(targetCategory), false);
    public final BooleanSetting holeCheck   = new BooleanSetting("HoleCheck", "Only mines the target's feet while they are in a hole.", new BooleanSetting.Visibility(feet, true), false);
    public final BooleanSetting corners     = new BooleanSetting("Corners", "With Double, also breaks the block next to the city block, so a crystal fits on the corner.", new BooleanSetting.Visibility(feet, true), true);
    public final BooleanSetting safe        = new BooleanSetting("Safe", "Never breaks the blocks of your own surround.", new BooleanSetting.Visibility(feet, true), true);
    public final BooleanSetting head        = new BooleanSetting("Head", "Breaks the block above the target's head (cev), when a crystal fits on top of it.", new CategorySetting.Visibility(targetCategory), false);
    public final BooleanSetting inCrawl     = new BooleanSetting("InCrawl", "Head and Face also go after a target that is crawling (Zio).", new BooleanSetting.Visibility(head, true), false);
    public final BooleanSetting trapOverride = new BooleanSetting("TrapOverride", "While AutoTrap is on, the head block goes before the feet (Zio).", new BooleanSetting.Visibility(head, true), false);
    public final BooleanSetting terrain     = new BooleanSetting("Terrain", "Breaks terrain next to the target where an obsidian base would hit them harder.", new CategorySetting.Visibility(targetCategory), false);
    public final BooleanSetting self        = new BooleanSetting("Self", "Breaks your own roof while a target is near (the floor under you too, with Down).", new CategorySetting.Visibility(targetCategory), false);
    public final BooleanSetting tryCrawl    = new BooleanSetting("TryCrawl", "Breaks the block you stand in when the one at your head is hard, so you drop into a crawl, and keeps you crawling (Zio).", new BooleanSetting.Visibility(self, true), false);
    public final BooleanSetting selfInstant = new BooleanSetting("Instant", "Keeps offering your last own block for a moment, so it's dug again right after it's put back (Zio).", new BooleanSetting.Visibility(self, true), false);
    public final BooleanSetting down        = new BooleanSetting("Down", "Breaks the floor under you when there's a safe hole below it.", new BooleanSetting.Visibility(self, true), true);
    public final BooleanSetting downPriority = new BooleanSetting("DownPriority", "Down doesn't check for a safe hole below first (Zio).", new BooleanSetting.Visibility(down, true), false);
    public final BooleanSetting enderChest  = new BooleanSetting("EnderChest", "Breaks nearby ender chests when there's nothing better to mine.", new CategorySetting.Visibility(targetCategory), false);
    public final NumberSetting  chestRange  = new NumberSetting("EnderChestRange", "Distance", "How close an ender chest has to be.", new BooleanSetting.Visibility(enderChest, true), 2.5, 0.5, 6.0);
    public final BooleanSetting antiCrawl   = new BooleanSetting("AntiCrawl", "Breaks the block forcing you into a crawl, or the one stuck in your face.", new CategorySetting.Visibility(targetCategory), false);

    private static final double INSET = 0.0625;
    private static final int OVERRIDE = 1500;
    private static final int STALE_TICKS = 2;

    private record Pending(BlockPos position, int tick) {}

    private final List<Pending> pending = new ArrayList<>();
    private PlayerEntity currentTarget;
    private int primaryStale, secondaryStale, ticks;
    private double reachSq;
    private SpeedMineModule sm;

    private BlockPos lastSecondary, recentSecondary;
    private long recentSecondaryTime = -1L;
    private BlockPos selfRecent;
    private int selfRecentPriority;
    private long selfRecentTime;
    private final Map<BlockPos, Long> othersBreaking = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long ASSIST_TIMEOUT = 3000L;

    private SpeedMineModule speedMine() {
        return Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(SpeedMineModule.class);
    }

    @Override
    public void onEnable() {
        currentTarget = null;
        pending.clear();
        lastSecondary = recentSecondary = selfRecent = null;
        recentSecondaryTime = -1L;
        othersBreaking.clear();
        SpeedMineModule speedMine = speedMine();
        if (speedMine != null && !speedMine.isToggled()) {
            Anatorius.CHAT_MANAGER.addMessage(ChatUtils.getPrimary().toString() + "§lAutoMine" + ChatUtils.getSecondary()
                    + " requires " + ChatUtils.getPrimary() + "SpeedMine" + ChatUtils.getSecondary() + " to be enabled to mine.", "automine-hint");
        }
    }

    @Override
    public void onDisable() {
        pending.clear();
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (getNull()) return;
        sm = speedMine();
        if (sm == null || !sm.isToggled()) return;

        ticks++;
        reachSq = MathHelper.square(sm.range.getValue().doubleValue());
        trackSecondary();
        if (!othersBreaking.isEmpty()) {
            long now = System.currentTimeMillis();
            othersBreaking.values().removeIf(time -> now - time > ASSIST_TIMEOUT);
        }

        if (!pending.isEmpty()) {
            pending.removeIf(p -> {
                if (ticks < p.tick()) return false;
                mine(p.position());
                return true;
            });
            return;
        }

        PlayerEntity target = getTarget();
        Candidates candidates = new Candidates();
        if (enderChest.getValue()) enderChestStage(candidates);
        if (burrow.getValue()) burrowStage(candidates, target);
        if (fish.getValue()) fishStage(candidates, target);
        if (feet.getValue()) feetStage(candidates, target);
        if (self.getValue() || antiCrawl.getValue()) selfStage(candidates, target);
        if ((head.getValue() || face.getValue()) && !cityOnly.getValue()) headStage(candidates, target);
        if (terrain.getValue()) terrainStage(candidates, target);

        dropStale(target, candidates);

        SpeedMineModule.Action primary = sm.getPrimary();
        boolean overrides = candidates.priority >= OVERRIDE;
        if (primary != null && primary.getPriority() == 1 && primary.isMining() && !mc.world.getBlockState(primary.getPosition()).isAir() && !overrides) return;

        if (!overrides && (digging(primary) || sm.isRebreakLocked())) return;

        if (candidates.primary == null) {
            if (candidates.secondary == null) return;
            if (isSecondary(candidates.secondary)) return;
            candidates.swap();
        }

        boolean doubleMine = sm.isDoubleMine();
        if (doubleMine && (sm.getSecondary() != null || recentSecondaryPending()) && !overrides) return;

        BlockPos main = candidates.primary;
        BlockPos extra = candidates.secondary;
        if (primary != null && main.equals(primary.getPosition())) return;

        if (doubleMine && extra != null && !extra.equals(main)) {
            boolean mainInside = insideTarget(main, target), extraInside = insideTarget(extra, target);
            if (sequence.getValue().equals("Surround") ? (extraInside && !mainInside) : (mainInside && !extraInside)) {
                BlockPos swap = main;
                main = extra;
                extra = swap;
            }
            if (mine(extra)) {
                pending.add(new Pending(main, ticks + (movementSync() ? 2 : 1)));
                return;
            }
        }
        mine(main);
    }

    private static final class Candidates {
        BlockPos primary, secondary;
        int priority;

        void offer(int incoming, Consumer<Candidates> update) {
            if (priority > incoming && primary != null) return;
            update.accept(this);
            priority = incoming;
        }

        void swap() {
            BlockPos previous = primary;
            primary = secondary;
            secondary = previous;
        }
    }

    private void enderChestStage(Candidates candidates) {
        double range = chestRange.getValue().doubleValue();
        int radius = MathHelper.ceil(range);
        Vec3d eyes = mc.player.getEyePos();
        BlockPos center = BlockPos.ofFloored(eyes);
        BlockPos selected = null;
        for (BlockPos position : BlockPos.iterate(center.add(-radius, -radius, -radius), center.add(radius, radius, radius))) {
            if (WorldUtils.getBlock(position) != Blocks.ENDER_CHEST) continue;
            if (eyes.distanceTo(Vec3d.ofCenter(position)) > range || !canMine(position)) continue;
            if (selected != null && !isMining(position)) continue;
            selected = position.toImmutable();
        }
        if (selected == null) return;
        BlockPos chest = selected;
        candidates.offer(5, c -> c.primary = chest);
    }

    private void burrowStage(Candidates candidates, PlayerEntity target) {
        if (target == null) return;
        boolean extra = sm.isDoubleMine();
        double y = Math.round(target.getY()) + 0.5;
        Box feet = target.getBoundingBox().expand(-INSET, 0.0, -INSET).withMinY(y).withMaxY(y);
        List<BlockPos> blocks = BlockPos.stream(feet).map(BlockPos::toImmutable)
                .filter(this::canMine)
                .filter(position -> {
                    Block block = WorldUtils.getBlock(position);
                    if (block == Blocks.COBWEB) return web.getValue();
                    return block.getBlastResistance() >= 600.0f && block != Blocks.ANVIL;
                })
                .sorted(Comparator.comparingDouble(position -> isMining(position) ? -1.0 : target.squaredDistanceTo(Vec3d.ofCenter(position))))
                .toList();
        if (blocks.isEmpty()) return;

        candidates.offer(extra && blocks.size() == 1 ? 500 : 700, c -> {
            if (!extra) c.primary = blocks.getFirst();
            else if (blocks.size() == 1) c.secondary = blocks.getFirst();
            else {
                c.primary = blocks.getFirst();
                c.secondary = blocks.get(1);
            }
        });
    }

    private void fishStage(Candidates candidates, PlayerEntity target) {
        if (target == null) return;
        if (BlockPos.stream(footprint(target)).count() != 1) return;
        BlockPos position = roundedPosition(target);
        if (mc.world.getBlockState(position).isAir() || breakable(position)) return;
        BlockPos below = position.down();
        if (!canMine(below)) return;
        candidates.offer(400, c -> {
            if (sm.isDoubleMine()) c.secondary = below;
            else c.primary = below;
        });
    }

    private void feetStage(Candidates candidates, PlayerEntity target) {
        if (target == null || airborne(target)) return;
        if (holeCheck.getValue() && !HoleUtils.isPlayerInHole(target)) return;

        boolean fishing = fish.getValue() && candidates.priority == 400 && candidates.secondary != null;
        if (standsOnBedrock(target) && !fishing) return;

        Set<BlockPos> protectedBlocks = protectedBlocks();
        BlockPos targetPosition = BlockPos.ofFloored(target.getEntityPos());
        Block base = WorldUtils.getBlock(targetPosition);
        boolean suppressRetain = base.getBlastResistance() >= 600.0f && base != Blocks.AIR && !isMining(targetPosition)
                && (recentSecondaryTime == -1L || System.currentTimeMillis() - recentSecondaryTime >= 500L);

        List<BlockPos> surround = new ArrayList<>(HoleUtils.getFeetPositions(target, true, false, true));
        boolean lowerBedrock = fishing && surround.stream().allMatch(position -> WorldUtils.getBlock(position) == Blocks.BEDROCK);
        BlockPos selfPosition = roundedPosition(mc.player);
        int selfCells = occupiedCells(mc.player).size();
        Set<BlockPos> ownSurround = HoleUtils.getFeetPositions(mc.player, true, false, true);
        double maxRangeSq = MathHelper.square(sm.range.getMaximum().doubleValue());

        surround.sort(Comparator.comparingDouble(position -> {
            if (mc.player.getBoundingBox().intersects(new Box(position))) return 9999.0;
            if (ownSurround.contains(position)) return 999.0;
            boolean crystalSpot = false;
            for (Direction direction : Direction.Type.HORIZONTAL) {
                if (canPlaceCrystal(position.offset(direction).down())) {
                    crystalSpot = true;
                    break;
                }
            }
            double score = position.getSquaredDistance(mc.player.getEntityPos());
            if (!crystalSpot) score += maxRangeSq;
            return -score;
        }));

        BlockPos main = null, extra = null;
        boolean mainMining = false;
        boolean instantRebreak = sm.instant.getValue();
        for (BlockPos position : surround) {
            if (!isMining(position) && selfPosition.down().equals(position) && selfCells == 1) continue;
            if (lowerBedrock) position = position.down();

            boolean retain = !suppressRetain && mc.world.getBlockState(position).isAir() && isPrimary(position);
            if (!hasCrystalSpot(position) && !fishing && !instantRebreak) continue;
            if (!canMineFeet(position, protectedBlocks) && !retain) continue;

            if (isMining(position)) {
                main = position;
                mainMining = true;
            } else if (mainMining) {
                extra = position;
            } else {
                extra = main;
                main = position;
            }
        }
        if (main == null) return;

        if (corners.getValue() && sm.isDoubleMine()) {
            BlockPos corner = cornerOf(target, main, protectedBlocks);
            if (corner != null) extra = corner;
        }

        BlockPos pickedMain = main, pickedExtra = extra;
        candidates.offer(600, c -> {
            if (c.secondary == null) c.secondary = pickedExtra;
            c.primary = pickedMain;
        });
    }

    private Set<BlockPos> protectedBlocks() {
        Set<BlockPos> blocks = new HashSet<>();
        boolean crawling = lowProfile();
        boolean movementSync = movementSync();
        for (BlockPos position : HoleUtils.getFeetPositions(mc.player, true, false, true)) {
            blocks.add(position);
            if (!crawling && !movementSync) blocks.add(position.up());
        }
        if (movementSync) return blocks;
        int up = MathHelper.ceil(mc.player.getBoundingBox().getLengthY());
        for (BlockPos cell : occupiedCells(mc.player)) blocks.add(cell.up(up));
        return blocks;
    }

    private boolean canMineFeet(BlockPos position, Set<BlockPos> protectedBlocks) {
        if (safe.getValue() && protectedBlocks.contains(position)) return false;
        Box box = mc.player.getBoundingBox().withMaxY(mc.player.getY()).expand(0.0, 1.0, 0.0);
        List<BlockPos> burrowed = BlockPos.stream(box).map(BlockPos::toImmutable)
                .filter(cell -> WorldUtils.getBlock(cell).getBlastResistance() >= 600.0f).toList();
        if (burrowed.size() <= 2 && burrowed.contains(position)) return false;
        return WorldUtils.getBlock(position) != Blocks.COBWEB && canMine(position);
    }

    private boolean hasCrystalSpot(BlockPos position) {
        if (canPlaceCrystal(position)) return true;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (canPlaceCrystal(position.offset(direction).down())) return true;
        }
        return false;
    }

    private BlockPos cornerOf(PlayerEntity target, BlockPos city, Set<BlockPos> protectedBlocks) {
        BlockPos targetPosition = BlockPos.ofFloored(target.getEntityPos());
        Direction side = null;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (targetPosition.offset(direction).equals(city)) {
                side = direction;
                break;
            }
        }
        if (side == null) return null;
        for (Direction direction : new Direction[]{side.rotateYClockwise(), side.rotateYCounterclockwise()}) {
            BlockPos corner = city.offset(direction);
            if (mc.world.getBlockState(corner).isAir() && bedrockOrObsidian(corner.down())) return null;
            if (canMineFeet(corner, protectedBlocks) && bedrockOrObsidian(corner.down())) return corner;
        }
        return null;
    }

    private boolean standsOnBedrock(PlayerEntity target) {
        Box feet = target.getBoundingBox().withMaxY(target.getY());
        return BlockPos.stream(feet).allMatch(position -> WorldUtils.getBlock(position) == Blocks.BEDROCK);
    }

    private void selfStage(Candidates candidates, PlayerEntity target) {
        if (mc.player.isGliding()) return;
        BlockPos faceBlock = antiCrawl.getValue() ? faceBlock() : null;
        boolean crawling = lowProfile();
        boolean stuck = crawling || faceBlock != null;
        if (stuck ? !antiCrawl.getValue() : !self.getValue() || target == null) return;

        if (self.getValue() && selfInstant.getValue() && selfRecent != null && System.currentTimeMillis() - selfRecentTime < 150L) {
            BlockPos recent = selfRecent;
            candidates.offer(selfRecentPriority, c -> c.primary = recent);
        }

        BlockPos position = selfBlock(faceBlock, crawling);
        if (position == null) return;
        int priority = stuck ? OVERRIDE : 100;
        if (self.getValue() && tryCrawl.getValue() && !crawling && position.getY() == Math.round(mc.player.getY())) priority = 1000;
        int picked = priority;
        candidates.offer(picked, c -> c.primary = position);
        selfRecent = position;
        selfRecentPriority = picked;
        selfRecentTime = System.currentTimeMillis();
    }

    private BlockPos selfBlock(BlockPos faceBlock, boolean crawling) {
        BlockPos base = roundedPosition(mc.player);
        BlockPos below = base.down();
        BlockPos overhead = BlockPos.ofFloored(mc.player.getEntityPos().add(0.0, mc.player.getBoundingBox().getLengthY() - INSET, 0.0)).up();
        boolean tryCrawling = self.getValue() && tryCrawl.getValue();

        if (tryCrawling && !crawling && canMine(base) && (isMining(base) || groundedHard(overhead.down()))) return base;

        if (self.getValue() && canMine(below) && (isMining(below) || crawling) && mineDown()) return below;

        if (crawling && closedUpperTrapdoor(overhead.down())) overhead = overhead.down();
        if (tryCrawling && crawling) return null;
        if (faceBlock != null) overhead = faceBlock;
        return canMine(overhead) ? overhead : null;
    }

    private boolean groundedHard(BlockPos position) {
        return mc.world.getBlockState(position).getBlock().getBlastResistance() >= 600.0f && mc.player.isOnGround();
    }

    private BlockPos faceBlock() {
        if (lowProfile()) return null;
        BlockPos eyes = BlockPos.ofFloored(mc.player.getEyePos());
        BlockState state = mc.world.getBlockState(eyes);
        if (state.isAir() || state.getBlock().getBlastResistance() < 600.0f || !canMine(eyes)) return null;
        BlockPos feet = mc.player.getBlockPos();
        if (!mc.world.getBlockState(feet).isAir() && !canMine(feet)) return null;
        return eyes;
    }

    private boolean mineDown() {
        if (!down.getValue()) return false;
        if (downPriority.getValue()) return true;
        BlockPos base = roundedPosition(mc.player);
        if (mc.world.getBlockState(base.down(2)).isAir()) return false;
        for (BlockPos position : HoleUtils.getFeetPositions(mc.player, true, false, true)) {
            BlockState state = mc.world.getBlockState(position.down());
            if (state.isAir() || state.getBlock().getBlastResistance() < 600.0f) return false;
        }
        return true;
    }

    private boolean closedUpperTrapdoor(BlockPos position) {
        BlockState state = mc.world.getBlockState(position);
        if (!(state.getBlock() instanceof TrapdoorBlock)) return false;
        return !state.get(TrapdoorBlock.OPEN) && state.get(TrapdoorBlock.HALF) != BlockHalf.BOTTOM;
    }

    private void headStage(Candidates candidates, PlayerEntity target) {
        if (target == null || airborne(target)) return;
        boolean crawlingTarget = target.isCrawling();
        if (crawlingTarget && !inCrawl.getValue()) return;

        boolean alternate = head.getValue() && twob2t();
        boolean extra = sm.isDoubleMine();
        Box bounds = target.getBoundingBox();
        boolean onBedrock = BlockPos.stream(bounds.withMaxY(bounds.minY)).anyMatch(position -> WorldUtils.getBlock(position) == Blocks.BEDROCK);

        if (!alternate) {
            Box inside = bounds.contract(0.05);
            boolean phased = BlockPos.stream(inside).anyMatch(position -> {
                Block block = WorldUtils.getBlock(position);
                return block != Blocks.AIR && block.getBlastResistance() >= 600.0f;
            });
            if (phased) return;
        }

        BlockPos main = null, side = null;
        if (head.getValue()) {
            Box above = bounds.withMaxY(bounds.maxY + 0.5);
            main = BlockPos.stream(above).map(BlockPos::toImmutable).filter(position -> validHeadBlock(position, alternate))
                    .max(Comparator.comparing(this::isMining)).orElse(null);
        }
        if (face.getValue()) {
            side = HoleUtils.getFeetPositions(target, true, false, true).stream().map(BlockPos::up).filter(position -> validHeadBlock(position, alternate))
                    .max(Comparator.comparingInt(position -> isMining(position) ? 2
                            : position.equals(candidates.primary) || position.equals(candidates.secondary) ? 1 : 0))
                    .orElse(null);
        }
        if (alternate && onBedrock) side = null;
        if (main == null && side != null && !alternate) {
            main = side;
            side = null;
        }
        if (main == null) return;

        BlockPos picked = main, pickedSide = side;
        if (alternate) {
            int priority = onBedrock ? (extra ? 900 : 1000) : (extra ? 850 : 950);
            candidates.offer(priority, c -> {
                if (extra && pickedSide != null) {
                    c.secondary = picked;
                    c.primary = pickedSide;
                } else {
                    c.primary = picked;
                }
            });
            return;
        }

        int priority = isMining(main) ? 650 : 599;
        if (trapOverride.getValue() && head.getValue()) {
            AutoTrapModule autoTrap = Anatorius.MODULE_MANAGER.getModule(AutoTrapModule.class);
            if (autoTrap != null && autoTrap.isToggled()) priority = 650;
        }
        if (crawlingTarget && occupiedCells(target).size() == 1 && mc.world.getBlockState(mc.player.getBlockPos()).isAir()) {
            candidates.offer(650, c -> c.secondary = picked);
            return;
        }
        candidates.offer(priority, c -> c.primary = picked);
    }

    private boolean validHeadBlock(BlockPos position, boolean alternate) {
        Box block = new Box(position);
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.getBoundingBox().intersects(block)) return false;
        }
        boolean allowed = alternate || WorldUtils.getBlock(position) == Blocks.OBSIDIAN;
        if (!(canMine(position) && allowed) && !isMining(position)) return false;
        return alternate || mc.world.getBlockState(position.up()).isAir();
    }

    private void terrainStage(Candidates candidates, PlayerEntity target) {
        if (target == null || airborne(target)) return;
        BlockPos targetPosition = BlockPos.ofFloored(target.getEntityPos());
        Block base = WorldUtils.getBlock(targetPosition);
        boolean resistantBase = base.getBlastResistance() >= 600.0f && base != Blocks.AIR;
        Set<BlockPos> surround = HoleUtils.getFeetPositions(target, true, false, true);
        boolean softSurround = surround.stream().anyMatch(position -> !bedrockOrObsidian(position));
        if (resistantBase && !softSurround) return;
        if (candidates.primary == null || candidates.primary.getY() != targetPosition.getY()) return;

        BlockPos reference = softSurround ? candidates.secondary : candidates.primary;
        BlockPos picked = terrainBlock(target, targetPosition, surround, reference);
        if (picked == null) return;
        candidates.offer(softSurround ? 700 : 600, c -> {
            if (softSurround) c.primary = picked;
            else c.secondary = picked;
        });
    }

    private BlockPos terrainBlock(PlayerEntity target, BlockPos targetPosition, Set<BlockPos> surround, BlockPos reference) {
        BlockPos best = null;
        double bestScore = -1.0;
        double bestDamage = 0.0;
        boolean bestMining = false;
        for (BlockPos mutable : BlockPos.iterate(targetPosition.add(-2, -1, -2), targetPosition.add(2, -1, 2))) {
            BlockPos position = mutable.toImmutable();
            if (surround.contains(position.up())) continue;
            boolean replaceable = WorldUtils.isReplaceable(position);
            if (!replaceable && !canMine(position)) continue;
            if (!clearColumn(position.up()) && !mc.world.getBlockState(position).isAir()) continue;

            double damage = DamageUtils.getCrystalDamage(target, target.getBoundingBox(), position, reference, false);
            if (replaceable && damage >= 6.0) return null;
            boolean mining = isSecondary(position);
            double score = mining ? Double.MAX_VALUE : damage;
            if (score > bestScore) {
                bestScore = score;
                best = position;
                bestDamage = damage;
                bestMining = mining;
            }
        }
        if (best == null || mc.world.getBlockState(best).isAir()) return null;
        if (!bestMining && bestDamage <= 4.0) return null;
        if (bedrockOrObsidian(best) || WorldUtils.isReplaceable(best)) return null;
        return best;
    }

    private boolean clearColumn(BlockPos position) {
        if (!mc.world.getBlockState(position.up()).isAir()) return false;
        Box column = new Box(position).stretch(0.0, 1.0, 0.0);
        for (Entity entity : mc.world.getEntities()) {
            if (entity.isRemoved() || !entity.isAlive() || entity instanceof EndCrystalEntity) continue;
            if (entity.getBoundingBox().intersects(column)) return false;
        }
        return true;
    }

    private void dropStale(PlayerEntity target, Candidates candidates) {
        SpeedMineModule.Action primary = sm.getPrimary();
        SpeedMineModule.Action secondary = sm.getSecondary();
        if (primary == null && secondary == null) return;

        Set<BlockPos> wanted = wantedBlocks(target, candidates);
        primaryStale = isStale(primary, wanted) ? primaryStale + 1 : 0;
        secondaryStale = isStale(secondary, wanted) ? secondaryStale + 1 : 0;

        if (primaryStale >= STALE_TICKS) {
            sm.abort(primary);
            primaryStale = 0;
        }
        if (secondaryStale >= STALE_TICKS) {
            sm.abort(secondary);
            secondaryStale = 0;
        }
    }

    private boolean isStale(SpeedMineModule.Action action, Set<BlockPos> wanted) {
        if (action == null || action.getPriority() != 0) return false;
        BlockPos position = action.getPosition();
        if (enderChest.getValue() && WorldUtils.getBlock(position) == Blocks.ENDER_CHEST) return false;
        return !wanted.contains(position);
    }

    private Set<BlockPos> wantedBlocks(PlayerEntity target, Candidates candidates) {
        Set<BlockPos> wanted = new HashSet<>();
        if (candidates.primary != null) wanted.add(candidates.primary);
        if (candidates.secondary != null) wanted.add(candidates.secondary);

        if (self.getValue() || antiCrawl.getValue()) {
            BlockPos base = roundedPosition(mc.player);
            for (int y = -1; y <= 2; y++) wanted.add(base.up(y));
            wanted.add(BlockPos.ofFloored(mc.player.getEyePos()));
        }
        if (target == null) return wanted;

        BlockPos feet = target.getBlockPos();
        for (int y = -1; y <= 2; y++) wanted.add(feet.up(y));
        for (BlockPos position : HoleUtils.getInsidePositions(target)) {
            wanted.add(position);
            wanted.add(position.down());
            wanted.add(position.up(2));
        }
        for (BlockPos position : BlockPos.stream(target.getBoundingBox().expand(0.0, 0.5, 0.0)).map(BlockPos::toImmutable).toList()) wanted.add(position);
        for (BlockPos position : HoleUtils.getFeetPositions(target, true, false, true)) {
            wanted.add(position);
            wanted.add(position.up());
            wanted.add(position.down());
            for (Direction direction : Direction.Type.HORIZONTAL) wanted.add(position.offset(direction));
        }
        if (terrain.getValue()) {
            for (BlockPos position : BlockPos.iterate(feet.add(-2, -1, -2), feet.add(2, -1, 2))) wanted.add(position.toImmutable());
        }
        return wanted;
    }

    private PlayerEntity getTarget() {
        SpeedMineModule.Action primary = sm.getPrimary();
        if (currentTarget != null && isValidTarget(currentTarget) && primary != null && primary.getPriority() == 0) return currentTarget;

        String mode = targetMode.getValue();
        PlayerEntity best = null;
        double bestKey = Double.MAX_VALUE;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!isValidTarget(player)) continue;
            double key = switch (mode) {
                case "Health" -> player.getHealth() + player.getAbsorptionAmount();
                case "Angle" -> {
                    Vec3d to = player.getEntityPos().subtract(mc.player.getEntityPos());
                    float yaw = (float) Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0f;
                    yield Math.abs(MathHelper.wrapDegrees(yaw - mc.player.getYaw()));
                }
                default -> mc.player.squaredDistanceTo(player);
            };
            if (key < bestKey) {
                bestKey = key;
                best = player;
            }
        }
        currentTarget = best;
        return best;
    }

    private boolean isValidTarget(PlayerEntity player) {
        if (player == mc.player) return false;
        if (!player.isAlive() || player.getHealth() <= 0.0f || player.isRemoved()) return false;
        if (Anatorius.FRIEND_MANAGER.contains(player.getName().getString())) return false;
        if (ignoreNaked.getValue() && naked(player)) return false;
        return mc.player.squaredDistanceTo(player) <= MathHelper.square(targetRange.getValue().doubleValue());
    }

    private static boolean naked(PlayerEntity player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!player.getEquippedStack(slot).isEmpty()) return false;
        }
        return true;
    }

    private boolean mine(BlockPos position) {
        if (!canMine(position) && !isMining(position)) return false;
        if (rotate.getValue()) lookAt(position);
        if (creative()) return creativeBreak(position);
        return sm.handle(position, 0);
    }

    private void lookAt(BlockPos position) {
        Vec3d point = Vec3d.ofCenter(position);
        if (raytrace.getValue()) {
            Vec3d visiblePoint = visiblePoint(position);
            if (visiblePoint != null) point = visiblePoint;
        }
        Anatorius.ROTATION_MANAGER.rotate(RotationUtils.getRotations(point, false), this);
    }

    private boolean creative() {
        return mc.interactionManager != null && mc.interactionManager.getCurrentGameMode() == GameMode.CREATIVE;
    }

    private boolean creativeBreak(BlockPos position) {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        Direction direction = WorldUtils.getClosestDirection(position, antiCheat != null && antiCheat.strictDirection.getValue());
        BlockState state = mc.world.getBlockState(position);
        int selected = mc.player.getInventory().getSelectedSlot();
        int held = Anatorius.POSITION_MANAGER.getServerSlot();
        int slot = -1;
        if (!mc.player.getInventory().getStack(held).canMine(state, mc.world, position, mc.player)) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).canMine(state, mc.world, position, mc.player)) {
                    slot = i;
                    break;
                }
            }
            if (slot == -1) return false;
            InventoryUtils.switchSlot("Silent", slot, selected);
        }
        boolean started = mc.interactionManager.attackBlock(position, direction);
        if (slot != -1) InventoryUtils.switchBack("Silent", slot, selected);
        return started;
    }

    private boolean canMine(BlockPos position) {
        if (!breakable(position)) return false;
        if (position.equals(recentSecondary) && System.currentTimeMillis() - recentSecondaryTime < 200L) return false;
        if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(position)) > reachSq) return false;
        Block block = WorldUtils.getBlock(position);
        if (!whitelist.getWhitelist().isEmpty() && !whitelist.isWhitelistContains(block)) return false;
        if (!sm.whitelist.getWhitelist().isEmpty() && !sm.whitelist.isWhitelistContains(block)) return false;
        if (assistant.getValue() && othersBreaking.containsKey(position)) return false;
        return !raytrace.getValue() || visible(position);
    }

    private void trackSecondary() {
        SpeedMineModule.Action secondary = sm.isDoubleMine() ? sm.getSecondary() : null;
        BlockPos position = secondary == null ? null : secondary.getPosition();
        if (lastSecondary != null && !lastSecondary.equals(position)) {
            recentSecondary = lastSecondary;
            recentSecondaryTime = System.currentTimeMillis();
        }
        lastSecondary = position;
    }

    private boolean recentSecondaryPending() {
        return recentSecondary != null && System.currentTimeMillis() - recentSecondaryTime < 200L
                && !mc.world.getBlockState(recentSecondary).isAir();
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPacket() instanceof BlockBreakingProgressS2CPacket packet) || mc.player == null) return;
        if (packet.getEntityId() == mc.player.getId()) return;
        BlockPos position = packet.getPos().toImmutable();
        int progress = packet.getProgress();
        if (progress >= 0 && progress < 10) othersBreaking.put(position, System.currentTimeMillis());
        else othersBreaking.remove(position);
    }

    private static boolean movementSync() {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        return antiCheat != null && antiCheat.movementSync.getValue();
    }

    private static boolean twob2t() {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        return antiCheat != null && antiCheat.preset.getValue().equalsIgnoreCase("2b2t");
    }

    private boolean breakable(BlockPos position) {
        BlockState state = mc.world.getBlockState(position);
        if (state.isAir() || state.getBlock() instanceof net.minecraft.block.FluidBlock) return false;
        return state.getHardness(mc.world, position) >= 0.0f;
    }

    private boolean visible(BlockPos position) {
        return WorldUtils.canSee(position) || visiblePoint(position) != null;
    }

    private Vec3d visiblePoint(BlockPos position) {
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = 0; z <= 1; z++) {
                    double px = position.getX() + 0.1 + x * 0.8, py = position.getY() + 0.1 + y * 0.8, pz = position.getZ() + 0.1 + z * 0.8;
                    if (WorldUtils.canSee(px, py, pz)) return new Vec3d(px, py, pz);
                }
            }
        }
        return null;
    }

    private boolean canPlaceCrystal(BlockPos base) {
        return bedrockOrObsidian(base) && WorldUtils.isCrystalPlaceable(base.up());
    }

    private static boolean bedrockOrObsidian(BlockPos position) {
        Block block = WorldUtils.getBlock(position);
        return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK;
    }

    private boolean airborne(Entity entity) {
        Box box = entity.getBoundingBox().stretch(0.0, -0.1, 0.0);
        return !mc.world.getBlockCollisions(entity, box).iterator().hasNext();
    }

    private boolean lowProfile() {
        return mc.player.getBoundingBox().getLengthY() < 1.0;
    }

    private boolean digging(SpeedMineModule.Action action) {
        return action != null && action.getPriority() == 0 && !mc.world.getBlockState(action.getPosition()).isAir();
    }

    private boolean isMining(BlockPos position) {
        return sm.isBeingBroken(position);
    }

    private boolean isPrimary(BlockPos position) {
        SpeedMineModule.Action primary = sm.getPrimary();
        return primary != null && primary.getPosition().equals(position);
    }

    private boolean isSecondary(BlockPos position) {
        SpeedMineModule.Action secondary = sm.getSecondary();
        return secondary != null && secondary.getPosition().equals(position);
    }

    private boolean insideTarget(BlockPos position, PlayerEntity target) {
        return target != null && target.getBoundingBox().intersects(new Box(position));
    }

    private static BlockPos roundedPosition(Entity entity) {
        return BlockPos.ofFloored(entity.getX(), Math.round(entity.getY()), entity.getZ());
    }

    private Box footprint(PlayerEntity player) {
        return player.getBoundingBox().expand(-INSET, 0.0, -INSET).withMaxY(player.getY());
    }

    private Set<BlockPos> occupiedCells(PlayerEntity player) {
        Set<BlockPos> cells = new HashSet<>();
        int y = roundedPosition(player).getY();
        Box box = player.getBoundingBox().contract(INSET);
        for (double x : new double[]{box.minX, box.maxX}) {
            for (double z : new double[]{box.minZ, box.maxZ}) cells.add(BlockPos.ofFloored(x, y, z));
        }
        return cells;
    }
}
