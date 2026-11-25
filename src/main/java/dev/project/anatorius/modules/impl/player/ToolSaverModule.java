package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.AttackBlockEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

@RegisterModule(name = "ToolSaver", description = "Keeps your tools from breaking: switches away from one about to.", category = Module.Category.PLAYER)
public class ToolSaverModule extends Module {
    private static ToolSaverModule INSTANCE;

    public NumberSetting durability = new NumberSetting("Durability", "Durability left (percent) at which a tool isn't used anymore.", 5, 1, 50);

    public ToolSaverModule() {
        INSTANCE = this;
    }

    public static boolean safeToUse(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable() || stack.contains(DataComponentTypes.EQUIPPABLE) || stack.isOf(Items.ELYTRA)) return true;
        double limit = INSTANCE == null ? 5.0 : INSTANCE.durability.getValue().doubleValue();
        return (stack.getMaxDamage() - stack.getDamage()) * 100.0 / stack.getMaxDamage() > limit;
    }

    public static boolean active() {
        return INSTANCE != null && INSTANCE.isToggled();
    }

    @SubscribeEvent
    public void onAttackBlock(AttackBlockEvent event) {
        if (mc.player == null) return;
        int selected = mc.player.getInventory().getSelectedSlot();
        if (safeToUse(mc.player.getInventory().getStack(selected))) return;
        InventoryUtils.switchSlot("Normal", selected == 8 ? 7 : selected + 1, selected);
        event.setCancelled(true);
    }
}
