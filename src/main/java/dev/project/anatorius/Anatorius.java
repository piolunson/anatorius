package dev.project.anatorius;

import dev.project.anatorius.commands.CommandManager;
import dev.project.anatorius.events.EventHandler;
import dev.project.anatorius.gui.ClickGuiScreen;
import dev.project.anatorius.managers.*;
import dev.project.anatorius.modules.ModuleManager;
import dev.project.anatorius.utils.system.ErrorDialog;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Anatorius implements ModInitializer {
    public static final String MOD_NAME = BuildConstants.MOD_NAME;
    public static final String MOD_ID = BuildConstants.MOD_ID;
    public static final String MOD_VERSION = BuildConstants.MOD_VERSION;
    public static final String MINECRAFT_VERSION = BuildConstants.MINECRAFT_VERSION;
    public static final String GIT_HASH = BuildConstants.GIT_HASH;
    public static final String GIT_REVISION = BuildConstants.GIT_REVISION;
    public static final long UPTIME = System.currentTimeMillis();

    public static final EventHandler EVENT_HANDLER = new EventHandler();
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static String UPDATE_STATUS = "none";

    public static ChatManager CHAT_MANAGER;
    public static FontManager FONT_MANAGER;
    public static FriendManager FRIEND_MANAGER;
    public static WorldManager WORLD_MANAGER;
    public static PositionManager POSITION_MANAGER;
    public static RotationManager ROTATION_MANAGER;
    public static ServerManager SERVER_MANAGER;
    public static RenderManager RENDER_MANAGER;
    public static TargetManager TARGET_MANAGER;
    public static MacroManager MACRO_MANAGER;
    public static TaskManager TASK_MANAGER;
    public static WaypointManager WAYPOINT_MANAGER;

    public static ModuleManager MODULE_MANAGER;
    public static CommandManager COMMAND_MANAGER;

    public static ShaderManager SHADER_MANAGER;
    public static ConfigManager CONFIG_MANAGER;

    public static ClickGuiScreen CLICK_GUI;

    @Override
    public void onInitialize() {
        ErrorDialog.registerGlobalHandler(MOD_NAME);

        try {
            CHAT_MANAGER = new ChatManager();
            FONT_MANAGER = new FontManager();
            FRIEND_MANAGER = new FriendManager();
            WORLD_MANAGER = new WorldManager();
            POSITION_MANAGER = new PositionManager();
            ROTATION_MANAGER = new RotationManager();
            SERVER_MANAGER = new ServerManager();
            RENDER_MANAGER = new RenderManager();
            TARGET_MANAGER = new TargetManager();
            MACRO_MANAGER = new MacroManager();
            TASK_MANAGER = new TaskManager();
            WAYPOINT_MANAGER = new WaypointManager();

            MODULE_MANAGER = new ModuleManager();
            COMMAND_MANAGER = new CommandManager();

            dev.project.anatorius.customsky.CustomSky.init();
        } catch (Throwable throwable) {
            LOGGER.error("Fatal error during initialization", throwable);
            ErrorDialog.showFatalError(MOD_NAME + " - Startup Error",
                    "A startup error occurred while loading " + MOD_NAME + ".",
                    throwable,
                    true);
            rethrowFatal(throwable);
        }
    }

    public static void onPostInitialize() {
        try {
            dev.project.anatorius.utils.graphics.EspLayers.init();
            dev.project.anatorius.utils.graphics.SkyShaderRenderer.init();

            SHADER_MANAGER = new ShaderManager();
            CONFIG_MANAGER = new ConfigManager();

            CLICK_GUI = new ClickGuiScreen();

            LOGGER.info("{} {} has been initialized.", MOD_NAME, MOD_VERSION);
        } catch (Throwable throwable) {
            LOGGER.error("Fatal error during post initialization", throwable);
            ErrorDialog.showFatalError(MOD_NAME + " - Post Init Error",
                    "A fatal error occurred after Minecraft started loading " + MOD_NAME + ".",
                    throwable,
                    true);
            rethrowFatal(throwable);
        }
    }

    private static void rethrowFatal(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(throwable);
    }

    public static void checkForUpdates() {
    }
}
