package dev.project.anatorius.mixins.accessors;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPlayerEntity.class)
public interface ClientPlayerEntityAccessor {
    @Invoker("canSprint")
    boolean invokeCanSprint(boolean allowTouchingWater);

    @Accessor("lastOnGround")
    void setLastOnGround(boolean lastOnGround);

    @Accessor("lastSprinting")
    boolean getLastSprinting();
}
