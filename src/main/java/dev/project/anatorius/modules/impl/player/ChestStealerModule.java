package dev.project.anatorius.modules.impl.player;

import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.UpdateMovementEvent;
import dev.project.anatorius.managers.KitManager;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.settings.impl.WhitelistSetting;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RegisterModule(name = "ChestStealer", description = "Steals items from the storages you open, or refills your kit from them.", category = Module.Category.PLAYER)
public class ChestStealerModule extends Module {
    private static final String[] MODES = {"Steal", "Fill", "Drop", "Refill", "Rekit"};

    public BooleanSetting onlyButtons = new BooleanSetting("OnlyButtons", "Adds a button per mode next to the chest instead; nothing moves until you click one.", false);
    public ModeSetting mode = new ModeSetting("Mode", "Steal: takes everything. Fill: puts your inventory in. Drop: throws the contents out. Refill: tops up the stacks you carry. Rekit: puts your current kit (kit save) back in your inventory, every item in its slot (shulker boxes, or chests with OnlyButtons).", new BooleanSetting.Visibility(onlyButtons, false), "Steal", MODES);
    public ModeSetting select = new ModeSetting("Select", "Which items it moves. Any: all of them. WhiteList: only the ones on the list. BlackList: all but those.", "Any", new String[]{"Any", "WhiteList", "BlackList"});
    public WhitelistSetting items = new WhitelistSetting("Whitelist", "Items", "The items Select goes by.", new ModeSetting.Visibility(select, "WhiteList", "BlackList"), WhitelistSetting.Type.ITEMS);
    public NumberSetting delay = new NumberSetting("Delay", "Milliseconds between moves.", 50, 0, 1000);
    public NumberSetting timeout = new NumberSetting("Timeout", "Milliseconds after opening before it starts (the contents need to arrive first).", 200, 0, 750);
    public NumberSetting frequency = new NumberSetting("Frequency", "Moves at a time. Max: everything at once.", 1, 1, 6).withMaxLabel("Max");
    public BooleanSetting close = new BooleanSetting("Close", "Closes the storage once it's done.", false);

    private final Map<Integer, Long> cooldowns = new HashMap<>();
    private Screen trackedScreen;
    private long openedAt, lastMoveAt, lastTransferAt;
    private boolean hasTransferred;
    private String requestedMode;

