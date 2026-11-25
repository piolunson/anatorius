package dev.project.anatorius.modules.impl.miscellaneous;

import com.mojang.authlib.GameProfile;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.settings.impl.*;
import lombok.Getter;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PacketReceiveEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.mixins.accessors.PlayerInteractEntityC2SPacketAccessor;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.utils.minecraft.DamageUtils;
import dev.project.anatorius.utils.minecraft.ItemUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.rotations.RotationUtils;
import dev.project.anatorius.utils.system.MathUtils;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

@RegisterModule(name = "FakePlayer", description = "Spawns in a fake player entity that you can use to test modules on.", category = Module.Category.MISCELLANEOUS)
public class FakePlayerModule extends Module {
    public StringSetting name = new StringSetting("Name", "The name that will be assigned to the fake player.", "Dummy");
    public NumberSetting health = new NumberSetting("Health", "The amount of health that will be assigned to the fake player.", 20.0f, 1.0f, 20.0f);
    public NumberSetting absorption = new NumberSetting("Absorption", "The amount of absorption that will be assigned to the fake player.", 16, 0, 16);
    public BooleanSetting damage = new BooleanSetting("Damage", "The fake player takes damage from your hits and from explosions (simulated in the client, the server doesn't know it).", true);
    public BooleanSetting totems = new BooleanSetting("Totems", "The fake player holds a totem and pops it instead of dying.", new BooleanSetting.Visibility(damage, true), true);

    public CategorySetting movementCategory = new CategorySetting("Movement", "The category that contains settings related to movement.");
    public ModeSetting movementMode = new ModeSetting("Movement", "Mode", "The mode that will be used for the fake player's movement.", new CategorySetting.Visibility(movementCategory), "None", new String[]{"None", "Random"});
    public NumberSetting velocity = new NumberSetting("Velocity", "Velocity", "The velocity to apply on the fake player.", new CategorySetting.Visibility(movementCategory), 0.3f, 0.1f, 0.4f);
    public BooleanSetting changeDirection = new BooleanSetting("ChangeDirection", "Changes the player's direction when a specified amount of time has passed.", new CategorySetting.Visibility(movementCategory), false);
    public NumberSetting timeout = new NumberSetting("Timeout", "The amount of time that it takes for the player to change direction.", new BooleanSetting.Visibility(changeDirection, true), 5, 1, 15);

    @Getter private OtherClientPlayerEntity player = null;

    private double[] direction = generateDirection();

    private final Timer timer = new Timer();

    private boolean stepping = false;
    private final Timer stepTimer = new Timer();

    private int hurtCooldown;
    private float lastDamageTaken;
    private int deadTicks;
    private int regenTicks;
    private int absorptionTicks;

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (player == null) return;
        if (!movementMode.getValue().equalsIgnoreCase("Random")) return;

        BlockPos position = player.getBlockPos();
        boolean changeDirection = false;

        if (this.changeDirection.getValue() && timer.hasTimeElapsed(timeout.getValue().longValue() * 1000L)) {
            changeDirection = true;
            timer.reset();
        }

        if (hasObstruction(position)) {
            for (Direction direction : Direction.values()) {
                BlockPos offsetPosition = position.offset(direction);
                if ((WorldUtils.blocksMovement(mc.world.getBlockState(offsetPosition)) || WorldUtils.blocksMovement(mc.world.getBlockState(offsetPosition.up()))) && WorldUtils.blocksMovement(mc.world.getBlockState(offsetPosition.up().up())) && player.getHorizontalFacing().equals(direction)) {
                    changeDirection = true;
                    timer.reset();
                }
            }
        }

        if (changeDirection) {
            double[] newDirection = generateDirection();
            if (direction == newDirection) newDirection = generateDirection();

            direction = newDirection;
        }

        if (stepping && stepTimer.hasTimeElapsed(500L)) {
            stepping = false;
            stepTimer.reset();
        }

        player.jump();

        float[] rotations = RotationUtils.getRotations(player, player.getX() + direction[0], player.getY() + fixAxisY(player), player.getZ() + direction[1]);

        player.setYaw(rotations[0]);
        player.headYaw = rotations[0];
        player.setPitch(0.0f);

