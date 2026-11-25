package dev.project.anatorius.utils.graphics;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.project.anatorius.mixins.accessors.PostEffectPassAccessor;
import dev.project.anatorius.mixins.accessors.PostEffectProcessorAccessor;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.UniformValue;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public final class PostEffectUniforms {
    private PostEffectUniforms() {}

    public static GpuBuffer makeWritable(PostEffectProcessor processor, int passIndex, String block, List<UniformValue> values) {
        Map<String, GpuBuffer> blocks = ((PostEffectPassAccessor) ((PostEffectProcessorAccessor) processor).getPasses().get(passIndex)).getUniformBuffers();
        GpuBuffer writable;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            writable = RenderSystem.getDevice().createBuffer(() -> "anatorius " + block,
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, pack(stack, values));
        }
        GpuBuffer old = blocks.put(block, writable);
        if (old != null) old.close();
        return writable;
    }

    public static void write(GpuBuffer buffer, List<UniformValue> values) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), pack(stack, values));
        }
    }

    private static ByteBuffer pack(MemoryStack stack, List<UniformValue> values) {
        Std140SizeCalculator size = new Std140SizeCalculator();
        for (UniformValue v : values) v.addSize(size);
        Std140Builder builder = Std140Builder.onStack(stack, size.get());
        for (UniformValue v : values) v.write(builder);
        return builder.get();
    }
}
