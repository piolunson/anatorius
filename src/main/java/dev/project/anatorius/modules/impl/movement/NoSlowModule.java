package dev.project.anatorius.modules.impl.movement;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.ChangeHandEvent;
import dev.project.anatorius.events.impl.KeyboardTickEvent;
import dev.project.anatorius.events.impl.PacketSendEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.events.impl.RenderWorldEvent;
import dev.project.anatorius.events.impl.TickEvent;
import dev.project.anatorius.events.impl.UpdateMovementEvent;
import dev.project.anatorius.gui.ClickGuiScreen;
import dev.project.anatorius.gui.WhitelistScreen;
import dev.project.anatorius.mixins.accessors.PlayerMoveC2SPacketAccessor;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.modules.impl.movement.elytrafly.ElytraPhysics;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.CategorySetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.utils.minecraft.NetworkUtils;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Queue;

@RegisterModule(name = "NoSlow", description = "Removes the slowness effect that you receive when doing certain actions.", category = Module.Category.MOVEMENT)
public class NoSlowModule extends Module {
    public ModeSetting mode = new ModeSetting("Mode", "How eating and using items stays at full speed on your server. None: just doesn't slow you. NCP: re-sends your slot with every move. GrimV3: full speed every other tick. GrimOld: uses your other hand too. Constantiam: eats in the air instead of on a block (no speed). 2b2t: a slot swap timed to your ping.", "None", new String[]{"None", "NCP", "GrimV3", "GrimOld", "Constantiam", "2b2t"});
    public BooleanSetting airStrict = new BooleanSetting("AirStrict", "NCP: sneaks for the server while you use an item in the air.", new ModeSetting.Visibility(mode, "NCP"), false);
    public ModeSetting inventory = new ModeSetting("InventoryMode", "Inventory", "How clicks in your inventory get through while you move (with GuiMove). NCP: stops sprinting, sneaking and item use for the click and hops off the ground once. Grim: holds your clicks until you stand still for a tick.", "None", new String[]{"None", "NCP", "Grim"});

    public CategorySetting playerCategory = new CategorySetting("Player", "Slowdowns of your own.");
    public BooleanSetting items = new BooleanSetting("Items", "Removes the slowness effect from eating or using items.", new CategorySetting.Visibility(playerCategory), true);
    public BooleanSetting sneak = new BooleanSetting("Sneak", "Walks at full speed while sneaking.", new CategorySetting.Visibility(playerCategory), false);
    public BooleanSetting crawl = new BooleanSetting("Crawl", "Walks at full speed while crawling.", new CategorySetting.Visibility(playerCategory), false);
    public BooleanSetting slowness = new BooleanSetting("Slowness", "Ignores the Slowness effect.", new CategorySetting.Visibility(playerCategory), false);

    public CategorySetting blocksCategory = new CategorySetting("Blocks", "Blocks that slow you down.");
    public BooleanSetting soulSand = new BooleanSetting("SoulSand", "Removes the slowness effect from walking on soul sand.", new CategorySetting.Visibility(blocksCategory), false);
    public BooleanSetting honeyBlocks = new BooleanSetting("HoneyBlocks", "Removes the slowness effect from walking on honey blocks, and the slow slide down their sides.", new CategorySetting.Visibility(blocksCategory), false);
    public BooleanSetting slimeBlocks = new BooleanSetting("SlimeBlocks", "Removes the slowness effect from walking on slime blocks.", new CategorySetting.Visibility(blocksCategory), false);
    public BooleanSetting noSlimeBounce = new BooleanSetting("NoSlimeBounce", "NoBounce", "Doesn't bounce off slime blocks when you land on them.", new BooleanSetting.Visibility(slimeBlocks, true), true);
    public BooleanSetting ice = new BooleanSetting("Ice", "Walks on ice like on normal ground (no sliding).", new CategorySetting.Visibility(blocksCategory), false);
    public BooleanSetting berryBush = new BooleanSetting("BerryBush", "Walks through sweet berry bushes at full speed.", new CategorySetting.Visibility(blocksCategory), false);

    public CategorySetting guiMoveCategory = new CategorySetting("GuiMove", "Moving while a screen is open.");
    public BooleanSetting guiMove = new BooleanSetting("GuiMove", "Enabled", "Lets you move while a GUI like your inventory is open.", new CategorySetting.Visibility(guiMoveCategory), false);
    public BooleanSetting containers = new BooleanSetting("Containers", "Also in chests and other containers. Off: only your own inventory.", new BooleanSetting.Visibility(guiMove, true), true);
    public BooleanSetting rotate = new BooleanSetting("Rotate", "The arrow keys turn your view while a screen is open.", new BooleanSetting.Visibility(guiMove, true), true);