        player.setPosition(player.getX() + direction[0], player.getY() + fixAxisY(player), player.getZ() + direction[1]);
    }

    @Override
    public void onEnable() {
        if (mc.world == null) {
            setToggled(false);
            return;
        }

        player = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), name.getValue()));
        player.copyPositionAndRotation(mc.player);
        player.setId(-673);
        player.copyFrom(mc.player);
        player.setHealth(health.getValue().floatValue());
        absorb(absorption.getValue().floatValue());
        if (damage.getValue() && totems.getValue()) player.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));

        mc.world.addEntity(player);

        player.tick();
        timer.reset();
        hurtCooldown = 0;
        lastDamageTaken = 0.0f;
        deadTicks = 0;
        regenTicks = 0;
        absorptionTicks = 0;
    }

    @Override
    public void onDisable() {
        if (mc.world == null || player == null) return;
        mc.world.removeEntity(player.getId(), Entity.RemovalReason.DISCARDED);
        player = null;
    }

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent event) {
        if (!damage.getValue() || player == null || mc.player == null || mc.world == null) return;
        if (!(event.getPacket() instanceof PlayerInteractEntityC2SPacket packet)) return;
        if (((PlayerInteractEntityC2SPacketAccessor) packet).getEntityId() != player.getId() || !isAttack(packet)) return;

        ItemStack weapon = weapon();
        float amount = meleeDamage(weapon);
        boolean crit = mc.player.getAttackCooldownProgress(0.5f) > 0.9f && critical();
        boolean enchanted = DamageUtils.getEnchantmentLevel(weapon, "sharpness") > 0;
        float yaw = mc.player.getYaw();
        onMain(() -> {
            if (player == null || mc.world == null || deadTicks > 0) return;
            if (crit) mc.particleManager.addEmitter(player, ParticleTypes.CRIT);
            if (enchanted) mc.particleManager.addEmitter(player, ParticleTypes.ENCHANTED_HIT);
            hurt(amount, yaw);
        });
    }

    @SubscribeEvent
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!damage.getValue() || player == null) return;
        if (!(event.getPacket() instanceof ExplosionS2CPacket packet)) return;

        onMain(() -> {
            if (player == null || mc.world == null || deadTicks > 0) return;
            float amount = DamageUtils.getDamage(player, player.getBoundingBox(), packet.center(), packet.radius(), null, false);
            float yaw = (float) Math.toDegrees(Math.atan2(player.getZ() - packet.center().z, player.getX() - packet.center().x)) - 90.0f;
            hurt(amount, yaw);
        });
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (player == null || mc.world == null) return;
        if (hurtCooldown > 0) hurtCooldown--;
        if (deadTicks > 0) {
            if (++deadTicks > 20) respawn();
            return;
        }

        if (regenTicks > 0 && --regenTicks % 25 == 0) player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 1.0f));
        if (absorptionTicks > 0 && --absorptionTicks == 0) player.setAbsorptionAmount(0.0f);
    }

    private void hurt(float amount, float attackerYaw) {
        if (amount <= 0.0f) return;

        float dealt;
        if (hurtCooldown > 10) {
            if (amount <= lastDamageTaken) return;
            dealt = amount - lastDamageTaken;
            lastDamageTaken = amount;
        } else {
            lastDamageTaken = amount;
            hurtCooldown = 20;
            dealt = amount;
            player.animateDamage(MathHelper.wrapDegrees(attackerYaw - player.getYaw()));
            mc.world.playSoundClient(player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 1.0f, false);
        }

        float absorbed = Math.min(player.getAbsorptionAmount(), dealt);
        player.setAbsorptionAmount(player.getAbsorptionAmount() - absorbed);
        float health = player.getHealth() - (dealt - absorbed);
        if (health > 0.0f) {
            player.setHealth(health);
        } else if (totems.getValue()) {
            pop();
        } else {
            player.setHealth(0.0f);
            deadTicks = 1;
        }
    }

    private void pop() {
        player.setHealth(1.0f);
        clearEffects();
        absorb(8.0f);
        regenTicks = 900;
        absorptionTicks = 100;

        EntityStatusS2CPacket status = new EntityStatusS2CPacket(player, (byte) 35);
        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().onEntityStatus(status);
        Anatorius.EVENT_HANDLER.post(new PacketReceiveEvent(status));
    }

    private void absorb(float amount) {
        var max = player.getAttributeInstance(EntityAttributes.MAX_ABSORPTION);
        if (max != null && max.getBaseValue() < 16.0) max.setBaseValue(16.0);
        player.setAbsorptionAmount(amount);
    }

    public void clearEffects() {
        if (player == null) return;
        for (var effect : new java.util.ArrayList<>(player.getActiveStatusEffects().keySet())) player.removeStatusEffectInternal(effect);
    }

    private void respawn() {
        deadTicks = 0;
        hurtCooldown = 0;
        lastDamageTaken = 0.0f;
        regenTicks = 0;
        absorptionTicks = 0;
        player.deathTime = 0;
        player.hurtTime = 0;
        player.setHealth(health.getValue().floatValue());
        absorb(absorption.getValue().floatValue());
    }

    private float meleeDamage(ItemStack weapon) {
        float cooldown = mc.player.getAttackCooldownProgress(0.5f);
        double fall = mc.player.isGliding() ? 0.0 : mc.player.fallDistance;
        return DamageUtils.getMeleeDamage(mc.player, weapon, player, fall, cooldown, critical());
    }

    private ItemStack weapon() {
        int slot = Anatorius.POSITION_MANAGER.getServerSlot();
        return slot >= 0 && slot < 9 ? mc.player.getInventory().getStack(slot) : mc.player.getMainHandStack();
    }

    private boolean critical() {
        return mc.player.fallDistance > 0.0 && !mc.player.isOnGround() && !mc.player.isClimbing() && !mc.player.isTouchingWater()
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS) && !mc.player.hasVehicle() && !mc.player.isSprinting();
    }

    private static boolean isAttack(PlayerInteractEntityC2SPacket packet) {
        boolean[] attack = {false};
        packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
            @Override public void interact(Hand hand) {}
            @Override public void interactAt(Hand hand, Vec3d pos) {}
            @Override public void attack() { attack[0] = true; }
        });
        return attack[0];
    }

    private void onMain(Runnable task) {
        if (mc.isOnThread()) task.run();
        else mc.execute(task);
    }

    public boolean hasObstruction(BlockPos position) {
        for (Direction direction : Direction.values()) {
            BlockPos offsetPosition = position.offset(direction);
            if (WorldUtils.blocksMovement(mc.world.getBlockState(offsetPosition))) return true;
            if (WorldUtils.blocksMovement(mc.world.getBlockState(offsetPosition.up()))) return true;
        }

        return false;
    }

    public float fixAxisY(PlayerEntity player) {
        if (mc.world.getBlockState(player.getBlockPos().down()).getBlock() == Blocks.AIR && !stepping) return -1;

        if (hasObstruction(player.getBlockPos())) {
            for (Direction direction : Direction.values()) {
                BlockPos offsetPosition = player.getBlockPos().offset(direction);

                if (WorldUtils.blocksMovement(mc.world.getBlockState(offsetPosition)) && !WorldUtils.blocksMovement(mc.world.getBlockState(offsetPosition.up())) && player.getHorizontalFacing().equals(direction) && !WorldUtils.blocksMovement(mc.world.getBlockState(offsetPosition.up().up()))) {
                    stepping = true;
                    stepTimer.reset();

                    return 1;
                }

                if (WorldUtils.blocksMovement(mc.world.getBlockState(offsetPosition.up())) && player.getHorizontalFacing().equals(direction) && !WorldUtils.blocksMovement(mc.world.getBlockState(offsetPosition.up().up()))) {
                    stepping = true;
                    stepTimer.reset();

                    return 2;
                }
            }

            return 0;
        }

        return 0;
    }

    public double[] generateDirection() {
        double angle = MathUtils.random(2 * Math.PI, 0);
        double[] direction = new double[]{-Math.sin(angle), Math.cos(angle)};

        return new double[]{direction[0] * velocity.getValue().floatValue(), direction[1] * velocity.getValue().floatValue()};
    }
}
