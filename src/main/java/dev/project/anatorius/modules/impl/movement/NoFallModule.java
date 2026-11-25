package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.mixins.accessors.PlayerMoveC2SPacketAccessor;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Box;

@RegisterModule(name = "NoFall", description = "Prevents you from taking fall damage.", category = Module.Category.MOVEMENT)
public class NoFallModule extends Module {
    public ModeSetting mode = new ModeSetting("Mode", "The method that will be used in preventing fall damage.", "Packet", new String[]{"Packet", "Grim"});

    public NumberSetting groundDistance = new NumberSetting("GroundDistance",
            "Grim: blocks above ground before spoofing onGround (smaller = more legit, riskier timing).",
            new ModeSetting.Visibility(mode, "Grim"), 1.0, 0.1, 4.0);

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!isFalling()) return;
        if (!mode.getValue().equalsIgnoreCase("Grim")) return;
        if (isAutoMaceActive() || killAuraMace()) return;
        if (fakeFlying()) return;

        if (!isAboutToLand(groundDistance.getValue().doubleValue())) return;

        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, mc.player.horizontalCollision));
        mc.player.onLanding();
    }

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!mode.getValue().equalsIgnoreCase("Packet")) return;
        if (isAutoMaceActive() || killAuraMace()) return;
        if (fakeFlying()) return;

        if (event.getPacket() instanceof PlayerMoveC2SPacket packet && isFalling()) {
            ((PlayerMoveC2SPacketAccessor) packet).setOnGround(true);
        }
    }

    private static boolean fakeFlying() {
        dev.project.anatorius.modules.impl.movement.FakeFlyModule fakeFly =
            dev.project.anatorius.Anatorius.MODULE_MANAGER.getModule(dev.project.anatorius.modules.impl.movement.FakeFlyModule.class);
        return fakeFly != null && fakeFly.isToggled();
    }

    private static boolean killAuraMace() {
        dev.project.anatorius.modules.impl.combat.KillAuraModule killAura =
            dev.project.anatorius.Anatorius.MODULE_MANAGER.getModule(dev.project.anatorius.modules.impl.combat.KillAuraModule.class);
        return killAura != null && killAura.maceComing();
    }

    private boolean isAutoMaceActive() {
        dev.project.anatorius.modules.impl.combat.AutoMaceModule autoMace =
            dev.project.anatorius.Anatorius.MODULE_MANAGER.getModule(dev.project.anatorius.modules.impl.combat.AutoMaceModule.class);
        return autoMace != null && autoMace.isToggled() && autoMace.hasTarget();
    }

    @Override
    public String getMetaData() {
        return mode.getValue();
    }

    private boolean isFalling() {
        return mc.player.fallDistance > mc.player.getSafeFallDistance() && !mc.player.isOnGround() && !mc.player.isGliding();
    }

    private boolean isAboutToLand(double distance) {
        Box fall = mc.player.getBoundingBox().stretch(0.0, -distance, 0.0);
        return mc.world.getBlockCollisions(mc.player, fall).iterator().hasNext();
    }
}
