package dev.project.anatorius.commands.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.commands.Command;
import dev.project.anatorius.commands.RegisterCommand;
import dev.project.anatorius.modules.impl.core.FriendModule;
import dev.project.anatorius.utils.chat.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RegisterCommand(name = "friend", tag = "Friend", description = "Allows you to manage the client's friend list.", syntax = "<add|del> <[player]> | <clear|list>", aliases = {"f", "friends"})
public class FriendCommand extends Command {
    private static CompletableFuture<Suggestions> suggestAddablePlayers(SuggestionsBuilder builder) {
        if (mc.world == null || mc.player == null) {
            return builder.buildFuture();
        }

        List<String> names = mc.world.getPlayers().stream()
            .map(PlayerEntity::getName)
            .map(name -> name.getString())
            .filter(name -> !name.equalsIgnoreCase(mc.player.getName().getString()))
            .filter(name -> !Anatorius.FRIEND_MANAGER.contains(name))
            .collect(Collectors.toList());

        return CommandSource.suggestMatching(names, builder);
    }

    private static CompletableFuture<Suggestions> suggestFriends(SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(Anatorius.FRIEND_MANAGER.getFriends(), builder);
    }

    @Override
    public void executeBuild(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("add").then(arg("player", StringArgumentType.word())
            .suggests((context, suggestionsBuilder) -> suggestAddablePlayers(suggestionsBuilder))
            .executes(context -> {
            execute(new String[]{"add", context.getArgument("player", String.class)});
            return 1;
        })));

        builder.then(literal("del").then(arg("player", StringArgumentType.word())
            .suggests((context, suggestionsBuilder) -> suggestFriends(suggestionsBuilder))
            .executes(context -> {
            execute(new String[]{"del", context.getArgument("player", String.class)});
            return 1;
        })));

        builder.then(literal("clear").executes(context -> {
            execute(new String[]{"clear"});
            return 1;
        }));

        builder.then(literal("list").executes(context -> {
            execute(new String[]{"list"});
            return 1;
        }));

        builder.executes(context -> {
            messageSyntax();
            return 1;
        });
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "add" -> {
                    if (!Anatorius.FRIEND_MANAGER.contains(args[1])) {
                        if (Anatorius.MODULE_MANAGER.getModule(FriendModule.class).friendMessage.getValue()) {
                            Anatorius.FRIEND_MANAGER.sendFriendMessage(args[1]);
                        }
                        Anatorius.FRIEND_MANAGER.add(args[1]);
                        Anatorius.CHAT_MANAGER.tagged("Successfully added " + ChatUtils.getPrimary() + args[1] + ChatUtils.getSecondary() + " to your friends list.", getTag(), getName());
                    } else {
                        Anatorius.CHAT_MANAGER.tagged(ChatUtils.getPrimary() + args[1] + ChatUtils.getSecondary() + " is already on your friends list.", getTag(), getName());
                    }
                }
                case "del" -> {
                    if (Anatorius.FRIEND_MANAGER.contains(args[1])) {
                        Anatorius.FRIEND_MANAGER.remove(args[1]);
                        Anatorius.CHAT_MANAGER.tagged("Successfully removed " + ChatUtils.getPrimary() + args[1] + ChatUtils.getSecondary() + " from your friends list.", getTag(), getName());
                    } else {
                        Anatorius.CHAT_MANAGER.tagged(ChatUtils.getPrimary() + args[1] + ChatUtils.getSecondary() + " is not on your friends list.", getTag(), getName());
                    }
                }
                default -> messageSyntax();
            }
        } else if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "clear" -> {
                    Anatorius.FRIEND_MANAGER.clear();
                    Anatorius.CHAT_MANAGER.tagged("Successfully cleared your friends list.", getTag(), getName() + "-list");
                }
                case "list" -> {
                    List<String> friends = Anatorius.FRIEND_MANAGER.getFriends();

                    if (friends.isEmpty()) {
                        Anatorius.CHAT_MANAGER.tagged("You currently have no friends.", getTag());
                    } else {
                        StringBuilder builder = new StringBuilder();
                        int index = 0;

                        for (String name : friends) {
                            index++;
                            builder.append(ChatUtils.getSecondary()).append(name)
                                    .append(index == friends.size() ? "" : ", ");
                        }

                        Anatorius.CHAT_MANAGER.message("Friends " + ChatUtils.getPrimary() + "[" + ChatUtils.getSecondary() + friends.size() + ChatUtils.getPrimary() + "]: " + ChatUtils.getSecondary() + builder, getName() + "-list");
                    }
                }
                default -> messageSyntax();
            }
        } else {
            messageSyntax();
        }
    }
}
