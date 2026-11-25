package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.settings.impl.WhitelistSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.NetworkUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.potion.Potion;
import net.minecraft.util.Hand;

@RegisterModule(name = "AutoPot", description = "Automatically switches to splash potions and throws them.", category = Module.Category.COMBAT)
public class AutoPot extends Module {
    public ModeSetting autoSwitch = new ModeSetting("Switch", "The mode that will be used for automatically switching to necessary items.", "Silent", InventoryUtils.SWITCH_MODES);
    public ModeSetting rotate = new ModeSetting("Rotate", "Rotate Potion", "Packet", new String[]{"None", "Packet", "Normal"});
    public BooleanSetting ncpStrict = new BooleanSetting("NCPStrict", "Uses slower, vanilla-like timing for stricter servers.", true);
    public WhitelistSetting whitelist = new WhitelistSetting("Whitelist", "Throws only the potions that are on this list. If empty, every splash potion will be thrown.", WhitelistSetting.Type.POTIONS);

    private int pendingSwitchSlot = -1;
    private int pendingPreviousSlot = -1;

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (pendingSwitchSlot != -1) {
            String mode = autoSwitch.getValue();
            if (pendingSwitchSlot > 8 && (mode.equalsIgnoreCase("None") || mode.equalsIgnoreCase("Normal") || mode.equalsIgnoreCase("Silent"))) {
                mode = "AltSwap";
            }
            InventoryUtils.switchBack(mode, pendingSwitchSlot, pendingPreviousSlot);
            pendingSwitchSlot = -1;
            pendingPreviousSlot = -1;
            setToggled(false);
            return;
        }

        if (autoSwitch.getValue().equalsIgnoreCase("None")) {
            boolean valid = false;
            ItemStack held = mc.player.getMainHandStack();
            if (held.getItem() == Items.SPLASH_POTION) {
                if (whitelist.getWhitelist().isEmpty()) {
                    valid = true;
                } else {
                    PotionContentsComponent contents = held.get(DataComponentTypes.POTION_CONTENTS);
                    if (contents != null && contents.potion().isPresent()) {
                        Potion potion = contents.potion().get().value();
                        if (whitelist.isWhitelistContains(potion)) {
                            valid = true;
                        }
                    }
                }
            }
            if (!valid) {
                Anatorius.CHAT_MANAGER.tagged("You are currently not holding any valid splash potions.", getName());
                setToggled(false);
                return;
            }
        }

        int slot = InventoryUtils.find((stack) -> {
            if (stack.getItem() != Items.SPLASH_POTION) return false;
            if (whitelist.getWhitelist().isEmpty()) return true;

            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents != null && contents.potion().isPresent()) {
                Potion potion = contents.potion().get().value();
                return whitelist.isWhitelistContains(potion);
            }
            return false;
        }, 0, 35);

        if (slot == -1) {
            Anatorius.CHAT_MANAGER.tagged("No valid splash potions could be found.", getName());
            setToggled(false);
            return;
        }

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        String switchMode = autoSwitch.getValue();
        if (slot > 8 && (switchMode.equalsIgnoreCase("None") || switchMode.equalsIgnoreCase("Normal") || switchMode.equalsIgnoreCase("Silent"))) {
            switchMode = "AltSwap";
        }

        InventoryUtils.switchSlot(switchMode, slot, previousSlot);

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        if (!rotate.getValue().equalsIgnoreCase("None")) {
            pitch = 85.0f;

            float[] rotations = new float[]{yaw, pitch};
            if (rotate.getValue().equalsIgnoreCase("Packet")) {
                Anatorius.ROTATION_MANAGER.packetRotate(rotations, Anatorius.ROTATION_MANAGER.getModulePriority(this));
            } else if (rotate.getValue().equalsIgnoreCase("Normal")) {
                Anatorius.ROTATION_MANAGER.rotate(rotations, this, Anatorius.ROTATION_MANAGER.getModulePriority(this));
            }
        }

        float finalYaw = yaw;
        float finalPitch = pitch;
        NetworkUtils.sendSequencedPacket(sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, finalYaw, finalPitch));
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        if (ncpStrict.getValue()) {
            pendingSwitchSlot = slot;
            pendingPreviousSlot = previousSlot;
        } else {
            InventoryUtils.switchBack(switchMode, slot, previousSlot);
            setToggled(false);
        }
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) setToggled(false);
    }
}
