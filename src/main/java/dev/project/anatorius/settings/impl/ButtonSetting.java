package dev.project.anatorius.settings.impl;

import dev.project.anatorius.settings.Setting;
import lombok.Getter;

@Getter
public class ButtonSetting extends Setting {
    private final Runnable action;

    public ButtonSetting(String name, String description, Runnable action) {
        super(name, name, description, new Setting.Visibility());
        this.action = action;
    }

    public ButtonSetting(String name, String tag, String description, Runnable action) {
        super(name, tag, description, new Setting.Visibility());
        this.action = action;
    }

    public void press() {
        if (action != null) action.run();
    }
}
