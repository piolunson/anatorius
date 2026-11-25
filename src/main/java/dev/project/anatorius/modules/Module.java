package dev.project.anatorius.modules;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.settings.impl.*;
import lombok.Getter;
import dev.project.anatorius.events.impl.ToggleModuleEvent;
import dev.project.anatorius.settings.Setting;
import dev.project.anatorius.utils.IMinecraft;
import dev.project.anatorius.utils.animations.Animation;
import dev.project.anatorius.utils.animations.Easing;
import dev.project.anatorius.modules.impl.core.NotificationsModule;

import java.util.ArrayList;
import java.util.List;

@Getter
public abstract class Module implements IMinecraft {
    private final String name, description;
    private final Category category;

    private final boolean persistent;
    private boolean toggled;
    private final List<Setting> settings;

    public BooleanSetting drawn;
    public BindSetting bind;

    private final Animation animationOffset;

    public Module() {
        RegisterModule annotation = getClass().getAnnotation(RegisterModule.class);

        name = annotation.name();
        description = annotation.description();
        category = annotation.category();
        persistent = annotation.persistent();
        toggled = annotation.toggled();
        settings = new ArrayList<>();
        animationOffset = new Animation(300, Easing.Method.EASE_OUT_CUBIC);

        drawn = new BooleanSetting("Drawn", "Renders the module's name on the HUD's module list.", annotation.drawn());
        bind = new BindSetting("Bind", "The keybind that toggles the module on and off.", annotation.bind());

        if (persistent) toggled = true;
        if (toggled) {
            Anatorius.EVENT_HANDLER.subscribe(this);
        }
    }

    public boolean getNull() {
        return (mc.player == null || mc.world == null);
    }

    public void onEnable() {}
    public void onDisable() {}

    public String getMetaData() {
        return "";
    }

    public void setToggled(boolean toggled) {
        setToggled(toggled, true);
    }

    public void setToggled(boolean toggled, boolean notify) {
        if (persistent) return;
        if (toggled == this.toggled) return;

        this.toggled = toggled;
        Anatorius.EVENT_HANDLER.post(new ToggleModuleEvent(this, this.toggled));

        if (this.toggled) {
            animationOffset.setEasing(Easing.Method.EASE_OUT_CUBIC);

            if (notify) NotificationsModule.moduleToggled(this, true);

            runToggleHook(this::onEnable);
            if (this.toggled) Anatorius.EVENT_HANDLER.subscribe(this);
        } else {
            animationOffset.setEasing(Easing.Method.EASE_IN_CUBIC);

            Anatorius.EVENT_HANDLER.unsubscribe(this);
            runToggleHook(this::onDisable);

            if (notify) NotificationsModule.moduleToggled(this, false);
        }
    }

    private void runToggleHook(Runnable hook) {
        if (!getNull()) {
            hook.run();
            return;
        }
        try {
            hook.run();
        } catch (RuntimeException exception) {
            Anatorius.LOGGER.warn("{} could not run its toggle hook outside a world", name, exception);
        }
    }

    public int getBind() {
        return bind.getValue();
    }

    public void setBind(int bind) {
        this.bind.setValue(bind);
    }

    public void resetValues() {
        for (Setting uncastedSetting : settings) {
            if (uncastedSetting instanceof BooleanSetting setting) setting.resetValue();
            if (uncastedSetting instanceof NumberSetting setting) setting.resetValue();
            if (uncastedSetting instanceof ModeSetting setting) setting.resetValue();
            if (uncastedSetting instanceof StringSetting setting) setting.resetValue();
            if (uncastedSetting instanceof BindSetting setting) setting.resetValue();
            if (uncastedSetting instanceof WhitelistSetting setting) setting.reset();
            if (uncastedSetting instanceof ColorSetting setting) setting.resetValue();
        }
    }

    public Setting getSetting(String name) {
        Setting byName = settings.stream().filter(s -> s.getName().equalsIgnoreCase(name) && !(s instanceof CategorySetting)).findFirst().orElse(null);
        if (byName != null) return byName;
        List<Setting> byTag = settings.stream().filter(s -> !(s instanceof CategorySetting) && s.getTag() != null && s.getTag().equalsIgnoreCase(name)).toList();
        return byTag.size() == 1 ? byTag.getFirst() : null;
    }

    public List<Setting> getClickGuiSettings() {
        return settings;
    }

    @Getter
    public enum Category {
        COMBAT("Combat"),
        VISUALS("Visuals"),
        PLAYER("Player"),
        MOVEMENT("Movement"),
        MISCELLANEOUS("Misc"),
        EXPLOIT("Exploit"),
        CORE("Core");

        private final String name;

        Category(String name) {
            this.name = name;
        }
    }
}