    private boolean sneaking = false;
    private boolean guiMoveActive = false;
    public volatile boolean testForceForward = false;

    private int groundedTicks;
    private long airborneAt;

    private boolean positionOffsetSent, groundSpoofPending;
    private final Queue<Packet<?>> heldPackets = new ArrayDeque<>();
    private boolean inputActive, flushPending, resetInputNext;
    private boolean sending;

    private long lastRotateFrame;

    public boolean shouldSlow() {
        if (mc.player == null || !mc.player.isUsingItem()) return false;
        if (mc.player.hasVehicle()) return true;
        return switch (mode.getValue()) {
            case "GrimV3" -> {
                int useTicks = mc.player.getItemUseTime();
                yield !(useTicks % 2 == 0 && useTicks > 1 && !mc.player.isGliding());
            }
            case "Constantiam" -> true;
            case "2b2t" -> {
                if (swapSlot2b2t() == -1 || mc.isInSingleplayer()) yield false;
                yield mc.player.getItemUseTime() <= threshold2b2t();
            }
            default -> false;
        };
    }

    private int threshold2b2t() {
        double latencyTicks = Anatorius.SERVER_MANAGER.getPing() / 50.0;
        return (int) (Math.ceil(latencyTicks) + 2.0);
    }

    private int swapSlot2b2t() {
        if (!mc.player.getOffHandStack().isEmpty()) return 40;
        int selected = mc.player.getInventory().getSelectedSlot();
        for (int slot = 0; slot < 9; slot++) {
            if (slot != selected && !mc.player.getInventory().getStack(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private boolean usingItemFreely() {
        return items.getValue() && mc.player.isUsingItem() && !mc.player.hasVehicle();
    }

    public boolean eatsInsteadOfUsingOn(Hand hand) {
        if (!isToggled() || !mode.getValue().equals("Constantiam") || !items.getValue() || mc.player.isGliding()) return false;
        return mc.player.getStackInHand(hand).contains(DataComponentTypes.FOOD) && !mc.player.isSneaking();
    }

    @SubscribeEvent
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) { guiMoveActive = false; return; }

        updateGuiMove();

        if (airStrict.getValue() && sneaking && !mc.player.isUsingItem()) {
            sendSneak(false);
            sneaking = false;
        }
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        if (mc.player == null || mc.world == null) return;
        groundedTicks = mc.player.isOnGround() ? groundedTicks + 1 : 0;
        if (!mc.player.isOnGround()) airborneAt = System.currentTimeMillis();
        positionOffsetSent = false;

        if (mode.getValue().equals("2b2t") && usingItemFreely() && !mc.isInSingleplayer()) {
            int swapSlot = swapSlot2b2t();
            if (swapSlot != -1 && mc.player.getItemUseTime() == threshold2b2t()) {
                int syncId = mc.player.playerScreenHandler.syncId;
                int menuSlot = 36 + mc.player.getInventory().getSelectedSlot();
                mc.interactionManager.clickSlot(syncId, menuSlot, swapSlot, SlotActionType.SWAP, mc.player);
                sendUncaught(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
                mc.interactionManager.clickSlot(syncId, menuSlot, swapSlot, SlotActionType.SWAP, mc.player);
            }
        }

        if (inventory.getValue().equals("Grim")) {
            if (flushPending) {
                flushHeld();
                flushPending = false;
            }
            boolean keepHolding = inputActive && mc.currentScreen instanceof HandledScreen<?>
                    && !mc.player.currentScreenHandler.getCursorStack().isEmpty();
            if (!keepHolding) flushPending = !heldPackets.isEmpty();
            if (flushPending) {
                resetInputNext = true;
                mc.player.setSprinting(false);
            }
        } else if (!heldPackets.isEmpty()) {
            flushHeld();
        }
    }

    @SubscribeEvent
    public void onKeyboardTick(KeyboardTickEvent event) {
        if (resetInputNext) {
            event.setResetInput(true);
            resetInputNext = false;
        }
    }

    @SubscribeEvent
    public void onUpdateMovement(UpdateMovementEvent event) {
        if (mc.player == null || mc.world == null) return;
        PlayerInput in = mc.player.input.playerInput;
        inputActive = in.forward() || in.backward() || in.left() || in.right() || in.jump() || in.sneak() || in.sprint();

        if (mode.getValue().equals("GrimOld") && usingItemFreely() && !mc.player.isGliding()) useOtherHand(mc.player.getActiveHand());
    }

    private void useOtherHand(Hand usedHand) {
        Hand otherHand = usedHand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;
        ItemStack other = mc.player.getStackInHand(otherHand);
        if (!usableItem(other)) {
            sendUse(otherHand);
            return;
        }
        if (otherHand == Hand.MAIN_HAND) {
            int serverSlot = Anatorius.POSITION_MANAGER.getServerSlot();
            sendUncaught(new UpdateSelectedSlotC2SPacket((serverSlot + 1) % 9));
            sendUse(otherHand);
            sendUncaught(new UpdateSelectedSlotC2SPacket(serverSlot));
            return;
        }
        int inventorySlot = -1;
        for (int i = 9; i <= 35; i++) {
            if (!usableItem(mc.player.getInventory().getStack(i))) { inventorySlot = i; break; }
        }
        if (inventorySlot == -1) return;
        int syncId = mc.player.playerScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, inventorySlot, 40, SlotActionType.SWAP, mc.player);
        sendUse(otherHand);
        mc.interactionManager.clickSlot(syncId, inventorySlot, 40, SlotActionType.SWAP, mc.player);
    }

    private static boolean usableItem(ItemStack stack) {
        return stack.contains(DataComponentTypes.FOOD) || stack.isOf(Items.BOW) || stack.isOf(Items.SHIELD) || stack.isOf(Items.ENDER_PEARL);
    }

    private void sendUse(Hand hand) {
        NetworkUtils.sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(hand, id, mc.player.getYaw(), mc.player.getPitch()));
    }

    @SubscribeEvent
    public void onPacketSend(PacketSendEvent event) {
        if (mc.player == null || mc.world == null || sending) return;
        Packet<?> packet = event.getPacket();

        if (mode.getValue().equals("NCP") && packet instanceof PlayerMoveC2SPacket && usingItemFreely() && !mc.player.isGliding()
                && mc.player.getStackInHand(mc.player.getActiveHand()).contains(DataComponentTypes.FOOD)) {
            sendUncaught(new UpdateSelectedSlotC2SPacket(Anatorius.POSITION_MANAGER.getServerSlot()));
        }

        switch (inventory.getValue()) {
            case "NCP" -> inventoryNcp(event, packet);
            case "Grim" -> inventoryGrim(event, packet);
            default -> { }
        }
    }

    private void inventoryNcp(PacketSendEvent event, Packet<?> packet) {
        if (packet instanceof PlayerMoveC2SPacket move && groundSpoofPending && guiMove.getValue() && mc.player.isOnGround()
                && System.currentTimeMillis() - airborneAt >= 200L && !mc.player.isGliding() && !mc.player.hasVehicle()) {
            ((PlayerMoveC2SPacketAccessor) move).setOnGround(false);
            groundSpoofPending = false;
            return;
        }
        if (!(packet instanceof ClickSlotC2SPacket) || !guiMove.getValue() || !guiMoveAllowed()) return;
        event.setCancelled(true);
        if (mc.player.isUsingItem()) mc.interactionManager.stopUsingItem(mc.player);
        boolean moving = ElytraPhysics.moving();
        if (moving) {
            if (groundedTicks > 1 && !positionOffsetSent) {
                sendUncaught(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.0626, mc.player.getZ(), false, mc.player.horizontalCollision));
                positionOffsetSent = true;
                groundSpoofPending = true;
            }
            if (mc.player.isSprinting()) sendUncaught(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            if (mc.player.isSneaking()) sendSneak(false);
        }
        sendUncaught(packet);
        if (moving) {
            if (mc.player.isSprinting()) sendUncaught(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            if (mc.player.isSneaking()) sendSneak(true);
        }
    }

    private void inventoryGrim(PacketSendEvent event, Packet<?> packet) {
        if (packet instanceof ClientCommandC2SPacket command && command.getMode() == ClientCommandC2SPacket.Mode.START_FALL_FLYING) {
            inputActive = false;
            flushHeld();
            return;
        }
        boolean holdable = guiMove.getValue() && guiMoveAllowed();
        if (packet instanceof ClickSlotC2SPacket && inputActive && holdable) {
            heldPackets.add(packet);
            event.setCancelled(true);
            return;
        }
        boolean follows = packet instanceof PlayerInteractItemC2SPacket || packet instanceof HandSwingC2SPacket || packet instanceof CloseHandledScreenC2SPacket;
        if (follows && inputActive && !heldPackets.isEmpty()) {
            heldPackets.add(packet);
            event.setCancelled(true);
        }
    }

    private void flushHeld() {
        while (!heldPackets.isEmpty()) {
            Packet<?> packet = heldPackets.poll();
            if (packet != null && mc.getNetworkHandler() != null) sendUncaught(packet);
        }
    }

    private void sendUncaught(Packet<?> packet) {
        sending = true;
        try {
            mc.getNetworkHandler().sendPacket(packet);
        } finally {
            sending = false;
        }
    }

    @SubscribeEvent
    public void onChangeHand(ChangeHandEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mode.getValue().equals("NCP") && airStrict.getValue() && !sneaking && (!mc.player.isRiding() && !mc.player.isSneaking() && (mc.player.isUsingItem() && items.getValue()))) {
            sendSneak(true);
            sneaking = true;
        }
    }

    private KeyBinding[] guiMoveKeys() {
        return new KeyBinding[]{mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey, mc.options.jumpKey, mc.options.sprintKey};
    }

    private boolean physicalDown(KeyBinding key) {
        if (testForceForward && key == mc.options.forwardKey) return true;
        InputUtil.Key bound = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
        if (bound.getCategory() != InputUtil.Type.KEYSYM || bound.getCode() == InputUtil.UNKNOWN_KEY.getCode()) return false;
        return InputUtil.isKeyPressed(mc.getWindow(), bound.getCode());
    }

    private boolean typingInScreen(Screen screen) {
        if (screen instanceof ChatScreen || screen instanceof AbstractSignEditScreen || screen instanceof BookEditScreen
                || screen instanceof WhitelistScreen) return true;
        if (screen instanceof GameMenuScreen && (mc.isInSingleplayer() || mc.isIntegratedServerRunning())) return true;
        Element focused = screen.getFocused();
        while (focused != null) {
            if (focused instanceof TextFieldWidget) return true;
            focused = focused instanceof ParentElement parent ? parent.getFocused() : null;
        }
        return false;
    }

    public boolean guiMoveAllowed() {
        Screen screen = mc.currentScreen;
        if (screen == null || typingInScreen(screen)) return false;
        if (!containers.getValue() && screen instanceof HandledScreen<?> && !(screen instanceof InventoryScreen) && !(screen instanceof CreativeInventoryScreen)) return false;
        return true;
    }

    private void updateGuiMove() {
        boolean apply = guiMove.getValue() && guiMoveAllowed();
        if (apply) {
            for (KeyBinding key : guiMoveKeys()) key.setPressed(physicalDown(key));
            guiMoveActive = true;
        } else if (guiMoveActive) {
            guiMoveActive = false;
            for (KeyBinding key : guiMoveKeys()) key.setPressed(mc.currentScreen == null && physicalDown(key));
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldEvent event) {
        long now = System.nanoTime();
        float seconds = lastRotateFrame == 0L ? 0.0f : Math.min(0.1f, (now - lastRotateFrame) / 1.0E9f);
        lastRotateFrame = now;
        if (mc.player == null || !guiMove.getValue() || !rotate.getValue() || !guiMoveAllowed() || mc.currentScreen instanceof ClickGuiScreen) return;
        float step = 160.0f * seconds;
        long window = mc.getWindow().getHandle();
        float pitch = mc.player.getPitch(), yaw = mc.player.getYaw();
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS) pitch += step;
        else if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS) pitch -= step;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS) yaw += step;
        else if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS) yaw -= step;
        mc.player.setPitch(MathHelper.clamp(pitch, -90.0f, 90.0f));
        mc.player.setYaw(yaw);
    }

    public boolean noPoseSlowdown() {
        if (!isToggled() || mc.player == null) return false;
        boolean crawling = mc.player.isCrawling() && crawl.getValue();
        return crawling || (!crawling && mc.player.isSneaking() && sneak.getValue());
    }

    public boolean ignoresSlowness() {
        return isToggled() && slowness.getValue();
    }

    @Override
    public void onDisable() {
        if (guiMoveActive && mc.player != null) {
            guiMoveActive = false;
            for (KeyBinding key : guiMoveKeys()) key.setPressed(mc.currentScreen == null && physicalDown(key));
        }
        if (mc.player != null && !heldPackets.isEmpty()) flushHeld();
        heldPackets.clear();
        flushPending = resetInputNext = false;

        if (mc.player == null) return;

        if (airStrict.getValue() && sneaking) {
            sendSneak(false);
        }

        sneaking = false;
    }

    private void sendSneak(boolean sneak) {
        PlayerInput cur = mc.player.input.playerInput;
        sendUncaught(new PlayerInputC2SPacket(
                new PlayerInput(cur.forward(), cur.backward(), cur.left(), cur.right(), cur.jump(), sneak, cur.sprint())));
    }

    @Override
    public String getMetaData() {
        return mode.getValue();
    }
}
