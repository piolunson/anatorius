package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.project.anatorius.modules.impl.exploit.TridentModule;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(TridentItem.class)
public class TridentItemMixin {
    @ModifyExpressionValue(method = "onStoppedUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isTouchingWaterOrRain()Z"))
    private boolean anatorius$riptideOnRelease(boolean wet) {
        return wet || TridentModule.riptideAnywhere();
    }

    @ModifyExpressionValue(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isTouchingWaterOrRain()Z"))
    private boolean anatorius$riptideOnUse(boolean wet) {
        return wet || TridentModule.riptideAnywhere();
    }

    @ModifyExpressionValue(method = "onStoppedUsing", at = @At(value = "CONSTANT", args = "intValue=10"))
    private int anatorius$minimumCharge(int ticks) {
        return TridentModule.minimumCharge(ticks);
    }

    @ModifyArgs(method = "onStoppedUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addVelocity(DDD)V"))
    private void anatorius$boost(Args args) {
        double boost = TridentModule.boost();
        if (boost == 1.0) return;
        args.set(0, args.<Double>get(0) * boost);
        args.set(1, args.<Double>get(1) * boost);
        args.set(2, args.<Double>get(2) * boost);
    }
}
