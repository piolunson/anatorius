package dev.project.anatorius.commands.impl;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.commands.Command;
import dev.project.anatorius.commands.RegisterCommand;
import dev.project.anatorius.managers.KitManager;
import dev.project.anatorius.utils.chat.ChatUtils;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

@RegisterCommand(name = "kit", tag = "Kit", description = "Saves and selects inventory kits (ChestStealer Rekit puts the current one back in your inventory).",
        syntax = "save <[name]> | load <[kit]> | remove <[kit]> | rename <[kit]> <[name]> | list")
public class KitCommand extends Command {
    @Override
    public void execute(String[] args) {
        KitManager kits = KitManager.get();
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            KitManager.Kit current = kits.current();
            List<String> others = new ArrayList<>();
            for (KitManager.Kit kit : kits.getKits()) if (kit != current) others.add(kit.getName());
            say("Kit selected: " + ChatUtils.getPrimary() + (current == null ? "None" : current.getName())
                    + ChatUtils.getSecondary() + "\nKits available: " + (others.isEmpty() ? "none" : String.join(", ", others)) + ".");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "save", "add" -> {
                if (args.length < 2) { say("kit save <name>"); return; }
                if (mc.player == null) return;
                kits.capture(args[1]);
                say("Kit " + ChatUtils.getPrimary() + args[1] + ChatUtils.getSecondary() + " has been saved and selected.");
            }
            case "load", "select" -> {
                KitManager.Kit kit = kit(args, 1);
                if (kit == null) return;
                kits.select(kit.getName());
                say("Kit " + ChatUtils.getPrimary() + kit.getName() + ChatUtils.getSecondary() + " has been loaded.");
            }
            case "remove", "delete", "del" -> {
                KitManager.Kit kit = kit(args, 1);
                if (kit == null) return;
                kits.remove(kit.getName());
                say("Kit " + ChatUtils.getPrimary() + kit.getName() + ChatUtils.getSecondary() + " has been removed.");
            }
            case "rename" -> {
                KitManager.Kit kit = kit(args, 1);
                if (kit == null) return;
                if (args.length < 3) { say("kit rename " + kit.getName() + " <name>"); return; }
                kits.rename(kit, args[2]);
                say("Kit " + ChatUtils.getPrimary() + kit.getName() + ChatUtils.getSecondary() + " has been renamed to " + ChatUtils.getPrimary() + args[2] + ChatUtils.getSecondary() + ".");
            }
            default -> messageSyntax();
        }
    }

    private KitManager.Kit kit(String[] args, int index) {
        if (args.length <= index) {
            say("Which kit?");
            return null;
        }
        KitManager.Kit kit = KitManager.get().find(args[index]);
        if (kit == null) say(Formatting.RED + "There is no kit called " + args[index] + ".");
        return kit;
    }

    private void say(String message) {
        Anatorius.CHAT_MANAGER.tagged(message, getTag(), "command-kit");
    }

    @Override
    public List<String> suggest(String[] args) {
        if (args.length == 0) return List.of("save", "load", "remove", "rename", "list");
        List<String> out = new ArrayList<>();
        if (args.length == 1 && !args[0].equalsIgnoreCase("save") && !args[0].equalsIgnoreCase("add")) {
            for (KitManager.Kit kit : KitManager.get().getKits()) out.add(kit.getName());
        }
        return out;
    }
}
