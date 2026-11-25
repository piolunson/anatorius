package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.NumberSetting;

@RegisterModule(name = "ViewClip", description = "Makes your camera clip through walls and allows you to change the camera's distance from yourself.", category = Module.Category.VISUALS)
public class ViewClipModule extends Module {
    public BooleanSetting extend = new BooleanSetting("Extend", "Changes the distance of the third person camera from yourself.", false);
    public NumberSetting distance = new NumberSetting("Distance", "The distance of the third person camera from your character.", new BooleanSetting.Visibility(extend, true), 4.0f, -0.34f, 4.0f);
    public BooleanSetting smooth = new BooleanSetting("Smooth", "Smoothly transitions the camera distance.", new BooleanSetting.Visibility(extend, true), true);

    private static final float SMOOTH_SPEED = 0.05f;
    private float currentDistance = 0.0f;
    private boolean wasThirdPerson = false;

    @Override
    public String getMetaData() {
        return extend.getValue() ? String.valueOf(distance.getValue().floatValue()) : "Vanilla";
    }

    public float getCurrentDistance() {
        return currentDistance;
    }

    public void updateCurrentDistance(float targetDistance, boolean isThirdPersonBack) {
        if (isThirdPersonBack && !wasThirdPerson) {
            currentDistance = 0.0f;
        }

        if (isThirdPersonBack) {
            if (smooth.getValue()) {
                currentDistance += (targetDistance - currentDistance) * SMOOTH_SPEED;
            } else {
                currentDistance = targetDistance;
            }
        } else {
            currentDistance = targetDistance;
        }

        wasThirdPerson = isThirdPersonBack;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        currentDistance = 0.0f;
        wasThirdPerson = false;
    }
}
