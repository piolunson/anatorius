package dev.project.anatorius.customsky.client.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class TokenArgument implements ArgumentType<String> {
   private static final TokenArgument INSTANCE = new TokenArgument();

   public static TokenArgument token() {
      return INSTANCE;
   }

   public String parse(StringReader reader) throws CommandSyntaxException {
      if (reader.canRead() && StringReader.isQuotedStringStart(reader.peek())) {
         return reader.readQuotedString();
      } else {
         int start = reader.getCursor();

         while(reader.canRead() && reader.peek() != ' ') {
            reader.skip();
         }

         return reader.getString().substring(start, reader.getCursor());
      }
   }

   public Collection<String> getExamples() {
      return List.of("galaxy.png", "space/galaxy.png", "mypack:galaxy.png");
   }
}
