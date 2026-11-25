package dev.project.anatorius.mixins;

import dev.project.anatorius.modules.impl.movement.EntityControlModule;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public class MobEntityMixin {
    @Inject(method = "hasSaddleEquipped", at = @At("HEAD"), cancellable = true)
    private void hasSaddleEquipped$entityControl(CallbackInfoReturnable<Boolean> cir) {
        if (EntityControlModule.saddles()) cir.setReturnValue(true);
    }
}
