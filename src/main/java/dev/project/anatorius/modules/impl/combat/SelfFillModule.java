package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.PositionUtils;
import dev.project.anatorius.utils.minecraft.WorldUtils;
import dev.project.anatorius.utils.rotations.RotationUtils;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

@RegisterModule(name = "SelfFill", description = "Automatically places a block in the spot that you were previously in.", category = Module.Category.COMBAT)
public class SelfFillModule extends Module {
    public ModeSetting autoSwitch = new ModeSetting("Switch", "The mode that will be used for automatically switching to necessary items.", "Silent", InventoryUtils.SWITCH_MODES);
    public ModeSetting jumpMode = new ModeSetting("JumpMode", "The mode that will be used for jumping.", "Normal", new String[]{"Normal", "Packet"});
    public ModeSetting burrow = new ModeSetting("Burrow", "Teleports you inside of the block that was placed.", new ModeSetting.Visibility(jumpMode, "Packet"), "None", new String[]{"None", "Bypass"});
    public BooleanSetting obsidianOnly = new BooleanSetting("ObsidianOnly", "Only places using obsidian.", false);
    public BooleanSetting rotate = new BooleanSetting("Rotate", "Whether or not to rotate when placing the block.", true);
    public BooleanSetting strictDirection = new BooleanSetting("StrictDirection", "Only places using directions that face you.", false);
    public BooleanSetting crystalDestruction = new BooleanSetting("Attack", "Breaks the crystals in the way before placing.", true);

    public BooleanSetting render = new BooleanSetting("Render", "Whether or not to render the place position.", true);

    private BlockPos lastPosition = null;

    private boolean jumped = false;
    private boolean rotatedBypass = false;
    private int ticks;

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (lastPosition == null) {
            setToggled(false);
            return;
        }

        if (jumpMode.getValue().equalsIgnoreCase("Normal")) {
            if (!jumped) {
                mc.player.jump();
                jumped = true;
                ticks = 0;
                return;
            }

            if (ticks++ < 3) return;
        }

        Direction direction = WorldUtils.getDirection(lastPosition, strictDirection.getValue());
        if (direction == null) {
            setToggled(false);
            return;
        }

        if (jumpMode.getValue().equalsIgnoreCase("Packet") && burrow.getValue().equalsIgnoreCase("Bypass")) {
            if (!rotatedBypass) {
                float[] rotations = RotationUtils.getRotations(WorldUtils.getHitVector(lastPosition, direction), false);
                Anatorius.ROTATION_MANAGER.rotate(rotations, this);
                Anatorius.ROTATION_MANAGER.packetRotate(rotations, Anatorius.ROTATION_MANAGER.getModulePriority(this));
                rotatedBypass = true;
                ticks = 0;
                return;
            }

            if (ticks++ < 3) return;
        }

        if (autoSwitch.getValue().equalsIgnoreCase("None") && (!(mc.player.getMainHandStack().getItem() instanceof BlockItem) || (obsidianOnly.getValue() && mc.player.getMainHandStack().getItem() != Items.OBSIDIAN))) {
            Anatorius.CHAT_MANAGER.tagged("You are currently not holding any " + (obsidianOnly.getValue() ? "valid " : "") + "blocks.", getName());
            setToggled(false);
            return;
        }

        if (!mc.world.getBlockState(lastPosition).isReplaceable()) return;
        if (!mc.world.getOtherEntities(null, new Box(lastPosition), e -> e != mc.player && !(e instanceof ExperienceOrbEntity) && !(e instanceof ItemEntity) && !(e instanceof EndCrystalEntity)).isEmpty()) return;

        int slot = InventoryUtils.findHardestBlock(0, autoSwitch.getValue().equalsIgnoreCase("AltSwap") ? 35 : 8);
        int previousSlot = mc.player.getInventory().getSelectedSlot();

        if (obsidianOnly.getValue()) slot = InventoryUtils.find(Items.OBSIDIAN, 0, autoSwitch.getValue().equalsIgnoreCase("AltSwap") ? 35 : 8);

        if (!autoSwitch.getValue().equalsIgnoreCase("None") && slot == -1) {
            Anatorius.CHAT_MANAGER.tagged("There are currently no " + (obsidianOnly.getValue() ? "valid " : "") + "blocks in your hotbar.", getName());
            setToggled(false);
            return;
        }

        if (jumpMode.getValue().equals("Packet")) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.4, mc.player.getZ(), false, mc.player.horizontalCollision));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.75, mc.player.getZ(), false, mc.player.horizontalCollision));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.01, mc.player.getZ(), false, mc.player.horizontalCollision));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + (jumpMode.getValue().equalsIgnoreCase("Packet") && burrow.getValue().equalsIgnoreCase("Bypass") ? 0.99999992 : 1.15), mc.player.getZ(), false, mc.player.horizontalCollision));
        }

        InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);
        WorldUtils.placeBlock(lastPosition, direction, Hand.MAIN_HAND, !(jumpMode.getValue().equalsIgnoreCase("Packet") && burrow.getValue().equalsIgnoreCase("Bypass")) && rotate.getValue(), crystalDestruction.getValue(), render.getValue(), Anatorius.ROTATION_MANAGER.getModulePriority(this));
        InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);

        if (jumpMode.getValue().equals("Packet") && burrow.getValue().equalsIgnoreCase("Bypass")) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 1.15, mc.player.getZ(), mc.player.isOnGround(), mc.player.horizontalCollision));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), mc.player.horizontalCollision));
            mc.player.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }

        setToggled(false);

        jumped = false;
        rotatedBypass = false;
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) {
            setToggled(false);
            return;
        }

        if (!mc.player.isOnGround()) {
            Anatorius.CHAT_MANAGER.tagged("You are currently in the air.", getName());
            setToggled(false);
            return;
        }

        lastPosition = PositionUtils.getFlooredPosition(mc.player);
    }
}
