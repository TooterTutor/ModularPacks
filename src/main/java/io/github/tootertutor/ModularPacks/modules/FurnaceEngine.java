package io.github.tootertutor.ModularPacks.modules;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.FurnaceView;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;

final class FurnaceEngine {

    private final ModularPacksPlugin plugin;

    FurnaceEngine(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    void tickFurnaceScreen(
            Player player,
            UUID backpackId,
            String backpackType,
            UUID moduleId,
            ScreenType screenType,
            Inventory inv,
            int dtTicks) {
        BackpackData data = plugin.repo().loadOrCreate(backpackId, backpackType);

        byte[] bytes = data.moduleStates().get(moduleId);
        FurnaceStateCodec.State stored = FurnaceStateCodec.decode(bytes);

        // Merge: use CURRENT UI items, keep STORED progress
        FurnaceStateCodec.State s = new FurnaceStateCodec.State();
        s.input = inv.getItem(0);
        s.fuel = inv.getItem(1);
        s.output = inv.getItem(2);

        s.burnTime = stored.burnTime;
        s.burnTotal = stored.burnTotal;
        s.cookTime = stored.cookTime;
        s.cookTotal = stored.cookTotal;
        s.xpStored = reconcileXpStored(stored, s.output);

        boolean changed = tickFurnaceLike(screenType, s, dtTicks);
        if (!changed)
            return;

        // Push back to UI
        inv.setItem(0, s.input);
        inv.setItem(1, s.fuel);
        inv.setItem(2, s.output);

        // Progress bars (client-side) for furnace-like screens.
        var view = player.getOpenInventory();
        if (view != null && view.getTopInventory() == inv) {
            if (view instanceof FurnaceView fv) {
                fv.setBurnTime(s.burnTime, s.burnTotal);
                fv.setCookTime(s.cookTime, s.cookTotal);
            }
        }
        // Send data packets as a best-effort fallback for older clients / edge cases.
        ContainerDataSync.trySyncFurnaceLike(player, s.burnTime, s.burnTotal, s.cookTime, s.cookTotal);

        // Persist
        data.moduleStates().put(moduleId, FurnaceStateCodec.encode(s));
        plugin.repo().saveBackpack(data);
    }

    boolean tickInstalledFurnaces(BackpackData data, Set<UUID> openModuleIds, int dtTicks) {
        boolean changedAny = false;
        for (UUID moduleId : data.installedModules().values()) {
            if (moduleId == null)
                continue;
            if (openModuleIds != null && openModuleIds.contains(moduleId))
                continue;

            ScreenType st = resolveInstalledModuleScreenType(data, moduleId);
            if (st != ScreenType.SMELTING && st != ScreenType.BLASTING && st != ScreenType.SMOKING)
                continue;

            byte[] stateBytes = data.moduleStates().get(moduleId);
            FurnaceStateCodec.State s = FurnaceStateCodec.decode(stateBytes);
            boolean changed = tickFurnaceLike(st, s, dtTicks);
            if (!changed)
                continue;

            data.moduleStates().put(moduleId, FurnaceStateCodec.encode(s));
            changedAny = true;
        }
        return changedAny;
    }

    private ScreenType resolveInstalledModuleScreenType(BackpackData data, UUID moduleId) {
        byte[] snap = data.installedSnapshots().get(moduleId);
        if (snap == null)
            return ScreenType.NONE;

        ItemStack[] arr;
        try {
            arr = ItemStackCodec.fromBytes(snap);
        } catch (Exception ex) {
            return ScreenType.NONE;
        }
        if (arr.length == 0 || arr[0] == null)
            return ScreenType.NONE;

        ItemMeta meta = arr[0].getItemMeta();
        if (meta == null)
            return ScreenType.NONE;

        var pdc = meta.getPersistentDataContainer();
        String upgradeId = pdc.get(plugin.keys().MODULE_TYPE, PersistentDataType.STRING);
        if (upgradeId == null)
            return ScreenType.NONE;

        Byte enabled = pdc.get(plugin.keys().MODULE_ENABLED, PersistentDataType.BYTE);
        if (enabled != null && enabled == 0)
            return ScreenType.NONE;

        var def = plugin.cfg().findUpgrade(upgradeId);
        if (def == null || !def.enabled())
            return ScreenType.NONE;

        return def.screenType();
    }

    private boolean tickFurnaceLike(ScreenType type, FurnaceStateCodec.State s, int dtTicks) {
        if (dtTicks <= 0)
            dtTicks = 1;

        boolean hasInput = s.input != null && !s.input.getType().isAir();
        boolean changed = false;

        if (!hasInput) {
            // Burn always decays if already lit, even if smelting can't progress.
            if (s.burnTime > 0) {
                int dec = Math.min(dtTicks, s.burnTime);
                s.burnTime -= dec;
                changed = true;
            }

            // cool down cookTime if no input
            if (s.cookTime > 0) {
                s.cookTime = Math.max(0, s.cookTime - 2 * dtTicks);
                changed = true;
            }
            if (s.burnTime <= 0 && (s.fuel == null || s.fuel.getType().isAir())) {
                if (s.burnTotal != 0) {
                    s.burnTotal = 0;
                    changed = true;
                }
            }
            return changed;
        }

        CookingRecipe<?> recipe = findCookingRecipe(type, s.input);
        if (recipe == null) {
            // Burn always decays if already lit, even if smelting can't progress.
            if (s.burnTime > 0) {
                int dec = Math.min(dtTicks, s.burnTime);
                s.burnTime -= dec;
                changed = true;
            }

            // input not valid; cool down
            if (s.cookTime > 0) {
                s.cookTime = Math.max(0, s.cookTime - 2 * dtTicks);
                changed = true;
            }
            if (s.burnTime <= 0 && (s.fuel == null || s.fuel.getType().isAir())) {
                if (s.burnTotal != 0) {
                    s.burnTotal = 0;
                    changed = true;
                }
            }
            return changed;
        }

        int total = recipe.getCookingTime();
        if (total <= 0)
            total = 200;
        if (s.cookTotal != total) {
            // recipe changed -> reset progress to prevent weird partial crafts
            s.cookTotal = total;
            s.cookTime = 0;
            changed = true;
        }

        ItemStack result = recipe.getResult();
        if (result == null || result.getType().isAir())
            return changed;

        int producedPerCraft = Math.max(1, result.getAmount());
        double xpPerCraft = Math.max(0.0, recipe.getExperience());

        boolean canOutput = true;
        int outputSpace = 0;
        if (s.output == null || s.output.getType().isAir()) {
            outputSpace = result.getMaxStackSize();
        } else {
            if (!s.output.isSimilar(result)) {
                canOutput = false;
            } else {
                outputSpace = s.output.getMaxStackSize() - s.output.getAmount();
            }
        }
        if (outputSpace < producedPerCraft)
            canOutput = false;

        // If we can't output, don't consume new fuel and don't progress cooking; just
        // cool down.
        if (!canOutput) {
            // Burn always decays if already lit, even if smelting can't progress.
            if (s.burnTime > 0) {
                int dec = Math.min(dtTicks, s.burnTime);
                s.burnTime -= dec;
                changed = true;
            }

            if (s.cookTime > 0) {
                s.cookTime = Math.max(0, s.cookTime - 2 * dtTicks);
                changed = true;
            }
            if (s.burnTime <= 0 && (s.fuel == null || s.fuel.getType().isAir())) {
                if (s.burnTotal != 0) {
                    s.burnTotal = 0;
                    changed = true;
                }
            }
            return changed;
        }

        // Consume fuel ONLY when we need to light (burnTime <= 0).
        if (s.burnTime <= 0) {
            int fuelTicks = fuelTicks(s.fuel);
            if (fuelTicks > 0) {
                s.fuel = consumeOneFuel(s.fuel);
                s.burnTime = fuelTicks;
                s.burnTotal = fuelTicks;
                changed = true;
            }
        }

        // If not burning after trying to light, cool down.
        if (s.burnTime <= 0) {
            if (s.cookTime > 0) {
                s.cookTime = Math.max(0, s.cookTime - 2 * dtTicks);
                changed = true;
            }
            if (s.burnTime <= 0 && (s.fuel == null || s.fuel.getType().isAir())) {
                if (s.burnTotal != 0) {
                    s.burnTotal = 0;
                    changed = true;
                }
            }
            return changed;
        }

        int burnStep = Math.min(dtTicks, s.burnTime);
        if (burnStep > 0) {
            s.burnTime -= burnStep;
            changed = true;
        }
        if (burnStep > 0) {
            int newCookTime = s.cookTime + burnStep;
            boolean crafted = false;

            while (newCookTime >= s.cookTotal) {
                if (s.input == null || s.input.getType().isAir())
                    break;

                // Re-check output space for each craft (important when producedPerCraft > 1).
                if (s.output == null || s.output.getType().isAir()) {
                    outputSpace = result.getMaxStackSize();
                } else {
                    outputSpace = s.output.getMaxStackSize() - s.output.getAmount();
                }
                if (outputSpace < producedPerCraft)
                    break;

                // produce output
                if (s.output == null || s.output.getType().isAir()) {
                    s.output = result.clone();
                } else {
                    s.output.setAmount(s.output.getAmount() + producedPerCraft);
                }

                // consume one input (after output is produced so single-item stacks still
                // craft)
                s.input = BackpackInventoryUtil.decrementOne(s.input);

                if (xpPerCraft > 0.0) {
                    s.xpStored += xpPerCraft;
                }

                crafted = true;
                newCookTime -= s.cookTotal;

                if (s.input == null || s.input.getType().isAir()) {
                    newCookTime = 0;
                    break;
                }
            }

            if (crafted || newCookTime != s.cookTime) {
                s.cookTime = newCookTime;
                changed = true;
            }
        }

        if (s.burnTime <= 0 && (s.fuel == null || s.fuel.getType().isAir())) {
            if (s.burnTotal != 0) {
                s.burnTotal = 0;
                changed = true;
            }
        }

        return changed;
    }

    private static double reconcileXpStored(FurnaceStateCodec.State stored, ItemStack currentOutput) {
        if (stored == null)
            return 0.0;

        double xp = Math.max(0.0, stored.xpStored);
        if (xp <= 0.0)
            return 0.0;

        if (isAir(currentOutput) || isAir(stored.output))
            return 0.0;

        if (!stored.output.isSimilar(currentOutput))
            return 0.0;

        int storedAmt = stored.output.getAmount();
        int currentAmt = currentOutput.getAmount();
        if (storedAmt <= 0 || currentAmt <= 0)
            return 0.0;

        if (storedAmt == currentAmt)
            return xp;

        // Scale XP proportionally to the current output count (handles out-of-sync
        // state due
        // to click timing).
        return xp * (currentAmt / (double) storedAmt);
    }

    private static boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private CookingRecipe<?> findCookingRecipe(ScreenType type, ItemStack input) {
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (!(r instanceof CookingRecipe<?> cr))
                continue;

            boolean matchesType = switch (type) {
                case SMELTING -> r instanceof FurnaceRecipe;
                case BLASTING -> r instanceof BlastingRecipe;
                case SMOKING -> r instanceof SmokingRecipe;
                default -> false;
            };
            if (!matchesType)
                continue;

            if (cr.getInputChoice() != null && cr.getInputChoice().test(input)) {
                return cr;
            }
        }
        return null;
    }

