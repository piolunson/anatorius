package dev.project.anatorius.managers;

import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.*;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldManager implements IMinecraft {
    private static final int SPHERE_RADIUS = 16;
    private static final Vec3i[] SPHERE = new Vec3i[(2 * SPHERE_RADIUS + 1) * (2 * SPHERE_RADIUS + 1) * (2 * SPHERE_RADIUS + 1)];
    private static final int[] INDICES = new int[SPHERE_RADIUS + 1];

    @Getter private final Map<UUID, Integer> poppedTotems = new ConcurrentHashMap<>();
    private final List<UUID> deadPlayers = new ArrayList<>();

    @Getter @Setter private float timerMultiplier = 1.0f;

    @Getter private final Timer placeTimer = new Timer();

    private final Map<BlockPos, Long> pendingPlacements = new ConcurrentHashMap<>();

    private final Set<BlockPos> tickPlacements = ConcurrentHashMap.newKeySet();

    public WorldManager() {
        Anatorius.EVENT_HANDLER.subscribe(this);

        BlockPos origin = BlockPos.ORIGIN;
        Set<BlockPos> positions = new TreeSet<>((o, p) -> {
            if (o.equals(p)) return 0;

            int compare = Double.compare(origin.getSquaredDistance(o), origin.getSquaredDistance(p));
            if (compare == 0) compare = Integer.compare(Math.abs(o.getX()) + Math.abs(o.getY()) + Math.abs(o.getZ()), Math.abs(p.getX()) + Math.abs(p.getY()) + Math.abs(p.getZ()));

            return compare == 0 ? 1 : compare;
        });

        for (int x = origin.getX() - SPHERE_RADIUS; x <= origin.getX() + SPHERE_RADIUS; x++) {
            for (int z = origin.getZ() - SPHERE_RADIUS; z <= origin.getZ() + SPHERE_RADIUS; z++) {
                for (int y = origin.getY() - SPHERE_RADIUS; y < origin.getY() + SPHERE_RADIUS; y++) {
                    double distance = (origin.getX() - x) * (origin.getX() - x) + (origin.getZ() - z) * (origin.getZ() - z) + (origin.getY() - y) * (origin.getY() - y);
                    if (distance < MathHelper.square(SPHERE_RADIUS)) positions.add(new BlockPos(x, y, z));
                }
            }
        }

        int i = 0;
        int currentDistance = 0;

        for (BlockPos position : positions) {
            if (Math.sqrt(origin.getSquaredDistance(position)) > currentDistance) INDICES[currentDistance++] = i;
            SPHERE[i++] = position;
        }

        for (int d = currentDistance; d < INDICES.length; d++) INDICES[d] = i;
    }

    @SubscribeEvent(priority = Integer.MAX_VALUE)
    public void onTickStart(TickEvent event) {
        tickPlacements.clear();
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (mc.world == null) return;

        for (UUID uuid : new ArrayList<>(poppedTotems.keySet())) {
            if (mc.world.getPlayers().stream().noneMatch(player -> player.getUuid().equals(uuid))) {
                poppedTotems.remove(uuid);
            }
        }

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.deathTime <= 0 && player.getHealth() > 0) {
                deadPlayers.remove(player.getUuid());
                continue;
            }

            if (deadPlayers.contains(player.getUuid())) continue;

            Anatorius.EVENT_HANDLER.post(new PlayerDeathEvent(player));
            deadPlayers.add(player.getUuid());

            poppedTotems.remove(player.getUuid());
        }

        long pingTimeout = Math.max(100L, Anatorius.SERVER_MANAGER.getPing() * 2L);
        pendingPlacements.entrySet().removeIf(entry ->
                System.currentTimeMillis() - entry.getValue() > pingTimeout);
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (mc.world == null) return;

        if (event.getPacket() instanceof BlockUpdateS2CPacket packet) {
            pendingPlacements.remove(packet.getPos());
        } else if (event.getPacket() instanceof ChunkDeltaUpdateS2CPacket packet) {
            packet.visitUpdates((pos, state) -> pendingPlacements.remove(pos));
        } else if (event.getPacket() instanceof EntityStatusS2CPacket packet && packet.getStatus() == 35) {
            if (!(packet.getEntity(mc.world) instanceof PlayerEntity player)) return;

            int pops = poppedTotems.getOrDefault(player.getUuid(), 0);
            poppedTotems.put(player.getUuid(), ++pops);

            Anatorius.EVENT_HANDLER.post(new PlayerPopEvent(player, pops));
        }
    }

    public int getRadius(double radius) {
        return INDICES[MathHelper.clamp((int) Math.ceil(radius), 0, INDICES.length - 1)];
    }

    public Vec3i getOffset(int index) {
        return SPHERE[index];
    }

    public void markPending(BlockPos pos) {
        pendingPlacements.put(pos, System.currentTimeMillis());
        tickPlacements.add(pos.toImmutable());
    }

    public boolean isPlacedThisTick(BlockPos pos) {
        return tickPlacements.contains(pos);
    }

    public boolean isPending(BlockPos pos) {
        return pendingPlacements.containsKey(pos);
    }
}
