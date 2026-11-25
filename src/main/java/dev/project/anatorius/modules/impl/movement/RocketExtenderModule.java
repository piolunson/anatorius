package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.ClientDisconnectEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.mixins.accessors.FireworkRocketEntityAccessor;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@RegisterModule(name = "RocketExtender", description = "Makes firework rockets last longer. Old Grim v2 only.", category = Module.Category.EXPLOIT)
public class RocketExtenderModule extends Module {
    public ModeSetting mode = new ModeSetting("Mode", "OldGrim: extends by Extend. Grim: stays under the server's firework ping limit.", "OldGrim", new String[]{"OldGrim", "Grim"});
    public NumberSetting extend = new NumberSetting("Extend", "How long the rocket is kept alive after the server removes it, in milliseconds.", 2000, 100, 10000);
    public NumberSetting grimLimit = new NumberSetting("GrimLimit", "The server's max-ping-firework-boost in milliseconds (Grim mode).", new ModeSetting.Visibility(mode, "Grim"), 1000, 200, 5000);

    private final Set<Integer> ownRockets = ConcurrentHashMap.newKeySet();
    private final Set<Integer> keptRockets = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<CommonPongC2SPacket> heldPongs = new ConcurrentLinkedQueue<>();
    private volatile boolean holding;
    private volatile long holdStart;
    private boolean releasing;
    private volatile int ping;

    @Override
    public void onDisable() {
        finish();
        ownRockets.clear();
    }

    @SubscribeEvent
    public void onDisconnect(ClientDisconnectEvent event) {
        heldPongs.clear();
        keptRockets.clear();
        ownRockets.clear();
        holding = false;
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (getNull()) return;

        ping = Anatorius.SERVER_MANAGER.getPing();
        ownRockets.clear();
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity rocket && ((FireworkRocketEntityAccessor) rocket).anatorius$getShooter() == mc.player) {
                ownRockets.add(rocket.getId());
            }
        }

        if (holding && (System.currentTimeMillis() - holdStart >= allowedMs() || !mc.player.isGliding() || keptRockets.stream().noneMatch(ownRockets::contains))) {
            finish();
        }
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPacket() instanceof EntitiesDestroyS2CPacket packet) || mc.player == null || !mc.player.isGliding() || allowedMs() <= 0) return;

        boolean kept = false;
        for (int i = packet.getEntityIds().size() - 1; i >= 0; i--) {
            int id = packet.getEntityIds().getInt(i);
            if (ownRockets.contains(id)) {
                packet.getEntityIds().removeInt(i);
                keptRockets.add(id);
                kept = true;
            }
        }
        if (kept && !holding) {
            holdStart = System.currentTimeMillis();
            holding = true;
        }
        if (packet.getEntityIds().isEmpty()) event.setCancelled(true);
    }

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent event) {
        if (holding && !releasing && event.getPacket() instanceof CommonPongC2SPacket pong) {
            heldPongs.add(pong);
            event.setCancelled(true);
        }
    }

    private void finish() {
        holding = false;
        if (mc.world != null) {
            for (int id : keptRockets) mc.world.removeEntity(id, Entity.RemovalReason.DISCARDED);
        }
        keptRockets.clear();

        if (mc.getNetworkHandler() == null) {
            heldPongs.clear();
            return;
        }
        releasing = true;
        try {
            CommonPongC2SPacket pong;
            while ((pong = heldPongs.poll()) != null) mc.getNetworkHandler().sendPacket(pong);
        } finally {
            releasing = false;
        }
    }

    private long allowedMs() {
        long ms = extend.getValue().longValue();
        if (mode.getValue().equals("Grim")) {
            ms = Math.min(ms, grimLimit.getValue().longValue() - ping - 100);
        }
        return ms;
    }

    @Override
    public String getMetaData() {
        return mode.getValue();
    }
}
