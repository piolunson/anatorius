package dev.project.anatorius.settings.impl;

import dev.project.anatorius.Anatorius;
import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.events.impl.SettingChangeEvent;
import dev.project.anatorius.settings.Setting;

@Getter @Setter
public class BindSetting extends Setting {
    private int value;
    private final int defaultValue;
    private BindMode mode;
    private final BindMode defaultMode;

    public BindSetting(String name, String description, int value) {
        super(name, name, description, new Setting.Visibility());
        this.value = value;
        this.defaultValue = value;
        this.mode = BindMode.TOGGLE;
        this.defaultMode = BindMode.TOGGLE;
    }

    public BindSetting(String name, String tag, String description, int value) {
        super(name, tag, description, new Setting.Visibility());
        this.value = value;
        this.defaultValue = value;
        this.mode = BindMode.TOGGLE;
        this.defaultMode = BindMode.TOGGLE;
    }

    public BindSetting(String name, String description, Setting.Visibility visibility, int value) {
        super(name, name, description, visibility);
        this.value = value;
        this.defaultValue = value;
        this.mode = BindMode.TOGGLE;
        this.defaultMode = BindMode.TOGGLE;
    }

    public BindSetting(String name, String tag, String description, Setting.Visibility visibility, int value) {
        super(name, tag, description, visibility);
        this.value = value;
        this.defaultValue = value;
        this.mode = BindMode.TOGGLE;
        this.defaultMode = BindMode.TOGGLE;
    }

    public void resetValue() {
        value = defaultValue;
        mode = defaultMode;
    }

    public void setValue(int value) {
        this.value = value;
        Anatorius.EVENT_HANDLER.post(new SettingChangeEvent(this));
    }

    public void setMode(BindMode mode) {
        this.mode = mode;
        Anatorius.EVENT_HANDLER.post(new SettingChangeEvent(this));
    }

    public void nextMode() {
        BindMode[] modes = BindMode.values();
        int nextIndex = (mode.ordinal() + 1) % modes.length;
        setMode(modes[nextIndex]);
    }

    public enum BindMode {
        TOGGLE("Toggle"),
        HOLD("Hold"),
        REVERSEHOLD("ReverseHold");

        @Getter
        private final String name;

        BindMode(String name) {
            this.name = name;
        }
    }

    public static class Visibility extends Setting.Visibility {
        private final BindSetting value;
        private final int targetValue;

        public Visibility(BindSetting value, int targetValue) {
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
            return isVisible() && value.getVisibility() != null ? value.getVisibility().getProgress() : super.getProgress();
        }
    }
}
