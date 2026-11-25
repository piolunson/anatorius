package dev.project.anatorius.modules.impl.miscellaneous;

import dev.project.anatorius.Anatorius;
import dev.project.anatorius.events.SubscribeEvent;
import dev.project.anatorius.events.impl.PlayerUpdateEvent;
import dev.project.anatorius.events.impl.UpdateMovementEvent;
import dev.project.anatorius.modules.Module;
import dev.project.anatorius.modules.RegisterModule;
import dev.project.anatorius.settings.impl.BooleanSetting;
import dev.project.anatorius.settings.impl.ModeSetting;
import dev.project.anatorius.settings.impl.NumberSetting;
import dev.project.anatorius.utils.minecraft.InventoryUtils;
import dev.project.anatorius.utils.rotations.RotationUtils;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFrameItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@RegisterModule(name = "AutoDupe", description = "Frame dupe islemlerini otomatiklestirir.", category = Module.Category.MISCELLANEOUS)
public class AnvilDupeModule extends Module {
    private static final double FRAME_RANGE = 4.0;
    private static final long FRAME_CLEANUP_INTERVAL_MS = 5000L;
    private static final long FRAME_COOLDOWN_EXPIRY_MS = 10000L;
    private static final long FRAME_DOUBLE_CLICK_DELAY_MS = 25L;
    private static final Set<Item> DEFAULT_FRAME_ITEMS = Set.of(
            Items.SHULKER_BOX,
            Items.WHITE_SHULKER_BOX,
            Items.ORANGE_SHULKER_BOX,
            Items.MAGENTA_SHULKER_BOX,
            Items.LIGHT_BLUE_SHULKER_BOX,
            Items.YELLOW_SHULKER_BOX,
            Items.LIME_SHULKER_BOX,
            Items.PINK_SHULKER_BOX,
            Items.GRAY_SHULKER_BOX,
            Items.LIGHT_GRAY_SHULKER_BOX,
            Items.CYAN_SHULKER_BOX,
            Items.PURPLE_SHULKER_BOX,
            Items.BLUE_SHULKER_BOX,
            Items.BROWN_SHULKER_BOX,
            Items.GREEN_SHULKER_BOX,
            Items.RED_SHULKER_BOX,
            Items.BLACK_SHULKER_BOX
    );

    public ModeSetting switchMode = new ModeSetting("SwitchMode", "Esya degisim modunu belirler.", "Silent", InventoryUtils.SWITCH_MODES);
    public NumberSetting frameCount = new NumberSetting("FrameCount", "Frame modunun ayni anda kullanacagi maksimum frame sayisi.", 9, 1, 9);
    public BooleanSetting rotate = new BooleanSetting("Rotate", "Her islemden once frame'e bakar, tick basina tek islem yapar (siki anti-cheat'ler icin). Kapaliyken tum frame'lere ayni anda, bakmadan islem yapar.", true);

    private final List<FrameSnapshot> targetFrames = new ArrayList<>();
    private final List<ItemFrameEntity> dontHit = new ArrayList<>();
    private final Map<Integer, Long> interactionCooldowns = new ConcurrentHashMap<>();
    private long lastCleanup = 0L;
    private int lastCapturedFrameLimit = -1;

    private Runnable plannedAction;
    private Vec3d plannedAim;
    private static final long SERVER_ANSWER_MS = 300L;
    private final Map<Object, Long> awaitingServer = new ConcurrentHashMap<>();

    private boolean awaiting(Object key) {
        Long since = awaitingServer.get(key);
        return since != null && System.currentTimeMillis() - since < SERVER_ANSWER_MS;
    }

    private void markAwaiting(Object key) {
        if (rotate.getValue()) awaitingServer.put(key, System.currentTimeMillis());
    }

    @Override
    public void onEnable() {
        resetFrameState();
        if (getNull()) return;
        captureTargetFrames(getNearbyFrames());
    }

    @Override
    public void onDisable() {
        resetFrameState();
    }

