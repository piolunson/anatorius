package dev.project.anatorius.events.impl;

import lombok.*;
import dev.project.anatorius.events.Event;

@EqualsAndHashCode(callSuper = true) @Data
public class KeyInputEvent extends Event {
    private final int key, modifiers, action;
}
