package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.KeyboardTickEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.chat.ChatUtils;
import dev.project.anatorius.utils.animations.Easing;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.ChunkOverlayRenderer;
import dev.project.anatorius.utils.graphics.Renderer3D;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.block.entity.VaultBlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.system.MemoryUtil;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@RegisterModule(name = "StashFinder", description = "Finds chunks full of storage blocks (stashes).", category = Module.Category.MISCELLANEOUS)
public class StashFinderModule extends Module {
    public final NumberSetting minStorage = new NumberSetting("MinStorage", "Storage blocks (chests, barrels, shulkers, ender chests) a chunk needs.", 8, 2, 64);
    public final BooleanSetting anyShulker = new BooleanSetting("AnyShulker", "A single shulker box marks the chunk (never natural).", true);
    public final BooleanSetting utility = new BooleanSetting("CountUtility", "Also count hoppers, furnaces, dispensers, droppers and crafters.", false);
    public final BooleanSetting ignoreNatural = new BooleanSetting("IgnoreNatural", "Chests and barrels in generated structures don't count: up to two chunks from a spawner, trial spawner or vault, and in chunks with blocks players can't place or the trial chamber tuff + copper (dungeons, trial chambers, strongholds, ancient cities, desert pyramids, ruins).", true);
    public final BooleanSetting mergeBorders = new BooleanSetting("MergeBorders", "A base across chunk borders counts as one: neighbouring chunks are added together.", true);
    public final BooleanSetting notify = new BooleanSetting("Notify", "Chat message when a stash is found.", true);
    public final dev.project.anatorius.settings.impl.WhitelistSetting sound = new dev.project.anatorius.settings.impl.WhitelistSetting("Sound", "Sounds", "The sound played when a stash is found. None picked: silent.", dev.project.anatorius.settings.impl.WhitelistSetting.Type.SOUNDS).withDefault("ping");
    public final BooleanSetting waypoint = new BooleanSetting("Waypoint", "Add found stashes to Waypoints.", true);
    public final BooleanSetting logFile = new BooleanSetting("LogFile", "Append found stashes to stashes.txt in the client folder.", true);
    public final NumberSetting range = new NumberSetting("Range", "How many chunks around you to draw.", 64, 8, 128);
    public final BooleanSetting throughWalls = new BooleanSetting("ThroughWalls", "Visible through terrain.", true);
    public final BooleanSetting boxes = new BooleanSetting("Boxes", "Outline the containers of nearby stashes.", true);
    public final BooleanSetting beam = new BooleanSetting("Beam", "A light beam above each stash, visible from far away.", true);
    public final ColorSetting color = new ColorSetting("Color", "Colour of stash chunks.", ColorUtils.palette(ColorUtils.THEME_CAUTION, 60));