    private int fuelTicks(ItemStack fuel) {
        if (fuel == null || fuel.getType().isAir())
            return 0;

        var itemType = fuel.getType().asItemType();
        if (itemType == null || !itemType.isFuel())
            return 0;

        return Math.max(0, itemType.getBurnDuration());
    }

    private ItemStack consumeOneFuel(ItemStack fuel) {
        if (fuel == null || fuel.getType().isAir())
            return null;

        // Handle container fuel items (lava bucket -> bucket).
        Material remaining = fuel.getType().getCraftingRemainingItem();
        if (remaining != null && !remaining.isAir()) {
            return new ItemStack(remaining, 1);
        }

        return BackpackInventoryUtil.decrementOne(fuel);
    }

    /**
     * Sends container data updates for furnace-like menus, including blast furnace
     * and smoker.
     * Uses reflection so this can compile against paper-api.
     */
    private static final class ContainerDataSync {
        private static volatile boolean initialized;
        private static volatile boolean available;

        private static Method craftPlayerGetHandle;
        private static Field serverPlayerConnectionField;
        private static Field serverPlayerContainerMenuField;
        private static Field menuContainerIdField;
        private static Method connectionSendMethod;
        private static Constructor<?> setDataPacketCtor;

        private ContainerDataSync() {
        }

