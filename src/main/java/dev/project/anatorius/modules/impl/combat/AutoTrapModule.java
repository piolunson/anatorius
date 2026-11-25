package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.core.AntiCheatModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.TrapUtils;
import dev.project.anatorius.utils.rotations.RotationUtils;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

@RegisterModule(name = "AutoTrap", description = "Automatically traps nearby enemies with blocks.", category = Module.Category.COMBAT)
public class AutoTrapModule extends Module {
    public ModeSetting autoSwitch = new ModeSetting("Switch", "The mode that will be used for automatically switching to necessary items.", "Silent", InventoryUtils.SWITCH_MODES);
    public NumberSetting range = new NumberSetting("Range", "The maximum range at which the blocks will be placed at.", 5.0, 1.0, 6.5);
    public NumberSetting delay = new NumberSetting("Delay", "Milliseconds to wait after placing before the next blocks go in.", 50, 0, 250);
    public NumberSetting blocksPerTick = new NumberSetting("BlocksPerTick", "Max blocks placed per tick.", 8, 1, 9);
    public BooleanSetting rotate = new BooleanSetting("Rotate", "Looks at each block after placing it (Grim does not need it).", false);
    public BooleanSetting crystalDestruction = new BooleanSetting("Attack", "Breaks the crystals in the way before placing.", true);
    public BooleanSetting airPlace = new BooleanSetting("Air", "Places blocks with nothing to click against in the air instead of putting a helper block under them first.", false);
    public BooleanSetting obsidianOnly = new BooleanSetting("ObsidianOnly", "Only uses obsidian, never another hard block.", false);
    public BooleanSetting whileEating = new BooleanSetting("WhileEating", "Places blocks normally while eating.", true);

    public CategorySetting coverCategory = new CategorySetting("Cover", "Which blocks around the target get covered.");
    public BooleanSetting head = new BooleanSetting("Head", "Covers the block on the target's head.", new CategorySetting.Visibility(coverCategory), true);
    public BooleanSetting body = new BooleanSetting("Body", "Covers the ring of blocks at the target's face height.", new CategorySetting.Visibility(coverCategory), true);
    public BooleanSetting feet = new BooleanSetting("Feet", "Also covers the ring of blocks at the target's feet.", new CategorySetting.Visibility(coverCategory), false);
    public BooleanSetting below = new BooleanSetting("Floor", "Also places the block under the target.", new CategorySetting.Visibility(coverCategory), false);
    public BooleanSetting antiStep = new BooleanSetting("AntiStep", "Adds the blocks above the face ring so the target can't step out.", new CategorySetting.Visibility(coverCategory), false);

    public CategorySetting targetsCategory = new CategorySetting("Targets", "Who gets trapped.");
    public BooleanSetting phasedOnly = new BooleanSetting("PhasedOnly", "Only traps players clipped into a block.", new CategorySetting.Visibility(targetsCategory), false);
    public BooleanSetting skipNaked = new BooleanSetting("SkipNaked", "Leaves players without armor alone.", new CategorySetting.Visibility(targetsCategory), false);
    public BooleanSetting skipCrawling = new BooleanSetting("SkipCrawling", "Leaves crawling players alone.", new CategorySetting.Visibility(targetsCategory), false);

    public CategorySetting autoDisableCategory = new CategorySetting("AutoDisable", "Contains all of the settings relating to automatic disabling.");
    public BooleanSetting selfDisable = new BooleanSetting("SelfDisable", "Toggles off the module once it is finished with placing.", new CategorySetting.Visibility(autoDisableCategory), false);
    public BooleanSetting itemDisable = new BooleanSetting("ItemDisable", "Toggles off the module whenever you run out of items to place with.", new CategorySetting.Visibility(autoDisableCategory), true);
    public BooleanSetting jumpDisable = new BooleanSetting("JumpDisable", "Toggles off the module when you jump.", new CategorySetting.Visibility(autoDisableCategory), false);

    public BooleanSetting render = new BooleanSetting("Render", "Whether or not to render the place position.", true);

    private List<BlockPos> positions = new ArrayList<>();
    private PlayerEntity target;
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
        positions = new ArrayList<>();
        target = null;
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

        target = selectTarget();
        if (target == null) {
            positions = new ArrayList<>();
            return;
        }

        TrapUtils.Shape shape = new TrapUtils.Shape(head.getValue(), body.getValue(), feet.getValue(), below.getValue(), antiStep.getValue(), false, airPlace.getValue());
        positions = TrapUtils.filter(TrapUtils.collect(target, shape), target, shape, range.getValue().doubleValue());
        if (positions.isEmpty()) {
            if (selfDisable.getValue()) setToggled(false);
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

        List<BlockPos> placed = TrapUtils.place(positions, new TrapUtils.Placing(autoSwitch.getValue(), slot, blocksPerTick.getValue().intValue(),
                rotate.getValue(), crystalDestruction.getValue(), airPlace.getValue(), render.getValue(), Anatorius.ROTATION_MANAGER.getModulePriority(this)));
        if (!placed.isEmpty()) placeTimer.reset();
    }

    private PlayerEntity selectTarget() {
        PlayerEntity best = null;
        float bestDifference = Float.MAX_VALUE;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!isValidTarget(player)) continue;
            float yaw = RotationUtils.getRotations(player.getX(), player.getEyeY(), player.getZ(), false)[0];
            float difference = Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - yaw));
            if (difference < bestDifference) {
                bestDifference = difference;
                best = player;
            }
        }
        return best;
    }

    private boolean isValidTarget(PlayerEntity player) {
        if (player == mc.player || !player.isAlive() || player.isDead()) return false;
        if (Anatorius.FRIEND_MANAGER.contains(player.getName().getString())) return false;
        if (skipNaked.getValue() && !TrapUtils.hasArmor(player)) return false;
        if (phasedOnly.getValue() && !TrapUtils.isPhased(player)) return false;
        if (skipCrawling.getValue() && player.getHeight() < 1.0f && !player.isGliding()) return false;

        if (!movementSync()) {
            double dx = player.getX() - player.lastX, dz = player.getZ() - player.lastZ;
            if (Math.sqrt(dx * dx + dz * dz) * 20.0 * 3.6 > 20.0) return false;
            if (mc.world.isSpaceEmpty(player, player.getBoundingBox().offset(0.0, -0.1, 0.0))) return false;
            if (Math.abs(player.lastY - player.getY()) > 0.6) return false;
        }

        double maximum = player.isGliding() ? 14.0 : range.getValue().doubleValue() + 2.0;
        return player.getEntityPos().distanceTo(mc.player.getEyePos()) <= maximum;
    }

    private static boolean movementSync() {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        return antiCheat != null && antiCheat.movementSync.getValue();
    }

    @Override
    public String getMetaData() {
        return target != null ? target.getName().getString() : String.valueOf(positions.size());
    }
}
