package dev.project.anatorius.settings.impl;

import lombok.Getter;
import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.impl.SettingChangeEvent;
import dev.project.anatorius.settings.Setting;

import java.util.Arrays;
import java.util.List;

@Getter
public class ModeSetting extends Setting {
    private String value;
    private final String defaultValue;
    private final List<String> modes;

    public ModeSetting(String name, String description, String value, String[] modes) {
        super(name, name, description, new Setting.Visibility());
        this.value = value;
        this.defaultValue = value;
        this.modes = Arrays.asList(modes);
    }

    public ModeSetting(String name, String tag, String description, String value, String[] modes) {
        super(name, tag, description, new Setting.Visibility());
        this.value = value;
        this.defaultValue = value;
        this.modes = Arrays.asList(modes);
    }

    public ModeSetting(String name, String description, Setting.Visibility visibility, String value, String[] modes) {
        super(name, name, description, visibility);
        this.value = value;
        this.defaultValue = value;
        this.modes = Arrays.asList(modes);
    }

    public ModeSetting(String name, String tag, String description, Setting.Visibility visibility, String value, String[] modes) {
        super(name, tag, description, visibility);
        this.value = value;
        this.defaultValue = value;
        this.modes = Arrays.asList(modes);
    }

    public void setValue(String value) {
        if (value == null) return;

        if (modes.contains(value)) {
            this.value = value;
            Anatorius.EVENT_HANDLER.post(new SettingChangeEvent(this));
            return;
        }

        String normalized = value.replace(" ", "").toLowerCase();

        for (String mode : modes) {
            if (mode.equalsIgnoreCase(value)) {
                this.value = mode;
                Anatorius.EVENT_HANDLER.post(new SettingChangeEvent(this));
                return;
            }

            String modeNormalized = mode.replace(" ", "").toLowerCase();
            if (modeNormalized.equals(normalized)) {
                this.value = mode;
                Anatorius.EVENT_HANDLER.post(new SettingChangeEvent(this));
                return;
            }
        }

        if (normalized.equals("plain")) {
            for (String mode : modes) {
                if (mode.replace(" ", "").equalsIgnoreCase("regular")) {
                    this.value = mode;
                    Anatorius.EVENT_HANDLER.post(new SettingChangeEvent(this));
                    return;
                }
            }
        }

        if (normalized.equals("bolditalic")) {
            for (String mode : modes) {
                if (mode.replace(" ", "").equalsIgnoreCase("bolditalic")) {
                    this.value = mode;
                    Anatorius.EVENT_HANDLER.post(new SettingChangeEvent(this));
                    return;
                }
            }
        }

        return;
    }

    public void resetValue() {
        value = defaultValue;
    }

    public static class Visibility extends Setting.Visibility {
        private final ModeSetting value;
        private final List<String> targetValues;

        public Visibility(ModeSetting value, String... targetValues) {
            super(value);
            this.value = value;
            this.targetValues = Arrays.asList(targetValues);
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

            boolean visible = false;
            for (String value : targetValues) {
                if (this.value.getValue().equals(value)) {
                    visible = true;
                    break;
                }
            }

            setVisible(visible);
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
