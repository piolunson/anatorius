package dev.project.anatorius.utils.graphics;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.SpriteAtlasTexture;

public final class EspLayers {
    private EspLayers() {}

    private static final RenderPipeline ESP_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation("pipeline/anatorius_esp_lines")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    private static final RenderPipeline ESP_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation("pipeline/anatorius_esp_quads")
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    public static final RenderLayer LINES = RenderLayer.of(
            "anatorius_esp_lines",
            RenderSetup.builder(ESP_LINES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .build());

    public static final RenderLayer QUADS = RenderLayer.of(
            "anatorius_esp_quads",
            RenderSetup.builder(ESP_QUADS_PIPELINE).translucent().build());

    private static final RenderPipeline SHINE_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation("pipeline/anatorius_shine_quads")
                    .withCull(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    private static final RenderPipeline SHINE_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation("pipeline/anatorius_shine_lines")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    public static final RenderLayer SHINE_QUADS = RenderLayer.of(
            "anatorius_shine_quads",
            RenderSetup.builder(SHINE_QUADS_PIPELINE).build());

    public static final RenderLayer SHINE_LINES = RenderLayer.of(
            "anatorius_shine_lines",
            RenderSetup.builder(SHINE_LINES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .build());

    private static final RenderPipeline DEPTH_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation("pipeline/anatorius_depth_quads")
                    .withCull(false)
                    .withDepthWrite(false)
                    .withDepthBias(-1.0F, -10.0F)
                    .build());

    private static final RenderPipeline DEPTH_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation("pipeline/anatorius_depth_lines")
                    .withDepthWrite(false)
                    .withDepthBias(-1.0F, -10.0F)
                    .build());

    private static final RenderPipeline DEPTH_SHINE_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation("pipeline/anatorius_depth_shine_quads")
                    .withCull(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthWrite(false)
                    .withDepthBias(-1.0F, -10.0F)
                    .build());

    private static final RenderPipeline DEPTH_SHINE_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation("pipeline/anatorius_depth_shine_lines")
                    .withBlend(BlendFunction.LIGHTNING)
                    .withDepthWrite(false)
                    .withDepthBias(-1.0F, -10.0F)
                    .build());

    public static final RenderLayer DEPTH_QUADS = RenderLayer.of(
            "anatorius_depth_quads",
            RenderSetup.builder(DEPTH_QUADS_PIPELINE).translucent().build());

    public static final RenderLayer DEPTH_LINES = RenderLayer.of(
            "anatorius_depth_lines",
            RenderSetup.builder(DEPTH_LINES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .build());

    public static final RenderLayer DEPTH_SHINE_QUADS = RenderLayer.of(
            "anatorius_depth_shine_quads",
            RenderSetup.builder(DEPTH_SHINE_QUADS_PIPELINE).build());

    public static final RenderLayer DEPTH_SHINE_LINES = RenderLayer.of(
            "anatorius_depth_shine_lines",
            RenderSetup.builder(DEPTH_SHINE_LINES_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .build());

    private static final RenderPipeline ITEM_NODEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_LIGHTING_SNIPPET)
                    .withLocation("pipeline/anatorius_item_nodepth")
                    .withVertexShader("core/rendertype_item_entity_translucent_cull")
                    .withFragmentShader("core/rendertype_item_entity_translucent_cull")
                    .withSampler("Sampler0")
                    .withSampler("Sampler2")
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build());

    public static final RenderLayer ITEM_NODEPTH = RenderLayer.of(
            "anatorius_item_nodepth",
            RenderSetup.builder(ITEM_NODEPTH_PIPELINE)
                    .texture("Sampler0", SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                    .useLightmap()
                    .useOverlay()
                    .build());

    public static void init() {}
}
