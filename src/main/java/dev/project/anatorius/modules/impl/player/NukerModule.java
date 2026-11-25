package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.AttackBlockEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.*;
import dev.project.anatorius.utils.graphics.Renderer3D;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.NetworkUtils;
import dev.project.anatorius.utils.rotations.RotationUtils;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RegisterModule(name = "Nuker", description = "Breaks blocks around you.", category = Module.Category.PLAYER)
public class NukerModule extends Module {
    public ModeSetting mode = new ModeSetting("Mode", "Which blocks it breaks. WhiteList / BlackList: by the Blocks list. Shulkers: shulker boxes. Bed: beds. Click: the kind of block you last hit.", "Shulkers", new String[]{"WhiteList", "BlackList", "Shulkers", "Bed", "Click"});
    public WhitelistSetting blocks = new WhitelistSetting("Blocks", "The blocks WhiteList / BlackList go by.", new ModeSetting.Visibility(mode, "WhiteList", "BlackList"), WhitelistSetting.Type.BLOCKS);
    public BooleanSetting playerProximity = new BooleanSetting("PlayerProximity", "Shulkers: only those an enemy stands next to (6.5 blocks).", new ModeSetting.Visibility(mode, "Shulkers"), false);
    public ModeSetting shape = new ModeSetting("Shape", "Sphere: all around you. Tunnel: a tunnel the way you face. Queue: blocks you hit, one after another. Crosshair: the block you look at.", "Sphere", new String[]{"Sphere", "Tunnel", "Queue", "Crosshair"});
    public NumberSetting height = new NumberSetting("Height", "Tunnel: how high. 0: your own height.", new ModeSetting.Visibility(shape, "Tunnel"), 0, 0, 5);
    public ModeSetting sort = new ModeSetting("Sort", "Which block goes first.", new ModeSetting.Visibility(shape, "Sphere", "Tunnel", "Crosshair"), "Closest", new String[]{"Closest", "Furthest", "Top", "Bottom"});
    public NumberSetting blocksPerTick = new NumberSetting("BlocksPerTick", "BPT", "Blocks broken a tick (only instant ones after the first).", 1, 1, 10);
    public NumberSetting delay = new NumberSetting("Delay", "Milliseconds between breaks.", 50, 0, 500);
    public NumberSetting range = new NumberSetting("Range", "How far a block you can see may be.", 4.5, 0.0, 6.0);
    public NumberSetting wallRange = new NumberSetting("WallRange", "How far a block behind others may be.", 4.5, 0.0, 6.0);
    public BooleanSetting strictDirection = new BooleanSetting("StrictDirection", "Mines only from a face you can actually reach (strict anticheats).", false);
    public BooleanSetting rotate = new BooleanSetting("Rotate", "Looks at each block it mines.", false);
    public BooleanSetting creative = new BooleanSetting("Creative", "Breaks with start / stop packets right away (creative, or instant blocks).", false);
    public BooleanSetting flatten = new BooleanSetting("Flatten", "Leaves the blocks below your feet.", false);
    public ColorSetting color = new ColorSetting("Color", "Queue: the outline of the queued blocks.", new ModeSetting.Visibility(shape, "Queue"), new ColorSetting.Color(new Color(130, 202, 255, 255), false, false));

    private final List<BlockPos> queue = new ArrayList<>();
    private final List<long[]> recentBreaks = new ArrayList<>();
    private long lastBreakAt;
    private int minedThisTick;
    private boolean instantBreak, internalMining;
    private Block clickedBlock;

    @Override
    public void onDisable() {
        clickedBlock = null;
        queue.clear();
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        long now = System.currentTimeMillis();
        recentBreaks.removeIf(entry -> now > entry[1]);
        instantBreak = false;
        minedThisTick = 0;
        if (now - lastBreakAt < delay.getValue().longValue()) return;

        queue.removeIf(pos -> !mineable(pos) || distance(pos) > range.getValue().doubleValue());
        List<BlockPos> candidates = positions();
        if (!shape.getValue().equals("Queue")) candidates.sort(Comparator.comparingDouble(this::sortKey));

        for (BlockPos pos : candidates) {
            if (flatten.getValue() && pos.getY() < mc.player.getY()) continue;
            if (!canMine(pos)) continue;
            internalMining = true;
            try {
                mine(pos);
            } finally {
                internalMining = false;
            }
            lastBreakAt = now;
            if (minedThisTick >= blocksPerTick.getValue().intValue()) return;
            if (minedThisTick > 0 && !instantBreak && !creative.getValue()) return;
        }
    }

