package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.impl.visuals.FreeLookModule;
import dev.project.anatorius.modules.impl.visuals.FreecamModule;
import dev.project.anatorius.modules.impl.visuals.NoRenderModule;
import dev.project.anatorius.modules.impl.visuals.ViewClipModule;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public class CameraMixin {
    @Shadow private boolean thirdPerson;

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"))
    private void update(Args args) {
        ViewClipModule viewClip = Anatorius.MODULE_MANAGER.getModule(ViewClipModule.class);
        if (viewClip.isToggled() && viewClip.extend.getValue()) {
            float targetDistance = viewClip.distance.getValue().floatValue();

            MinecraftClient mc = MinecraftClient.getInstance();
            boolean isThirdPersonBack = mc.options.getPerspective() == Perspective.THIRD_PERSON_BACK;
            viewClip.updateCurrentDistance(targetDistance, isThirdPersonBack);

            args.set(0, viewClip.getCurrentDistance());
        }
    }

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void clipToSpace(float f, CallbackInfoReturnable<Float> info) {
        if (Anatorius.MODULE_MANAGER.getModule(ViewClipModule.class).isToggled()) {
            info.setReturnValue(f);
        }
    }

    @Inject(method = "getSubmersionType", at = @At("HEAD"), cancellable = true)
    private void getSubmersionType(CallbackInfoReturnable<CameraSubmersionType> info) {
        if (Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(NoRenderModule.class).liquidOverlay.getValue()) {
            info.setReturnValue(CameraSubmersionType.NONE);
        }
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void update$TAIL(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo info) {
        if (Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).isToggled()) {
            this.thirdPerson = true;
        }
    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void update$setRotation(Args args) {
        if (Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).isToggled()) {
            args.setAll(Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).getFreeYaw(), Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).getFreePitch());
        } else if (Anatorius.MODULE_MANAGER.getModule(FreeLookModule.class).isToggled()) {
            FreeLookModule module = Anatorius.MODULE_MANAGER.getModule(FreeLookModule.class);
            args.setAll(module.getCameraYaw(), module.getCameraPitch());
        }
    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V"))
    private void update$setPos(Args args) {
        if (Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).isToggled()) {
            args.setAll(Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).getFreeX(), Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).getFreeY(), Anatorius.MODULE_MANAGER.getModule(FreecamModule.class).getFreeZ());
        }
    }
}
