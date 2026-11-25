package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.PlayerTravelEvent;
import dev.project.anatorius.modules.impl.movement.KeepSprintModule;
import dev.project.anatorius.modules.impl.movement.SafeWalkModule;
import dev.project.anatorius.modules.impl.movement.SpeedModule;
import dev.project.anatorius.modules.impl.movement.VelocityModule;
import dev.project.anatorius.modules.impl.exploit.ReachModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity implements IMinecraft {
    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @ModifyReturnValue(method = "isPushedByFluids", at = @At("RETURN"))
    private boolean isPushedByFluids(boolean original) {
        if ((Object) this == mc.player && Anatorius.MODULE_MANAGER.getModule(VelocityModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(VelocityModule.class).antiLiquidPush.getValue()) return false;
        return original;
    }

    @ModifyExpressionValue(method = "knockbackTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;multiply(DDD)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d keepSprint$velocity(Vec3d reducedVelocity) {
        KeepSprintModule module = Anatorius.MODULE_MANAGER.getModule(KeepSprintModule.class);
        if ((Object) this == mc.player && module.isToggled()) {
            double factor = (0.6 + 0.4 * module.motion.getValue().doubleValue()) / 0.6;
            return reducedVelocity.multiply(factor, 1.0, factor);
        }
        return reducedVelocity;
    }

    @Inject(method = "knockbackTarget", at = @At("TAIL"))
    private void keepSprint$sprint(Entity target, float strength, Vec3d playerTargetVelocity, CallbackInfo ci) {
        if ((Object) this == mc.player && strength > 0.0F && Anatorius.MODULE_MANAGER.getModule(KeepSprintModule.class).isToggled()) {
            mc.player.setSprinting(true);
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void travel(Vec3d movementInput, CallbackInfo info) {
        if ((Object) this != mc.player) return;
        PlayerTravelEvent event = new PlayerTravelEvent(movementInput);
        Anatorius.EVENT_HANDLER.post(event);

        if (event.isCancelled()) {
            move(MovementType.SELF, getVelocity());
            info.cancel();
        }
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void travel$RETURN(Vec3d movementInput, CallbackInfo info) {
        if ((Object) this != mc.player) return;
        Anatorius.EVENT_HANDLER.post(new PlayerTravelEvent.Post(movementInput));
    }

    @Inject(method = "getBlockInteractionRange", at = @At("HEAD"), cancellable = true)
    private void getBlockInteractionRange(CallbackInfoReturnable<Double> info) {
        if (Anatorius.MODULE_MANAGER.getModule(ReachModule.class).isToggled()) {
            info.setReturnValue(Anatorius.MODULE_MANAGER.getModule(ReachModule.class).amount.getValue().doubleValue());
        }
    }

    @Inject(method = "getEntityInteractionRange", at = @At("HEAD"), cancellable = true)
    private void getEntityInteractionRange(CallbackInfoReturnable<Double> info) {
        if (Anatorius.MODULE_MANAGER.getModule(ReachModule.class).isToggled()) {
            info.setReturnValue(Anatorius.MODULE_MANAGER.getModule(ReachModule.class).amount.getValue().doubleValue());
        }
    }

    @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true)
    private void clipAtLedge(CallbackInfoReturnable<Boolean> info) {
        if (Anatorius.MODULE_MANAGER.getModule(SafeWalkModule.class).isToggled()) {
            info.setReturnValue(true);
        }
    }

    @Inject(method = "getMovementSpeed", at = @At("HEAD"), cancellable = true)
    private void getMovementSpeed(CallbackInfoReturnable<Float> info) {
        if (Anatorius.MODULE_MANAGER.getModule(SpeedModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(SpeedModule.class).mode.getValue().equalsIgnoreCase("Vanilla")) {
            info.setReturnValue(Anatorius.MODULE_MANAGER.getModule(SpeedModule.class).vanillaSpeed.getValue().floatValue());
        }
    }

    @ModifyReturnValue(method = "getMovementSpeed", at = @At("RETURN"))
    private float getMovementSpeed$noSlow(float original) {
        if ((Object) this != mc.player || !getEntityWorld().isClient()) return original;
        dev.project.anatorius.modules.impl.movement.NoSlowModule module = dev.project.anatorius.Anatorius.MODULE_MANAGER.getModule(dev.project.anatorius.modules.impl.movement.NoSlowModule.class);
        if (module == null || !module.ignoresSlowness()) return original;
        float walking = ((PlayerEntity) (Object) this).getAbilities().getWalkSpeed();
        if (original >= walking) return original;
        return isSprinting() ? walking * 1.3000001f : walking;
    }
}
