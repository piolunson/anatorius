package dev.project.anatorius.modules.impl.core;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.ClientConnectEvent;
import dev.project.anatorius.events.impl.PlayerDeathEvent;
import dev.project.anatorius.events.impl.PlayerPopEvent;
import dev.project.anatorius.events.impl.RenderOverlayEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.hud.HudEditor;
import dev.project.anatorius.hud.HudElement;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.Setting;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.animations.Easing;
import dev.project.anatorius.utils.chat.ChatUtils;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer2D;
import dev.project.anatorius.utils.minecraft.EntityUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.Formatting;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RegisterModule(name = "Notifications", description = "Notifies you when modules get toggled and when something significant happens.", category = Module.Category.CORE, persistent = true, drawn = false)
public class NotificationsModule extends Module {
    public BooleanSetting chatNotify = new BooleanSetting("ChatNotify", "Notifies you whenever a module gets toggled on or off.", true);
    public ModeSetting theme = new ModeSetting("Theme", "Where toggle notifications show up. Classic: in chat. Overlay: a fading list at the top of the screen, movable in the HUD editor. Both: chat and the overlay.", new BooleanSetting.Visibility(chatNotify, true), "Classic", new String[]{"Classic", "Overlay", "Both"});

    public ModeSetting overlayFormat = new ModeSetting("OverlayFormat", "Format", "How a toggle reads on the overlay. Symbols: [+] Module. Words: Module Enabled.", new ModeSetting.Visibility(theme, "Overlay", "Both"), "Symbols", new String[]{"Symbols", "Words"});
    public ModeSetting overlayAlign = new ModeSetting("OverlayAlign", "Align", "Which side the lines line up on. Auto follows where the overlay sits on the screen.", new ModeSetting.Visibility(theme, "Overlay", "Both"), "Auto", new String[]{"Auto", "Left", "Center", "Right"});
    public BooleanSetting overlaySync = new BooleanSetting("OverlaySync", "ColorSync", "Draws the module names in the client's color.", new ModeSetting.Visibility(theme, "Overlay", "Both"), false);
    public NumberSetting overlayDuration = new NumberSetting("OverlayDuration", "Duration", "How long a notification stays on screen, in milliseconds.", new ModeSetting.Visibility(theme, "Overlay", "Both"), 2000L, 500L, 10000L);
    public NumberSetting overlayAnimation = new NumberSetting("OverlayAnimation", "AnimTime", "How long a notification takes to slide in, in milliseconds (it folds away in two thirds of that).", new ModeSetting.Visibility(theme, "Overlay", "Both"), 300L, 0L, 1000L);
    public NumberSetting overlayLimit = new NumberSetting("OverlayLimit", "Limit", "The most notifications shown at once; the oldest makes room.", new ModeSetting.Visibility(theme, "Overlay", "Both"), 6L, 1L, 20L);
    public BooleanSetting overlayEvents = new BooleanSetting("OverlayEvents", "Events", "Shows the event notifications (totem pops, visual range, pearl throws) on the overlay too, the way Theme says.", new ModeSetting.Visibility(theme, "Overlay", "Both"), false);
    public BooleanSetting overlayBackground = new BooleanSetting("OverlayBackground", "Background", "Draws a rounded box behind the overlay.", new ModeSetting.Visibility(theme, "Overlay", "Both"), false);
    public ColorSetting overlayBackgroundColor = new ColorSetting("OverlayBackgroundColor", "BGColor", "The color of the box behind the overlay.", new BooleanSetting.Visibility(overlayBackground, true), new ColorSetting.Color(new Color(0, 0, 0, 100), false, false));
    public NumberSetting overlayRadius = new NumberSetting("OverlayRadius", "Radius", "How round the box's corners are.", new BooleanSetting.Visibility(overlayBackground, true), 4.0, 0.0, 10.0);

