package dev.project.anatorius.utils.minecraft;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.mixins.accessors.ClientPlayerInteractionManagerAccessor;
import dev.project.anatorius.modules.impl.core.AntiCheatModule;
import dev.project.anatorius.utils.IMinecraft;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;

public class InventoryUtils implements IMinecraft {
    public static String[] SWITCH_MODES = new String[]{"None", "Normal", "Silent", "AltSwap"};
    public static String[] SWAP_MODES = new String[]{"Pickup", "Swap"};

    public static int HOTBAR_START = 0;
    public static int HOTBAR_END = 8;

    public static int INVENTORY_START = 9;
    public static int INVENTORY_END = 35;

    private static String effectiveMode(String mode) {
        AntiCheatModule antiCheat = Anatorius.MODULE_MANAGER == null ? null : Anatorius.MODULE_MANAGER.getModule(AntiCheatModule.class);
        if (antiCheat != null && antiCheat.noServerSlot.getValue() && mode.equalsIgnoreCase("Silent")) return "Normal";
        return mode;
    }

    private static int pending;
    private static int pendingTick;
    private static final SwapStack SWAPS = new SwapStack();

    public static boolean hasPendingSilentSwitch() {
        return (pending > 0 && pendingTick == tickId()) || SWAPS.hasPending(tickId());
    }

    public static int openSwaps() {
        return (pending > 0 && pendingTick == tickId() ? pending : 0) + SWAPS.size();
    }

    public static String debugState() {
        return "selected=" + (mc.player == null ? -1 : mc.player.getInventory().getSelectedSlot()) + " server=" + serverSlot() + " open=" + openSwaps();
    }

    private static int tickId() {
        return mc.player == null ? 0 : mc.player.age;
    }

    private static int serverSlot() {
        return Anatorius.POSITION_MANAGER.getServerSlot();
    }

