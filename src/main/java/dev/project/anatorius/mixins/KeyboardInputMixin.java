package dev.project.anatorius.mixins;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.KeyboardTickEvent;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At(value = "TAIL"))
    private void tick$TAIL(CallbackInfo info) {
        KeyboardTickEvent event = new KeyboardTickEvent(movementVector.y, movementVector.x);
        Anatorius.EVENT_HANDLER.post(event);
        if (event.isCancelled()) {
            this.movementVector = new net.minecraft.util.math.Vec2f(event.getMovementSideways(), event.getMovementForward());
        }
        if (event.getOverrideInput() != null) this.playerInput = event.getOverrideInput();
        if (event.isForceJump() && !this.playerInput.jump()) {
            net.minecraft.util.PlayerInput in = this.playerInput;
            this.playerInput = new net.minecraft.util.PlayerInput(in.forward(), in.backward(), in.left(), in.right(), true, in.sneak(), in.sprint());
        }
        if (event.isForceSneak() && !this.playerInput.sneak()) {
            net.minecraft.util.PlayerInput in = this.playerInput;
            this.playerInput = new net.minecraft.util.PlayerInput(in.forward(), in.backward(), in.left(), in.right(), in.jump(), true, in.sprint());
        }
        if (event.isReleaseJump() && this.playerInput.jump()) {
            net.minecraft.util.PlayerInput in = this.playerInput;
            this.playerInput = new net.minecraft.util.PlayerInput(in.forward(), in.backward(), in.left(), in.right(), false, in.sneak(), in.sprint());
        }
        if (event.isForceSprint() && !this.playerInput.sprint()) {
            net.minecraft.util.PlayerInput in = this.playerInput;
            this.playerInput = new net.minecraft.util.PlayerInput(in.forward(), in.backward(), in.left(), in.right(), in.jump(), in.sneak(), true);
        }
        if (event.isForceForward()) {
            this.movementVector = new net.minecraft.util.math.Vec2f(this.movementVector.x, 1.0f);
            net.minecraft.util.PlayerInput in = this.playerInput;
            this.playerInput = new net.minecraft.util.PlayerInput(true, in.backward(), in.left(), in.right(), in.jump(), in.sneak(), in.sprint());
        }
        if (event.isResetInput()) {
            this.movementVector = net.minecraft.util.math.Vec2f.ZERO;
            this.playerInput = net.minecraft.util.PlayerInput.DEFAULT;
        }
    }
}