    public BooleanSetting totemPops = new BooleanSetting("TotemPops", "Notifies you whenever a player pops a totem.", true);
    public BooleanSetting visualRange = new BooleanSetting("VisualRange", "Notifies you whenever a player enters your render distance.", false);
    public BooleanSetting pearlThrows = new BooleanSetting("PearlThrows", "Notifies you whenever a player throws a pearl.", true);

    private final ArrayList<PlayerEntity> loadedPlayers = new ArrayList<>();
    private final ArrayList<Integer> thrownPearls = new ArrayList<>();

    private static final int FRAME_WIDTH = 150;
    private static final String[] PREVIEW = {Formatting.GREEN + "[+] " + Formatting.WHITE + "AutoCrystal", Formatting.RED + "[-] " + Formatting.WHITE + "Surround"};

    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final HudElement overlayElement = new HudElement("notifications", "Notifications", this::overlayActive);

    public NotificationsModule() {
        getSettings().add(overlayElement.getX());
        getSettings().add(overlayElement.getY());
    }

    @Override
    public List<Setting> getClickGuiSettings() {
        return getSettings().stream().filter(s -> s != overlayElement.getX() && s != overlayElement.getY()).toList();
    }

    public static void moduleToggled(Module module, boolean enabled) {
        if (Anatorius.MODULE_MANAGER == null) return;
        NotificationsModule notifications = Anatorius.MODULE_MANAGER.getModule(NotificationsModule.class);
        if (notifications == null || !notifications.isToggled() || !notifications.chatNotify.getValue()) return;

        String id = "toggle-" + module.getName().toLowerCase();
        if (notifications.toChat()) {
            Anatorius.CHAT_MANAGER.addMessage(ChatUtils.getPrimary().toString() + "§l" + module.getName() + ChatUtils.getSecondary() + " " + (enabled ? Formatting.GREEN + "Enabled" : Formatting.RED + "Disabled") + ChatUtils.getSecondary(), id);
        }
        if (notifications.overlayActive()) {
            notifications.push(notifications.overlayToggleText(module.getName(), enabled), id);
        }
    }

    private String overlayToggleText(String name, boolean enabled) {
        String nameColor = Formatting.RESET.toString();
        if (overlayFormat.getValue().equalsIgnoreCase("Words")) {
            return nameColor + name + " " + (enabled ? Formatting.GREEN + "Enabled" : Formatting.RED + "Disabled");
        }
        return (enabled ? Formatting.DARK_GREEN + "[+] " : Formatting.DARK_RED + "[-] ") + nameColor + name;
    }

    private boolean toChat() {
        return !theme.getValue().equalsIgnoreCase("Overlay");
    }

    private boolean overlayActive() {
        return isToggled() && chatNotify.getValue() && !theme.getValue().equalsIgnoreCase("Classic");
    }

    private void event(String message, String id) {
        boolean overlay = overlayActive() && overlayEvents.getValue();
        if (!overlay || toChat()) Anatorius.CHAT_MANAGER.message(message, id);
        if (overlay) push(Formatting.WHITE + message, id);
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (getNull()) return;

        if (visualRange.getValue()) {
            for (Entity entity : mc.world.getEntities()) {
                if (!(entity instanceof PlayerEntity player) || entity == mc.player) continue;

                if (!loadedPlayers.contains(player)) {
                    loadedPlayers.add(player);
                    event(ChatUtils.getPrimary() + player.getName().getString() + ChatUtils.getSecondary() + " has entered your visual range.", "visual-range-" + player.getName().getString());
                }
            }

            if (!loadedPlayers.isEmpty()) {
                for (PlayerEntity player : new ArrayList<>(loadedPlayers)) {
                    if (!mc.world.getPlayers().contains(player)) {
                        loadedPlayers.remove(player);
                        event(ChatUtils.getPrimary() + player.getName().getString() + ChatUtils.getSecondary() + " has left your visual range.", "visual-range-" + player.getName().getString());
                    }
                }
            }
        }

        if (pearlThrows.getValue()) {
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof EnderPearlEntity pearl)) continue;
                if (pearl.getOwner() == null || thrownPearls.contains(pearl.getId())) continue;

