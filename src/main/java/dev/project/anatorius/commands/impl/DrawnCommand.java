package dev.project.anatorius.commands.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.commands.Command;
import dev.project.anatorius.commands.RegisterCommand;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.utils.chat.ChatUtils;

import java.util.ArrayList;

@RegisterCommand(name = "drawn", tag = "Drawn", description = "Manages the modules that will be shown on the HUD's module list.", syntax = "<true|false|list|reset> | <[module]> <true|false|reset>")
public class DrawnCommand extends Command {
    @Override
    public void execute(String[] args) {
        if (args.length == 2) {
            Module module = Anatorius.MODULE_MANAGER.getModule(args[0]);
            if (module == null) {
                Anatorius.CHAT_MANAGER.tagged("Could not find the module specified.", getTag(), getName());
                return;
            }

            switch (args[1]) {
                case "true" -> {
                    module.drawn.setValue(true);
                    Anatorius.CHAT_MANAGER.tagged("Successfully set the module's drawn status to " + ChatUtils.getPrimary() + "true" + ChatUtils.getSecondary() + ".", getTag(), getName());
                }
                case "false" -> {
                    module.drawn.setValue(false);
                    Anatorius.CHAT_MANAGER.tagged("Successfully set the module's drawn status to " + ChatUtils.getPrimary() + "false" + ChatUtils.getSecondary() + ".", getTag(), getName());
                }
                case "reset" -> {
                    module.drawn.setValue(module.drawn.getDefaultValue());
                    Anatorius.CHAT_MANAGER.tagged("Successfully set the module's drawn status to it's default value.", getTag(), getName());
                }
                default -> messageSyntax();
            }
        } else if (args.length == 1) {
            switch (args[0]) {
                case "true" -> {
                    for (Module module : Anatorius.MODULE_MANAGER.getModules()) module.drawn.setValue(true);
                    Anatorius.CHAT_MANAGER.tagged("Successfully set every module's drawn status to " + ChatUtils.getPrimary() + "true" + ChatUtils.getSecondary() + ".", getTag(), getName());
                }
                case "false" -> {
                    for (Module module : Anatorius.MODULE_MANAGER.getModules()) module.drawn.setValue(false);
                    Anatorius.CHAT_MANAGER.tagged("Successfully set every module's drawn status to " + ChatUtils.getPrimary() + "false" + ChatUtils.getSecondary() + ".", getTag(), getName());
                }
                case "list" -> {
                    ArrayList<Module> drawnModules = new ArrayList<>(Anatorius.MODULE_MANAGER.getModules().stream().filter(m -> m.drawn.getValue()).toList());

                    if (drawnModules.isEmpty()) {
                        Anatorius.CHAT_MANAGER.tagged("There are currently no modules with their drawn status set to " + ChatUtils.getPrimary() + "true" + ChatUtils.getSecondary() + ".", getTag(), getName() + "-list");
                    } else {
                        StringBuilder modulesString = new StringBuilder();
                        int index = 0;

                        for (Module module : drawnModules) {
                            modulesString.append(ChatUtils.getSecondary()).append(module.getName()).append(index + 1 == drawnModules.size() ? "" : ", ");
                            index++;
                        }

                        Anatorius.CHAT_MANAGER.message(ChatUtils.getSecondary() + "Drawn Modules " + ChatUtils.getPrimary() + "[" + ChatUtils.getSecondary() + drawnModules.size() + ChatUtils.getPrimary() + "]: " + ChatUtils.getSecondary() + modulesString, getName() + "-list");
                    }
                }
                case "reset" -> {
                    for (Module module : Anatorius.MODULE_MANAGER.getModules()) module.drawn.setValue(module.drawn.getDefaultValue());
                    Anatorius.CHAT_MANAGER.tagged("Successfully set every module's drawn status to it's default value.", getTag(), getName());
                }
                default -> messageSyntax();
            }
        } else {
            messageSyntax();
        }
    }
}
