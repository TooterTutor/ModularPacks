package io.github.tootertutor.ModularPacks.gui;

import java.util.ArrayList;
import java.util.List;

public final class SlotLayout {
    private SlotLayout() {
    }

    public static int bottomRowStart(int invSize) {
        return invSize - 9;
    }

    public static boolean isBottomRow(int invSize, int slot) {
        int start = bottomRowStart(invSize);
        return slot >= start && slot < invSize;
    }

    public static int prevButtonSlot(int invSize) {
        return invSize - 9;
    } // first slot of nav row

    public static int nextButtonSlot(int invSize) {
        return invSize - 1;
    } // last slot of nav row

    /**
     * Center the upgrade sockets on the bottom row. Max 7 sockets (leaves room for
     * buttons).
     */
    public static List<Integer> upgradeSocketSlots(int invSize, int upgradeSlots, boolean paginated) {
        upgradeSlots = Math.max(0, Math.min(7, upgradeSlots));
        List<Integer> out = new ArrayList<>();
        if (upgradeSlots == 0)
            return out;

        int rowStart = bottomRowStart(invSize);

        // Reserve fixed spots: sort (offset 1) and mode (offset 7). If paginated,
        // also reserve prev/next (offsets 0 and 8).
        int[] preferredOffsets = paginated
                ? new int[] { 4, 5, 3, 6, 2 } // keep center bias; 0/8 reserved for prev/next
                : new int[] { 4, 5, 3, 6, 2, 0, 8 };

        for (int offset : preferredOffsets) {
            if (out.size() >= upgradeSlots)
                break;
            if (offset == 1 || offset == 7)
                continue;
            if (paginated && (offset == 0 || offset == 8))
                continue;
            out.add(rowStart + offset);
        }

        return out;
    }

    /** Storage area = everything except bottom row (if nav row exists). */
    public static int storageAreaSize(int invSize, boolean hasNavRow) {
        return hasNavRow ? invSize - 9 : invSize;
    }

    /**
     * Pick a bottom-row slot for a mode button that mirrors the sort button
     * position.
     * If sort is on the left, mode is on the right, and vice versa.
     */
    public static int modeButtonSlot(int invSize, List<Integer> upgradeSlots, boolean paginated, int sortSlot) {
        int bottomStart = bottomRowStart(invSize);
        int prev = prevButtonSlot(invSize);
        int next = nextButtonSlot(invSize);

        int preferred = bottomStart + 7; // second-from-right
        if (!isBlocked(preferred, bottomStart, invSize, upgradeSlots, paginated, prev, next, sortSlot))
            return preferred;

        // Fallback: scan from right to left for the first free slot (excluding sort)
        for (int i = 8; i >= 0; i--) {
            int candidate = bottomStart + i;
            if (candidate == sortSlot)
                continue;
            if (!isBlocked(candidate, bottomStart, invSize, upgradeSlots, paginated, prev, next, sortSlot))
                return candidate;
        }

        return -1;
    }

    private static boolean isBlocked(int slot, int bottomStart, int invSize, List<Integer> upgradeSlots,
            boolean paginated, int prev, int next, int sortSlot) {
        if (slot < bottomStart || slot >= invSize)
            return true;
        if (paginated && (slot == prev || slot == next))
            return true;
        if (upgradeSlots != null && upgradeSlots.contains(slot))
            return true;
        if (sortSlot >= 0 && slot == sortSlot)
            return true;
        return false;
    }

    /**
     * Pick a bottom-row slot for a sort button that does not collide with upgrade
     * sockets (and does not collide with page buttons if paginated).
     *
     * @return inventory slot index, or -1 if no slot is available.
     */
    public static int sortButtonSlot(int invSize, List<Integer> upgradeSlots, boolean paginated) {
        int bottomStart = bottomRowStart(invSize);
        int prev = prevButtonSlot(invSize);
        int next = nextButtonSlot(invSize);

        int preferred = bottomStart + 1; // second-from-left
        if (!isBlocked(preferred, bottomStart, invSize, upgradeSlots, paginated, prev, next, -1))
            return preferred;

        // Fallback: scan from left to right for the first free slot
        for (int i = 0; i < 9; i++) {
            int candidate = bottomStart + i;
            if (!isBlocked(candidate, bottomStart, invSize, upgradeSlots, paginated, prev, next, -1))
                return candidate;
        }
        return -1;
    }
}