    public final CategorySetting autoCategory = new CategorySetting("Automatic", "Fly a search route by yourself.");
    public final BooleanSetting automatic = new BooleanSetting("Automatic", "Takes off and flies a search route by itself while looking for stashes. A movement key takes the controls while held.", new CategorySetting.Visibility(autoCategory), false);
    public final ModeSetting engine = new ModeSetting("Engine", "What flies. ElytraFly and FakeFly fly with that module in the mode and speed you set there (ElytraFly Control / Packet and FakeFly Tick stop in the air before unloaded chunks, FakeFly Grim circles instead). Baritone: its elytra path finder with rockets, the only one under the Nether roof. Auto: Baritone in the Nether, otherwise FakeFly if it's on, else ElytraFly.", new BooleanSetting.Visibility(automatic, true), "Auto", new String[]{"Auto", "ElytraFly", "FakeFly", "Baritone"});
    public final ModeSetting pattern = new ModeSetting("Pattern", "Spiral: a square spiral around where you start. Straight: a line in the direction you face.", new BooleanSetting.Visibility(automatic, true), "Spiral", new String[]{"Spiral", "Straight"});
    public final NumberSetting laneSpacing = new NumberSetting("LaneSpacing", "Chunks between spiral lanes. 0 = from the server's view distance, so the searched strips touch.", new BooleanSetting.Visibility(automatic, true), 0, 0, 64);
    public final NumberSetting maxDistance = new NumberSetting("MaxDistance", "How far from the start the route goes, in blocks.", new BooleanSetting.Visibility(automatic, true), 10000, 100, 100000).withMaxLabel("Unlimited");
    public final NumberSetting clearance = new NumberSetting("Clearance", "Blocks kept between you and the highest terrain ahead (ElytraFly, FakeFly).", new BooleanSetting.Visibility(automatic, true), 14, 6, 48);
    public final NumberSetting climbSpeed = new NumberSetting("ClimbSpeed", "ElytraFly Packet: blocks per tick the route climbs and sinks at (never more than ElytraFly's Vertical). Strict servers kick fast up / down packet flight, keep it low there.", new BooleanSetting.Visibility(automatic, true), 0.4, 0.05, 1.0);
    public final ModeSetting onFind = new ModeSetting("OnFind", "Continue: keep searching. Stop: stop the route and hover there.", new BooleanSetting.Visibility(automatic, true), "Continue", new String[]{"Continue", "Stop"});
    public final BooleanSetting resume = new BooleanSetting("Resume", "Turning Automatic on again in the same world goes on with the last route where it stopped, instead of searching the same area again. Off: a new route around you.", new BooleanSetting.Visibility(automatic, true), true);
    public final BooleanSetting showRoute = new BooleanSetting("ShowRoute", "Draw the route ahead: a line to the next corner and the corners after it.", new BooleanSetting.Visibility(automatic, true), true);

    private static final int MAX_STASHES = 2_000;
    private static final int MAX_QUEUED = 20_000;
    private static final int MAX_SCANNED = 40_000;
    private static final int CHECKS_PER_TICK = 64;
    private static final int MAX_CONTAINERS = 128;
    private static final double BOX_RANGE = 128;
    private static final float WALL_HEIGHT = 6f;
    private static final float BEAM_HEIGHT = 160f;
    private static final float BEAM_HALF_WIDTH = 0.25f;

    private static final class ChunkInfo {
        int chests, barrels, shulkers, enderChests, utility;
        boolean natural;
        long[] containers;
    }

    private static final class Stash {
        final int cx, cz;
        int chests, barrels, shulkers, enderChests, utility;
        long[] containers = new long[0];
        double x, z;
        int y;
        int total;
        long born = System.currentTimeMillis();

        Stash(int cx, int cz) { this.cx = cx; this.cz = cz; }
    }

    private final ConcurrentLinkedQueue<Long> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final Set<Long> changed = ConcurrentHashMap.newKeySet();
    private final LongArrayFIFOQueue retry = new LongArrayFIFOQueue();
    private final Long2ObjectOpenHashMap<ChunkInfo> scanned = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Stash> stashes = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet markers = new LongOpenHashSet();
    private static final int MARKER_REACH = 2;
    private final Set<String> announced = new HashSet<>();

    private volatile int playerCx, playerCz;
    private volatile int radiusNow, radiusLast;
    private int radiusTicks;

    private final StashAutopilot autopilot = new StashAutopilot(this);
    private final ChunkOverlayRenderer overlay = new ChunkOverlayRenderer("anatorius stashfinder");
    private ClientWorld lastWorld;
    private boolean dirty;
    private boolean animating;
    private static final long FLOOR_MS = 400, WALL_DELAY_MS = 100, WALL_MS = 600, BEAM_DELAY_MS = 250, BEAM_MS = 900, RING_MS = 1800;
    private long lastCenter = Long.MIN_VALUE;
    private int lastSettingsHash;

    @Override
    public void onEnable() {
        clearWorld();
    }

    @Override
    public void onDisable() {
        autopilot.stop(null);
        clearWorld();
    }

