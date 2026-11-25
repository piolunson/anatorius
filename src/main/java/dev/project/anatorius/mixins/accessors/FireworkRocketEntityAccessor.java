package dev.project.anatorius.mixins.accessors;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public interface FireworkRocketEntityAccessor {
    @Accessor("shooter")
    LivingEntity anatorius$getShooter();

    @Accessor("life")
    int anatorius$getLife();
}
