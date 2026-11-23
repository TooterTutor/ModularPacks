package io.github.tootertutor.ModularPacks.listeners.backpack;

import org.bukkit.inventory.InventoryHolder;

import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.gui.ModuleScreenHolder;

public final class BackpackMenuDebug {

    private BackpackMenuDebug() {
    }

    public static String describeHolder(InventoryHolder holder) {
        if (holder instanceof BackpackMenuHolder bmh) {
            return "BackpackMenu(backpackId=" + bmh.backpackId()
                    + ",type=" + bmh.type().id()
                    + ",page=" + bmh.page()
                    + ",rows=" + bmh.type().rows()
                    + ",upgradeSlots=" + bmh.type().upgradeSlots()
                    + ")";
        }
        if (holder instanceof ModuleScreenHolder msh) {
            return "ModuleScreen(backpackId=" + msh.backpackId()
                    + ",type=" + msh.backpackType()
                    + ",moduleId=" + msh.moduleId()
                    + ",screen=" + msh.screenType()
                    + ")";
        }
        return null;
    }
}
