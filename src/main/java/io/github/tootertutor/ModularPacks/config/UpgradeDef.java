package io.github.tootertutor.ModularPacks.config;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.bukkit.Material;

public record UpgradeDef(
                String id,
                String displayName,
                Material material,
                List<String> lore,
                int customModelData,
                boolean glint,
                boolean enabled,
                boolean toggleable,
                boolean secondaryAction,
                ScreenType screenType,
                int maxInstalled,
                String installGroup,
                int stackMultiplier,
                String rarityColor,
                String tier) {

        private static final Pattern LEADING_COLOR = Pattern.compile("^(?:&[k-or])*(&(?:#[0-9a-f]{6}|[0-9a-f]))",
                        Pattern.CASE_INSENSITIVE);

        /**
         * Backward-compatible constructor for module providers compiled against the
         * original definition API. Definitions created this way allow one installed
         * module of their exact type.
         */
        public UpgradeDef(
                        String id,
                        String displayName,
                        Material material,
                        List<String> lore,
                        int customModelData,
                        boolean glint,
                        boolean enabled,
                        boolean toggleable,
                        boolean secondaryAction,
                        ScreenType screenType) {
                this(id, displayName, material, lore, customModelData, glint, enabled, toggleable,
                                secondaryAction, screenType, 1, null, 1);
        }

        /**
         * Backward-compatible constructor without module-specific capacity.
         */
        public UpgradeDef(
                        String id,
                        String displayName,
                        Material material,
                        List<String> lore,
                        int customModelData,
                        boolean glint,
                        boolean enabled,
                        boolean toggleable,
                        boolean secondaryAction,
                        ScreenType screenType,
                        int maxInstalled,
                        String installGroup) {
                this(id, displayName, material, lore, customModelData, glint, enabled, toggleable,
                                secondaryAction, screenType, maxInstalled, installGroup, 1);
        }

        /** Preserves the original constructor for external module providers. */
        public UpgradeDef(String id, String displayName, Material material, List<String> lore,
                        int customModelData, boolean glint, boolean enabled, boolean toggleable,
                        boolean secondaryAction, ScreenType screenType, int maxInstalled,
                        String installGroup, int stackMultiplier) {
                this(id, displayName, material, lore, customModelData, glint, enabled, toggleable,
                                secondaryAction, screenType, maxInstalled, installGroup, stackMultiplier, null, null);
        }

        public UpgradeDef {
                maxInstalled = Math.max(1, maxInstalled);
                if (installGroup != null) {
                        installGroup = installGroup.trim();
                        if (installGroup.isEmpty()) {
                                installGroup = null;
                        }
                }
                stackMultiplier = Math.max(1, stackMultiplier);
                String type = id == null ? "" : id.toLowerCase(Locale.ROOT);
                if (rarityColor == null || rarityColor.isBlank()) {
                        rarityColor = switch (type) {
                                case "goldstack" -> "&6";
                                case "diamondstack", "everlasting" -> "&b";
                                case "netheritestack" -> "&8";
                                default -> displayNameColor(displayName);
                        };
                }
                if (tier == null) {
                        tier = switch (type) {
                                case "ironstack" -> "Iron";
                                case "goldstack" -> "Gold";
                                case "diamondstack" -> "Diamond";
                                case "netheritestack" -> "Netherite";
                                default -> "";
                        };
                }
        }

        private static String displayNameColor(String displayName) {
                var color = LEADING_COLOR.matcher(displayName == null ? "" : displayName);
                return color.find() ? color.group(1) : "&f";
        }
}
