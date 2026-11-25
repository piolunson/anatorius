package dev.project.anatorius.customsky.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Formatting;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.registry.RegistryKeys;
import dev.project.anatorius.customsky.Customskyboxes;
import dev.project.anatorius.customsky.client.config.SkyboxConfig;
import dev.project.anatorius.customsky.client.model.SkyModels;
import dev.project.anatorius.customsky.client.network.ServerSync;
import dev.project.anatorius.customsky.client.resource.ResourcePackScanner;
import dev.project.anatorius.customsky.client.sky.CelestialPreset;
import dev.project.anatorius.customsky.client.sky.RiftEffects;
import dev.project.anatorius.customsky.client.sky.SkyEffects;
import dev.project.anatorius.customsky.client.sky.SkyParam;
import dev.project.anatorius.customsky.client.sky.SkyParams;
import dev.project.anatorius.customsky.client.sky.SkyboxEntry;
import dev.project.anatorius.customsky.client.sky.SkyboxManager;
import dev.project.anatorius.customsky.client.sky.SkyboxType;
import dev.project.anatorius.customsky.network.SkyArgumentSuggestions;

@Environment(EnvType.CLIENT)
public final class SkyboxCommand {
   private static final LinkedHashMap<String, Integer> NAMED_COLORS = new LinkedHashMap();
   private static final SuggestionProvider<Object> TYPE_OR_ALL_SUGG;
   private static final SuggestionProvider<Object> BIOME_SUGG;
   private static final SuggestionProvider<Object> RUNNING_SUGG;
   private static CommandDispatcher<Object> own;
   private static boolean afterTargets;
   private static boolean forwarding;
   private static boolean fromServer;

   private SkyboxCommand() {
   }

   private static LiteralArgumentBuilder<Object> literal(String name) {
      return LiteralArgumentBuilder.literal(name);
   }

   private static <T> RequiredArgumentBuilder<Object, T> argument(String name, ArgumentType<T> type) {
      return RequiredArgumentBuilder.argument(name, type);
   }

   private static void chat(Text text) {
      MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(text);
   }

   private static void chatError(Text text) {
      chat(Text.empty().append(text).formatted(Formatting.RED));
   }

   private static SuggestionProvider<Object> valueSuggFor(SkyboxType type) {
      return (ctx, b) -> {
         String rem = b.getRemaining();
         if (!type.isTextureBased()) {
            String remU = rem.toUpperCase();

            for(String name : NAMED_COLORS.keySet()) {
               if (name.startsWith(remU)) {
                  b.suggest(name);
               }
            }
         } else {
            if ("vanilla".startsWith(rem.toLowerCase())) {
               b.suggest("vanilla");
            }

            for(String name : ResourcePackScanner.get().list(type)) {
               if (name.startsWith(rem)) {
                  b.suggest(name);
               }
            }
         }

         return b.buildFuture();
      };
   }

   private static SuggestionProvider<Object> argsSuggFor(SkyboxType type) {
      return (ctx, b) -> suggestWords(b, SkyParams.suggest(type, b.getRemaining()));
   }

   private static SuggestionProvider<Object> animateArgsSugg(SkyboxType type) {
      return (ctx, b) -> {
         String word = SkyParams.currentWord(b.getRemaining());
         List<String> out = new ArrayList();
         boolean scaleOnly = "scale".equalsIgnoreCase(presetOf(ctx));
         if (word.startsWith("color=")) {
            String partial = word.substring("color=".length()).toUpperCase();
            LinkedHashSet<String> names = new LinkedHashSet(NAMED_COLORS.keySet());
            names.addAll(SkyParams.colorNames());

            for(String name : names) {
               if (name.startsWith(partial)) {
                  out.add("color=" + name);
               }
            }
         } else if (word.startsWith("loop=")) {
            for(String value : List.of("true", "false")) {
               if (value.startsWith(word.substring("loop=".length()))) {
                  out.add("loop=" + value);
               }
            }
         } else if (word.startsWith("layer=")) {
            for(int i = 1; i <= 8; ++i) {
               out.add("layer=" + i);
            }
         } else if (type.isRift() && word.matches("(?i)(delay|spin|shake|glow|flash)=.*")) {
            String k = word.substring(0, word.indexOf(61) + 1);
            List<String> var10000;
            switch (k.toLowerCase()) {
               case "delay=" -> var10000 = List.of("10", "20", "40");
               case "spin=" -> var10000 = List.of("90", "180", "360", "-360", "720");
               default -> var10000 = List.of("0", "0.5", "2", "3");
            }

            for(String v : var10000) {
               if ((k + v).startsWith(word)) {
                  out.add(k + v);
               }
            }
         } else if (!word.startsWith("strength=") && !word.startsWith("size=") && !word.startsWith("scale=")) {
            List<String> used = SkyParams.usedBefore(b.getRemaining());
            List<String> keys = new ArrayList(scaleOnly ? List.of("scale", "loop") : List.of("color", "strength", "size", "loop"));
            if (type.hasLayers()) {
               keys.add("layer");
            }

            if (type.isRift() && !scaleOnly) {
               keys.addAll(List.of("delay", "spin", "shake", "glow", "flash"));
            }

            if (used.isEmpty() && !b.getRemaining().contains(" ")) {
               for(String ticks : List.of("20", "60", "100", "200")) {
                  if (ticks.startsWith(word)) {
                     out.add(ticks);
                  }
               }
            }

            for(String key : keys) {
               if (!used.contains(key) && (key + "=").startsWith(word)) {
                  out.add(key + "=");
               }
            }
         } else {
            String var10001 = word.substring(0, word.indexOf(61) + 1);
            out.add(var10001 + (word.startsWith("scale=") ? "2" : "1"));
         }

         return suggestWords(b, out);
      };
   }

