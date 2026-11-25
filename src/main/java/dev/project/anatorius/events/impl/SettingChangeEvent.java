package dev.project.anatorius.events.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import dev.project.anatorius.events.Event;
import dev.project.anatorius.settings.Setting;

@Getter @AllArgsConstructor
public class SettingChangeEvent extends Event {
    private final Setting setting;
}