    private void clearWorld() {
        pending.clear();
        queued.set(0);
        changed.clear();
        retry.clear();
        scanned.clear();
        scanned.trim();
        markers.clear();
        markers.trim();
        stashes.clear();
        stashes.trim();
        overlay.close();
        lastWorld = null;
        lastCenter = Long.MIN_VALUE;
        radiusNow = radiusLast = 0;
        dirty = true;
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacket() instanceof ChunkDataS2CPacket p) {
            int r = Math.max(Math.abs(p.getChunkX() - playerCx), Math.abs(p.getChunkZ() - playerCz));
            if (r <= 64 && r > radiusNow) radiusNow = r;
            if (queued.get() < MAX_QUEUED) {
                pending.add(ChunkPos.toLong(p.getChunkX(), p.getChunkZ()));
                queued.incrementAndGet();
            }
        } else if (event.getPacket() instanceof BlockUpdateS2CPacket p) {
            if (p.getState().hasBlockEntity() || p.getState().isAir()) changed.add(ChunkPos.toLong(p.getPos().getX() >> 4, p.getPos().getZ() >> 4));
        } else if (event.getPacket() instanceof ChunkDeltaUpdateS2CPacket p) {
            p.visitUpdates((pos, state) -> {
                if (state.hasBlockEntity()) changed.add(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
            });
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (getNull()) return;
        if (mc.world != lastWorld) {
            if (lastWorld != null) autopilot.stop("World changed: Automatic stopped.");
            if (lastWorld != null) automatic.setValue(false);
            clearWorld();
            lastWorld = mc.world;
        }
        playerCx = mc.player.getChunkPos().x;
        playerCz = mc.player.getChunkPos().z;
        if (++radiusTicks >= 200) { radiusTicks = 0; radiusLast = radiusNow; radiusNow = 0; }

        for (int n = retry.size(); n > 0; n--) check(retry.dequeueLong(), false);
        Long boxed;
        for (int i = 0; i < CHECKS_PER_TICK && (boxed = pending.poll()) != null; i++) {
            queued.decrementAndGet();
            check(boxed, true);
        }
        if (!changed.isEmpty()) {
            Long[] keys = changed.toArray(new Long[0]);
            for (Long k : keys) { changed.remove(k); check(k, false); }
        }
        if (scanned.size() > MAX_SCANNED) forgetFar();

        if (automatic.getValue() && !autopilot.active()) autopilot.start();
        else if (!automatic.getValue() && autopilot.active()) autopilot.stop("Automatic off.");
        autopilot.tick();

        maybeRebuild();
    }

    @SubscribeEvent
    public void onKeyboardTick(KeyboardTickEvent event) {
        if (getNull()) return;
        autopilot.keyboard(event);
    }

    private void check(long chunk, boolean mayRetry) {
        int cx = ChunkPos.getPackedX(chunk), cz = ChunkPos.getPackedZ(chunk);
        WorldChunk wc = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
        if (wc == null) {
            if (mayRetry) retry.enqueue(chunk);
            return;
        }

        ChunkInfo ci = new ChunkInfo();
        long[] found = new long[MAX_CONTAINERS];
        int count = 0;
        boolean marker = false;
        for (BlockEntity be : wc.getBlockEntities().values()) {
            if (be instanceof ChestBlockEntity) ci.chests++;
            else if (be instanceof BarrelBlockEntity) ci.barrels++;
            else if (be instanceof ShulkerBoxBlockEntity) ci.shulkers++;
            else if (be instanceof EnderChestBlockEntity) ci.enderChests++;
            else if (be instanceof HopperBlockEntity || be instanceof AbstractFurnaceBlockEntity
                    || be instanceof DispenserBlockEntity || be instanceof CrafterBlockEntity) ci.utility++;
            else {
                if (be instanceof MobSpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity || be instanceof VaultBlockEntity) marker = true;
                continue;
            }
            if (count < MAX_CONTAINERS) found[count++] = be.getPos().asLong();
        }
        ci.containers = Arrays.copyOf(found, count);
        if (marker && markers.add(chunk)) {
            for (int dx = -MARKER_REACH - 1; dx <= MARKER_REACH + 1; dx++) for (int dz = -MARKER_REACH - 1; dz <= MARKER_REACH + 1; dz++) {
                long n = ChunkPos.toLong(cx + dx, cz + dz);
                if (n != chunk && scanned.containsKey(n)) evaluate(n);
            }
        }
        if (count > 0) ci.natural = marker || structureBlocks(wc);

        boolean had = scanned.containsKey(chunk);
        if (count == 0) {
            if (!had) return;
            scanned.remove(chunk);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                long n = ChunkPos.toLong(cx + dx, cz + dz);
                if (n == chunk ? stashes.containsKey(n) : scanned.containsKey(n)) evaluate(n);
            }
            return;
        }
        scanned.put(chunk, ci);
        evaluate(chunk);
    }

