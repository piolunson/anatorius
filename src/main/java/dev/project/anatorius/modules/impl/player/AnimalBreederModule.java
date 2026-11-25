package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.rotations.RotationUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.DonkeyEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

@RegisterModule(name = "AnimalBreeder", description = "Feeds grown animals their breeding food automatically.", category = Module.Category.PLAYER)
public class AnimalBreederModule extends Module {
    public NumberSetting reach = new NumberSetting("Reach", "How close an animal has to be to feed it, in blocks.", 3.5, 1.0, 6.0);
    public NumberSetting perCycle = new NumberSetting("PerCycle", "How many animals to feed before waiting again.", 2, 1, 8);
    public NumberSetting interval = new NumberSetting("Interval", "Seconds to wait between feeding rounds.", 0.0, 0.0, 5.0);
    public BooleanSetting face = new BooleanSetting("Face", "Looks at the animal as you feed it (packet).", true);

    public CategorySetting kindsCategory = new CategorySetting("Kinds", "Which animals to feed.");
    public BooleanSetting wolves = new BooleanSetting("Wolves", "Feed tamed wolves.", new CategorySetting.Visibility(kindsCategory), true);
    public BooleanSetting cats = new BooleanSetting("Cats", "Feed cats.", new CategorySetting.Visibility(kindsCategory), true);
    public BooleanSetting parrots = new BooleanSetting("Parrots", "Feed parrots.", new CategorySetting.Visibility(kindsCategory), true);
    public BooleanSetting horses = new BooleanSetting("Horses", "Feed horses.", new CategorySetting.Visibility(kindsCategory), true);
    public BooleanSetting donkeys = new BooleanSetting("Donkeys", "Feed donkeys.", new CategorySetting.Visibility(kindsCategory), true);
    public BooleanSetting others = new BooleanSetting("Others", "Feed cows, sheep, pigs and the rest.", new CategorySetting.Visibility(kindsCategory), true);

    private static final long BREED_COOLDOWN_MS = 300_000L;
    private final Map<Integer, Long> fed = new HashMap<>();
    private long lastRound;

    @Override
    public void onEnable() {
        fed.clear();
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        fed.values().removeIf(deadline -> now > deadline);
        if (now - lastRound < (long) (interval.getValue().doubleValue() * 1000.0)) return;
        lastRound = now;

        int previous = mc.player.getInventory().getSelectedSlot();
        int fedThisRound = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (fedThisRound >= perCycle.getValue().intValue()) break;
            if (!(entity instanceof AnimalEntity animal)) continue;
            if (animal.isBaby() || !enabledFor(animal)) continue;
            if (fed.containsKey(animal.getId())) continue;
            if (mc.player.distanceTo(animal) > reach.getValue().doubleValue()) continue;

            int slot = InventoryUtils.find(foodFor(animal), InventoryUtils.HOTBAR_START, InventoryUtils.HOTBAR_END);
            if (slot == -1) continue;

            if (face.getValue()) {
                Vec3d center = animal.getBoundingBox().getCenter();
                Anatorius.ROTATION_MANAGER.packetRotate(RotationUtils.getRotations(center.x, center.y, center.z, false),
                        Anatorius.ROTATION_MANAGER.getModulePriority(this));
            }
            mc.player.getInventory().setSelectedSlot(slot);
            mc.interactionManager.interactEntity(mc.player, animal, Hand.MAIN_HAND);
            fed.put(animal.getId(), now + BREED_COOLDOWN_MS);
            fedThisRound++;
        }

        if (fedThisRound > 0) mc.player.getInventory().setSelectedSlot(previous);
    }

    private Predicate<ItemStack> foodFor(AnimalEntity animal) {
        return animal::isBreedingItem;
    }

    private boolean enabledFor(AnimalEntity animal) {
        if (animal instanceof WolfEntity) return wolves.getValue();
        if (animal instanceof CatEntity) return cats.getValue();
        if (animal instanceof ParrotEntity) return parrots.getValue();
        if (animal instanceof DonkeyEntity) return donkeys.getValue();
        if (animal instanceof HorseEntity) return horses.getValue();
        return others.getValue();
    }
}
