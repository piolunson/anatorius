package dev.project.anatorius.mixins;

import dev.project.anatorius.modules.impl.visuals.Items3DModule;
import dev.project.anatorius.utils.items3d.Items3DRenderer;
import dev.project.anatorius.utils.mixins.ICrystalItemState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderState.class)
public class ItemRenderStateMixin implements ICrystalItemState {
    @Unique private boolean crystal;

    @Override
    public boolean anatorius$isCrystal() {
        return crystal;
    }

    @Override
    public void anatorius$setCrystal(boolean crystal) {
        this.crystal = crystal;
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void clear$items3d(CallbackInfo ci) {
        crystal = false;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void render$items3d(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay, int outlineColor, CallbackInfo ci) {
        if (!crystal) return;
        Items3DModule m = Items3DModule.active();
        if (m != null && Items3DRenderer.INSTANCE.renderCrystalState(m, (ItemRenderState) (Object) this, matrices, queue, light)) ci.cancel();
    }
}
