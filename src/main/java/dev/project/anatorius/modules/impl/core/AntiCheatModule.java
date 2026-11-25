package dev.project.anatorius.modules.impl.core;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.utils.network.PacketLimiter;
import dev.project.anatorius.events.impl.SettingChangeEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.Setting;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.utils.minecraft.NetworkUtils;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;

@RegisterModule(name = "AntiCheat", description = "Compatibility settings for different server anti-cheat metas.", category = Module.Category.CORE, persistent = true, drawn = false)
public class AntiCheatModule extends Module {
    public ModeSetting preset = new ModeSetting("Preset", "Compatibility preset.", "Default", new String[]{"Default", "2b2t"});

    public BooleanSetting noServerSlot = new BooleanSetting("NoServerSlot", "Avoids packet-only hotbar switching where possible.", false);
    public BooleanSetting movementSync = new BooleanSetting("MovementSync", "Keeps movement consistent while rotating.", false);
    public ModeSetting rotations = new ModeSetting("Rotations", "How rotations should be applied when MovementSync is enabled.", new BooleanSetting.Visibility(movementSync, true), "Silent", new String[]{"Silent", "Motion"});

    public ModeSetting movementPackets = new ModeSetting(
            "MovementPackets",
            "How movement packets should be sent while rotating (Grim may require look to be included consistently).",
            new BooleanSetting.Visibility(movementSync, true),
            "Vanilla",
            new String[]{"Vanilla", "Grim"}
    );

    public BooleanSetting grimJitter = new BooleanSetting(
            "GrimJitter",
            "Adds tiny pitch jitter to match Grim rotation rounding behavior.",
            new BooleanSetting.Visibility(movementSync, true),
            false
    );

    public ModeSetting packetRotate = new ModeSetting(
            "PacketRotate",
            "Tick: a module's look rides the tick's own movement packet and its place/hit packets follow it (Zio, one movement packet per tick). Instant: an extra movement packet for every look, sent right before the action (the old way; Zio only allows it on 1.17-1.20.6).",
            "Tick",
            new String[]{"Tick", "Instant"}
    );

    public BooleanSetting strictDirection =new BooleanSetting("StrictDirection", "Prefers strict placement/mining directions.", false);

    public ModeSetting placements = new ModeSetting("Placements", "Placement interaction style.", "Vanilla", new String[]{"Vanilla", "Paper"});

    public BooleanSetting grimAirPlace = new BooleanSetting("GrimAirPlace", "Air places (a block with nothing to click against, e.g. PistonCrystal's piston/redstone) go out from the off hand: the block is swapped there (F), placed and swapped back, which Grim accepts (Hachimi's GrimAirplace).", true);

    public BooleanSetting limiter = new BooleanSetting("Limiter", "Holds back the interaction packets over the server's per-second cap (2b2t kicks for more), so a burst of placing, hitting or mining never gets you kicked.", false);

    private final PacketLimiter packetLimiter = new PacketLimiter();

    public ModeSetting mining = new ModeSetting("Mining", "How every dig goes out (Zio's mining modes). Vanilla: as is. Grim: a decoy ABORT 500-549 blocks up right before every STOP. "
            + "GrimStrict: a STOP right before every START (reconnect after switching to it). NCP: a clean, fully timed dig, one block at a time, no double mining.",
            "Vanilla", new String[]{"Vanilla", "Grim", "GrimStrict", "NCP"});

    public static boolean ncpMining() {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        return antiCheat != null && antiCheat.mining.getValue().equalsIgnoreCase("NCP");
    }

    public static boolean grimMining() {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        return antiCheat != null && antiCheat.mining.getValue().startsWith("Grim");
    }

    public static boolean grimStrictMining() {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        return antiCheat != null && antiCheat.mining.getValue().equalsIgnoreCase("GrimStrict");
    }

    public static class GrimMiningVisibility extends Setting.Visibility {
        @Override
        public void update() {
            dev.project.anatorius.modules.impl.player.SpeedMineModule speedMine = Anatorius.MODULE_MANAGER == null ? null
                    : Anatorius.MODULE_MANAGER.getModule(dev.project.anatorius.modules.impl.player.SpeedMineModule.class);
            setVisible(!ncpMining() || (speedMine != null && speedMine.doubleMine.getValue()));
        }
    }

    @Override
    public void onEnable() {
        applyPreset();
        syncMovementFix();
    }

    @SubscribeEvent
    public void onSettingChange(SettingChangeEvent event) {
        if (event.getSetting() == preset) {
            applyPreset();
        }
        if (event.getSetting() == movementSync) {
            syncMovementFix();
        }
    }

    @SubscribeEvent(priority = Integer.MIN_VALUE)
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled() || mc.player == null) return;
        if (limiter.getValue() && packetLimiter.shouldCancel(event.getPacket())) {
            event.setCancelled(true);
            return;
        }
        if (event.getPacket() instanceof PlayerActionC2SPacket packet) applyMiningMode(packet);
    }

    private void applyMiningMode(PlayerActionC2SPacket packet) {
        if (Vec3d.ofCenter(packet.getPos()).squaredDistanceTo(mc.player.getEyePos()) > 100.0) return;
        String mode = mining.getValue();
        if (mode.equalsIgnoreCase("Grim") && packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
            BlockPos decoy = packet.getPos().up(500 + ThreadLocalRandom.current().nextInt(50));
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, decoy, packet.getDirection()));
        } else if (mode.equalsIgnoreCase("GrimStrict") && packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
            NetworkUtils.sendSequencedPacket(sequence -> new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, packet.getPos(), packet.getDirection(), sequence));
        }
    }

    public int recentPackets() {
        return packetLimiter.recent();
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!noServerSlot.getValue()) return;
        if (mc.player == null) return;

        if (event.getPacket() instanceof UpdateSelectedSlotS2CPacket packet) {
            event.setCancelled(true);
            int ours = Anatorius.POSITION_MANAGER.getServerSlot();
            if (packet.slot() != ours) mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(ours));
        }
    }

    private void applyPreset() {
        if (preset.getValue().equalsIgnoreCase("2b2t")) {
            noServerSlot.setValue(true);
            strictDirection.setValue(true);
            movementSync.setValue(true);
            placements.setValue("Vanilla");
            grimAirPlace.setValue(true);
            movementPackets.setValue("Grim");
            grimJitter.setValue(true);
            limiter.setValue(true);
            packetRotate.setValue("Tick");
            return;
        }

        if (preset.getValue().equalsIgnoreCase("Default")) {
            noServerSlot.setValue(false);
            strictDirection.setValue(false);
            placements.setValue("Vanilla");
            movementPackets.setValue("Vanilla");
            grimJitter.setValue(false);
            limiter.setValue(false);
        }
    }

    private void syncMovementFix() {
        RotationsModule rotationsModule = Anatorius.MODULE_MANAGER.getModule(RotationsModule.class);
        if (rotationsModule == null) return;
        if (rotationsModule.movementFix.getValue() != movementSync.getValue()) {
            rotationsModule.movementFix.setValue(movementSync.getValue());
        }
    }
}