   private static String presetOf(CommandContext<?> ctx) {
      try {
         return StringArgumentType.getString(ctx, "preset");
      } catch (Exception var2) {
         return null;
      }
   }

   private static CompletableFuture<Suggestions> suggestWords(SuggestionsBuilder builder, List<String> words) {
      String remaining = builder.getRemaining();
      int wordStart = remaining.lastIndexOf(32) + 1;
      SuggestionsBuilder wordBuilder = builder.createOffset(builder.getStart() + wordStart);

      for(String w : words) {
         wordBuilder.suggest(w);
      }

      return wordBuilder.buildFuture();
   }

   private static String biomeArg(CommandContext<?> ctx) {
      return ((Identifier)ctx.getArgument("biome", Identifier.class)).toString();
   }

   private static SuggestionProvider<Object> presetSuggFor(SkyboxType type) {
      return (ctx, b) -> {
         String rem = b.getRemaining().toLowerCase();
         List<String> names = new ArrayList(type.isRift() ? RiftEffects.Effect.names() : (type.isDome() ? SkyEffects.Effect.names() : CelestialPreset.names()));
         names.add("stop");

         for(String name : names) {
            if (name.startsWith(rem)) {
               b.suggest(name);
            }
         }

         return b.buildFuture();
      };
   }

   private static List<String> layerSuggestions(SuggestionsBuilder b) {
      String word = SkyParams.currentWord(b.getRemaining());
      List<String> out = new ArrayList();
      if (word.startsWith("layer=")) {
         out.add("layer=1");
      } else if ("layer=".startsWith(word)) {
         out.add("layer=");
      }

      return out;
   }