    public static void reassertServerSlot(int slot) {
        if (mc.player == null || slot < 0 || slot > 8 || mc.getNetworkHandler() == null) return;
        if (serverSlot() != slot) mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    public static void endOfTick() {
        if (pendingTick != tickId()) pending = 0;
        SWAPS.prune(tickId());
    }

    public static void switchSlot(String mode, int slot, int previousSlot) {
        if (mode.equalsIgnoreCase("None") || mc.player == null) return;
        if (slot == -1 || previousSlot == -1) return;
        if (slot == serverSlot() && effectiveMode(mode).equals("Silent")) { opened(); return; }

        switch (effectiveMode(mode)) {
            case "Normal" -> {
                if (slot < 0 || slot > 8) return;
                mc.player.getInventory().setSelectedSlot(slot);
                ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).invokeSyncSelectedSlot();
                if (serverSlot() != slot) mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
            case "Silent" -> {
                if (slot < 0 || slot > 8) return;
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                opened();
            }
            case "AltSwap"   -> { swap("Swap",   slot, previousSlot); opened(); }
        }
    }

    private static void opened() {
        if (pendingTick != tickId()) pending = 0;
        pendingTick = tickId();
        pending++;
    }

    private static void closed() {
        if (pending > 0) pending--;
    }

    public static void switchBack(String mode, int slot, int previousSlot) {
        if (mode.equalsIgnoreCase("None") || mc.player == null) return;
        if (previousSlot == -1) return;

        switch (effectiveMode(mode)) {
            case "Normal" -> {
                if (previousSlot < 0 || previousSlot > 8) return;
                mc.player.getInventory().setSelectedSlot(previousSlot);
                ((ClientPlayerInteractionManagerAccessor) mc.interactionManager).invokeSyncSelectedSlot();
                if (serverSlot() != previousSlot) mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            }
            case "Silent" -> {
                closed();
                if (previousSlot < 0 || previousSlot > 8) return;
                if (previousSlot == serverSlot()) return;
                mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
            }
            case "AltSwap"   -> { closed(); swap("Swap",   slot, previousSlot); }
        }
    }

    public static void swap(String mode, int slot, int targetSlot) {
        switch (mode) {
            case "Pickup" -> {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, indexToSlot(slot),      0,         SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, indexToSlot(targetSlot), 0,         SlotActionType.PICKUP, mc.player);
                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty())
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, indexToSlot(slot),      0,         SlotActionType.PICKUP, mc.player);
            }
            case "Swap" -> {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, indexToSlot(slot), targetSlot, SlotActionType.SWAP, mc.player);
            }
        }
    }

    public static int indexToSlot(int index) {
        if (index >= 0 && index <= 8) return 36 + index;
        return index;
    }

    public static int find(Item item) { return find(item, HOTBAR_START, INVENTORY_END); }
    public static int findHotbar(Item item) { return find(item, HOTBAR_START, HOTBAR_END); }
    public static int findInventory(Item item) { return find(item, INVENTORY_START, INVENTORY_END); }
    public static int find(Item item, int start, int end) {
        for (int i = end; i >= start; i--) {
            if (mc.player.getInventory().getStack(i).getItem() != item) continue;
            return i;
        }
        return -1;
    }

    public static int find(Class<? extends Item> item) { return find(item, HOTBAR_START, INVENTORY_END); }
    public static int findHotbar(Class<? extends Item> item) { return find(item, HOTBAR_START, HOTBAR_END); }
    public static int findInventory(Class<? extends Item> item) { return find(item, INVENTORY_START, INVENTORY_END); }
    public static int find(Class<? extends Item> item, int start, int end) {
        for (int i = end; i >= start; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!item.isAssignableFrom(stack.getItem().getClass())) continue;
            return i;
        }
        return -1;
    }

    public static int find(java.util.function.Predicate<ItemStack> predicate, int start, int end) {
        for (int i = end; i >= start; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !predicate.test(stack)) continue;
            return i;
        }
        return -1;
    }

    public static int findInventory(Item item, int count) {
        for (int i = INVENTORY_END; i >= INVENTORY_START; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() != item) continue;
            if (stack.getCount() < count) continue;
            return i;
        }
        return -1;
    }

    public static int findHardestBlock(int start, int end) {
        float bestHardness = -1;
        int bestSlot = -1;
        for (int i = start; i <= end; i++) {
            if (!(mc.player.getInventory().getStack(i).getItem() instanceof BlockItem item)) continue;
            float hardness = item.getBlock().getHardness();
            if (hardness == -1) return i;
            if (hardness > bestHardness) { bestHardness = hardness; bestSlot = i; }
        }
        return bestSlot;
    }

    public static int findHardestObsidian(int start, int end) {
        float bestObsidian = -1;
        int bestSlot = -1;
        for (int i = start; i <= end; i++) {
            if (mc.player.getInventory().getStack(i).getItem() != Items.OBSIDIAN) continue;
            float hardness = Blocks.OBSIDIAN.getHardness();
            if (hardness == -1) return i;
            if (hardness > bestObsidian) { bestObsidian = hardness; bestSlot = i; }
        }
        return bestSlot;
    }

    public static int findFastestItem(BlockState blockState, int start, int end) {
        double bestScore = -1;
        int bestSlot = -1;
        for (int i = start; i <= end; i++) {
            double score = mc.player.getInventory().getStack(i).getMiningSpeedMultiplier(blockState);
            if (score > bestScore) { bestScore = score; bestSlot = i; }
        }
        return bestSlot;
    }

    public static int findBestSword(int start, int end) {
        int netheriteSlot = -1, diamondSlot = -1, ironSlot = -1, goldenSlot = -1, stoneSlot = -1, woodenSlot = -1;
        for (int i = end; i >= start; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.NETHERITE_SWORD) netheriteSlot = i;
            if (stack.getItem() == Items.DIAMOND_SWORD)   diamondSlot   = i;
            if (stack.getItem() == Items.IRON_SWORD)      ironSlot      = i;
            if (stack.getItem() == Items.GOLDEN_SWORD)    goldenSlot    = i;
            if (stack.getItem() == Items.STONE_SWORD)     stoneSlot     = i;
            if (stack.getItem() == Items.WOODEN_SWORD)    woodenSlot    = i;
        }
        if (netheriteSlot != -1) return netheriteSlot;
        if (diamondSlot   != -1) return diamondSlot;
        if (ironSlot      != -1) return ironSlot;
        if (goldenSlot    != -1) return goldenSlot;
        if (stoneSlot     != -1) return stoneSlot;
        return woodenSlot;
    }

    public static int findEmptySlot(int start, int end) {
        for (int i = end; i >= start; i--) {
            if (!mc.player.getInventory().getStack(i).isEmpty()) continue;
            return i;
        }
        return -1;
    }

    public static int findChestplate() {
        for (int i = INVENTORY_END; i >= INVENTORY_START; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (item != Items.ELYTRA && (
                    item == Items.NETHERITE_CHESTPLATE ||
                    item == Items.DIAMOND_CHESTPLATE   ||
                    item == Items.IRON_CHESTPLATE      ||
                    item == Items.CHAINMAIL_CHESTPLATE ||
                    item == Items.GOLDEN_CHESTPLATE    ||
                    item == Items.LEATHER_CHESTPLATE
            )) return i;
        }
        for (int i = HOTBAR_END; i >= HOTBAR_START; i--) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (item != Items.ELYTRA && (
                    item == Items.NETHERITE_CHESTPLATE ||
                    item == Items.DIAMOND_CHESTPLATE   ||
                    item == Items.IRON_CHESTPLATE      ||
                    item == Items.CHAINMAIL_CHESTPLATE ||
                    item == Items.GOLDEN_CHESTPLATE    ||
                    item == Items.LEATHER_CHESTPLATE
            )) return i;
        }
        return -1;
    }

    public static void swapArmor(int armorSlotIndex, int inventorySlot) {
        if (mc.player == null || inventorySlot == -1) return;
        int syncId    = mc.player.playerScreenHandler.syncId;
        int armorSlot = 8 - armorSlotIndex;
        if (inventorySlot >= HOTBAR_START && inventorySlot <= HOTBAR_END) {
            mc.interactionManager.clickSlot(syncId, armorSlot, inventorySlot, SlotActionType.SWAP, mc.player);
            return;
        }
        mc.interactionManager.clickSlot(syncId, armorSlot,              0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, indexToSlot(inventorySlot), 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, armorSlot,              0, SlotActionType.PICKUP, mc.player);
    }

    public static boolean inInventoryScreen() {
        return mc.currentScreen instanceof InventoryScreen
                || mc.currentScreen instanceof CreativeInventoryScreen
                || mc.currentScreen instanceof GenericContainerScreen
                || mc.currentScreen instanceof ShulkerBoxScreen;
    }
}
