package dev.project.anatorius.settings.impl;

import lombok.Getter;
import lombok.Setter;
import dev.project.anatorius.settings.Setting;
import dev.project.anatorius.utils.annotations.AllowedTypes;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter @Setter
public class WhitelistSetting extends Setting {
    private final Type type;
    private final Set<Object> whitelist = new HashSet<>();
    private final List<Object> defaults = new java.util.ArrayList<>();

    public WhitelistSetting(String name, String description, Type type) {
        super(name, name, description, new Setting.Visibility());
        this.type = type;
    }

    public WhitelistSetting(String name, String tag, String description, Type type) {
        super(name, tag, description, new Setting.Visibility());
        this.type = type;
    }

    public WhitelistSetting(String name, String description, Setting.Visibility visibility, Type type) {
        super(name, name, description, visibility);
        this.type = type;
    }

    public WhitelistSetting(String name, String tag, String description, Setting.Visibility visibility, Type type) {
        super(name, tag, description, visibility);
        this.type = type;
    }

    @AllowedTypes({Item.class, Block.class, net.minecraft.potion.Potion.class, net.minecraft.entity.effect.StatusEffect.class, String.class})
    public void add(Object object) {
        whitelist.add(object);
    }

    @AllowedTypes({Item.class, Block.class, net.minecraft.potion.Potion.class, net.minecraft.entity.effect.StatusEffect.class, String.class})
    public void remove(Object id) {
        whitelist.remove(id);
    }

    @AllowedTypes({Item.class, Block.class, net.minecraft.potion.Potion.class, net.minecraft.entity.effect.StatusEffect.class, String.class})
    public boolean isWhitelistContains(Object object) {
        return whitelist.contains(object);
    }

    public List<String> getWhitelistIds() {
        return whitelist.stream().map(object -> {
            if (object instanceof Item item) {
                return Registries.ITEM.getId(item).toString();
            } else if (object instanceof Block block) {
                return Registries.BLOCK.getId(block).toString();
            } else if (object instanceof net.minecraft.entity.effect.StatusEffect effect) {
                return Registries.STATUS_EFFECT.getId(effect).toString();
            } else if (object instanceof net.minecraft.potion.Potion potion) {
                return Registries.POTION.getId(potion).toString();
            } else if (object instanceof String id) {
                return id;
            }
            return null;
        }).toList();
    }

    public void clear() {
        whitelist.clear();
    }

    public WhitelistSetting withDefault(Object... values) {
        for (Object value : values) {
            defaults.add(value);
            whitelist.add(value);
        }
        return this;
    }

    public void reset() {
        whitelist.clear();
        whitelist.addAll(defaults);
    }

    public String getSound() {
        return whitelist.stream().filter(String.class::isInstance).map(String.class::cast).sorted().findFirst().orElse(null);
    }

    public enum Type {
        ITEMS, BLOCKS, POTIONS,
        MODELS,
        EFFECTS,
        SOUNDS
    }
}
