package dev.project.anatorius.modules.impl.core;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.StringSetting;
import dev.project.anatorius.utils.system.MathUtils;
import dev.project.anatorius.utils.system.Timer;
import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;

@RegisterModule(name = "RPC", description = "Enabled the discord presence for the client.", category = Module.Category.CORE)
public class RPCModule extends Module {
    private static final long APPLICATION_ID = 1416525959770673152L;
    private static final String IMAGE_KEY = "anat";

    public ModeSetting detailsMode = new ModeSetting("Details", "The mode for the discord presence details.", "Random", new String[]{"Custom", "Activity", "Random"});
    public StringSetting customDetails = new StringSetting("CustomDetails", "The custom text to use in the discord presence details.", new ModeSetting.Visibility(detailsMode, "Custom"), "margielaware.cc");

    private final String[] DETAILS = {
            "look",
            "Loading...",
            "Allah will safe you",
            "LEL",
            "Anatorius 2.1.0",
            "DesBilalz is Your Back",
            "What is your Discord Name",
            "Powered by Anatorius",
            "Not Safe",
            "No motion modules are enabled",
            "Pussy",
            "Anatorius Fucing Your Pc",
            "Mio users still loading...",
            "Uninstall Mio, install Anatorius",
            "Mio config can't save you",
            "Phobos? 2019 called, wants it back",
            "Phobos skidders in shambles",
            "Pogon users can't hit a crystal",
            "Pogon = paid crash report",
            "Mio + Phobos + Pogon still lose",
            "Future bought, still lost",
            "RusherHack refund pending",
            "Meteor is free for a reason",
            "Konas is dead, so are you",
            "ThunderHack? More like ThunderLag",
            "Your AutoCrystal is a placebo",
            "Newfag detected",
            "Spawn newfags getting crystalled",
            "Newfags crying in 2b2t chat",
            "2b2t queue > your skill",
            "Popping your totems since 2.1.9",
            "Hole camper cleaner",
            "1v1 me in the hole",
            "Skill issue (yours)",
            "Anatorius > every paid client",
            "Logged in, you logged out",
            "Mio kullanan ağlıyor",
            "Phobos'la mı geldin kardeşim?",
            "Totem bitti mi? Anatorius bitirdi"
    };
    private int lastDetail = -1;

    private final RichPresence rpc = new RichPresence();
    private final Timer detailsTimer = new Timer();
    private int ticks = 0;

    @Override
    public void onEnable() {
        DiscordIPC.start(APPLICATION_ID, null);
        rpc.setStart(Anatorius.UPTIME/1000);
        setImage();
        if(detailsMode.getValue().equals("Random")) rpc.setDetails(getDetails());
    }

    @Override
    public void onDisable() {
        DiscordIPC.stop();
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if(ticks > 0) {
            ticks--;
            return;
        }

        if(detailsMode.getValue().equals("Random")) {
            if (detailsTimer.hasTimeElapsed(300000)) {
                rpc.setDetails(getDetails());
                detailsTimer.reset();
            }
        } else if(detailsMode.getValue().equals("Activity")) {
            if(mc.isInSingleplayer()) {
                rpc.setDetails("Playing Singleplayer");
            } else if(mc.getCurrentServerEntry() != null) {
                rpc.setDetails("Playing " + mc.getCurrentServerEntry().address);
            } else {
                rpc.setDetails("Idling");
            }
        } else {
            rpc.setDetails(customDetails.getValue());
        }

        DiscordIPC.setActivity(rpc);
        ticks = 200;
    }

    private void setImage() {
        rpc.setLargeImage(IMAGE_KEY, Anatorius.MOD_NAME + " " + Anatorius.MOD_VERSION);
    }

    private String getDetails() {
        int i;
        do i = (int) MathUtils.random(DETAILS.length, 0); while (i == lastDetail || i >= DETAILS.length);
        lastDetail = i;
        return DETAILS[i];
    }
}
