package dev.project.anatorius.modules.impl.core;

import dev.project.anatorius.utils.minecraft.ItemUtils;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.RenderOverlayEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.hud.HudEditor;
import dev.project.anatorius.hud.HudElement;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.Setting;
import dev.project.anatorius.settings.impl.*;
import dev.project.anatorius.utils.animations.Animation;
import dev.project.anatorius.utils.animations.Easing;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer2D;
import dev.project.anatorius.utils.minecraft.EntityUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.text.FormattingUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import dev.project.anatorius.utils.system.MathUtils;
import net.minecraft.world.GameMode;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RegisterModule(name = "HUD", description = "Renders information about the game and the client on the screen.", category = Module.Category.CORE, toggled = true, drawn = false)
public class HUDModule extends Module {
    public CategorySetting watermarkCategory = new CategorySetting("Watermark", "The settings for the client's watermark.");
    public BooleanSetting watermark = new BooleanSetting("Watermark", "Enabled", "Renders the client's name and version at the top left.", new CategorySetting.Visibility(watermarkCategory), true);
    public StringSetting watermarkText = new StringSetting("WatermarkText", "Text", "The client name that will be rendered.", new CategorySetting.Visibility(watermarkCategory), "Anatorius");
    public BooleanSetting watermarkVersion = new BooleanSetting("WatermarkVersion", "Version", "Renders the client's version after the name.", new CategorySetting.Visibility(watermarkCategory), false);
    public BooleanSetting watermarkMinecraftVersion = new BooleanSetting("WatermarkMinecraftVersion", "MinecraftVersion", "Renders the client's minecraft version after the version.", new CategorySetting.Visibility(watermarkCategory), false);
    public BooleanSetting watermarkRevision = new BooleanSetting("WatermarkRevision", "Revision", "Renders the client's git revision next to the version.", new BooleanSetting.Visibility(watermarkVersion, true), false);
    public BooleanSetting watermarkSync = new BooleanSetting("WatermarkSync", "ColorSync", "Uses the client's color for the version.", new CategorySetting.Visibility(watermarkCategory), false);

    public CategorySetting welcomerCategory = new CategorySetting("Welcomer", "The settings for the client's welcomer.");
    public BooleanSetting welcomer = new BooleanSetting("Welcomer", "Enabled", "Renders a nice welcome message directed to you.", new CategorySetting.Visibility(welcomerCategory), true);
    public BooleanSetting welcomerSync = new BooleanSetting("WelcomerSync", "ColorSync", "Uses the client's color for the username.", new CategorySetting.Visibility(welcomerCategory), false);

    public CategorySetting moduleListCategory = new CategorySetting("ModuleList", "The settings for the client's list of enabled modules.");
    public BooleanSetting moduleList = new BooleanSetting("ModuleList", "Enabled", "Renders every enabled module in an organized list.", new CategorySetting.Visibility(moduleListCategory), true);
    public BooleanSetting metaData = new BooleanSetting("MetaData", "Whether or not to show module metadata in the module list.", new CategorySetting.Visibility(moduleListCategory), true);
    public BooleanSetting onlyBound = new BooleanSetting("OnlyBound", "Only shows modules that have a keybind assigned.", new CategorySetting.Visibility(moduleListCategory), false);
    public ModeSetting moduleColorMode = new ModeSetting("ModuleColor", "Color", "The color mode for the modules on the list.", new CategorySetting.Visibility(moduleListCategory), "Default", new String[]{"Default", "Rainbow", "Random"});
    public ModeSetting moduleListSorting = new ModeSetting("ModuleListSorting", "Sorting", "The sorting for the modules on the list.", new CategorySetting.Visibility(moduleListCategory), "Width", new String[]{"Width", "Alphabetical"});

    public CategorySetting playerRadarCategory = new CategorySetting("Player Radar", "The settings for the client's list of players in render distance.");
    public BooleanSetting playerRadar = new BooleanSetting("PlayerRadar", "Enabled", "Renders the name of every player in render distance.", new CategorySetting.Visibility(playerRadarCategory), true);
    public NumberSetting playerRadarLimit = new NumberSetting("PlayerRadarLimit", "Limit", "The maximum amount of players that will be listed. Setting it to 0 means removing the limiter.", new CategorySetting.Visibility(playerRadarCategory), 8, 0, 100);
    public ModeSetting playerRadarSorting = new ModeSetting("PlayerRadarSorting", "Sorting", "The sorting for the players on the radar.", new CategorySetting.Visibility(playerRadarCategory), "Distance", new String[]{"None", "Distance", "Alphabetical"});
    public BooleanSetting playerRadarAntiBot = new BooleanSetting("PlayerRadarAntiBot", "AntiBot", "Prevents bots from being listed on the radar.", new CategorySetting.Visibility(playerRadarCategory), true);
    public BooleanSetting playerIcons = new BooleanSetting("PlayerIcons", "Icons", "Renders the player's head icon next to their name.", new CategorySetting.Visibility(playerRadarCategory), true);
    public BooleanSetting playerRadarDistance = new BooleanSetting("PlayerRadarDistance", "Distance", "Renders the distance between you and the player.", new CategorySetting.Visibility(playerRadarCategory), true);
    public BooleanSetting playerRadarEntityID = new BooleanSetting("PlayerRadarEntityID", "EntityID", "Renders the player's entity ID.", new CategorySetting.Visibility(playerRadarCategory), false);
    public BooleanSetting playerRadarGameMode = new BooleanSetting("PlayerRadarGameMode", "GameMode", "Renders the player's current gamemode.", new CategorySetting.Visibility(playerRadarCategory), true);
    public BooleanSetting playerRadarPing = new BooleanSetting("PlayerRadarPing", "Ping", "Renders the player's current latency.", new CategorySetting.Visibility(playerRadarCategory), true);
    public BooleanSetting playerRadarHealth = new BooleanSetting("PlayerRadarHealth", "Health", "Renders the player's current health.", new CategorySetting.Visibility(playerRadarCategory), true);
    public BooleanSetting playerRadarTotems = new BooleanSetting("PlayerRadarTotems", "Totems", "Renders the amount of totems that the player has popped.", new CategorySetting.Visibility(playerRadarCategory), true);
    public BooleanSetting playerRadarAnimations = new BooleanSetting("PlayerRadarAnimations", "Animations", "Renders the player radar with animations.", new CategorySetting.Visibility(playerRadarCategory), true);
    public NumberSetting playerRadarAnimationTime = new NumberSetting("PlayerRadarAnimationTime", "AnimTime", "The animation time in milliseconds.", new BooleanSetting.Visibility(playerRadarAnimations, true), 250L, 0L, 1000L);
    public NumberSetting playerRadarSlideDistance = new NumberSetting("PlayerRadarSlideDistance", "Slide", "The vertical slide distance for the appear animation.", new BooleanSetting.Visibility(playerRadarAnimations, true), 10L, 0L, 50L);

    public CategorySetting itemsCategory = new CategorySetting("Items", "The settings for information about items in your inventory and specific item counters.");
    public BooleanSetting armor = new BooleanSetting("Armor", "Renders the armor you're currently wearing and its status.", new CategorySetting.Visibility(itemsCategory), true);
    public ModeSetting armorDurability = new ModeSetting("ArmorDurability", "Durability", "The way that the durability will be rendered in.", new BooleanSetting.Visibility(armor, true), "Both", new String[]{"None", "Bar", "Percentage", "Both"});
    public BooleanSetting totemCounter = new BooleanSetting("TotemCounter", "Renders the amount of totems that you have in your inventory.", new CategorySetting.Visibility(itemsCategory), true);
    public BooleanSetting crystalCounter = new BooleanSetting("CrystalCounter", "Renders the amount of crystals that you have in your inventory.", new CategorySetting.Visibility(itemsCategory), true);
    public BooleanSetting xpCounter = new BooleanSetting("XPCounter", "Renders the amount of totems that you have in your inventory.", new CategorySetting.Visibility(itemsCategory), true);
    public BooleanSetting counterChatOffset = new BooleanSetting("CounterChatOffset", "ChatOffset", "Offsets the crystal and XP counter's positions whenever the chat is open.", new CategorySetting.Visibility(itemsCategory), false);

    public ModeSetting itemCounterAnimations = new ModeSetting("ItemCounterAnimations", "Animations", "The animation style for item counters.", new CategorySetting.Visibility(itemsCategory), "Rolling", new String[]{"None", "Popup", "Rolling"});
    public NumberSetting itemCounterAnimationTime = new NumberSetting("ItemCounterAnimationTime", "AnimTime", "The animation time in milliseconds.", new ModeSetting.Visibility(itemCounterAnimations, "Popup", "Rolling"), 250L, 0L, 1000L);
    public NumberSetting itemCounterDropDistance = new NumberSetting("ItemCounterDropDistance", "Drop", "The drop distance for the decrease popup.", new ModeSetting.Visibility(itemCounterAnimations, "Popup"), 12L, 0L, 50L);

