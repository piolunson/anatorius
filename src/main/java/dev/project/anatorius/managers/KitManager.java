package dev.project.anatorius.managers;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.project.anatorius.Anatorius;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class KitManager {
    private static KitManager instance;

    public record Slot(String itemId, String potion) {
        public boolean matches(ItemStack stack) {
            return Registries.ITEM.getId(stack.getItem()).toString().equals(itemId) && Objects.equals(potionSignature(stack), potion);
        }

        public boolean isEmpty() {
            return itemId.equals("minecraft:air");
        }
    }

    public static final class Kit {
        private final String name;
        private final Map<Integer, Slot> slots = new TreeMap<>();

        public Kit(String name) {
            this.name = name;
        }

        public String getName() { return name; }

        public Map<Integer, Slot> getSlots() { return slots; }
    }

    private final List<Kit> kits = new ArrayList<>();
    private String current;

    public static KitManager get() {
        if (instance == null) {
            instance = new KitManager();
            instance.load();
        }
        return instance;
    }

    private static Path file() {
        return Path.of(Anatorius.MOD_NAME, "Kits.json");
    }

    public List<Kit> getKits() { return kits; }

    public Kit find(String name) {
        for (Kit kit : kits) if (kit.getName().equalsIgnoreCase(name)) return kit;
        return null;
    }

    public Kit current() {
        return current == null ? null : find(current);
    }

    public void select(String name) {
        current = name;
        save();
    }

    public Kit capture(String name) {
        kits.removeIf(kit -> kit.getName().equalsIgnoreCase(name));
        Kit kit = new Kit(name);
        var inventory = MinecraftClient.getInstance().player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.getStack(i);
            kit.getSlots().put(i, new Slot(Registries.ITEM.getId(stack.getItem()).toString(), potionSignature(stack)));
        }
        kits.add(kit);
        current = name;
        save();
        return kit;
    }

    public boolean remove(String name) {
        boolean removed = kits.removeIf(kit -> kit.getName().equalsIgnoreCase(name));
        if (removed && name.equalsIgnoreCase(current)) current = null;
        save();
        return removed;
    }

    public void rename(Kit kit, String name) {
        boolean wasCurrent = kit == current();
        kits.remove(kit);
        Kit renamed = new Kit(name);
        renamed.getSlots().putAll(kit.getSlots());
        kits.add(renamed);
        if (wasCurrent) current = name;
        save();
    }

    public static String potionSignature(ItemStack stack) {
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return null;
        List<String> effects = new ArrayList<>();
        contents.forEachEffect(effect -> effects.add(effect.getTranslationKey() + " " + (effect.getAmplifier() + 1)), 1.0f);
        effects.sort(String::compareTo);
        return String.join("", effects);
    }

    private void load() {
        try {
            if (!Files.exists(file())) return;
            JsonObject root = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("current")) current = root.get("current").getAsString();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (!entry.getValue().isJsonArray()) continue;
                Kit kit = new Kit(entry.getKey());
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    JsonObject slot = element.getAsJsonObject();
                    JsonObject item = slot.getAsJsonObject("item");
                    String id = item.get("id").getAsString();
                    if (Identifier.tryParse(id) == null) continue;
                    kit.getSlots().put(slot.get("slot").getAsInt(), new Slot(id, item.has("data") ? item.get("data").getAsString() : null));
                }
                kits.add(kit);
            }
        } catch (Exception exception) {
            Anatorius.LOGGER.warn("[Anatorius] Kits.json could not be read: {}", exception.toString());
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        if (current != null) root.addProperty("current", current);
        for (Kit kit : kits) {
            JsonArray slots = new JsonArray();
            kit.getSlots().forEach((index, slot) -> {
                JsonObject item = new JsonObject();
                item.addProperty("id", slot.itemId());
                if (slot.potion() != null) item.addProperty("data", slot.potion());
                JsonObject entry = new JsonObject();
                entry.addProperty("slot", index);
                entry.add("item", item);
                slots.add(entry);
            });
            root.add(kit.getName(), slots);
        }
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            Anatorius.LOGGER.warn("[Anatorius] Kits.json could not be written: {}", exception.toString());
        }
    }
}
