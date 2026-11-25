package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.settings.impl.WhitelistSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

import java.util.HashSet;
import java.util.Set;

@RegisterModule(name = "Xray", description = "See ores through blocks. Exposed mode filters out fake anti-xray ores.", category = Module.Category.VISUALS)
public class XrayModule extends Module {
    public ModeSetting mode = new ModeSetting("Mode", "Normal: every chosen block. Exposed: only the ones touching air or liquid (filters anti-xray fakes).", "Normal", new String[]{"Normal", "Exposed"});
    public WhitelistSetting blocks = new WhitelistSetting("Blocks", "The blocks to show. Empty = all ores, ancient debris and spawners.", WhitelistSetting.Type.BLOCKS);
    public BooleanSetting fluids = new BooleanSetting("Fluids", "Also shows water and lava.", false);
    public NumberSetting range = new NumberSetting("Range", "Only shows the chosen blocks this many blocks around you. Unlimited = every loaded chunk.", 256, 8, 256).withMaxLabel("Unlimited");

    private static final double FOLLOW_STEP = 8.0;

    private static Set<Block> defaultBlocks() {
        return Set.of(
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.ANCIENT_DEBRIS,
            Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE, Blocks.RAW_GOLD_BLOCK,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.RAW_IRON_BLOCK,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
            Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
            Blocks.NETHER_QUARTZ_ORE, Blocks.SPAWNER);
    }

    private static volatile boolean active;
    private static volatile boolean exposedOnly;
    private static volatile boolean showFluids;
    private static volatile Set<Block> shown = Set.of();
    private static volatile double rangeSq = -1, centerX, centerY, centerZ;
    private String builtFor = "";

    @Override
    public void onEnable() {
        publish();
        active = true;
        reload();
    }

    @Override
    public void onDisable() {
        active = false;
        builtFor = "";
        reload();
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (!signature().equals(builtFor)) {
            publish();
            reload();
            return;
        }
        if (rangeSq >= 0 && mc.player != null) followPlayer();
    }

    private void followPlayer() {
        double x = mc.player.getX(), y = mc.player.getEyeY(), z = mc.player.getZ();
        double ox = centerX, oy = centerY, oz = centerZ;
        double moved = Math.sqrt((x - ox) * (x - ox) + (y - oy) * (y - oy) + (z - oz) * (z - oz));
        if (moved < FOLLOW_STEP) return;
        centerX = x;
        centerY = y;
        centerZ = z;

        double r = Math.sqrt(rangeSq);
        double band = moved + 14.0;
        int minSX = (int) Math.floor((Math.min(x, ox) - r - 16) / 16), maxSX = (int) Math.floor((Math.max(x, ox) + r + 16) / 16);
        int minSZ = (int) Math.floor((Math.min(z, oz) - r - 16) / 16), maxSZ = (int) Math.floor((Math.max(z, oz) + r + 16) / 16);
        int minSY = Math.max(mc.world.getBottomSectionCoord(), (int) Math.floor((Math.min(y, oy) - r - 16) / 16));
        int maxSY = Math.min(mc.world.getTopSectionCoord(), (int) Math.floor((Math.max(y, oy) + r + 16) / 16));
        for (int sx = minSX; sx <= maxSX; sx++) {
            for (int sz = minSZ; sz <= maxSZ; sz++) {
                for (int sy = minSY; sy <= maxSY; sy++) {
                    double cx = sx * 16 + 8, cy = sy * 16 + 8, cz = sz * 16 + 8;
                    double dNew = Math.sqrt((cx - x) * (cx - x) + (cy - y) * (cy - y) + (cz - z) * (cz - z));
                    double dOld = Math.sqrt((cx - ox) * (cx - ox) + (cy - oy) * (cy - oy) + (cz - oz) * (cz - oz));
                    if (Math.abs(dNew - r) <= band || Math.abs(dOld - r) <= band) mc.worldRenderer.scheduleChunkRender(sx, sy, sz);
                }
            }
        }
    }

    private String signature() {
        return mode.getValue() + "|" + fluids.getValue() + "|" + range.getValue() + "|" + blocks.getWhitelistIds();
    }

    private void publish() {
        Set<Block> chosen = new HashSet<>();
        for (Object object : blocks.getWhitelist()) if (object instanceof Block block) chosen.add(block);
        shown = chosen.isEmpty() ? defaultBlocks() : Set.copyOf(chosen);
        exposedOnly = mode.getValue().equals("Exposed");
        showFluids = fluids.getValue();
        double r = range.getValue().doubleValue();
        if (mc.player != null) {
            centerX = mc.player.getX();
            centerY = mc.player.getEyeY();
            centerZ = mc.player.getZ();
        }
        rangeSq = range.isAtMaxLabel() ? -1 : r * r;
        builtFor = signature();
    }

    private void reload() {
        if (mc.worldRenderer != null && mc.world != null) mc.worldRenderer.reload();
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean showsFluids() {
        return showFluids;
    }

    public static boolean isShown(BlockState state) {
        return shown.contains(state.getBlock());
    }

    public static BlockState filter(BlockState state, BlockPos pos) {
        if (shouldRender(state, pos)) return state;
        if (showFluids && !state.getFluidState().isEmpty()) return state.getFluidState().getBlockState();
        return net.minecraft.block.Blocks.AIR.getDefaultState();
    }

    public static boolean shouldRender(BlockState state, BlockPos pos) {
        if (!isShown(state)) return false;
        double limit = rangeSq;
        if (limit >= 0) {
            double dx = pos.getX() + 0.5 - centerX, dy = pos.getY() + 0.5 - centerY, dz = pos.getZ() + 0.5 - centerZ;
            if (dx * dx + dy * dy + dz * dz > limit) return false;
        }
        if (!exposedOnly) return true;
        BlockView world = mc.world;
        if (world == null) return true;
        BlockPos.Mutable neighbour = new BlockPos.Mutable();
        for (Direction direction : Direction.values()) {
            BlockState other = world.getBlockState(neighbour.set(pos, direction));
            if (!other.isOpaqueFullCube() || !other.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    @Override
    public String getMetaData() {
        return mode.getValue() + " " + range.getDisplayValue();
    }
}
