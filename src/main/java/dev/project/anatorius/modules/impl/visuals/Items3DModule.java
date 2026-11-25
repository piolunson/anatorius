package dev.project.anatorius.modules.impl.visuals;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.KeyInputEvent;
import dev.project.anatorius.events.impl.MouseInputEvent;
import dev.project.anatorius.settings.impl.BindSetting;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.WhitelistSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.items3d.InventoryAnimator;
import dev.project.anatorius.utils.items3d.ItemMotion;
import dev.project.anatorius.utils.items3d.Items3DRenderer;
import net.minecraft.item.ItemDisplayContext;
import org.lwjgl.glfw.GLFW;

@RegisterModule(name = "CustomModel", description = "Real 3D items (hand-made models, 3D End Crystal), hand physics and inventory animations.", category = Module.Category.VISUALS)
public class Items3DModule extends Module {
    public final ModeSetting preset = new ModeSetting("Preset", "Quality: Low = basic models, Balanced = smooth models + light physics, High/Ultra = deeper models and every effect.",
            "Balanced", new String[]{"Low", "Balanced", "High", "Ultra"});
    public final NumberSetting modelScale = new NumberSetting("ModelScale", "Scale", "Size of the 3D models, in percent.", 100, 50, 200);
    public final ModeSetting priority = new ModeSetting("ModelPriority", "Priority", "CustomModel: always our 3D models. ResourcePack: a resource pack's own 3D model wins (it can hide our crystal).",
            "CustomModel", new String[]{"CustomModel", "ResourcePack"});
    public final BooleanSetting crystal3D = new BooleanSetting("Crystal3D", "3D Crystal", "The real spinning End Crystal model in your hand, hotbar, third person and on the ground.", true);
    public final BooleanSetting showHand = new BooleanSetting("ShowHand", "Show Hand", "Draws your arm holding the 3D crystal in first person.", true);
    public final BooleanSetting weapons = new BooleanSetting("Weapons3D", "3D Weapons", "Swords, axes, pickaxes, shovels, hoes, mace, trident.", true);
    public final BooleanSetting specials = new BooleanSetting("Specials3D", "3D Items", "Totem, pearls, apples, nether star, rods, charges, potions, buckets, torches...", true);
    public final BooleanSetting physics = new BooleanSetting("HandPhysics", "Hand Physics", "Your hand sways and swings with inertia when you move and turn.", true);
    public final BooleanSetting inventoryAnimations = new BooleanSetting("InventoryAnim", "Inventory Animations", "Items pop and glide when you move them in inventories.", true);

    public final NumberSetting physicsStrength = new NumberSetting("PhysicsStrength", "Physics Strength", "How strongly your hand and held lanterns swing with movement, in percent.", 20, 0, 100);

    public final WhitelistSetting playerModels = new WhitelistSetting("PlayerModels", "Models",
            "3D player models worn instead of the skin. Pick one, or several: you wear the first, other players are spread over them (like two teams). Your own .amdl models go in Anatorius/Models.",
            WhitelistSetting.Type.MODELS);
    public final CategorySetting targets = new CategorySetting("Targets", "Who wears the player model.");
    public final BooleanSetting targetSelf = new BooleanSetting("Self", "You (third person and your first-person arm).", new CategorySetting.Visibility(targets), true);
    public final BooleanSetting targetOthers = new BooleanSetting("Others", "Other players.", new CategorySetting.Visibility(targets), true);
    public final CategorySetting back = new CategorySetting("Back", "A prop behind players wearing one of your own models: the first .glb in Anatorius/Models/Back (Zafkiel for Kurumi).");
    public final BooleanSetting backShow = new BooleanSetting("BackShow", "Show", "Show the back item (its key toggles this).", new CategorySetting.Visibility(back), true);
    public final BindSetting backKey = new BindSetting("BackKey", "Key", "Shows / hides the back item.", new CategorySetting.Visibility(back), GLFW.GLFW_KEY_K);
    public final NumberSetting backSize = new NumberSetting("BackSize", "Size", "Size of the back item, in percent.", new CategorySetting.Visibility(back), 100, 50, 300);

