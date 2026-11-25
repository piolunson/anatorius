package dev.project.anatorius.modules;

import dev.project.anatorius.Anatorius;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import lombok.Getter;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.KeyInputEvent;
import dev.project.anatorius.events.impl.MouseInputEvent;
import dev.project.anatorius.settings.Setting;
import dev.project.anatorius.utils.IMinecraft;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Getter
public class ModuleManager implements IMinecraft {
    private final List<Module> modules = new ArrayList<>();
    private final Map<Class<? extends Module>, Module> moduleClasses = new Reference2ReferenceOpenHashMap<>();

    public ModuleManager() {
        Anatorius.EVENT_HANDLER.subscribe(this);

        for (Class<?> clazz : dev.project.anatorius.generated.GeneratedRegistry.MODULES) register(clazz);

        modules.sort(Comparator.comparing(Module::getName));
    }

    private void register(Class<?> clazz) {
        try {
            if (clazz.getAnnotation(RegisterModule.class) == null) return;
            Module module = (Module) clazz.getDeclaredConstructor().newInstance();

            for (Field field : module.getClass().getDeclaredFields()) {
                if (!Setting.class.isAssignableFrom(field.getType())) continue;
                if (!field.canAccess(module)) field.setAccessible(true);

                module.getSettings().add((Setting) field.get(module));
            }

            module.getSettings().add(module.drawn);
            module.getSettings().add(module.bind);

            modules.add(module);
            moduleClasses.put(module.getClass(), module);
        } catch (Throwable throwable) {
            Anatorius.LOGGER.error("Failed to register module {}", clazz.getName(), throwable);
        }
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        modules.stream()
                .filter(m -> m.getBind() == event.getKey())
                .forEach(m -> handleBind(m, event.getAction()));
    }

    @SubscribeEvent
    public void onMouseInput(MouseInputEvent event) {
        modules.stream()
                .filter(m -> m.getBind() == (-event.getButton() - 1))
                .forEach(m -> handleBind(m, event.getAction()));
    }

    private void handleBind(Module module, int action) {
        dev.project.anatorius.settings.impl.BindSetting.BindMode mode = module.bind.getMode();

        switch (mode) {
            case TOGGLE:
                if (action == 1) {
                    module.setToggled(!module.isToggled());
                }
                break;
            case HOLD:
                if (action == 1) {
                    module.setToggled(true);
                } else if (action == 0) {
                    module.setToggled(false);
                }
                break;
            case REVERSEHOLD:
                if (action == 1) {
                    module.setToggled(false);
                } else if (action == 0) {
                    module.setToggled(true);
                }
                break;
        }
    }

    public List<Module> getModules(Module.Category category) {
        return modules.stream().filter(m -> m.getCategory() == category).toList();
    }

    public Module getModule(String name) {
        return modules.stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        return (T) moduleClasses.get(clazz);
    }
}
