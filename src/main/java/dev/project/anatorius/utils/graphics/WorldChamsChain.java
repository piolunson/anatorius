package dev.project.anatorius.utils.graphics;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.mixins.accessors.PostEffectPassAccessor;
import dev.project.anatorius.mixins.accessors.PostEffectProcessorAccessor;
import dev.project.anatorius.modules.impl.visuals.ShadersModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.UniformValue;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class WorldChamsChain {
    private static final Identifier SCREENQUAD = Identifier.ofVanilla("core/screenquad");
    private static final Identifier H_FSH = Identifier.of("anatorius", "post/chams_default_h");
    private static final Identifier V_FSH = Identifier.of("anatorius", "post/chams_default_v");
    private static final Identifier BLIT_FSH = Identifier.of("anatorius", "post/chams_blit");
    private static final Identifier CHAMS_H = Identifier.of("anatorius", "chams_h");
    private static final Identifier CHAMS_V = Identifier.of("anatorius", "chams_v");
    private static final Identifier CHAIN_NAME = Identifier.of("anatorius", "chams_default_runtime");

    private static final int LINE_WIDTH = 2;

    private static ProjectionMatrix2 projection;

    private static final Cache WORLD = new Cache();
    private static final Cache HAND = new Cache();

    private static final class Cache {
        PostEffectProcessor processor;
        Set<Identifier> targets;
        Identifier vShader;
        GpuBuffer dilate, chams;
        List<UniformValue> lastDilate, lastChams;
    }

    private WorldChamsChain() {}

    public static PostEffectProcessor get(Set<Identifier> externalTargets, ShadersModule m, int colorA, int flatFill) {
        Cache c = flatFill == 1 ? HAND : WORLD;
        int glowRadius = m.getGlowRadius();
        int colorB = m.getColorB();
        int outlineColorRgb = m.getOutlineColorRgb();
        int outlineB = m.getOutlineColorB();
        int glowStep = m.getGlowStep();
        List<UniformValue> dilate = List.of(integer(LINE_WIDTH), integer(glowRadius), integer(glowStep));
        List<UniformValue> chams = List.of(
                flt(m.getGlowIntensity()), flt(m.getFillTint()), flt(m.getFillAlpha()), integer(glowRadius), integer(LINE_WIDTH),
                integer(m.getThemeIndex()),
                flt(((colorA >> 16) & 0xFF) / 255.0f), flt(((colorA >> 8) & 0xFF) / 255.0f), flt((colorA & 0xFF) / 255.0f),
                flt(((colorB >> 16) & 0xFF) / 255.0f), flt(((colorB >> 8) & 0xFF) / 255.0f), flt((colorB & 0xFF) / 255.0f),
                flt(((outlineColorRgb >> 16) & 0xFF) / 255.0f), flt(((outlineColorRgb >> 8) & 0xFF) / 255.0f), flt((outlineColorRgb & 0xFF) / 255.0f),
                flt(m.getGlobalAlpha()), integer(m.getRenderMode()), integer(flatFill), integer(m.getGlowInside()),
                flt(m.getClock()), flt(m.getViewX()), flt(m.getViewY()), flt(m.getPatternScale()),
                flt(((outlineB >> 16) & 0xFF) / 255.0f), flt(((outlineB >> 8) & 0xFF) / 255.0f), flt((outlineB & 0xFF) / 255.0f),
                flt(m.getFillOffset()), flt(m.getFillStrength()), flt(m.getOutlineOffset()), flt(m.getOutlineStrength()),
                integer(m.getDotsMode()), integer(m.getDotsRadius()), flt(m.getDotsAlpha()), integer(glowStep));

        Identifier vShader = V_FSH;
        if (m.isScriptTheme()) {
            Identifier script = ShaderScript.currentId();
            if (script != null) vShader = script;
        }

        boolean live = c.chams != null;
        boolean changed = !dilate.equals(c.lastDilate) || !chams.equals(c.lastChams);
        if (c.processor == null || !externalTargets.equals(c.targets) || !vShader.equals(c.vShader) || (!live && changed)) {
            PostEffectProcessor rebuilt = build(externalTargets, dilate, chams, vShader);
            if (rebuilt != null && !vShader.equals(V_FSH) && !compiled(rebuilt, 1)) {
                ShaderScript.markBroken(vShader);
                rebuilt.close();
                vShader = V_FSH;
                rebuilt = build(externalTargets, dilate, chams, vShader);
            }
            if (rebuilt == null) return c.processor;
            if (c.processor != null) c.processor.close();
            c.processor = rebuilt;
            c.targets = externalTargets;
            c.vShader = vShader;
            c.lastDilate = dilate;
            c.lastChams = chams;
            try {
                c.dilate = PostEffectUniforms.makeWritable(rebuilt, 0, "DilateConfig", dilate);
                c.chams = PostEffectUniforms.makeWritable(rebuilt, 1, "ChamsConfig", chams);
            } catch (Throwable t) {
                c.dilate = c.chams = null;
                Anatorius.LOGGER.warn("[Anatorius] chams live uniforms unavailable, rebuilding on change: {}", t.toString());
            }
            return rebuilt;
        }

        if (changed) {
            if (!dilate.equals(c.lastDilate)) {
                PostEffectUniforms.write(c.dilate, dilate);
                c.lastDilate = dilate;
            }
            if (!chams.equals(c.lastChams)) {
                PostEffectUniforms.write(c.chams, chams);
                c.lastChams = chams;
            }
        }
        return c.processor;
    }

    private static boolean compiled(PostEffectProcessor processor, int passIndex) {
        try {
            RenderPipeline pipeline = ((PostEffectPassAccessor) ((PostEffectProcessorAccessor) processor).getPasses().get(passIndex)).getPipeline();
            return RenderSystem.getDevice().precompilePipeline(pipeline).isValid();
        } catch (Throwable t) {
            return false;
        }
    }

    private static PostEffectProcessor build(Set<Identifier> externalTargets, List<UniformValue> dilate, List<UniformValue> chamsConfig, Identifier vShader) {
        try {
            if (projection == null) {
                projection = new ProjectionMatrix2("anatorius_chams", 0.1f, 1000.0f, false);
            }

            Identifier outlineTarget = DefaultFramebufferSet.ENTITY_OUTLINE;

            PostEffectPipeline.Pass passH = new PostEffectPipeline.Pass(
                    SCREENQUAD, H_FSH,
                    List.of(new PostEffectPipeline.TargetSampler("In", outlineTarget, false, false)),
                    CHAMS_H,
                    Map.of("DilateConfig", dilate));

            PostEffectPipeline.Pass passV = new PostEffectPipeline.Pass(
                    SCREENQUAD, vShader,
                    List.of(new PostEffectPipeline.TargetSampler("In", CHAMS_H, false, false),
                            new PostEffectPipeline.TargetSampler("Orig", outlineTarget, false, false)),
                    CHAMS_V,
                    Map.of("ChamsConfig", chamsConfig));

            PostEffectPipeline.Pass passBlit = new PostEffectPipeline.Pass(
                    SCREENQUAD, BLIT_FSH,
                    List.of(new PostEffectPipeline.TargetSampler("In", CHAMS_V, false, false)),
                    outlineTarget,
                    Map.of());

            PostEffectPipeline pipeline = new PostEffectPipeline(
                    Map.of(CHAMS_H, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0),
                            CHAMS_V, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0)),
                    List.of(passH, passV, passBlit));

            return PostEffectProcessor.parseEffect(pipeline, MinecraftClient.getInstance().getTextureManager(),
                    externalTargets, CHAIN_NAME, projection);
        } catch (Throwable t) {
            Anatorius.LOGGER.warn("[Anatorius] chams chain build failed: {}", t.getMessage());
            return null;
        }
    }

    private static UniformValue flt(float v) {
        return new UniformValue.FloatValue(v);
    }

    private static UniformValue integer(int v) {
        return new UniformValue.IntValue(v);
    }
}
