package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.graphics.ChunkOverlayRenderer;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@RegisterModule(name = "NewChunks", description = "Highlights chunks that are generated for the first time.", category = Module.Category.EXPLOIT)
public class NewChunksModule extends Module {
    public final ModeSetting quality = new ModeSetting("Quality", "Auto: High on an RTX card, Low otherwise.", "Auto", new String[]{"Auto", "Low", "High"});
    public final ModeSetting style = new ModeSetting("Style", "3D: glowing walls rising from the chunk borders. Flat: plain squares.", "3D", new String[]{"3D", "Flat"});
    public final NumberSetting wallHeight = new NumberSetting("WallHeight", "Height of the 3D walls, in blocks.", new ModeSetting.Visibility(style, "3D"), 6, 1, 32);
    public final NumberSetting range = new NumberSetting("Range", "How many chunks around you to draw.", 24, 4, 64);
    public final BooleanSetting followPlayer = new BooleanSetting("FollowPlayer", "Draw the squares at your feet height (otherwise at Height).", true);
    public final NumberSetting height = new NumberSetting("Height", "Y level of the squares when FollowPlayer is off.", 0, -64, 320);
    public final BooleanSetting throughWalls = new BooleanSetting("ThroughWalls", "Visible through terrain.", true);
    public final BooleanSetting showOld = new BooleanSetting("ShowOld", "Also mark chunks detected as old.", false);
    public final BooleanSetting overflow = new BooleanSetting("Overflow", "Also flag a fresh chunk that arrives already flooded with flowing liquid as new (generation overflow).", true);
    public final NumberSetting overflowBlocks = new NumberSetting("OverflowBlocks", "How many flowing-liquid blocks count as a flood.", new BooleanSetting.Visibility(overflow, true), 12, 2, 64);
    public final ColorSetting newColor = new ColorSetting("NewColor", "Colour of new chunks.", new ColorSetting.Color(new Color(255, 107, 122, 60), false, false));
    public final ColorSetting oldColor = new ColorSetting("OldColor", "Colour of old chunks.", new ColorSetting.Color(new Color(110, 231, 160, 40), false, false));

    private static final int MAX_CHUNKS = 60_000;
    private static final int MAX_QUEUED = 20_000;
    private static final int DATA_CHECKS_PER_TICK = 48;
    private static final Direction[] FLOW_NEIGHBOURS = {Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.UP};

    private final LongOpenHashSet newChunks = new LongOpenHashSet();
    private final LongOpenHashSet oldChunks = new LongOpenHashSet();
    private final ConcurrentLinkedQueue<Long> pendingData = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<long[]> pendingFlow = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();

    private final Long2LongOpenHashMap loadedAt = new Long2LongOpenHashMap();
    private final LongArrayFIFOQueue paletteChecks = new LongArrayFIFOQueue();
    private static final long FRESH_MS = 8_000;
    private long lastLoadPrune;

    private final ChunkOverlayRenderer overlay = new ChunkOverlayRenderer("anatorius newchunks");
    private ClientWorld lastWorld;
    private boolean dirty;
    private long lastBuild;
    private long lastCenter = Long.MIN_VALUE;
    private int lastSettingsHash;

    @Override
    public void onEnable() {
        clearAll();
    }

    @Override
    public void onDisable() {
        clearAll();
    }

    private void clearAll() {
        newChunks.clear();
        newChunks.trim();
        oldChunks.clear();
        oldChunks.trim();
        pendingData.clear();
        pendingFlow.clear();
        queued.set(0);
        loadedAt.clear();
        loadedAt.trim();
        paletteChecks.clear();
        paletteChecks.trim();
        overlay.close();
        lastWorld = null;
        lastCenter = Long.MIN_VALUE;
        dirty = true;
    }

    private ChunkOverlayRenderer.GpuTier tier() {
        return switch (quality.getValue()) {
            case "Low" -> ChunkOverlayRenderer.GpuTier.LOW;
            case "High" -> ChunkOverlayRenderer.GpuTier.HIGH;
            default -> ChunkOverlayRenderer.gpuTier();
        };
    }

