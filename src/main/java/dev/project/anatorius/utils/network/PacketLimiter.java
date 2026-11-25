package dev.project.anatorius.utils.network;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

public final class PacketLimiter {
    private record Window(int limit, int importantLimit, Predicate<Packet<?>> counts, Deque<Long> times) {
        int recent(long now) {
            while (!times.isEmpty() && now - times.peekFirst() > 1000L) times.pollFirst();
            return times.size();
        }
    }

    private final Window global = new Window(280, 320, PacketLimiter::isGlobal, new ArrayDeque<>());
    private final Window interacts = new Window(90, 100, PacketLimiter::isInteract, new ArrayDeque<>());
    private final List<Window> windows = List.of(global, interacts);

    public synchronized boolean shouldCancel(Packet<?> packet) {
        long now = System.currentTimeMillis();
        if (packet instanceof HandSwingC2SPacket && interacts.recent(now) >= interacts.limit()) return true;
        for (Window window : windows) {
            if (window.counts().test(packet) && window.recent(now) >= (isImportant(packet) ? window.importantLimit() : window.limit())) return true;
        }
        for (Window window : windows) {
            if (window.counts().test(packet)) window.times().addLast(now);
        }
        return false;
    }

    public synchronized int recent() {
        return global.recent(System.currentTimeMillis());
    }

    private static boolean isImportant(Packet<?> packet) {
        if (packet instanceof PlayerActionC2SPacket action) {
            PlayerActionC2SPacket.Action type = action.getAction();
            return type == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                    || type == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK
                    || type == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND;
        }
        return packet instanceof CommonPongC2SPacket;
    }

    private static boolean isInteract(Packet<?> packet) {
        return packet instanceof PlayerInteractEntityC2SPacket || packet instanceof PlayerActionC2SPacket;
    }

    private static boolean isGlobal(Packet<?> packet) {
        return packet instanceof PlayerInteractEntityC2SPacket
                || packet instanceof PlayerInteractItemC2SPacket
                || packet instanceof PlayerInteractBlockC2SPacket
                || packet instanceof PlayerActionC2SPacket
                || packet instanceof CommonPongC2SPacket;
    }
}
