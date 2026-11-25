package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.ClientDisconnectEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.mixins.accessors.FireworkRocketEntityAccessor;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.movement.ElytraFlyModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.NetworkUtils;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.util.Hand;

@RegisterModule(name = "AutoFirework", description = "Automatically switches to fireworks and throws them.", category = Module.Category.PLAYER)
public class AutoFireworkModule extends Module {
    public ModeSetting autoSwitch = new ModeSetting("Switch", "The mode that will be used for automatically switching to necessary items.", "Silent", InventoryUtils.SWITCH_MODES);

    public CategorySetting autoLaunchCategory = new CategorySetting("AutoLaunch", "Auto Launch settings.");
    public BooleanSetting elytraOnly = new BooleanSetting("ElytraOnly", "Only use fireworks when flying with elytra.", new CategorySetting.Visibility(autoLaunchCategory), true);
    public BooleanSetting onlyFirst = new BooleanSetting("OnlyFirst", "Only use firework once when taking off.", new CategorySetting.Visibility(autoLaunchCategory), false);
    public NumberSetting delay = new NumberSetting("Delay", "Delay between fireworks in ms. Max is Auto.", new CategorySetting.Visibility(autoLaunchCategory), 1000, 0, 5000);

    public CategorySetting controlCategory = new CategorySetting("Control", "ElytraFly Control mode settings. Delay is not used there.");
    public NumberSetting minSpeed = new NumberSetting("MinSpeed", "Control: fires a new rocket once your speed (blocks per tick) drops below this while you are moving forward.", new CategorySetting.Visibility(controlCategory), 0.6f, 0.0f, 3.0f);
    public NumberSetting falloff = new NumberSetting("Falloff", "Control: fires a new rocket once your speed falls below this percent of your recent top speed.", new CategorySetting.Visibility(controlCategory), 85, 50, 99);

    private final Timer timer = new Timer();
    private boolean hasFired = false;

    private volatile boolean serverGliding;
    private volatile boolean serverGlideKnown;
    private volatile float sentYaw;
    private volatile float sentPitch;
    private volatile boolean sentLook;
    private int glideTicks;
    private boolean startFired;
    private int lastFireAge = -1000;
    private double prevX, prevZ;
    private boolean hasPrev;
    private double topSpeed;

    @Override
    public void onEnable() {
        hasFired = false;
        timer.reset();
        resetControl();
        serverGliding = false;
        serverGlideKnown = false;
        sentLook = false;
    }

    @SubscribeEvent
    public void onDisconnect(ClientDisconnectEvent event) {
        serverGliding = false;
        serverGlideKnown = false;
        sentLook = false;
        resetControl();
    }

    private void resetControl() {
        glideTicks = 0;
        startFired = false;
        lastFireAge = -1000;
        hasPrev = false;
        topSpeed = 0;
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (mc.player == null || !(event.getPacket() instanceof EntityTrackerUpdateS2CPacket packet) || packet.id() != mc.player.getId()) return;
        for (DataTracker.SerializedEntry<?> entry : packet.trackedValues()) {
            if (entry.id() == 0 && entry.value() instanceof Byte flags) {
                serverGliding = (flags & 0x80) != 0;
                serverGlideKnown = true;
            }
        }
    }

