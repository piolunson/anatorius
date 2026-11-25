package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.EntitySpawnEvent;
import dev.project.anatorius.modules.impl.visuals.AtmosphereModule;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(method = "addEntity", at = @At(value = "HEAD"))
    private void addEntity(Entity entity, CallbackInfo info) {
        Anatorius.EVENT_HANDLER.post(new EntitySpawnEvent(entity));
    }

    @Inject(method = "tickTime", at = @At("HEAD"))
    private void tickTime$head(CallbackInfo info) {
        AtmosphereModule.readingRealTime = true;
    }

    @Inject(method = "tickTime", at = @At("RETURN"))
    private void tickTime$return(CallbackInfo info) {
        AtmosphereModule.readingRealTime = false;
    }
}
