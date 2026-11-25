package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerMineEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.player.SpeedMineModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.BlockBudget;
import dev.project.anatorius.utils.minecraft.HoleUtils;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.system.ThreadExecutor;
import dev.project.anatorius.utils.system.Timer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RegisterModule(name = "Blocker", description = "Places blocks to stop enemies from placing crystals.", category = Module.Category.COMBAT)
public class BlockerModule extends Module {
    public ModeSetting autoSwitch = new ModeSetting("Switch", "The mode that will be used for automatically switching to necessary items.", "Silent", InventoryUtils.SWITCH_MODES);
    public BooleanSetting asynchronous = new BooleanSetting("Asynchronous", "Performs calculations on separate threads.", true);
    public BooleanSetting instant = new BooleanSetting("Instant", "Places every block in a single tick instead of throttling to BlocksPerTick. Strict anticheats may flag the packet burst.", false);
    public NumberSetting blocksPerTick = new NumberSetting("BlocksPerTick", "Max blocks placed per tick when Instant is off. 0 = unlimited.", new BooleanSetting.Visibility(instant, false), 4, 0, 10);
    public NumberSetting delay = new NumberSetting("Delay", "The amount of ticks that have to be waited for between placements.", 0, 0, 20);
    public NumberSetting range = new NumberSetting("Range", "The maximum range at which the blocks will be placed at.", 5.0, 0.0, 12.0);
    public BooleanSetting rotate = new BooleanSetting("Rotate", "Sends a packet rotation whenever placing a block.", true);
    public BooleanSetting strictDirection = new BooleanSetting("StrictDirection", "Only places using directions that face you.", false);
    public BooleanSetting crystalDestruction = new BooleanSetting("Attack", "Breaks the crystals in the way before placing.", true);
    public BooleanSetting whileEating = new BooleanSetting("WhileEating", "Places blocks normally while eating.", true);

    public CategorySetting targetsCategory = new CategorySetting("Targets", "Which blocks around you get blocked.");
    public BooleanSetting feet = new BooleanSetting("Feet", "Places on feet level blocks.", new CategorySetting.Visibility(targetsCategory), true);
    public BooleanSetting head = new BooleanSetting("Head", "Places on head level blocks.", new CategorySetting.Visibility(targetsCategory), true);

    public BooleanSetting render = new BooleanSetting("Render", "Whether or not to render the place position.", true);

    private final CopyOnWriteArrayList<Position> targetPositions = new CopyOnWriteArrayList<>();
    private Mine mine = null;

    private final BlockBudget budget = new BlockBudget();
    private int ticks = 0;

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (!whileEating.getValue() && mc.player.isUsingItem()) return;

