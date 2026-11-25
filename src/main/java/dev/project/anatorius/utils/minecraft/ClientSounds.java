package dev.project.anatorius.utils.minecraft;

import dev.project.anatorius.Anatorius;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.Sound;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.floatprovider.ConstantFloatProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClientSounds {
    private static final String FOLDER = "sounds";

    private ClientSounds() {}

    public static List<String> list() {
        List<String> names = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getResourceManager() == null) return names;
        mc.getResourceManager().findResources(FOLDER, id -> id.getNamespace().equals(Anatorius.MOD_ID) && id.getPath().endsWith(".ogg"))
                .keySet().forEach(id -> {
                    String path = id.getPath();
                    names.add(path.substring(FOLDER.length() + 1, path.length() - ".ogg".length()));
                });
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static String pretty(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.split("[_\\-\\s]+")) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    public static void play(String name, float volume) {
        if (name == null || name.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSoundManager() == null) return;
        if (!Identifier.isPathValid(name)) return;
        Identifier id = Identifier.of(Anatorius.MOD_ID, name);
        if (mc.getResourceManager().getResource(Identifier.of(Anatorius.MOD_ID, FOLDER + "/" + name + ".ogg")).isEmpty()) return;
        mc.getSoundManager().play(new Instance(id, volume));
    }

    private static final class Instance extends AbstractSoundInstance {
        Instance(Identifier id, float volume) {
            super(id, SoundCategory.MASTER, SoundInstance.createRandom());
            this.volume = volume;
            this.pitch = 1.0f;
            this.relative = true;
            this.attenuationType = AttenuationType.NONE;
        }

        @Override
        public WeightedSoundSet getSoundSet(SoundManager soundManager) {
            Sound file = new Sound(id, ConstantFloatProvider.create(1.0f), ConstantFloatProvider.create(1.0f), 1, Sound.RegistrationType.FILE, false, false, 16);
            WeightedSoundSet set = new WeightedSoundSet(id, null);
            set.add(file);
            this.sound = file;
            return set;
        }
    }
}
