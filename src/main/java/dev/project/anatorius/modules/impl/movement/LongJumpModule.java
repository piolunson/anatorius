package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PlayerJumpEvent;
import dev.project.anatorius.events.impl.PlayerMoveEvent;
import dev.project.anatorius.events.impl.PlayerTravelEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.MovementUtils;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2d;

@RegisterModule(name = "LongJump", description = "Jump but long and fast.", category = Module.Category.MOVEMENT)
public class LongJumpModule extends Module {
    public static LongJumpModule INSTANCE;

    public ModeSetting mode = new ModeSetting("Mode", "The method used for long jumping.", "Boost", new String[]{"Boost", "Grim", "Strict", "StrictHigh"});
    public NumberSetting boost = new NumberSetting("Boost", "Speed boost multiplier.", 0.4, 0.2, 10.0);
    public BooleanSetting autoDisable = new BooleanSetting("AutoDisable", "Automatically disables on server position update.", true);
    public BooleanSetting friction = new BooleanSetting("Friction", "Only start when on ground.", true);

    private boolean hasBeenGrounded = false;
    private int stage = 0;
    private double moveSpeed, lastDist;

    public LongJumpModule() {
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        hasBeenGrounded = false;
        stage = 0;
        moveSpeed = 0;
        lastDist = 0;

        if (mc.player != null && !AccelerateModule.INSTANCE.isToggled()) {
            AccelerateModule.INSTANCE.setToggled(true);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null && AccelerateModule.INSTANCE.isToggled()) {
            AccelerateModule.INSTANCE.setToggled(false);
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (getNull()) return;

        if (!hasBeenGrounded) {
            hasBeenGrounded = mc.player.isOnGround();
        }

        if (!hasBeenGrounded && friction.getValue()) return;

        lastDist = Math.sqrt(MathHelper.square((float) (mc.player.getX() - mc.player.lastX)) +
                            MathHelper.square((float) (mc.player.getZ() - mc.player.lastZ)));

        if (canSprint()) {
            mc.player.setSprinting(true);
        }
    }

    @SubscribeEvent
    public void onPlayerMove(PlayerMoveEvent event) {
        if (getNull()) return;

        switch (mode.getValue()) {
            case "Grim":
                if (!mc.player.isGliding()) return;
                break;

            default:
                if (!hasBeenGrounded && friction.getValue()) return;

                if (mc.player.isGliding() || mc.player.isInLava() || mc.player.isTouchingWater()) {
                    return;
                }

                switch (stage) {
                    case 0:
                        ++stage;
                        lastDist = 0.0D;
                        break;

                    case 2:
                        double motionY = 0.40123128;
                        if ((mc.player.input.getMovementInput().y != 0.0F || mc.player.input.getMovementInput().x != 0.0F) && mc.player.isOnGround()) {
                            if (mc.player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
                                motionY += ((mc.player.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1F);
                            }
                            event.setMovement(new Vec3d(event.getMovement().getX(), motionY, event.getMovement().getZ()));
                            moveSpeed *= 2.149;
                        }
                        break;

                    case 3:
                        moveSpeed = lastDist - (0.76 * (lastDist - getBaseMoveSpeed()));
                        break;

                    default:
                        Box box = mc.player.getBoundingBox().offset(0.0D, mc.player.getVelocity().y, 0.0D);
                        boolean hasCollision = !mc.world.getBlockCollisions(mc.player, box).iterator().hasNext();

                        if ((!hasCollision || mc.player.verticalCollision) && stage > 0) {
                            stage = mc.player.input.getMovementInput().y == 0.0F && mc.player.input.getMovementInput().x == 0.0F ? 0 : 1;
                        }
                        moveSpeed = lastDist - lastDist / 159.0D;
                        break;
                }

                moveSpeed = Math.max(moveSpeed, getBaseMoveSpeed());

                double forward = mc.player.input.getMovementInput().y;
                double strafe = mc.player.input.getMovementInput().x;
                float yaw = mc.player.getYaw();

                if (forward != 0 && strafe != 0) {
                    forward = forward * Math.sin(Math.PI / 4);
                    strafe = strafe * Math.cos(Math.PI / 4);
                } else if (forward == 0 && strafe == 0) {
                    event.setMovement(new Vec3d(0, event.getMovement().getY(), 0));
                    ++stage;
                    return;
                }

                double motionX = (forward * moveSpeed * -Math.sin(Math.toRadians(yaw)) + strafe * moveSpeed * Math.cos(Math.toRadians(yaw))) * 0.99D;
                double motionZ = (forward * moveSpeed * Math.cos(Math.toRadians(yaw)) - strafe * moveSpeed * -Math.sin(Math.toRadians(yaw))) * 0.99D;

                event.setMovement(new Vec3d(motionX, event.getMovement().getY(), motionZ));
                ++stage;
                break;
        }
    }

    @SubscribeEvent
    public void onPlayerTravel(PlayerTravelEvent event) {
        if (getNull()) return;

        if (mode.getValue().equals("Grim")) {
            int slot = InventoryUtils.findHotbar(Items.ELYTRA);
            if (slot == -1) return;

            if (!mc.player.isGliding()) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }

            if (mc.player.isOnGround()) {
                mc.player.jump();
            }
        }
    }

    @SubscribeEvent
    public void onPlayerJump(PlayerJumpEvent event) {
        if (getNull()) return;

        if (mode.getValue().equals("Grim")) {
            int slot = InventoryUtils.findHotbar(Items.ELYTRA);
            if (slot != -1) {
                event.setCancelled(true);
            }
        }
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (getNull()) return;

        if (event.getPacket() instanceof PlayerPositionLookS2CPacket && autoDisable.getValue()) {
            setToggled(false);
        }
    }

    private double getBaseMoveSpeed() {
        double baseSpeed = 0.272;
        if (mc.player.hasStatusEffect(StatusEffects.SPEED)) {
            final int amplifier = mc.player.getStatusEffect(StatusEffects.SPEED).getAmplifier();
            baseSpeed *= 1.0 + (0.2 * (amplifier + 1));
        }
        return baseSpeed * boost.getValue().doubleValue();
    }

    public static boolean isGrimJumping() {
        if (INSTANCE == null || !INSTANCE.isToggled()) return false;
        int slot = InventoryUtils.findHotbar(Items.ELYTRA);
        if (slot == -1) return false;
        return INSTANCE.mode.getValue().equals("Grim");
    }

    private boolean canSprint() {
        return ((mc.player.input.getMovementInput().x != 0.0F || mc.player.input.getMovementInput().y != 0.0F)
                && !mc.player.isUsingItem()
                && !mc.player.isClimbing()
                && !mc.player.horizontalCollision
                && mc.player.getHungerManager().getFoodLevel() > 6);
    }

    @Override
    public String getMetaData() {
        return mode.getValue();
    }
}
