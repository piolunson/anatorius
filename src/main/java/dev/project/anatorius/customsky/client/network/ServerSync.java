package dev.project.anatorius.customsky.client.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ServerSync {
   private static volatile Channel channel;

   private ServerSync() {
   }

   public static void bind(Channel loader) {
      channel = loader;
   }

   public static void tell(String command) {
      Channel loader = channel;
      if (loader != null && command != null && !command.isBlank()) {
         try {
            loader.send(command);
         } catch (Throwable var3) {
         }
      }
   }

   @FunctionalInterface
   @Environment(EnvType.CLIENT)
   public interface Channel {
      void send(String var1);
   }
}
