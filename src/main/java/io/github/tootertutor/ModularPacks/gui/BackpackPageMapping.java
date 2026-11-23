package io.github.tootertutor.ModularPacks.gui;

public final class BackpackPageMapping {
    public static final int PAGINATED_PAGE_SIZE =
            io.github.tootertutor.ModularCore.gui.StoragePageMapping.PAGINATED_PAGE_SIZE;

    private BackpackPageMapping() {
    }

    public static int logicalIndex(boolean paginated, int page, int visibleSlot, int logicalSize) {
        return io.github.tootertutor.ModularCore.gui.StoragePageMapping.logicalIndex(paginated, page, visibleSlot, logicalSize);
    }

    public static int validVisibleSlots(boolean paginated, int page, int visibleCapacity, int logicalSize) {
        return io.github.tootertutor.ModularCore.gui.StoragePageMapping.validVisibleSlots(paginated, page, visibleCapacity, logicalSize);
    }
}
