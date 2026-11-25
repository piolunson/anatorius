package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.*;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.*;
import dev.project.anatorius.utils.graphics.DeathEffectRenderer;
import dev.project.anatorius.utils.system.FileUtils;
import dev.project.anatorius.utils.system.MathUtils;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@RegisterModule(name = "DeathEffects", description = "Renders effects on players when they die and announces your kills.", category = Module.Category.VISUALS)
public class DeathEffectsModule extends Module {
    private static final String[] FX_MODES = {"Soul", "Shockwave", "Burst", "Pillar", "Shatter", "Vortex"};

    public ModeSetting mode = new ModeSetting("Mode", "The effect that will be rendered.", "Lightning", new String[]{"Lightning", "Soul", "Shockwave", "Burst", "Pillar", "Shatter", "Vortex", "None"});
    public ColorSetting lightningColor = new ColorSetting("LightningColor", "The color of the lightning bolt.", new ModeSetting.Visibility(mode, "Lightning"), new ColorSetting.Color(new Color(130, 202, 255, 255), false, false));
    public ColorSetting effectColor = new ColorSetting("EffectColor", "Color", "Main colour of the effect. Rainbow gives every particle its own hue.", new ModeSetting.Visibility(mode, FX_MODES), new ColorSetting.Color(new Color(130, 202, 255, 255), false, false));
    public ColorSetting secondColor = new ColorSetting("SecondColor", "Second colour, blended with the main one.", new ModeSetting.Visibility(mode, FX_MODES), new ColorSetting.Color(new Color(196, 120, 255, 255), false, false));
    public NumberSetting scale = new NumberSetting("Scale", "Size of the effect.", new ModeSetting.Visibility(mode, FX_MODES), 1.0, 0.4, 2.5);
    public NumberSetting duration = new NumberSetting("Duration", "How long the effect lasts (1 = normal speed, 2 = twice as long).", new ModeSetting.Visibility(mode, FX_MODES), 1.0, 0.5, 2.5);
    public NumberSetting density = new NumberSetting("Density", "How many particles the effect spawns.", new ModeSetting.Visibility(mode, FX_MODES), 1.0, 0.25, 2.0);
    public BooleanSetting self = new BooleanSetting("Self", "Also plays the effect when you die.", true);
    public dev.project.anatorius.settings.impl.WhitelistSetting sound = new dev.project.anatorius.settings.impl.WhitelistSetting("Sound", "Sounds", "The sound played when a player dies. None picked: silent.", dev.project.anatorius.settings.impl.WhitelistSetting.Type.SOUNDS).withDefault("cancel");

    public static final int BOLT_ID = -701;

    public CategorySetting killSayCategory = new CategorySetting("KillSayCategory", "KillSay", "Automatically sends a message when you kill someone.");
    public BooleanSetting kills = new BooleanSetting("Kills", "Sends a message when you kill someone.", new CategorySetting.Visibility(killSayCategory), false);
    public BooleanSetting pops = new BooleanSetting("Pops", "Sends a message when you pop someone.", new CategorySetting.Visibility(killSayCategory), false);
    public ModeSetting killSayMode = new ModeSetting("KillSayMode", "Messages", "Where the messages come from.", new CategorySetting.Visibility(killSayCategory), "Default", new String[]{"Default", "Custom"});
    public StringSetting killFile = new StringSetting("KillFile", "The name of the file containing the kill messages.", new CategorySetting.Visibility(killSayCategory), "killfile.txt");
    public StringSetting popFile = new StringSetting("PopFile", "The name of the file containing the pop messages.", new CategorySetting.Visibility(killSayCategory), "popfile.txt");
    public NumberSetting delay = new NumberSetting("Delay", "The delay between messages in seconds.", new CategorySetting.Visibility(killSayCategory), 5, 0, 10);
    public BooleanSetting clientside = new BooleanSetting("Clientside", "Sends the messages only on your side.", new CategorySetting.Visibility(killSayCategory), false);
    public BooleanSetting greenText = new BooleanSetting("GreenText", "Makes your message green.", new CategorySetting.Visibility(killSayCategory), false);
    public BooleanSetting killStreak = new BooleanSetting("KillStreak", "Keeps track of your kills.", new CategorySetting.Visibility(killSayCategory), false);
    public BooleanSetting suicideIgnore = new BooleanSetting("SuicideIgnore", "Doesn't reset kill streak when you suicide.", new CategorySetting.Visibility(killSayCategory), false);

    private final String[] KILL_MESSAGES = {"Sit [username], Anatorius owns me and all!", "[username] I killed you with the power of Anatorius!", "Sorry [username], get good get Anatorius!", "I just killed [username] thanks to Anatorius!"};

    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private List<String> killMessages = new ArrayList<>();
    private List<String> popMessages = new ArrayList<>();
    private final Timer timer = new Timer();
    private String lastMessage;
    private int streak;
    private boolean suicide = false;

    private final DeathEffectRenderer renderer = new DeathEffectRenderer();

