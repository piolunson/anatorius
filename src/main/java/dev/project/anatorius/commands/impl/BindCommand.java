package dev.project.anatorius.commands.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.commands.Command;
import dev.project.anatorius.commands.RegisterCommand;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.utils.chat.ChatUtils;
import dev.project.anatorius.utils.input.KeyboardUtils;

import java.util.List;

@RegisterCommand(name = "bind", tag = "Bind", description = "Changes the toggle keybind of a module.", syntax = "<[module]> <[key]|reset> | <reset|list> ", aliases = {"b", "key", "keybind"})
public class BindCommand extends Command {
    @Override
    public void execute(String[] args) {
        if (args.length == 2) {
            Module module = Anatorius.MODULE_MANAGER.getModule(args[0]);
            if (module == null) {
                Anatorius.CHAT_MANAGER.tagged("Could not find the module specified.", getTag(), getName());
                return;
            }

            if (args[1].equalsIgnoreCase("reset")) {
                module.setBind(0);
                Anatorius.CHAT_MANAGER.tagged("Successfully reset the toggle keybind of the " + ChatUtils.getPrimary() + module.getName() + ChatUtils.getSecondary() + " module.", getTag(), getName());
            } else {
                module.setBind(KeyboardUtils.getKeyNumber(args[1]));
                Anatorius.CHAT_MANAGER.tagged("Successfully bound the " + ChatUtils.getPrimary() + module.getName() + ChatUtils.getSecondary() + " module to the " + ChatUtils.getPrimary() + KeyboardUtils.getKeyName(module.getBind()) + ChatUtils.getSecondary() + " key.", getTag(), getName());
            }
        } else if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "reset" -> {
                    Anatorius.MODULE_MANAGER.getModules().forEach(m -> m.bind.resetValue());
                    Anatorius.CHAT_MANAGER.tagged("Successfully reset every module's toggle keybind.", getTag(), getName());
                }
                case "list" -> {
                    List<Module> modules = Anatorius.MODULE_MANAGER.getModules().stream().filter(m -> m.getBind() != 0).toList();

                    if (modules.isEmpty()) {
                        Anatorius.CHAT_MANAGER.tagged("There are currently no bound modules.", getTag(), getName() + "-list");
                    } else {
                        StringBuilder builder = new StringBuilder();
                        int index = 0;

                        for (Module module : modules) {
                            index++;
                            builder.append(ChatUtils.getSecondary()).append(module.getName())
                                    .append(ChatUtils.getPrimary()).append(" [")
                                    .append(ChatUtils.getSecondary()).append(KeyboardUtils.getKeyName(module.getBind()).toUpperCase())
                                    .append(ChatUtils.getPrimary()).append("]")
                                    .append(index == modules.size() ? "" : ", ");
                        }

                        Anatorius.CHAT_MANAGER.message("Bound Modules " + ChatUtils.getPrimary() + "[" + ChatUtils.getSecondary() + modules.size() + ChatUtils.getPrimary() + "]: " + ChatUtils.getSecondary() + builder, getName() + "-list");
                    }
                }
                default -> messageSyntax();
            }
        } else {
            messageSyntax();
        }
    }
}