    @SubscribeEvent(priority = -1000)
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacket() instanceof PlayerMoveC2SPacket packet && packet.changesLook()) {
            sentYaw = packet.getYaw(0.0f);
            sentPitch = packet.getPitch(0.0f);
            sentLook = true;
        }
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        ElytraFlyModule elytraFly = Anatorius.MODULE_MANAGER.getModule(ElytraFlyModule.class);
        boolean isElytraFlyActive = elytraFly != null && elytraFly.isToggled() && mc.player.getInventory().getStack(38).getItem() == Items.ELYTRA;

        if (elytraFly != null && elytraFly.isToggled() && elytraFly.mode.getValue().equalsIgnoreCase("Control")) {
            controlTick();
            return;
        }
        resetControl();

        boolean isFlying = mc.player.isGliding() || (isElytraFlyActive && !mc.player.isOnGround());
        if (!isFlying) {
            hasFired = false;
        }

        if (elytraOnly.getValue() && !isFlying) {
            return;
        }

        if (onlyFirst.getValue() && hasFired) {
            return;
        }

        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.FIREWORK_ROCKET))) {
            return;
        }

        boolean shouldFire = false;
        if (delay.getValue().intValue() >= 5000) {
            if (isFlying) {
                if (isElytraFlyActive) {
                    if (mc.player.input.hasForwardMovement() && timer.hasTimeElapsed(1000)) {
                        shouldFire = true;
                    }
                } else if (mc.player.getVelocity().y < -0.1) {
                    if (timer.hasTimeElapsed(500)) {
                        shouldFire = true;
                    }
                }
            } else if (!elytraOnly.getValue()) {
                if (timer.hasTimeElapsed(1000)) {
                    shouldFire = true;
                }
            }
        } else {
            if (timer.hasTimeElapsed(delay.getValue().intValue())) {
                shouldFire = true;
            }
        }

        if (shouldFire) {
            fireFirework(mc.player.getYaw(), mc.player.getPitch());
            timer.reset();
            hasFired = true;
        }
    }

    private void controlTick() {
        if (!mc.player.isGliding()) {
            resetControl();
            return;
        }
        glideTicks++;

        double speed = 0;
        if (hasPrev) speed = Math.hypot(mc.player.getX() - prevX, mc.player.getZ() - prevZ);
        prevX = mc.player.getX();
        prevZ = mc.player.getZ();
        hasPrev = true;

        topSpeed = Math.max(speed, topSpeed * 0.98);

        boolean confirmed = serverGlideKnown ? serverGliding && glideTicks >= 2 : glideTicks >= 30;
        if (!confirmed) return;

        if (rocketAlive() || mc.player.age - lastFireAge < Math.max(4, Anatorius.SERVER_MANAGER.getPing() / 50 + 3)) return;
        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.FIREWORK_ROCKET))) return;

        boolean fire;
        if (!startFired) {
            fire = true;
        } else if (onlyFirst.getValue() || !mc.player.input.hasForwardMovement()) {
            fire = false;
        } else {
            fire = speed < minSpeed.getValue().doubleValue() || speed < topSpeed * falloff.getValue().doubleValue() / 100.0;
        }
        if (!fire) return;

        float yaw = sentLook ? sentYaw : Anatorius.ROTATION_MANAGER.getServerYaw();
        float pitch = sentLook ? sentPitch : Anatorius.ROTATION_MANAGER.getServerPitch();
        if (fireFirework(yaw, pitch)) {
            startFired = true;
            lastFireAge = mc.player.age;
            topSpeed = speed;
            hasFired = true;
            timer.reset();
        }
    }

    private boolean rocketAlive() {
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof FireworkRocketEntity rocket && ((FireworkRocketEntityAccessor) rocket).anatorius$getShooter() == mc.player) return true;
        }
        return false;
    }

    private boolean fireFirework(float yaw, float pitch) {
        boolean offhand = mc.player.getOffHandStack().getItem() == Items.FIREWORK_ROCKET;
        if (autoSwitch.getValue().equalsIgnoreCase("None") && mc.player.getMainHandStack().getItem() != Items.FIREWORK_ROCKET && !offhand) {
            Anatorius.CHAT_MANAGER.tagged("You are currently not holding any fireworks.", getName());
            setToggled(false);
            return false;
        }

        int slot = InventoryUtils.find(Items.FIREWORK_ROCKET, 0, autoSwitch.getValue().equalsIgnoreCase("AltSwap") ? 35 : 8);
        int previousSlot = mc.player.getInventory().getSelectedSlot();

        if (slot == -1 || (offhand && autoSwitch.getValue().equalsIgnoreCase("None") && mc.player.getMainHandStack().getItem() != Items.FIREWORK_ROCKET)) {
            if (offhand) {
                NetworkUtils.sendSequencedPacket(sequence -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, sequence, yaw, pitch));
                return true;
            }
            Anatorius.CHAT_MANAGER.tagged("No fireworks could be found in your hotbar.", getName());
            setToggled(false);
            return false;
        }

        InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);
        NetworkUtils.sendSequencedPacket(sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, yaw, pitch));
        InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
        return true;
    }
}
