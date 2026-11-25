package dev.project.anatorius.modules.impl.combat;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.minecraft.NetworkUtils;
import dev.project.anatorius.utils.minecraft.ItemUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

@RegisterModule(name = "AutoXp", description = "Automatically switches to experience bottles and throws them.", category = Module.Category.COMBAT
)
public class AutoXp extends Module {
    public ModeSetting autoSwitch = new ModeSetting("Switch", "The mode that will be used for automatically switching to necessary items.", "Silent", InventoryUtils.SWITCH_MODES);
    public NumberSetting delay = new NumberSetting("Delay", "The delay in ticks between throwing experience bottles (0 = every tick; NCPStrict forces at least 3).", 4, 0, 20);
    public NumberSetting repeat = new NumberSetting("Repeat", "Allows you to throw a lot more XP bottles at once.", 1, 1, 15);
    public ModeSetting antiWaste = new ModeSetting("AntiWaste", "How wasting of experience bottles should be prevented.", "Avoid", new String[]{"None", "Avoid", "Disable"});
    public ModeSetting rotate = new ModeSetting("Rotate", "Rotate Xp", "Packet", new String[]{"None", "Packet", "Normal"});
    public BooleanSetting ncpStrict = new BooleanSetting("NCPStrict", "Uses slower, vanilla-like timing for stricter servers.", true);
    public BooleanSetting itemDisable = new BooleanSetting("ItemDisable", "Automatically disables the module when you run out of XP.", true);

    private int ticks = 0;
    private int pendingSwitchSlot = -1;
    private int pendingPreviousSlot = -1;

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (pendingSwitchSlot != -1) {
            InventoryUtils.switchBack(autoSwitch.getValue(), pendingSwitchSlot, pendingPreviousSlot);
            pendingSwitchSlot = -1;
            pendingPreviousSlot = -1;
            return;
        }

        if (autoSwitch.getValue().equalsIgnoreCase("None") && mc.player.getMainHandStack().getItem() != Items.EXPERIENCE_BOTTLE) {
            Anatorius.CHAT_MANAGER.tagged("You are currently not holding any experience bottles.", getName());
            setToggled(false);
            return;
        }

        int effectiveDelay = ncpStrict.getValue() ? Math.max(delay.getValue().intValue(), 3) : delay.getValue().intValue();
        if (ticks < effectiveDelay) {
            ticks++;
            return;
        }

        if (!needsExperience() && !antiWaste.getValue().equals("None")) {
            if (antiWaste.getValue().equals("Disable")) setToggled(false);
            return;
        }

        int slot = InventoryUtils.find(Items.EXPERIENCE_BOTTLE, 0, autoSwitch.getValue().equalsIgnoreCase("AltSwap") ? 35 : 8);
        int previousSlot = mc.player.getInventory().getSelectedSlot();

        if (slot == -1) {
            Anatorius.CHAT_MANAGER.tagged("No experience bottles could be found in your hotbar.", getName());
            setToggled(false);
            return;
        }

        InventoryUtils.switchSlot(autoSwitch.getValue(), slot, previousSlot);

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();
        if (!rotate.getValue().equalsIgnoreCase("None")) {
            Vec3d velocity = mc.player.getVelocity();
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
        int throwsThisTick = ncpStrict.getValue() ? 1 : repeat.getValue().intValue();
        for (int i = 0; i < throwsThisTick; i++) {
            NetworkUtils.sendSequencedPacket(sequence -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, finalYaw, finalPitch));
        }
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        if (ncpStrict.getValue()) {
            pendingSwitchSlot = slot;
            pendingPreviousSlot = previousSlot;
        } else {
            InventoryUtils.switchBack(autoSwitch.getValue(), slot, previousSlot);
        }

        ticks = 0;
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) setToggled(false);
    }

    private boolean needsExperience() {
        for (ItemStack stack : ItemUtils.getArmorItems(mc.player)) {
            if (!stack.isEmpty() && ItemUtils.isArmor(stack) && (Math.round(((stack.getMaxDamage() - stack.getDamage()) * 100.0f) / stack.getMaxDamage()) < 100.0f)) {
                return true;
            }
        }

        return false;
    }
}
