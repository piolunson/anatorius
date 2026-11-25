package dev.project.anatorius.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.KeyInputEvent;
import dev.project.anatorius.events.impl.MouseInputEvent;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.chat.ChatUtils;
import dev.project.anatorius.utils.input.KeyboardUtils;
import dev.project.anatorius.utils.macro.Macro;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class MacroManager implements IMinecraft {
    private final List<Macro> macros = new ArrayList<>();

    private String pendingName;
    private Macro.Type pendingType;

    public MacroManager() {
        Anatorius.EVENT_HANDLER.subscribe(this);
    }

    public List<Macro> getMacros() {
        return macros;
    }

    public Macro get(String name) {
        for (Macro macro : macros) if (macro.getName().equalsIgnoreCase(name)) return macro;
        return null;
    }

    public void add(Macro macro) {
        macros.removeIf(m -> m.getName().equalsIgnoreCase(macro.getName()));
        macros.add(macro);
    }

    public void remove(Macro macro) {
        macros.remove(macro);
    }

    public void clear() {
        macros.clear();
    }

    public void awaitKey(String name, Macro.Type type) {
        pendingName = name;
        pendingType = type;
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        input(event.getKey(), event.getAction());
    }

    @SubscribeEvent
    public void onMouseInput(MouseInputEvent event) {
        input(-event.getButton() - 1, event.getAction());
    }

    private void input(int key, int action) {
        if (mc.player == null || mc.world == null) return;
        if (action == GLFW.GLFW_PRESS && pendingName != null) {
            Macro macro = new Macro(pendingName, pendingType, key);
            add(macro);
            pendingName = null;
            pendingType = null;
            Anatorius.CHAT_MANAGER.tagged("Macro " + ChatUtils.getPrimary() + macro.getName() + ChatUtils.getSecondary()
                    + " has been created, bound to " + ChatUtils.getPrimary() + KeyboardUtils.getKeyName(key) + ChatUtils.getSecondary() + ".", "Macro", "command-macro");
            dev.project.anatorius.commands.impl.MacroCommand.addHint(macro);
            return;
        }
        for (Macro macro : new ArrayList<>(macros)) {
            if (!macro.isToggled() || macro.getKey() != key) continue;
            if (action == GLFW.GLFW_PRESS) macro.press();
            else if (action == GLFW.GLFW_RELEASE) macro.release();
        }
    }

    public static JsonObject toJson(Macro macro) {
        JsonObject object = new JsonObject();
        object.addProperty("name", macro.getName());
        object.addProperty("type", macro.getType().id());
        object.addProperty("key", macro.getKey());
        object.addProperty("toggled", macro.isToggled());
        JsonArray commands = new JsonArray();
        macro.getCommands().forEach(commands::add);
        object.add("commands", commands);
        return object;
    }

    public static Macro fromJson(JsonElement element) {
        if (element.isJsonPrimitive()) {
            String[] split = element.getAsString().split(":", 2);
            if (split.length < 2) return null;
            Macro macro = new Macro(split[1].length() > 24 ? split[1].substring(0, 24) : split[1], Macro.Type.SIMPLE, Integer.parseInt(split[0]));
            macro.getCommands().add(split[1]);
            return macro;
        }
        if (!element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        if (!object.has("name") || !object.has("type") || !object.has("key")) return null;
        Macro.Type type = Macro.Type.fromName(object.get("type").getAsString());
        if (type == null) return null;
        Macro macro = new Macro(object.get("name").getAsString(), type, object.get("key").getAsInt());
        if (object.has("toggled")) macro.setToggled(object.get("toggled").getAsBoolean());
        if (object.has("commands")) for (JsonElement command : object.getAsJsonArray("commands")) macro.getCommands().add(command.getAsString());
        if (type == Macro.Type.HOLD) while (macro.getCommands().size() > Macro.HOLD_LIMIT) macro.getCommands().removeLast();
        return macro;
    }
}