        Runnable runnable = () -> {
            if (mc.player == null || mc.world == null) return;

            SpeedMineModule module = Anatorius.MODULE_MANAGER.getModule(SpeedMineModule.class);
            if (mine != null && (module.getPrimary() != null && mine.position().equals(module.getPrimary().getPosition())) || (module.getSecondary() != null && mine.position().equals(module.getSecondary().getPosition()))) {
                mine = null;
                return;
            }

            if (ticks < delay.getValue().intValue()) {
                ticks++;
                return;
            }

            HashSet<BlockPos> feetPositions = HoleUtils.getFeetPositions(mc.player, true, false, false);
            List<BlockPos> insidePositions = HoleUtils.getInsidePositions(mc.player);

            if (mine != null && mine.timer().hasTimeElapsed(Math.max(mine.breakTime() - 200L, 0L))) {
                BlockPos position = mine.position();
                if (mine.type() == MineType.FEET && feet.getValue()) {
                    if (feetPositions.contains(mine.position())) {
                        targetPositions.add(new Position(position, position.up()));

                        for (Direction direction : Direction.values()) {
                            if (!direction.getAxis().isHorizontal()) continue;
                            targetPositions.add(new Position(position, position.offset(direction)));
                        }
                    }

                    mine = null;
                } else if ((mine.type() == MineType.HEAD || mine.type() == MineType.SIDE) && head.getValue()) {
                    if ((mine.type() == MineType.HEAD && insidePositions.contains(mine.position().down().down())) || (mine.type() == MineType.SIDE && feetPositions.contains(mine.position().down()))) {
                        targetPositions.add(new Position(position, position.up()));
                    }

                    mine = null;
                }
            }

            targetPositions.removeIf(position -> !WorldUtils.isPlaceable(position.position()));
            targetPositions.removeIf(position -> mc.player.squaredDistanceTo(Vec3d.ofCenter(position.position())) > MathHelper.square(range.getValue().doubleValue()));

            targetPositions.removeIf(position -> !feetPositions.contains(position.original()) && !feetPositions.contains(position.original().down()) && !insidePositions.contains(position.original().down().down()));

            if (targetPositions.isEmpty()) return;

            ticks = 0;

            mc.execute(() -> {
                if (mc.player == null || mc.world == null || !isToggled()) return;
                budget.newTick();
                int slot = InventoryUtils.findHardestBlock(0, autoSwitch.getValue().equalsIgnoreCase("AltSwap") ? 35 : 8);
                int previousSlot = mc.player.getInventory().getSelectedSlot();

                if (slot == -1) {
                    targetPositions.clear();
                    return;
                }

                InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);

                for (Position position : new ArrayList<>(targetPositions)) {
                    if (!budget.canPlace(instant.getValue(), blocksPerTick.getValue().intValue())) break;

                    Direction direction = WorldUtils.getDirection(position.position(), null, strictDirection.getValue());
                    if (direction == null) continue;

                    if (WorldUtils.placeBlock(position.position(), direction, Hand.MAIN_HAND, rotate.getValue(), crystalDestruction.getValue(), render.getValue())) {
                        targetPositions.remove(position);
                        budget.consume();
                    }
                }

                InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
            });
        };

        if (asynchronous.getValue()) ThreadExecutor.execute(runnable);
        else runnable.run();
    }

    @SubscribeEvent
    public void onPlayerMine(PlayerMineEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mine != null && mine.position().equals(event.getPosition())) return;

        SpeedMineModule module = Anatorius.MODULE_MANAGER.getModule(SpeedMineModule.class);
        if ((module.getPrimary() != null && event.getPosition().equals(module.getPrimary().getPosition())) || (module.getSecondary() != null && event.getPosition().equals(module.getSecondary().getPosition())))
            return;

        Entity entity = mc.world.getEntityById(event.getActorID());

        if (entity == mc.player) return;
        if (!(entity instanceof PlayerEntity player)) return;
        if (Anatorius.FRIEND_MANAGER.contains(player.getName().getString())) return;

        HashSet<BlockPos> feetPositions = HoleUtils.getFeetPositions(mc.player, true, false, false);
        List<BlockPos> insidePositions = HoleUtils.getInsidePositions(mc.player);

        if (feet.getValue() && feetPositions.contains(event.getPosition())) {
            mine = new Mine(event.getPosition(), new Timer(), WorldUtils.getBreakTime(player, mc.world.getBlockState(event.getPosition())), MineType.FEET);
            return;
        }

        if (head.getValue()) {
            if (feetPositions.contains(event.getPosition().down())) {
                mine = new Mine(event.getPosition(), new Timer(), WorldUtils.getBreakTime(player, mc.world.getBlockState(event.getPosition())), MineType.SIDE);
            }

            if (insidePositions.contains(event.getPosition().down().down())) {
                mine = new Mine(event.getPosition(), new Timer(), WorldUtils.getBreakTime(player, mc.world.getBlockState(event.getPosition())), MineType.HEAD);
            }
        }
    }

    @Override
    public void onDisable() {
        budget.reset();
        targetPositions.clear();
    }

    @Override
    public String getMetaData() {
        if (targetPositions == null) return "0";
        return String.valueOf(targetPositions.size());
    }

    private record Mine(BlockPos position, Timer timer, float breakTime, MineType type) { }
    private record Position(BlockPos original, BlockPos position) { }
    private enum MineType {
        FEET, HEAD, SIDE
    }
}
