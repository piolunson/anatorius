package dev.project.anatorius.settings.impl;

import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.settings.Setting;
import dev.project.anatorius.utils.animations.Animation;
import dev.project.anatorius.utils.animations.Easing;

@Getter
@Setter
public class CategorySetting extends Setting {
    private boolean open = false;

    private final transient Animation openAnimation = new Animation(280, Easing.Method.EASE_OUT_CUBIC);

    public CategorySetting(String name, String description) {
        super(name, name, description, new Setting.Visibility());
    }

    public CategorySetting(String name, String tag, String description) {
        super(name, tag, description, new Setting.Visibility());
    }

    public CategorySetting(String name, String description, Setting.Visibility visibility) {
        super(name, name, description, visibility);
    }

    public CategorySetting(String name, String tag, String description, Setting.Visibility visibility) {
        super(name, tag, description, visibility);
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public float getOpenProgress() {
        return openAnimation.get(open ? 1.0f : 0.0f);
    }

    public static class Visibility extends Setting.Visibility {
        private final CategorySetting value;

        public Visibility(CategorySetting value) {
            super(value);
            this.value = value;
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

            setVisible(value.getOpenProgress() > 0.01f);
        }

        @Override
        public float getProgress() {
            float parentProgress = value.getVisibility() == null ? 1.0f : value.getVisibility().getProgress();
            return parentProgress * value.getOpenProgress();
        }

        public CategorySetting getValue() {
            return value;
        }
    }
}
