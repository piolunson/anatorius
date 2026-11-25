package dev.project.anatorius.managers;

import com.google.gson.*;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.settings.impl.*;
import lombok.Cleanup;
import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.settings.Setting;
import dev.project.anatorius.utils.minecraft.IdentifierUtils;
import dev.project.anatorius.utils.system.FileUtils;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@Getter @Setter
public class ConfigManager {
    private String currentConfig = "default";

    public ConfigManager() {
        loadConfig();
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveConfig));
    }

    public void loadConfig() {
        try {
            FileUtils.createDirectory(Anatorius.MOD_NAME);
            FileUtils.createDirectory(Anatorius.MOD_NAME + "/Configs");
            FileUtils.createDirectory(Anatorius.MOD_NAME + "/Client");
            for (ConfigCategory category : ConfigCategory.realValues()) FileUtils.createDirectory(Anatorius.MOD_NAME + "/" + category.folder);

            loadGeneral();
            loadWaypoints();
            loadModules(currentConfig);
        } catch (IOException exception) {
            Anatorius.LOGGER.error("Failed to load the client's configuration!", exception);
            Anatorius.CHAT_MANAGER.await("The configuration has not been loaded properly. Read the stacktrace for more information.");
        }
    }

    public void saveConfig() {
        try {
            FileUtils.createDirectory(Anatorius.MOD_NAME);
            FileUtils.createDirectory(Anatorius.MOD_NAME + "/Configs");
            FileUtils.createDirectory(Anatorius.MOD_NAME + "/Client");
            for (ConfigCategory category : ConfigCategory.realValues()) FileUtils.createDirectory(Anatorius.MOD_NAME + "/" + category.folder);

            saveGeneral();
            saveWaypoints();
            saveModules(currentConfig);
        } catch (IOException exception) {
            Anatorius.LOGGER.error("Failed to save the client's configuration!", exception);
        }
    }

    public void loadWaypoints() throws IOException {
        if (!FileUtils.fileExists(Anatorius.MOD_NAME + "/Waypoints.json")) return;
        @Cleanup InputStream stream = Files.newInputStream(Paths.get(Anatorius.MOD_NAME + "/Waypoints.json"));

        JsonObject configObject;
        try {
            configObject = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
        } catch (IllegalStateException exception) {
            Anatorius.LOGGER.error("Failed to load the client's Waypoint configuration!", exception);
            Anatorius.CHAT_MANAGER.await("The Waypoint configuration has not been loaded properly. Read the stacktrace for more information.");
            return;
        }

        if(configObject.has("Waypoints")) {
            for(JsonElement element : configObject.get("Waypoints").getAsJsonArray()) {
                String[] args = element.getAsString().split(":");
                if(Anatorius.WAYPOINT_MANAGER.contains(args[0])) continue;
                Anatorius.WAYPOINT_MANAGER.add(args[0], new Vec3d(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3])), args[4], args[5]);
            }
        }
    }

    public void loadGeneral() throws IOException {
        if (!FileUtils.fileExists(Anatorius.MOD_NAME + "/General.json")) return;
        @Cleanup InputStream stream = Files.newInputStream(Paths.get(Anatorius.MOD_NAME + "/General.json"));

        JsonObject configObject;
        try {
            configObject = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
        } catch (IllegalStateException exception) {
            Anatorius.LOGGER.error("Failed to load the client's General configuration!", exception);
            Anatorius.CHAT_MANAGER.await("The General configuration has not been loaded properly. Read the stacktrace for more information.");
            return;
        }

        if (configObject.has("Config")) currentConfig = configObject.get("Config").getAsString();
        if (configObject.has("Prefix")) Anatorius.COMMAND_MANAGER.setPrefix(configObject.get("Prefix").getAsString());
        if (configObject.has("Friends")) {
            for (JsonElement element : configObject.get("Friends").getAsJsonArray()) {
                if (Anatorius.FRIEND_MANAGER.contains(element.getAsString())) continue;
                Anatorius.FRIEND_MANAGER.add(element.getAsString());
            }
        }

        if (configObject.has("Macros")) {
            for (JsonElement element : configObject.get("Macros").getAsJsonArray()) {
                try {
                    dev.project.anatorius.utils.macro.Macro macro = MacroManager.fromJson(element);
                    if (macro != null) Anatorius.MACRO_MANAGER.add(macro);
                } catch (RuntimeException exception) {
                    Anatorius.LOGGER.error("Failed to load the " + element + " macro!", exception);
                    Anatorius.CHAT_MANAGER.await("The " + element + " macro has failed to load. Read the stacktrace for more information.");
                }
            }
        }
    }

    public void saveGeneral() throws IOException {
        FileUtils.resetFile(Anatorius.MOD_NAME + "/General.json");

        JsonObject configObject = new JsonObject();
        configObject.add("Config", new JsonPrimitive(currentConfig));
        configObject.add("Prefix", new JsonPrimitive(Anatorius.COMMAND_MANAGER.getPrefix()));

        JsonArray friendsArray = new JsonArray();
        Anatorius.FRIEND_MANAGER.getFriends().forEach(friendsArray::add);
        configObject.add("Friends", friendsArray);

        JsonArray macroArray = new JsonArray();
        Anatorius.MACRO_MANAGER.getMacros().forEach(macro -> macroArray.add(MacroManager.toJson(macro)));
        configObject.add("Macros", macroArray);

        @Cleanup OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(Anatorius.MOD_NAME + "/General.json"), StandardCharsets.UTF_8);
        writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(configObject.toString())));
    }

    public void saveWaypoints() throws IOException {
        FileUtils.resetFile(Anatorius.MOD_NAME + "/Waypoints.json");

        JsonObject configObject = new JsonObject();

        JsonArray waypointArray = new JsonArray();
        for(WaypointManager.Waypoint waypoint : Anatorius.WAYPOINT_MANAGER.getWaypoints()) {
            waypointArray.add(waypoint.getName() + ":" + (int)waypoint.getPos().x + ":" + (int)waypoint.getPos().y + ":" + (int)waypoint.getPos().z + ":" + waypoint.getDimension() + ":" + waypoint.getServer());
        }
        configObject.add("Waypoints", waypointArray);

        @Cleanup OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(Anatorius.MOD_NAME + "/Waypoints.json"), StandardCharsets.UTF_8);
        writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(configObject.toString())));
    }

    public void loadModules(String config) throws IOException {
        if (!FileUtils.fileExists(Anatorius.MOD_NAME + "/Configs/" + config + ".json")) return;
        @Cleanup InputStream stream = Files.newInputStream(Paths.get(Anatorius.MOD_NAME + "/Configs/" + config + ".json"));

        JsonObject modulesObject;
        try {
            JsonObject configObject = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            modulesObject = configObject.getAsJsonObject("Modules");
        } catch (RuntimeException exception) {
            modulesObject = null;
            Anatorius.LOGGER.error("Failed to load the client's Module configuration!", exception);
        }
        if (modulesObject == null) {
            Anatorius.CHAT_MANAGER.await("The config \"" + config + "\" is damaged and was not loaded. Your current setup is unchanged.");
            return;
        }

        Map<Module, Boolean> status = new LinkedHashMap<>();
        Map<Module, Boolean> changed = new HashMap<>();
        for (Module module : Anatorius.MODULE_MANAGER.getModules()) {
            JsonObject before = serializeSettings(module);
            boolean enabled = false;
            try {
                JsonElement moduleElement = modulesObject.get(module.getName());
                JsonObject moduleObject = moduleElement != null && moduleElement.isJsonObject() ? moduleElement.getAsJsonObject() : null;
                module.resetValues();
                if (moduleObject != null) {
                    JsonElement statusElement = moduleObject.get("Status");
                    enabled = statusElement != null && statusElement.isJsonPrimitive() && statusElement.getAsBoolean();
                    JsonElement settingsElement = moduleObject.get("Settings");
                    if (settingsElement != null && settingsElement.isJsonObject()) applySettings(module, settingsElement.getAsJsonObject());
                }
            } catch (RuntimeException exception) {
                Anatorius.LOGGER.error("Config '{}': module {} could not be loaded, using its defaults", config, module.getName(), exception);
                module.resetValues();
                enabled = false;
            }
            status.put(module, enabled);
            changed.put(module, !before.equals(serializeSettings(module)));
        }

        applyLegacyMining();
        for (Map.Entry<Module, Boolean> entry : status.entrySet()) {
            Module module = entry.getKey();
            try {
                if (entry.getValue() && module.isToggled() && changed.get(module)) {
                    module.setToggled(false, false);
                }
                module.setToggled(entry.getValue(), false);
            } catch (RuntimeException exception) {
                Anatorius.LOGGER.error("Config '{}': toggling {} failed", config, module.getName(), exception);
            }
        }

        this.currentConfig = config;
    }

    private static final Map<String, String> LEGACY_KEYS = Map.of(
            "HUD.LagNotifyColor", "Color",
            "ClickGui.BackgroundShader", "Background",
            "AutoCrystal.RotatePlace", "Rotate",
            "AutoCrystal.RotateAttack", "Rotate",
            "AutoTrap.BlocksPerTick", "Limit",
            "SelfTrap.BlocksPerTick", "Limit",
            "AntiPiston.BlocksPerTick", "Limit",
            "Blocker.BlocksPerTick", "Limit");

    private boolean legacyNcpMining;

    private void migrateSpeedMine(Module module, JsonObject settingsObject) {
        if (!module.getName().equals("SpeedMine") || settingsObject.has("Alternative")) return;
        JsonElement oldSwitch = settingsObject.get("Switch");
        if (oldSwitch != null && oldSwitch.isJsonPrimitive() && oldSwitch.getAsString().startsWith("Alt")) {
            settingsObject.addProperty("Switch", "Silent");
            settingsObject.addProperty("Alternative", true);
        }
        JsonElement oldMode = settingsObject.get("Mode");
        legacyNcpMining = oldMode != null && oldMode.isJsonPrimitive() && oldMode.getAsString().equals("NCP");
    }

    private void applyLegacyMining() {
        if (!legacyNcpMining) return;
        legacyNcpMining = false;
        var antiCheat = Anatorius.MODULE_MANAGER.getModule(dev.project.anatorius.modules.impl.core.AntiCheatModule.class);
        if (antiCheat != null) antiCheat.mining.setValue("NCP");
    }

    private void applySettings(Module module, JsonObject settingsObject) {
        migrateSpeedMine(module, settingsObject);
        for (Setting uncastedSetting : module.getSettings()) {
            JsonElement valueObject = settingsObject.get(uncastedSetting.getName());
            if (valueObject == null) {
                String legacy = LEGACY_KEYS.get(module.getName() + "." + uncastedSetting.getName());
                if (legacy != null) valueObject = settingsObject.get(legacy);
            }
            if (valueObject == null || !valueObject.isJsonPrimitive()) continue;

            try {
                switch (uncastedSetting) {
                    case BooleanSetting setting -> setting.setValue(valueObject.isJsonPrimitive() && valueObject.getAsJsonPrimitive().isString()
                            && !valueObject.getAsString().equalsIgnoreCase("true") && !valueObject.getAsString().equalsIgnoreCase("false")
                            ? !valueObject.getAsString().equalsIgnoreCase("None")
                            : valueObject.getAsBoolean());
                    case NumberSetting setting -> setting.setValue(valueObject.getAsNumber());
                    case ModeSetting setting -> setting.setValue(valueObject.getAsString().equals("AltPickup") ? "AltSwap" : valueObject.getAsString());
                    case StringSetting setting -> setting.setValue(valueObject.getAsString());
                    case BindSetting setting -> {
                        String[] data = valueObject.getAsString().split(",");
                        setting.setValue(Integer.parseInt(data[0].trim()));
                        if (data.length >= 2) {
                            try {
                                setting.setMode(dev.project.anatorius.settings.impl.BindSetting.BindMode.valueOf(data[1].trim()));
                            } catch (IllegalArgumentException e) {
                                setting.setMode(dev.project.anatorius.settings.impl.BindSetting.BindMode.TOGGLE);
                            }
                        }
                    }
                    case ColorSetting setting -> {
                        String[] data = valueObject.getAsString().split(",");
                        if (data.length < 4) break;
                        setting.setColor(new Color(channel(data[0]), channel(data[1]), channel(data[2]), channel(data[3])));
                        if (data.length >= 5) setting.setSync(Boolean.parseBoolean(data[4].trim()));
                        if (data.length >= 6) setting.setRainbow(Boolean.parseBoolean(data[5].trim()));
                    }
                    case WhitelistSetting setting -> {
                        setting.clear();
                        for (String id : valueObject.getAsString().split(",")) {
                            id = id.trim();
                            if (id.isEmpty() || id.equals("null")) continue;
                            Object object = switch (setting.getType()) {
                                case ITEMS -> IdentifierUtils.getItem(id);
                                case BLOCKS -> IdentifierUtils.getBlock(id);
                                case POTIONS -> IdentifierUtils.getPotion(id);
                                case EFFECTS -> {
                                    net.minecraft.util.Identifier effectId = net.minecraft.util.Identifier.tryParse(id);
                                    yield effectId == null ? null : net.minecraft.registry.Registries.STATUS_EFFECT.get(effectId);
                                }
                                case MODELS -> dev.project.anatorius.utils.playermodels.PlayerModels.byId(id) != null ? id : null;
                                case SOUNDS -> id;
                            };
                            if (object != null && !setting.isWhitelistContains(object)) setting.add(object);
                        }
                    }
                    default -> {}
                }
            } catch (RuntimeException exception) {
                Anatorius.LOGGER.warn("Config: {}.{} has an invalid value ({}), using the default", module.getName(), uncastedSetting.getName(), valueObject);
                resetSetting(uncastedSetting);
            }
        }
    }

    private static int channel(String value) {
        return Math.clamp(Integer.parseInt(value.trim()), 0, 255);
    }

    private static void resetSetting(Setting uncastedSetting) {
        switch (uncastedSetting) {
            case BooleanSetting setting -> setting.resetValue();
            case NumberSetting setting -> setting.resetValue();
            case ModeSetting setting -> setting.resetValue();
            case StringSetting setting -> setting.resetValue();
            case BindSetting setting -> setting.resetValue();
            case ColorSetting setting -> setting.resetValue();
            case WhitelistSetting setting -> setting.reset();
            default -> {}
        }
    }

    private static JsonObject serializeSettings(Module module) {
        return serializeSettings(module, ConfigCategory.ALL);
    }

    private static JsonObject serializeSettings(Module module, ConfigCategory category) {
        return serializeSettings(module, module, category);
    }

    private static JsonObject serializeSettings(Module classifyAs, Module module, ConfigCategory category) {
        JsonObject settingsObject = new JsonObject();
        for (Setting uncastedSetting : module.getSettings()) {
            if (!inCategory(classifyAs, uncastedSetting, category)) continue;
            switch (uncastedSetting) {
                case BooleanSetting setting -> settingsObject.add(setting.getName(), new JsonPrimitive(setting.getValue()));
                case NumberSetting setting -> settingsObject.add(setting.getName(), new JsonPrimitive(setting.getValue()));
                case ModeSetting setting -> settingsObject.add(setting.getName(), new JsonPrimitive(setting.getValue()));
                case StringSetting setting -> settingsObject.add(setting.getName(), new JsonPrimitive(setting.getValue()));
                case BindSetting setting -> settingsObject.add(setting.getName(), new JsonPrimitive(setting.getValue() + "," + setting.getMode().name()));
                case ColorSetting setting -> settingsObject.add(setting.getName(), new JsonPrimitive(setting.getValue().getColor().getRed() + "," + setting.getValue().getColor().getGreen() + "," + setting.getValue().getColor().getBlue() + "," + setting.getValue().getColor().getAlpha() + "," + setting.isSync() + "," + setting.isRainbow()));
                case WhitelistSetting setting -> {
                    StringJoiner objects = new StringJoiner(",");
                    for (String id : setting.getWhitelistIds()) if (id != null) objects.add(id);
                    settingsObject.add(setting.getName(), new JsonPrimitive(objects.toString()));
                }
                default -> {}
            }
        }
        return settingsObject;
    }

    public void saveModules(String config) throws IOException {
        FileUtils.resetFile(Anatorius.MOD_NAME + "/Configs/" + config + ".json");

        JsonObject configObject = new JsonObject();
        configObject.add("Config", new JsonPrimitive(config));

        JsonObject modulesObject = new JsonObject();
        for (Module module : Anatorius.MODULE_MANAGER.getModules()) {
            JsonObject moduleObject = new JsonObject();
            moduleObject.add("Status", new JsonPrimitive(module.isToggled()));

            JsonObject settingsObject = serializeSettings(module);

            moduleObject.add("Settings", settingsObject);
            modulesObject.add(module.getName(), moduleObject);
        }

        configObject.add("Modules", modulesObject);

        @Cleanup OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(Anatorius.MOD_NAME + "/Configs/" + config + ".json"), StandardCharsets.UTF_8);
        writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(configObject.toString())));

        this.currentConfig = config;
    }

    public enum ConfigCategory {
        MODULES("modules"), VISUALS("visuals"), BINDS("binds"), ALL("all");

        public final String folder;
        ConfigCategory(String folder) { this.folder = folder; }

        public static ConfigCategory[] realValues() {
            return new ConfigCategory[]{MODULES, VISUALS, BINDS};
        }

        public static ConfigCategory from(String token) {
            if (token == null) return null;
            for (ConfigCategory category : values()) {
                if (category.name().equalsIgnoreCase(token) || category.folder.equalsIgnoreCase(token)) return category;
            }
            return null;
        }
    }

    private static boolean isVisualModule(Module module) {
        return module != null && module.getCategory() == Module.Category.VISUALS;
    }

    private static final java.util.Set<String> LOOK_MODULES = java.util.Set.of("ClickGui", "Color", "Font", "GuiChanger", "HUD", "Renders", "Menu");

    private static boolean isLookModule(Module module) {
        return module != null && module.getCategory() == Module.Category.CORE && LOOK_MODULES.contains(module.getName());
    }

    private static boolean isVisualSetting(Module module, Setting setting) {
        if (setting instanceof BindSetting) return false;
        if (isVisualModule(module) || isLookModule(module)) return true;
        if (setting instanceof ColorSetting) return true;
        Setting.Visibility visibility = setting.getVisibility();
        for (int guard = 0; visibility instanceof CategorySetting.Visibility categoryVisibility && guard < 16; guard++) {
            CategorySetting category = categoryVisibility.getValue();
            if (category == null) break;
            if (category.getName().equalsIgnoreCase("Render")) return true;
            visibility = category.getVisibility();
        }
        return false;
    }

    private static boolean inCategory(Module module, Setting setting, ConfigCategory category) {
        if (setting instanceof CategorySetting) return false;
        return switch (category) {
            case BINDS -> setting instanceof BindSetting;
            case VISUALS -> isVisualSetting(module, setting);
            case MODULES -> !(setting instanceof BindSetting) && !isVisualSetting(module, setting);
            case ALL -> true;
        };
    }

    private static final int CATEGORY_VERSION = 2;

    public void saveCategory(ConfigCategory category, String name) throws IOException {
        if (category == ConfigCategory.ALL) {
            for (ConfigCategory real : ConfigCategory.realValues()) saveCategory(real, name);
            return;
        }

        FileUtils.createDirectory(Anatorius.MOD_NAME + "/" + category.folder);
        String path = Anatorius.MOD_NAME + "/" + category.folder + "/" + name + ".json";
        FileUtils.resetFile(path);

        JsonObject configObject = new JsonObject();
        configObject.add("Category", new JsonPrimitive(category.folder));
        configObject.add("Version", new JsonPrimitive(CATEGORY_VERSION));

        JsonObject modulesObject = new JsonObject();
        for (Module module : Anatorius.MODULE_MANAGER.getModules()) {
            JsonObject settingsObject = serializeSettings(module, category);
            boolean withStatus = category == ConfigCategory.MODULES || (category == ConfigCategory.VISUALS && isVisualModule(module));
            if (!withStatus && settingsObject.size() == 0) continue;

            JsonObject moduleObject = new JsonObject();
            if (withStatus) moduleObject.add("Status", new JsonPrimitive(module.isToggled()));
            moduleObject.add("Settings", settingsObject);
            modulesObject.add(module.getName(), moduleObject);
        }
        configObject.add("Modules", modulesObject);

        @Cleanup OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8);
        writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(configObject.toString())));
    }

    public void loadCategory(ConfigCategory category, String name) throws IOException {
        if (category == ConfigCategory.ALL) {
            for (ConfigCategory real : ConfigCategory.realValues()) loadCategory(real, name);
            return;
        }

        String path = Anatorius.MOD_NAME + "/" + category.folder + "/" + name + ".json";
        if (!FileUtils.fileExists(path)) return;
        @Cleanup InputStream stream = Files.newInputStream(Paths.get(path));

        JsonObject modulesObject;
        int version = 1;
        try {
            JsonObject configObject = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            modulesObject = configObject.getAsJsonObject("Modules");
            JsonElement versionElement = configObject.get("Version");
            if (versionElement != null && versionElement.isJsonPrimitive()) version = versionElement.getAsInt();
        } catch (RuntimeException exception) {
            modulesObject = null;
            Anatorius.LOGGER.error("Failed to load the client's {} configuration!", category.folder, exception);
        }
        boolean legacy = category == ConfigCategory.VISUALS && version < 2;
        if (modulesObject == null) {
            Anatorius.CHAT_MANAGER.await("The " + category.folder + " config \"" + name + "\" is damaged and was not loaded. Your current setup is unchanged.");
            return;
        }

        Map<Module, Boolean> status = new LinkedHashMap<>();
        Map<Module, Boolean> changed = new HashMap<>();
        for (Module module : Anatorius.MODULE_MANAGER.getModules()) {
            JsonObject before = serializeSettings(legacy ? null : module, module, category);
            boolean enabled = module.isToggled();
            try {
                JsonElement moduleElement = modulesObject.get(module.getName());
                JsonObject moduleObject = moduleElement != null && moduleElement.isJsonObject() ? moduleElement.getAsJsonObject() : null;
                Module classifyAs = legacy ? null : module;
                for (Setting setting : module.getSettings()) if (inCategory(classifyAs, setting, category)) resetSetting(setting);
                if (moduleObject != null) {
                    JsonElement statusElement = moduleObject.get("Status");
                    if (category == ConfigCategory.MODULES) {
                        enabled = statusElement != null && statusElement.isJsonPrimitive() && statusElement.getAsBoolean();
                    } else if (category == ConfigCategory.VISUALS && !legacy && isVisualModule(module)
                            && statusElement != null && statusElement.isJsonPrimitive()) {
                        enabled = statusElement.getAsBoolean();
                    }
                    JsonElement settingsElement = moduleObject.get("Settings");
                    if (settingsElement != null && settingsElement.isJsonObject()) applySettings(module, settingsElement.getAsJsonObject());
                }
            } catch (RuntimeException exception) {
                Anatorius.LOGGER.error("{} config '{}': module {} could not be loaded", category.folder, name, module.getName(), exception);
            }
            status.put(module, enabled);
            changed.put(module, !before.equals(serializeSettings(legacy ? null : module, module, category)));
        }

        if (category == ConfigCategory.BINDS) return;

        applyLegacyMining();
        for (Map.Entry<Module, Boolean> entry : status.entrySet()) {
            Module module = entry.getKey();
            try {
                boolean target = entry.getValue();
                if (target && module.isToggled() && Boolean.TRUE.equals(changed.get(module))) {
                    module.setToggled(false, false);
                }
                module.setToggled(target, false);
            } catch (RuntimeException exception) {
                Anatorius.LOGGER.error("{} config '{}': toggling {} failed", category.folder, name, module.getName(), exception);
            }
        }
    }

    public static java.util.List<String> names(ConfigCategory category) {
        java.util.List<String> names = new java.util.ArrayList<>();
        File[] files = new File(Anatorius.MOD_NAME + "/" + category.folder).listFiles((dir, file) -> file.toLowerCase().endsWith(".json"));
        if (files != null) for (File file : files) names.add(file.getName().substring(0, file.getName().length() - 5));
        return names;
    }
}
