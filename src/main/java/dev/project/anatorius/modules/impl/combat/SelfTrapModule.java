package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.HoleUtils;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.TrapUtils;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

@RegisterModule(name = "SelfTrap", description = "Automatically places blocks around you to prevent other people from getting inside your hole.", category = Module.Category.COMBAT)
public class SelfTrapModule extends Module {
    public ModeSetting autoSwitch = new ModeSetting("Switch", "The mode that will be used for automatically switching to necessary items.", "Silent", InventoryUtils.SWITCH_MODES);
    public NumberSetting delay = new NumberSetting("Delay", "Milliseconds to wait after placing before the next blocks go in.", 50, 0, 250);
    public NumberSetting blocksPerTick = new NumberSetting("BlocksPerTick", "Max blocks placed per tick.", 8, 1, 9);
    public BooleanSetting rotate = new BooleanSetting("Rotate", "Looks at each block after placing it (Grim does not need it).", false);
    public BooleanSetting crystalDestruction = new BooleanSetting("Attack", "Breaks the crystals in the way before placing.", true);
    public BooleanSetting airPlace = new BooleanSetting("Air", "Places blocks with nothing to click against in the air instead of putting a helper block under them first.", false);
    public BooleanSetting obsidianOnly = new BooleanSetting("ObsidianOnly", "Only uses obsidian, never another hard block.", false);
    public BooleanSetting whileEating = new BooleanSetting("WhileEating", "Places blocks normally while eating.", true);

    public CategorySetting coverCategory = new CategorySetting("Cover", "Which blocks around you get covered.");
    public BooleanSetting head = new BooleanSetting("Head", "Covers the block on your head.", new CategorySetting.Visibility(coverCategory), true);
    public BooleanSetting body = new BooleanSetting("Body", "Covers the ring of blocks at face height.", new CategorySetting.Visibility(coverCategory), true);
    public BooleanSetting feet = new BooleanSetting("Feet", "Also covers the ring of blocks at your feet.", new CategorySetting.Visibility(coverCategory), false);
    public BooleanSetting antiStep = new BooleanSetting("AntiStep", "Adds the blocks above the face ring so nobody can step out of the hole.", new CategorySetting.Visibility(coverCategory), false);
    public BooleanSetting antiBomb = new BooleanSetting("AntiBomb", "Places an extra block above your head to prevent you from getting bombed.", new CategorySetting.Visibility(coverCategory), false);

    public CategorySetting conditionsCategory = new CategorySetting("Conditions", "When it covers you.");
    public BooleanSetting holeCheck = new BooleanSetting("HoleCheck", "Only covers you while you are in a hole.", new CategorySetting.Visibility(conditionsCategory), false);
    public BooleanSetting standingOnly = new BooleanSetting("StandingOnly", "Does nothing while you crawl.", new CategorySetting.Visibility(conditionsCategory), false);
    public BooleanSetting enemyCheck = new BooleanSetting("EnemyCheck", "Only covers you while an enemy is within EnemyRange.", new CategorySetting.Visibility(conditionsCategory), false);
    public NumberSetting enemyRange = new NumberSetting("EnemyRange", "How close an enemy has to be for EnemyCheck.", new BooleanSetting.Visibility(enemyCheck, true), 3.0, 1.0, 6.0);

    public CategorySetting autoDisableCategory = new CategorySetting("AutoDisable", "Contains all of the settings relating to automatic disabling.");
    public BooleanSetting selfDisable = new BooleanSetting("SelfDisable", "Toggles off the module once it is finished with placing.", new CategorySetting.Visibility(autoDisableCategory), false);
    public BooleanSetting itemDisable = new BooleanSetting("ItemDisable", "Toggles off the module whenever you run out of items to place with.", new CategorySetting.Visibility(autoDisableCategory), true);
    public BooleanSetting jumpDisable = new BooleanSetting("JumpDisable", "Toggles off the module when you jump out.", new CategorySetting.Visibility(autoDisableCategory), true);

    public BooleanSetting render = new BooleanSetting("Render", "Whether or not to render the place position.", true);

    private List<BlockPos> targetPositions = new ArrayList<>();
    private final Timer placeTimer = new Timer();
    private double groundY;

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) {
            setToggled(false);
            return;
        }
        groundY = mc.player.getY();
    }

    @Override
    public void onDisable() {
        targetPositions = new ArrayList<>();
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (getNull()) return;

        if (mc.player.isOnGround()) groundY = mc.player.getY();
        else if (jumpDisable.getValue() && mc.player.getY() - groundY >= 0.4) {
            setToggled(false);
            return;
        }

        if (!whileEating.getValue() && mc.player.isUsingItem()) return;
        if (!placeTimer.hasTimeElapsed(delay.getValue().longValue())) return;

        targetPositions = positions();
        if (targetPositions.isEmpty()) {
            if (selfDisable.getValue() && conditionsMet()) setToggled(false);
            return;
        }

        int slot = TrapUtils.findBlock(autoSwitch.getValue(), obsidianOnly.getValue());
        if (slot == -1) {
            if (itemDisable.getValue()) {
                Anatorius.CHAT_MANAGER.tagged("No blocks could be found in your hotbar.", getName());
                setToggled(false);
            }
            return;
        }

        List<BlockPos> placed = TrapUtils.place(targetPositions, new TrapUtils.Placing(autoSwitch.getValue(), slot, blocksPerTick.getValue().intValue(),
                rotate.getValue(), crystalDestruction.getValue(), airPlace.getValue(), render.getValue(), Anatorius.ROTATION_MANAGER.getModulePriority(this)));
        if (!placed.isEmpty()) placeTimer.reset();
    }

    private List<BlockPos> positions() {
        if (!conditionsMet()) return new ArrayList<>();
        TrapUtils.Shape shape = new TrapUtils.Shape(head.getValue(), body.getValue(), feet.getValue(), false, antiStep.getValue(), antiBomb.getValue(), airPlace.getValue());
        return TrapUtils.filter(TrapUtils.collect(mc.player, shape), mc.player, shape, mc.player.getBlockInteractionRange());
    }

    private boolean conditionsMet() {
        if (standingOnly.getValue() && mc.player.getHeight() < 1.0f) return false;
        if (enemyCheck.getValue() && !enemyNear()) return false;
        return !holeCheck.getValue() || HoleUtils.isPlayerInHole(mc.player);
    }

    private boolean enemyNear() {
        double rangeSq = enemyRange.getValue().doubleValue() * enemyRange.getValue().doubleValue();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (Anatorius.FRIEND_MANAGER.contains(player.getName().getString())) continue;
            if (mc.player.squaredDistanceTo(player) <= rangeSq) return true;
        }
        return false;
    }

    @Override
    public String getMetaData() {
        return String.valueOf(targetPositions.size());
    }
}
