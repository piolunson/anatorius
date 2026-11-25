package dev.project.anatorius.settings.impl;

import dev.project.anatorius.Anatorius;
import lombok.Setter;
import dev.project.anatorius.events.impl.SettingChangeEvent;
import dev.project.anatorius.settings.Setting;

@Setter
public class BooleanSetting extends Setting {
    private boolean value;
    private final boolean defaultValue;

    public BooleanSetting(String name, String description, boolean value) {
        super(name, name, description, new Setting.Visibility());
        this.value = value;
        this.defaultValue = value;
    }

    public BooleanSetting(String name, String tag, String description, boolean value) {
        super(name, tag, description, new Setting.Visibility());
        this.value = value;
        this.defaultValue = value;
    }

    public BooleanSetting(String name, String description, Setting.Visibility visibility, boolean value) {
        super(name, name, description, visibility);
        this.value = value;
        this.defaultValue = value;
    }

    public BooleanSetting(String name, String tag, String description, Setting.Visibility visibility, boolean value) {
        super(name, tag, description, visibility);
        this.value = value;
        this.defaultValue = value;
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
        Anatorius.EVENT_HANDLER.post(new SettingChangeEvent(this));
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }

    public void resetValue() {
        value = defaultValue;
    }

    public static class Visibility extends Setting.Visibility {
        private final BooleanSetting value;
        private final boolean targetValue;

        public Visibility(BooleanSetting value, boolean targetValue) {
            super(value);
            this.value = value;
            this.targetValue = targetValue;
        }

        @Override
        public void update() {
            if (value.getVisibility() != null) {
                value.getVisibility().update();
                if (!value.getVisibility().isVisible()) {
                    setVisible(false);
                    return;
                }
            }

            setVisible(value.getValue() == targetValue);
        }

        @Override
        public float getProgress() {
            float p = super.getProgress();
            if (value.getVisibility() != null) {
                p *= value.getVisibility().getProgress();
            }
            return p;
        }
    }
}