    public CategorySetting informationCategory = new CategorySetting("Information", "The settings for information about the game and the client.");
    public BooleanSetting health = new BooleanSetting("Health", "Renders your current health in the middle of the screen.", new CategorySetting.Visibility(informationCategory), false);
    public BooleanSetting ping = new BooleanSetting("Ping", "Renders your current latency to the server in milliseconds.", new CategorySetting.Visibility(informationCategory), true);
    public BooleanSetting tps = new BooleanSetting("TPS", "Renders the server's current tick-rate.", new CategorySetting.Visibility(informationCategory), true);
    public BooleanSetting fps = new BooleanSetting("FPS", "Renders the game's frames per second counter.", new CategorySetting.Visibility(informationCategory), true);
    public BooleanSetting durability = new BooleanSetting("Durability", "Renders your held item durability.", new CategorySetting.Visibility(informationCategory), true);
    public ModeSetting speed = new ModeSetting("Speed", "Renders the speed that you are currently moving at.", new CategorySetting.Visibility(informationCategory), "Kilometers", new String[]{"None", "Meters", "Kilometers"});
    public BooleanSetting uptime = new BooleanSetting("Uptime", "Renders the uptime of the client.", new CategorySetting.Visibility(informationCategory), false);
    public BooleanSetting serverBrand = new BooleanSetting("ServerBrand", "Renders the brand of the server that you are currently on.", new CategorySetting.Visibility(informationCategory), false);
    public BooleanSetting informationSync = new BooleanSetting("InformationSync", "ColorSync", "Uses the client's color for the information elements.", new CategorySetting.Visibility(informationCategory), true);
    public BooleanSetting informationChatOffset = new BooleanSetting("InformationChatOffset", "ChatOffset", "Offsets the rendering when the chat is open.", new CategorySetting.Visibility(informationCategory), true);

    public CategorySetting potionsCategory = new CategorySetting("Potions", "The settings for information about potion effects and their status.");
    public BooleanSetting potions = new BooleanSetting("Potions", "Enabled", "Renders the name and status of every potion effect you have.", new CategorySetting.Visibility(potionsCategory), true);
    public BooleanSetting potionIcons = new BooleanSetting("PotionIcons", "Icons", "Whether or not to render the icons next to the potion's name.", new CategorySetting.Visibility(potionsCategory), true);
    public ModeSetting potionColor = new ModeSetting("PotionColor", "Color", "The color that will be used in rendering the potion's text.", new CategorySetting.Visibility(potionsCategory), "Enhanced", new String[]{"Vanilla", "Enhanced", "Client"});
    public ModeSetting potionSorting = new ModeSetting("PotionSorting", "Sorting", "The sorting for the potion effects rendered.", new CategorySetting.Visibility(potionsCategory), "Alphabetical", new String[]{"None", "Width", "Alphabetical"});
    public ModeSetting vanillaPotions = new ModeSetting("VanillaPotions", "The way that the vanilla potion icons will be handled.", new CategorySetting.Visibility(potionsCategory), "Hide", new String[]{"Keep", "Move", "Hide"});

    public CategorySetting positionCategory = new CategorySetting("Position","The settings for information about your current position and velocity.");
    public BooleanSetting coordinates = new BooleanSetting("Coordinates", "Renders your current coordinates.", new CategorySetting.Visibility(positionCategory), true);
    public BooleanSetting netherCoordinates = new BooleanSetting("NetherCoordinates", "Renders your current coordinates in the alternate dimension.", new BooleanSetting.Visibility(coordinates, true), true);
    public BooleanSetting direction = new BooleanSetting("Direction", "Renders the current direction that you are facing.", new CategorySetting.Visibility(positionCategory), true);
    public BooleanSetting positionSync = new BooleanSetting("PositionSync", "ColorSync", "Uses the client's color for the position elements.", new CategorySetting.Visibility(positionCategory), true);
    public BooleanSetting positionChatOffset = new BooleanSetting("PositionChatOffset", "ChatOffset", "Offsets the text when the chat is open.", new CategorySetting.Visibility(positionCategory), true);

    public CategorySetting spotifyCategory = new CategorySetting("Spotify", "The settings for the song playing in Spotify.");
    public BooleanSetting spotify = new BooleanSetting("Spotify", "Enabled", "Renders the artist and name of the song Spotify is playing.", new CategorySetting.Visibility(spotifyCategory), true);

    public CategorySetting playerModelCategory = new CategorySetting("PlayerModel", "The settings for the model of your player on the HUD.");
    public BooleanSetting playerModel = new BooleanSetting("PlayerModel", "Enabled", "Renders your player's model on the HUD, turned the way you face.", new CategorySetting.Visibility(playerModelCategory), false);
    public NumberSetting playerModelScale = new NumberSetting("PlayerModelScale", "Scale", "The size of the player model.", new CategorySetting.Visibility(playerModelCategory), 1.0, 0.5, 3.0);

    public CategorySetting parallaxCategory = new CategorySetting("Parallax", "The HUD drifts a little against your camera turns, then settles back.");
    public BooleanSetting parallax = new BooleanSetting("Parallax", "Enabled", "Moves the HUD slightly the other way when you turn your camera.", new CategorySetting.Visibility(parallaxCategory), true);
    public NumberSetting parallaxStrength = new NumberSetting("ParallaxStrength", "Strength", "How far the HUD drifts.", new CategorySetting.Visibility(parallaxCategory), 1.0, 0.1, 3.0);

    public CategorySetting colorCategory = new CategorySetting("Color", "The settings for the coloring of the text.");
    public ModeSetting colorMode = new ModeSetting("Color", "The color that will be applied to the text.", new CategorySetting.Visibility(colorCategory), "Default", new String[]{"Default", "Rainbow", "Wave", "Custom"});
    public ColorSetting customColor = new ColorSetting("CustomColor", "The color that will be used for the Custom mode.", new ModeSetting.Visibility(colorMode, "Custom"), ColorUtils.palette(ColorUtils.THEME_ACCENT, 255));
    public ModeSetting rainbowMode = new ModeSetting("Rainbow", "The mode for the HUD Rainbow.", new ModeSetting.Visibility(colorMode, "Rainbow"), "Vertical", new String[]{"Vertical", "Horizontal"});
    public NumberSetting rainbowOffset = new NumberSetting("RainbowOffset", "Offset", "The offset that will be applied to the rainbow.", new ModeSetting.Visibility(colorMode, "Rainbow", "Wave"), 10L, 1L, 50L);
    public BooleanSetting inversion = new BooleanSetting("Inversion", "Inverts primary and secondary colors.", new CategorySetting.Visibility(colorCategory), false);
    public BooleanSetting glow = new BooleanSetting("Glow", "Renders a soft glow (bloom) behind the HUD text.", new CategorySetting.Visibility(colorCategory), false);
    public NumberSetting glowStrength = new NumberSetting("GlowStrength", "Strength", "The intensity of the text glow.", new BooleanSetting.Visibility(glow, true), 70, 0, 255);
    public NumberSetting glowRadius = new NumberSetting("GlowRadius", "Radius", "The spread of the text glow (higher = softer and wider).", new BooleanSetting.Visibility(glow, true), 3, 1, 4);

    public ButtonSetting hudEditorButton = new ButtonSetting("HUD Editor", "Opens the HUD editor, where every element can be positioned freely with the mouse.", this::openEditor);

    private final HudEditor editor = new HudEditor();

    private HudElement watermarkElement, uptimeElement, welcomerElement, moduleListElement, playerRadarElement;
    private HudElement armorElement, totemElement, xpElement, crystalElement;
    private HudElement healthElement, informationElement, positionElement;
    private HudElement spotifyElement, playerModelElement;

    public CategorySetting lagNotifyCategory = new CategorySetting("LagNotify", "Settings for lag notifications.");
    public BooleanSetting lagNotify = new BooleanSetting("LagNotify", "Enabled", "Enables lag notifications.", new CategorySetting.Visibility(lagNotifyCategory), true);
    public BooleanSetting lagNotifyServer = new BooleanSetting("Server", "Notifies you when the server stops responding.", new BooleanSetting.Visibility(lagNotify, true), true);
    public BooleanSetting lagNotifyLagback = new BooleanSetting("Lagback", "Notifies you when you lagback.", new BooleanSetting.Visibility(lagNotify, true), true);
    public WhitelistSetting lagNotifySound = new WhitelistSetting("LagbackSound", "Sounds", "The sound played when you lagback. None picked: silent.", new BooleanSetting.Visibility(lagNotifyLagback, true), WhitelistSetting.Type.SOUNDS).withDefault("ping");
    public ColorSetting lagNotifyColor = new ColorSetting("LagNotifyColor", "Color", "The color of the notification text.", new BooleanSetting.Visibility(lagNotify, true), ColorUtils.getDefaultColor());
    public BooleanSetting lagNotifyBackground = new BooleanSetting("Background", "Renders a background behind the notification text.", new BooleanSetting.Visibility(lagNotify, true), true);
    public ColorSetting lagNotifyBackgroundColor = new ColorSetting("BackgroundColor", "The color of the notification background.", new BooleanSetting.Visibility(lagNotifyBackground, true), ColorUtils.palette(ColorUtils.THEME_INK, 150));
    public BooleanSetting lagNotifyAnimations = new BooleanSetting("Animations", "Enables smooth animations for notifications.", new BooleanSetting.Visibility(lagNotify, true), true);
    public NumberSetting lagNotifyAnimationDuration = new NumberSetting("AnimationDuration", "The duration of the animations in milliseconds.", new BooleanSetting.Visibility(lagNotifyAnimations, true), 300L, 100L, 1000L);
    public NumberSetting lagNotifyXOffset = new NumberSetting("XOffset", "The horizontal offset from the center.", new BooleanSetting.Visibility(lagNotify, true), 0L, -1000L, 1000L);
    public NumberSetting lagNotifyYOffset = new NumberSetting("YOffset", "The vertical offset from the top 1/4.", new BooleanSetting.Visibility(lagNotify, true), 0L, -1000L, 1000L);

