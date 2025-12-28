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
    public static List<Integer> upgradeSocketSlots(int invSize, int upgradeSlots) {
        upgradeSlots = Math.max(0, Math.min(7, upgradeSlots));
        List<Integer> out = new ArrayList<>();
        if (upgradeSlots == 0)
            return out;

        int startCol = (9 - upgradeSlots) / 2;
        int rowStart = bottomRowStart(invSize);

        for (int i = 0; i < upgradeSlots; i++) {
            out.add(rowStart + startCol + i);
        }
        return out;
    }

    /** Storage area = everything except bottom row (if nav row exists). */
    public static int storageAreaSize(int invSize, boolean hasNavRow) {
        return hasNavRow ? invSize - 9 : invSize;
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

        int[] preferred = new int[] {
                bottomStart + 4,
                bottomStart + 5,
                bottomStart + 3,
                bottomStart + 6,
                bottomStart + 2,
                bottomStart + 1,
                bottomStart + 7,
                bottomStart + 0,
                bottomStart + 8
        };

        for (int slot : preferred) {
            if (slot < bottomStart || slot >= invSize)
                continue;
            if (paginated && (slot == prev || slot == next))
                continue;
            if (upgradeSlots != null && upgradeSlots.contains(slot))
                continue;
            return slot;
        }
        return -1;
    }
}
