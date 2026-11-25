package dev.project.anatorius.customsky.client.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import dev.project.anatorius.customsky.Customskyboxes;
import dev.project.anatorius.customsky.client.command.SkyboxCommand;

@Environment(EnvType.CLIENT)
public record ServerCommandPayload(String command) implements CustomPayload {
   public static final int MAX_LENGTH = 32767;
   public static final CustomPayload.Id<ServerCommandPayload> ID = new CustomPayload.Id(Identifier.of("customskyboxes", "command"));
   public static final PacketCodec<RegistryByteBuf, ServerCommandPayload> CODEC = CustomPayload.codecOf(ServerCommandPayload::write, ServerCommandPayload::read);

   private void write(RegistryByteBuf buf) {
      buf.writeString(this.command, 32767);
   }

   private static ServerCommandPayload read(RegistryByteBuf buf) {
      return new ServerCommandPayload(buf.readString(32767));
   }

   public CustomPayload.Id<? extends CustomPayload> getId() {
      return ID;
   }

   public static void run(MinecraftClient client, String command) {
      if (client.getNetworkHandler() != null && !command.isBlank() && command.indexOf(10) < 0) {
         Customskyboxes.LOGGER.info("Server skybox command: /customskyboxes {}", command);
         SkyboxCommand.runFromServer(() -> client.getNetworkHandler().sendChatCommand("customskyboxes " + command));
      }
   }
}
