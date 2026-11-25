package dev.project.anatorius.utils.minecraft;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class SwapStack {
    private static final int MAX = 16;
    private static final int MAX_AGE_TICKS = 400;

    private final ArrayDeque<int[]> entries = new ArrayDeque<>();

    public void push(int clientBefore, int serverBefore, int tick) {
        entries.push(new int[]{clientBefore, serverBefore, tick});
        if (entries.size() > MAX) entries.removeLast();
    }

    public int[] pop() {
        return entries.isEmpty() ? null : entries.pop();
    }

    public boolean hasPending(int tick) {
        return !entries.isEmpty() && entries.peek()[2] == tick;
    }

    public int originClientSlot() {
        return entries.isEmpty() ? -1 : entries.peekLast()[0];
    }

    public void prune(int tick) {
        Iterator<int[]> it = entries.descendingIterator();
        while (it.hasNext()) {
            if (tick - it.next()[2] > MAX_AGE_TICKS) it.remove();
            else break;
        }
    }

    public int size() {
        return entries.size();
    }
}