    Vec3d lagPos = null;
    double lagDistance;
    long lagTime = System.currentTimeMillis();
    boolean serverLagActive = false;
    long serverLagStart = 0;
    private final Animation serverNotificationAnimation = new Animation(300, Easing.Method.EASE_OUT_CUBIC);
    private final Animation lagbackNotificationAnimation = new Animation(300, Easing.Method.EASE_OUT_CUBIC);

    private final Animation potionsAnimation = new Animation(300, Easing.Method.EASE_OUT_CUBIC);
    private final Animation chatAnimation = new Animation(300, Easing.Method.EASE_OUT_CUBIC);
    private float chatOffset;

    private List<ModuleEntry> moduleEntries = new ArrayList<>();
    private List<PlayerEntry> playerEntries = new ArrayList<>();
    private List<PotionEntry> potionEntries = new ArrayList<>();

    private final Map<UUID, PlayerRadarState> playerRadarStates = new HashMap<>();

    private final SlotMachineCounter totemSlot = new SlotMachineCounter();
    private final SlotMachineCounter crystalSlot = new SlotMachineCounter();
    private final SlotMachineCounter xpSlot = new SlotMachineCounter();

    private int lastTotemCount = -1;
    private int lastCrystalCount = -1;
    private int lastXpCount = -1;

    private ItemDecreasePopup totemDecreasePopup;
    private ItemDecreasePopup crystalDecreasePopup;
    private ItemDecreasePopup xpDecreasePopup;

    public HUDModule() {
        watermarkElement  = element("watermark", "Watermark", watermark::getValue, watermark, watermarkText, watermarkVersion, watermarkMinecraftVersion, watermarkRevision, watermarkSync);
        uptimeElement     = element("uptime", "Uptime", uptime::getValue, uptime);
        welcomerElement   = element("welcomer", "Welcomer", welcomer::getValue, welcomer, welcomerSync);
        moduleListElement = element("modulelist", "ModuleList", moduleList::getValue, moduleList, metaData, onlyBound, moduleColorMode, moduleListSorting);
        playerRadarElement = element("playerradar", "Player Radar", playerRadar::getValue, playerRadar, playerRadarLimit, playerRadarSorting, playerRadarAntiBot, playerIcons, playerRadarDistance, playerRadarEntityID, playerRadarGameMode, playerRadarPing, playerRadarHealth, playerRadarTotems, playerRadarAnimations, playerRadarAnimationTime, playerRadarSlideDistance);

        armorElement   = element("armor", "Armor", armor::getValue, armor, armorDurability);
        totemElement   = element("totemcounter", "Totem Counter", totemCounter::getValue, totemCounter, itemCounterAnimations, itemCounterAnimationTime, itemCounterDropDistance, counterChatOffset);
        xpElement      = element("xpcounter", "XP Counter", xpCounter::getValue, xpCounter);
        crystalElement = element("crystalcounter", "Crystal Counter", crystalCounter::getValue, crystalCounter);

        healthElement      = element("health", "Health", health::getValue, health);
        informationElement = element("information", "Information", () -> ping.getValue() || fps.getValue() || tps.getValue() || !speed.getValue().equalsIgnoreCase("None") || durability.getValue() || serverBrand.getValue() || potions.getValue(), ping, fps, tps, speed, durability, serverBrand, informationSync, informationChatOffset, potions, potionIcons, potionColor, potionSorting, vanillaPotions);
        positionElement    = element("position", "Position", () -> coordinates.getValue() || direction.getValue(), coordinates, netherCoordinates, direction, positionSync, positionChatOffset);
        spotifyElement     = element("spotify", "Spotify", spotify::getValue, spotify);
        playerModelElement = element("playermodel", "PlayerModel", playerModel::getValue, playerModel, playerModelScale);
    }

    private final java.util.Set<Setting> positionSettings = new java.util.HashSet<>();

    private HudElement element(String id, String name, java.util.function.BooleanSupplier enabled, Setting... settings) {
        HudElement e = new HudElement(id, name, enabled).with(settings);
        editor.register(e);
        getSettings().add(e.getX());
        getSettings().add(e.getY());
        positionSettings.add(e.getX());
        positionSettings.add(e.getY());
        return e;
    }

    public HudEditor getEditor() {
        return editor;
    }

    public java.util.List<Setting> getEditorSettings() {
        java.util.List<Setting> list = new java.util.ArrayList<>();
        for (Setting setting : getSettings()) {
            if (positionSettings.contains(setting)) continue;
            if (setting == hudEditorButton || setting == drawn || setting == bind) continue;
            list.add(setting);
        }
        return list;
    }

    private void openEditor() {
        if (mc.player == null) return;
        Anatorius.MODULE_MANAGER.getModule(ClickGuiModule.class).setToggled(false);
        mc.setScreen(new dev.project.anatorius.gui.HudEditorScreen());
    }

    public java.awt.Color getEditorColor() {
        return getHudColor(0);
    }

