package dev.project.anatorius.mixins.accessors;

import net.minecraft.network.packet.BundlePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BundlePacket.class)
public interface BundlePacketAccessor {
    @Accessor("packets") @Mutable
    void anatorius$setPackets(Iterable<?> packets);
}
