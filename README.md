# ModularPacks

![Paper](https://img.shields.io/badge/Paper-1.21.10%2B-blue?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square)
![API](https://img.shields.io/badge/API-available-brightgreen?style=flat-square)
![Storage](https://img.shields.io/badge/storage-SQLite-lightgrey?style=flat-square)
![Status](https://img.shields.io/badge/status-active_development-purple?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-lightgrey?style=flat-square)

**Configurable, modular backpacks for Paper servers.**

Physical backpack items with persistent storage, portable workstations, passive automation, and shared inventories. Supports vanilla clients, optional resource-pack visuals, and custom modules through a public API.

<p align="center">
  <a href="https://discord.com/invite/QPBFaD4dkQ"><img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_vector.svg" height="56" alt="Join the Discord community"></a>
  <a href="https://modrinth.com/plugin/modularpacks"><img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg" height="56" alt="Download ModularPacks on Modrinth"></a>
  <a href="https://github.com/TooterTutor/ModularPacks/issues"><img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/github_vector.svg" height="56" alt="GitHub issues"></a>
</p>

<h2 align="center"> Table of Contents </h2>
<p align="center">
  <a href="#features">Features</a> · <a href="#backpacks">Backpacks</a> · <a href="#modules">Modules</a> · <a href="#server-setup">Server setup</a> · <a href="#controls">Controls</a> · <a href="#reference">Reference</a>
</p>

---

<a id="features"></a>

## Features

| Capability | Support |
| --- | --- |
| **Backpack storage** | Configurable tiers, paginated inventories, sorting, and stack-capacity multipliers. |
| **Equipment & placement** | Virtual back slot independent of chest equipment, wearable models, and placeable backpacks. |
| **Modules** | Portable workstations, item collection, restocking, feeding, fluid and XP storage, and automatic crafting. |
| **Shared inventories** | Backpack hosting and joining with configurable sharing limits. |
| **Server configuration** | Custom recipes, module installation limits, insertion restrictions, rendering, and localized menus. |
| **Administration & integration** | SQLite persistence, inspection and recovery commands, void recovery logs, and a custom-module API. |

## Backpacks

Default survival tiers are defined in `backpacks.yml`. Storage capacity, module slots, recipes, and appearance are configurable per type.

| Backpack | Storage rows | Storage slots | Module slots |
| --- | ---: | ---: | ---: |
| Leather | 2 | 18 | 1 |
| Copper | 3 | 27 | 1 |
| Iron | 4 | 36 | 2 |
| Gold | 6 | 54 | 3 |
| Diamond | 8 | 72 | 4 |
| Netherite | 10 | 90 | 5 |

Storage rows exclude the navigation row, which stays available on every backpack. Larger packs use pages of up to 45 storage slots while retaining their full capacity.

<details>
<summary><strong>Custom tiers and the Creative backpack</strong></summary>

These are the defaults in `backpacks.yml`; server owners can change them or add new types. The command-only `Creative` backpack provides 30 storage rows (270 slots) and no module slots.

</details>

<a id="modules"></a>

## Modules

Module definitions live under `Upgrades:` in `modules.yml`. The IDs below are used in commands, recipe ingredients, and configuration. Most upgrade recipes use `ModuleTemplate`; `/backpack recipe module <id>` displays the configured recipe.

### Utility

| Module ID | Behavior |
| --- | --- |
| `Everlasting` | Makes the backpack indestructible. |
| `Magnet` | Collects nearby dropped items. |
| `Feeding` | Automatically feeds the player from backpack contents. |
| `Restock` | Restocks player inventory from backpack storage. |
| `Void` | Deletes filtered items, with logs for admin recovery. |
| `Quiver` | Supplies arrows from backpack storage. |
| `Jukebox` | Plays music discs with playlist controls. |

### Tanks & pumps

| Module ID | Behavior |
| --- | --- |
| `FluidTank` | Stores bucket-based fluids. |
| `Pump` | Moves fluids between inventory buckets and a Fluid Tank. |
| `ExperienceTank` | Stores and dispenses experience. |
| `ExpPump` | Transfers XP or maintains a target level, with optional Mending support. |

### Portable workstations

| Module ID | Workstation |
| --- | --- |
| `Crafting` | Crafting table. |
| `Autocrafting` | Automatic crafting at a configurable interval. |
| `Smelting` · `Blasting` · `Smoking` | Furnace, blast furnace, or smoker. |
| `Stonecutter` | Stonecutter. |
| `Anvil` | Anvil. |
| `Smithing` | Smithing table. |

### Stack upgrades

| Module ID | Stack capacity multiplier |
| --- | ---: |
| `IronStack` | ×2 |
| `GoldStack` | ×4 |
| `DiamondStack` | ×8 |
| `NetheriteStack` | ×16 |

Multipliers combine: Iron + Gold gives **×8** normal stack capacity. They also apply to normally unstackable items.

<details>
<summary><strong>Installation limits and module configuration</strong></summary>

Stack upgrades share a default limit of three combined, subject to available module slots. Defaults also allow two Fluid Tanks, two Experience Tanks, and three each of Smelting, Blasting, Smoking, and Autocrafting.

Module recipes, appearance, limits, and behavior live under `Upgrades:` in `modules.yml`. `MaxInstalled` controls installation limits; `InstallGroup` shares a limit between module types. The legacy mixed `Tank` module is disabled by default.

</details>

---

<a id="server-setup"></a>

## Server setup

**Requirements:** Paper **1.21.10+** and Java **21**; use the Java version required by your chosen Paper release. No client mods are required.

Install the JAR from [Modrinth](https://modrinth.com/plugin/modularpacks) or [GitHub Releases](https://github.com/TooterTutor/ModularPacks/releases/latest) into `plugins/` and start the server to generate configuration.

Files are generated in `plugins/ModularPacks/`:

| File | Customize |
| --- | --- |
| `config.yml` | Global behavior, rendering, sharing, update checks, and resource packs. |
| `backpacks.yml` | Backpack tiers, capacity, recipes, and appearance. |
| `modules.yml` | Module recipes, limits, and behavior. |
| `lang/en_us.yml` | Messages, menu text, and module instructions. |

Run `/backpack reload all` after changes, or `/backpack reload lang` for translations. Backpack data is stored in `backpacks.db`.

Resource-pack delivery, default layers, required-pack behavior, and update checks are configured under `modularpacks.ResourcePack` in `config.yml`. Players select and apply layers through **Backpack Settings → Resource Packs**.

Back up the plugin data folder, including `backpacks.db`, before major updates or migrations. Player permissions for commands, recipe previews, opening, placement, and pickup default to enabled; administration and reload permissions default to operators. See [Commands and permissions](#commands-and-permissions) for the full list.

## Controls

| Action | Control |
| --- | --- |
| Open a held backpack | Right-click. |
| Pack items into a backpack | Pick up an item stack with your cursor, then right-click the backpack in your inventory. |
| Equip or unequip | Use **Equip / Unequip** in Backpack Settings. |
| Open an equipped backpack | Sneak + swap-hands key (**Shift+F** by default). Works in Survival, Adventure, and Creative. |
| Place a held backpack | Sneak + right-click a block. |
| Open a placed backpack | Right-click it. |
| Pick up a placed backpack | Sneak + right-click with **both hands empty**. |
| Toggle a toggleable module | Shift + left-click its installed icon. |
| Remove a module | Shift + right-click its installed icon. |

The backpack takes as many items as will fit and leaves any remainder on your cursor. A successful insert plays the vanilla bundle sound. Backpacks cannot be packed into other backpacks.

In Survival or Adventure, use your regular inventory. In Creative, open a crafting table and use the player inventory below the crafting grid. Spectator inventories are read-only.

Hover over an installed module for its specific actions, including screens and secondary controls.

<details>
<summary><strong>Additional inventory gestures and interaction rules</strong></summary>

In the ordinary Survival/Adventure inventory, double-left-click **outside the inventory window** within 350 ms to open an equipped backpack. Both clicks require an empty cursor. This does not apply to item slots, containers, or plugin menus.

The virtual back slot takes priority over the chestplate slot. Normal hand swaps are preserved when you are not sneaking or have no backpack equipped. Equipped backpacks do not open on sneak-right-click.

Sneaking with an item in either hand bypasses placed-backpack interaction, allowing vanilla item use or held-backpack placement to proceed.

</details>

---

## Reference

Backpack items carry a persistent UUID; contents, module state, and sharing data live in SQLite. Admin tools can inspect stored backpacks, recover backpack items, and restore items logged by the Void module.

Full command syntax, configuration examples, persistence details, and API integration are documented below.

<details>
<summary><strong id="commands-and-permissions">Commands and permissions</strong></summary>

## Commands

The base command is:

```text
/backpack
```

### Help

```text
/backpack help
/backpack help <command>
```

Shows available commands based on the sender's permissions.

### Give backpacks and modules

```text
/backpack give type <typeId> [player] [amount]
/backpack give module <upgradeId> [player] [amount]
```

Examples:

```text
/backpack give type Leather Steve 1
/backpack give module Magnet Steve 1
```

### Preview recipes

```text
/backpack recipe backpack <typeId> [variantId]
/backpack recipe module <upgradeId>
```

Examples:

```text
/backpack recipe backpack Iron
/backpack recipe backpack Iron 2
/backpack recipe module Magnet
```

Recipe previews are useful for players because ModularPacks recipes can include backpack or module ingredients, not just vanilla materials.

### List backpacks

```text
/backpack list [playerName [menu] | unowned]
```

Examples:

```text
/backpack list
/backpack list Steve
/backpack list Steve menu
/backpack list unowned
```

This allows admins to inspect stored backpack records, including backpacks owned by offline players if the server has cached their UUID.

If `menu` is added after a player name, ModularPacks opens a GUI showing that player's stored backpacks instead of printing the list to chat. This is useful when a player owns multiple backpacks and an admin needs to quickly inspect or open one of them.

### Open backpacks as an admin

```text
/backpack open <uuid>
/backpack open <player> <Type#N>
```

Example:

```text
/backpack open Steve Netherite#1
```

This is useful for moderation, recovery, and support.

### Change the backpack type in your hand

```text
/backpack settype <typeId> [--force]
```

This changes the tier/type of the backpack currently held in your main hand.

Use this carefully. When changing a backpack to a smaller type, ModularPacks normally checks whether any items exist outside the new size and blocks the change if those slots are occupied.

The `--force` flag bypasses that protection and allows the backpack to be resized anyway. If the new type has fewer storage slots, any items beyond the new size may be truncated and lost. This should only be used by admins who understand the risk, preferably after checking the backpack contents or making a database backup.

### Reload configuration

```text
/backpack reload [config|lang|recipes|all]
```

Examples:

```text
/backpack reload
/backpack reload config
/backpack reload lang
/backpack reload recipes
/backpack reload all
```

Reloads specific parts of ModularPacks without requiring a full server restart.

* `config` reloads `config.yml`, backpack types, upgrades, placed backpack renders, and update checker settings.
* `lang` reloads language files.
* `recipes` reloads and re-registers backpack and module recipes.
* `all` reloads everything.

Running `/backpack reload` with no argument behaves like `all`.

### Recovery tools

```text
/backpack recover backpack <player> <backpackUuid>
/backpack recover void <player|uuid> list [limit] [all]
/backpack recover void <player|uuid> <id|latest> [receiver]
```

The void recovery commands work with the `voided_items` database table. When the Void module deletes an item, ModularPacks stores enough item data for admins to recover it later.

## Permissions

| Permission                   | Default | Description                                    |
| ---------------------------- | ------- | ---------------------------------------------- |
| `modularpacks.command`       | `true`  | Allows use of the base `/backpack` command     |
| `modularpacks.recipe`        | `true`  | Allows recipe preview commands                 |
| `modularpacks.open`          | `true`  | Allows players to open backpacks               |
| `modularpacks.place`         | `true`  | Allows players to place backpacks in the world |
| `modularpacks.pickup`        | `true`  | Allows players to pick up placed backpacks     |
| `modularpacks.reload`        | `op`    | Allows reloading config, language, and recipes |
| `modularpacks.admin`         | `op`    | Allows admin-level backpack tools              |
| `modularpacks.update.notify` | `op`    | Receives update notifications when enabled     |

Server owners should review these defaults before publishing a server. The player-facing permissions are enabled by default, while administrative permissions are restricted to operators.

</details>

<details>
<summary><strong id="configuration-and-data">Configuration and data</strong></summary>

## Configuration

Main configuration file:

```text
plugins/ModularPacks/config.yml
```

Configuration is split across three files:

* `config.yml` → `modularpacks:` for global plugin behavior
* `backpacks.yml` → `BackpackTypes:` for backpack definitions
* `modules.yml` → `Upgrades:` for module definitions

### Global settings

Important global settings include:

```yaml
modularpacks:
  Enabled: true
  ResizeGUI: true
  AutoCloseOnTeleport: true
  AllowShulkerBoxes: false
  AllowBundles: false
  BackpackInsertBlacklist: []
```

`ResizeGUI` controls whether backpack pages dynamically resize or keep a fixed size.

Every backpack includes a navigation row for settings, sorting, and module slots,
even when `UpgradeSlots` is zero. `Rows` counts storage only: `Rows: 2` provides
18 storage slots plus the navigation row. Packs with more than five storage rows
use pages of up to 45 storage slots, preserving their full configured capacity.

`AutoCloseOnTeleport` closes open backpack menus when a player teleports.

`AllowShulkerBoxes` and `AllowBundles` control whether nested storage items can be placed inside backpacks.

`BackpackInsertBlacklist` blocks specific materials from being inserted into backpacks. The Magnet module also respects this list.

### Update checker

```yaml
UpdateChecker:
  Enabled: true
  ShowChangeLog: true
  CheckOnStartup: true
  PeriodicCheck: true
  CheckIntervalHours: 24
  NotifyPermission: modularpacks.update.notify
  ReleaseApiUrl: https://api.github.com/repos/tootertutor/ModularPacks/releases/latest
```

The update checker can notify console and permitted players when a new GitHub release is available. You can disable startup checks, periodic checks, changelog output, or the entire update checker.

### Debug click logging

```yaml
Debug:
  ClickLog: false
```

When enabled, ModularPacks writes backpack and module inventory click or drag events to:

```text
plugins/ModularPacks/click-events.log
```

This is mainly intended for debugging inventory sorting mods, unexpected GUI behavior, or possible dupe reports. Leave it disabled unless you are actively troubleshooting.

### GUI materials

```yaml
NavPageButtons: ARROW
NavBorderFiller: GRAY_STAINED_GLASS_PANE
ModuleSlotMaterial: WHITE_STAINED_GLASS_PANE
```

These settings control common menu items such as page buttons, border fillers, unlocked module slots, and locked module slots.

### Placeable backpacks

```yaml
Placeable: true
DropPlacedBackpacksOnExplosion: false
```

When placeable backpacks are enabled, players can place backpack items into the world. Placed backpacks use a rendered item display and can be interacted with like a physical object.

The render transform can be customized:

```yaml
PlacedBackpackRender:
  Offset:
    X: 0.5
    Y: 0.4
    Z: 0.4
  Rotation:
    X: 0.0
    Y: 0.0
    Z: 0.0
  Scale:
    X: 1.0
    Y: 1.0
    Z: 1.0
```

This is useful when using custom resource pack models that need to sit differently on the block.

### Shared backpacks

```yaml
SharedBackpacks:
  Enabled: true
  MaxSharedUsers: 5
```

Shared backpacks allow multiple players to work with the same backpack data. The maximum number of shared users can be adjusted to fit your server's balance and moderation needs.

## Backpack type configuration

Backpack types are defined under `BackpackTypes:` in `backpacks.yml`.

Example:

```yaml
BackpackTypes:
  Leather:
    Rows: 2
    UpgradeSlots: 1
    CraftingRecipe:
      Type: Crafting
      Pattern:
        - "SLS"
        - "WCW"
        - "LLL"
      Ingredients:
        L: LEATHER
        S: STRING
        C: CHEST
        W: WHITE_WOOL
      OutputMaterial: PLAYER_HEAD
    DisplayName: "&6Leather Backpack"
    Lore:
      - "&7Leather Backpack that can carry 2 rows of items."
      - "{backpackContents}"
      - "{backpackUuidLine}"
      - "{installedModules}"
    CustomModelData: 1001
    DefaultColor: 11100488
    SkullData: <base64 skull texture>
```

Each backpack type can define:

* `Rows` - number of storage rows
* `UpgradeSlots` - number of module slots
* `CraftingRecipe` - crafting or smithing recipe
* `OutputMaterial` - item material used for the backpack
* `DisplayName` - item display name
* `Lore` - item lore with placeholder support
* `CustomModelData` - optional resource pack model data
* `DefaultColor` - color value used by supported visuals
* `SkullData` - base64 skull texture data for player head backpacks

### Crafting recipes

A standard crafting recipe uses a 3x3 pattern:

```yaml
CraftingRecipe:
  Type: Crafting
  Pattern:
    - "III"
    - "IBI"
    - "III"
  Ingredients:
    I: IRON_INGOT
    B: BACKPACK:Leather
  OutputMaterial: PLAYER_HEAD
```

### Multiple recipe variants

Backpacks can have multiple alternative recipes:

```yaml
CraftingRecipe:
  - "1":
      Type: Crafting
      Pattern:
        - "III"
        - "IBI"
        - "III"
      Ingredients:
        I: IRON_INGOT
        B: BACKPACK:Leather
  - "2":
      Type: Crafting
      Pattern:
        - " I "
        - "IBI"
        - " I "
      Ingredients:
        I: IRON_INGOT
        B: BACKPACK:Copper
```

Players can preview a specific variant with:

```text
/backpack recipe backpack Iron 2
```

### Smithing recipes

Backpack types can also use smithing recipes:

```yaml
CraftingRecipe:
  Type: Smithing
  Template: NETHERITE_UPGRADE_SMITHING_TEMPLATE
  Base: BACKPACK:Diamond
  Addition: NETHERITE_INGOT
  OutputMaterial: PLAYER_HEAD
```

This is useful for tier upgrades like Diamond to Netherite.

## Recipe ingredient tokens

Recipe ingredients support vanilla Bukkit material names and ModularPacks-specific tokens.

| Token                 | Description                                        | Example            |
| --------------------- | -------------------------------------------------- | ------------------ |
| `BACKPACK:<TypeId>`   | Requires a ModularPacks backpack of the given type | `BACKPACK:Leather` |
| `<TYPE>_BACKPACK`     | Alternate backpack token format                    | `DIAMOND_BACKPACK` |
| `MODULE:<UpgradeId>`  | Requires a ModularPacks module item                | `MODULE:Magnet`    |
| `UPGRADE:<UpgradeId>` | Alternate module token format                      | `UPGRADE:Crafting` |

These tokens are what allow recipes to require actual ModularPacks items instead of only vanilla materials.

## Upgrade configuration

Modules are defined under `Upgrades:` in `modules.yml`.

Example:

```yaml
Upgrades:
  Magnet:
    Enabled: true
    Toggleable: true
    Range: 6.0
    MaxItemsPerTick: 32
    CraftingRecipe:
      Type: Crafting
      Pattern:
        - "L R"
        - "ITI"
        - "III"
      Ingredients:
        L: LAPIS_LAZULI
        R: REDSTONE
        I: IRON_INGOT
        T: MODULE:ModuleTemplate
      OutputMaterial: LEVER
      Glint: true
    DisplayName: "&aMagnet Upgrade"
    Lore:
      - "&7Automatically picks up items in a radius of the backpack."
      - "{toggleState}"
      - "{moduleActions}"
      - "{magnetRange}"
    CustomModelData: 2002
```

Common upgrade settings include:

* `Enabled` - whether the module is available
* `Toggleable` - whether the module can be toggled on or off
* `SecondaryAction` - whether the module has a right-click action
* `CraftingRecipe` - recipe used to craft the module
* `OutputMaterial` - material used for the module item
* `Glint` - whether the item should have an enchanted glint
* `DisplayName` - module item name
* `Lore` - module item lore
* `CustomModelData` - optional resource pack model data
* `MaxInstalled` - installation limit for the module type or its shared group
* `InstallGroup` - shared installation limit across module types
* `StackMultiplier` - multiplier applied to normal item stack capacity

Some modules also have their own settings, such as Magnet range, Feeding thresholds, Jukebox playback mode, Pump direction, or Exp Pump behavior.

## Language file

Language file:

```text
plugins/ModularPacks/lang/en_us.yml
```

The language file controls GUI titles, buttons and descriptions, Settings and color, dialogs, sharing prompts, module screens, sorting modes, resource-pack selection, admin menus, module action text, and placed backpack messages.
UI translations live under `gui` in `en_us.yml`.

For another language, copy `en_us.yml` to a file matching the player's Minecraft language code then translate its values.
Partial files are supported: missing keys fall back to `en_us.yml`.
Players see menus in their own client language, and existing English customizations remain the server default.
An empty lore list (`[]`) intentionally hides those description lines.

New English keys are added on startup or language reload without replacing existing values.
Run `/backpack reload lang` after editing language files, then close and reopen the menu.
Keep named tokens such as `{name}`, `{type}`, `{count}`, `{group}`, and `{color}` intact; translators can move them within a sentence.
Custom backpack names and configured type/resource-pack names keep their original values.

An optional translation starter and a smoke-test checklist are in [docs/localization.md](docs/localization.md).
This adds UI localization; command usage text and diagnostic messages outside these screens retain their existing configuration and behavior.

This lets you change the plugin's tone without editing the source. You can make the messages clean and minimal, more stylized, or match the theme of your server.

### Common placeholders

Backpack lore placeholders:

| Placeholder          | Description                                                          |
| -------------------- | -------------------------------------------------------------------- |
| `{backpackContents}` | Replaced with current used slots and item count, or an empty message |
| `{backpackUuidLine}` | Shows the UUID using the language file template |
| `{backpackId}`       | Shows the raw backpack UUID                                              |
| `{installedModules}` | Shows installed module information                                   |

Edit `backpackContentsEmpty`, `backpackContents`, `backpackUuidLine`, `backpackInstalledModulesHeader`, and `backpackInstalledModuleEntry` in `lang/en_us.yml` to translate backpack item lore. The module entry accepts `{moduleName}`, `{moduleId}`, `{rarityColor}`, `{tier}`, and `{tierSuffix}`. Set `RarityColor` in a module definition to color its star. Modules without an explicit rarity color use their built-in default where defined, then the leading color from `DisplayName`, with white as the fallback. Stack upgrades use their configured `Tier` and the `backpackInstalledStackModuleName` and `backpackInstalledModuleTier` language templates. Other entries use the module's display name with its formatting removed.

If your language file already defines `backpackInstalledModuleEntry`, use `"&7[{rarityColor}*&7] &f{moduleName}{tierSuffix}"` to adopt the star format.

For an existing `backpacks.yml`, replace the UUID lore line with `"{backpackUuidLine}"` to use the translated label. Existing custom lore can still use `{backpackId}` for the raw ID. Reload the plugin and refresh the backpack's item lore to see the changes.

Module lore placeholders:

| Placeholder                     | Description                             |
| ------------------------------- | --------------------------------------- |
| `{moduleActions}`               | Default module interaction instructions |
| `{moduleActionsFeeding}`        | Feeding module instructions             |
| `{moduleActionsFluidTank}`      | Fluid Tank module instructions          |
| `{moduleActionsExperienceTank}` | Experience Tank instructions            |
| `{moduleActionsJukebox}`        | Jukebox module instructions             |
| `{moduleActionsRestock}`        | Restock module instructions             |
| `{moduleActionsAutocrafting}`   | Autocrafting module instructions        |
| `{moduleActionsPump}`           | Pump module instructions                |
| `{moduleActionsExpPump}`        | Exp Pump module instructions            |
| `{toggleState}`                 | Enabled or disabled toggle text         |
| `{magnetRange}`                 | Magnet range display                    |
| `{feedingMode}`                 | Feeding mode display                    |
| `{containedFluid}`              | Tank or storage display text            |
| `{jukeboxMode}`                 | Jukebox play mode                       |
| `{restockThreshold}`            | Restock threshold value                 |
| `{pumpMode}`                    | Pump or Exp Pump mode                   |
| `{expPumpMending}`              | Exp Pump mending state                  |

### Placed backpack messages

Placed backpack messages are also configurable:

```yaml
backpack:
  placement:
    disabled: "&cPlaceable backpacks are disabled."
    no_permission: "&cYou don't have permission to place backpacks."
  open:
    no_permission: "&cYou don't have permission to open backpacks."
    locked: "&cThis backpack is currently open by {player}."
  pickup:
    no_permission: "&cYou don't have permission to pick up backpacks."
```

## Data storage

ModularPacks uses SQLite and stores data in:

```text
plugins/ModularPacks/backpacks.db
```

Important tables include:

| Table              | Purpose                                                                                                    |
| ------------------ | ---------------------------------------------------------------------------------------------------------- |
| `backpacks`        | Stores backpack metadata, owner data, contents, sharing data, sorting lock state, and custom backpack name |
| `backpack_modules` | Stores installed modules, module snapshots, and module state                                               |
| `voided_items`     | Stores recoverable item data for items removed by the Void module                                          |
| `player_resource_pack_preferences` | Stores each player's ordered selection of optional ModularPacks resource-pack layers       |

SQLite is used so server owners do not need to set up MySQL or another external database. The plugin also enables SQLite settings such as WAL mode and a busy timeout to make normal server usage smoother.

Backpack contents use a versioned logical-storage payload with amount-one item prototypes and separate counts. Legacy `ItemStack[]` payloads are read automatically and rewritten in the new format on their next save. Module snapshots and state continue to use their existing, separate `ItemStackCodec` format.

Internal gameplay, passive modules, sorting, and GUI persistence operate on `BackpackStorage`. The Bukkit inventory is only a vanilla-safe visual projection; clicks and drags are applied directly to logical storage and rendered items are never read back as canonical state. The legacy public `ItemStack[]` view remains as a deprecated compatibility adapter and fails explicitly when a virtual count cannot be represented.

## Resource pack support

Resource packs are optional and selected per player. The Backpack Settings menu opens a dedicated resource-pack screen where players can mix and match configured layers, stage changes, and apply the complete selection once.

ModularPacks checks configured assets from a GitHub release, downloads each ZIP server-side for validation and SHA-1 calculation, then gives players the release asset's public `browser_download_url`. The plugin does not expose an HTTP listener or require an additional public port. Pack priority controls stacking order, with higher-priority packs layered over lower-priority packs.

When a release asset changes, online players using its previous SHA-1 receive a notification. The plugin does not force a potentially disruptive mid-session reload: players can relog or use **Apply Selected Packs** in the GUI.

The plugin can still use normal Minecraft materials and player heads without any packs. For custom backpack or module visuals, assign `CustomModelData` values and provide matching models in one or more configured pack assets.

Useful config fields for visuals:

* `OutputMaterial`
* `CustomModelData`
* `DisplayName`
* `Lore`
* `DefaultColor`
* `SkullData`
* `PlacedBackpackRender`

This means the plugin can work as a server-side storage system first, then be visually upgraded later without changing the core backpack data.

</details>

<details>
<summary><strong id="developer-api">Developer API</strong></summary>

## API

ModularPacks includes a public API for other plugins.

External plugins can use the API to:

* Check whether ModularPacks is loaded
* Access the ModularPacks plugin instance
* Register custom modules
* Register custom module item definitions
* Look up modules by ID
* Look up modules by screen type
* Check a player's active module session
* Get the backpack UUID for an active module session

### Getting the API

```java
ModularPacksAPI api = ModularPacksAPI.getAPI();

if (api == null) {
    // ModularPacks is not loaded or not ready.
    return;
}
```

You can also use:

```java
ModularPacksAPI api = ModularPacksAPI.getInstance();
```

`getAPI()` performs a plugin manager check first, which is usually safer for soft-depend integrations.

### Registering a module implementation

```java
ModularPacksAPI api = ModularPacksAPI.getAPI();

if (api != null) {
    api.registerModule(new MyCustomModule());
}
```

This registers the module behavior only. If you want the module to be craftable or available through ModularPacks item creation, register it with an `UpgradeDef` as well.

### Registering a module with an item definition

```java
ModularPacksAPI api = ModularPacksAPI.getAPI();

if (api != null) {
    MyCustomModule module = new MyCustomModule();

    UpgradeDef def = new UpgradeDef(
        "MyModule",
        "&6My Custom Module",
        Material.DIAMOND,
        List.of("&7Custom functionality"),
        1001,
        false,
        true,
        true,
        false,
        ScreenType.GENERIC
    );

    api.registerModule(module, def);
}
```

The module ID and the `UpgradeDef` ID must match. ModularPacks validates this so modules do not register under one ID while their item definition uses another.

`UpgradeDef` has extended constructors for `maxInstalled`, `installGroup`, and `stackMultiplier`. The original constructor remains supported and defaults to one installed module of the exact type with a multiplier of one. A non-empty install group makes the maximum apply across all definitions in that group. Stack multipliers compose multiplicatively and apply to each item's normal maximum stack size, including items whose vanilla maximum is one.

External plugin definitions under `modularpacks-modules` can configure the same policy fields:

```yaml
modularpacks-modules:
  MyModule:
    Enabled: true
    MaxInstalled: 3
    InstallGroup: MySharedGroup
    StackMultiplier: 2
    OutputMaterial: DIAMOND
    DisplayName: "&6My Custom Module"
```

### Implementing `IModule`

Custom modules implement:

```java
public interface IModule {
    String getModuleId();
    ScreenType getScreenType();
    boolean hasSession(Player player);
    UUID getSessionBackpackId(Player player);
    void open(ModularPacksPlugin plugin, Player player, UUID backpackId, String backpackType, UUID moduleId);
    void handleClose(ModularPacksPlugin plugin, Player player, Inventory inventory);
    boolean isValidInventoryView(InventoryView view);
    String getDisplayName();
}
```

Modules can also override:

```java
default void updateResult(Inventory inventory) {}
default boolean isEnabled() { return true; }
```

This gives custom modules a consistent way to open screens, save state on close, validate inventory views, and expose display names.

### Plugin dependency setup

If your plugin requires ModularPacks, add it as a dependency in your `plugin.yml`:

```yaml
depend: [ModularPacks]
```

If your plugin can run without ModularPacks, use a soft dependency:

```yaml
softdepend: [ModularPacks]
```

Then check `ModularPacksAPI.getAPI()` before using the integration.

</details>

<details>
<summary><strong id="building-publishing-and-project-status">Building, publishing, and project status</strong></summary>

## Building from source

Clone the repository and build with Maven:

```bash
git clone https://github.com/TooterTutor/ModularPacks.git
cd ModularPacks
mvn -DskipTests package
```

The compiled jar will be created in:

```text
target/modularpacks-<version>.jar
```

## Project status

ModularPacks is actively being developed. Configuration keys, module behavior, and API details may evolve as the plugin matures. Server owners should back up `plugins/ModularPacks/backpacks.db` before major updates.

## Credits

Created by **TooterTutor**.

Inspired by the idea of modded, upgradeable backpacks, but built as a Paper plugin with its own storage model, admin tools, configuration system, and extension API.

</details>
