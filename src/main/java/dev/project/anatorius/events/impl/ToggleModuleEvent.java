package dev.project.anatorius.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import dev.project.anatorius.events.Event;
import dev.project.anatorius.modules.Module;

@AllArgsConstructor @Getter
public class ToggleModuleEvent extends Event {
    private final Module module;
    private final boolean state;
}
