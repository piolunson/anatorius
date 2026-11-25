package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.NetworkUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;

@RegisterModule(name = "AutoPearl", description = "Automatically switches to pearls and throws them.", category = Module.Category.PLAYER)
public class AutoPearlModule extends Module {
    public ModeSetting autoSwitch = new ModeSetting("Switch", "The mode that will be used for automatically switching to necessary items.", "Silent", InventoryUtils.SWITCH_MODES);
    public BooleanSetting rotate = new BooleanSetting("Rotate", "Sends a packet rotation right before throwing the pearl.", true);

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) {
            setToggled(false);
            return;
        }

        if (autoSwitch.getValue().equalsIgnoreCase("None") && mc.player.getMainHandStack().getItem() != Items.ENDER_PEARL) {
            Anatorius.CHAT_MANAGER.tagged("You are currently not holding any pearls.", getName());
            setToggled(false);
            return;
        }

        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(Items.ENDER_PEARL))) {
            setToggled(false);
            return;
        }

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        String mode = autoSwitch.getValue();

        int slot = mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL ? previousSlot : InventoryUtils.findHotbar(Items.ENDER_PEARL);
        if (slot == -1 && !mode.equalsIgnoreCase("None")) {
            slot = InventoryUtils.findInventory(Items.ENDER_PEARL);
            if (slot != -1) mode = "AltSwap";
        }

        if (slot == -1) {
            Anatorius.CHAT_MANAGER.tagged("No pearls could be found in your inventory.", getName());
            setToggled(false);
            return;
        }

        InventoryUtils.switchSlot(mode, slot, previousSlot);

        Anatorius.ROTATION_MANAGER.packetRotate(mc.player.getYaw(), mc.player.getPitch());
        NetworkUtils.sendSequencedPacket(sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, mc.player.getYaw(), mc.player.getPitch()));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        InventoryUtils.switchBack(mode, slot, previousSlot);

        setToggled(false);
    }
}
