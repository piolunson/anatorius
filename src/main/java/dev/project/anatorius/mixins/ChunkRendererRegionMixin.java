package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.project.anatorius.modules.impl.visuals.XrayModule;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkRendererRegion.class)
public class ChunkRendererRegionMixin {
    @ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
    private BlockState xrayBlock(BlockState state, @Local(argsOnly = true) BlockPos pos) {
        if (!XrayModule.isActive() || state.isAir()) return state;
        return XrayModule.filter(state, pos);
    }

    @ModifyReturnValue(method = "getFluidState", at = @At("RETURN"))
    private FluidState xrayFluid(FluidState state) {
        return XrayModule.isActive() && !XrayModule.showsFluids() ? Fluids.EMPTY.getDefaultState() : state;
    }
}
