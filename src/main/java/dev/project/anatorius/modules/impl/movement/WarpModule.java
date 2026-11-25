package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.EntityUtils;
import net.minecraft.util.Formatting;

@RegisterModule(name = "Warp", description = "Manipulates minecraft timer to give you a small speed boost.", category = Module.Category.MOVEMENT)
public class WarpModule extends Module {
    public NumberSetting maxTicks = new NumberSetting("MaxTicks", "The maximum amount of ticks that the boost will be charging for.", 1, 1, 40);
    public NumberSetting delay = new NumberSetting("Delay", "The delay between each tick.", 4, 1, 10);
    public NumberSetting speed = new NumberSetting("Speed", "The speed of charging each tick.", 2.0f, 1.0f, 10.0f);
    public BooleanSetting onlyGround = new BooleanSetting("OnlyGround", "Only boost while standing on the ground (not while jumping/falling).", false);

    private static final long NANOS_PER_TICK = 50_000_000L;

    int ticks = 0;
    int wait = 0;
    private boolean initialised;
    private long offSince;
    private float boost = 1.0f;

    public float getBoost() {
        return isToggled() ? boost : 1.0f;
    }

    public static void applyTimer() {
        float speed = Anatorius.MODULE_MANAGER.getModule(SpeedModule.class).getTimerWanted();
        float warp = Anatorius.MODULE_MANAGER.getModule(WarpModule.class).getBoost();
        Anatorius.WORLD_MANAGER.setTimerMultiplier(Math.max(speed, warp));
    }

    private void setBoost(float value) {
        boost = value;
        applyTimer();
    }

    @Override
    public void onEnable() {
        long now = System.nanoTime();
        if (!initialised) {
            initialised = true;
            ticks = maxTicks.getValue().intValue();
            wait = 0;
        } else {
            int delayTicks = Math.max(1, delay.getValue().intValue());
            int max = maxTicks.getValue().intValue();
            long offTicks = Math.max(0L, (now - offSince) / NANOS_PER_TICK);
            long total = wait + offTicks;
            ticks = (int) Math.min(max, ticks + total / delayTicks);
            wait = ticks >= max ? 0 : (int) (total % delayTicks);
        }
        if (getNull()) return;
        setBoost(1.0f);
    }

    @Override
    public void onDisable() {
        offSince = System.nanoTime();
        if (getNull()) return;
        setBoost(1.0f);
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (getNull()) return;

        if (mc.player.fallDistance >= 5.0f) {
            setBoost(1.0f);
            return;
        }

        if ((mc.player.sidewaysSpeed == 0.0f && mc.player.forwardSpeed == 0.0f && mc.player.fallDistance == 0.0f) || EntityUtils.getSpeed(mc.player, EntityUtils.SpeedUnit.KILOMETERS) <= 5) {
            setBoost(1.0f);
            if(wait >= delay.getValue().intValue()) {
                if(ticks < maxTicks.getValue().intValue()) {
                    ticks++;
                }
                wait = 0;
            }
            wait++;
        } else {
            if(ticks > 0) {
                boolean allowed = !onlyGround.getValue() || (mc.player.isOnGround() && !mc.options.jumpKey.isPressed());
                if (allowed) {
                    setBoost(speed.getValue().floatValue());
                    ticks--;
                } else {
                    setBoost(1.0f);
                }
            } else {
                reset();
            }
        }
    }

    public void reset() {
        ticks = 0;
        wait = 0;
        setBoost(1.0f);
    }

    @Override
    public String getMetaData() {
        return (ticks >= maxTicks.getValue().intValue() ? Formatting.GREEN + "" : "") + ticks + Formatting.RESET;
    }
}
