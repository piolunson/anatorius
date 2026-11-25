package dev.project.anatorius.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import dev.project.anatorius.events.Event;

@Getter
@AllArgsConstructor
public class MouseInputEvent extends Event {
    private final int button, action;
}
