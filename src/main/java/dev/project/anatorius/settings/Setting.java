package dev.project.anatorius.settings;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import dev.project.anatorius.utils.animations.Animation;
import dev.project.anatorius.utils.animations.Easing;

@Getter @AllArgsConstructor
public class Setting {
    private final String name, tag, description;
    private final Visibility visibility;

    @Getter @Setter @RequiredArgsConstructor
    public static class Visibility {
        private final Setting setting;
        private boolean visible = true;
        private final Animation animation = new Animation(280, Easing.Method.EASE_OUT_CUBIC);

        public Visibility() {
            this.setting = null;
        }

        public void update() {}

        public float getProgress() {
            return animation.get(visible ? 1.0f : 0.0f);
        }
    }
}
