package dev.project.anatorius.mixins;

import com.mojang.blaze3d.shaders.ShaderType;
import dev.project.anatorius.utils.graphics.ShaderScript;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderLoader.class)
public abstract class ShaderLoaderMixin {
    @Inject(method = "getSource", at = @At("HEAD"), cancellable = true)
    private void anatorius$scriptSource(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
        if (type != ShaderType.FRAGMENT || !ShaderScript.isScriptId(id)) return;
        String template = ((ShaderLoader) (Object) this).getSource(ShaderScript.TEMPLATE, type);
        cir.setReturnValue(ShaderScript.compose(template));
    }
}
