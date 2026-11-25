package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.AttackBlockEvent;
import dev.project.anatorius.events.impl.AttackEntityEvent;
import dev.project.anatorius.events.impl.BreakBlockEvent;
import dev.project.anatorius.modules.impl.player.NoInteractModule;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void attackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> info) {
        AttackBlockEvent event = new AttackBlockEvent(pos, direction);
        Anatorius.EVENT_HANDLER.post(event);
        if (event.isCancelled()) {
            info.setReturnValue(false);
        }
    }

    @Inject(method = "interactBlock", at = @At(value = "HEAD"), cancellable = true)
    private void interactBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> info) {
        NoInteractModule noInteractModule = Anatorius.MODULE_MANAGER.getModule(NoInteractModule.class);
        if (noInteractModule.isToggled() && noInteractModule.shouldNoInteract() && noInteractModule.mode.getValue().equalsIgnoreCase("Disable") && WorldUtils.RIGHT_CLICKABLE_BLOCKS.contains(client.world.getBlockState(hitResult.getBlockPos()).getBlock())) {
            info.setReturnValue(ActionResult.FAIL);
            return;
        }
        dev.project.anatorius.modules.impl.movement.NoSlowModule noSlow = dev.project.anatorius.Anatorius.MODULE_MANAGER.getModule(dev.project.anatorius.modules.impl.movement.NoSlowModule.class);
        if (noSlow != null && noSlow.eatsInsteadOfUsingOn(hand)) {
            ActionResult result = ((net.minecraft.client.network.ClientPlayerInteractionManager) (Object) this).interactItem(player, hand);
            info.setReturnValue(result instanceof ActionResult.Pass ? ActionResult.FAIL : result);
        }
    }

    @Inject(method = "interactBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V"))
    private void interactBlock$BEFORE(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> cir) {
        NoInteractModule noInteractModule = Anatorius.MODULE_MANAGER.getModule(NoInteractModule.class);
        if (!client.player.isSneaking() && noInteractModule.isToggled() && noInteractModule.shouldNoInteract() && noInteractModule.mode.getValue().equalsIgnoreCase("Sneak") && WorldUtils.RIGHT_CLICKABLE_BLOCKS.contains(client.world.getBlockState(hitResult.getBlockPos()).getBlock())) {
        }
    }

    @Inject(method = "interactBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;sendSequencedPacket(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/client/network/SequencedPacketCreator;)V", shift = At.Shift.AFTER))
    private void interactBlock$AFTER(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> info) {
        NoInteractModule noInteractModule = Anatorius.MODULE_MANAGER.getModule(NoInteractModule.class);
        if (!client.player.isSneaking() && noInteractModule.isToggled() && noInteractModule.shouldNoInteract() && noInteractModule.mode.getValue().equalsIgnoreCase("Sneak") && WorldUtils.RIGHT_CLICKABLE_BLOCKS.contains(client.world.getBlockState(hitResult.getBlockPos()).getBlock())) {
        }
    }

    @Inject(method = "clickSlot", at = @At("HEAD"))
    private void clickSlot$head(int syncId, int slotId, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        dev.project.anatorius.modules.impl.movement.FakeFlyModule.recordClick();
        dev.project.anatorius.modules.impl.miscellaneous.SwapDebugModule.logClick(syncId, slotId, button, actionType);
    }

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void interactItem$fakeFlyRocket(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (dev.project.anatorius.modules.impl.movement.FakeFlyModule.holdsRocketUse(hand)) cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void attackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        Anatorius.EVENT_HANDLER.post(new AttackEntityEvent(player, target));
    }

    @Inject(method = "attackEntity", at = @At("RETURN"))
    private void attackEntityReturn(PlayerEntity player, Entity target, CallbackInfo ci) {
        Anatorius.EVENT_HANDLER.post(new AttackEntityEvent.Post(player, target));
    }

    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void breakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Anatorius.EVENT_HANDLER.post(new BreakBlockEvent(pos));
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "breakBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z"))
    private boolean noGlitchBreak(net.minecraft.world.World world, BlockPos pos, net.minecraft.block.BlockState state, int flags, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> original) {
        if (dev.project.anatorius.modules.impl.player.NoGlitchBlocksModule.blocksBreakPrediction()) return false;
        return original.call(world, pos, state, flags);
    }
}