    private static boolean isFlowing(BlockState state) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && !fluid.isStill();
    }

    private static int countFlowing(WorldChunk chunk, int cap) {
        int count = 0;
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty() || !section.getBlockStateContainer().hasAny(NewChunksModule::isFlowing)) continue;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        if (isFlowing(section.getBlockState(x, y, z)) && ++count >= cap) return count;
                    }
                }
            }
        }
        return count;
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (queued.get() > MAX_QUEUED) return;
        if (event.getPacket() instanceof ChunkDataS2CPacket p) {
            pendingData.add(ChunkPos.toLong(p.getChunkX(), p.getChunkZ()));
            queued.incrementAndGet();
        } else if (event.getPacket() instanceof ChunkDeltaUpdateS2CPacket p) {
            p.visitUpdates((pos, state) -> {
                if (isFlowing(state)) { pendingFlow.add(new long[]{pos.asLong()}); queued.incrementAndGet(); }
            });
        } else if (event.getPacket() instanceof BlockUpdateS2CPacket p && isFlowing(p.getState())) {
            pendingFlow.add(new long[]{p.getPos().asLong()});
            queued.incrementAndGet();
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (getNull()) return;
        if (mc.world != lastWorld) {
            clearAll();
            lastWorld = mc.world;
        }

        long now = System.currentTimeMillis();
        for (Long boxed; (boxed = pendingData.poll()) != null; ) {
            queued.decrementAndGet();
            long chunk = boxed;
            loadedAt.put(chunk, now);
            paletteChecks.enqueue(chunk);
        }
        if (now - lastLoadPrune > 1000) {
            lastLoadPrune = now;
            loadedAt.long2LongEntrySet().removeIf(en -> now - en.getLongValue() > FRESH_MS);
        }

        BlockPos.Mutable neighbour = new BlockPos.Mutable();
        for (long[] e; (e = pendingFlow.poll()) != null; ) {
            queued.decrementAndGet();
            long pos = e[0];
            long chunk = ChunkPos.toLong(BlockPos.unpackLongX(pos) >> 4, BlockPos.unpackLongZ(pos) >> 4);
            if (newChunks.contains(chunk)) continue;
            long arrived = loadedAt.getOrDefault(chunk, -1L);
            if (arrived < 0 || now - arrived > FRESH_MS) continue;
            BlockPos p = BlockPos.fromLong(pos);
            for (Direction dir : FLOW_NEIGHBOURS) {
                FluidState f = mc.world.getBlockState(neighbour.set(p, dir)).getFluidState();
                if (!f.isEmpty() && f.isStill()) {
                    newChunks.add(chunk);
                    oldChunks.remove(chunk);
                    dirty = true;
                    break;
                }
            }
        }

        for (int i = 0; i < DATA_CHECKS_PER_TICK && !paletteChecks.isEmpty(); i++) {
            long chunk = paletteChecks.dequeueLong();
            if (newChunks.contains(chunk) || oldChunks.contains(chunk)) continue;
            WorldChunk wc = mc.world.getChunkManager().getChunk(ChunkPos.getPackedX(chunk), ChunkPos.getPackedZ(chunk), ChunkStatus.FULL, false);
            if (wc == null) continue;
            boolean hasFlowing = false;
            for (ChunkSection section : wc.getSectionArray()) {
                if (section != null && !section.isEmpty() && section.getBlockStateContainer().hasAny(NewChunksModule::isFlowing)) {
                    hasFlowing = true;
                    break;
                }
            }
            if (!hasFlowing) continue;

            long arrived = loadedAt.getOrDefault(chunk, -1L);
            boolean fresh = arrived >= 0 && now - arrived <= FRESH_MS;
            if (overflow.getValue() && fresh && countFlowing(wc, overflowBlocks.getValue().intValue()) >= overflowBlocks.getValue().intValue()) {
                newChunks.add(chunk);
                oldChunks.remove(chunk);
                dirty = true;
            } else {
                oldChunks.add(chunk);
                if (showOld.getValue()) dirty = true;
            }
        }

        if (newChunks.size() + oldChunks.size() > MAX_CHUNKS) prune();
        maybeRebuild();
    }

    private void prune() {
        int cx = mc.player.getChunkPos().x, cz = mc.player.getChunkPos().z;
        int keep = range.getValue().intValue() * 2;
        for (LongOpenHashSet set : new LongOpenHashSet[]{newChunks, oldChunks}) {
            for (LongIterator it = set.iterator(); it.hasNext(); ) {
                long c = it.nextLong();
                if (Math.max(Math.abs(ChunkPos.getPackedX(c) - cx), Math.abs(ChunkPos.getPackedZ(c) - cz)) > keep) it.remove();
            }
            set.trim();
        }
        dirty = true;
    }

    private int settingsHash() {
        return java.util.Objects.hash(range.getValue().intValue(), showOld.getValue(), newColor.getColor().getRGB(),
                oldColor.getColor().getRGB(), tier(), style.getValue(), wallHeight.getValue().floatValue());
    }

    private void maybeRebuild() {
        ChunkOverlayRenderer.GpuTier t = tier();
        long now = System.currentTimeMillis();
        long center = mc.player.getChunkPos().toLong();
        int hash = settingsHash();
        boolean needed = dirty || center != lastCenter || hash != lastSettingsHash;
        if (!needed || now - lastBuild < (t == ChunkOverlayRenderer.GpuTier.HIGH ? 100 : 333)) return;
        lastBuild = now;
        dirty = false;
        lastCenter = center;
        lastSettingsHash = hash;
        rebuild(t);
    }

    private void rebuild(ChunkOverlayRenderer.GpuTier t) {
        boolean high = t == ChunkOverlayRenderer.GpuTier.HIGH;
        boolean threeD = style.getValue().equals("3D");
        int r = range.getValue().intValue();
        if (!high) r = Math.min(r, 16);
        int cx = mc.player.getChunkPos().x, cz = mc.player.getChunkPos().z;
        boolean old = showOld.getValue();

        int count = countInRange(newChunks, cx, cz, r) + (old ? countInRange(oldChunks, cx, cz, r) : 0);
        if (count == 0) { overlay.close(); return; }

        int fillPer = threeD ? 30 : 6, linePer = threeD ? 16 : 8;
        boolean withLines = high || threeD;
        ByteBuffer fill = MemoryUtil.memAlloc(count * fillPer * ChunkOverlayRenderer.VERTEX_BYTES);
        ByteBuffer lines = withLines ? MemoryUtil.memAlloc(count * linePer * ChunkOverlayRenderer.VERTEX_BYTES) : null;
        try {
            float h = wallHeight.getValue().floatValue();
            emit(newChunks, newColor.getColor(), cx, cz, r, high, threeD, h, fill, lines);
            if (old) emit(oldChunks, oldColor.getColor(), cx, cz, r, high, threeD, h, fill, lines);
            int fillVerts = fill.position() / ChunkOverlayRenderer.VERTEX_BYTES;
            int lineVerts = lines == null ? 0 : lines.position() / ChunkOverlayRenderer.VERTEX_BYTES;
            fill.flip();
            if (lines != null) lines.flip();
            overlay.upload(fill, fillVerts, lines, lineVerts, cx * 16.0, cz * 16.0);
        } finally {
            MemoryUtil.memFree(fill);
            if (lines != null) MemoryUtil.memFree(lines);
        }
    }

    private static int countInRange(LongOpenHashSet set, int cx, int cz, int r) {
        int n = 0;
        for (LongIterator it = set.iterator(); it.hasNext(); ) {
            long c = it.nextLong();
            if (Math.abs(ChunkPos.getPackedX(c) - cx) <= r && Math.abs(ChunkPos.getPackedZ(c) - cz) <= r) n++;
        }
        return n;
    }

    private static void emit(LongOpenHashSet set, Color color, int cx, int cz, int r, boolean high, boolean threeD, float h,
                             ByteBuffer fill, ByteBuffer lines) {
        for (LongIterator it = set.iterator(); it.hasNext(); ) {
            long c = it.nextLong();
            int px = ChunkPos.getPackedX(c), pz = ChunkPos.getPackedZ(c);
            int dx = px - cx, dz = pz - cz;
            if (Math.abs(dx) > r || Math.abs(dz) > r) continue;
            float alpha = 1f;
            if (high) {
                float d = (float) Math.sqrt(dx * dx + dz * dz) / (r + 1);
                alpha = 1f - 0.65f * d * d;
            }
            float x0 = dx * 16f, z0 = dz * 16f, x1 = x0 + 16f, z1 = z0 + 16f;
            int floor = ChunkOverlayRenderer.argb(color, alpha * (threeD ? 0.55f : 1f));
            ChunkOverlayRenderer.quad(fill, x0, 0, z0, x0, 0, z1, x1, 0, z1, x1, 0, z0, floor, floor);
            int edge = ChunkOverlayRenderer.argb(color, alpha * 3f);

            if (!threeD) {
                if (lines != null) {
                    ChunkOverlayRenderer.vertex(lines, x0, 0, z0, edge); ChunkOverlayRenderer.vertex(lines, x1, 0, z0, edge);
                    ChunkOverlayRenderer.vertex(lines, x1, 0, z0, edge); ChunkOverlayRenderer.vertex(lines, x1, 0, z1, edge);
                    ChunkOverlayRenderer.vertex(lines, x1, 0, z1, edge); ChunkOverlayRenderer.vertex(lines, x0, 0, z1, edge);
                    ChunkOverlayRenderer.vertex(lines, x0, 0, z1, edge); ChunkOverlayRenderer.vertex(lines, x0, 0, z0, edge);
                }
                continue;
            }
            int bottom = ChunkOverlayRenderer.argb(color, alpha * 2.4f), top = ChunkOverlayRenderer.argb(color, 0f);
            int topEdge = ChunkOverlayRenderer.argb(color, alpha * 0.9f);
            if (!set.contains(ChunkPos.toLong(px, pz - 1))) side(fill, lines, x0, z0, x1, z0, h, bottom, top, edge, high ? topEdge : 0);
            if (!set.contains(ChunkPos.toLong(px, pz + 1))) side(fill, lines, x0, z1, x1, z1, h, bottom, top, edge, high ? topEdge : 0);
            if (!set.contains(ChunkPos.toLong(px - 1, pz))) side(fill, lines, x0, z0, x0, z1, h, bottom, top, edge, high ? topEdge : 0);
            if (!set.contains(ChunkPos.toLong(px + 1, pz))) side(fill, lines, x1, z0, x1, z1, h, bottom, top, edge, high ? topEdge : 0);
        }
    }

    private static void side(ByteBuffer fill, ByteBuffer lines, float xa, float za, float xb, float zb, float h,
                             int bottom, int top, int edge, int topEdge) {
        ChunkOverlayRenderer.wall(fill, xa, za, xb, zb, 0, h, bottom, top);
        ChunkOverlayRenderer.vertex(lines, xa, 0, za, edge);
        ChunkOverlayRenderer.vertex(lines, xb, 0, zb, edge);
        if (topEdge != 0) {
            ChunkOverlayRenderer.vertex(lines, xa, h, za, topEdge);
            ChunkOverlayRenderer.vertex(lines, xb, h, zb, topEdge);
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (getNull() || overlay.isEmpty()) return;
        double y = followPlayer.getValue() ? Math.floor(mc.player.getY()) + 0.02 : height.getValue().doubleValue();
        overlay.draw(y, throughWalls.getValue());
    }

    public int newCount() { return newChunks.size(); }
    public int oldCount() { return oldChunks.size(); }

    @Override
    public String getMetaData() {
        return newChunks.size() + "";
    }
}
