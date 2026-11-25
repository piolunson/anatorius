package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.managers.KitManager;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@RegisterModule(name = "InventorySorter", description = "Sorts the items in your inventory.", category = Module.Category.PLAYER)
public class InventorySorterModule extends Module {
    public ModeSetting scope = new ModeSetting("Scope", "Which part of the inventory it sorts.", "Inventory", new String[]{"Inventory", "Hotbar", "Both"});
    public ModeSetting mode = new ModeSetting("Mode", "Type: by creative tab. Name: alphabetically. Kit: into the slots of your current kit (kit save).", "Type", new String[]{"Type", "Name", "Kit"});
    public NumberSetting delay = new NumberSetting("Delay", "Milliseconds between swaps.", 50, 50, 500);
    public NumberSetting frequency = new NumberSetting("Frequency", "Swaps at a time.", 1, 1, 3);
    public BooleanSetting silent = new BooleanSetting("Silent", "Sorts without the inventory open. Off: only while it is.", false);
    public BooleanSetting autoDisable = new BooleanSetting("AutoDisable", "Turns itself off once everything is in order.", false);

    private long lastSwapAt;

    private int scopeStart() {
        return scope.getValue().equals("Inventory") ? 9 : 0;
    }

    private int scopeEnd() {
        return scope.getValue().equals("Hotbar") ? 9 : 36;
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
        if (mode.getValue().equals("Kit") && KitManager.get().current() == null) return;
        if (!silent.getValue() && !(mc.currentScreen instanceof HandledScreen<?>)) return;
        if (System.currentTimeMillis() - lastSwapAt < delay.getValue().longValue()) return;

        int[] targets = targetSlots();
        Integer[] sources = sourceSlots();
        int swaps = 0;
        for (int i = 0; i < Math.min(sources.length, targets.length); i++) {
            int source = sources[i], target = targets[i];
            ItemStack sourceStack = mc.player.getInventory().getStack(source);
            ItemStack targetStack = mc.player.getInventory().getStack(target);
            if (source != target && !ItemStack.areItemsEqual(sourceStack, targetStack)) {
                swap(menuSlot(target), menuSlot(source));
                swaps++;
            }
            if (swaps >= frequency.getValue().intValue()) break;
            sources = sourceSlots();
        }
        if (swaps == 0 && autoDisable.getValue()) setToggled(false);
    }

    private int[] targetSlots() {
        if (scope.getValue().equals("Both") && !mode.getValue().equals("Kit")) {
            return IntStream.concat(IntStream.range(9, 36), IntStream.range(0, 9)).toArray();
        }
        return IntStream.range(scopeStart(), scopeEnd()).toArray();
    }

    private Integer[] sourceSlots() {
        if (mode.getValue().equals("Kit")) return kitSourceSlots();
        Comparator<Integer> order = mode.getValue().equals("Name")
                ? Comparator.comparing((Integer slot) -> stack(slot).getName().getString())
                : Comparator.comparingInt((Integer slot) -> creativeTab(stack(slot)));
        order = order.thenComparingInt(slot -> stack(slot).getDamage()).thenComparingInt(slot -> -stack(slot).getCount());
        return IntStream.range(scopeStart(), scopeEnd()).filter(slot -> !stack(slot).isEmpty()).boxed().sorted(order).toArray(Integer[]::new);
    }

    private Integer[] kitSourceSlots() {
        KitManager.Kit kit = KitManager.get().current();
        int start = scopeStart(), end = scopeEnd();
        List<Integer> slots = new ArrayList<>(IntStream.range(start, end).boxed().toList());
        List<ItemStack> stacks = new ArrayList<>(slots.stream().map(this::stack).toList());
        boolean[] claimed = new boolean[stacks.size()];
        for (Map.Entry<Integer, KitManager.Slot> entry : kit.getSlots().entrySet()) {
            int kitSlot = entry.getKey();
            if (kitSlot < start || kitSlot >= end) continue;
            int target = kitSlot - start;
            var wanted = Registries.ITEM.get(Identifier.of(entry.getValue().itemId()));
            if (!stacks.get(target).isEmpty() && stacks.get(target).getItem() == wanted) {
                claimed[target] = true;
                continue;
            }
            for (int candidate = 0; candidate < stacks.size(); candidate++) {
                if (claimed[candidate] || candidate == target) continue;
                if (!stacks.get(candidate).isEmpty() && stacks.get(candidate).getItem() == wanted) {
                    Collections.swap(slots, target, candidate);
                    Collections.swap(stacks, target, candidate);
                    claimed[target] = true;
                    break;
                }
            }
        }
        return slots.toArray(Integer[]::new);
    }

    private ItemStack stack(int slot) {
        return mc.player.getInventory().getStack(slot);
    }

    private int creativeTab(ItemStack stack) {
        List<ItemGroup> groups = ItemGroups.getGroups();
        if (!groups.isEmpty() && !groups.getFirst().hasStacks() && mc.player.networkHandler != null) {
            ItemGroups.updateDisplayContext(mc.player.networkHandler.getEnabledFeatures(), true, mc.world.getRegistryManager());
        }
        ItemStack plain = stack.getItem().getDefaultStack();
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).contains(plain)) return i;
        return groups.size();
    }

    private static int menuSlot(int inventorySlot) {
        return inventorySlot < 9 ? 36 + inventorySlot : inventorySlot;
    }

    private void swap(int first, int second) {
        lastSwapAt = System.currentTimeMillis();
        int syncId = mc.player.playerScreenHandler.syncId;
        if (second >= 36) {
            mc.interactionManager.clickSlot(syncId, first, second - 36, SlotActionType.SWAP, mc.player);
        } else if (first >= 36) {
            mc.interactionManager.clickSlot(syncId, second, first - 36, SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(syncId, first, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, second, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(syncId, first, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    @Override
    public String getMetaData() {
        return mode.getValue();
    }
}
