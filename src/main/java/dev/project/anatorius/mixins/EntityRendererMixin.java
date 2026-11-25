package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.visuals.NameTagsModule;
import dev.project.anatorius.modules.impl.visuals.ShadersModule;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void getDisplayName(T entity, CallbackInfoReturnable<Text> info) {
        if (entity instanceof PlayerEntity && Anatorius.MODULE_MANAGER.getModule(NameTagsModule.class).isToggled()) {
            info.setReturnValue(null);
        }
    }

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void updateRenderState$shaderOutline(T entity, S state, float tickProgress, CallbackInfo info) {
        if (Anatorius.MODULE_MANAGER == null) return;
        ShadersModule module = Anatorius.MODULE_MANAGER.getModule(ShadersModule.class);
        if (module == null || !module.wantsChams()) return;
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (entity == mc.player && mc.options.getPerspective().isFirstPerson()) return;
        if (!module.isValidEntity(entity)) return;

        state.outlineColor = ColorHelper.fullAlpha(module.getRgbFor(entity));
    }
}
