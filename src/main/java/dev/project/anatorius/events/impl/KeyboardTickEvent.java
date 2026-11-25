package dev.project.anatorius.events.impl;

import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.events.Event;

@Getter @Setter
public class KeyboardTickEvent extends Event {
    private float movementForward;
    private float movementSideways;
    private boolean forceJump;
    private boolean forceSneak;
    private boolean forceForward;
    private boolean releaseJump;
    private boolean forceSprint;
    private boolean resetInput;
    private net.minecraft.util.PlayerInput overrideInput;

    public KeyboardTickEvent(float movementForward, float movementSideways) {
        this.movementForward = movementForward;
        this.movementSideways = movementSideways;
    }
}
