package dev.project.anatorius.utils.items3d;

import dev.project.anatorius.modules.impl.visuals.Items3DModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2fStack;

import java.util.Arrays;

public final class InventoryAnimator {
    public static final InventoryAnimator INSTANCE = new InventoryAnimator();

    private static final long SETTLE_NS = 200_000_000L;

    private ScreenHandler handler;
    private long openedAt;
    private Item[] items = new Item[0];
    private int[] counts = new int[0];
    private long[] placedAt = new long[0];

    private Item cursorItem;
    private long pickedAt;
    private float originX, originY;
    private boolean hasOrigin;

    private boolean slotPushed, cursorPushed;

    private InventoryAnimator() { }

    public void clear() {
        handler = null;
        items = new Item[0];
        counts = new int[0];
        placedAt = new long[0];
        cursorItem = null;
        pickedAt = 0;
    }

    private static Items3DModule enabled() {
        Items3DModule m = Items3DModule.active();
        return m != null && m.inventoryAnimations.getValue() && !m.inventoryHandledElsewhere() ? m : null;
    }

    public void frame(ScreenHandler h, Slot focused, int screenX, int screenY) {
        if (enabled() == null) { if (handler != null) clear(); return; }
        long now = System.nanoTime();
        int n = h.slots.size();
        if (h != handler || items.length != n) {
            handler = h;
            openedAt = now;
            items = new Item[n];
            counts = new int[n];
            placedAt = new long[n];
            snapshot(h);
            ItemStack c = h.getCursorStack();
            cursorItem = c.isEmpty() ? null : c.getItem();
            return;
        }
        boolean settling = now - openedAt < SETTLE_NS;

        ItemStack cursor = h.getCursorStack();
        Item ci = cursor.isEmpty() ? null : cursor.getItem();
        if (ci != null && (cursorItem == null || ci != cursorItem) && !settling) {
            pickedAt = now;
            hasOrigin = false;
            for (int i = 0; i < n; i++) {
                ItemStack s = h.slots.get(i).getStack();
                if (items[i] == ci && (s.isEmpty() || s.getItem() != ci || s.getCount() < counts[i])) {
                    Slot slot = h.slots.get(i);
                    originX = screenX + slot.x + 8; originY = screenY + slot.y + 8;
                    hasOrigin = true;
                    break;
                }
            }
            if (!hasOrigin && focused != null) {
                originX = screenX + focused.x + 8; originY = screenY + focused.y + 8;
                hasOrigin = true;
            }
        }
        cursorItem = ci;

        for (int i = 0; i < n; i++) {
            ItemStack s = h.slots.get(i).getStack();
            Item item = s.isEmpty() ? null : s.getItem();
            int count = s.isEmpty() ? 0 : s.getCount();
            if (item != null && !settling && (item != items[i] || count > counts[i])) placedAt[i] = now;
            items[i] = item;
            counts[i] = count;
        }
    }

    private void snapshot(ScreenHandler h) {
        for (int i = 0; i < items.length; i++) {
            ItemStack s = h.slots.get(i).getStack();
            items[i] = s.isEmpty() ? null : s.getItem();
            counts[i] = s.isEmpty() ? 0 : s.getCount();
        }
        Arrays.fill(placedAt, 0L);
    }

    public void beginSlot(DrawContext context, Slot slot) {
        slotPushed = false;
        Items3DModule m = enabled();
        if (m == null || !Items3DModule.PLACEMENT || slot.id < 0 || slot.id >= placedAt.length || placedAt[slot.id] == 0) return;
        float t = progress(m, placedAt[slot.id]);
        if (t >= 1f) { placedAt[slot.id] = 0; return; }
        float s = 1f + Items3DModule.SCALE_EFFECT / 100f * 1.6f * (1f - ease(m, t));
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        float cx = slot.x + 8, cy = slot.y + 8;
        matrices.translate(cx, cy);
        matrices.scale(s, s);
        matrices.translate(-cx, -cy);
        slotPushed = true;
    }

    public void endSlot(DrawContext context) {
        if (slotPushed) { context.getMatrices().popMatrix(); slotPushed = false; }
    }

    public void beginCursor(DrawContext context, int mouseX, int mouseY) {
        cursorPushed = false;
        Items3DModule m = enabled();
        if (m == null || pickedAt == 0 || cursorItem == null) return;
        float t = progress(m, pickedAt);
        if (t >= 1f) { pickedAt = 0; return; }
        float e = ease(m, t);
        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        if (Items3DModule.SLOT_TRANSITION && hasOrigin) {
            matrices.translate((originX - mouseX) * (1f - e), (originY - mouseY) * (1f - e));
        }
        if (Items3DModule.PICKUP) {
            float s = 1f + Items3DModule.SCALE_EFFECT / 100f * MathHelper.sin(t * MathHelper.PI);
            matrices.translate(mouseX, mouseY);
            matrices.scale(s, s);
            matrices.translate(-mouseX, -mouseY);
        }
        cursorPushed = true;
    }

    public void endCursor(DrawContext context) {
        if (cursorPushed) { context.getMatrices().popMatrix(); cursorPushed = false; }
    }

    private static float progress(Items3DModule m, long start) {
        float ms = Items3DModule.DURATION_MS / Math.max(0.05f, m.speed());
        return Math.min(1f, (System.nanoTime() - start) / 1.0e6f / ms);
    }

    public static float ease(Items3DModule m, float t) {
        return switch (Items3DModule.EASING) {
            case "Linear" -> t;
            case "EaseIn" -> t * t;
            case "EaseOut" -> 1f - (1f - t) * (1f - t);
            case "EaseInOut" -> t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
            case "Back" -> { float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f; yield 1f + c3 * u * u * u + c1 * u * u; }
            case "Elastic" -> t <= 0f ? 0f : t >= 1f ? 1f
                    : (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI) / 3) + 1);
            case "Spring" -> 1f - (float) (Math.exp(-6.0 * t) * Math.cos(10.0 * t));
            default -> 1f - (1f - t) * (1f - t) * (1f - t);
        };
    }

    public int activeSlotAnimations() {
        long now = System.nanoTime();
        int n = 0;
        for (long t : placedAt) if (t != 0 && now - t < 600_000_000L) n++;
        return n;
    }

    public boolean pickupActive() {
        return pickedAt != 0 && System.nanoTime() - pickedAt < 600_000_000L;
    }
}
