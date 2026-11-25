package dev.project.anatorius.utils.macro;

import dev.project.anatorius.Anatorius;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Macro {
    public enum Type {
        SIMPLE("simple"),
        QUEUE("queue"),
        DOUBLE_TAP("double_tap"),
        HOLD("hold");

        private final String id;

        Type(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Type fromName(String name) {
            for (Type type : values()) if (type.id.equalsIgnoreCase(name) || type.name().equalsIgnoreCase(name)) return type;
            return null;
        }
    }

    public static final int HOLD_LIMIT = 2;
    private static final long DOUBLE_TAP_MS = 300L;

    private final String name;
    private final Type type;
    private int key;
    private boolean toggled = true;
    private final List<String> commands = new ArrayList<>();

    private int queueIndex;
    private long lastTap;
    private boolean pressed;

    public Macro(String name, Type type, int key) {
        this.name = name;
        this.type = type;
        this.key = key;
    }

    public String getName() { return name; }

    public Type getType() { return type; }

    public int getKey() { return key; }

    public void setKey(int key) { this.key = key; }

    public boolean isToggled() { return toggled; }

    public void setToggled(boolean toggled) { this.toggled = toggled; }

    public List<String> getCommands() { return commands; }

    public void press() {
        switch (type) {
            case SIMPLE -> commands.forEach(Macro::execute);
            case QUEUE -> {
                if (commands.isEmpty()) return;
                queueIndex %= commands.size();
                execute(commands.get(queueIndex));
                queueIndex++;
            }
            case DOUBLE_TAP -> {
                long now = System.currentTimeMillis();
                if (now - lastTap <= DOUBLE_TAP_MS) {
                    commands.forEach(Macro::execute);
                    lastTap = 0L;
                } else {
                    lastTap = now;
                }
            }
            case HOLD -> {
                if (pressed) return;
                pressed = true;
                if (!commands.isEmpty()) execute(commands.getFirst());
            }
        }
    }

    public void release() {
        if (type != Type.HOLD || !pressed) return;
        pressed = false;
        if (!commands.isEmpty()) execute(commands.getLast());
    }

    public static void execute(String line) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || line == null) return;
        String prefix = Anatorius.COMMAND_MANAGER.getPrefix();
        for (String part : line.split(";")) {
            String command = part.trim();
            if (command.isEmpty()) continue;
            if (command.startsWith("/")) {
                mc.player.networkHandler.sendChatCommand(command.substring(1));
            } else if (command.startsWith(prefix)) {
                Anatorius.COMMAND_MANAGER.execute(command);
            } else if (isClientCommand(command)) {
                Anatorius.COMMAND_MANAGER.execute(prefix + command);
            } else {
                mc.player.networkHandler.sendChatMessage(command);
            }
        }
    }

    private static boolean isClientCommand(String command) {
        String first = command.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        return Anatorius.COMMAND_MANAGER.getCommand(first) != null;
    }
}
