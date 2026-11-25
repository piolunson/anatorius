package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.movement.NoSlowModule;
import net.minecraft.block.HoneyBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HoneyBlock.class)
public class HoneyBlockMixin {
    @WrapOperation(method = "onEntityCollision", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/HoneyBlock;isSliding(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;)Z"))
    private boolean isSliding$noSlow(HoneyBlock instance, BlockPos pos, Entity entity, Operation<Boolean> original) {
        NoSlowModule module = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(NoSlowModule.class);
        if (entity == MinecraftClient.getInstance().player && module != null && module.isToggled() && module.honeyBlocks.getValue()) return false;
        return original.call(instance, pos, entity);
    }
}