    @SubscribeEvent
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        plannedAction = null;
        plannedAim = null;
        if (getNull()) return;
        handleFrameMode();
        if (plannedAction != null && rotate.getValue()) {
            Anatorius.ROTATION_MANAGER.rotate(RotationUtils.getRotations(plannedAim, false), this);
        }
    }

    @SubscribeEvent
    public void onUpdateMovementPost(UpdateMovementEvent.Post event) {
        if (plannedAction == null || getNull()) return;
        Runnable action = plannedAction;
        plannedAction = null;
        if (rotate.getValue() && !serverLooksAt(plannedAim)) return;
        action.run();
    }

    private boolean serverLooksAt(Vec3d aim) {
        float[] wanted = RotationUtils.getRotations(aim, false);
        float yaw = Anatorius.ROTATION_MANAGER.getServerYaw();
        float pitch = Anatorius.ROTATION_MANAGER.getServerPitch();
        return Math.abs(MathHelper.wrapDegrees(wanted[0] - yaw)) <= 4.0f && Math.abs(wanted[1] - pitch) <= 4.0f;
    }

    private boolean schedule(Vec3d aim, Runnable action) {
        if (!rotate.getValue()) {
            action.run();
            return false;
        }
        plannedAim = aim;
        plannedAction = action;
        return true;
    }

    private void useOn(ItemFrameEntity frame, Hand hand) {
        Vec3d hit = frame.getBoundingBox().getCenter();
        ActionResult result = mc.interactionManager.interactEntityAtLocation(mc.player, frame, new EntityHitResult(frame, hit), hand);
        if (!result.isAccepted()) result = mc.interactionManager.interactEntity(mc.player, frame, hand);
        if (result instanceof ActionResult.Success success && success.swingSource() == ActionResult.SwingSource.CLIENT) {
            mc.player.swingHand(hand);
        }
    }

    @Override
    public String getMetaData() {
        return "Frame";
    }

    private void resetFrameState() {
        targetFrames.clear();
        dontHit.clear();
        interactionCooldowns.clear();
        lastCleanup = 0L;
        lastCapturedFrameLimit = -1;
        plannedAction = null;
        plannedAim = null;
        awaitingServer.clear();
    }

    private void handleFrameMode() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup >= FRAME_CLEANUP_INTERVAL_MS) {
            cleanupDeadFrames();
            lastCleanup = now;
        }

        List<ItemFrameEntity> detectedFrames = getNearbyFrames();
        int desiredLimit = frameCount.getValue().intValue();

        if (targetFrames.isEmpty()
                || lastCapturedFrameLimit != desiredLimit
                || (targetFrames.size() < desiredLimit && detectedFrames.size() > targetFrames.size())) {
            captureTargetFrames(detectedFrames);
        }

        FrameState state = buildFrameState(detectedFrames);
        if (state.presentFrames.isEmpty() && state.missingTargets.isEmpty() && !detectedFrames.isEmpty()) {
            captureTargetFrames(detectedFrames);
            state = buildFrameState(detectedFrames);
        }

        if (replaceMissingFrames(state.missingTargets)) return;

        Set<Item> dupeItems = collectDupeItems(state.presentFrames);
        for (ItemFrameEntity frame : state.presentFrames) {
            if (frame.isRemoved() || !frame.isAlive()) {
                continue;
            }

            if (frame.getHeldItemStack().isEmpty()) {
                if (awaiting(frame.getId())) continue;
                int slot = getDupeSlot(dupeItems);
                if (slot == -1) {
                    continue;
                }

                Hand fillHand = (slot == -2) ? Hand.OFF_HAND : Hand.MAIN_HAND;
                boolean planned = schedule(frame.getBoundingBox().getCenter(), () -> {
                    if (frame.isRemoved() || !frame.getHeldItemStack().isEmpty()) return;
                    int prevSlot = mc.player.getInventory().getSelectedSlot();
                    boolean switching = fillHand == Hand.MAIN_HAND && slot != prevSlot;
                    if (switching) InventoryUtils.switchSlot(switchMode.getValue(), slot, prevSlot);
                    useOn(frame, fillHand);
                    if (switching) InventoryUtils.switchBack(switchMode.getValue(), slot, prevSlot);
                    dontHit.remove(frame);
                    markAwaiting(frame.getId());
                });
                if (planned) return;
                continue;
            }

            if (dontHit.contains(frame)) {
                continue;
            }

            long lastInteraction = interactionCooldowns.getOrDefault(frame.getId(), 0L);
            if (now - lastInteraction < FRAME_DOUBLE_CLICK_DELAY_MS) {
                continue;
            }

            boolean planned = schedule(frame.getBoundingBox().getCenter(), () -> {
                if (frame.isRemoved()) return;
                mc.interactionManager.attackEntity(mc.player, frame);
                mc.player.swingHand(Hand.MAIN_HAND);
                dontHit.add(frame);
                interactionCooldowns.put(frame.getId(), System.currentTimeMillis());
            });
            if (planned) return;
        }
    }

    private List<ItemFrameEntity> getNearbyFrames() {
        List<ItemFrameEntity> detected = new ArrayList<>();
        double rangeSq = FRAME_RANGE * FRAME_RANGE;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemFrameEntity itemFrame)) {
                continue;
            }

            if (itemFrame.isRemoved() || !itemFrame.isAlive()) {
                continue;
            }

            if (entity.squaredDistanceTo(mc.player.getEyePos()) > rangeSq) {
                continue;
            }

            detected.add(itemFrame);
        }

        detected.sort(Comparator.comparingDouble(frame -> frame.squaredDistanceTo(mc.player.getEyePos())));
        return detected;
    }

    private void captureTargetFrames(List<ItemFrameEntity> detectedFrames) {
        targetFrames.clear();
        dontHit.clear();

        int limit = Math.min(frameCount.getValue().intValue(), detectedFrames.size());
        for (int i = 0; i < limit; i++) {
            ItemFrameEntity frame = detectedFrames.get(i);
            Vec3d rotationVec = frame.getRotationVector();
            if (rotationVec == null) {
                continue;
            }

            BlockPos supportPos = BlockPos.ofFloored(frame.getEntityPos().subtract(rotationVec.normalize()));
            targetFrames.add(new FrameSnapshot(frame.getEntityPos(), supportPos, frame.getHorizontalFacing()));
        }

        lastCapturedFrameLimit = frameCount.getValue().intValue();
    }

    private FrameState buildFrameState(List<ItemFrameEntity> detectedFrames) {
        List<ItemFrameEntity> presentFrames = new ArrayList<>();
        List<FrameSnapshot> missingTargets = new ArrayList<>();
        double rangeSq = FRAME_RANGE * FRAME_RANGE;

        for (FrameSnapshot target : targetFrames) {
            if (target.pos.squaredDistanceTo(mc.player.getEyePos()) > rangeSq) {
                continue;
            }

            ItemFrameEntity foundFrame = null;
            for (ItemFrameEntity detected : detectedFrames) {
                Vec3d rotationVec = detected.getRotationVector();
                if (rotationVec == null) {
                    continue;
                }

                BlockPos supportPos = BlockPos.ofFloored(detected.getEntityPos().subtract(rotationVec.normalize()));
                if (detected.getHorizontalFacing() == target.facing && supportPos.equals(target.supportPos)) {
                    foundFrame = detected;
                    break;
                }
            }

            if (foundFrame == null) {
                missingTargets.add(target);
            } else {
                presentFrames.add(foundFrame);
            }
        }

        return new FrameState(presentFrames, missingTargets);
    }

    private boolean replaceMissingFrames(List<FrameSnapshot> missingTargets) {
        for (FrameSnapshot target : missingTargets) {
            if (awaiting(target)) continue;
            int slot = getFramePlacementSlot();
            if (slot == -1) {
                return false;
            }

            Hand hand = (slot == -2) ? Hand.OFF_HAND : Hand.MAIN_HAND;
            Vec3d hit = Vec3d.ofCenter(target.supportPos).add(Vec3d.of(target.facing.getVector()).multiply(0.5));

            boolean planned = schedule(hit, () -> {
                int prevSlot = mc.player.getInventory().getSelectedSlot();
                boolean switching = hand == Hand.MAIN_HAND && slot != prevSlot;
                if (switching) InventoryUtils.switchSlot(switchMode.getValue(), slot, prevSlot);
                ActionResult result = mc.interactionManager.interactBlock(mc.player, hand,
                        new BlockHitResult(hit, target.facing, target.supportPos, false));
                if (result instanceof ActionResult.Success success && success.swingSource() == ActionResult.SwingSource.CLIENT) {
                    mc.player.swingHand(hand);
                }
                if (switching) InventoryUtils.switchBack(switchMode.getValue(), slot, prevSlot);
                markAwaiting(target);
            });
            if (planned) return true;
        }
        return false;
    }

    private int getFramePlacementSlot() {
        if (mc.player.getOffHandStack().getItem() instanceof ItemFrameItem) {
            return -2;
        }

        if (mc.player.getMainHandStack().getItem() instanceof ItemFrameItem) {
            return mc.player.getInventory().getSelectedSlot();
        }

        PlayerInventory inventory = mc.player.getInventory();
        int hotbarSlot = findHotbarSlot(item -> item instanceof ItemFrameItem);
        if (hotbarSlot != -1) {
            return hotbarSlot;
        }

        int inventorySlot = findInventorySlot(item -> item instanceof ItemFrameItem);
        if (inventorySlot != -1) {
            InventoryUtils.swap("Swap", inventorySlot, inventory.getSelectedSlot());
            if (mc.player.getMainHandStack().getItem() instanceof ItemFrameItem) {
                return inventory.getSelectedSlot();
            }
        }

        return -1;
    }

    private Set<Item> collectDupeItems(List<ItemFrameEntity> frames) {
        LinkedHashSet<Item> items = new LinkedHashSet<>();

        for (ItemFrameEntity frame : frames) {
            Item item = frame.getHeldItemStack().getItem();
            if (isUsableDupeItem(item)) {
                items.add(item);
            }
        }

        if (items.isEmpty()) {
            Item mainHand = mc.player.getMainHandStack().getItem();
            if (isUsableDupeItem(mainHand)) {
                items.add(mainHand);
            }
        }

        if (items.isEmpty()) {
            Item offHand = mc.player.getOffHandStack().getItem();
            if (isUsableDupeItem(offHand)) {
                items.add(offHand);
            }
        }

        if (items.isEmpty()) {
            items.addAll(DEFAULT_FRAME_ITEMS);
        }

        return items;
    }

    private boolean isUsableDupeItem(Item item) {
        return item != Items.AIR && !(item instanceof ItemFrameItem) && item != Items.TOTEM_OF_UNDYING;
    }

    private int getDupeSlot(Set<Item> dupeItems) {
        if (dupeItems.contains(mc.player.getMainHandStack().getItem())) {
            return mc.player.getInventory().getSelectedSlot();
        }

        if (dupeItems.contains(mc.player.getOffHandStack().getItem())) {
            return -2;
        }

        PlayerInventory inventory = mc.player.getInventory();
        int hotbarSlot = findHotbarSlot(dupeItems::contains);
        if (hotbarSlot != -1) {
            return hotbarSlot;
        }

        int inventorySlot = findInventorySlot(dupeItems::contains);
        if (inventorySlot != -1) {
            InventoryUtils.swap("Swap", inventorySlot, inventory.getSelectedSlot());
            if (dupeItems.contains(mc.player.getMainHandStack().getItem())) {
                return inventory.getSelectedSlot();
            }
        }

        return -1;
    }

    private int findHotbarSlot(ItemMatcher matcher) {
        for (int slot = 0; slot < 9; slot++) {
            if (matcher.matches(mc.player.getInventory().getStack(slot).getItem())) {
                return slot;
            }
        }

        return -1;
    }

    private int findInventorySlot(ItemMatcher matcher) {
        for (int slot = 9; slot < mc.player.getInventory().size(); slot++) {
            if (matcher.matches(mc.player.getInventory().getStack(slot).getItem())) {
                return slot;
            }
        }

        return -1;
    }

    private void cleanupDeadFrames() {
        Iterator<ItemFrameEntity> iterator = dontHit.iterator();
        while (iterator.hasNext()) {
            ItemFrameEntity frame = iterator.next();
            if (frame.isRemoved() || !frame.isAlive() || frame.getHeldItemStack().isEmpty()) {
                iterator.remove();
            }
        }

        long now = System.currentTimeMillis();
        interactionCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > FRAME_COOLDOWN_EXPIRY_MS);
        awaitingServer.entrySet().removeIf(entry -> now - entry.getValue() > FRAME_COOLDOWN_EXPIRY_MS);
    }

    private record FrameSnapshot(Vec3d pos, BlockPos supportPos, net.minecraft.util.math.Direction facing) {
    }

    private record FrameState(List<ItemFrameEntity> presentFrames, List<FrameSnapshot> missingTargets) {
    }

    @FunctionalInterface
    private interface ItemMatcher {
        boolean matches(Item item);
    }
}
