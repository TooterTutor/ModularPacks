package io.github.tootertutor.ModularPacks.gui;

/** Backpack storage capacity excludes the always-present navigation row. */
public record BackpackMenuLayout(boolean paginated, int page, int pageSize, int inventorySize) {

    public static BackpackMenuLayout create(int rows, int requestedPage, boolean resizeGui) {
        if (rows < 1) {
            throw new IllegalArgumentException("Backpacks require at least one storage row");
        }
        boolean paginated = rows > 5;
        int logicalSlots = Math.multiplyExact(rows, 9);
        int pageSize = paginated ? BackpackPageMapping.PAGINATED_PAGE_SIZE : logicalSlots;
        int pageCount = 1 + (logicalSlots - 1) / pageSize;
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int storageRows = Math.min(rows, 5);
        if (paginated && resizeGui) {
            int validSlots = Math.min(pageSize, logicalSlots - page * pageSize);
            storageRows = (validSlots + 8) / 9;
        }
        return new BackpackMenuLayout(paginated, page, pageSize, (storageRows + 1) * 9);
    }
}
