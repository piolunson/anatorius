package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.visuals.NoRenderModule;
import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LimbAnimator.class)
public class LimbAnimatorMixin {
    @ModifyReturnValue(method = "getAnimationProgress()F", at = @At("RETURN"))
    private float getPos(float original) {
        if(Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).limbSwing.getValue()) {
            return 0;
        } else {
            return original;
        }
    }

    @ModifyReturnValue(method = "getAnimationProgress(F)F", at = @At("RETURN"))
    private float getPos2(float original) {
        if(Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).limbSwing.getValue()) {
            return 0;
        } else {
            return original;
        }
    }

    @ModifyReturnValue(method = "getSpeed()F", at = @At("RETURN"))
    private float getSpeed(float original) {
        if(Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).limbSwing.getValue()) {
            return 0;
        } else {
            return original;
        }
    }

    @ModifyReturnValue(method = "getAmplitude(F)F", at = @At("RETURN"))
    private float getSpeed2(float original) {
        if(Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).limbSwing.getValue()) {
            return 0;
        } else {
            return original;
        }
    }
}
