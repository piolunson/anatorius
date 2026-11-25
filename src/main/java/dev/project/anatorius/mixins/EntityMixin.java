package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.ChangePitchEvent;
import dev.project.anatorius.events.impl.ChangeYawEvent;
import dev.project.anatorius.events.impl.UpdateVelocityEvent;
import dev.project.anatorius.modules.impl.movement.NoSlowModule;
import dev.project.anatorius.modules.impl.movement.VelocityModule;
import dev.project.anatorius.modules.impl.player.RotationLockModule;
import dev.project.anatorius.modules.impl.visuals.FreeLookModule;
import dev.project.anatorius.modules.impl.visuals.FreecamModule;
import dev.project.anatorius.modules.impl.visuals.ZoomModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin implements IMinecraft {
    @Inject(method = "getPose", at = @At("RETURN"), cancellable = true)
    private void getPose$fakeFly(CallbackInfoReturnable<net.minecraft.entity.EntityPose> cir) {
        if (dev.project.anatorius.modules.impl.movement.FakeFlyModule.stands((Entity) (Object) this)
                || dev.project.anatorius.modules.impl.movement.ElytraFlyModule.stands((Entity) (Object) this)) {
            cir.setReturnValue(net.minecraft.entity.EntityPose.STANDING);
        }
    }

    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void pushAwayFrom(Entity entity, CallbackInfo info) {
        VelocityModule module = Anatorius.MODULE_MANAGER.getModule(VelocityModule.class);
        if ((Object) this == mc.player && module != null && module.isToggled() && module.antiPush.getValue()) {
            info.cancel();
        }
    }

    @ModifyExpressionValue(method = "updateMovementInFluid", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getVelocity()Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d updateMovementInFluid(Vec3d vec3d) {
        VelocityModule module = Anatorius.MODULE_MANAGER.getModule(VelocityModule.class);
        if ((Object) this == mc.player && module != null && module.isToggled() && module.antiLiquidPush.getValue()) {
            return new Vec3d(0, 0, 0);
        }

        return vec3d;
    }

    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double zoomLookX(double cursorDeltaX) {
        return (Object) this == mc.player ? cursorDeltaX / ZoomModule.currentZoom() : cursorDeltaX;
    }

    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double zoomLookY(double cursorDeltaY) {
        return (Object) this == mc.player ? cursorDeltaY / ZoomModule.currentZoom() : cursorDeltaY;
    }

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void changeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo info) {
        if ((Object) this != mc.player) return;

        FreeLookModule module = Anatorius.MODULE_MANAGER.getModule(FreeLookModule.class);
        FreecamModule freecam = Anatorius.MODULE_MANAGER.getModule(FreecamModule.class);
        if (module != null && module.isToggled() && (freecam == null || !freecam.isToggled())) {
            module.applyMouse(cursorDeltaX, cursorDeltaY);
            info.cancel();
        }
    }

    @Inject(method = "updateVelocity", at = @At("HEAD"), cancellable = true)
    private void updateVelocity(float speed, Vec3d movementInput, CallbackInfo info) {
        if ((Object) this != mc.player) return;

        UpdateVelocityEvent event = new UpdateVelocityEvent(movementInput, speed);
        Anatorius.EVENT_HANDLER.post(event);
        if (event.isCancelled()) {
            info.cancel();
            mc.player.setVelocity(mc.player.getVelocity().add(event.getVelocity()));
        }
    }

    @Inject(method = "getYaw*", at = @At("HEAD"), cancellable = true)
    private void getYaw(CallbackInfoReturnable<Float> info) {
        RotationLockModule module = Anatorius.MODULE_MANAGER.getModule(RotationLockModule.class);
        if (module != null && module.isToggled() && (module.mode.getValue().equals("Yaw") || module.mode.getValue().equals("Both")) && (Object) this == mc.player) {
            info.setReturnValue(module.yaw.getValue().floatValue());
        }
    }

    @Inject(method = "getPitch*", at = @At("HEAD"), cancellable = true)
    private void getPitch(CallbackInfoReturnable<Float> info) {
        RotationLockModule module = Anatorius.MODULE_MANAGER.getModule(RotationLockModule.class);
        if (module != null && module.isToggled() && (module.mode.getValue().equals("Pitch") || module.mode.getValue().equals("Both")) && (Object) this == mc.player) {
            info.setReturnValue(module.pitch.getValue().floatValue());
        }
    }

    @Inject(method = "setYaw", at = @At("HEAD"), cancellable = true)
    private void setYaw(float yaw, CallbackInfo info) {
        if ((Object) this != mc.player) return;
        Anatorius.EVENT_HANDLER.post(new ChangeYawEvent(yaw));
    }

    @Inject(method = "setPitch", at = @At("HEAD"), cancellable = true)
    private void setPitch(float pitch, CallbackInfo info) {
        if ((Object) this != mc.player) return;
        Anatorius.EVENT_HANDLER.post(new ChangePitchEvent(pitch));
    }

    @ModifyExpressionValue(method = "getVelocityMultiplier", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getBlock()Lnet/minecraft/block/Block;"))
    private Block getVelocityMultiplier(Block original) {
        NoSlowModule module = Anatorius.MODULE_MANAGER.getModule(NoSlowModule.class);
        if (module != null && module.isToggled()) {
            if ((original == Blocks.SOUL_SAND && module.soulSand.getValue()) || (original == Blocks.HONEY_BLOCK && module.honeyBlocks.getValue())) {
                return Blocks.STONE;
            }
        }

        return original;
    }

    @Inject(method = "playSound", at = @At("HEAD"), cancellable = true)
    private void playSound(net.minecraft.sound.SoundEvent sound, float volume, float pitch, CallbackInfo info) {
        if (!dev.project.anatorius.modules.impl.movement.FakeFlyModule.muted()) return;
        net.minecraft.util.Identifier soundId = net.minecraft.registry.Registries.SOUND_EVENT.getId(sound);
        if (dev.project.anatorius.modules.impl.movement.FakeFlyModule.isSwapSound(soundId)) {
            info.cancel();
            return;
        }
        if (soundId != null) {
            String id = soundId.toString();
            if (id.contains("firework_rocket.launch") || id.contains("firework_rocket.blast")
                    || id.contains("firework_rocket_launch") || id.contains("firework_rocket_blast")) {
                info.cancel();
            }
        }
    }
}
