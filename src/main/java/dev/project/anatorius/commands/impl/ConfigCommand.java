package dev.project.anatorius.commands.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.commands.Command;
import dev.project.anatorius.commands.CommandSuggestions;
import dev.project.anatorius.commands.RegisterCommand;
import dev.project.anatorius.managers.ConfigManager.ConfigCategory;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.utils.chat.ChatUtils;
import dev.project.anatorius.utils.input.KeyboardUtils;

import java.io.IOException;
import java.util.List;

@RegisterCommand(name = "config", tag = "Config", description = "Manage the client's configuration system.", syntax = "<modules|visuals|binds|all> <save|load> <[name]> | <save|load> <[name]> | <reload|current>")
public class ConfigCommand extends Command {
    @Override
    public void execute(String[] args) {
        if (args.length == 3) {
            ConfigCategory category = ConfigCategory.from(args[0]);
            if (category == null) { messageSyntax(); return; }
            run(category, args[1], args[2]);
            return;
        }

        if (args.length == 2) {
            run(ConfigCategory.ALL, args[0], args[1]);
            return;
        }

        if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "reload" -> {
                    Anatorius.CONFIG_MANAGER.loadConfig();
                    Anatorius.CHAT_MANAGER.tagged("Successfully reloaded the current configuration.", getTag(), getName());
                }
                case "current" -> Anatorius.CHAT_MANAGER.tagged("The client is currently using the " + ChatUtils.getPrimary() + Anatorius.CONFIG_MANAGER.getCurrentConfig() + ChatUtils.getSecondary() + " configuration.", getTag(), getName());
                default -> messageSyntax();
            }
            return;
        }

        messageSyntax();
    }

    private void run(ConfigCategory category, String action, String name) {
        String label = category == ConfigCategory.ALL ? "" : category.folder + " ";
        switch (action.toLowerCase()) {
            case "load" -> {
                if (!exists(category, name)) {
                    Anatorius.CHAT_MANAGER.tagged("The specified " + label + "configuration does not exist.", getTag(), getName());
                    return;
                }
                try {
                    Anatorius.CONFIG_MANAGER.loadCategory(category, name);
                    Anatorius.CHAT_MANAGER.tagged("Successfully loaded the " + ChatUtils.getPrimary() + name + ChatUtils.getSecondary() + " " + label + "configuration.", getTag(), getName());
                    if (category == ConfigCategory.MODULES || category == ConfigCategory.ALL) messageBinds();
                } catch (IOException exception) {
                    Anatorius.CHAT_MANAGER.tagged("Failed to load the " + ChatUtils.getPrimary() + name + ChatUtils.getSecondary() + " " + label + "configuration.", getTag(), getName());
                }
            }
            case "save" -> {
                try {
                    Anatorius.CONFIG_MANAGER.saveCategory(category, name);
                    Anatorius.CHAT_MANAGER.tagged("Successfully saved the " + ChatUtils.getPrimary() + name + ChatUtils.getSecondary() + " " + label + "configuration.", getTag(), getName());
                } catch (IOException exception) {
                    Anatorius.CHAT_MANAGER.tagged("Failed to save the " + ChatUtils.getPrimary() + name + ChatUtils.getSecondary() + " " + label + "configuration.", getTag(), getName());
                }
            }
            default -> messageSyntax();
        }
    }

    private void messageBinds() {
        List<Module> bound = Anatorius.MODULE_MANAGER.getModules().stream().filter(module -> module.getBind() != 0).toList();
        if (bound.isEmpty()) {
            Anatorius.CHAT_MANAGER.tagged("No module has a keybind in this configuration.", getTag());
            return;
        }

        Anatorius.CHAT_MANAGER.tagged("Keybinds " + ChatUtils.getPrimary() + "[" + ChatUtils.getSecondary() + bound.size() + ChatUtils.getPrimary() + "]" + ChatUtils.getSecondary() + ":", getTag());
        for (Module module : bound) {
            Anatorius.CHAT_MANAGER.tagged(ChatUtils.getPrimary() + module.getName() + ChatUtils.getSecondary() + " » " + ChatUtils.getPrimary() + KeyboardUtils.getKeyName(module.getBind()).toUpperCase(), getTag());
        }
    }

    private boolean exists(ConfigCategory category, String name) {
        if (category == ConfigCategory.ALL) {
            for (ConfigCategory real : ConfigCategory.realValues()) if (dev.project.anatorius.utils.system.FileUtils.fileExists(Anatorius.MOD_NAME + "/" + real.folder + "/" + name + ".json")) return true;
            return false;
        }
        return dev.project.anatorius.utils.system.FileUtils.fileExists(Anatorius.MOD_NAME + "/" + category.folder + "/" + name + ".json");
    }

    @Override
    public List<String> resolvePlaceholder(String placeholder, String[] args) {
        if (placeholder.equalsIgnoreCase("name")) return CommandSuggestions.configNames();
        return super.resolvePlaceholder(placeholder, args);
    }
}
