package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.ConsumeItemEvent;
import dev.project.anatorius.events.impl.PlayerJumpEvent;
import dev.project.anatorius.modules.impl.movement.*;
import dev.project.anatorius.modules.impl.player.SwingModule;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.mixins.ILivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements ILivingEntity, IMinecraft {
    @Shadow public abstract @Nullable EntityAttributeInstance getAttributeInstance(RegistryEntry<EntityAttribute> attribute);

    @Shadow @Final private static EntityAttributeModifier SPRINTING_SPEED_BOOST;

    @Shadow private int jumpingCooldown;

    @Shadow protected ItemStack activeItemStack;

    @Unique private boolean staticPlayerEntity = false;

    @Inject(method = "hasStatusEffect", at = @At("HEAD"), cancellable = true)
    private void hasStatusEffect$noEffects(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == net.minecraft.client.MinecraftClient.getInstance().player && dev.project.anatorius.modules.impl.player.NoEffectsModule.blocks(effect)) {
            cir.setReturnValue(false);
        }
    }

    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "applyMovementInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"), index = 1)
    private net.minecraft.util.math.Vec3d applyMovementInput$entityControl(net.minecraft.util.math.Vec3d motion) {
        LivingEntity self = (LivingEntity) (Object) this;
        net.minecraft.util.math.Vec3d controlled = dev.project.anatorius.modules.impl.movement.EntityControlModule.vehicleMotion(self, motion);
        if (controlled == null) return motion;
        self.setVelocity(controlled);
        return controlled;
    }

    @Inject(method = "isGliding", at = @At("HEAD"), cancellable = true)
    private void isGliding$infDurability(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == net.minecraft.client.MinecraftClient.getInstance().player && FakeFlyModule.overridesGliding()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void travel$hookHead(net.minecraft.util.math.Vec3d movementInput, CallbackInfo ci) {
        if ((Object) this == net.minecraft.client.MinecraftClient.getInstance().player) FakeFlyModule.hook = true;
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void travel$hookReturn(net.minecraft.util.math.Vec3d movementInput, CallbackInfo ci) {
        if ((Object) this == net.minecraft.client.MinecraftClient.getInstance().player) FakeFlyModule.hook = false;
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isGliding()Z"))
    private boolean travel$fakeFly(LivingEntity instance) {
        return instance.isGliding() || FakeFlyModule.glides(instance) || dev.project.anatorius.modules.impl.movement.ElytraFlyModule.glides(instance);
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "calcGlidingVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getRotationVector()Lnet/minecraft/util/math/Vec3d;"))
    private net.minecraft.util.math.Vec3d calcGlidingVelocity$fakeFlyLook(LivingEntity instance) {
        float[] look = dev.project.anatorius.utils.minecraft.GlideSteering.look(instance);
        return look == null ? instance.getRotationVector() : instance.getRotationVector(look[1], look[0]);
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "calcGlidingVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getPitch()F"))
    private float calcGlidingVelocity$fakeFlyPitch(LivingEntity instance) {
        float[] look = dev.project.anatorius.utils.minecraft.GlideSteering.look(instance);
        return look == null ? instance.getPitch() : look[1];
    }

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override
    public boolean anatorius$isStaticPlayerEntity() {
        return staticPlayerEntity;
    }

    @Override
    public void anatorius$setStaticPlayerEntity(boolean staticPlayerEntity) {
        this.staticPlayerEntity = staticPlayerEntity;
    }

    @WrapMethod(method = "getStepHeight")
    private float getStepHeight(Operation<Float> original) {
        if ((Object) this == mc.player && Anatorius.MODULE_MANAGER != null && Anatorius.MODULE_MANAGER.getModule(StepModule.class).isToggled() && mc.player.isOnGround()) {
            return Anatorius.MODULE_MANAGER.getModule(StepModule.class).height.getValue().floatValue();
        }

        return original.call();
    }

    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;pop()V", ordinal = 2, shift = At.Shift.BEFORE))
    private void doItemUse(CallbackInfo info) {
        NoJumpDelayModule module = Anatorius.MODULE_MANAGER.getModule(NoJumpDelayModule.class);
        if (Anatorius.MODULE_MANAGER != null && module != null && module.isToggled() && jumpingCooldown == 10) {
            jumpingCooldown = module.ticks.getValue().intValue();
        }
    }

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "getHandSwingDuration", at = @At("RETURN"))
    private int getHandSwingDuration(int original) {
        if ((Object) this != mc.player) return original;
        return Anatorius.MODULE_MANAGER.getModule(SwingModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(SwingModule.class).modifySpeed.getValue() && mc.options.getPerspective().isFirstPerson() ? (21 - Anatorius.MODULE_MANAGER.getModule(SwingModule.class).speed.getValue().intValue()) : original;
    }

    @Inject(method = "consumeItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;finishUsing(Lnet/minecraft/world/World;Lnet/minecraft/entity/LivingEntity;)Lnet/minecraft/item/ItemStack;", shift = At.Shift.AFTER))
    private void consumeItem(CallbackInfo ci) {
        if((Object) this == mc.player) Anatorius.EVENT_HANDLER.post(new ConsumeItemEvent(activeItemStack));
    }

    @Inject(method = "setSprinting", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setSprinting(Z)V", shift = At.Shift.AFTER), cancellable = true)
    private void setSprinting$setSprinting(boolean sprinting, CallbackInfo info) {
        if ((Object) this == mc.player && Anatorius.MODULE_MANAGER.getModule(SprintModule.class).isToggled()) {
            EntityAttributeInstance entityAttributeInstance = getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
            entityAttributeInstance.removeModifier(SPRINTING_SPEED_BOOST.id());

            if (Anatorius.MODULE_MANAGER.getModule(SprintModule.class).shouldSprint()) {
                setFlag(3, true);
                entityAttributeInstance.addTemporaryModifier(SPRINTING_SPEED_BOOST);
            } else {
                setFlag(3, false);
            }

            info.cancel();
        }
    }

    @ModifyExpressionValue(method = "travelMidAir", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Lnet/minecraft/entity/effect/StatusEffectInstance;"))
    private @Nullable StatusEffectInstance travelMidAir$getStatusEffect(@Nullable StatusEffectInstance original) {
        if (Anatorius.MODULE_MANAGER.getModule(AntiLevitationModule.class).isToggled()) return null;
        return original;
    }

    @ModifyExpressionValue(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z", ordinal = 1))
    private boolean tickMovement$hasStatusEffect(boolean original) {
        if (Anatorius.MODULE_MANAGER.getModule(AntiLevitationModule.class).isToggled()) return false;
        return original;
    }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void jump$HEAD(CallbackInfo info) {
        if ((Object) this != mc.player) return;
        PlayerJumpEvent event = new PlayerJumpEvent();
        Anatorius.EVENT_HANDLER.post(event);

        if (event.isCancelled()) {
            Anatorius.EVENT_HANDLER.post(new PlayerJumpEvent.Post());
            info.cancel();
        }
    }

    @Inject(method = "jump", at = @At("RETURN"))
    private void jump$RETURN(CallbackInfo info) {
        if ((Object) this != mc.player) return;
        Anatorius.EVENT_HANDLER.post(new PlayerJumpEvent.Post());
    }
}
