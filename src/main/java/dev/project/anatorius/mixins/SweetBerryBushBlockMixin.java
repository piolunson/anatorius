package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.movement.NoSlowModule;
import net.minecraft.block.BlockState;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SweetBerryBushBlock.class)
public class SweetBerryBushBlockMixin {
    @WrapWithCondition(method = "onEntityCollision", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;slowMovement(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Vec3d;)V"))
    private boolean slowMovement$noSlow(Entity entity, BlockState state, Vec3d multiplier) {
        NoSlowModule module = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(NoSlowModule.class);
        return !(entity == MinecraftClient.getInstance().player && module != null && module.isToggled() && module.berryBush.getValue());
    }
}
