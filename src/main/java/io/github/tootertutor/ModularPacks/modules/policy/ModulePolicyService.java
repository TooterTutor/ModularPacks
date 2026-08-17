package io.github.tootertutor.ModularPacks.modules.policy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;

/**
 * Central entry point for module installation and removal validation.
 */
public final class ModulePolicyService {

    private final ModularPacksPlugin plugin;
    private final List<ModulePolicy> policies;

    public ModulePolicyService(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.policies = List.of(
                new InstallationLimitPolicy(plugin.cfg()::findUpgrade),
                new TankCompatibilityPolicy(),
                new StackCapacityRemovalPolicy(plugin));
    }

    public ModulePolicyDecision canInstall(BackpackData data, String moduleType) {
        ModulePolicyContext context = context(data, moduleType, null);
        for (ModulePolicy policy : policies) {
            ModulePolicyDecision decision = policy.canInstall(context);
            if (!decision.allowed()) {
                return decision;
            }
        }
        return ModulePolicyDecision.allow();
    }

    public ModulePolicyDecision canRemove(BackpackData data, UUID moduleId) {
        List<ModulePolicyContext.InstalledModule> installedModules = installedModules(data);

        ModulePolicyContext.InstalledModule installedModule = installedModules.stream()
                .filter(installed -> installed.moduleId().equals(moduleId))
                .findFirst()
                .orElse(null);

        if (installedModule == null) {
            return ModulePolicyDecision.allow();
        }

        String moduleType = installedModule.moduleType();

        ModulePolicyContext context = new ModulePolicyContext(
                data,
                moduleType,
                moduleId,
                plugin.cfg().findUpgrade(moduleType),
                installedModules);

        for (ModulePolicy policy : policies) {
            ModulePolicyDecision decision = policy.canRemove(context);
            if (!decision.allowed()) {
                return decision;
            }
        }

        return ModulePolicyDecision.allow();
    }

    private ModulePolicyContext context(BackpackData data, String moduleType, UUID moduleId) {
        return new ModulePolicyContext(
                data,
                moduleType,
                moduleId,
                plugin.cfg().findUpgrade(moduleType),
                installedModules(data));
    }

    private List<ModulePolicyContext.InstalledModule> installedModules(BackpackData data) {
        if (data == null) {
            return List.of();
        }

        List<Map.Entry<Integer, UUID>> entries = new ArrayList<>(data.installedModules().entrySet());

        entries.sort(Comparator.comparingInt(entry -> entry.getKey()));

        List<ModulePolicyContext.InstalledModule> installed = new ArrayList<>(entries.size());

        for (Map.Entry<Integer, UUID> entry : entries) {
            UUID moduleId = entry.getValue();

            installed.add(new ModulePolicyContext.InstalledModule(
                    entry.getKey(),
                    moduleId,
                    readModuleType(data, moduleId)));
        }

        return List.copyOf(installed);
    }

    private String readModuleType(BackpackData data, UUID moduleId) {
        byte[] snapshot = data.installedSnapshots().get(moduleId);
        if (snapshot == null) {
            return null;
        }

        try {
            ItemStack[] items = ItemStackCodec.fromBytes(snapshot);
            if (items.length == 0 || items[0] == null) {
                return null;
            }
            ItemMeta meta = items[0].getItemMeta();
            if (meta == null) {
                return null;
            }
            return meta.getPersistentDataContainer().get(plugin.keys().MODULE_TYPE, PersistentDataType.STRING);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Unable to inspect installed module " + moduleId
                    + " while evaluating module policy: " + ex.getMessage());
            return null;
        }
    }
}
