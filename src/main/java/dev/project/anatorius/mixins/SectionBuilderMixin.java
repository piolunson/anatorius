package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.project.anatorius.modules.impl.visuals.XrayModule;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SectionBuilder.class)
public class SectionBuilderMixin {
    @WrapOperation(method = "build", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/chunk/ChunkOcclusionDataBuilder;markClosed(Lnet/minecraft/util/math/BlockPos;)V"))
    private void xrayOcclusion(ChunkOcclusionDataBuilder builder, BlockPos pos, Operation<Void> original) {
        if (!XrayModule.isActive()) original.call(builder, pos);
    }
}
