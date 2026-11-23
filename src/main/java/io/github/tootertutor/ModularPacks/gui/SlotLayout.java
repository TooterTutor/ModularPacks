package io.github.tootertutor.ModularPacks.gui;

import java.util.List;

public final class SlotLayout {
    private SlotLayout() {
    }

    public static int bottomRowStart(int invSize) {
        return io.github.tootertutor.ModularCore.gui.SlotLayout.bottomRowStart(invSize);
    }

    public static boolean isBottomRow(int invSize, int slot) {
        return io.github.tootertutor.ModularCore.gui.SlotLayout.isBottomRow(invSize, slot);
    }

    public static int prevButtonSlot(int invSize) {
        return io.github.tootertutor.ModularCore.gui.SlotLayout.prevButtonSlot(invSize);
    }

    public static int nextButtonSlot(int invSize) {
        return io.github.tootertutor.ModularCore.gui.SlotLayout.nextButtonSlot(invSize);
    }

    public static int navRowSlot(int invSize, int rowOffset) {
        return io.github.tootertutor.ModularCore.gui.SlotLayout.navRowSlot(invSize, rowOffset);
    }

    public static List<Integer> centeredRowSlots(int rowStart, int itemCount, int gap) {
        return io.github.tootertutor.ModularCore.gui.SlotLayout.centeredRowSlots(rowStart, itemCount, gap);
    }

    public static List<Integer> upgradeSocketSlots(int invSize, int upgradeSlots, boolean paginated) {
        return io.github.tootertutor.ModularCore.gui.SlotLayout.upgradeSocketSlots(invSize, upgradeSlots, paginated);
    }

    public static int storageAreaSize(int invSize, boolean hasNavRow) {
        return io.github.tootertutor.ModularCore.gui.SlotLayout.storageAreaSize(invSize, hasNavRow);
    }

    public static int modeButtonSlot(int invSize, List<Integer> upgradeSlots, boolean paginated, int sortSlot) {
        return io.github.tootertutor.ModularCore.gui.SlotLayout.modeButtonSlot(invSize, upgradeSlots, paginated, sortSlot);
    }

    public static int sortButtonSlot(int invSize, List<Integer> upgradeSlots, boolean paginated) {
        return io.github.tootertutor.ModularCore.gui.SlotLayout.sortButtonSlot(invSize, upgradeSlots, paginated);
    }
}