    @Override
    public java.util.List<Setting> getClickGuiSettings() {
        return java.util.List.of(colorCategory, colorMode, customColor, rainbowMode, rainbowOffset, inversion, glow, glowStrength, glowRadius, hudEditorButton);
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if(getNull()) return;

        if(event.getPacket() instanceof PlayerPositionLookS2CPacket packet) {
            lagPos = new Vec3d(packet.change().position().getX(), packet.change().position().getY(), packet.change().position().getZ());
            lagDistance = mc.player.getEntityPos().distanceTo(lagPos);
            lagTime = System.currentTimeMillis();
            if (lagNotify.getValue() && lagNotifyLagback.getValue()) {
                String sound = lagNotifySound.getSound();
                mc.execute(() -> dev.project.anatorius.utils.minecraft.ClientSounds.play(sound, 1.0f));
            }
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (moduleList.getValue()) {
            Comparator<Module> widthComparator = Comparator.comparingInt(m -> -Anatorius.FONT_MANAGER.getWidth(getModuleText(m)));
            Comparator<Module> alphabeticalComparator = Comparator.comparing(Module::getName);

            List<ModuleEntry> entries = new ArrayList<>();
            List<Module> modules = Anatorius.MODULE_MANAGER.getModules().stream()
                    .filter(module -> module.isToggled() || module.getAnimationOffset().get(0) > 0)
                    .filter(module -> module.drawn.getValue())
                    .filter(module -> !onlyBound.getValue() || module.getBind() != 0)
                    .sorted(mio() || moduleListSorting.getValue().equalsIgnoreCase("Width") ? widthComparator : alphabeticalComparator)
                    .toList();

            for (Module module : modules) {
                String text = getModuleText(module);
                entries.add(new ModuleEntry(module, text));
            }

            moduleEntries = entries;
        }

        if (playerRadar.getValue()) {
            Comparator<AbstractClientPlayerEntity> distanceComparator = Comparator.comparingDouble(p -> mc.player.distanceTo(p));
            Comparator<AbstractClientPlayerEntity> alphabeticalComparator = Comparator.comparing(p -> p.getName().getString());

            long now = System.currentTimeMillis();
            List<PlayerEntry> entries = new ArrayList<>();

            long limit = playerRadarLimit.getValue().longValue();
            var playerStream = mc.world.getPlayers().stream()
                    .filter(p -> p != mc.player)
                    .filter(p -> !playerRadarAntiBot.getValue() || !EntityUtils.isBot(p))
                    .sorted(playerRadarSorting.getValue().equalsIgnoreCase("Distance") ? distanceComparator : alphabeticalComparator);

            List<AbstractClientPlayerEntity> players = (limit > 0 ? playerStream.limit(limit) : playerStream).toList();

            for (PlayerEntity player : players) {
                Identifier headTexture = null;

                if (playerIcons.getValue() && mc.getNetworkHandler() != null) {
                    PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(player.getName().getString());
                    if (entry != null) {
                        headTexture = entry.getSkinTextures().body().texturePath();
                    }
                }

                String text = player.getName().getString();

                if (playerRadarDistance.getValue()) text += Formatting.WHITE + " " + new DecimalFormat("0.0").format(mc.player.distanceTo(player));
                if (playerRadarEntityID.getValue()) text += Formatting.WHITE + " " + player.getId();
                if (playerRadarGameMode.getValue()) text += Formatting.WHITE + " [" + EntityUtils.getGameModeName(EntityUtils.getGameMode(player)) + "]";
                if (playerRadarPing.getValue()) text += Formatting.WHITE + " " + EntityUtils.getLatency(player) + "ms";
                if (playerRadarHealth.getValue()) text += ColorUtils.getHealthColor(player.getHealth() + player.getAbsorptionAmount()) + " " + new DecimalFormat("0.0").format(player.getHealth() + player.getAbsorptionAmount()) + Formatting.RESET;

                int pops = Anatorius.WORLD_MANAGER.getPoppedTotems().getOrDefault(player.getUuid(), 0);
                if (playerRadarTotems.getValue() && pops > 0) text += ColorUtils.getTotemColor(pops) + " -" + pops + Formatting.RESET;

                entries.add(new PlayerEntry(player, text, headTexture));
            }

            playerEntries = entries;

            int index = 0;
            for (PlayerEntry entry : entries) {
                UUID uuid = entry.player().getUuid();
                PlayerRadarState state = playerRadarStates.computeIfAbsent(uuid, u -> new PlayerRadarState((int) playerRadarAnimationTime.getValue().longValue()));

                state.entry = entry;
                state.orderIndex = index;
                state.lastSeen = now;
                state.seenThisTick = true;

                state.setTargetVisible(1.0f);
                index++;
            }

            for (Map.Entry<UUID, PlayerRadarState> stateEntry : new ArrayList<>(playerRadarStates.entrySet())) {
                PlayerRadarState state = stateEntry.getValue();
                if (!state.seenThisTick) {
                    state.setTargetVisible(0.0f);
                    float v = state.visible.get(0.0f);
                    if ((now - state.lastSeen) > (playerRadarAnimationTime.getValue().longValue() + 250L) && v <= 0.01f) {
                        playerRadarStates.remove(stateEntry.getKey());
                    }
                }
                state.seenThisTick = false;
            }
        }

        if (potions.getValue()) {
            Comparator<StatusEffectInstance> widthComparator = Comparator.comparingInt(e -> -Anatorius.FONT_MANAGER.getWidth(e.getEffectType().value().getName().getString() + " " + (e.getAmplifier() + 1)));
            Comparator<StatusEffectInstance> alphabeticalComparator = Comparator.comparing(e -> e.getEffectType().value().getName().getString());

            List<PotionEntry> entries = new ArrayList<>();
            List<StatusEffectInstance> effects = mc.player.getStatusEffects().stream()
                    .sorted(potionSorting.getValue().equalsIgnoreCase("Width") ? widthComparator : alphabeticalComparator)
                    .toList();

            for (StatusEffectInstance effect : effects) {
                String text = getPotionText(effect);
                Identifier sprite = null;

                if (potionIcons.getValue()) sprite = net.minecraft.client.gui.hud.InGameHud.getEffectTexture(effect.getEffectType());

                entries.add(new PotionEntry(text, sprite, potionColor.getValue().equalsIgnoreCase("Vanilla") ? new Color(effect.getEffectType().value().getColor()) : potionColor.getValue().equalsIgnoreCase("Enhanced") ? (EntityUtils.POTION_COLORS.containsKey(effect.getEffectType().value()) ? EntityUtils.POTION_COLORS.get(effect.getEffectType().value()) : new Color(effect.getEffectType().value().getColor())) : null));
            }

            potionEntries = entries;
        }

        if (itemCounterAnimations.getValue().equalsIgnoreCase("Popup")) {
            int duration = (int) itemCounterAnimationTime.getValue().longValue();

            if (totemCounter.getValue()) {
                int totems = mc.player.getInventory().count(Items.TOTEM_OF_UNDYING);
                if (lastTotemCount != -1 && totems < lastTotemCount) {
                    totemDecreasePopup = new ItemDecreasePopup(lastTotemCount - totems, duration);
                }
                lastTotemCount = totems;
            } else {
                lastTotemCount = -1;
                totemDecreasePopup = null;
            }

            if (crystalCounter.getValue()) {
                int crystals = mc.player.getInventory().count(Items.END_CRYSTAL);
                if (lastCrystalCount != -1 && crystals < lastCrystalCount) {
                    crystalDecreasePopup = new ItemDecreasePopup(lastCrystalCount - crystals, duration);
                }
                lastCrystalCount = crystals;
            } else {
                lastCrystalCount = -1;
                crystalDecreasePopup = null;
            }

            if (xpCounter.getValue()) {
                int experienceBottles = mc.player.getInventory().count(Items.EXPERIENCE_BOTTLE);
                if (lastXpCount != -1 && experienceBottles < lastXpCount) {
                    xpDecreasePopup = new ItemDecreasePopup(lastXpCount - experienceBottles, duration);
                }
                lastXpCount = experienceBottles;
            } else {
                lastXpCount = -1;
                xpDecreasePopup = null;
            }
        } else if (itemCounterAnimations.getValue().equalsIgnoreCase("Rolling")) {
            if (totemCounter.getValue()) totemSlot.update(mc.player.getInventory().count(Items.TOTEM_OF_UNDYING));
            if (crystalCounter.getValue()) crystalSlot.update(mc.player.getInventory().count(Items.END_CRYSTAL));
            if (xpCounter.getValue()) xpSlot.update(mc.player.getInventory().count(Items.EXPERIENCE_BOTTLE));
        } else {
            lastTotemCount = -1;
            lastCrystalCount = -1;
            lastXpCount = -1;
            totemDecreasePopup = null;
            crystalDecreasePopup = null;
            xpDecreasePopup = null;
        }
    }

    @SubscribeEvent
    public void renderWatermark(RenderOverlayEvent event) {
        if (mc.player == null) return;

        chatOffset = chatAnimation.get(mc.currentScreen instanceof ChatScreen ? 14 : 0);

        Renderer2D.renderQuad(event.getContext(), 2, mc.getWindow().getScaledHeight() - chatOffset, mc.getWindow().getScaledWidth() - 2, mc.getWindow().getScaledHeight() + 12 - chatOffset, new Color(0, 0, 0, (int) (mc.options.getTextBackgroundOpacity().getValue() * 255)));

        int fontHeight = Anatorius.FONT_MANAGER.getHeight();

        if (watermark.getValue()) {
            String text = watermarkText.getValue() + (watermarkVersion.getValue() ? (watermarkSync.getValue() ? "" : inversion.getValue() ? Formatting.GRAY : Formatting.WHITE) + " " + Anatorius.MOD_VERSION + (watermarkMinecraftVersion.getValue() ? "-mc" + Anatorius.MINECRAFT_VERSION : "") + (watermarkRevision.getValue() ? "+" + Anatorius.GIT_REVISION + "." + Anatorius.GIT_HASH : "") : "");
            float x = watermarkElement.resolveX(2);
            float y = watermarkElement.resolveY(0);
            if (mio()) {
                Seg[] segs = watermarkVersion.getValue()
                        ? new Seg[]{mioSeg(watermarkText.getValue()), whiteSeg(" v" + Anatorius.MOD_VERSION + (watermarkMinecraftVersion.getValue() ? "-mc" + Anatorius.MINECRAFT_VERSION : "") + (watermarkRevision.getValue() ? "+" + Anatorius.GIT_REVISION + "." + Anatorius.GIT_HASH : ""))}
                        : new Seg[]{mioSeg(watermarkText.getValue())};
                watermarkElement.updateBounds(x, y, mioWidth(segs), fontHeight);
                drawMio(event.getContext(), x, y, segs);
            } else {
                watermarkElement.updateBounds(x, y, Anatorius.FONT_MANAGER.getWidth(text), fontHeight);
                drawText(event.getContext(), text, x, y);
            }
        }

        if(uptime.getValue()) {
            String[] hms = FormattingUtils.formatSeconds((System.currentTimeMillis() - Anatorius.UPTIME)/1000);
            String text = "Uptime " + Formatting.WHITE + hms[0] + ":" + hms[1] + ":" + hms[2];
            float x = uptimeElement.resolveX(2);
            float y = uptimeElement.resolveY(2 + (watermark.getValue() ? fontHeight : 0));
            if (mio()) {
                Seg[] segs = {mioSeg("Uptime "), whiteSeg(hms[0] + ":" + hms[1] + ":" + hms[2])};
                uptimeElement.updateBounds(x, y, mioWidth(segs), fontHeight);
                drawMio(event.getContext(), x, y, segs);
            } else {
                uptimeElement.updateBounds(x, y, Anatorius.FONT_MANAGER.getWidth(text), fontHeight);
                drawText(event.getContext(), text, x, y, informationSync.getValue() ? null : new Color(170, 170, 170));
            }
        }

        renderSpotify(event, fontHeight);

        if (welcomer.getValue()) {
            String displayText = mc.getSession().getUsername();

            int hour = java.time.LocalTime.now().getHour();
            String greeting = hour >= 5 && hour < 12 ? "Good Morning"
                    : hour >= 12 && hour < 18 ? "Good Afternoon"
                    : hour >= 18 && hour < 22 ? "Good Evening" : "Good Night";

            String coloredName = (welcomerSync.getValue() ? "" : inversion.getValue() ? Formatting.GRAY : Formatting.WHITE) + displayText + Formatting.RESET;
            String text = greeting + ", " + coloredName;
            int width = Anatorius.FONT_MANAGER.getWidth(text);
            float x = welcomerElement.resolveX(mc.getWindow().getScaledWidth() / 2.0f - width / 2.0f);
            float y = welcomerElement.resolveY(0);
            welcomerElement.updateBounds(x, y, width, fontHeight);
            if (mio()) drawText(event.getContext(), text, x, y, false, mioColor(y));
            else drawText(event.getContext(), text, x, y);
        }
    }

    @SubscribeEvent
    public void renderModuleList(RenderOverlayEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!moduleList.getValue()) return;

        float potionOffset = potionsAnimation.get(!vanillaPotions.getValue().equalsIgnoreCase("Move") || mc.player.getStatusEffects().isEmpty() ? 0 : (EntityUtils.hasNegativeEffects(mc.player) ? 51 : 25));

        int fontHeight = Anatorius.FONT_MANAGER.getHeight();
        int maxWidth = 0;
        for (ModuleEntry entry : moduleEntries) maxWidth = Math.max(maxWidth, Anatorius.FONT_MANAGER.getWidth(entry.text()));

        float defaultLeft = mc.getWindow().getScaledWidth() - maxWidth - 2;
        float defaultTop = 2 + potionOffset;
        boolean custom = moduleListElement.isCustomized();
        float blockLeft = moduleListElement.resolveX(defaultLeft);
        float blockTop = moduleListElement.resolveY(defaultTop);
        moduleListElement.updateBounds(custom ? blockLeft : defaultLeft, custom ? blockTop : defaultTop, maxWidth, moduleEntries.size() * fontHeight);

        int index = 0;
        for (ModuleEntry entry : moduleEntries) {
            float x, y;
            if (custom) {
                x = blockLeft;
                y = blockTop + index * fontHeight;
            } else {
                x = mc.getWindow().getScaledWidth() - (entry.module().getAnimationOffset().get(entry.module().isToggled() ? Anatorius.FONT_MANAGER.getWidth(entry.text()) + 2 : 0));
                y = 2 + potionOffset + (index * fontHeight);
            }

            drawModuleText(entry.module(), event.getContext(), entry.text(), x, y);
            index++;
        }
    }

