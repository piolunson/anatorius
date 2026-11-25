package dev.project.anatorius.mixins;

import dev.project.anatorius.modules.impl.movement.EntityControlModule;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(AbstractBoatEntity.class)
public abstract class AbstractBoatEntityMixin extends Entity {
    public AbstractBoatEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/AbstractBoatEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"), index = 1)
    private Vec3d tick$entityControl(Vec3d motion) {
        Vec3d controlled = EntityControlModule.vehicleMotion(this, motion);
        if (controlled == null) return motion;
        this.setVelocity(controlled);
        return controlled;
    }
}
