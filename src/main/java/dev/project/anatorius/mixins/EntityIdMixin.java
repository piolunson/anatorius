package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(Entity.class)
public class EntityIdMixin {
    @Unique private static final AtomicInteger CLIENT_IDS = new AtomicInteger(-1_000_000);

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicInteger;incrementAndGet()I"))
    private int anatorius$clientIds(AtomicInteger counter, Operation<Integer> original, @Local(argsOnly = true) World world) {
        if (world != null && world.isClient()) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.isIntegratedServerRunning()) return CLIENT_IDS.decrementAndGet();
        }
        return original.call(counter);
    }
}
