package dev.project.anatorius.commands;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.settings.Setting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.input.KeyboardUtils;
import net.minecraft.entity.player.PlayerEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CommandSuggestions implements IMinecraft {
    private CommandSuggestions() {}

    private static List<String> keyNames;

    public static List<String> fromSyntax(Command command, String syntax, String[] args) {
        Set<String> out = new LinkedHashSet<>();
        if (syntax == null || syntax.isBlank()) return new ArrayList<>(out);

        for (List<List<String>> alt : parse(syntax)) {
            if (args.length >= alt.size()) continue;
            boolean matches = true;
            for (int i = 0; i < args.length && matches; i++) {
                List<String> options = alt.get(i);
                if (options.stream().anyMatch(CommandSuggestions::isGreedy)) { matches = false; break; }
                if (options.stream().anyMatch(CommandSuggestions::isPlaceholder)) continue;
                matches = options.stream().anyMatch(args[i]::equalsIgnoreCase);
            }
            if (!matches) continue;

            for (String option : alt.get(args.length)) {
                if (isPlaceholder(option)) out.addAll(command.resolvePlaceholder(option.substring(1, option.length() - 1).trim(), args));
                else out.add(option);
            }
        }
        return new ArrayList<>(out);
    }

    static List<List<List<String>>> parse(String syntax) {
        List<List<List<String>>> alts = new ArrayList<>();
        List<List<String>> current = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        int depth = 0;
        for (int i = 0; i <= syntax.length(); i++) {
            char c = i < syntax.length() ? syntax.charAt(i) : ' ';
            if (c == '<' || c == '[') depth++;
            if (c == '>' || c == ']') depth = Math.max(0, depth - 1);
            boolean separator = depth == 0 && (Character.isWhitespace(c) || c == '|');
            if (separator) {
                if (!token.isEmpty()) { addToken(current, token.toString()); token.setLength(0); }
                if (c == '|') { if (!current.isEmpty()) alts.add(current); current = new ArrayList<>(); }
            } else {
                token.append(c);
            }
        }
        if (!current.isEmpty()) alts.add(current);
        return alts;
    }

    private static void addToken(List<List<String>> alt, String token) {
        if (token.equalsIgnoreCase("empty")) return;
        String body = token.startsWith("<") && token.endsWith(">") ? token.substring(1, token.length() - 1) : token;
        List<String> options = new ArrayList<>();
        StringBuilder option = new StringBuilder();
        int depth = 0;
        for (char c : body.toCharArray()) {
            if (c == '[') depth++;
            if (c == ']') depth = Math.max(0, depth - 1);
            if (c == '|' && depth == 0) { addOption(options, option); continue; }
            option.append(c);
        }
        addOption(options, option);
        if (!options.isEmpty()) alt.add(options);
    }

    private static void addOption(List<String> options, StringBuilder option) {
        String s = option.toString().trim();
        if (!s.isEmpty()) options.add(s);
        option.setLength(0);
    }

    private static boolean isPlaceholder(String option) {
        return option.startsWith("[") && option.endsWith("]");
    }

    private static boolean isGreedy(String option) {
        String s = option.toLowerCase();
        return s.equals("[...]") || s.equals("[message]") || s.equals("[input]");
    }

    public static List<String> moduleNames() {
        List<String> names = new ArrayList<>();
        for (Module m : Anatorius.MODULE_MANAGER.getModules()) names.add(m.getName());
        return names;
    }

    public static Module findModule(String[] args) {
        for (String a : args) {
            Module m = Anatorius.MODULE_MANAGER.getModule(a);
            if (m != null) return m;
        }
        return null;
    }

    public static List<String> settingNames(Module module) {
        List<String> names = new ArrayList<>();
        if (module == null) return names;
        for (Setting s : module.getSettings()) if (!(s instanceof CategorySetting)) names.add(s.getName());
        return names;
    }

    public static List<String> commandNames() {
        List<String> names = new ArrayList<>();
        for (Command c : Anatorius.COMMAND_MANAGER.getCommands()) names.add(c.getName());
        return names;
    }

    public static List<String> playerNames() {
        List<String> names = new ArrayList<>();
        if (mc.world == null) return names;
        String self = mc.player != null ? mc.player.getName().getString() : "";
        for (PlayerEntity p : mc.world.getPlayers()) {
            String n = p.getName().getString();
            if (!n.equalsIgnoreCase(self)) names.add(n);
        }
        return names;
    }

    public static List<String> configNames() {
        Set<String> names = new LinkedHashSet<>();
        String[] folders = {"Configs", "modules", "visuals", "binds"};
        for (String folder : folders) {
            File[] files = new File(Anatorius.MOD_NAME + "/" + folder).listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files != null) for (File f : files) names.add(f.getName().substring(0, f.getName().length() - 5));
        }
        return new ArrayList<>(names);
    }

    public static List<String> keyNames() {
        if (keyNames == null) {
            Set<String> names = new LinkedHashSet<>();
            for (int code = 32; code <= 348; code++) {
                String name = KeyboardUtils.getKeyName(code);
                if (name == null || name.isBlank() || name.equalsIgnoreCase("Unknown")) continue;
                String lower = name.toLowerCase();
                if (KeyboardUtils.getKeyNumber(lower) == code) names.add(lower);
            }
            keyNames = new ArrayList<>(names);
        }
        return keyNames;
    }
}