    @Override
    public void onEnable() {
        queue.clear();
        timer.reset();
        streak = 0;
    }

    @Override
    public void onDisable() {
        renderer.close();
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (getNull()) return;
        renderer.tick();
        if (queue.isEmpty()) return;

        if (timer.hasTimeElapsed(delay.getValue().intValue() * 1000L)) {
            String message = queue.poll();
            if (message != null && !message.isEmpty()) {
                if (clientside.getValue()) Anatorius.CHAT_MANAGER.message(message);
                else mc.player.networkHandler.sendChatMessage((greenText.getValue() ? "> " : "") + message);

                lastMessage = message;
            }
            timer.reset();
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (getNull()) return;

        if (event.getPlayer() == mc.player) {
            if (!suicideIgnore.getValue() || !suicide && !dev.project.anatorius.modules.impl.combat.AutoCrystalModule.suiciding()) {
                streak = 0;
            }
            suicide = false;
        }

        dev.project.anatorius.utils.minecraft.ClientSounds.play(sound.getSound(), 1.0f);

        if (event.getPlayer() == mc.player && !self.getValue()) return;

        if (mode.getValue().equals("Lightning")) {
            LightningEntity entity = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);

            entity.setPosition(event.getPlayer().getEntityPos());
            entity.setId(BOLT_ID);

            mc.world.addEntity(entity);
        } else {
            DeathEffectRenderer.Type type = effectType(mode.getValue());
            if (type != null) {
                renderer.spawn(type, event.getPlayer().getEntityPos(), event.getPlayer().getHeight(), style());
            }
        }
    }

    public void preview(String effect, net.minecraft.util.math.Vec3d feet) {
        DeathEffectRenderer.Type type = effectType(effect);
        if (type != null) renderer.spawn(type, feet, 1.8f, style());
    }

    private static DeathEffectRenderer.Type effectType(String mode) {
        for (int i = 0; i < FX_MODES.length; i++) {
            if (FX_MODES[i].equals(mode)) return DeathEffectRenderer.Type.values()[i];
        }
        return null;
    }

    private DeathEffectRenderer.Style style() {
        return new DeathEffectRenderer.Style(scale.getValue().floatValue(), duration.getValue().floatValue(), density.getValue().floatValue(),
                effectColor.getColor(), secondColor.getColor(), effectColor.isRainbow());
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (getNull() || renderer.isEmpty()) return;
        renderer.render(event.getTickDelta());
    }

    @SubscribeEvent
    public void onTargetDeath(TargetDeathEvent event) {
        if (getNull() || event.getPlayer() == mc.player) return;
        streak++;

        if (kills.getValue()) {
            reloadMessages();
            queue.clear();
            queue.add(getKillMessage(event.getPlayer().getName().getString()));
        }

        if (kills.getValue() && killStreak.getValue() && streak > 1) {
            Anatorius.CHAT_MANAGER.message("You are on a " + streak + " kill streak.");
        }
    }

    @SubscribeEvent
    public void onPlayerPop(PlayerPopEvent event) {
        if (getNull() || event.getPlayer() == mc.player || !Anatorius.TARGET_MANAGER.isTarget(event.getPlayer()) || !pops.getValue()) return;

        reloadMessages();
        queue.add(getPopMessage(event.getPlayer().getName().getString(), event.getPops()));
    }

    @SubscribeEvent
    public void onCommandInput(CommandInputEvent event) {
        if (getNull()) return;

        if (event.getMessage().contains("/kill")) suicide = true;
    }

    @SubscribeEvent
    public void onChatInput(ChatInputEvent event) {
        if (getNull()) return;
        timer.reset();
    }

    private void reloadMessages() {
        if (!killSayMode.getValue().equals("Custom")) return;
        killMessages = FileUtils.readLines(new File(Anatorius.MOD_NAME + "/Client/" + killFile.getValue()));
        popMessages = FileUtils.readLines(new File(Anatorius.MOD_NAME + "/Client/" + popFile.getValue()));
    }

    private String getPopMessage(String username, int pops) {
        String message;
        if (killSayMode.getValue().equals("Custom") && !popMessages.isEmpty()) {
            do message = popMessages.get((int) MathUtils.random(popMessages.size(), 0)).replace("[username]", username);
            while (message.equals(lastMessage) && popMessages.size() > 1);
        } else {
            message = pops + (pops > 1 ? " pops " : " pop ") + username + "!";
        }

        return message;
    }

    private String getKillMessage(String username) {
        String message;
        if (killSayMode.getValue().equals("Custom") && !killMessages.isEmpty()) {
            do message = killMessages.get((int) MathUtils.random(killMessages.size(), 0)).replace("[username]", username);
            while (message.equals(lastMessage) && killMessages.size() > 1);
        } else {
            do message = KILL_MESSAGES[(int) MathUtils.random(KILL_MESSAGES.length, 0)].replace("[username]", username);
            while (message.equals(lastMessage));
        }

        return message;
    }

    @Override
    public String getMetaData() {
        return killStreak.getValue() ? streak + "" : mode.getValue();
    }
}
