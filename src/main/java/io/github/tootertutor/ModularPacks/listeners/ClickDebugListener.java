package io.github.tootertutor.ModularPacks.listeners;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.gui.ModuleScreenHolder;

/**
 * Debug-only listener: logs inventory click/drag events to a file for
 * diagnosing
 * client sorting mods (e.g. IPN).
 */
public final class ClickDebugListener implements Listener {

    private static final int FLUSH_PERIOD_TICKS = 20;
    private static final int FLUSH_MAX_LINES = 750;

    private final ModularPacksPlugin plugin;
    private final File logFile;
    private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();
    private BukkitTask flushTask;

    public ClickDebugListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "click-events.log");
    }

    public void start() {
        if (flushTask != null)
            return;

        plugin.getDataFolder().mkdirs();
        enqueue("# --- click debug started at " + Instant.now() + " ---");

        flushTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                flushSome();
            } catch (Exception ex) {
                plugin.getLogger().warning("ClickDebugListener flush failed: " + ex.getMessage());
            }
        }, FLUSH_PERIOD_TICKS, FLUSH_PERIOD_TICKS);
    }

    public void stop() {
        if (flushTask != null)
            flushTask.cancel();
        flushTask = null;

        enqueue("# --- click debug stopped at " + Instant.now() + " ---");
        try {
            flushAll();
        } catch (Exception ignored) {
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onClick(InventoryClickEvent e) {
        HumanEntity who = e.getWhoClicked();
        if (!(who instanceof Player player))
            return;

        Inventory top = e.getView().getTopInventory();
        Object holder = top == null ? null : top.getHolder();

        // Keep noise down: only log our plugin menus by default.
        if (!(holder instanceof BackpackMenuHolder) && !(holder instanceof ModuleScreenHolder))
            return;

        int tick = Bukkit.getCurrentTick();
        Instant ts = Instant.now();

        boolean clickedTop = e.getClickedInventory() != null && e.getClickedInventory().equals(top);
        String clickedInv = e.getClickedInventory() == null ? "NONE" : (clickedTop ? "TOP" : "BOTTOM");
        String direction = moveDirection(e, clickedTop);

        String line = new StringJoiner(" | ")
                .add("ts=" + ts)
                .add("tick=" + tick)
                .add("event=" + e.getClass().getSimpleName())
                .add("player=" + player.getName())
                .add("holder=" + holderSummary(holder))
                .add("invType=" + safeInvType(top))
                .add("clicked=" + clickedInv)
                .add("dir=" + direction)
                .add("click=" + e.getClick())
                .add("action=" + e.getAction())
                .add("slotType=" + e.getSlotType())
                .add("slot=" + e.getSlot())
                .add("rawSlot=" + e.getRawSlot())
                .add("hotbar=" + e.getHotbarButton())
                .add("cancelled=" + e.isCancelled())
                .add("result=" + e.getResult())
                .add("current=" + itemSummary(e.getCurrentItem()))
                .add("cursor=" + itemSummary(e.getCursor()))
                .toString();

        enqueue(line);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent e) {
        HumanEntity who = e.getWhoClicked();
        if (!(who instanceof Player player))
            return;

        Inventory top = e.getView().getTopInventory();
        Object holder = top == null ? null : top.getHolder();

        if (!(holder instanceof BackpackMenuHolder) && !(holder instanceof ModuleScreenHolder))
            return;

        int tick = Bukkit.getCurrentTick();
        Instant ts = Instant.now();

        String line = new StringJoiner(" | ")
                .add("ts=" + ts)
                .add("tick=" + tick)
                .add("event=" + e.getClass().getSimpleName())
                .add("player=" + player.getName())
                .add("holder=" + holderSummary(holder))
                .add("invType=" + safeInvType(top))
                .add("type=" + e.getType())
                .add("rawSlots=" + e.getRawSlots())
                .add("cancelled=" + e.isCancelled())
                .add("oldCursor=" + itemSummary(e.getOldCursor()))
                .add("newItems=" + e.getNewItems().size())
                .toString();

        enqueue(line);
    }

    private static String safeInvType(Inventory inv) {
        try {
            InventoryType t = inv == null ? null : inv.getType();
            return t == null ? "UNKNOWN" : t.name();
        } catch (Exception ignored) {
            return "UNKNOWN";
        }
    }

    private static String moveDirection(InventoryClickEvent e, boolean clickedTop) {
        if (e.getAction() == null)
            return "UNKNOWN";

        if (e.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return clickedTop ? "TOP->BOTTOM" : "BOTTOM->TOP";
        }

        if (e.getAction() == org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP) {
            return clickedTop ? "HOTBAR->TOP" : "HOTBAR->BOTTOM";
        }

        return clickedTop ? "TOP" : "BOTTOM";
    }

    private static String holderSummary(Object holder) {
        if (holder == null)
            return "NONE";
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
        return holder.getClass().getSimpleName();
    }

    private static String itemSummary(ItemStack it) {
        if (it == null)
            return "null";
        if (it.getType() == null || it.getType().isAir())
            return "AIR";
        String type = it.getType().name();
        int amt = it.getAmount();
        String meta = it.hasItemMeta() ? "meta" : "no-meta";
        return type + "x" + amt + "(" + meta + ")";
    }

    private void enqueue(String line) {
        pending.add(line);
    }

    private void flushSome() throws IOException {
        if (pending.isEmpty())
            return;

        int wrote = 0;
        StringBuilder sb = new StringBuilder(8 * 1024);
        while (wrote < FLUSH_MAX_LINES) {
            String line = pending.poll();
            if (line == null)
                break;
            sb.append(line).append('\n');
            wrote++;
        }

        if (wrote == 0)
            return;

        Files.writeString(logFile.toPath(), sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private void flushAll() throws IOException {
        while (!pending.isEmpty()) {
            flushSome();
        }
    }
}
