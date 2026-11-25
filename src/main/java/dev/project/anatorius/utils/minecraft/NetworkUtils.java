package dev.project.anatorius.utils.minecraft;

import dev.project.anatorius.mixins.accessors.ClientWorldAccessor;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.network.SequencedPacketCreator;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.*;

public class NetworkUtils implements IMinecraft {
    private static final ThreadLocal<Boolean> IGNORING = ThreadLocal.withInitial(() -> false);

    public static boolean isIgnoring() {
        return IGNORING.get();
    }

    public static void sendIgnoredPacket(Packet<?> packet) {
        IGNORING.set(true);
        try {
            mc.getNetworkHandler().getConnection().send(packet, null, true);
        } finally {
            IGNORING.set(false);
        }
    }

    public static void sendSequencedPacket(SequencedPacketCreator packetCreator) {
        try (PendingUpdateManager pendingUpdateManager = ((ClientWorldAccessor)mc.world).invokeGetPendingUpdateManager().incrementSequence();){
            Packet<ServerPlayPacketListener> packet = packetCreator.predict(pendingUpdateManager.getSequence());
            mc.getNetworkHandler().sendPacket(packet);
        }
    }
}