    @SubscribeEvent
    public void renderPlayerRadar(RenderOverlayEvent event) {
        if (!playerRadar.getValue()) return;

        int fontHeight = Anatorius.FONT_MANAGER.getHeight();
        float lineHeight = fontHeight + 1;

        float baseX = playerRadarElement.resolveX(2);
        float baseY = playerRadarElement.resolveY(2 + (fontHeight * 2));

        List<PlayerRadarState> states = new ArrayList<>(playerRadarStates.values());
        states.sort(Comparator.comparingInt(s -> s.orderIndex));

        int offset = 0;
        int maxWidth = 0;
        int visibleCount = 0;
        for (PlayerRadarState state : states) {
            if (state.entry == null) continue;

            PlayerEntry entry = state.entry;
            float visible = playerRadarAnimations.getValue() ? state.visible.get(state.targetVisible) : 1.0f;
            if (visible <= 0.01f) {
                offset++;
                continue;
            }

            float targetY = baseY + (lineHeight * offset);
            float y = playerRadarAnimations.getValue() ? state.y.get(targetY) : targetY;
            float slide = playerRadarAnimations.getValue() ? (1.0f - visible) * -playerRadarSlideDistance.getValue().floatValue() : 0.0f;

            int alpha = Math.min(255, Math.max(0, (int) (visible * 255.0f)));

            float iconWidth = entry.headTexture() != null ? fontHeight + 2 : 0;
            if (entry.headTexture() != null) {
                int argb = (alpha << 24) | 0xFFFFFF;
                PlayerSkinDrawer.draw(event.getContext(), entry.headTexture(), (int) baseX, (int) (1 + y + slide), fontHeight, true, false, argb);
            }

            Color baseColor = Anatorius.FRIEND_MANAGER.contains(entry.player().getName().getString()) ? Anatorius.FRIEND_MANAGER.getDefaultFriendColor() : null;
            drawText(event.getContext(), entry.text(), baseX + iconWidth, y + slide, false, withAlpha(baseColor == null ? getHudColor(y) : baseColor, alpha));

            maxWidth = Math.max(maxWidth, (int) iconWidth + Anatorius.FONT_MANAGER.getWidth(entry.text()));
            visibleCount++;
            offset++;
        }

        playerRadarElement.updateBounds(baseX, baseY, maxWidth, (int) (visibleCount * lineHeight));
    }

    @SubscribeEvent
    public void renderPlayerModel(RenderOverlayEvent event) {
        if (mc.player == null || !playerModel.getValue()) return;
        float scale = playerModelScale.getValue().floatValue();
        int width = Math.round(50 * scale), height = Math.round(80 * scale);
        float x = playerModelElement.resolveX(4);
        float y = playerModelElement.resolveY(mc.getWindow().getScaledHeight() / 2.0f - height / 2.0f);
        playerModelElement.updateBounds(x, y, width, height);

        var state = mc.getEntityRenderDispatcher().getRenderer(mc.player)
                .getAndUpdateRenderState(mc.player, mc.getRenderTickCounter().getTickProgress(true));
        state.light = 15728880;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        org.joml.Vector3f translation = new org.joml.Vector3f(0.0f, mc.player.getHeight() / 2.0f, 0.0f);
        org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotateZ((float) Math.PI);
        int x1 = Math.round(x), y1 = Math.round(y);
        event.getContext().addEntity(state, 35.0f * scale, translation, rotation, null, x1, y1, x1 + width, y1 + height);
    }

    private float parallaxX, parallaxY, lastParallaxYaw = Float.NaN, lastParallaxPitch;
    private long lastParallaxFrame;
    private static final float PARALLAX_MAX = 14.0f;

    public float[] parallaxOffset() {
        if (!isToggled() || !parallax.getValue() || mc.player == null || mc.currentScreen instanceof dev.project.anatorius.gui.HudEditorScreen) {
            parallaxX = parallaxY = 0.0f;
            lastParallaxYaw = Float.NaN;
            return new float[]{0.0f, 0.0f};
        }
        long now = System.nanoTime();
        float seconds = lastParallaxFrame == 0L ? 0.0f : Math.min(0.1f, (now - lastParallaxFrame) / 1.0E9f);
        lastParallaxFrame = now;
        float yaw = mc.player.getYaw(), pitch = mc.player.getPitch();
        if (!Float.isNaN(lastParallaxYaw)) {
            float strength = parallaxStrength.getValue().floatValue() * 0.35f;
            parallaxX -= MathHelper.wrapDegrees(yaw - lastParallaxYaw) * strength;
            parallaxY -= (pitch - lastParallaxPitch) * strength;
        }
        lastParallaxYaw = yaw;
        lastParallaxPitch = pitch;
        float keep = (float) Math.exp(-seconds * 7.0);
        parallaxX = MathHelper.clamp(parallaxX * keep, -PARALLAX_MAX, PARALLAX_MAX);
        parallaxY = MathHelper.clamp(parallaxY * keep, -PARALLAX_MAX, PARALLAX_MAX);
        return new float[]{parallaxX, parallaxY};
    }

    private void renderSpotify(RenderOverlayEvent event, int fontHeight) {
        if (!spotify.getValue()) return;
        String artist = dev.project.anatorius.utils.system.SpotifyTracker.getArtist();
        String song = dev.project.anatorius.utils.system.SpotifyTracker.getSong();
        String track;
        if (artist != null && song != null) {
            track = artist + " - " + song;
            if (track.length() > 64) track = track.substring(0, 61) + "...";
        } else if (editor.isEditing()) {
            track = "Nothing playing";
        } else {
            return;
        }

        String label = "Spotify ";
        int width = Anatorius.FONT_MANAGER.getWidth(label + track);
        float x = spotifyElement.resolveX(mc.getWindow().getScaledWidth() / 2.0f - width / 2.0f);
        float y = spotifyElement.resolveY(2 + fontHeight);
        if (mio()) {
            Seg[] segs = {mioSeg(label), whiteSeg(track)};
            spotifyElement.updateBounds(x, y, mioWidth(segs), fontHeight);
            drawMio(event.getContext(), x, y, segs);
        } else {
            spotifyElement.updateBounds(x, y, width, fontHeight);
            drawText(event.getContext(), label + (inversion.getValue() ? Formatting.GRAY : Formatting.WHITE) + track, x, y);
        }
    }

