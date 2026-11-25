package dev.project.anatorius.utils.graphics;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.visuals.ShadersModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.util.memory.ObjectPool;

public final class HandChamsRenderer implements IMinecraft {
    public static final HandChamsRenderer INSTANCE = new HandChamsRenderer();

    private final ObjectPool pool = new ObjectPool(3);
    private SimpleFramebuffer silhouette;
    private int fbWidth, fbHeight;
    private boolean captured;

    private HandChamsRenderer() {}

    private void ensureFbo() {
        Framebuffer main = mc.getFramebuffer();
        int w = main.textureWidth, h = main.textureHeight;
        if (silhouette == null || w != fbWidth || h != fbHeight) {
            if (silhouette != null) silhouette.delete();
            pool.clear();
            silhouette = new SimpleFramebuffer("anatorius_hand_chams", w, h, true);
            fbWidth = w;
            fbHeight = h;
        }
    }

    public void beginHandCapture() {
        ensureFbo();
        if (!captured) {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    silhouette.getColorAttachment(), 0, silhouette.getDepthAttachment(), 1.0);
        }
        RenderSystem.outputColorTextureOverride = silhouette.getColorAttachmentView();
        RenderSystem.outputDepthTextureOverride = silhouette.getDepthAttachmentView();
    }

    public void endHandCapture(net.minecraft.client.render.VertexConsumerProvider.Immediate immediate) {
        immediate.draw();
        RenderSystem.outputColorTextureOverride = null;
        RenderSystem.outputDepthTextureOverride = null;
        captured = true;
    }

    public void composite() {
        if (!captured) return;
        captured = false;

        ShadersModule module = Anatorius.MODULE_MANAGER.getModule(ShadersModule.class);
        if (module == null || silhouette == null) return;

        PostEffectProcessor processor = WorldChamsChain.get(DefaultFramebufferSet.MAIN_AND_ENTITY_OUTLINE,
                module, module.getHandBaseRgb(), 1);

        Framebuffer main = mc.getFramebuffer();
        try {
            if (processor != null) {
                FrameGraphBuilder builder = new FrameGraphBuilder();
                DefaultFramebufferSet set = new DefaultFramebufferSet();
                set.mainFramebuffer = builder.createObjectNode("main", main);
                set.entityOutlineFramebuffer = builder.createObjectNode("entity_outline", silhouette);
                processor.render(builder, main.textureWidth, main.textureHeight, set);
                builder.run(pool);
                pool.decrementLifespan();
            }
            silhouette.drawBlit(main.getColorAttachmentView());
        } catch (Throwable t) {
            Anatorius.LOGGER.warn("[Anatorius] hand chams composite failed: {}", t.getMessage());
        }
    }
}
