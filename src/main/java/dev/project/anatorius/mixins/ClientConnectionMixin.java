package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import dev.project.anatorius.events.impl.ClientDisconnectEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.modules.impl.miscellaneous.AntiPacketKickModule;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {
    private boolean anatorius$serverSide() {
        return ((ClientConnection) (Object) this).getSide() == net.minecraft.network.NetworkSide.SERVERBOUND;
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void send$HEAD(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, CallbackInfo info) {
        if (anatorius$serverSide()) return;
        if (dev.project.anatorius.utils.minecraft.NetworkUtils.isIgnoring()) return;
        PacketSendEvent event = new PacketSendEvent(packet);
        Anatorius.EVENT_HANDLER.post(event);
        if (event.isCancelled()) {
            info.cancel();
            return;
        }
        if (listener == null && Anatorius.ROTATION_MANAGER != null && Anatorius.ROTATION_MANAGER.defer(packet)) {
            info.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("TAIL"), cancellable = true)
    private void send$TAIL(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, CallbackInfo info) {
        if (anatorius$serverSide()) return;
        if (dev.project.anatorius.utils.network.NetStats.enabled) dev.project.anatorius.utils.network.NetStats.sent(packet);
        Anatorius.EVENT_HANDLER.post(new PacketSendEvent.Post(packet));
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void channelRead0(ChannelHandlerContext channelHandlerContext, Packet<?> packet, CallbackInfo info) {
        if (anatorius$serverSide()) return;
        long statStart = dev.project.anatorius.utils.network.NetStats.enabled ? System.nanoTime() : 0;
        PacketReceiveEvent event = new PacketReceiveEvent(packet);
        Anatorius.EVENT_HANDLER.post(event);
        if (statStart != 0) dev.project.anatorius.utils.network.NetStats.received(packet, System.nanoTime() - statStart);

        if (packet instanceof BundleS2CPacket bundleS2CPacket) {
            java.util.List<Packet<? super ClientPlayPacketListener>> kept = new java.util.ArrayList<>();
            boolean changed = false;
            for (Packet<? super ClientPlayPacketListener> subPacket : bundleS2CPacket.getPackets()) {
                PacketReceiveEvent subEvent = new PacketReceiveEvent(subPacket);
                Anatorius.EVENT_HANDLER.post(subEvent);
                if (subEvent.isCancelled()) changed = true;
                else kept.add(subPacket);
            }
            if (changed) ((dev.project.anatorius.mixins.accessors.BundlePacketAccessor) bundleS2CPacket).anatorius$setPackets(kept);
        }

        if (event.isCancelled()) {
            info.cancel();
            return;
        }

        if (packet instanceof net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket keepAlive) {
            ((net.minecraft.network.ClientConnection) (Object) this).send(new net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket(keepAlive.getId()));
            info.cancel();
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionInfo;)V", at = @At("HEAD"))
    private void disconnect(DisconnectionInfo disconnectionInfo, CallbackInfo info) {
        Anatorius.EVENT_HANDLER.post(new ClientDisconnectEvent());
    }

    @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
    private void exceptionCaught(ChannelHandlerContext context, Throwable ex, CallbackInfo info) {
        if (Anatorius.MODULE_MANAGER.getModule(AntiPacketKickModule.class).isToggled()) {
            Anatorius.CHAT_MANAGER.error("An exception happened in the packet handler. Check stacktrace for more details.");
            Anatorius.LOGGER.error("The packet handler has thrown an exception!", ex);
            info.cancel();
        }
    }
}
