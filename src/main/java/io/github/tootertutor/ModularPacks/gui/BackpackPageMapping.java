package io.github.tootertutor.ModularPacks.gui;

/**
 * Explicit mapping between visible GUI storage slots and global logical slots.
 */
public final class BackpackPageMapping {

    public static final int PAGINATED_PAGE_SIZE = 45;

    private BackpackPageMapping() {
    }

    public static int logicalIndex(boolean paginated, int page, int visibleSlot, int logicalSize) {
        if (page < 0 || visibleSlot < 0 || logicalSize < 0) {
            return -1;
        }
        long index = paginated
                ? (long) page * PAGINATED_PAGE_SIZE + visibleSlot
                : visibleSlot;
        return index >= logicalSize ? -1 : (int) index;
    }

    public static int validVisibleSlots(boolean paginated, int page, int visibleCapacity, int logicalSize) {
        if (visibleCapacity <= 0 || logicalSize <= 0) {
            return 0;
        }
        int start = logicalIndex(paginated, page, 0, logicalSize);
        if (start < 0) {
            return 0;
        }
        return Math.min(visibleCapacity, logicalSize - start);
    }
}
