package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.mixins.accessors.EntityVelocityUpdateS2CPacketAccessor;
import dev.project.anatorius.mixins.accessors.Vec3dAccessor;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

@RegisterModule(name = "Velocity", description = "Modifies the amount of knockback that you receive.", category = Module.Category.MOVEMENT)
public class VelocityModule extends Module {
    public ModeSetting mode = new ModeSetting("Mode", "The method that will be used to achieve the knockback modification.", "Normal", new String[]{"Normal", "Cancel", "Grim", "Walls"});
    public NumberSetting horizontal = new NumberSetting("Horizontal", "The amount of horizontal knockback that you will receive.", new ModeSetting.Visibility(mode, "Normal"), 0, 0, 100);
    public NumberSetting vertical = new NumberSetting("Vertical", "The amount of vertical knockback that you will receive.", new ModeSetting.Visibility(mode, "Normal"), 0, 0, 100);
    public BooleanSetting explosions = new BooleanSetting("Explosions", "Modifies knockback received from explosions.", true);
    public BooleanSetting pause = new BooleanSetting("Pause", "Pauses the velocity for a certain duration whenever you get rubberbanded.", new ModeSetting.Visibility(mode, "Cancel", "Grim", "Walls"), true);
    public BooleanSetting groundOnly = new BooleanSetting("GroundOnly", "Only cancels knockback while grounded (Walls mode).", new ModeSetting.Visibility(mode, "Walls"), false);

    public CategorySetting antiPushCategory = new CategorySetting("AntiPush", "Prevents certain things from pushing you.");
    public BooleanSetting antiPush = new BooleanSetting("AntiPush", "Entities", "Prevents other entities from pushing you.", new CategorySetting.Visibility(antiPushCategory), true);
    public BooleanSetting antiLiquidPush = new BooleanSetting("AntiLiquidPush", "Liquids", "Prevents liquids from pushing you.", new CategorySetting.Visibility(antiPushCategory), false);
    public BooleanSetting antiBlockPush = new BooleanSetting("AntiBlockPush", "Blocks", "Prevents you from being pushed outside of blocks.", new CategorySetting.Visibility(antiPushCategory), true);
    public BooleanSetting antiFishingRod = new BooleanSetting("AntiFishingRod", "FishingRods", "Prevents fishing rods from pushing you.", new CategorySetting.Visibility(antiPushCategory), false);

    private boolean cancel;

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (mc.player == null) return;
        if (!cancel) return;

        if (mode.getValue().equalsIgnoreCase("Grim") && (!pause.getValue() || Anatorius.SERVER_MANAGER.getSetbackTimer().hasTimeElapsed(100L))) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), Anatorius.ROTATION_MANAGER.getServerYaw(), Anatorius.ROTATION_MANAGER.getServerPitch(), mc.player.isOnGround(), mc.player.horizontalCollision));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, mc.player.isCrawling() ? mc.player.getBlockPos() : mc.player.getBlockPos().up(), Direction.DOWN));
        }

        cancel = false;
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (mc.player == null) return;

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
            if (packet.getEntityId() != mc.player.getId()) return;

            switch (mode.getValue()) {
                case "Normal" -> {
                    net.minecraft.util.math.Vec3d v = packet.getVelocity();
                    dev.project.anatorius.modules.impl.core.AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER.getModule(dev.project.anatorius.modules.impl.core.AntiCheatModule.class);
                    if (antiCheat != null && antiCheat.movementSync.getValue() && v.x == 0.0 && v.z == 0.0 && Math.abs(v.y) < 0.0788) return;
                    net.minecraft.util.math.Vec3d pv = mc.player.getVelocity();
                    double nx = (v.x - pv.x) * (horizontal.getValue().doubleValue() / 100.0) + pv.x;
                    double ny = (v.y - pv.y) * (vertical.getValue().doubleValue() / 100.0) + pv.y;
                    double nz = (v.z - pv.z) * (horizontal.getValue().doubleValue() / 100.0) + pv.z;
                    ((EntityVelocityUpdateS2CPacketAccessor) packet).setVelocity(new net.minecraft.util.math.Vec3d(nx, ny, nz));
                }
                case "Cancel" -> {
                    if (pause.getValue() && !Anatorius.SERVER_MANAGER.getSetbackTimer().hasTimeElapsed(100L)) return;

                    event.setCancelled(true);
                }
                case "Grim" -> {
                    if (pause.getValue() && !Anatorius.SERVER_MANAGER.getSetbackTimer().hasTimeElapsed(100L)) return;

                    event.setCancelled(true);
                    cancel = true;
                }
                case "Walls" -> {
                    if (pause.getValue() && !Anatorius.SERVER_MANAGER.getSetbackTimer().hasTimeElapsed(100L)) return;
                    if (shouldCancelWalls()) event.setCancelled(true);
                }
            }
        }

        if (event.getPacket() instanceof ExplosionS2CPacket packet && explosions.getValue()) {
            switch (mode.getValue()) {
                case "Normal" -> {
                    if (packet.playerKnockback().isPresent()) ((Vec3dAccessor) packet.playerKnockback().get()).setX((float) (packet.playerKnockback().get().getX() * (horizontal.getValue().doubleValue() / 100.0)));
                    if (packet.playerKnockback().isPresent()) ((Vec3dAccessor) packet.playerKnockback().get()).setY((float) (packet.playerKnockback().get().getY() * (vertical.getValue().doubleValue() / 100.0)));
                    if (packet.playerKnockback().isPresent()) ((Vec3dAccessor) packet.playerKnockback().get()).setZ((float) (packet.playerKnockback().get().getZ() * (horizontal.getValue().doubleValue() / 100.0)));
                }
                case "Cancel" -> {
                    if (pause.getValue() && !Anatorius.SERVER_MANAGER.getSetbackTimer().hasTimeElapsed(100L)) return;

                    event.setCancelled(true);
                }
                case "Grim" -> {
                    if (pause.getValue() && !Anatorius.SERVER_MANAGER.getSetbackTimer().hasTimeElapsed(100L)) return;

                    event.setCancelled(true);
                    cancel = true;
                }
                case "Walls" -> {
                    if (pause.getValue() && !Anatorius.SERVER_MANAGER.getSetbackTimer().hasTimeElapsed(100L)) return;
                    if (shouldCancelWalls()) event.setCancelled(true);
                }
            }

            if (event.isCancelled()) {
                mc.executeSync(() -> {
                    Vec3d vec3d = packet.center();
                    mc.world.playSoundClient(vec3d.getX(), vec3d.getY(), vec3d.getZ(), packet.explosionSound().value(), SoundCategory.BLOCKS, 4.0F, (1.0F + (mc.world.random.nextFloat() - mc.world.random.nextFloat()) * 0.2F) * 0.7F, false);
                    mc.world.addParticleClient(packet.explosionParticle(), vec3d.getX(), vec3d.getY(), vec3d.getZ(), 1.0, 0.0, 0.0);
                });
            }
        }
    }

    private boolean shouldCancelWalls() {
        return isInsideBlock() && (!groundOnly.getValue() || mc.player.isOnGround());
    }

    private boolean isInsideBlock() {
        return mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.0625, -0.0625, -0.0625)).iterator().hasNext();
    }

    @Override
    public String getMetaData() {
        if (mode.getValue().equalsIgnoreCase("Cancel")) return "0%, 0%";
        if (mode.getValue().equalsIgnoreCase("Grim")) return "Grim";
        if (mode.getValue().equalsIgnoreCase("Walls")) return "Walls";
        return horizontal.getValue().intValue() + "%, " + vertical.getValue().intValue() + "%";
    }
}
