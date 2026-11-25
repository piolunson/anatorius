package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.RemoveFireworkEvent;
import dev.project.anatorius.modules.impl.combat.AutoMaceModule;
import dev.project.anatorius.modules.impl.movement.FakeFlyModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityMixin implements IMinecraft {
    @Shadow private int life;

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick$hookHead(CallbackInfo info) {
        FakeFlyModule.hook = true;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void tick$hookReturn(CallbackInfo info) {
        FakeFlyModule.hook = false;
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/projectile/FireworkRocketEntity;updateRotation()V", shift = At.Shift.AFTER), cancellable = true)
    private void tick(CallbackInfo info) {
        FireworkRocketEntity entity = ((FireworkRocketEntity) (Object) this);

        RemoveFireworkEvent event = new RemoveFireworkEvent(entity);
        Anatorius.EVENT_HANDLER.post(event);

        if (event.isCancelled()) {
            FakeFlyModule.hook = false;
            info.cancel();

            if (life == 0 && !entity.isSilent()) {
                if (!FakeFlyModule.muted()) {
                    mc.world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.AMBIENT, 3.0f, 1.0f);
                }
            }

            ++life;
            if (mc.world.isClient() && life % 2 < 2) {
                mc.world.addParticleClient(ParticleTypes.FIREWORK, entity.getX(), entity.getY(), entity.getZ(), mc.world.random.nextGaussian() * 0.05, -entity.getVelocity().y * 0.5, mc.world.random.nextGaussian() * 0.05);
            }
        }
    }

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getRotationVector()Lnet/minecraft/util/math/Vec3d;")
    )
    private Vec3d redirectFireworkRotation(LivingEntity instance) {
        if (instance == mc.player && boostsAlongServerLook()) {
            float[] zioLook = FakeFlyModule.rocketLook();
            float yaw = zioLook != null ? zioLook[0] : Anatorius.ROTATION_MANAGER.getServerYaw();
            float pitch = zioLook != null ? zioLook[1] : Anatorius.ROTATION_MANAGER.getServerPitch();
            float f = pitch * ((float)Math.PI / 180F);
            float g = -yaw * ((float)Math.PI / 180F);
            float h = (float)Math.cos(g);
            float i = (float)Math.sin(g);
            float j = (float)Math.cos(f);
            float k = (float)Math.sin(f);
            return new Vec3d(i * j, -k, h * j);
        }
        return instance.getRotationVector();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isGliding()Z"))
    private boolean tick$shooterGliding(LivingEntity instance) {
        return instance.isGliding() || FakeFlyModule.glides(instance) || dev.project.anatorius.modules.impl.movement.ElytraFlyModule.glides(instance);
    }

    private boolean boostsAlongServerLook() {
        if (Anatorius.MODULE_MANAGER == null) return false;

        AutoMaceModule autoMace = Anatorius.MODULE_MANAGER.getModule(AutoMaceModule.class);
        if (autoMace != null && autoMace.isToggled()) return true;

        if (dev.project.anatorius.modules.impl.movement.ElytraFlyModule.rocketsAlongServerLook()) return true;

        FakeFlyModule fakeFly = Anatorius.MODULE_MANAGER.getModule(FakeFlyModule.class);
        return fakeFly != null && fakeFly.isToggled();
    }
}
