package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerMoveEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.minecraft.FlightInput;
import dev.project.anatorius.utils.minecraft.MovementUtils;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2d;

@RegisterModule(name = "Flight", description = "Lets you fly.", category = Module.Category.MOVEMENT)
public class FlightModule extends Module implements IMinecraft {
    private static final double KICK_DROP = -0.04;
    private static final float ABILITY_SCALE = 0.05f / 0.55f;

    public ModeSetting mode = new ModeSetting("Mode", "Vanilla: movement with momentum, you glide to a stop. Motion: instant start and stop at exactly your speed. Creative: the creative-mode flight ability (works where the server lets you fly, ignores Vertical).",
            "Vanilla", new String[]{"Vanilla", "Motion", "Creative"});

    public CategorySetting speedCategory = new CategorySetting("Speed", "The speeds you fly at.");
    public NumberSetting horizontal = new NumberSetting("Horizontal", "How fast you fly forward and sideways, in blocks per tick (20 ticks = 1 second).", new CategorySetting.Visibility(speedCategory), 0.8f, 0.1f, 5.0f);
    public NumberSetting vertical = new NumberSetting("Vertical", "How fast you fly up (jump) and down (sneak), in blocks per tick.", new ModeSetting.Visibility(mode, "Vanilla", "Motion"), 0.5f, 0.1f, 5.0f);
    public NumberSetting glide = new NumberSetting("Glide", "How fast you sink in blocks per tick while no up or down key is held. 0 holds you in place.", new ModeSetting.Visibility(mode, "Vanilla", "Motion"), 0.0f, 0.0f, 0.5f);

    public CategorySetting safetyCategory = new CategorySetting("Safety", "Settings that keep you from being kicked or hurt.");
    public BooleanSetting antiKick = new BooleanSetting("AntiKick", "Drops you a few hundredths of a block every KickInterval ticks, so the server's floating check (kick after 80 ticks) never fills up.", new CategorySetting.Visibility(safetyCategory), true);
    public NumberSetting kickInterval = new NumberSetting("KickInterval", "Interval", "Ticks between two AntiKick drops. Keep it well under 80.", new BooleanSetting.Visibility(antiKick, true), 30, 5, 70);
    public BooleanSetting stopOnDisable = new BooleanSetting("StopOnDisable", "Stops all your movement when Flight is turned off. Off: you keep your last speed.", new CategorySetting.Visibility(safetyCategory), true);

    private Vec3d last = Vec3d.ZERO;
    private int floating;
    private boolean savedAllowFlying;
    private float savedFlySpeed;

    @Override
    public void onEnable() {
        last = Vec3d.ZERO;
        floating = 0;
        if (mc.player == null) return;
        PlayerAbilities abilities = mc.player.getAbilities();
        savedAllowFlying = abilities.allowFlying;
        savedFlySpeed = abilities.getFlySpeed();
    }

    @Override
    public void onDisable() {
        if (mc.player == null) return;
        restoreAbilities();
        if (stopOnDisable.getValue()) mc.player.setVelocity(Vec3d.ZERO);
        else if (!isCreativeMode()) mc.player.setVelocity(last);
    }

    private boolean isCreativeMode() {
        return mc.player.isCreative() || mc.player.isSpectator();
    }

    private void restoreAbilities() {
        PlayerAbilities abilities = mc.player.getAbilities();
        abilities.setFlySpeed(savedFlySpeed > 0.0f ? savedFlySpeed : 0.05f);
        if (isCreativeMode()) return;
        abilities.flying = false;
        abilities.allowFlying = savedAllowFlying;
    }

    private boolean active() {
        return mc.player != null && mc.world != null && !mc.player.isGliding() && !mc.player.hasVehicle() && !mc.player.isSleeping();
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (!active()) return;
        PlayerAbilities abilities = mc.player.getAbilities();

        if (mode.getValue().equalsIgnoreCase("Creative")) {
            abilities.allowFlying = true;
            abilities.setFlySpeed(horizontal.getValue().floatValue() * ABILITY_SCALE);
            if (!mc.player.isOnGround() || FlightInput.up()) abilities.flying = true;
        } else if (!isCreativeMode() && (abilities.flying || abilities.allowFlying != savedAllowFlying)) {
            abilities.flying = false;
            abilities.allowFlying = savedAllowFlying;
            abilities.setFlySpeed(savedFlySpeed > 0.0f ? savedFlySpeed : 0.05f);
        }
    }

    @SubscribeEvent
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!active() || event.getMovementType() != MovementType.SELF) return;

        if (mode.getValue().equalsIgnoreCase("Creative")) {
            if (mc.player.getAbilities().flying && applyAntiKick(event, event.getY())) event.setCancelled(true);
            return;
        }

        double speed = horizontal.getValue().doubleValue();
        Vector2d move = MovementUtils.forward(speed);
        double verticalMotion = FlightInput.motion(vertical.getValue().doubleValue());
        double targetY = verticalMotion != 0 ? verticalMotion : -glide.getValue().doubleValue();

        Vec3d next;
        if (mode.getValue().equalsIgnoreCase("Vanilla")) {
            next = new Vec3d(last.x + (move.x - last.x) * 0.35, last.y + (targetY - last.y) * 0.45, last.z + (move.y - last.z) * 0.35);
            if (Math.abs(next.x) < 0.003) next = new Vec3d(0, next.y, next.z);
            if (Math.abs(next.z) < 0.003) next = new Vec3d(next.x, next.y, 0);
            if (Math.abs(next.y) < 0.003) next = new Vec3d(next.x, 0, next.z);
        } else {
            next = new Vec3d(move.x, targetY, move.y);
        }
        last = next;

        double y = next.y;
        applyAntiKickTo(y);
        if (floating < 0) {
            y = KICK_DROP;
            floating = 0;
        }

        event.setMovement(new Vec3d(next.x, y, next.z));
        event.setCancelled(true);
        mc.player.setVelocity(Vec3d.ZERO);
    }

    private void applyAntiKickTo(double y) {
        if (!antiKick.getValue() || mc.player.isOnGround() || y <= KICK_DROP * 0.8) {
            floating = 0;
            return;
        }
        if (++floating >= kickInterval.getValue().intValue()) floating = -1;
    }

    private boolean applyAntiKick(PlayerMoveEvent event, double y) {
        applyAntiKickTo(y);
        if (floating >= 0) return false;
        floating = 0;
        event.setY(KICK_DROP);
        return true;
    }

    @Override
    public String getMetaData() {
        return mode.getValue();
    }
}
