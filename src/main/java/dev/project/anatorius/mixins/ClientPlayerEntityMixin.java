package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.*;
import dev.project.anatorius.modules.impl.movement.InventoryControlModule;
import dev.project.anatorius.modules.impl.movement.NoSlowModule;
import dev.project.anatorius.modules.impl.movement.VelocityModule;
import dev.project.anatorius.modules.impl.exploit.ReachModule;
import dev.project.anatorius.modules.impl.player.SwingModule;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.MovementType;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayerEntity {
    public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
        super(world, profile);
    }

    @Shadow @Final protected MinecraftClient client;

    @Shadow protected abstract void autoJump(float dx, float dz);

    @Shadow @Final public ClientPlayNetworkHandler networkHandler;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V", shift = At.Shift.BEFORE))
    private void tick$BEFORE(CallbackInfo info) {
        Anatorius.ROTATION_MANAGER.beginTickWindow();
        try {
            Anatorius.EVENT_HANDLER.post(new PlayerUpdateEvent());
        } finally {
            Anatorius.ROTATION_MANAGER.endTickWindow();
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V", shift = At.Shift.AFTER))
    private void tick$AFTER(CallbackInfo info) {
        if (Math.abs(getStandingEyeHeight() - getEyeHeight(getPose())) > 1.0E-4f) calculateDimensions();

        Anatorius.EVENT_HANDLER.post(new UpdateMovementEvent());
    }

    @Inject(method = "tick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;tickables:Ljava/util/List;", shift = At.Shift.BEFORE))
    private void tick$tickables(CallbackInfo ci) {
        Anatorius.EVENT_HANDLER.post(new UpdateMovementEvent.Post());
    }

    @WrapOperation(method = "sendMovementPackets", at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;lastYawClient:F", ordinal = 0))
    private float sendMovementPackets$lastYaw(ClientPlayerEntity instance, Operation<Float> original) {
        if (Anatorius.ROTATION_MANAGER.getRotation() != null) return Anatorius.ROTATION_MANAGER.getSentYaw();
        return original.call(instance);
    }

    @WrapOperation(method = "sendMovementPackets", at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;lastPitchClient:F", ordinal = 0))
    private float sendMovementPackets$lastPitch(ClientPlayerEntity instance, Operation<Float> original) {
        if (Anatorius.ROTATION_MANAGER.getRotation() != null) return Anatorius.ROTATION_MANAGER.getSentPitch();
        return original.call(instance);
    }

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "isBlockedFromSprinting", at = @At("RETURN"))
    private boolean isBlockedFromSprinting(boolean original) {
        NoSlowModule module = Anatorius.MODULE_MANAGER.getModule(NoSlowModule.class);
        if (module != null && module.isToggled() && module.items.getValue() && !module.shouldSlow()) return false;
        return original;
    }

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "getActiveItemSpeedMultiplier", at = @At("RETURN"))
    private float getActiveItemSpeedMultiplier(float original) {
        NoSlowModule module = Anatorius.MODULE_MANAGER.getModule(NoSlowModule.class);
        if (module != null && module.isToggled() && module.items.getValue() && !module.shouldSlow()) return 1.0F;
        return original;
    }

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "shouldSlowDown", at = @At("RETURN"))
    private boolean shouldSlowDown$noSlow(boolean original) {
        dev.project.anatorius.modules.impl.movement.NoSlowModule module = dev.project.anatorius.Anatorius.MODULE_MANAGER.getModule(dev.project.anatorius.modules.impl.movement.NoSlowModule.class);
        if (original && module != null && module.noPoseSlowdown()) return false;
        return original;
    }

    @Inject(method = "getMountJumpStrength", at = @At("HEAD"), cancellable = true)
    private void getMountJumpStrength$entityControl(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
        if (dev.project.anatorius.modules.impl.movement.EntityControlModule.fullJumps()) cir.setReturnValue(1.0f);
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void move(MovementType movementType, Vec3d movement, CallbackInfo info) {
        PlayerMoveEvent event = new PlayerMoveEvent(movementType, movement);
        Anatorius.EVENT_HANDLER.post(event);

        if (event.isCancelled()) {
            info.cancel();

            double prevX = getX();
            double prevZ = getZ();

            super.move(movementType, event.getMovement());
            autoJump((float) (getX() - prevX), (float) (getZ() - prevZ));
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("HEAD"), cancellable = true)
    private void sendMovementPackets(CallbackInfo info) {
        SendMovementEvent event = new SendMovementEvent();
        Anatorius.EVENT_HANDLER.post(event);
        if (event.isCancelled()) {
            info.cancel();
        }
    }

    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void pushOutOfBlocks(double x, double z, CallbackInfo info) {
        if (dev.project.anatorius.modules.impl.exploit.PhaseModule.holdsInBlock()) {
            info.cancel();
            return;
        }
        if (Anatorius.MODULE_MANAGER.getModule(VelocityModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(VelocityModule.class).antiBlockPush.getValue()) {
            info.cancel();
        }
    }

    @Inject(method = "swingHand", at = @At("HEAD"), cancellable = true)
    private void swingHand(Hand hand, CallbackInfo info) {
        if (Anatorius.MODULE_MANAGER.getModule(SwingModule.class).isToggled()) {
            if (!Anatorius.MODULE_MANAGER.getModule(SwingModule.class).hand.getValue().equals("None")) {
                switch (Anatorius.MODULE_MANAGER.getModule(SwingModule.class).hand.getValue()) {
                    case "Default" -> super.swingHand(hand);
                    case "Mainhand" -> super.swingHand(Hand.MAIN_HAND);
                    case "Offhand" -> super.swingHand(Hand.OFF_HAND);
                    case "Both" -> {
                        super.swingHand(Hand.MAIN_HAND);
                        super.swingHand(Hand.OFF_HAND);
                    }
                }

                if (Anatorius.MODULE_MANAGER.getModule(SwingModule.class).hand.getValue().equalsIgnoreCase("Packet") || !Anatorius.MODULE_MANAGER.getModule(SwingModule.class).noPacket.getValue()) {
                    networkHandler.sendPacket(new HandSwingC2SPacket(hand));
                }
            }

            info.cancel()   ;
        }
    }

    @Inject(method = "tickNausea", at = @At("HEAD"), cancellable = true)
    private void tickNausea(boolean fromPortalEffect, CallbackInfo info) {
        if (Anatorius.MODULE_MANAGER.getModule(InventoryControlModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(InventoryControlModule.class).portals.getValue()) {
            info.cancel();
        }
    }

    @Inject(method = "setCurrentHand", at = @At(value = "HEAD"))
    private void setCurrentHand(Hand hand, CallbackInfo info) {
        Anatorius.EVENT_HANDLER.post(new ChangeHandEvent());
    }

    @ModifyExpressionValue(method = "getCrosshairTarget(Lnet/minecraft/entity/Entity;DDF)Lnet/minecraft/util/hit/HitResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/projectile/ProjectileUtil;raycast(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/EntityHitResult;"))
    private static EntityHitResult anatorius$noEntityTrace(EntityHitResult original) {
        ReachModule reach = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(ReachModule.class);
        if (reach != null && reach.shouldIgnoreEntities()) {
            return null;
        }
        return original;
    }
}