    @SubscribeEvent
    public void onAttackBlock(AttackBlockEvent event) {
        if (internalMining || mc.world == null) return;
        if (shape.getValue().equals("Queue")) {
            if (!queue.contains(event.getPosition())) queue.add(event.getPosition());
            event.setCancelled(true);
        } else if (mode.getValue().equals("Click") && mineable(event.getPosition())) {
            clickedBlock = mc.world.getBlockState(event.getPosition()).getBlock();
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (!shape.getValue().equals("Queue")) return;
        for (BlockPos pos : queue) Renderer3D.renderBoxOutline(event.getMatrices(), new Box(pos), color.getColor());
    }

    private List<BlockPos> positions() {
        List<BlockPos> list = new ArrayList<>();
        switch (shape.getValue()) {
            case "Tunnel" -> {
                int tunnelHeight = height.getValue().intValue() == 0 ? (int) Math.ceil(mc.player.getHeight()) : height.getValue().intValue();
                Direction facing = mc.player.getHorizontalFacing();
                for (int distance = 0; distance <= Math.ceil(range.getValue().doubleValue()); distance++) {
                    for (int up = 0; up < tunnelHeight; up++) list.add(mc.player.getBlockPos().up(up).offset(facing, distance));
                }
            }
            case "Queue" -> {
                if (!queue.isEmpty()) list.add(queue.getFirst());
            }
            case "Crosshair" -> {
                if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) list.add(hit.getBlockPos());
            }
            default -> {
                int r = (int) Math.ceil(range.getValue().doubleValue());
                BlockPos center = BlockPos.ofFloored(mc.player.getEyePos());
                for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) list.add(center.add(x, y, z));
            }
        }
        return list;
    }

    private double sortKey(BlockPos pos) {
        return switch (sort.getValue()) {
            case "Furthest" -> -mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
            case "Top" -> -pos.getY();
            case "Bottom" -> pos.getY();
            default -> mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
        };
    }

    private boolean mineable(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return !state.isAir() && state.getHardness(mc.world, pos) >= 0.0f && state.getFluidState().isEmpty();
    }

    private double distance(BlockPos pos) {
        return mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
    }

    private boolean canSee(BlockPos pos) {
        BlockHitResult hit = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), Vec3d.ofCenter(pos), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    public boolean canMine(BlockPos pos) {
        if (!mineable(pos)) return false;
        long key = pos.asLong();
        for (long[] entry : recentBreaks) if (entry[0] == key) return false;
        double limit = canSee(pos) ? range.getValue().doubleValue() : wallRange.getValue().doubleValue();
        if (distance(pos) > limit) return false;
        Block block = mc.world.getBlockState(pos).getBlock();
        return switch (mode.getValue()) {
            case "WhiteList" -> blocks.isWhitelistContains(block);
            case "BlackList" -> !blocks.isWhitelistContains(block);
            case "Bed" -> block instanceof BedBlock && mc.world.getBlockState(pos).get(BedBlock.PART) == BedPart.HEAD;
            case "Click" -> clickedBlock != null && block == clickedBlock;
            default -> block instanceof ShulkerBoxBlock && (!playerProximity.getValue() || enemyNear(pos));
        };
    }

    private boolean enemyNear(BlockPos pos) {
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || Anatorius.FRIEND_MANAGER.contains(player.getName().getString())) continue;
            if (player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) <= 6.5) return true;
        }
        return false;
    }

    private Direction strictFace(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        for (Direction direction : Direction.values()) {
            if (!mc.world.getBlockState(pos.offset(direction)).isReplaceable()) continue;
            Vec3d normal = Vec3d.of(direction.getVector());
            if (eye.subtract(center).dotProduct(normal) > 0.0) return direction;
        }
        return null;
    }

    private void mine(BlockPos pos) {
        Direction direction = Direction.UP;
        if (strictDirection.getValue()) {
            direction = strictFace(pos);
            if (direction == null) return;
        }
        if (rotate.getValue()) Anatorius.ROTATION_MANAGER.rotate(RotationUtils.getRotations(Vec3d.ofCenter(pos), false), this);
        minedThisTick++;

        BlockState state = mc.world.getBlockState(pos);
        int bestSlot = bestTool(state);
        int selected = mc.player.getInventory().getSelectedSlot();
        if (bestSlot != -1 && bestSlot != selected && !mc.player.isCreative()) InventoryUtils.switchSlot("Normal", bestSlot, selected);
        float progress = state.calcBlockBreakingDelta(mc.player, mc.world, pos);

        if (creative.getValue()) {
            Direction face = direction;
            NetworkUtils.sendSequencedPacket(id -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, face, id));
            if (progress < 1.0f) NetworkUtils.sendSequencedPacket(id -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, face, id));
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            recentBreaks.add(new long[]{pos.asLong(), System.currentTimeMillis() + 1000L});
        } else {
            instantBreak = progress >= 1.0f;
            mc.interactionManager.updateBlockBreakingProgress(pos, direction);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private int bestTool(BlockState state) {
        int best = -1;
        float bestSpeed = -1.0f;
        int selected = mc.player.getInventory().getSelectedSlot();
        for (int slot = 0; slot < 9; slot++) {
            var stack = mc.player.getInventory().getStack(slot);
            if (ToolSaverModule.active() && !ToolSaverModule.safeToUse(stack)) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed || (speed == bestSpeed && slot == selected)) {
                bestSpeed = speed;
                best = slot;
            }
        }
        return best;
    }

    @Override
    public String getMetaData() {
        return mode.getValue();
    }
}
