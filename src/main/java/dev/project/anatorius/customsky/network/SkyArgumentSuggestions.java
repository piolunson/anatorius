package dev.project.anatorius.customsky.network;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.command.suggestion.SuggestionProviders;
import net.minecraft.util.Identifier;

public final class SkyArgumentSuggestions {
   public static final String ARGUMENT = "arguments";
   private static volatile ClientSide clientSide;
   private static SuggestionProvider<ServerCommandSource> provider;

   private SkyArgumentSuggestions() {
   }

   public static synchronized SuggestionProvider<ServerCommandSource> provider() {
      if (provider == null) {
         provider = SuggestionProviders.register(Identifier.of("customskyboxes", "arguments"), (ctx, builder) -> {
            ClientSide client = clientSide;
            return client == null ? builder.buildFuture() : client.suggest(ctx.getSource(), builder);
         });
      }

      return provider;
   }

   public static void setClientSide(ClientSide client) {
      clientSide = client;
   }

   @FunctionalInterface
   public interface ClientSide {
      CompletableFuture<Suggestions> suggest(Object var1, SuggestionsBuilder var2);
   }
}
