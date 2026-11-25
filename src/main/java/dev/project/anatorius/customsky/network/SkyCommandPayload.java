package dev.project.anatorius.customsky.network;

import net.minecraft.util.Identifier;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

public record SkyCommandPayload(String command) implements CustomPayload {
   public static final int MAX_LENGTH = 32767;
   public static final CustomPayload.Id<SkyCommandPayload> ID = new CustomPayload.Id(Identifier.of("customskyboxes", "command"));
   public static final PacketCodec<RegistryByteBuf, SkyCommandPayload> CODEC = CustomPayload.codecOf(SkyCommandPayload::write, SkyCommandPayload::read);

   private void write(RegistryByteBuf buf) {
      buf.writeString(this.command, 32767);
   }

   private static SkyCommandPayload read(RegistryByteBuf buf) {
      return new SkyCommandPayload(buf.readString(32767));
   }

   public CustomPayload.Id<? extends CustomPayload> getId() {
      return ID;
   }
}
