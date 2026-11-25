package dev.project.anatorius.customsky.network;

import net.minecraft.util.Identifier;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

public record SkyForgetPayload(String command) implements CustomPayload {
   public static final int MAX_LENGTH = 32767;
   public static final CustomPayload.Id<SkyForgetPayload> ID = new CustomPayload.Id(Identifier.of("customskyboxes", "forget"));
   public static final PacketCodec<RegistryByteBuf, SkyForgetPayload> CODEC = CustomPayload.codecOf(SkyForgetPayload::write, SkyForgetPayload::read);

   private void write(RegistryByteBuf buf) {
      buf.writeString(this.command, 32767);
   }

   private static SkyForgetPayload read(RegistryByteBuf buf) {
      return new SkyForgetPayload(buf.readString(32767));
   }

   public CustomPayload.Id<? extends CustomPayload> getId() {
      return ID;
   }
}
