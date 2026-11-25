package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ColorSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.color.ColorUtils;
import dev.project.anatorius.utils.graphics.Renderer3D;
import dev.project.anatorius.utils.minecraft.BlockBudget;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

@RegisterModule(name = "AntiPiston", description = "Blocks and breaks enemy piston-crystal setups before they can push a crystal into you.", category = Module.Category.COMBAT)
public class AntiPistonModule extends Module {
    public final BooleanSetting proactive  = new BooleanSetting("Proactive", "Fill the air blocks around your head with obsidian so no piston setup can be built.", true);
    public final BooleanSetting coverAbove = new BooleanSetting("CoverAbove", "Also fill the block above your head and its neighbours (covers the top-push setup).", new BooleanSetting.Visibility(proactive, true), false);
    public final BooleanSetting reactive   = new BooleanSetting("Reactive", "Mine out an enemy piston in the danger zone before it fires.", true);
    public final NumberSetting  range       = new NumberSetting("Range", "The reach for placing and breaking.", 4.5f, 0.0f, 6.0f);
    public final BooleanSetting instant     = new BooleanSetting("Instant", "Fill every open spot in a single tick instead of throttling to BlocksPerTick. Strict anticheats may flag the burst.", false);
    public final NumberSetting  blocksPerTick = new NumberSetting("BlocksPerTick", "Max blocks placed per tick when Instant is off. 0 = unlimited.", new BooleanSetting.Visibility(instant, false), 2, 0, 10);
    public final BooleanSetting rotate      = new BooleanSetting("Rotate", "Rotate towards the block being placed or broken.", true);
    public final ModeSetting    autoSwitch  = new ModeSetting("Switch", "Inventory switch mode for obsidian.", "Silent", InventoryUtils.SWITCH_MODES);

    public final CategorySetting renderCategory = new CategorySetting("Render", "Show the protected spots.");
    public final BooleanSetting  render       = new BooleanSetting("Render", "Draw the blocks we protect / place.", new CategorySetting.Visibility(renderCategory), true);
    public final ColorSetting    fillColor    = new ColorSetting("FillColor", "Box fill.", new CategorySetting.Visibility(renderCategory), ColorUtils.getDefaultFillColor());
    public final ColorSetting    outlineColor = new ColorSetting("OutlineColor", "Box outline.", new CategorySetting.Visibility(renderCategory), ColorUtils.getDefaultOutlineColor());

    private final List<BlockPos> renderSpots = new ArrayList<>();
    private final BlockBudget budget = new BlockBudget();

    @Override
    public void onDisable() {
        renderSpots.clear();
        budget.reset();
    }

    @SubscribeEvent
    public void onUpdate(PlayerUpdateEvent event) {
        if (getNull() || mc.player.isDead()) return;

        renderSpots.clear();
        BlockPos head = mc.player.getBlockPos().up();

        if (reactive.getValue()) reactBreak(head);

        if (proactive.getValue()) proactiveFill(head);
    }

    private List<BlockPos> vulnerableSpots(BlockPos head) {
        List<BlockPos> spots = new ArrayList<>();
        for (Direction dir : Direction.Type.HORIZONTAL) spots.add(head.offset(dir));
        if (coverAbove.getValue()) {
            spots.add(head.up());
            for (Direction dir : Direction.Type.HORIZONTAL) spots.add(head.up().offset(dir));
        }
        return spots;
    }

    private void proactiveFill(BlockPos head) {
        int obsidianSlot = InventoryUtils.findHotbar(Items.OBSIDIAN);
        if (obsidianSlot < 0) return;

        Vec3d eye = mc.player.getEyePos();
        double rangeSq = range.getValue().doubleValue() * range.getValue().doubleValue();
        budget.newTick();

        for (BlockPos pos : vulnerableSpots(head)) {
            if (!mc.world.getBlockState(pos).isAir()) continue;
            if (!WorldUtils.isPlaceable(pos)) continue;
            if (eye.squaredDistanceTo(pos.toCenterPos()) > rangeSq) continue;

            renderSpots.add(pos.toImmutable());
            if (!budget.canPlace(instant.getValue(), blocksPerTick.getValue().intValue())) continue;

            placeObsidian(pos, obsidianSlot);
            budget.consume();
        }
    }

    private void placeObsidian(BlockPos pos, int slot) {
        Direction direction = WorldUtils.getDirection(pos, false);
        if (rotate.getValue()) {
            Vec3d hit = pos.toCenterPos();
            Anatorius.ROTATION_MANAGER.packetRotate(
                    dev.project.anatorius.utils.rotations.RotationUtils.getRotations(hit.x, hit.y, hit.z, false),
                    Anatorius.ROTATION_MANAGER.getModulePriority(this));
        }
        int previousSlot = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);
        WorldUtils.placeBlock(pos, direction, Hand.MAIN_HAND, false, false, render.getValue());
        InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
    }

    private void reactBreak(BlockPos head) {
        Vec3d eye = mc.player.getEyePos();
        double rangeSq = range.getValue().doubleValue() * range.getValue().doubleValue();

        List<BlockPos> candidates = new ArrayList<>();
        for (Direction dir : Direction.Type.HORIZONTAL) {
            candidates.add(head.offset(dir, 2));
            candidates.add(head.offset(dir));
            if (coverAbove.getValue()) candidates.add(head.up().offset(dir));
        }

        for (BlockPos pos : candidates) {
            BlockState state = mc.world.getBlockState(pos);
            if (!isPiston(state)) continue;
            if (state.contains(Properties.EXTENDED) && state.get(Properties.EXTENDED)) continue;
            if (eye.squaredDistanceTo(pos.toCenterPos()) > rangeSq) continue;

            renderSpots.add(pos.toImmutable());
            minePiston(pos, state);
            return;
        }
    }

    private boolean isPiston(BlockState state) {
        return state.isOf(Blocks.PISTON) || state.isOf(Blocks.STICKY_PISTON);
    }

    private void minePiston(BlockPos pos, BlockState state) {
        Direction face = state.contains(Properties.FACING) ? state.get(Properties.FACING).getOpposite() : Direction.UP;
        if (rotate.getValue()) {
            Vec3d hit = pos.toCenterPos();
            Anatorius.ROTATION_MANAGER.packetRotate(
                    dev.project.anatorius.utils.rotations.RotationUtils.getRotations(hit.x, hit.y, hit.z, false),
                    Anatorius.ROTATION_MANAGER.getModulePriority(this));
        }
        mc.interactionManager.updateBlockBreakingProgress(pos, face);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        if (getNull() || !render.getValue()) return;
        for (BlockPos pos : renderSpots) {
            Box box = new Box(pos);
            Renderer3D.renderBox(event.getMatrices(), box, fillColor.getColor());
            Renderer3D.renderBoxOutline(event.getMatrices(), box, outlineColor.getColor());
        }
    }
}
