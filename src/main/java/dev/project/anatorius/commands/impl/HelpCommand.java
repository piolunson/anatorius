package dev.project.anatorius.commands.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.commands.Command;
import dev.project.anatorius.commands.RegisterCommand;
import dev.project.anatorius.utils.chat.ChatUtils;

import java.util.List;

@RegisterCommand(name = "help", tag = "Help", description = "Shows you a list of all of the client's commands or information about a certain command.", syntax = "empty | <[command]>", aliases = {"cmds"})
public class HelpCommand extends Command {
    @Override
    public void execute(String[] args) {
        if (args.length == 1)  {
            Command command = Anatorius.COMMAND_MANAGER.getCommand(args[0]);
            if (command == null) {
                Anatorius.CHAT_MANAGER.tagged("Could not find the command specified.", getTag(), getName());
                return;
            }

            Anatorius.CHAT_MANAGER.info(command.getTag() + ChatUtils.getPrimary() + " - " + ChatUtils.getSecondary() + command.getName() + " " + command.getSyntax());
            Anatorius.CHAT_MANAGER.info(command.getDescription());
        } else if (args.length == 0) {
            List<Command> commands = Anatorius.COMMAND_MANAGER.getCommands();

            if (commands.isEmpty()) {
                Anatorius.CHAT_MANAGER.tagged("There are currently no registered commands.", getTag(), getName() + "-list");
            } else {
                StringBuilder builder = new StringBuilder();
                int index = 0;

                for (Command command : commands) {
                    index++;
                    builder.append(ChatUtils.getSecondary()).append(command.getName())
                            .append(index == commands.size() ? "" : ", ");
                }

                Anatorius.CHAT_MANAGER.message("Commands " + ChatUtils.getPrimary() + "[" + ChatUtils.getSecondary() + commands.size() + ChatUtils.getPrimary() + "]: " + ChatUtils.getSecondary() + builder, getName() + "-list");
            }
        } else {
            messageSyntax();
        }
    }
}
