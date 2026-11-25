package dev.project.anatorius.mixins.accessors;

import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
    @Accessor("specialModelType")
    SpecialModelRenderer<Object> getSpecialModelType();

    @Accessor("data")
    Object getData();

    @Accessor("glint")
    ItemRenderState.Glint getGlint();

    @Accessor("tints")
    int[] getTints();

    @Accessor("transform")
    net.minecraft.client.render.model.json.Transformation getTransform();
}