    @SubscribeEvent
    public void renderItems(RenderOverlayEvent event) {
        if (mc.player == null) return;

        org.joml.Matrix3x2fStack matrices = event.getContext().getMatrices();
        boolean editing = editor.isEditing();

        if (armor.getValue()) {
            List<ItemStack> pieces = new ArrayList<>();
            for (ItemStack stack : ItemUtils.getArmorItems(mc.player)) if (!stack.isEmpty()) pieces.add(stack);

            if (!pieces.isEmpty()) {
                int wateroffset = (mc.player.isSubmergedInWater() || mc.player.getAir() < mc.player.getMaxAir()) ? 10 : 0;
                int count = pieces.size();

                float defaultLeft = mc.getWindow().getScaledWidth() / 2f + 69 - 18 * (count - 1);
                float defaultY = mc.getWindow().getScaledHeight() - 55 - wateroffset;
                float left = armorElement.resolveX(defaultLeft);
                float y = armorElement.resolveY(defaultY);
                armorElement.updateBounds(left, y, 18 * count, 16);

                for (int i = 0; i < count; i++) {
                    ItemStack stack = pieces.get(i);
                    int x = (int) (left + 18 * i);

                    event.getContext().drawItem(stack, x, (int) y);
                    event.getContext().drawStackOverlay(mc.textRenderer, stack, x, (int) y);

                    int damage = stack.getDamage();
                    int maxDamage = stack.getMaxDamage();

                    if (armorDurability.getValue().equalsIgnoreCase("Percentage") || armorDurability.getValue().equalsIgnoreCase("Both") && maxDamage > 0) {
                        matrices.pushMatrix();
                        matrices.scale(0.625f, 0.625f);
                        drawText(event.getContext(), (((maxDamage - damage) * 100) / maxDamage) + "%", (int) ((x + 1) * 1.6F), (int) ((y - 3) * 1.6F - 5), false, new Color(1.0f - ((maxDamage - damage) / (float) maxDamage), (maxDamage - damage) / (float) maxDamage, 0));
                        matrices.popMatrix();
                    }
                }
            }
        }

        if (totemCounter.getValue()) {
            int totems = mc.player.getInventory().count(Items.TOTEM_OF_UNDYING);
            boolean animating = itemCounterAnimations.getValue().equalsIgnoreCase("Rolling") && totemSlot.isAnimating();
            if (totems > 0 || animating || editing) {
                ItemStack stack = new ItemStack(Items.TOTEM_OF_UNDYING);
                float defaultX = (mc.getWindow().getScaledWidth() / 2f) - 9;
                float defaultY = mc.getWindow().getScaledHeight() - 55 - ((mc.player.isSubmergedInWater() && mc.interactionManager.getCurrentGameMode() != GameMode.CREATIVE) ? 10 : 0);
                int x = (int) totemElement.resolveX(defaultX);
                int y = (int) totemElement.resolveY(defaultY);
                totemElement.updateBounds(x, y, 16, 16);

                event.getContext().drawItem(stack, x, y);

                if (itemCounterAnimations.getValue().equalsIgnoreCase("Rolling")) {
                    totemSlot.render(event.getContext(), x, y, mc.textRenderer, stack);
                } else {
                    event.getContext().drawStackOverlay(mc.textRenderer, stack, x, y, String.valueOf(totems));
                }

                renderItemDecreasePopup(event.getContext(), totemDecreasePopup, x + 9, y - 2);
            }
        }

        boolean renderedXpCounter = false;
        if (xpCounter.getValue()) {
            int experienceBottles = mc.player.getInventory().count(Items.EXPERIENCE_BOTTLE);
            boolean animating = itemCounterAnimations.getValue().equalsIgnoreCase("Rolling") && xpSlot.isAnimating();
            if (experienceBottles > 0 || animating || editing) {
                ItemStack stack = new ItemStack(Items.EXPERIENCE_BOTTLE);
                float chatShift = counterChatOffset.getValue() ? chatOffset : 0;
                float defaultX = (mc.getWindow().getScaledWidth() / 2f) + 106;
                float defaultY = mc.getWindow().getScaledHeight() - 20;
                float x = xpElement.resolveX(defaultX);
                float y = xpElement.resolveY(defaultY) - chatShift;
                xpElement.updateBounds(x, y, 16, 16);

                matrices.pushMatrix();
                matrices.translate(x, y);
                event.getContext().drawItem(stack, 0, 0);

                if (itemCounterAnimations.getValue().equalsIgnoreCase("Rolling")) {
                    matrices.popMatrix();
                    xpSlot.render(event.getContext(), (int) x, (int) y, mc.textRenderer, stack);
                } else {
                    event.getContext().drawStackOverlay(mc.textRenderer, stack, 0, 0, String.valueOf(experienceBottles));
                    matrices.popMatrix();
                }

                renderItemDecreasePopup(event.getContext(), xpDecreasePopup, x + 8, y - 2);

                renderedXpCounter = true;
            }
        }

        if (crystalCounter.getValue()) {
            int crystals = mc.player.getInventory().count(Items.END_CRYSTAL);
            boolean animating = itemCounterAnimations.getValue().equalsIgnoreCase("Rolling") && crystalSlot.isAnimating();
            if (crystals > 0 || animating || editing) {
                ItemStack stack = new ItemStack(Items.END_CRYSTAL);
                float chatShift = counterChatOffset.getValue() ? chatOffset : 0;
                float defaultX = (mc.getWindow().getScaledWidth() / 2f) + 106;
                float defaultY = mc.getWindow().getScaledHeight() - (renderedXpCounter ? 40 : 20);
                float x = crystalElement.resolveX(defaultX);
                float y = crystalElement.resolveY(defaultY) - chatShift;
                crystalElement.updateBounds(x, y, 16, 16);

                matrices.pushMatrix();
                matrices.translate(x, y);
                event.getContext().drawItem(stack, 0, 0);

                if (itemCounterAnimations.getValue().equalsIgnoreCase("Rolling")) {
                    matrices.popMatrix();
                    crystalSlot.render(event.getContext(), (int) x, (int) y, mc.textRenderer, stack);
                } else {
                    event.getContext().drawStackOverlay(mc.textRenderer, stack, 0, 0, String.valueOf(crystals));
                    matrices.popMatrix();
                }

                renderItemDecreasePopup(event.getContext(), crystalDecreasePopup, x + 8, y - 2);
            }
        }
    }