    private boolean natural(long chunk, ChunkInfo ci) {
        if (!ignoreNatural.getValue()) return false;
        if (ci.natural) return true;
        int cx = ChunkPos.getPackedX(chunk), cz = ChunkPos.getPackedZ(chunk);
        for (int dx = -MARKER_REACH; dx <= MARKER_REACH; dx++) for (int dz = -MARKER_REACH; dz <= MARKER_REACH; dz++)
            if (markers.contains(ChunkPos.toLong(cx + dx, cz + dz))) return true;
        return false;
    }

    private static Set<Block> unplaceable, tuff, waxedCopper;

    private static void structureSets() {
        if (unplaceable != null) return;
        unplaceable = Set.of(Blocks.SPAWNER, Blocks.TRIAL_SPAWNER, Blocks.VAULT, Blocks.REINFORCED_DEEPSLATE, Blocks.END_PORTAL_FRAME,
                Blocks.INFESTED_STONE, Blocks.INFESTED_STONE_BRICKS, Blocks.INFESTED_MOSSY_STONE_BRICKS, Blocks.INFESTED_CRACKED_STONE_BRICKS,
                Blocks.INFESTED_CHISELED_STONE_BRICKS, Blocks.INFESTED_COBBLESTONE, Blocks.INFESTED_DEEPSLATE,
                Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL);
        tuff = Set.of(Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF, Blocks.CHISELED_TUFF_BRICKS, Blocks.POLISHED_TUFF,
                Blocks.TUFF_BRICK_STAIRS, Blocks.TUFF_BRICK_SLAB, Blocks.TUFF_BRICK_WALL);
        waxedCopper = Set.of(Blocks.WAXED_COPPER_BLOCK, Blocks.WAXED_CUT_COPPER, Blocks.WAXED_CHISELED_COPPER, Blocks.WAXED_COPPER_GRATE,
                Blocks.WAXED_COPPER_BULB, Blocks.WAXED_OXIDIZED_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER, Blocks.WAXED_OXIDIZED_CHISELED_COPPER,
                Blocks.WAXED_OXIDIZED_COPPER_GRATE, Blocks.WAXED_OXIDIZED_COPPER_BULB, Blocks.WAXED_WEATHERED_COPPER_GRATE,
                Blocks.WAXED_EXPOSED_COPPER_GRATE, Blocks.WAXED_COPPER_DOOR, Blocks.WAXED_COPPER_TRAPDOOR);
    }

    private static boolean structureBlocks(WorldChunk wc) {
        structureSets();
        boolean[] seen = new boolean[3];
        for (ChunkSection section : wc.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;
            section.getBlockStateContainer().count((state, n) -> {
                Block b = state.getBlock();
                if (unplaceable.contains(b)) seen[0] = true;
                else if (tuff.contains(b)) seen[1] = true;
                else if (waxedCopper.contains(b)) seen[2] = true;
            });
            if (seen[0] || (seen[1] && seen[2])) return true;
        }
        return false;
    }

    private int storageOf(long chunk, ChunkInfo ci) {
        if (natural(chunk, ci)) return ci.shulkers + ci.enderChests;
        return ci.chests + ci.barrels + ci.shulkers + ci.enderChests + (utility.getValue() ? ci.utility : 0);
    }

