package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.WhitelistSetting;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;

@RegisterModule(name = "NoEffects", description = "Cancels the effects you pick (levitation, slow falling, ...).", category = Module.Category.PLAYER)
public class NoEffectsModule extends Module {
    private static NoEffectsModule INSTANCE;

    public WhitelistSetting effects = new WhitelistSetting("Effects", "The effects that don't apply to you.", WhitelistSetting.Type.EFFECTS)
            .withDefault(StatusEffects.LEVITATION.value(), StatusEffects.SLOW_FALLING.value());

    public NoEffectsModule() {
        INSTANCE = this;
    }

    public static boolean blocks(RegistryEntry<StatusEffect> effect) {
        return INSTANCE != null && INSTANCE.isToggled() && effect != null && INSTANCE.effects.isWhitelistContains(effect.value());
    }

    @Override
    public String getMetaData() {
        return String.valueOf(effects.getWhitelist().size());
    }
}
