package dev.project.anatorius.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.commands.impl.ModuleCommand;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.ChatInputEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.command.CommandSource;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Getter @Setter
public class CommandManager {
    private final ArrayList<Command> commands = new ArrayList<>();
    private final CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
    private final Map<String, Command> commandAliasMap = new LinkedHashMap<>();
    private final CommandSource source;
    private String prefix = ".";

    public CommandManager() {
        this.source = new ClientCommandSource(null, MinecraftClient.getInstance(), net.minecraft.command.permission.PermissionPredicate.NONE);
        Anatorius.EVENT_HANDLER.subscribe(this);

        for (Class<?> clazz : dev.project.anatorius.generated.GeneratedRegistry.COMMANDS) {
            try {
                if (clazz.getAnnotation(RegisterCommand.class) == null) continue;
                if (clazz == ModuleCommand.class) continue;

                Command cmd = (Command) clazz.getDeclaredConstructor().newInstance();
                commands.add(cmd);
                register(cmd);
            } catch (Throwable throwable) {
                Anatorius.LOGGER.error("Failed to register command {}", clazz.getName(), throwable);
            }
        }

        Anatorius.MODULE_MANAGER.getModules().forEach(m -> {
            ModuleCommand cmd = new ModuleCommand(m);
            commands.add(cmd);
            register(cmd);
        });
    }

    private void register(Command command) {
        commandAliasMap.put(command.getName().toLowerCase(), command);

        LiteralArgumentBuilder<CommandSource> builder = LiteralArgumentBuilder.literal(command.getName().toLowerCase());
        command.executeBuild(builder);
        dispatcher.register(builder);

        for (String alias : command.getAliases()) {
            commandAliasMap.put(alias.toLowerCase(), command);

            LiteralArgumentBuilder<CommandSource> aliasBuilder = LiteralArgumentBuilder.literal(alias.toLowerCase());
            command.executeBuild(aliasBuilder);
            dispatcher.register(aliasBuilder);
        }
    }

    @SubscribeEvent
    public void onChatInput(ChatInputEvent event) {
        String message = event.getMessage();
        if (!message.startsWith(prefix)) return;

        event.setCancelled(true);
        String input = message.substring(prefix.length()).trim();

        try {
            dispatcher.execute(input, source);
        } catch (CommandSyntaxException e) {
            Anatorius.CHAT_MANAGER.error(e.getMessage());
        } catch (Exception e) {
            Anatorius.CHAT_MANAGER.error("Error executing command: " + e.getMessage());
        }
    }

    public void execute(String input) {
        ChatInputEvent event = new ChatInputEvent(input);
        onChatInput(event);
    }

    public Command getCommand(String name) {
        return commandAliasMap.get(name.toLowerCase());
    }

    public Set<String> getCommandAliases() {
        return commandAliasMap.keySet();
    }

    public CommandDispatcher<CommandSource> getDispatcher() {
        return dispatcher;
    }

    public CommandSource getSource() {
        return source;
    }
}
