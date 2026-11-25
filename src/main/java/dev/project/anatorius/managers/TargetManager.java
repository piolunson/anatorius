package dev.project.anatorius.managers;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.ClientConnectEvent;
import dev.project.anatorius.events.impl.PlayerDeathEvent;
import dev.project.anatorius.events.impl.TargetDeathEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.impl.combat.KillAuraModule;
import dev.project.anatorius.modules.impl.combat.AutoCrystalModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;

public class TargetManager implements IMinecraft {
    private final ArrayList<Target> targets = new ArrayList<>();

    public TargetManager() {
        Anatorius.EVENT_HANDLER.subscribe(this);
    }

    @SubscribeEvent
    public void onClientConnect(ClientConnectEvent event) {
        targets.clear();
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if(mc.player == null || mc.world == null) return;

        PlayerEntity caTarget = Anatorius.MODULE_MANAGER.getModule(AutoCrystalModule.class).getTarget();
        Entity kaTarget = Anatorius.MODULE_MANAGER.getModule(KillAuraModule.class).target;

        synchronized (targets) {
            targets.removeIf(t -> System.currentTimeMillis() - t.time > 15000);

            if (caTarget != null) refresh(caTarget);
            if (kaTarget instanceof PlayerEntity kp) refresh(kp);
        }
    }

    private void refresh(PlayerEntity player) {
        targets.removeIf(t -> t.player == player);
        targets.add(new Target(player));
    }

    @SubscribeEvent
    public void onPlayerDeath(PlayerDeathEvent event) {
        if(mc.player == null || mc.world == null || !isTarget(event.getPlayer())) return;

        synchronized (targets) {
            Anatorius.EVENT_HANDLER.post(new TargetDeathEvent(event.getPlayer()));
            targets.remove(getTarget(event.getPlayer()));
        }
    }

    private Target getTarget(PlayerEntity player) {
        for(Target target : targets) {
            if(target.player == player) return target;
        }
        return null;
    }

    public boolean isTarget(PlayerEntity player) {
        for(Target target : targets) {
            if(target.player == player) return true;
        }
        return false;
    }

    private class Target {
        private final PlayerEntity player;
        private final long time;

        public Target(PlayerEntity player) {
            this.player = player;
            this.time = System.currentTimeMillis();
        }
    }
}
