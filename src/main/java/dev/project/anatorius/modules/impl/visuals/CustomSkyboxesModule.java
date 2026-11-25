package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.customsky.api.CustomSkyboxesAPI;
import dev.project.anatorius.customsky.client.sky.SkyboxType;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.ModeSetting;
import net.minecraft.util.Identifier;

@RegisterModule(name = "CustomSkyboxes", description = "Replaces the sky. Pick one of ours with Sky, or Custom to set sky, sun, moon, stars, clouds and weather yourself with /customskyboxes.", category = Module.Category.VISUALS)
public class CustomSkyboxesModule extends Module {
    public final ModeSetting sky = new ModeSetting("Sky", "Our skies, each with its own sun and moon. Custom: whatever you set with /customskyboxes.", "Nebula",
            new String[]{"Nebula", "Crimson", "Vortex", "Aurora", "Borealis", "DeepSpace", "Sunset", "Violet", "Blood", "Synthwave", "BGXLC", "Custom"});

    private record Preset(String sky, String sun, String moon, boolean stars) { }

    private static Preset preset(String name) {
        return switch (name) {
            case "Nebula" -> new Preset("nebula", null, "moon", false);
            case "Crimson" -> new Preset("crimson", null, "moon", false);
            case "Vortex" -> new Preset("vortex", null, null, false);
            case "Aurora" -> new Preset("aurora", null, "moon", false);
            case "Borealis" -> new Preset("borealis", null, "moon", false);
            case "DeepSpace" -> new Preset("deep_space", null, null, false);
            case "Sunset" -> new Preset("sunset", "sun_sunset", "moon", true);
            case "Violet" -> new Preset("violet", "sun", "moon", true);
            case "Blood" -> new Preset("blood", "sun_blood", "moon_blood", true);
            case "Synthwave" -> new Preset("synthwave", null, null, false);
            case "BGXLC" -> new Preset("bgxlc", null, null, false);
            default -> null;
        };
    }

    private String applied;

    @Override
    public void onEnable() {
        applied = null;
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        String want = sky.getValue();
        if (want.equals(applied) || mc.world == null) return;
        Preset p = preset(want);
        if (p != null) {
            check("sky", CustomSkyboxesAPI.set(SkyboxType.SKY, id("sky", p.sky()), ""));
            celestial(SkyboxType.SUN, p.sun());
            celestial(SkyboxType.MOON, p.moon());
            if (p.stars()) CustomSkyboxesAPI.clear(SkyboxType.STARS);
            else check("stars", CustomSkyboxesAPI.setColor(SkyboxType.STARS, 0xFFFFFF, "opacity=0"));
        }
        applied = want;
    }

    private static void celestial(SkyboxType type, String file) {
        if (file != null) check(type.id(), CustomSkyboxesAPI.set(type, id(type.id(), file), type == SkyboxType.MOON ? "scale=1.8" : "scale=1.3"));
        else check(type.id(), CustomSkyboxesAPI.set(type, id(type.id(), type == SkyboxType.SUN ? "sun" : "moon"), "opacity=0"));
    }

    private static void check(String what, boolean ok) {
        if (!ok) Anatorius.LOGGER.warn("CustomSkyboxes: preset {} was rejected", what);
    }

    private static Identifier id(String folder, String file) {
        return Identifier.of("customskyboxes", folder + "/" + file + ".png");
    }
}
