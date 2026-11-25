package dev.project.anatorius.mixins;

import dev.project.anatorius.modules.impl.visuals.XrayModule;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public class SodiumLevelSliceMixin {
    @Inject(method = {"getBlockState(III)Lnet/minecraft/class_2680;", "getBlockState(III)Lnet/minecraft/block/BlockState;"}, at = @At("RETURN"), cancellable = true, require = 0)
    private void xrayBlock(int x, int y, int z, CallbackInfoReturnable<BlockState> info) {
        if (!XrayModule.isActive()) return;
        BlockState state = info.getReturnValue();
        if (state == null || state.isAir()) return;
        BlockState shown = XrayModule.filter(state, new BlockPos(x, y, z));
        if (shown != state) info.setReturnValue(shown);
    }
}