    public static final float SPEED = 1f;
    public static final boolean HANDMADE = true;
    public static final float THICKNESS = 1f;
    public static final boolean CORE_ROTATION = true;
    public static final float CRYSTAL_SPEED = 0.8f;
    public static final boolean FLOATING = true;
    public static final float FLOAT_STRENGTH = 0.20f;
    public static final boolean GLOW = true;
    public static final float GLOW_INTENSITY = 0.30f;
    public static final boolean REALISTIC_HOLDING = true;
    public static final boolean CUSTOM_SWING = true;
    public static final float SWING_SPEED = 1.0f;
    public static final float HAND_SWAY = 1.0f;
    public static final boolean SMOOTH_TRANSITIONS = true;
    public static final boolean LANTERN_PHYSICS = true;
    public static final float PENDULUM_STRENGTH = 0.35f;
    public static final float DAMPING = 0.75f;
    public static final float MAX_ROTATION_DEG = 12f;
    public static final boolean PICKUP = true;
    public static final boolean SLOT_TRANSITION = true;
    public static final boolean PLACEMENT = true;
    public static final float DURATION_MS = 140f;
    public static final String EASING = "EaseOutCubic";
    public static final float SCALE_EFFECT = 8f;
    public static final boolean LOW_FPS_PROTECTION = true;
    public static final int TARGET_FPS = 60;
    public static final boolean AUTO_COMPAT = true;

    private static Boolean otherInventoryMod, otherHandMod;

    @Override
    public void onEnable() {
        ItemMotion.INSTANCE.reset();
    }

    @Override
    public void onDisable() {
        Items3DRenderer.INSTANCE.clear();
        dev.project.anatorius.utils.playermodels.PlayerModels.clear();
        InventoryAnimator.INSTANCE.clear();
        ItemMotion.INSTANCE.reset();
    }

    @SubscribeEvent
    public void onKeyInput(KeyInputEvent event) {
        int key = backKey.getValue();
        if (event.getAction() == 1 && key > 0 && event.getKey() == key) toggleBack();
    }

    @SubscribeEvent
    public void onMouseInput(MouseInputEvent event) {
        int key = backKey.getValue();
        if (event.getAction() == 1 && key < 0 && key == -event.getButton() - 1) toggleBack();
    }

    private void toggleBack() {
        backShow.setValue(!backShow.getValue());
        String name = dev.project.anatorius.utils.playermodels.PlayerModels.backName();
        Anatorius.CHAT_MANAGER.message(name + (backShow.getValue() ? " shown." : " hidden."), "module-customodel-back");
    }

    public static Items3DModule active() {
        if (Anatorius.MODULE_MANAGER == null) return null;
        Items3DModule m = Anatorius.MODULE_MANAGER.getModule(Items3DModule.class);
        return m != null && m.isToggled() ? m : null;
    }

    public int quality() {
        return switch (preset.getValue()) {
            case "Low" -> 0;
            case "High" -> 2;
            case "Ultra" -> 3;
            default -> 1;
        };
    }

    public boolean crystalShownIn(ItemDisplayContext context) {
        if (!crystal3D.getValue()) return false;
        return switch (context) {
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> true;
            case GUI, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND, GROUND, FIXED, HEAD, ON_SHELF -> true;
            default -> false;
        };
    }

    public float speed() {
        return SPEED;
    }

    public boolean inventoryHandledElsewhere() {
        if (!AUTO_COMPAT) return false;
        if (otherInventoryMod == null) otherInventoryMod = anyModLoaded("tiny_item_animations", "tiny-item-animations", "tinyitemanimations");
        return otherInventoryMod;
    }

    public boolean handsHandledElsewhere() {
        if (!AUTO_COMPAT) return false;
        if (otherHandMod == null) otherHandMod = anyModLoaded("hold_my_items", "holdmyitems", "hold-my-items");
        return otherHandMod;
    }

    private static boolean anyModLoaded(String... ids) {
        try {
            net.fabricmc.loader.api.FabricLoader loader = net.fabricmc.loader.api.FabricLoader.getInstance();
            for (String id : ids) if (loader.isModLoaded(id)) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }
}