                String name = pearl.getOwner().getName().getString();
                event(ChatUtils.getPrimary() + name + ChatUtils.getSecondary() + " threw a pearl towards " + EntityUtils.getPearlDirection(pearl).toString() + ".", "pearl-throws-" + name);
                thrownPearls.add(pearl.getId());
            }

            thrownPearls.removeIf(id -> !(mc.world.getEntityById(id) instanceof EnderPearlEntity));
        }
    }

    @SubscribeEvent
    public void onClientConnect(ClientConnectEvent event) {
        loadedPlayers.clear();
        entries.clear();
    }

    @SubscribeEvent
    public void onPlayerPop(PlayerPopEvent event) {
        if (totemPops.getValue()) {
            event(ChatUtils.getPrimary() + event.getPlayer().getName().getString() + ChatUtils.getSecondary() + " has popped " + ChatUtils.getPrimary() + event.getPops() + ChatUtils.getSecondary() + " totem" + (event.getPops() > 1 ? "s" : "") + ".", "totem-pop-" + event.getPlayer().getName().getString());
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(PlayerDeathEvent event) {
        int pops = Anatorius.WORLD_MANAGER.getPoppedTotems().getOrDefault(event.getPlayer().getUuid(), 0);
        if (totemPops.getValue() && pops > 0) {
            event(ChatUtils.getPrimary() + event.getPlayer().getName().getString() + ChatUtils.getSecondary() + " has died after popping " + ChatUtils.getPrimary() + pops + ChatUtils.getSecondary() + " totem" + (pops > 1 ? "s" : "") + ".", "totem-pop-" + event.getPlayer().getName().getString());
        }
    }

    private void push(String text, String id) {
        long now = System.currentTimeMillis();
        for (Entry entry : entries) {
            if (id != null && id.equals(entry.id)) entry.leave(now);
        }
        entries.add(new Entry(text, id, now));

        int limit = overlayLimit.getValue().intValue();
        List<Entry> staying = entries.stream().filter(e -> !e.leaving).toList();
        for (int i = 0; i < staying.size() - limit; i++) staying.get(i).leave(now);
    }

    @SubscribeEvent
    public void renderOverlay(RenderOverlayEvent event) {
        HUDModule hud = Anatorius.MODULE_MANAGER.getModule(HUDModule.class);
        HudEditor editor = hud == null ? null : hud.getEditor();
        if (editor != null && editor.get(overlayElement.getId()) == null) editor.register(overlayElement);
        if (mc.player == null) return;

        boolean editing = editor != null && editor.isEditing();
        if (!overlayActive()) {
            entries.clear();
            return;
        }

        DrawContext context = event.getContext();
        long now = System.currentTimeMillis();
        long animIn = overlayAnimation.getValue().longValue();
        long animOut = animIn * 2 / 3;
        long duration = overlayDuration.getValue().longValue();

        List<Row> rows = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.leaving && now - entry.start >= animIn + duration) entry.leave(now);

            float progress;
            if (entry.leaving) {
                progress = animOut <= 0 ? 0.0f : 1.0f - Easing.ease(Math.min(1.0f, (now - entry.leaveStart) / (float) animOut), Easing.Method.EASE_IN_OUT_CUBIC);
            } else {
                progress = animIn <= 0 ? 1.0f : Easing.ease(Math.min(1.0f, (now - entry.start) / (float) animIn), Easing.Method.EASE_IN_OUT_CUBIC);
            }

            if (entry.leaving && progress <= 0.0f) {
                entries.remove(entry);
                continue;
            }
            rows.add(new Row(entry.text, progress));
        }

        if (rows.isEmpty() && editing) {
            for (String text : PREVIEW) rows.add(new Row(text, 1.0f));
        }

        int fontHeight = Anatorius.FONT_MANAGER.getHeight();
        int rowHeight = fontHeight + 2;

        int maxWidth = 0;
        float totalHeight = 0;
        for (Row row : rows) {
            if (row.progress < 0.03f) continue;
            maxWidth = Math.max(maxWidth, Anatorius.FONT_MANAGER.getWidth(row.text));
            totalHeight += row.progress * rowHeight;
        }

        int screenWidth = mc.getWindow().getScaledWidth();
        int frameWidth = Math.max(FRAME_WIDTH, maxWidth);
        float left = overlayElement.resolveX(screenWidth / 2.0f - frameWidth / 2.0f);
        float top = overlayElement.resolveY(defaultTop(hud, fontHeight));
        overlayElement.updateBounds(left, top, frameWidth, Math.max(rowHeight, (int) Math.ceil(totalHeight)));

        if (rows.isEmpty()) return;

        String align = overlayAlign.getValue();
        if (align.equalsIgnoreCase("Auto")) {
            float center = left + frameWidth / 2.0f;
            align = center < screenWidth / 3.0f ? "Left" : center > screenWidth * 2 / 3.0f ? "Right" : "Center";
        }

        if (overlayBackground.getValue() && totalHeight > 0.5f) {
            float boxLeft = alignX(align, left, frameWidth, maxWidth) - 3;
            float fill = Math.min(1.0f, totalHeight / rowHeight);
            Color bg = overlayBackgroundColor.getColor();
            Color faded = new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), Math.round(bg.getAlpha() * fill));
            Renderer2D.renderRoundedQuad(context, boxLeft, top - 2, boxLeft + maxWidth + 6, top + totalHeight + 1, overlayRadius.getValue().floatValue(), faded);
        }

        Color base = overlaySync.getValue() ? ColorUtils.getGlobalColor() : Color.WHITE;
        org.joml.Matrix3x2fStack matrices = context.getMatrices();
        float y = top;
        for (Row row : rows) {
            if (row.progress < 0.03f) continue;
            int alpha = Math.round(255 * row.progress);
            if (alpha >= 8) {
                float x = alignX(align, left, frameWidth, Anatorius.FONT_MANAGER.getWidth(row.text));
                float slide = (1.0f - row.progress) * -4.0f;
                matrices.pushMatrix();
                matrices.translate(x, y + slide);
                Anatorius.FONT_MANAGER.drawTextWithShadow(context, row.text, 0, 0, new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
                matrices.popMatrix();
            }
            y += row.progress * rowHeight;
        }
    }

    private static float alignX(String align, float left, int frameWidth, int width) {
        if (align.equalsIgnoreCase("Left")) return left;
        if (align.equalsIgnoreCase("Right")) return left + frameWidth - width;
        return left + frameWidth / 2.0f - width / 2.0f;
    }

    private static float defaultTop(HUDModule hud, int fontHeight) {
        float top = 10;
        if (hud == null || !hud.isToggled()) return top;
        int lines = 0;
        if (hud.welcomer.getValue() && !hud.getEditor().get("welcomer").isCustomized()) lines = 1;
        if (hud.spotify.getValue() && !hud.getEditor().get("spotify").isCustomized() && dev.project.anatorius.utils.system.SpotifyTracker.getSong() != null) lines = 2;
        return lines == 0 ? top : 2 + fontHeight * lines + 6;
    }

    private record Row(String text, float progress) {}

    private static class Entry {
        private final String text;
        private final String id;
        private final long start;
        private boolean leaving;
        private long leaveStart;

        private Entry(String text, String id, long start) {
            this.text = text;
            this.id = id;
            this.start = start;
        }

        private void leave(long now) {
            if (leaving) return;
            leaving = true;
            leaveStart = now;
        }
    }
}
