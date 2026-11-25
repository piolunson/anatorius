package dev.project.anatorius.mixins;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.GameLoopEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.modules.impl.combat.NoHitDelayModule;
import dev.project.anatorius.modules.impl.core.MenuModule;
import dev.project.anatorius.modules.impl.miscellaneous.AutoRespawnModule;
import dev.project.anatorius.modules.impl.player.FastPlaceModule;
import dev.project.anatorius.modules.impl.player.MultiTaskModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.RunArgs;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin implements IMinecraft {
    @Shadow @Nullable public ClientPlayerEntity player;

    @Shadow private int itemUseCooldown;

    @Shadow @Final public GameOptions options;

    @Shadow public int attackCooldown;

    @Shadow public abstract void setScreen(@Nullable Screen screen);

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setOverlay(Lnet/minecraft/client/gui/screen/Overlay;)V", shift = At.Shift.BEFORE))
    private void init(RunArgs args, CallbackInfo info) {
        Anatorius.onPostInitialize();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;runTasks()V", shift = At.Shift.AFTER))
    private void runTickHook(boolean tick, CallbackInfo info) {
        Anatorius.EVENT_HANDLER.post(new GameLoopEvent());
        if (Anatorius.POSITION_MANAGER != null) dev.project.anatorius.utils.minecraft.InventoryUtils.endOfTick();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(CallbackInfo info) {
        Anatorius.EVENT_HANDLER.post(new TickEvent());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void tickEnd(CallbackInfo info) {
        if (Anatorius.POSITION_MANAGER != null) dev.project.anatorius.utils.minecraft.InventoryUtils.endOfTick();
    }

    @com.llamalad7.mixinextras.injector.v2.WrapWithCondition(method = "handleInputEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;stopUsingItem(Lnet/minecraft/entity/player/PlayerEntity;)V"))
    private boolean handleInputEvents$keepShield(net.minecraft.client.network.ClientPlayerInteractionManager manager, net.minecraft.entity.player.PlayerEntity player) {
        return !dev.project.anatorius.modules.impl.combat.AntiMaceModule.holdingShield;
    }

    @Inject(method = "doItemUse", at = @At("HEAD"))
    private void doItemUseHead(CallbackInfo info) {
        dev.project.anatorius.modules.impl.player.NoGlitchBlocksModule.manualUse = true;
    }

    @Inject(method = "doItemUse", at = @At("RETURN"))
    private void doItemUseReturn(CallbackInfo info) {
        dev.project.anatorius.modules.impl.player.NoGlitchBlocksModule.manualUse = false;
    }

    @Inject(method = "doItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isRiding()Z", ordinal = 0, shift = At.Shift.BEFORE))
    private void doItemUse(CallbackInfo info) {
        if (Anatorius.MODULE_MANAGER != null && Anatorius.MODULE_MANAGER.getModule(FastPlaceModule.class).isToggled() && Anatorius.MODULE_MANAGER.getModule(FastPlaceModule.class).isValidItem(player.getMainHandStack().getItem())) {
            itemUseCooldown = Anatorius.MODULE_MANAGER.getModule(FastPlaceModule.class).ticks.getValue().intValue();
        }
    }

    @ModifyExpressionValue(method = "handleBlockBreaking", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean handleBlockBreaking(boolean original) {
        if (Anatorius.MODULE_MANAGER != null && Anatorius.MODULE_MANAGER.getModule(MultiTaskModule.class).isToggled()) return false;
        return original;
    }

    @ModifyExpressionValue(method = "doItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;isBreakingBlock()Z"))
    private boolean handleInputEvents(boolean original) {
        if (Anatorius.MODULE_MANAGER != null && Anatorius.MODULE_MANAGER.getModule(MultiTaskModule.class).isToggled()) return false;
        return original;
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void setScreen(Screen screen, CallbackInfo info) {
        AutoRespawnModule autoRespawnModule = Anatorius.MODULE_MANAGER.getModule(AutoRespawnModule.class);
        if (screen instanceof DeathScreen && player != null && autoRespawnModule != null && autoRespawnModule.isToggled()) {
            player.requestRespawn();
            info.cancel();
        }

        if (screen instanceof TitleScreen) {
            Anatorius.checkForUpdates();
        }
    }

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void doAttack(CallbackInfoReturnable<Boolean> info) {
        if (Anatorius.MODULE_MANAGER != null && Anatorius.MODULE_MANAGER.getModule(NoHitDelayModule.class).isToggled()) {
            attackCooldown = 0;
        }
    }
}
