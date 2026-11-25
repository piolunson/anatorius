package dev.project.anatorius.utils.network;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class NetStats {
    public static volatile boolean enabled = false;
    private static final Map<String, LongAdder> sent = new ConcurrentHashMap<>();
    private static final Map<String, LongAdder> received = new ConcurrentHashMap<>();
    private static final LongAdder hookNanos = new LongAdder();
    private static volatile long maxHookNanos;

    private NetStats() {
    }

    public static void sent(Object packet) {
        sent.computeIfAbsent(packet.getClass().getSimpleName(), k -> new LongAdder()).increment();
    }

    public static void received(Object packet, long nanos) {
        received.computeIfAbsent(packet.getClass().getSimpleName(), k -> new LongAdder()).increment();
        hookNanos.add(nanos);
        if (nanos > maxHookNanos) maxHookNanos = nanos;
    }

    public static long count(String sentType) {
        LongAdder adder = sent.get(sentType);
        return adder == null ? 0 : adder.sum();
    }

    public static void reset() {
        sent.clear();
        received.clear();
        hookNanos.reset();
        maxHookNanos = 0;
    }

    public static String dump(double seconds) {
        StringBuilder sb = new StringBuilder();
        sb.append("sent/s ");
        append(sb, sent, seconds);
        sb.append(" | recv/s ");
        append(sb, received, seconds);
        sb.append(String.format(" | hook %.2fms/s max %.2fms", hookNanos.sum() / 1e6 / seconds, maxHookNanos / 1e6));
        return sb.toString();
    }

    private static void append(StringBuilder sb, Map<String, LongAdder> map, double seconds) {
        long total = 0;
        Map<String, Long> sorted = new TreeMap<>();
        for (var e : map.entrySet()) {
            sorted.put(e.getKey(), e.getValue().sum());
            total += e.getValue().sum();
        }
        sb.append(String.format("total=%.1f", total / seconds));
        sorted.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(12)
                .forEach(e -> sb.append(String.format(" %s=%.1f", e.getKey().replace("C2SPacket", "").replace("S2CPacket", ""), e.getValue() / seconds)));
    }
}
