package dev.project.anatorius.mixins;

import dev.project.anatorius.utils.mixins.ILightningColor;
import net.minecraft.client.render.entity.state.LightningEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LightningEntityRenderState.class)
public class LightningEntityRenderStateMixin implements ILightningColor {
    @Unique private int anatorius$color;

    @Override
    public int anatorius$getColor() {
        return anatorius$color;
    }

    @Override
    public void anatorius$setColor(int argb) {
        anatorius$color = argb;
    }
}
