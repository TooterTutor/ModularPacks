# ModularPacks

![Paper](https://img.shields.io/badge/Paper-1.21.10%2B-blue?style=flat-square)
![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square)
![API](https://img.shields.io/badge/API-available-brightgreen?style=flat-square)
![Storage](https://img.shields.io/badge/storage-SQLite-lightgrey?style=flat-square)
![Status](https://img.shields.io/badge/status-active_development-purple?style=flat-square)
![License](https://img.shields.io/badge/license-MIT-lightgrey?style=flat-square)

**ModularPacks** is a Paper plugin that adds physical, upgradeable backpacks to Minecraft servers.

Instead of treating backpacks like another command-based player vault, ModularPacks makes the backpack an actual item. Players carry it, open it, upgrade it, place it, share it, and build around it like a real part of the world. The goal is to bring a modded-style backpack experience to Paper servers while keeping the server playable for vanilla clients.

ModularPacks was heavily inspired by the feel of modded backpack systems, especially the idea of installable upgrades, but it is not meant to be a quick clone with a few settings changed. This plugin was built around Paper's server-side systems, PersistentDataContainer item identity, SQLite-backed storage, configurable recipes, admin recovery tools, and an API for custom module support.

## Features

* Physical backpack items with persistent identity
* Configurable backpack tiers, sizes, recipes, names, lore, colors, skull textures, and custom model data
* Installable upgrade modules
* Placeable backpacks with configurable world rendering
* Shared backpacks with configurable shared-user limits
* SQLite-backed storage for backpack contents and installed module state
* Admin commands for giving, opening, listing, recovering, and changing backpacks
* Void recovery logging for accidentally voided items
* Optional update checker with in-game and console notifications
* Optional resource pack support through `CustomModelData`
* Public API for registering custom modules from other plugins

## Requirements

* Paper `1.21.10+`
* Java `21`
* Maven, if building from source

ModularPacks is built against the Paper API and is intended for Paper-based servers. Other server platforms may work if they maintain Paper API compatibility, but Paper is the supported target.

## Installation

1. Download the plugin jar.
2. Place it in your server's `plugins/` folder.
3. Start the server once.
4. ModularPacks will generate its data folder and default files:

   * `plugins/ModularPacks/config.yml`
   * `plugins/ModularPacks/lang/en_us.yml`
   * `plugins/ModularPacks/backpacks.db`, created when backpack data is first used
5. Edit `config.yml` and `lang/en_us.yml` as needed.
6. Restart the server, or run:

```text
/backpack reload
```

## What makes ModularPacks different?

A lot of backpack plugins solve storage by adding a command, a virtual inventory, or a simple item that opens a menu. ModularPacks takes a different approach.

Each backpack has its own UUID stored on the item. The item itself is only the key. The contents, installed modules, module state, share data, and recovery information are stored safely in SQLite. This means a backpack can be renamed, upgraded, placed, opened by admins, recovered, or inspected without relying on fragile item lore alone.

The plugin is also designed around modules rather than one hardcoded backpack menu. A backpack can have upgrade slots, and modules can provide passive effects, custom screens, filters, pumps, tanks, crafting utilities, or other behavior. That structure is what makes the plugin expandable instead of just being a fixed-size storage menu.

In short: the inspiration is familiar, but the implementation is built specifically for Paper servers, server owners, admins, and developers who want something configurable and expandable.

## How backpacks work

Every ModularPacks backpack stores persistent metadata on the item:

* `backpack_id` - the UUID identity of the backpack
* `backpack_type` - the configured type or tier, such as `Leather`, `Iron`, or `Netherite`

The actual contents are stored in the plugin database, not directly in the item lore. When a player opens a backpack, ModularPacks loads the contents and installed modules for that `backpack_id`. When the menu closes, the plugin saves the updated contents and module state back to SQLite.

This design helps prevent stale item data and makes admin recovery possible. It also allows linked or shared backpack behavior without trying to store an entire inventory inside item text.

## Default backpack tiers

The default configuration includes these backpack types:

| Type        | Rows | Upgrade Slots |
| ----------- | ---- | ------------- |
| `Leather`   | 2    | 1             |
| `Copper`    | 3    | 1             |
| `Iron`      | 4    | 2             |
| `Gold`      | 6    | 3             |
| `Diamond`   | 8    | 4             |
| `Netherite` | 10   | 5             |

These are only defaults. You can edit existing types or add new ones through `config.yml`.

## Default modules

Modules are configured under `Upgrades:` in `config.yml`. Each module can have its own recipe, display item, lore, custom model data, toggle behavior, and module-specific settings.

Default modules include:

### Utility modules

* `Everlasting` - makes a backpack indestructible
* `Magnet` - pulls nearby items into the backpack
* `FluidTank` - stores bucket-based fluids
* `ExperienceTank` - stores and dispenses player experience
* `Feeding` - automatically feeds the player using backpack contents
* `Void` - voids filtered items while logging recoverable item data
* `Restock` - restocks the player's inventory from backpack contents
* `Pump` - moves buckets between the player and a Fluid Tank module
* `ExpPump` - moves XP between the player and an Experience Tank module

### Workstation modules

* `Crafting` - portable crafting table
* `Smelting` - portable furnace
* `Blasting` - portable blast furnace
* `Smoking` - portable smoker
* `Stonecutter` - portable stonecutter
* `Anvil` - portable anvil
* `Smithing` - portable smithing table
* `Jukebox` - portable jukebox with playlist behavior
* `Autocrafting` - configurable automatic crafting behavior

## Basic usage

Players can obtain backpacks through recipes or admin commands.

To open a backpack, right-click the backpack item.

Inside the backpack menu, players can interact with storage slots and installed modules. Module actions are shown in the module lore, so server owners can customize the instructions in the language file.

Common module interactions include:

* Left-click to open or configure a module
* Right-click to run a secondary action or cycle a mode
* Shift + left-click to toggle a toggleable module
* Shift + right-click to remove a module

The exact behavior depends on the module.

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

## Configuration

Main configuration file:

```text
plugins/ModularPacks/config.yml
```

The config is split into two main sections:

* `modularpacks:` for global plugin behavior
* `BackpackTypes:` and `Upgrades:` for backpack and module definitions

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
UnlockedUpgradeSlotMaterial: WHITE_STAINED_GLASS_PANE
LockedUpgradeSlotMaterial: IRON_BARS
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

Backpack types are defined under `BackpackTypes:`.

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
      - "{backpackId}"
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

Modules are defined under `Upgrades:`.

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
        T: MODULE:CraftingTemplate
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

Some modules also have their own settings, such as Magnet range, Feeding thresholds, Jukebox playback mode, Pump direction, or Exp Pump behavior.

## Language file

Language file:

```text
plugins/ModularPacks/lang/en_us.yml
```

The language file controls user-facing module action text, toggle labels, mode labels, placeholder expansions, and placed backpack messages.

This lets you change the plugin's tone without editing the source. You can make the messages clean and minimal, more stylized, or match the theme of your server.

### Common placeholders

Backpack lore placeholders:

| Placeholder          | Description                                                          |
| -------------------- | -------------------------------------------------------------------- |
| `{backpackContents}` | Replaced with current used slots and item count, or an empty message |
| `{backpackId}`       | Shows the backpack UUID                                              |
| `{installedModules}` | Shows installed module information                                   |

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

SQLite is used so server owners do not need to set up MySQL or another external database. The plugin also enables SQLite settings such as WAL mode and a busy timeout to make normal server usage smoother.

## Resource pack support

A resource pack is optional.

By default, ModularPacks can use normal Minecraft materials and player heads. If you want custom backpack or module visuals, assign `CustomModelData` values in `config.yml` and provide matching models in your resource pack.

Useful config fields for visuals:

* `OutputMaterial`
* `CustomModelData`
* `DisplayName`
* `Lore`
* `DefaultColor`
* `SkullData`
* `PlacedBackpackRender`

This means the plugin can work as a server-side storage system first, then be visually upgraded later without changing the core backpack data.

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

Inspired by the idea of modded, upgradeable backpacks, but built as a Paper plugin with its own storage model, admin tools, configuration system, and extension API