    public ChestStealerModule() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!isToggled() || !onlyButtons.getValue()) return;
            int rows = storageRows(screen);
            if (rows < 0) return;
            requestedMode = null;
            int backgroundHeight = 114 + rows * 18;
            int buttonWidth = 45;
            int x = (screen.width - 176) / 2 - buttonWidth - 2;
            int y = (screen.height - backgroundHeight) / 2;
            for (String modeName : MODES) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(modeName), button -> {
                    requestedMode = modeName.equals(requestedMode) ? null : modeName;
                    if (requestedMode == null) screen.setFocused(null);
                }).dimensions(x, y, buttonWidth, 15).build());
                y += 17;
            }
        });
    }

    private static int storageRows(Screen screen) {
        if (screen instanceof GenericContainerScreen chest) return chest.getScreenHandler().getRows();
        if (screen instanceof ShulkerBoxScreen) return 3;
        return -1;
    }

    @Override
    public void onDisable() {
        cooldowns.clear();
        trackedScreen = null;
    }

    @SubscribeEvent
    public void onUpdateMovement(UpdateMovementEvent event) {
        if (mc.player == null || mc.interactionManager == null) return;
        Screen screen = mc.currentScreen;
        long now = System.currentTimeMillis();
        if (screen != trackedScreen) {
            trackedScreen = screen;
            openedAt = now;
            hasTransferred = false;
            cooldowns.clear();
        }
        int rows = storageRows(screen);
        if (rows < 0 || mc.player.currentScreenHandler == mc.player.playerScreenHandler) return;

        boolean shulkerScreen = screen instanceof ShulkerBoxScreen;
        String selected = onlyButtons.getValue() ? requestedMode : mode.getValue();
        if (selected == null) return;
        if (now - lastMoveAt < delay.getValue().longValue() || now - openedAt < timeout.getValue().longValue()) return;
        if (!shulkerScreen && selected.equals("Rekit") && !onlyButtons.getValue()) return;

        ScreenHandler handler = mc.player.currentScreenHandler;
        int containerSlots = rows * 9;
        int start = 0, count = containerSlots;
        boolean freeRekitSlot = false;
        if (selected.equals("Fill")) {
            start = containerSlots;
            count = 36;
        } else if (selected.equals("Rekit")) {
            freeRekitSlot = hasFreeRekitSlot(handler, containerSlots);
        }

        int transfers = 0;
        boolean transferred = false;
        for (int slot : transferSlots(handler, start, start + count, selected)) {
            ItemStack stack = handler.getSlot(slot).getStack();
            switch (selected) {
                case "Drop" -> click(handler, slot, 1, SlotActionType.THROW);
                case "Fill", "Steal" -> {
                    if (shulkerScreen && isShulker(stack)) continue;
                    click(handler, slot, 0, SlotActionType.QUICK_MOVE);
                }
                case "Refill" -> {
                    if (!refill(handler, slot, containerSlots)) continue;
                }
                case "Rekit" -> {
                    boolean moved = !freeRekitSlot ? refill(handler, slot, containerSlots) : false;
                    if (!moved && !moveRekitStack(handler, stack, slot, containerSlots)) continue;
                }
                default -> { continue; }
            }
            transferred = true;
            hasTransferred = true;
            lastMoveAt = now;
            transfers++;
            if (transfers >= frequency.getValue().intValue() && !frequency.isAtMaxLabel()) break;
        }

        if (transferred) lastTransferAt = now;
        if (close.getValue() && hasTransferred && now - lastTransferAt >= 100L) {
            mc.player.closeHandledScreen();
            hasTransferred = false;
        }
    }

    private void click(ScreenHandler handler, int slot, int button, SlotActionType type) {
        mc.interactionManager.clickSlot(handler.syncId, slot, button, type, mc.player);
    }

    private List<Integer> transferSlots(ScreenHandler handler, int start, int end, String selected) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = start; slot < end && slot < handler.slots.size(); slot++) {
            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty() || !selectAllows(stack)) continue;
            slots.add(slot);
        }
        if (selected.equals("Refill") || selected.equals("Rekit")) {
            slots.sort(Comparator.comparingDouble(slot -> priority(handler.getSlot(slot).getStack())));
        }
        return slots;
    }

    private boolean selectAllows(ItemStack stack) {
        return switch (select.getValue()) {
            case "WhiteList" -> items.isWhitelistContains(stack.getItem());
            case "BlackList" -> !items.isWhitelistContains(stack.getItem());
            default -> true;
        };
    }

    private static double priority(ItemStack stack) {
        if (!stack.isStackable()) {
            if (!stack.isDamageable()) return 0.0;
            return -(1.0 - stack.getDamage() / (double) stack.getMaxDamage());
        }
        return -stack.getCount();
    }

    private static boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean refill(ScreenHandler handler, int slot, int containerSlots) {
        ItemStack stack = handler.getSlot(slot).getStack();
        if (stack.isEmpty() || !stack.isStackable()) return false;
        List<Integer> targets = new ArrayList<>();
        int room = 0;
        for (int i = containerSlots; i < containerSlots + 36 && i < handler.slots.size(); i++) {
            ItemStack target = handler.getSlot(i).getStack();
            if (target.isEmpty() || target.getCount() >= target.getMaxCount() || !ItemStack.areItemsAndComponentsEqual(target, stack)) continue;
            targets.add(i);
            room += target.getMaxCount() - target.getCount();
        }
        if (targets.isEmpty()) return false;
        if (inventoryFull() || room >= stack.getCount()) {
            click(handler, slot, 0, SlotActionType.QUICK_MOVE);
            return true;
        }
        click(handler, slot, 0, SlotActionType.PICKUP);
        for (int target : targets) {
            click(handler, target, 0, SlotActionType.PICKUP);
            if (handler.getCursorStack().isEmpty()) break;
        }
        if (!handler.getCursorStack().isEmpty()) click(handler, slot, 0, SlotActionType.PICKUP);
        return true;
    }

    private boolean inventoryFull() {
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        return true;
    }

    private static int storageSlotOf(int kitSlot, int containerSlots) {
        return containerSlots + (kitSlot < 9 ? kitSlot + 27 : kitSlot - 9);
    }

    private boolean moveRekitStack(ScreenHandler handler, ItemStack stack, int slot, int containerSlots) {
        KitManager.Kit kit = KitManager.get().current();
        if (kit == null || coolingDown(slot)) return false;
        for (Map.Entry<Integer, KitManager.Slot> entry : kit.getSlots().entrySet()) {
            if (!entry.getValue().matches(stack)) continue;
            int kitSlot = entry.getKey();
            int destination = storageSlotOf(kitSlot, containerSlots);
            if (destination >= handler.slots.size() || coolingDown(destination)) continue;
            ItemStack there = handler.getSlot(destination).getStack();
            if (entry.getValue().matches(there)) continue;
            if (isShulker(there) && handler instanceof ShulkerBoxScreenHandler) continue;
            if (kitSlot < 9) {
                click(handler, slot, kitSlot, SlotActionType.SWAP);
            } else {
                click(handler, slot, 0, SlotActionType.PICKUP);
                click(handler, destination, 0, SlotActionType.PICKUP);
                click(handler, slot, 0, SlotActionType.PICKUP);
            }
            coolDown(slot);
            coolDown(destination);
            return true;
        }
        return false;
    }

    private boolean hasFreeRekitSlot(ScreenHandler handler, int containerSlots) {
        KitManager.Kit kit = KitManager.get().current();
        if (kit == null) return false;
        for (int slot = 0; slot < containerSlots; slot++) {
            if (coolingDown(slot)) continue;
            ItemStack stack = handler.getSlot(slot).getStack();
            for (Map.Entry<Integer, KitManager.Slot> entry : kit.getSlots().entrySet()) {
                if (entry.getValue().isEmpty() || !entry.getValue().matches(stack)) continue;
                int destination = storageSlotOf(entry.getKey(), containerSlots);
                if (destination >= handler.slots.size() || coolingDown(destination)) continue;
                ItemStack there = handler.getSlot(destination).getStack();
                if (!entry.getValue().matches(there) && !isShulker(there)) return true;
            }
        }
        return false;
    }

    private boolean coolingDown(int slot) {
        Long until = cooldowns.get(slot);
        return until != null && System.currentTimeMillis() < until;
    }

    private void coolDown(int slot) {
        cooldowns.put(slot, System.currentTimeMillis() + 1000L);
    }

    @Override
    public String getMetaData() {
        return onlyButtons.getValue() ? "Buttons" : mode.getValue();
    }
}
