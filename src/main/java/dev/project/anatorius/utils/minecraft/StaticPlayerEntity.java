package dev.project.anatorius.utils.minecraft;

import com.mojang.authlib.GameProfile;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.mixins.accessors.LivingEntityAccessor;
import dev.project.anatorius.mixins.accessors.LimbAnimatorAccessor;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.graphics.ModelRenderer;
import dev.project.anatorius.utils.mixins.ILivingEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;

import java.awt.*;
import java.util.UUID;

public class StaticPlayerEntity extends OtherClientPlayerEntity implements IMinecraft {
    public StaticPlayerEntity(PlayerEntity player) {
        super(mc.world, new GameProfile(UUID.randomUUID(), player.getName().getString()));

        ((ILivingEntity) this).anatorius$setStaticPlayerEntity(true);

        copyFrom(player);

        copyPositionAndRotation(player);
        lastYaw = getYaw();
        lastPitch = getPitch();
        headYaw = player.headYaw;
        lastHeadYaw = headYaw;
        bodyYaw = player.bodyYaw;
        lastBodyYaw = bodyYaw;

        setSneaking(player.isSneaking());

        ((LivingEntityAccessor) this).setLeaningPitch(((LivingEntityAccessor) player).getLeaningPitch());
        ((LivingEntityAccessor) this).setLastLeaningPitch(((LivingEntityAccessor) player).getLastLeaningPitch());

        limbAnimator.setSpeed(player.limbAnimator.getSpeed());
        ((LimbAnimatorAccessor) limbAnimator).setPos(player.limbAnimator.getAnimationProgress());

        setPose(player.getPose());
    }

    public void render(RenderWorldEvent event, boolean fill, Color fillColor, boolean outline, Color outlineColor) {
        ModelRenderer.renderModel(this, true, 1.0f, event.getTickDelta(), new ModelRenderer.Render(fill, fillColor, outline, outlineColor, false));
    }
}