   public static void register(CommandDispatcher<?> commandDispatcher) {
      LiteralArgumentBuilder<Object> setNode = literal("set");

      for(SkyboxType type : SkyboxType.values()) {
         setNode.then(literal(type.id()).then(((RequiredArgumentBuilder)argument("value", TokenArgument.token()).suggests(valueSuggFor(type)).executes((ctx) -> doSet(ctx, type, ""))).then(argument("args", StringArgumentType.greedyString()).suggests(argsSuggFor(type)).executes((ctx) -> doSet(ctx, type, StringArgumentType.getString(ctx, "args"))))));
      }

      LiteralArgumentBuilder<Object> clearNode = literal("clear");
      List<String> clearable = new ArrayList(SkyboxType.allIds());
      clearable.add("all");

      for(String typeStr : clearable) {
         clearNode.then(((LiteralArgumentBuilder)literal(typeStr).executes((ctx) -> doClear(typeStr, ""))).then(argument("args", StringArgumentType.greedyString()).suggests((ctx, b) -> suggestWords(b, layerSuggestions(b))).executes((ctx) -> doClear(typeStr, StringArgumentType.getString(ctx, "args")))));
      }

      LiteralArgumentBuilder<Object> reloadNode = (LiteralArgumentBuilder)literal("reload").executes((ctx) -> {
         ResourcePackScanner.get().rescan();
         csFeedback(Text.literal("[CustomSkyboxes] Resource packs rescanned").formatted(Formatting.GREEN));
         return 1;
      });
      LiteralArgumentBuilder<Object> animateNode = literal("animate");

      for(SkyboxType type : SkyboxType.values()) {
         if (type.isDome() || type.isCelestial() || type.isRift()) {
            animateNode.then(literal(type.id()).then(((RequiredArgumentBuilder)argument("preset", StringArgumentType.word()).suggests(presetSuggFor(type)).executes((ctx) -> doAnimate(ctx, type, 60, "", (String)null))).then(argument("args", StringArgumentType.greedyString()).suggests(animateArgsSugg(type)).executes((ctx) -> doAnimateArgs(ctx, type, StringArgumentType.getString(ctx, "args"), (String)null)))));
         }
      }

      animateNode.then(((LiteralArgumentBuilder)literal("stop").executes((ctx) -> {
         SkyEffects.stopAll();
         RiftEffects.stopAll();
         report("animate stop");
         csFeedback(Text.literal("[CustomSkyboxes] Animations stopped").formatted(Formatting.YELLOW));
         return 1;
      })).then(argument("name", StringArgumentType.word()).suggests(RUNNING_SUGG).executes((ctx) -> {
         String name = StringArgumentType.getString(ctx, "name");
         if (!SkyEffects.stopEverywhere(name)) {
            chatError(Text.literal("Nothing called " + name + " is playing"));
            return 0;
         } else {
            csFeedback(Text.literal("[CustomSkyboxes] " + name + " stopped").formatted(Formatting.YELLOW));
            return 1;
         }
      })));
      LiteralArgumentBuilder<Object> biomeSetNode = literal("set");

      for(SkyboxType type : SkyboxType.values()) {
         biomeSetNode.then(argument("biome", IdentifierArgumentType.identifier()).suggests(BIOME_SUGG).then(literal(type.id()).then(((RequiredArgumentBuilder)argument("value", TokenArgument.token()).suggests(valueSuggFor(type)).executes((ctx) -> doBiomeSet(ctx, type, ""))).then(argument("args", StringArgumentType.greedyString()).suggests(argsSuggFor(type)).executes((ctx) -> doBiomeSet(ctx, type, StringArgumentType.getString(ctx, "args")))))));
      }

      LiteralArgumentBuilder<Object> biomeAnimateNode = literal("animate");

      for(SkyboxType type : SkyboxType.values()) {
         if (type.isDome() || type.isCelestial()) {
            biomeAnimateNode.then(argument("biome", IdentifierArgumentType.identifier()).suggests(BIOME_SUGG).then(literal(type.id()).then(((RequiredArgumentBuilder)argument("preset", StringArgumentType.word()).suggests(presetSuggFor(type)).executes((ctx) -> doAnimate(ctx, type, 60, "", biomeArg(ctx)))).then(argument("args", StringArgumentType.greedyString()).suggests(animateArgsSugg(type)).executes((ctx) -> doAnimateArgs(ctx, type, StringArgumentType.getString(ctx, "args"), biomeArg(ctx)))))));
         }
      }

      LiteralArgumentBuilder<Object> biomeNode = (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)literal("biome").then(biomeSetNode)).then(biomeAnimateNode)).then(literal("list").executes(SkyboxCommand::doBiomeList))).then(literal("clear").then(argument("biome", IdentifierArgumentType.identifier()).suggests(BIOME_SUGG).then(argument("tipo", StringArgumentType.word()).suggests(TYPE_OR_ALL_SUGG).executes(SkyboxCommand::doBiomeClear))));
      LiteralArgumentBuilder<Object> modelSetNode = literal("set");
      LiteralArgumentBuilder<Object> modelAnimateNode = literal("animate");

      for(SkyboxType type : new SkyboxType[]{SkyboxType.SUN, SkyboxType.MOON, SkyboxType.CUSTOM, SkyboxType.RIFT}) {
         if (!type.isRift()) {
            modelSetNode.then(literal(type.id()).then(((RequiredArgumentBuilder)argument("model", TokenArgument.token()).suggests((ctx, b) -> suggestWords(b, startingWith(SkyModels.names(), b.getRemaining()))).executes((ctx) -> doModelSet(ctx, type, ""))).then(argument("args", StringArgumentType.greedyString()).suggests(argsSuggFor(type)).executes((ctx) -> doModelSet(ctx, type, StringArgumentType.getString(ctx, "args"))))));
         }

         modelAnimateNode.then(literal(type.id()).then(((RequiredArgumentBuilder)argument("animation", StringArgumentType.word()).suggests((ctx, b) -> suggestWords(b, startingWith(SkyModels.animationNames(SkyboxManager.get().activeLayers(type)), b.getRemaining()))).executes((ctx) -> doModelAnimate(ctx, type, ""))).then(argument("args", StringArgumentType.greedyString()).suggests((ctx, b) -> suggestWords(b, layerSuggestions(b))).executes((ctx) -> doModelAnimate(ctx, type, StringArgumentType.getString(ctx, "args"))))));
      }

      LiteralArgumentBuilder<Object> modelNode = (LiteralArgumentBuilder)((LiteralArgumentBuilder)literal("model").then(modelSetNode)).then(modelAnimateNode);
      setNode.then(targeted("set"));
      clearNode.then(targeted("clear"));
      reloadNode.then(targeted("reload"));
      animateNode.then(targeted("animate"));
      biomeNode.then(targeted("biome"));
      modelNode.then(targeted("model"));
      commandDispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)literal("customskyboxes").then(setNode)).then(clearNode)).then(reloadNode)).then(animateNode)).then(biomeNode)).then(modelNode));
      own = (CommandDispatcher<Object>)(CommandDispatcher)commandDispatcher;
      SkyArgumentSuggestions.setClientSide(SkyboxCommand::suggestWholeCommand);
      Customskyboxes.LOGGER.info("CustomSkyboxes command registered");
   }

   public static void runFromServer(Runnable run) {
      fromServer = true;

      try {
         run.run();
      } finally {
         fromServer = false;
      }
   }

   private static RequiredArgumentBuilder<Object, ?> targeted(String sub) {
      return (RequiredArgumentBuilder)((RequiredArgumentBuilder)((RequiredArgumentBuilder)argument("targets", EntityArgumentType.players()).requires((source) -> serverTakesTargets())).suggests((ctx, b) -> afterTargets ? b.buildFuture() : EntityArgumentType.players().listSuggestions(ctx, b)).executes(SkyboxCommand::sendToServer)).then(argument("rest", StringArgumentType.greedyString()).suggests((ctx, b) -> suggestAfterTargets(sub, ctx.getSource(), b)).executes(SkyboxCommand::sendToServer));
   }

   private static boolean serverTakesTargets() {
      ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
      if (handler == null) {
         return false;
      } else {
         CommandNode<?> root = handler.getCommandDispatcher().getRoot().getChild("customskyboxes");
         CommandNode<?> set = root == null ? null : root.getChild("set");
         return set != null && set.getChild("arguments") != null;
      }
   }

   private static int sendToServer(CommandContext<?> ctx) throws CommandSyntaxException {
      if (forwarding) {
         throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
      } else if (fromServer) {
         chatError(Text.literal("A command sent by the server can't name other players."));
         return 0;
      } else {
         ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
         if (handler == null) {
            return 0;
         } else {
            forwarding = true;

            try {
               handler.sendChatCommand(ctx.getInput());
            } finally {
               forwarding = false;
            }

            return 1;
         }
      }
   }

   private static CompletableFuture<Suggestions> suggestAfterTargets(String sub, Object source, SuggestionsBuilder b) {
      String prefix = "customskyboxes " + sub + " ";
      afterTargets = true;

      CompletableFuture var4;
      try {
         var4 = suggestShifted(prefix + b.getRemaining(), b.getStart() - prefix.length(), source, b.getInput());
      } finally {
         afterTargets = false;
      }

      return var4;
   }

   public static CompletableFuture<Suggestions> suggestWholeCommand(Object source, SuggestionsBuilder b) {
      String input = b.getInput();
      int at = input.indexOf("customskyboxes");
      return at < 0 ? b.buildFuture() : suggestShifted(input.substring(at), at, source, input);
   }

   private static CompletableFuture<Suggestions> suggestShifted(String command, int shift, Object source, String input) {
      CommandDispatcher<Object> dispatcher = own;
      if (dispatcher == null) {
         return Suggestions.empty();
      } else {
         ParseResults<Object> parse = dispatcher.parse(command, source);
         return dispatcher.getCompletionSuggestions(parse).thenApply((found) -> {
            List<Suggestion> moved = new ArrayList();

            for(Suggestion s : found.getList()) {
               StringRange r = s.getRange();
               moved.add(new Suggestion(new StringRange(r.getStart() + shift, r.getEnd() + shift), s.getText(), s.getTooltip()));
            }

            return Suggestions.create(input, moved);
         });
      }
   }

   private static List<String> startingWith(List<String> words, String typed) {
      List<String> out = new ArrayList();

      for(String w : words) {
         if (w.toLowerCase().startsWith(typed.toLowerCase())) {
            out.add(w);
         }
      }

      return out;
   }

   private static int doModelSet(CommandContext<?> ctx, SkyboxType type, String rawArgs) {
      String model = StringArgumentType.getString(ctx, "model");
      if (SkyModels.get(model) == null) {
         chatError(Text.literal("No model called " + model + ". Put it in assets/customskyboxes/geo/" + model + ".geo.json (animations in animations/, texture in textures/geo/)"));
         return 0;
      } else {
         SkyboxEntry entry = buildEntry(type, "vanilla", (rawArgs + " model=" + model).trim());
         if (entry == null) {
            return 0;
         } else {
            SkyboxManager.get().setGlobal(type, entry);
            SkyboxConfig.save();
            String var10000 = describe(type, entry);
            csFeedback(Text.literal("[CustomSkyboxes] " + var10000 + " is now the model " + model).formatted(Formatting.GREEN));
            return 1;
         }
      }
   }

   private static int doModelAnimate(CommandContext<?> ctx, SkyboxType type, String rawArgs) {
      String animation = StringArgumentType.getString(ctx, "animation");
      int layer = readLayer(rawArgs);
      if (layer < 0) {
         chatError(Text.literal("Bad value for layer"));
         return 0;
      } else {
         if (layer == 0) {
            layer = 1;
         }

         SkyboxEntry entry = null;

         for(SkyboxEntry e : SkyboxManager.get().activeLayers(type)) {
            if (e.layer() == layer) {
               entry = e;
            }
         }

         SkyModels.Loaded loaded = SkyModels.forEntry(entry);
         if (loaded == null) {
            chatError(Text.literal(type.isRift() ? "That rift has no model. Add model=<model> to /customskyboxes set rift" : "The " + type.id() + " has no model. Use /customskyboxes model set " + type.id() + " <model> first"));
            return 0;
         } else if (!"stop".equalsIgnoreCase(animation) && SkyModels.find(loaded, animation) == null) {
            chatError(Text.literal("The model has no animation " + animation + ". It has: " + String.join(", ", SkyModels.animationNames(loaded))));
            return 0;
         } else {
            SkyModels.play(type, layer, animation);
            String var10000 = type.id();
            csFeedback(Text.literal("[CustomSkyboxes] " + var10000 + " model: " + animation).formatted(Formatting.GREEN));
            return 1;
         }
      }
   }

   private static int doSet(CommandContext<?> ctx, SkyboxType type, String rawArgs) {
      String value = StringArgumentType.getString(ctx, "value");
      SkyboxEntry entry = buildEntry(type, value, rawArgs);
      if (entry == null) {
         return 0;
      } else {
         SkyboxManager.get().setGlobal(type, entry);
         SkyboxConfig.save();
         String var10000 = describe(type, entry);
         csFeedback(Text.literal("[CustomSkyboxes] " + var10000 + " set to " + value).formatted(Formatting.GREEN));
         return 1;
      }
   }

   private static int doBiomeSet(CommandContext<?> ctx, SkyboxType type, String rawArgs) {
      Identifier biome = (Identifier)ctx.getArgument("biome", Identifier.class);
      String value = StringArgumentType.getString(ctx, "value");
      SkyboxEntry entry = buildEntry(type, value, rawArgs);
      if (entry == null) {
         return 0;
      } else {
         SkyboxManager.get().setBiome(biome, type, entry);
         SkyboxConfig.save();
         csFeedback(Text.literal("[CustomSkyboxes] " + describe(type, entry) + " set to " + value + " for biome " + String.valueOf(biome)).formatted(Formatting.GREEN));
         return 1;
      }
   }

   private static int doClear(String typeStr, String rawArgs) {
      report(("clear " + typeStr + " " + rawArgs).trim());
      if ("all".equalsIgnoreCase(typeStr)) {
         SkyboxManager.get().clearAllGlobal();
         SkyEffects.stopAll();
         SkyboxConfig.save();
         csFeedback(Text.literal("[CustomSkyboxes] All global overrides cleared").formatted(Formatting.YELLOW));
         return 1;
      } else {
         Optional<SkyboxType> opt = SkyboxType.fromId(typeStr);
         if (opt.isEmpty()) {
            chatError(Text.literal("Unknown type: " + typeStr));
            return 0;
         } else {
            SkyboxType type = (SkyboxType)opt.get();
            int layer = readLayer(rawArgs);
            if (layer < 0) {
               chatError(Text.literal("Bad value for layer"));
               return 0;
            } else if (layer > 0 && type.hasLayers()) {
               SkyboxManager.get().clearGlobalLayer(type, layer);
               SkyboxConfig.save();
               String var10000 = type.id();
               csFeedback(Text.literal("[CustomSkyboxes] Cleared global " + var10000 + " layer " + layer).formatted(Formatting.YELLOW));
               return 1;
            } else {
               SkyboxManager.get().clearGlobal(type);
               SkyboxConfig.save();
               csFeedback(Text.literal("[CustomSkyboxes] Cleared global " + type.id()).formatted(Formatting.YELLOW));
               return 1;
            }
         }
      }
   }

   private static int doAnimateStop(SkyboxType type) {
      if (type.isRift()) {
         RiftEffects.stopAll();
      } else {
         SkyEffects.stop(type);
      }

      report("animate " + type.id() + " stop");
      csFeedback(Text.literal("[CustomSkyboxes] " + type.id() + " animations stopped").formatted(Formatting.YELLOW));
      return 1;
   }

   private static void report(String command) {
      if (!fromServer) {
         ServerSync.tell(command);
      }
   }

   private static int doAnimateArgs(CommandContext<?> ctx, SkyboxType type, String rawArgs, String biome) {
      String rest = rawArgs.trim();
      int time = 60;
      String first = rest.split("\\s+", 2)[0];
      if (!first.isEmpty() && first.chars().allMatch(Character::isDigit)) {
         try {
            time = Integer.parseInt(first);
         } catch (NumberFormatException var8) {
            time = -1;
         }

         if (time < 1 || time > 72000) {
            chatError(Text.literal("The time must be between 1 and 72000 ticks"));
            return 0;
         }

         rest = rest.substring(first.length()).trim();
      }

      return doAnimate(ctx, type, time, rest, biome);
   }

   private static int doAnimate(CommandContext<?> ctx, SkyboxType type, int time, String rawArgs, String biome) {
      String presetName = StringArgumentType.getString(ctx, "preset");
      if (presetName.equalsIgnoreCase("stop")) {
         return doAnimateStop(type);
      } else {
         int color = 16777215;
         float strength = 1.0F;
         float size = 1.0F;
         boolean loop = false;
         float scaleUpTo = 2.0F;
         int layer = 0;
         boolean colorGiven = false;
         int delay = 0;
         float spin = 0.0F;
         float shake = 1.0F;
         float glow = 1.0F;
         float flash = 1.0F;

         for(String token : rawArgs.trim().split("\\s+")) {
            if (!token.isBlank()) {
               int eq = token.indexOf(61);
               if (eq <= 0) {
                  chatError(Text.literal("Arguments look like name=value: " + token));
                  return 0;
               }

               String key = token.substring(0, eq);
               String value = token.substring(eq + 1);
               if (key.equalsIgnoreCase("color")) {
                  color = parseColor(value);
                  if (color < 0) {
                     chatError(Text.literal("Invalid colour: " + value));
                     return 0;
                  }

                  colorGiven = true;
               } else if (!type.isRift() || !key.equalsIgnoreCase("delay") && !key.equalsIgnoreCase("spin") && !key.equalsIgnoreCase("shake") && !key.equalsIgnoreCase("glow") && !key.equalsIgnoreCase("flash")) {
                  if (key.equalsIgnoreCase("strength")) {
                     try {
                        strength = Float.parseFloat(value);
                     } catch (NumberFormatException var33) {
                        chatError(Text.literal("Bad value for strength: " + value));
                        return 0;
                     }

                     if (strength < 0.0F || strength > 5.0F) {
                        chatError(Text.literal("strength goes from 0 to 5"));
                        return 0;
                     }
                  } else if (key.equalsIgnoreCase("size")) {
                     try {
                        size = Float.parseFloat(value);
                     } catch (NumberFormatException var32) {
                        chatError(Text.literal("Bad value for size: " + value));
                        return 0;
                     }

                     if (size < 0.1F || size > 5.0F) {
                        chatError(Text.literal("size goes from 0.1 to 5"));
                        return 0;
                     }
                  } else if (key.equalsIgnoreCase("scale")) {
                     try {
                        scaleUpTo = Float.parseFloat(value);
                     } catch (NumberFormatException var31) {
                        chatError(Text.literal("Bad value for scale: " + value));
                        return 0;
                     }

                     if (scaleUpTo < 0.0F || scaleUpTo > 100.0F) {
                        chatError(Text.literal("scale goes from 0 to 100"));
                        return 0;
                     }
                  } else if (key.equalsIgnoreCase("loop")) {
                     if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                        chatError(Text.literal("loop is true or false, not " + value));
                        return 0;
                     }

                     loop = value.equalsIgnoreCase("true");
                  } else {
                     if (!key.equalsIgnoreCase("layer") || !type.isRift()) {
                        chatError(Text.literal(type.isRift() ? "animate rift takes color, strength, size, loop, layer, delay, spin, shake, glow and flash, not " + key : "animate takes color, strength, size, scale and loop, not " + key));
                        return 0;
                     }

                     try {
                        layer = Integer.parseInt(value);
                     } catch (NumberFormatException var30) {
                        chatError(Text.literal("Bad value for layer: " + value));
                        return 0;
                     }
                  }
               } else {
                  float number;
                  try {
                     number = Float.parseFloat(value);
                  } catch (NumberFormatException var34) {
                     chatError(Text.literal("Bad value for " + key + ": " + value));
                     return 0;
                  }

                  String k = key.toLowerCase();
                  float[] var10000;
                  switch (k) {
                     case "delay" -> var10000 = new float[]{0.0F, 72000.0F};
                     case "spin" -> var10000 = new float[]{-3600.0F, 3600.0F};
                     default -> var10000 = new float[]{0.0F, 10.0F};
                  }

                  float[] range = var10000;
                  if (number < range[0] || number > range[1]) {
                     chatError(Text.literal(k + " goes from " + Math.round(range[0]) + " to " + Math.round(range[1])));
                     return 0;
                  }

                  switch (k) {
                     case "delay":
                        delay = Math.round(number);
                        break;
                     case "spin":
                        spin = number;
                        break;
                     case "shake":
                        shake = number;
                        break;
                     case "glow":
                        glow = number;
                        break;
                     default:
                        flash = number;
                  }
               }
            }
         }

         if (type.isRift()) {
            Optional<RiftEffects.Effect> effect = RiftEffects.Effect.byId(presetName);
            if (effect.isEmpty()) {
               chatError(Text.literal("Unknown animation: " + presetName));
               return 0;
            }

            RiftEffects.start((RiftEffects.Effect)effect.get(), time, strength, loop, Math.max(0, layer), new RiftEffects.Options(colorGiven ? color : -1, size, delay, spin, shake, glow, flash));
         } else if (type.isDome()) {
            Optional<SkyEffects.Effect> effect = SkyEffects.Effect.byId(presetName);
            if (effect.isEmpty()) {
               chatError(Text.literal("Unknown animation: " + presetName));
               return 0;
            }

            SkyEffects.start(type, (SkyEffects.Effect)effect.get(), time, color, strength, size, loop, biome);
         } else {
            Optional<CelestialPreset> preset = CelestialPreset.byId(presetName);
            if (preset.isEmpty()) {
               chatError(Text.literal("Unknown animation: " + presetName));
               return 0;
            }

            SkyEffects.start(type, (CelestialPreset)preset.get(), time, strength, loop, biome, scaleUpTo);
         }

         String var39 = biome == null ? "animate " : "biome animate " + biome + " ";
         report(var39 + type.id() + " " + presetName + " " + time + " " + rawArgs.trim() + (!loop && !rawArgs.toLowerCase().contains("loop=") ? "" : ""));
         csFeedback(Text.literal("[CustomSkyboxes] " + presetName + " on " + type.id() + " for " + time + " ticks" + (loop ? " on loop" : "") + (biome == null ? "" : " in " + biome)).formatted(Formatting.AQUA));
         return 1;
      }
   }

   private static int doBiomeList(CommandContext<?> ctx) {
      Map<Identifier, Map<SkyboxManager.SkyKey, SkyboxEntry>> all = SkyboxManager.get().snapshotBiomes();
      if (all.isEmpty()) {
         csFeedback(Text.literal("No biome overrides").formatted(Formatting.GRAY));
         return 1;
      } else {
         all.forEach((biome, m) -> {
            csFeedback(Text.literal("== " + String.valueOf(biome) + " ==").formatted(Formatting.GOLD));
            m.forEach((key, e) -> {
               String what = key.type().isTextureBased() ? (e.isVanilla() ? "vanilla" : String.valueOf(e.texture())) : String.format("#%06x", e.color());
               csFeedback(Text.literal("  " + String.valueOf(key) + " -> " + what + " " + printArgs(e)));
            });
         });
         return 1;
      }
   }

   private static int doBiomeClear(CommandContext<?> ctx) {
      Identifier biome = (Identifier)ctx.getArgument("biome", Identifier.class);
      String typeStr = StringArgumentType.getString(ctx, "tipo");
      if ("all".equalsIgnoreCase(typeStr)) {
         SkyboxManager.get().clearAllForBiome(biome);
         SkyboxConfig.save();
         csFeedback(Text.literal("[CustomSkyboxes] Cleared all overrides for biome " + String.valueOf(biome)).formatted(Formatting.YELLOW));
         return 1;
      } else {
         Optional<SkyboxType> opt = SkyboxType.fromId(typeStr);
         if (opt.isEmpty()) {
            chatError(Text.literal("Unknown type: " + typeStr));
            return 0;
         } else {
            SkyboxManager.get().clearBiome(biome, (SkyboxType)opt.get());
            SkyboxConfig.save();
            String var10000 = ((SkyboxType)opt.get()).id();
            csFeedback(Text.literal("[CustomSkyboxes] Cleared " + var10000 + " for biome " + String.valueOf(biome)).formatted(Formatting.YELLOW));
            return 1;
         }
      }
   }

   private static SkyboxEntry buildEntry(SkyboxType type, String value, String rawArgs) {
      SkyParams.Result parsed = SkyParams.parse(type, rawArgs);
      if (!parsed.ok()) {
         chatError(Text.literal(parsed.error()));
         return null;
      } else {
         SkyParams params = parsed.params();
         if (!type.isTextureBased()) {
            int color = parseColor(value);
            if (color < 0) {
               chatError(Text.literal("Invalid colour: " + value + " (use a preset like RED / BLUE or #rrggbb)"));
               return null;
            } else {
               return SkyboxEntry.ofColor(color, params);
            }
         } else if ("vanilla".equalsIgnoreCase(value) && type.isTextureBased()) {
            return SkyboxEntry.ofVanilla(params);
         } else {
            Identifier tex = ResourcePackScanner.resolveTexture(type, value);
            if (tex == null) {
               chatError(Text.literal("Invalid texture id: " + value));
               return null;
            } else {
               return SkyboxEntry.ofTexture(tex, ResourcePackScanner.isGif(tex), params);
            }
         }
      }
   }

   private static int readLayer(String rawArgs) {
      if (rawArgs != null && !rawArgs.isBlank()) {
         int found = 0;

         for(String token : rawArgs.trim().split("\\s+")) {
            if (!token.isBlank()) {
               int eq = token.indexOf(61);
               if (eq <= 0 || !token.substring(0, eq).equalsIgnoreCase(SkyParam.LAYER.key())) {
                  return -1;
               }

               try {
                  found = Integer.parseInt(token.substring(eq + 1));
               } catch (NumberFormatException var8) {
                  return -1;
               }

               if (found < 1 || found > 8) {
                  return -1;
               }
            }
         }

         return found;
      } else {
         return 0;
      }
   }

   private static String describe(SkyboxType type, SkyboxEntry entry) {
      return type.hasLayers() && entry.layer() > 1 ? type.id() + " layer " + entry.layer() : type.id();
   }

   private static String printArgs(SkyboxEntry entry) {
      Map<String, String> args = entry.params().toMap();
      if (args.isEmpty()) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder();
         args.forEach((k, v) -> sb.append(k).append('=').append(v).append(' '));
         return sb.toString().trim();
      }
   }

   private static int parseColor(String s) {
      if (s != null && !s.isEmpty()) {
         Integer named = (Integer)NAMED_COLORS.get(s.toUpperCase());
         if (named != null) {
            return named;
         } else {
            Integer more = SkyParams.namedColor(s);
            if (more != null) {
               return more;
            } else {
               String h = s.startsWith("#") ? s.substring(1) : s;

               try {
                  if (h.length() == 3) {
                     int r = Integer.parseInt(h.substring(0, 1), 16) * 17;
                     int g = Integer.parseInt(h.substring(1, 2), 16) * 17;
                     int b = Integer.parseInt(h.substring(2, 3), 16) * 17;
                     return r << 16 | g << 8 | b;
                  }

                  if (h.length() == 6) {
                     return Integer.parseInt(h, 16) & 16777215;
                  }
               } catch (NumberFormatException var7) {
               }

               return -1;
            }
         }
      } else {
         return -1;
      }
   }

   private static void csFeedback(Text text) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null && mc.player != null && mc.player.getAbilities().creativeMode) {
         chat(text);
      }
   }

   static {
      NAMED_COLORS.put("WHITE", 16777215);
      NAMED_COLORS.put("BLACK", 0);
      NAMED_COLORS.put("RED", 16724016);
      NAMED_COLORS.put("ORANGE", 16746496);
      NAMED_COLORS.put("YELLOW", 16768324);
      NAMED_COLORS.put("GREEN", 4521864);
      NAMED_COLORS.put("CYAN", 65535);
      NAMED_COLORS.put("BLUE", 5601279);
      NAMED_COLORS.put("PURPLE", 11158783);
      NAMED_COLORS.put("PINK", 16742314);
      NAMED_COLORS.put("MAGENTA", 16711935);
      NAMED_COLORS.put("GRAY", 8421504);
      TYPE_OR_ALL_SUGG = (ctx, b) -> {
         String rem = b.getRemaining().toLowerCase();
         if ("all".startsWith(rem)) {
            b.suggest("all");
         }

         for(String id : SkyboxType.allIds()) {
            if (id.startsWith(rem)) {
               b.suggest(id);
            }
         }

         return b.buildFuture();
      };
      BIOME_SUGG = (ctx, b) -> {
         MinecraftClient mc = MinecraftClient.getInstance();
         return mc != null && mc.world != null ? CommandSource.suggestIdentifiers(((Registry)mc.world.getRegistryManager().getOptional(RegistryKeys.BIOME).orElseThrow()).getIds().stream(), b) : b.buildFuture();
      };
      RUNNING_SUGG = (ctx, b) -> {
         String rem = b.getRemaining().toLowerCase();

         for(String name : SkyEffects.Effect.names()) {
            if (name.startsWith(rem)) {
               b.suggest(name);
            }
         }

         for(String name : CelestialPreset.names()) {
            if (name.startsWith(rem)) {
               b.suggest(name);
            }
         }

         return b.buildFuture();
      };
   }
}