    private void evaluate(long chunk) {
        int cx = ChunkPos.getPackedX(chunk), cz = ChunkPos.getPackedZ(chunk);
        int reach = mergeBorders.getValue() ? 1 : 0;
        Stash s = null;
        int total = 0, shulkers = 0, best = -1;
        long anchor = chunk;
        long[] found = new long[MAX_CONTAINERS];
        int count = 0;
        double sx = 0, sz = 0;
        int[] ys = new int[MAX_CONTAINERS];
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
            long key = ChunkPos.toLong(cx + dx, cz + dz);
            ChunkInfo ci = scanned.get(key);
            if (ci == null) continue;
            int st = storageOf(key, ci);
            if (st == 0) continue;
            if (s == null) s = new Stash(cx, cz);
            total += st;
            shulkers += ci.shulkers;
            if (st > best) { best = st; anchor = key; }
            boolean naturalOnly = natural(key, ci);
            s.chests += naturalOnly ? 0 : ci.chests;
            s.barrels += naturalOnly ? 0 : ci.barrels;
            s.shulkers += ci.shulkers;
            s.enderChests += ci.enderChests;
            s.utility += naturalOnly ? 0 : ci.utility;
            for (long p : ci.containers) {
                if (count >= MAX_CONTAINERS || (naturalOnly && ci.shulkers + ci.enderChests == 0)) break;
                BlockPos bp = BlockPos.fromLong(p);
                found[count] = p;
                ys[count] = bp.getY();
                sx += bp.getX() + 0.5;
                sz += bp.getZ() + 0.5;
                count++;
            }
        }

        boolean isStash = s != null && (total >= minStorage.getValue().intValue() || (anyShulker.getValue() && shulkers > 0));
        long existing = Long.MIN_VALUE;
        for (int dx = -2; dx <= 2 && existing == Long.MIN_VALUE; dx++) for (int dz = -2; dz <= 2; dz++) {
            long key = ChunkPos.toLong(cx + dx, cz + dz);
            if (stashes.containsKey(key)) { existing = key; break; }
        }
        if (!isStash) {
            if (stashes.remove(chunk) != null) dirty = true;
            return;
        }
        long born = System.currentTimeMillis();
        if (existing != Long.MIN_VALUE) {
            Stash old = stashes.get(existing);
            if (old.total >= total && existing != chunk) return;
            born = old.born;
            stashes.remove(existing);
            anchor = existing;
        } else if (stashes.size() >= MAX_STASHES) {
            return;
        }

