package dev.project.anatorius.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.project.anatorius.Anatorius;
import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.command.CommandSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Getter @Setter
public abstract class Command implements IMinecraft {
    private String name, tag;
    private final String description, syntax;
    private final List<String> aliases;

    public Command() {
        RegisterCommand annotation = getClass().getAnnotation(RegisterCommand.class);

        name = annotation.name();
        tag = annotation.tag().isEmpty() ? annotation.name() : annotation.tag();
        description = annotation.description();
        syntax = annotation.syntax();
        aliases = Arrays.asList(annotation.aliases());
    }

    public abstract void execute(String[] args);

    public void executeBuild(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            execute(new String[0]);
            return 1;
        });

        builder.then(arg("args", StringArgumentType.greedyString())
                .suggests((context, suggestions) -> suggestArgs(suggestions))
                .executes(context -> {
            String raw = context.getArgument("args", String.class);
            String trimmed = raw == null ? "" : raw.trim();
            execute(trimmed.isEmpty() ? new String[0] : trimmed.split(" "));
            return 1;
        }));
    }

    public List<String> suggest(String[] args) {
        return CommandSuggestions.fromSyntax(this, getSyntax(), args);
    }

    public List<String> resolvePlaceholder(String placeholder, String[] args) {
        return switch (placeholder.toLowerCase(Locale.ROOT)) {
            case "module" -> CommandSuggestions.moduleNames();
            case "setting" -> CommandSuggestions.settingNames(CommandSuggestions.findModule(args));
            case "command" -> CommandSuggestions.commandNames();
            case "player" -> CommandSuggestions.playerNames();
            case "key" -> CommandSuggestions.keyNames();
            default -> new ArrayList<>();
        };
    }

    private CompletableFuture<Suggestions> suggestArgs(SuggestionsBuilder builder) {
        String typed = builder.getRemaining();
        int lastSpace = typed.lastIndexOf(' ');
        String current = typed.substring(lastSpace + 1).toLowerCase(Locale.ROOT);
        String[] done = lastSpace < 0 ? new String[0]
                : Arrays.stream(typed.substring(0, lastSpace).trim().split(" +")).filter(w -> !w.isEmpty()).toArray(String[]::new);

        SuggestionsBuilder word = builder.createOffset(builder.getStart() + lastSpace + 1);
        List<String> candidates;
        try {
            candidates = suggest(done);
        } catch (Throwable t) {
            return word.buildFuture();
        }
        for (String candidate : new LinkedHashSet<>(candidates)) {
            if (candidate != null && candidate.toLowerCase(Locale.ROOT).startsWith(current)) word.suggest(candidate);
        }
        return word.buildFuture();
    }

    public void messageSyntax() {
        Anatorius.CHAT_MANAGER.info(name + " " + syntax);
    }

    protected static <T> RequiredArgumentBuilder<CommandSource, T> arg(final String name, final ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    protected static LiteralArgumentBuilder<CommandSource> literal(final String name) {
        return LiteralArgumentBuilder.literal(name);
    }
}