        static void trySyncFurnaceLike(Player player, int burnTime, int burnTotal, int cookTime, int cookTotal) {
            if (player == null)
                return;

            ensureInit();
            if (!available)
                return;

            try {
                Object handle = craftPlayerGetHandle.invoke(player);
                if (handle == null)
                    return;

                Object menu = serverPlayerContainerMenuField.get(handle);
                if (menu == null)
                    return;

                int containerId = menuContainerIdField.getInt(menu);

                Object connection = serverPlayerConnectionField.get(handle);
                if (connection == null)
                    return;

                // AbstractFurnaceMenu data indices:
                // 0 = burnTime (litTime), 1 = burnTotal (litDuration), 2 = cookTime, 3 =
                // cookTotal
                send(connection, newSetDataPacket(containerId, 0, burnTime));
                send(connection, newSetDataPacket(containerId, 1, burnTotal));
                send(connection, newSetDataPacket(containerId, 2, cookTime));
                send(connection, newSetDataPacket(containerId, 3, cookTotal));
            } catch (ReflectiveOperationException ignored) {
            }
        }

        private static Object newSetDataPacket(int containerId, int id, int value) throws ReflectiveOperationException {
            return setDataPacketCtor.newInstance(containerId, id, value);
        }

        private static void send(Object connection, Object packet) throws ReflectiveOperationException {
            connectionSendMethod.invoke(connection, packet);
        }

