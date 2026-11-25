package dev.project.anatorius.commands.impl;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.commands.Command;
import dev.project.anatorius.commands.CommandSuggestions;
import dev.project.anatorius.commands.RegisterCommand;
import dev.project.anatorius.managers.MacroManager;
import dev.project.anatorius.utils.chat.ChatUtils;
import dev.project.anatorius.utils.input.KeyboardUtils;
import dev.project.anatorius.utils.macro.Macro;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@RegisterCommand(name = "macro", tag = "Macro", description = "Manages the client's macros.",
        syntax = "new <[name]> <simple|queue|double_tap|hold> [key] [command] | delete <[macro]> | list | commands <[macro]> [add|remove|clear|edit] | bind <[macro]> [key] | toggle <[macro]> [true|false] | export <[macro]> | import")
public class MacroCommand extends Command {
    private static final String[] SUBCOMMANDS = {"new", "delete", "list", "commands", "bind", "toggle", "export", "import"};

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            messageSyntax();
            return;
        }
        switch (args[0].toLowerCase()) {
            case "new", "add" -> create(args);
            case "delete", "remove", "del" -> {
                Macro macro = macro(args, 1);
                if (macro == null) return;
                Anatorius.MACRO_MANAGER.remove(macro);
                say(name(macro) + " has been deleted.");
            }
            case "list" -> list();
            case "commands" -> commands(args);
            case "bind" -> bind(args);
            case "toggle" -> toggle(args);
            case "export" -> export(args);
            case "import" -> importMacro();
            case "clear" -> {
                Anatorius.MACRO_MANAGER.clear();
                say("All macros have been deleted.");
            }
            default -> messageSyntax();
        }
    }

    private void create(String[] args) {
        if (args.length < 3) {
            say("macro new <name> <simple|queue|double_tap|hold> [key] [command]");
            return;
        }
        Macro.Type type = Macro.Type.fromName(args[2]);
        if (type == null) {
            say(Formatting.RED + "Unknown type " + args[2] + ". Types: simple, queue, double_tap, hold.");
            return;
        }
        if (args.length == 3) {
            Anatorius.MACRO_MANAGER.awaitKey(args[1], type);
            say("Press a key (or mouse button) for " + ChatUtils.getPrimary() + args[1] + ChatUtils.getSecondary() + ".");
            return;
        }
        int key = KeyboardUtils.getKeyNumber(args[3]);
        if (key == 0) {
            say(Formatting.RED + "The key " + args[3] + " isn't valid.");
            return;
        }
        Macro macro = new Macro(args[1], type, key);
        if (args.length > 4) macro.getCommands().add(join(args, 4));
        Anatorius.MACRO_MANAGER.add(macro);
        say("Macro " + name(macro) + " has been created, bound to " + ChatUtils.getPrimary() + KeyboardUtils.getKeyName(key) + ChatUtils.getSecondary() + ".");
        if (macro.getCommands().isEmpty()) addHint(macro);
    }

    public static void addHint(Macro macro) {
        String prefix = Anatorius.COMMAND_MANAGER.getPrefix();
        MutableText text = Text.literal(ChatUtils.getPrimary() + macro.getName() + ChatUtils.getSecondary() + " has no commands yet. ")
                .append(action("[Add command]", prefix + "macro commands " + macro.getName() + " add ", "click to add a command"));
        Anatorius.CHAT_MANAGER.taggedText(text, "Macro", "command-macro-hint");
    }

    private void list() {
        List<Macro> macros = Anatorius.MACRO_MANAGER.getMacros();
        if (macros.isEmpty()) {
            say("There are no macros.");
            return;
        }
        StringBuilder builder = new StringBuilder("Macros " + ChatUtils.getPrimary() + "[" + ChatUtils.getSecondary() + macros.size() + ChatUtils.getPrimary() + "]" + ChatUtils.getSecondary() + ":");
        for (Macro macro : macros) {
            builder.append("\n").append(macro.isToggled() ? ChatUtils.getSecondary() : Formatting.GRAY)
                    .append(macro.getName()).append(" (").append(KeyboardUtils.getKeyName(macro.getKey())).append(", ").append(macro.getType().id()).append(")");
        }
        Anatorius.CHAT_MANAGER.tagged(builder.toString(), getTag(), "command-macro-list");
    }

    private void commands(String[] args) {
        Macro macro = macro(args, 1);
        if (macro == null) return;
        if (args.length == 2) {
            showCommands(macro);
            return;
        }
        List<String> commands = macro.getCommands();
        switch (args[2].toLowerCase()) {
            case "add" -> {
                if (args.length < 4) { say("macro commands " + macro.getName() + " add <command>"); return; }
                if (macro.getType() == Macro.Type.HOLD && commands.size() >= Macro.HOLD_LIMIT) {
                    say(Formatting.RED + "A hold macro has two lines at most: pressed and let go.");
                    return;
                }
                commands.add(join(args, 3));
                showCommands(macro);
            }
            case "remove", "delete" -> {
                int index = index(args, 3, macro);
                if (index < 0) return;
                commands.remove(index);
                showCommands(macro);
            }
            case "clear" -> {
                commands.clear();
                say(name(macro) + "'s commands have been cleared.");
            }
            case "edit", "set" -> {
                int index = index(args, 3, macro);
                if (index < 0) return;
                if (args.length < 5) { say("macro commands " + macro.getName() + " edit " + (index + 1) + " <command>"); return; }
                commands.set(index, join(args, 4));
                showCommands(macro);
            }
            default -> say("macro commands <macro> [add|remove|clear|edit]");
        }
    }

    private void showCommands(Macro macro) {
        MutableText text = Text.literal(ChatUtils.getPrimary() + macro.getName() + ChatUtils.getSecondary() + "'s commands:");
        if (macro.getCommands().isEmpty()) text.append(Text.literal(Formatting.GRAY + " none"));
        String prefix = Anatorius.COMMAND_MANAGER.getPrefix();
        for (int i = 0; i < macro.getCommands().size(); i++) {
            int line = i + 1;
            text.append(Text.literal("\n" + ChatUtils.getSecondary() + line + ". " + macro.getCommands().get(i) + " "));
            text.append(action("[Edit]", prefix + "macro commands " + macro.getName() + " edit " + line + " ", "click to edit"));
            text.append(Text.literal(" "));
            text.append(action("[Remove]", prefix + "macro commands " + macro.getName() + " remove " + line, "click to remove"));
        }
        Anatorius.CHAT_MANAGER.taggedText(text, getTag(), "command-macro-commands");
    }

    private static MutableText action(String label, String suggestion, String hover) {
        return Text.literal(label).formatted(Formatting.GRAY).styled(style -> style
                .withClickEvent(new ClickEvent.SuggestCommand(suggestion))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal(hover))));
    }

    private void bind(String[] args) {
        Macro macro = macro(args, 1);
        if (macro == null) return;
        if (args.length < 3) {
            say("Macro " + name(macro) + " (" + KeyboardUtils.getKeyName(macro.getKey()) + ")");
            return;
        }
        int key = KeyboardUtils.getKeyNumber(args[2]);
        if (key == 0) {
            say(Formatting.RED + "The key " + args[2] + " isn't valid.");
            return;
        }
        macro.setKey(key);
        say("Macro " + name(macro) + " key has been changed to " + ChatUtils.getPrimary() + KeyboardUtils.getKeyName(key) + ChatUtils.getSecondary() + ".");
    }

    private void toggle(String[] args) {
        Macro macro = macro(args, 1);
        if (macro == null) return;
        boolean state = args.length >= 3 ? Boolean.parseBoolean(args[2]) : !macro.isToggled();
        macro.setToggled(state);
        say("Macro " + name(macro) + " has been " + (state ? Formatting.GREEN + "enabled" : Formatting.RED + "disabled") + ChatUtils.getSecondary() + ".");
    }

    private void export(String[] args) {
        Macro macro = macro(args, 1);
        if (macro == null) return;
        String json = new GsonBuilder().create().toJson(MacroManager.toJson(macro));
        mc.keyboard.setClipboard(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
        say("Successfully copied the " + name(macro) + " macro to your clipboard.");
    }

    private void importMacro() {
        try {
            String clipboard = mc.keyboard.getClipboard();
            String json = new String(Base64.getDecoder().decode(clipboard.trim()), StandardCharsets.UTF_8);
            Macro macro = MacroManager.fromJson(JsonParser.parseString(json));
            if (macro == null) throw new IllegalArgumentException();
            Anatorius.MACRO_MANAGER.add(macro);
            say("Successfully loaded the " + name(macro) + " macro from your clipboard.");
        } catch (RuntimeException exception) {
            say(Formatting.RED + "Failed to load a macro from your clipboard.");
        }
    }

    private Macro macro(String[] args, int index) {
        if (args.length <= index) {
            say("Which macro? " + names());
            return null;
        }
        Macro macro = Anatorius.MACRO_MANAGER.get(args[index]);
        if (macro == null) say(Formatting.RED + "There is no macro called " + args[index] + ". " + ChatUtils.getSecondary() + names());
        return macro;
    }

    private int index(String[] args, int position, Macro macro) {
        try {
            int index = Integer.parseInt(args[position]) - 1;
            if (index >= 0 && index < macro.getCommands().size()) return index;
        } catch (RuntimeException ignored) {
        }
        say(Formatting.RED + "Invalid index.");
        showCommands(macro);
        return -1;
    }

    private static String names() {
        List<String> names = new ArrayList<>();
        for (Macro macro : Anatorius.MACRO_MANAGER.getMacros()) names.add(macro.getName());
        return names.isEmpty() ? "There are no macros." : "Macros: " + String.join(", ", names);
    }

    private static String name(Macro macro) {
        return ChatUtils.getPrimary() + macro.getName() + ChatUtils.getSecondary();
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private void say(String message) {
        Anatorius.CHAT_MANAGER.tagged(message, getTag(), "command-macro");
    }

    private static List<String> suggestCommandLine(String[] words) {
        int start = 0;
        for (int i = 0; i < words.length; i++) if (words[i].endsWith(";")) start = i + 1;
        String[] segment = Arrays.copyOfRange(words, start, words.length);
        if (segment.length == 0) return CommandSuggestions.commandNames();
        String name = segment[0];
        String prefix = Anatorius.COMMAND_MANAGER.getPrefix();
        if (name.startsWith(prefix)) name = name.substring(prefix.length());
        Command command = Anatorius.COMMAND_MANAGER.getCommand(name);
        if (command == null) return new ArrayList<>();
        try {
            return command.suggest(Arrays.copyOfRange(segment, 1, segment.length));
        } catch (RuntimeException exception) {
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> suggest(String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 0) return List.of(SUBCOMMANDS);
        String sub = args[0].toLowerCase();
        if (args.length == 1) {
            if (!sub.equals("new") && !sub.equals("list") && !sub.equals("import")) {
                for (Macro macro : Anatorius.MACRO_MANAGER.getMacros()) out.add(macro.getName());
            }
            return out;
        }
        if (sub.equals("new") && args.length == 2) {
            for (Macro.Type type : Macro.Type.values()) out.add(type.id());
            return out;
        }
        if ((sub.equals("new") && args.length == 3) || (sub.equals("bind") && args.length == 2)) return CommandSuggestions.keyNames();
        if (sub.equals("new") && args.length >= 4) return suggestCommandLine(Arrays.copyOfRange(args, 4, args.length));
        if (sub.equals("commands") && args.length >= 3 && args[2].equalsIgnoreCase("add")) return suggestCommandLine(Arrays.copyOfRange(args, 3, args.length));
        if (sub.equals("commands") && args.length >= 4 && args[2].equalsIgnoreCase("edit")) return suggestCommandLine(Arrays.copyOfRange(args, 4, args.length));
        if (sub.equals("commands") && args.length == 3 && (args[2].equalsIgnoreCase("remove") || args[2].equalsIgnoreCase("edit"))) {
            Macro macro = Anatorius.MACRO_MANAGER.get(args[1]);
            if (macro != null) for (int i = 1; i <= macro.getCommands().size(); i++) out.add(String.valueOf(i));
            return out;
        }
        if (sub.equals("toggle") && args.length == 2) return List.of("true", "false");
        if (sub.equals("commands") && args.length == 2) return List.of("add", "remove", "clear", "edit");
        return out;
    }
}