    @SubscribeEvent
    public void renderInformation(RenderOverlayEvent event) {
        if (mc.player == null) return;

        org.joml.Matrix3x2fStack matrices = event.getContext().getMatrices();

        int fontHeight = Anatorius.FONT_MANAGER.getHeight();
        float chatShift = informationChatOffset.getValue() ? this.chatOffset : 0;
        Color infoColor = informationSync.getValue() ? null : new Color(170, 170, 170);

        if (health.getValue()) {
            String text = ColorUtils.getHealthColor(mc.player.getHealth() + mc.player.getAbsorptionAmount()) + new DecimalFormat("0").format(mc.player.getHealth() + mc.player.getAbsorptionAmount());
            int width = Anatorius.FONT_MANAGER.getWidth(text);
            float x = healthElement.resolveX(mc.getWindow().getScaledWidth() / 2f - width / 2f);
            float y = healthElement.resolveY(mc.getWindow().getScaledHeight() / 2f + 16);
            healthElement.updateBounds(x, y, width, fontHeight);
            Anatorius.FONT_MANAGER.drawTextWithOutline(event.getContext(), text, (int) x, (int) y, Color.WHITE, Color.BLACK);
        }

        List<String> informationEntries = new ArrayList<>();
        if (ping.getValue()) informationEntries.add(infoLine("Ping", Anatorius.SERVER_MANAGER.getPing() + "ms"));
        if (fps.getValue()) informationEntries.add(infoLine("FPS", String.valueOf(Anatorius.RENDER_MANAGER.getFps())));
        if (durability.getValue()) informationEntries.add("Durability " + (mc.player.getMainHandStack().getMaxDamage() - mc.player.getMainHandStack().getDamage()));
        if (!speed.getValue().equalsIgnoreCase("None")) informationEntries.add(infoLine("Speed", new DecimalFormat("0.00").format(EntityUtils.getSpeed(mc.player, speed.getValue().equalsIgnoreCase("Meters") ? EntityUtils.SpeedUnit.METERS : EntityUtils.SpeedUnit.KILOMETERS)) + (speed.getValue().equalsIgnoreCase("Meters") ? "m/s" : "km/h")));
        if (serverBrand.getValue()) informationEntries.add(infoLine("Brand", String.valueOf(Anatorius.SERVER_MANAGER.getServerBrand())));

        float tickRate = Anatorius.SERVER_MANAGER.getTickRate();
        if (tps.getValue()) informationEntries.add(infoLine("TPS", tickRate > 19.79 ? "20.00" : new DecimalFormat("00.00").format(tickRate)));

        informationEntries.sort(Comparator.comparingInt((String line) -> Anatorius.FONT_MANAGER.getWidth(line)).reversed());

        List<String> infoDraw = new ArrayList<>();
        int maxWidth = 0;
        for (String text : informationEntries) {
            if (text.startsWith("Durability") && !mc.player.getMainHandStack().isDamageable()) continue;
            infoDraw.add(text);
            maxWidth = Math.max(maxWidth, Anatorius.FONT_MANAGER.getWidth(text));
        }

        int potionCount = potions.getValue() ? potionEntries.size() : 0;
        if (potions.getValue()) {
            for (PotionEntry entry : potionEntries) maxWidth = Math.max(maxWidth, Anatorius.FONT_MANAGER.getWidth(entry.text()) + (entry.sprite() != null ? fontHeight + 2 : 0));
        }

        int infoDrawn = infoDraw.size();
        int totalRows = infoDrawn + potionCount;

        if (totalRows > 0) {
            float columnHeight = totalRows * fontHeight;
            float defaultLeft = mc.getWindow().getScaledWidth() - 2 - maxWidth;
            float defaultTop = mc.getWindow().getScaledHeight() - 2 - columnHeight;
            float left = informationElement.resolveX(defaultLeft);
            float top = informationElement.resolveY(defaultTop) - chatShift;
            float right = left + maxWidth;
            informationElement.updateBounds(left, top, maxWidth, (int) columnHeight);

            for (int i = 0; i < infoDrawn; i++) {
                String text = infoDraw.get(i);
                float rowY = top + (infoDrawn - 1 - i) * fontHeight;
                if (mio()) {
                    int sp = text.indexOf(' ');
                    drawMio(event.getContext(), right - Anatorius.FONT_MANAGER.getWidth(text), rowY, mioSeg(text.substring(0, sp + 1)), whiteSeg(text.substring(sp + 1)));
                } else if (text.startsWith("Durability")) {
                    int maxDamage = mc.player.getMainHandStack().getMaxDamage(), damage = mc.player.getMainHandStack().getDamage();
                    String s = String.valueOf(maxDamage - damage);
                    drawText(event.getContext(), getPrimary() + "Durability ", right - Anatorius.FONT_MANAGER.getWidth("Durability ") - Anatorius.FONT_MANAGER.getWidth(s), rowY, infoColor);
                    drawText(event.getContext(), s, right - Anatorius.FONT_MANAGER.getWidth(s), rowY, false, new Color(1.0f - ((maxDamage - damage) / (float) maxDamage), (maxDamage - damage) / (float) maxDamage, 0));
                } else {
                    drawText(event.getContext(), text, right - Anatorius.FONT_MANAGER.getWidth(text), rowY, infoColor);
                }
            }

            if (potions.getValue()) {
                boolean rainbow = potionColor.getValue().equals("Client") && colorMode.getValue().equals("Rainbow") && rainbowMode.getValue().equals("Horizontal");
                for (int p = 0; p < potionCount; p++) {
                    PotionEntry entry = potionEntries.get(p);
                    float rowY = top + (totalRows - 1 - p) * fontHeight;
                    float textX = right - Anatorius.FONT_MANAGER.getWidth(entry.text());

                    if (entry.sprite() != null) {
                        matrices.pushMatrix();
                        matrices.translate(textX - fontHeight - 2, rowY - 1);
                        event.getContext().drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, entry.sprite(), 0, 0, fontHeight, fontHeight, -1);
                        matrices.popMatrix();
                    }

                    if (mio()) {
                        String pt = entry.text();
                        int sp = pt.lastIndexOf(' ');
                        drawMio(event.getContext(), textX, rowY, mioSeg(pt.substring(0, sp)), whiteSeg(pt.substring(sp)));
                    } else {
                        drawText(event.getContext(), entry.text(), textX, rowY, rainbow, entry.color());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void renderCoordinates(RenderOverlayEvent event) {
        if (mc.player == null || mc.world == null) return;

        float chatShift = positionChatOffset.getValue() ? this.chatOffset : 0;
        int fontHeight = Anatorius.FONT_MANAGER.getHeight();

        if (mio()) {
            renderCoordinatesMio(event, chatShift, fontHeight);
            return;
        }

        String coordText = null;
        if (coordinates.getValue()) {
            coordText = getSecondary() + String.valueOf(mc.player.getBlockX()) + (netherCoordinates.getValue() ? Formatting.GRAY + " [" + getSecondary() + WorldUtils.getNetherPosition(mc.player.getBlockX()) + Formatting.GRAY + "]" : "") + (inversion.getValue() || positionSync.getValue() ? Formatting.RESET : Formatting.GRAY) + ", " + getSecondary() + mc.player.getBlockY() + (inversion.getValue() || positionSync.getValue() ? Formatting.RESET : Formatting.GRAY) + ", " + getSecondary() + mc.player.getBlockZ() + (netherCoordinates.getValue() ? Formatting.GRAY + " [" + getSecondary() + WorldUtils.getNetherPosition(mc.player.getBlockZ()) + Formatting.GRAY + "]" : "");
        }

        String dirText = null;
        if (direction.getValue()) {
            dirText = getPrimary() + StringUtils.capitalize(mc.player.getMovementDirection().asString()) + (inversion.getValue() ? getSecondary() : Formatting.GRAY) + " [" + (inversion.getValue() ? getSecondary() : Formatting.WHITE) + WorldUtils.getMovementDirection(mc.player.getMovementDirection()) + (inversion.getValue() ? getSecondary() : Formatting.GRAY) + "]";
        }

        int drawn = 0, maxWidth = 0;
        if (coordText != null) { drawn++; maxWidth = Math.max(maxWidth, Anatorius.FONT_MANAGER.getWidth(coordText)); }
        if (dirText != null) { drawn++; maxWidth = Math.max(maxWidth, Anatorius.FONT_MANAGER.getWidth(dirText)); }
        if (drawn == 0) return;

        float defaultTop = mc.getWindow().getScaledHeight() - 2 - drawn * fontHeight;
        float left = positionElement.resolveX(2);
        float top = positionElement.resolveY(defaultTop) - chatShift;
        positionElement.updateBounds(left, top, maxWidth, drawn * fontHeight);

        float rowY = top;
        if (dirText != null && coordText != null) {
            drawText(event.getContext(), dirText, left, rowY, positionSync.getValue() ? null : Color.WHITE);
            rowY += fontHeight;
            drawText(event.getContext(), coordText, left, rowY);
        } else if (coordText != null) {
            drawText(event.getContext(), coordText, left, rowY);
        } else {
            drawText(event.getContext(), dirText, left, rowY, positionSync.getValue() ? null : Color.WHITE);
        }
    }

    @SubscribeEvent
    public void renderLagNotify(RenderOverlayEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!lagNotify.getValue()) return;

        int baseWidth = mc.getWindow().getScaledWidth() / 2;
        int baseHeight = mc.getWindow().getScaledHeight() / 4;
        int x = baseWidth + lagNotifyXOffset.getValue().intValue();
        int y = baseHeight + lagNotifyYOffset.getValue().intValue();

        boolean showServer = lagNotifyServer.getValue() && Anatorius.SERVER_MANAGER.getResponseTimer().hasTimeElapsed(1000);
        boolean showLagback = lagNotifyLagback.getValue() && System.currentTimeMillis() - lagTime < 3000;

        if (showServer && !serverLagActive) {
            serverLagActive = true;
            serverLagStart = System.currentTimeMillis();
        } else if (!showServer && serverLagActive) {
            serverLagActive = false;
        }

        float serverProgress = serverNotificationAnimation.get(showServer ? 1.0f : 0.0f);
        float lagbackProgress = lagbackNotificationAnimation.get(showLagback ? 1.0f : 0.0f);

        int currentY = y;

        if (serverProgress > 0.01f) {
            String text = "Server not responding: " + MathUtils.round(Anatorius.SERVER_MANAGER.getResponseTimer().timeElapsed()/1000f, 1) + "s";
            renderNotification(event, text, x, currentY, serverProgress, lagNotifyColor.getColor());
            currentY += Anatorius.FONT_MANAGER.getHeight() + 8;
        }

        if (lagbackProgress > 0.01f) {
            String text = "Lagback: " + MathUtils.round(lagDistance, 1) + " blocks - " + MathUtils.round((System.currentTimeMillis() - lagTime) / 1000f, 1) + "s";
            renderNotification(event, text, x, currentY, lagbackProgress, lagNotifyColor.getColor());
        }
    }

    private void renderNotification(RenderOverlayEvent event, String text, int x, int y, float progress, Color textColor) {
        int width = Anatorius.FONT_MANAGER.getWidth(text);
        int height = Anatorius.FONT_MANAGER.getHeight();

        int xPos = x - width / 2 - 4;
        int yPos = y - height;

        if (lagNotifyAnimations.getValue()) {
            float ease = Easing.ease(progress, Easing.Method.EASE_OUT_CUBIC);
            yPos -= (int) ((1.0f - ease) * 20);

            int alpha = (int) (progress * 255);
            textColor = new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), alpha);
        }

        if (lagNotifyBackground.getValue()) {
            Color bgColor = lagNotifyBackgroundColor.getColor();
            if (lagNotifyAnimations.getValue()) {
                int bgAlpha = (int) (progress * bgColor.getAlpha());
                bgColor = new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), bgAlpha);
            }
            Renderer2D.renderQuad(event.getContext(), xPos - 2, yPos - 2, xPos + width + 6, yPos + height + 2, bgColor);
        }