        private static void ensureInit() {
            if (initialized)
                return;
            synchronized (ContainerDataSync.class) {
                if (initialized)
                    return;
                initialized = true;

                try {
                    String craftPackage = Bukkit.getServer().getClass().getPackage().getName();
                    Class<?> craftPlayer = Class.forName(craftPackage + ".entity.CraftPlayer");
                    craftPlayerGetHandle = craftPlayer.getMethod("getHandle");

                    Class<?> serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
                    Class<?> abstractMenu = Class.forName("net.minecraft.world.inventory.AbstractContainerMenu");
                    Class<?> connectionClazz = Class
                            .forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
                    Class<?> packetInterface = Class.forName("net.minecraft.network.protocol.Packet");

                    serverPlayerConnectionField = findField(serverPlayer, "connection", connectionClazz);
                    serverPlayerContainerMenuField = findField(serverPlayer, "containerMenu", abstractMenu);
                    menuContainerIdField = findIntField(abstractMenu, "containerId");

                    connectionSendMethod = null;
                    for (Method m : connectionClazz.getMethods()) {
                        if (!m.getName().equals("send"))
                            continue;
                        if (m.getParameterCount() != 1)
                            continue;
                        if (!packetInterface.isAssignableFrom(m.getParameterTypes()[0]))
                            continue;
                        connectionSendMethod = m;
                        break;
                    }
                    if (connectionSendMethod == null) {
                        available = false;
                        return;
                    }

                    Class<?> packetClazz = Class
                            .forName("net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket");
                    setDataPacketCtor = null;
                    for (var c : packetClazz.getConstructors()) {
                        var params = c.getParameterTypes();
                        if (params.length == 3 && params[0] == int.class && params[1] == int.class
                                && params[2] == int.class) {
                            setDataPacketCtor = c;
                            break;
                        }
                    }

                    available = serverPlayerConnectionField != null
                            && serverPlayerContainerMenuField != null
                            && menuContainerIdField != null
                            && setDataPacketCtor != null;
                } catch (ReflectiveOperationException ex) {
                    available = false;
                }
            }
        }

        private static java.lang.reflect.Field findField(Class<?> owner, String preferredName, Class<?> type)
                throws ReflectiveOperationException {
            try {
                var f = owner.getDeclaredField(preferredName);
                if (type.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f;
                }
            } catch (NoSuchFieldException ignored) {
            }

            for (var f : owner.getDeclaredFields()) {
                if (!type.isAssignableFrom(f.getType()))
                    continue;
                f.setAccessible(true);
                return f;
            }

            throw new NoSuchFieldException(preferredName);
        }

        private static java.lang.reflect.Field findIntField(Class<?> owner, String preferredName)
                throws ReflectiveOperationException {
            try {
                var f = owner.getDeclaredField(preferredName);
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    return f;
                }
            } catch (NoSuchFieldException ignored) {
            }

            for (var f : owner.getDeclaredFields()) {
                if (f.getType() != int.class)
                    continue;
                f.setAccessible(true);
                return f;
            }

            throw new NoSuchFieldException(preferredName);
        }
    }
}
