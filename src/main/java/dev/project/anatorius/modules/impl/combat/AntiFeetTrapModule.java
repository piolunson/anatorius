package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.player.MultiTaskModule;
import dev.project.anatorius.modules.impl.player.SpeedMineModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.HoleUtils;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.ItemUtils;
import dev.project.anatorius.utils.minecraft.PositionUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

@RegisterModule(name = "AntiFeetTrap", description = "Replaces an enemy's surround block you are mining with a faster-breaking one. Requires SpeedMine.", category = Module.Category.COMBAT)
public class AntiFeetTrapModule extends Module {
    public final ModeSetting autoSwitch = new ModeSetting("Switch", "The mode used to switch to the replacement item.", "Silent", InventoryUtils.SWITCH_MODES);
    public final ModeSetting mode = new ModeSetting("Mode", "Which block to replace the mined block with.", "Glass", new String[]{"Glass", "Ice", "Anvil"});
    public final NumberSetting delay = new NumberSetting("Delay", "Delay in ms between replacements.", 0.0, 0.0, 500.0);
    public final BooleanSetting airPlace = new BooleanSetting("AirPlace", "Place without requiring a valid direction.", false);
    public final BooleanSetting strictDirection = new BooleanSetting("StrictDirection", "Only use directions that face you.", false);
    public final BooleanSetting rotate = new BooleanSetting("Rotate", "Sends a packet rotation when placing.", false);
    public final BooleanSetting render = new BooleanSetting("Render", "Render the placement position.", false);

    private final Timer placementTimer = new Timer();

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        replaceMinedBlock();
    }

    private void replaceMinedBlock() {
        SpeedMineModule speedMine = Anatorius.MODULE_MANAGER.getModule(SpeedMineModule.class);
        if (speedMine == null || !speedMine.isToggled()) return;

        SpeedMineModule.Action primary = speedMine.getPrimary();
        BlockPos miningPos = primary == null ? null : primary.getPosition();
        if (miningPos == null) return;

        boolean air = mc.world.getBlockState(miningPos).isReplaceable();
        if (!air && !speedMine.isBreakingSoon(miningPos, 0.95f)) return;

        MultiTaskModule multiTask = Anatorius.MODULE_MANAGER.getModule(MultiTaskModule.class);
        boolean canActWhileUsing = multiTask != null && multiTask.isToggled();
        if (!canActWhileUsing && mc.player.isUsingItem()) return;

        if (mc.player.isSpectator() || !mc.player.isAlive() || mc.player.isGliding()) return;
        if (delay.getValue().doubleValue() > 0 && !placementTimer.hasTimeElapsed(delay.getValue())) return;

        if (!hasEligibleEnemy(miningPos)) return;
        if (!isBedrockOrObsidian(miningPos.down())) return;

        Direction direction = WorldUtils.getDirection(miningPos, strictDirection.getValue());
        if (direction == null && !airPlace.getValue()) return;

        boolean offhand = predicate().test(mc.player.getOffHandStack());
        int slot = InventoryUtils.find(predicate(), 0, autoSwitch.getValue().equalsIgnoreCase("AltSwap") ? 35 : 8);
        if (slot == -1 && !offhand) return;

        if (slot == -1) {
            WorldUtils.placeBlock(miningPos, direction, Hand.OFF_HAND, rotate.getValue(), false, render.getValue());
        } else {
            int previousSlot = mc.player.getInventory().getSelectedSlot();
            InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);
            WorldUtils.placeBlock(miningPos, direction, Hand.MAIN_HAND, rotate.getValue(), false, render.getValue());
            InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
        }

        placementTimer.reset();
    }

    private boolean hasEligibleEnemy(BlockPos position) {
        for (PlayerEntity target : mc.world.getPlayers()) {
            if (target == mc.player || !target.isAlive()) continue;
            if (Anatorius.FRIEND_MANAGER.contains(target.getName().getString())) continue;
            if (target.squaredDistanceTo(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5) > 36.0) continue;

            boolean armored = false;
            for (ItemStack stack : ItemUtils.getArmorItems(target)) {
                if (!stack.isEmpty()) { armored = true; break; }
            }
            if (!armored) continue;

            Set<BlockPos> surround = HoleUtils.getFeetPositions(target, true, false, true);
            if (!surround.contains(position)) continue;

            for (BlockPos feet : getOccupiedFeetPositions(target)) {
                if (feet.getManhattanDistance(position) != 1) continue;
                if (mc.world.getBlockState(feet).isAir()) return true;
            }
        }
        return false;
    }

    private Set<BlockPos> getOccupiedFeetPositions(PlayerEntity target) {
        Set<BlockPos> positions = new HashSet<>();
        BlockPos feet = PositionUtils.getFlooredPosition(target);
        Box box = target.getBoundingBox();
        for (int x = (int) Math.floor(box.minX); x < Math.ceil(box.maxX); x++) {
            for (int z = (int) Math.floor(box.minZ); z < Math.ceil(box.maxZ); z++) {
                positions.add(new BlockPos(x, feet.getY(), z));
            }
        }
        return positions;
    }

    private boolean isBedrockOrObsidian(BlockPos pos) {
        net.minecraft.block.Block block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.BEDROCK || block == Blocks.OBSIDIAN;
    }

    private Predicate<ItemStack> predicate() {
        return switch (mode.getValue()) {
            case "Ice" -> stack -> {
                if (stack.isOf(Items.PACKED_ICE)) return true;
                return stack.isOf(Items.ICE) && mc.world.getRegistryKey() != net.minecraft.world.World.NETHER;
            };
            case "Anvil" -> stack -> stack.isOf(Items.ANVIL) || stack.isOf(Items.CHIPPED_ANVIL) || stack.isOf(Items.DAMAGED_ANVIL);
            default -> stack -> !stack.isEmpty() && Registries.ITEM.getId(stack.getItem()).getPath().endsWith("glass");
        };
    }
}