        Stash st = new Stash(ChunkPos.getPackedX(anchor), ChunkPos.getPackedZ(anchor));
        st.born = born;
        st.chests = s.chests; st.barrels = s.barrels; st.shulkers = s.shulkers; st.enderChests = s.enderChests; st.utility = s.utility;
        st.total = total;
        st.containers = Arrays.copyOf(found, count);
        if (count > 0) {
            st.x = sx / count;
            st.z = sz / count;
            int[] sorted = Arrays.copyOf(ys, count);
            Arrays.sort(sorted);
            st.y = sorted[count / 2];
        } else {
            st.x = st.cx * 16 + 8;
            st.z = st.cz * 16 + 8;
            st.y = mc.player.getBlockY();
        }
        stashes.put(anchor, st);
        dirty = true;
        if (existing == Long.MIN_VALUE) announce(st);
    }

    private void forgetFar() {
        int px = playerCx, pz = playerCz;
        scanned.long2ObjectEntrySet().removeIf(e -> Math.max(Math.abs(ChunkPos.getPackedX(e.getLongKey()) - px),
                Math.abs(ChunkPos.getPackedZ(e.getLongKey()) - pz)) > 48);
        scanned.trim();
    }

    private void announce(Stash s) {
        String server = Anatorius.SERVER_MANAGER.getServer();
        String dimension = WorldUtils.getDimension();
        if (!announced.add(server + "|" + dimension + "|" + s.cx + "|" + s.cz)) return;
        int x = (int) Math.floor(s.x), z = (int) Math.floor(s.z);
        String what = describe(s);
        int dist = (int) Math.sqrt(mc.player.squaredDistanceTo(s.x, mc.player.getY(), s.z));

        dev.project.anatorius.utils.minecraft.ClientSounds.play(sound.getSound(), 1.0f);
        if (notify.getValue()) {
            Anatorius.CHAT_MANAGER.tagged("Stash at " + ChatUtils.getPrimary() + x + ", " + s.y + ", " + z
                    + ChatUtils.getSecondary() + " (" + what + ", " + dist + " m away)", "StashFinder");
        }
        if (waypoint.getValue()) {
            Anatorius.WAYPOINT_MANAGER.add("Stash_" + x + "_" + z, new net.minecraft.util.math.Vec3d(x + 0.5, s.y + 4, z + 0.5), dimension, server);
        }
        if (logFile.getValue()) {
            String line = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " " + server + " "
                    + dimension + " " + x + " " + s.y + " " + z + " total=" + s.total + " " + what + System.lineSeparator();
            try {
                Path file = Path.of(Anatorius.MOD_NAME, "stashes.txt");
                Files.createDirectories(file.getParent());
                Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception e) {
                Anatorius.LOGGER.warn("[Anatorius] StashFinder could not write stashes.txt: {}", e.toString());
            }
        }
        if (autopilot.active() && onFind.getValue().equals("Stop")) {
            autopilot.stop("Stash found: route stopped, you have the controls.");
            automatic.setValue(false);
        }
    }

    private static String describe(Stash s) {
        StringBuilder b = new StringBuilder();
        append(b, s.chests, "chest");
        append(b, s.shulkers, "shulker");
        append(b, s.barrels, "barrel");
        append(b, s.enderChests, "ender chest");
        append(b, s.utility, "utility");
        return b.toString();
    }

    private static void append(StringBuilder b, int n, String what) {
        if (n == 0) return;
        if (!b.isEmpty()) b.append(", ");
        b.append(n).append(' ').append(what).append(n == 1 || what.equals("utility") ? "" : "s");
    }

    int seenRadius() {
        int r = Math.max(radiusNow, radiusLast);
        return r > 0 ? r : mc.options.getClampedViewDistance();
    }

    private void maybeRebuild() {
        long center = mc.player.getChunkPos().toLong();
        int hash = java.util.Objects.hash(range.getValue().intValue(), color.getColor().getRGB(), beam.getValue());
        if (!dirty && !animating && center == lastCenter && hash == lastSettingsHash) return;
        dirty = false;
        lastCenter = center;
        lastSettingsHash = hash;

        int cx = mc.player.getChunkPos().x, cz = mc.player.getChunkPos().z, r = range.getValue().intValue();
        int count = 0;
        for (Stash s : stashes.values()) if (Math.abs(s.cx - cx) <= r && Math.abs(s.cz - cz) <= r) count++;
        if (count == 0) { overlay.close(); animating = false; return; }

        boolean withBeam = beam.getValue();
        ByteBuffer fill = MemoryUtil.memAlloc(count * (withBeam ? 54 : 30) * ChunkOverlayRenderer.VERTEX_BYTES);
        ByteBuffer lines = MemoryUtil.memAlloc(count * 8 * ChunkOverlayRenderer.VERTEX_BYTES);
        try {
            Color c = color.getColor();
            int floor = ChunkOverlayRenderer.argb(c, 0.6f), edge = ChunkOverlayRenderer.argb(c, 3f);
            int wallBottom = ChunkOverlayRenderer.argb(c, 2.4f), clear = ChunkOverlayRenderer.argb(c, 0f);
            int beamBottom = ChunkOverlayRenderer.argb(c, 2.2f);
            long now = System.currentTimeMillis();
            boolean stillRising = false;
            for (Stash s : stashes.values()) {
                int dx = s.cx - cx, dz = s.cz - cz;
                if (Math.abs(dx) > r || Math.abs(dz) > r) continue;
                float floorP = progress(s, now, 0, FLOOR_MS, Easing.Method.EASE_OUT_CUBIC);
                float wallP = progress(s, now, WALL_DELAY_MS, WALL_MS, Easing.Method.EASE_OUT_BACK);
                float beamP = progress(s, now, BEAM_DELAY_MS, BEAM_MS, Easing.Method.EASE_OUT_EXPO);
                if (floorP < 1f || wallP < 1f || beamP < 1f) stillRising = true;
                floor = ChunkOverlayRenderer.argb(c, 0.6f * floorP);
                edge = ChunkOverlayRenderer.argb(c, 3f * floorP);
                wallBottom = ChunkOverlayRenderer.argb(c, 2.4f * floorP);
                beamBottom = ChunkOverlayRenderer.argb(c, 2.2f * floorP);
                float wallHeight = WALL_HEIGHT * wallP, beamHeight = BEAM_HEIGHT * beamP;
                float x0 = dx * 16f, z0 = dz * 16f, x1 = x0 + 16f, z1 = z0 + 16f, y = s.y;
                ChunkOverlayRenderer.quad(fill, x0, y, z0, x0, y, z1, x1, y, z1, x1, y, z0, floor, floor);
                ChunkOverlayRenderer.wall(fill, x0, z0, x1, z0, y, wallHeight, wallBottom, clear);
                ChunkOverlayRenderer.wall(fill, x1, z0, x1, z1, y, wallHeight, wallBottom, clear);
                ChunkOverlayRenderer.wall(fill, x1, z1, x0, z1, y, wallHeight, wallBottom, clear);
                ChunkOverlayRenderer.wall(fill, x0, z1, x0, z0, y, wallHeight, wallBottom, clear);
                if (withBeam) {
                    float bxc = (float) (s.x - cx * 16), bzc = (float) (s.z - cz * 16);
                    float bx0 = bxc - BEAM_HALF_WIDTH, bx1 = bxc + BEAM_HALF_WIDTH;
                    float bz0 = bzc - BEAM_HALF_WIDTH, bz1 = bzc + BEAM_HALF_WIDTH;
                    ChunkOverlayRenderer.wall(fill, bx0, bz0, bx1, bz0, y, beamHeight, beamBottom, clear);
                    ChunkOverlayRenderer.wall(fill, bx1, bz0, bx1, bz1, y, beamHeight, beamBottom, clear);
                    ChunkOverlayRenderer.wall(fill, bx1, bz1, bx0, bz1, y, beamHeight, beamBottom, clear);
                    ChunkOverlayRenderer.wall(fill, bx0, bz1, bx0, bz0, y, beamHeight, beamBottom, clear);
                }
                ChunkOverlayRenderer.vertex(lines, x0, y, z0, edge); ChunkOverlayRenderer.vertex(lines, x1, y, z0, edge);
                ChunkOverlayRenderer.vertex(lines, x1, y, z0, edge); ChunkOverlayRenderer.vertex(lines, x1, y, z1, edge);
                ChunkOverlayRenderer.vertex(lines, x1, y, z1, edge); ChunkOverlayRenderer.vertex(lines, x0, y, z1, edge);
                ChunkOverlayRenderer.vertex(lines, x0, y, z1, edge); ChunkOverlayRenderer.vertex(lines, x0, y, z0, edge);
            }
            animating = stillRising;
            int fillVerts = fill.position() / ChunkOverlayRenderer.VERTEX_BYTES;
            int lineVerts = lines.position() / ChunkOverlayRenderer.VERTEX_BYTES;
            fill.flip();
            lines.flip();
            overlay.upload(fill, fillVerts, lines, lineVerts, cx * 16.0, cz * 16.0);
        } finally {
            MemoryUtil.memFree(fill);
            MemoryUtil.memFree(lines);
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (getNull()) return;
        if (showRoute.getValue() && autopilot.active()) renderRoute(event);
        if (stashes.isEmpty()) return;
        if (animating) maybeRebuild();
        overlay.draw(0, throughWalls.getValue());

        Color c = color.getColor();
        long now = System.currentTimeMillis();
        double boxRangeSq = BOX_RANGE * BOX_RANGE;
        int drawn = 0;
        for (Stash s : stashes.values()) {
            double distSq = mc.player.squaredDistanceTo(s.x, s.y, s.z);
            float appear = progress(s, now, 0, FLOOR_MS, Easing.Method.EASE_OUT_CUBIC);

            if (now - s.born < RING_MS) {
                for (int ring = 0; ring < 2; ring++) {
                    float t = (now - s.born - ring * 350L) / (float) (RING_MS - 350L);
                    if (t <= 0f || t >= 1f) continue;
                    float radius = 1f + Easing.ease(t, Easing.Method.EASE_OUT_CUBIC) * 14f;
                    int alpha = (int) (Math.min(255, c.getAlpha() * 4) * (1f - t));
                    Renderer3D.renderFilledRing(event.getMatrices(), new net.minecraft.util.math.Vec3d(s.x, s.y + 0.05, s.z),
                            Math.max(0, radius - 0.7f), radius, 48, ColorUtils.getColor(c, alpha), ColorUtils.getColor(c, alpha / 3));
                }
            }

            if (distSq > boxRangeSq) continue;
            if (boxes.getValue()) {
                Color boxFill = ColorUtils.getColor(c, (int) (Math.min(255, c.getAlpha() * 1.2f) * appear));
                Color boxOutline = ColorUtils.getColor(c, (int) (Math.min(255, c.getAlpha() * 3) * appear));
                for (long p : s.containers) {
                    if (drawn++ > 512) break;
                    Box box = new Box(BlockPos.fromLong(p)).contract(0.02);
                    Renderer3D.renderBox(event.getMatrices(), box, boxFill);
                    Renderer3D.renderBoxOutline(event.getMatrices(), box, boxOutline);
                }
            }
            int distance = (int) Math.sqrt(mc.player.squaredDistanceTo(s.x, mc.player.getY(), s.z));
            Renderer3D.renderScaledText(event.getMatrices(), "Stash " + s.total + " - " + distance + "m",
                    s.x, s.y + 2, s.z, 30, true, new Color(255, 255, 255, Math.max(8, (int) (255 * appear))));
        }
    }

    private float progress(Stash s, long now, long delay, long duration, Easing.Method method) {
        float t = (now - s.born - delay) / (float) duration;
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return Easing.ease(t, method);
    }

    private void renderRoute(RenderWorldEvent event) {
        java.util.List<net.minecraft.util.math.Vec3d> route = autopilot.preview(3);
        if (route.isEmpty()) return;
        Color c = color.getColor();
        Color line = new Color(c.getRed(), c.getGreen(), c.getBlue(), 200);
        Color later = new Color(c.getRed(), c.getGreen(), c.getBlue(), 90);
        double y = mc.player.getY();
        net.minecraft.util.math.Vec3d from = new net.minecraft.util.math.Vec3d(mc.player.getX(), y, mc.player.getZ());
        for (int i = 0; i < route.size(); i++) {
            net.minecraft.util.math.Vec3d to = new net.minecraft.util.math.Vec3d(route.get(i).x, y, route.get(i).z);
            Renderer3D.renderLine(event.getMatrices(), from, to, i == 0 ? line : later);
            from = to;
        }
        net.minecraft.util.math.Vec3d next = route.get(0);
        int dist = (int) Math.sqrt(mc.player.squaredDistanceTo(next.x, y, next.z));
        Renderer3D.renderScaledText(event.getMatrices(), "Next corner " + dist + " m", next.x, y + 2, next.z, 30, true, Color.WHITE);
    }

    public int stashCount() { return stashes.size(); }

    public String autopilotDebug() { return autopilot.debug; }

    public int autopilotCorner() { return autopilot.cornerNo(); }

    public String autopilotStatus() { return autopilot.active() ? autopilot.status() : "off"; }

    @Override
    public String getMetaData() {
        return autopilot.active() ? stashes.size() + " " + autopilot.status() : String.valueOf(stashes.size());
    }
}