        Anatorius.FONT_MANAGER.drawTextWithShadow(event.getContext(), text, xPos, yPos, textColor);
    }

    private void drawModuleText(Module module, DrawContext context, String text, float x, float y) {
        if (mio()) {
            String meta = module.getMetaData();
            if (meta == null || meta.isEmpty() || !metaData.getValue()) drawMio(context, x, y, mioSeg(module.getName()));
            else drawMio(context, x, y, mioSeg(module.getName()), greySeg(" ["), whiteSeg(meta), greySeg("]"));
            return;
        }
        Color color = getHudColor(y);
        if (moduleColorMode.getValue().equalsIgnoreCase("Rainbow")) {
            long index = ((long) y / Anatorius.FONT_MANAGER.getHeight()) * (rainbowOffset.getValue().longValue() * 10L);
            color = ColorUtils.getOffsetRainbow(index);
        } else if (moduleColorMode.getValue().equals("Random")) {
            color = ColorUtils.getHashColor(module.getName());
        }

        drawText(context, text, x, y, (moduleColorMode.getValue().equals("Rainbow") || (moduleColorMode.getValue().equals("Default") && colorMode.getValue().equals("Rainbow")))&& rainbowMode.getValue().equals("Horizontal"), color);
    }

    private void drawText(DrawContext context, String text, float x, float y) {
        drawText(context, text, x, y, null);
    }

    public void drawText(DrawContext context, String text, float x, float y, Color color) {
        drawText(context, text, x, y, colorMode.getValue().equals("Rainbow") && rainbowMode.getValue().equals("Horizontal"), color);
    }

    public void drawText(DrawContext context, String text, float x, float y, boolean rainbow, Color color) {
        if (color == null) color = getHudColor(y);

        org.joml.Matrix3x2fStack matrices = context.getMatrices();

        matrices.pushMatrix();
        matrices.translate(x, y);

        if (glow.getValue() && glowStrength.getValue().intValue() > 0) {
            drawTextGlow(context, text, color, y, rainbow);
        }

        if (rainbow)  {
            Anatorius.FONT_MANAGER.drawRainbowString(context, text, 0, 0, rainbowOffset.getValue().longValue() * 5L);
        } else {
            Anatorius.FONT_MANAGER.drawTextWithShadow(context, text, 0, 0, color);
        }

        matrices.popMatrix();
    }

    private void drawTextGlow(DrawContext context, String text, Color base, float y, boolean rainbow) {
        String stripped = text.replaceAll("(?i)§[0-9A-FK-OR]", "");
        if (stripped.isBlank()) return;
        Color glowColor = rainbow ? getHudColor(y) : base;
        float strength = Math.min(1.0f, glowStrength.getValue().intValue() / 255.0f * 1.8f);
        dev.project.anatorius.utils.graphics.TextGlow.draw(context, stripped, glowColor, glowRadius.getValue().intValue(), strength);
    }

    private Color getHudColor(float offset) {
        if (colorMode.getValue().equalsIgnoreCase("Rainbow")) {
            long index = ((long) offset / Anatorius.FONT_MANAGER.getHeight()) * (rainbowOffset.getValue().longValue() * 10L);
            return ColorUtils.getOffsetRainbow(index);
        } else if (colorMode.getValue().equalsIgnoreCase("Wave")) {
            long index = ((long) offset / Anatorius.FONT_MANAGER.getHeight()) * (rainbowOffset.getValue().longValue() * 20L);
            return ColorUtils.getOffsetWave(ColorUtils.getGlobalColor(), index);
        } else if (colorMode.getValue().equalsIgnoreCase("Custom")) {
            return ColorUtils.getColor(customColor.getColor(), 255);
        }
        return ColorUtils.getGlobalColor();
    }

    private String getModuleText(Module module) {
        String meta = module.getMetaData();
        if (meta == null || meta.isEmpty() || !metaData.getValue()) return module.getName();
        if (mio()) return module.getName() + " [" + meta + "]";
        return module.getName() + Formatting.GRAY + " [" + Formatting.WHITE + meta + Formatting.GRAY + "]";
    }

    private String getPotionText(StatusEffectInstance effect) {
        if (mio()) return effect.getEffectType().value().getName().getString() + " " + (effect.getAmplifier() + 1) + " " + StatusEffectUtil.getDurationText(effect, 1.0F, mc.world.getTickManager().getTickRate()).getString().replace(Text.translatable("effect.duration.infinite").getString(), "**:**");
        return effect.getEffectType().value().getName().getString() + " " + (effect.getAmplifier() + 1) + " " + Formatting.WHITE + StatusEffectUtil.getDurationText(effect, 1.0F, mc.world.getTickManager().getTickRate()).getString().replace(Text.translatable("effect.duration.infinite").getString(), "**:**");
    }

    private record Seg(String text, int kind) {}

    private Seg mioSeg(String t) { return new Seg(t, 0); }
    private Seg whiteSeg(String t) { return new Seg(t, 1); }
    private Seg greySeg(String t) { return new Seg(t, 2); }

    private boolean mio() {
        GuiChangerModule g = Anatorius.MODULE_MANAGER.getModule(GuiChangerModule.class);
        return g != null && g.mioHudActive();
    }

    private Color mioColor(float y) {
        return Anatorius.MODULE_MANAGER.getModule(GuiChangerModule.class).mioColorAt((int) y);
    }

    private int mioWidth(Seg... segs) {
        int w = 0;
        for (Seg s : segs) w += Anatorius.FONT_MANAGER.getWidth(s.text());
        return w;
    }

    private void drawMio(DrawContext context, float x, float y, Seg... segs) {
        float cx = x;
        for (Seg s : segs) {
            Color c = s.kind() == 0 ? mioColor(y) : s.kind() == 1 ? Color.WHITE : new Color(0xAAAAAA);
            drawText(context, s.text(), cx, y, false, c);
            cx += Anatorius.FONT_MANAGER.getWidth(s.text());
        }
    }

    private String infoLine(String label, String value) {
        if (mio()) return label + " " + value;
        return getPrimary() + label + " " + getSecondary() + value;
    }

    private static final String[] MIO_DIR_NAMES = {"South", "SouthWest", "West", "NorthWest", "North", "NorthEast", "East", "SouthEast"};
    private static final String[] MIO_DIR_AXES = {"+Z", "-X +Z", "-X", "-X -Z", "-Z", "+X -Z", "+X", "+X +Z"};

    private void renderCoordinatesMio(RenderOverlayEvent event, float chatShift, int fontHeight) {
        Seg[] coordSegs = null;
        if (coordinates.getValue()) {
            int bx = mc.player.getBlockX(), by = mc.player.getBlockY(), bz = mc.player.getBlockZ();
            String v = bx + ", " + by + ", " + bz;
            if (netherCoordinates.getValue()) v += " (" + WorldUtils.getNetherPosition(bx) + ", " + by + ", " + WorldUtils.getNetherPosition(bz) + ")";
            coordSegs = new Seg[]{mioSeg("XYZ: "), whiteSeg(v)};
        }

        Seg[] dirSegs = null;
        if (direction.getValue()) {
            float yaw = MathHelper.wrapDegrees(mc.player.getYaw());
            int idx = Math.floorMod((int) Math.floor((yaw + 22.5f) / 45.0f), 8);
            dirSegs = new Seg[]{mioSeg(MIO_DIR_NAMES[idx]), whiteSeg(" (" + (int) yaw + ", " + MIO_DIR_AXES[idx] + ")")};
        }

        int drawn = 0, maxWidth = 0;
        if (coordSegs != null) { drawn++; maxWidth = Math.max(maxWidth, mioWidth(coordSegs)); }
        if (dirSegs != null) { drawn++; maxWidth = Math.max(maxWidth, mioWidth(dirSegs)); }
        if (drawn == 0) return;

        float defaultTop = mc.getWindow().getScaledHeight() - 2 - drawn * fontHeight;
        float left = positionElement.resolveX(2);
        float top = positionElement.resolveY(defaultTop) - chatShift;
        positionElement.updateBounds(left, top, maxWidth, drawn * fontHeight);

        float rowY = top;
        if (dirSegs != null) {
            drawMio(event.getContext(), left, rowY, dirSegs);
            rowY += fontHeight;
        }
        if (coordSegs != null) drawMio(event.getContext(), left, rowY, coordSegs);
    }

    private Formatting getPrimary() {
        return inversion.getValue() ? Formatting.GRAY : Formatting.RESET;
    }

    private Formatting getSecondary() {
        return inversion.getValue() ? Formatting.RESET : Formatting.WHITE;
    }

    public record ModuleEntry(Module module, String text) {}
    public record PlayerEntry(PlayerEntity player, String text, Identifier headTexture) {}
    public record PotionEntry(String text, Identifier sprite, Color color) {}

    private static class PlayerRadarState {
        private PlayerEntry entry;
        private int orderIndex;
        private long lastSeen;
        private boolean seenThisTick;

        private final Animation visible;
        private final Animation y;
        private float targetVisible;

        private PlayerRadarState(int duration) {
            this.visible = new Animation(duration, Easing.Method.EASE_OUT_CUBIC);
            this.y = new Animation(duration, Easing.Method.EASE_OUT_CUBIC);
            this.targetVisible = 0.0f;
        }

        private void setTargetVisible(float targetVisible) {
            if (this.targetVisible == targetVisible) return;
            this.targetVisible = targetVisible;
            this.visible.setEasing(targetVisible > 0.0f ? Easing.Method.EASE_OUT_CUBIC : Easing.Method.EASE_IN_CUBIC);
        }
    }

    private static class ItemDecreasePopup {
        private final int amount;
        private final Animation progress;

        private ItemDecreasePopup(int amount, int duration) {
            this.amount = amount;
            this.progress = new Animation(duration, Easing.Method.EASE_OUT_CUBIC);
        }
    }

    private void renderItemDecreasePopup(DrawContext context, ItemDecreasePopup popup, float x, float y) {
        if (!itemCounterAnimations.getValue().equalsIgnoreCase("Popup")) return;
        if (popup == null) return;

        float t = popup.progress.get(1.0f);
        if (t >= 0.99f) return;

        float drop = itemCounterDropDistance.getValue().floatValue();
        float yOffset = t * drop;
        int alpha = Math.min(255, Math.max(0, (int) ((1.0f - t) * 255.0f)));

        drawText(context, "-" + popup.amount, x, y + yOffset, false, withAlpha(new Color(255, 85, 85), alpha));
    }

    private Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(255, Math.max(0, alpha)));
    }

    private static class SlotMachineCounter {
        private int currentValue = 0;
        private int previousValue = 0;
        private Animation slideAnimation;
        private boolean animating = false;
        private final int duration;

        public SlotMachineCounter() {
            this.duration = 250;
            this.slideAnimation = new Animation(duration, Easing.Method.EASE_OUT_CUBIC);
        }

        public void update(int newValue) {
            if (newValue != currentValue) {
                previousValue = currentValue;
                currentValue = newValue;
                animating = true;
                slideAnimation = new Animation(duration, Easing.Method.EASE_OUT_CUBIC);
            }
        }

        public boolean isAnimating() {
            if (animating) {
                float progress = slideAnimation.get(1.0f);
                if (progress >= 0.99f) {
                    animating = false;
                }
            }
            return animating;
        }

        public void render(DrawContext context, int x, int y, TextRenderer textRenderer, ItemStack stack) {
            float progress = slideAnimation.get(1.0f);
            int fontHeight = textRenderer.fontHeight;

            float slideDistance = fontHeight * 0.8f;

            float oldY = y + (progress * slideDistance);
            float newY = y - slideDistance + (progress * slideDistance);

            int oldAlpha = (int) ((1.0f - progress) * 255);
            int newAlpha = (int) (progress * 255);

            org.joml.Matrix3x2fStack matrices = context.getMatrices();

            if (oldAlpha > 10 && previousValue != currentValue) {
                matrices.pushMatrix();
                matrices.translate(0, oldY - y);
                context.drawStackOverlay(textRenderer, stack, x, y, String.valueOf(previousValue));
                matrices.popMatrix();
            }

            if (newAlpha > 10) {
                matrices.pushMatrix();
                matrices.translate(0, newY - y);
                context.drawStackOverlay(textRenderer, stack, x, y, String.valueOf(currentValue));
                matrices.popMatrix();
            }
        }
    }
}
