# ModularPacks 2.0

**Release Date:** 2026-01-26


# ModularPacks 2.5.8

**Release Date:** 2026-07-01

## Changes since 2.5.7

### Features & Improvements
- Added Database insertion/extraction methods (7eb8417)

### Bug Fixes
- Fixed session lock race (b13b2a4)

### All Commits
- Fixed session lock race (b13b2a4)
- Bump version (e368a1e)
- Added Database insertion/extraction methods (7eb8417)
---


# ModularPacks 2.5.7

**Release Date:** 2026-06-12

## Changes since 2.5.6

### Bug Fixes
- Fixed experience pump wasting experience on items with only 1 damage (7898c78)
- Fixed item_model to 'air' and stripped backpack data from visual slot. (c777859)

### All Commits
- Bump version (3831af8)
- Fixed experience pump wasting experience on items with only 1 damage (7898c78)
- Fixed Switched item_model to 'air' and stripped backpack data from visual slot. (c777859)
---


# ModularPacks 2.5.6

**Release Date:** 2026-06-07

## Changes since 2.5.5

### Features & Improvements
- Added recursive checks and blocking for blacklisted items within shulker boxes and bundles (ef8fd7d)
- Added support for 26.1.2 (a2c18de)
- Added criteria for Model Rendering in config (f342bd9)

### Refactoring & Code Quality
- Modularized configs into three files. (7a5010d)
- Renamed `CraftingTemplate` to `ModuleTemplate` to alleviate confusion (6874b74)
- Removed `LockedUpgradeSlotMaterial` since it was unused (246cda3)

### All Commits
- Added recursive checks and blocking for blacklisted items within shulker boxes and bundles (ef8fd7d)
- Renamed `CraftingTemplate` to `ModuleTemplate` to alleviate confusion (6874b74)
- Removed `LockedUpgradeSlotMaterial` since it was unused (246cda3)
- Minor update to Readme (4538ac0)
- Modularized configs into three files. (7a5010d)
- Updated ModelManager (41d6762)
- Updated configs (5ff1a35)
- Added support for 26.1.2 (a2c18de)
- Added criteria for Model Rendering in config (f342bd9)
---


# ModularPacks 2.5.5

**Release Date:** 2026-05-17

## Changes since 2.5.4

### Features & Improvements
- Added built-in backpack rendering on players (ae32fe3)

### Bug Fixes
- Fixed issue where backpacks could break when placed from offhand (66ec180)

### All Commits
- Bump version (27e4c7d)
- Added built-in backpack rendering on players (ae32fe3)
- Fixed issue where backpacks could break when placed from offhand (66ec180)
- Reverted CuriosPaper integration (ca1f209)
---


# ModularPacks 2.5.4

**Release Date:** 2026-05-14

## Changes since 2.5.3

### Features & Improvements
- Added Curios Compatiblity (628dcb4)
- Added distribution management (e964032)
- Added Table of Contents (d3a6b6e)

### All Commits
- Bump version and added artifactSet shading (674305c)
- Added Curios Compatiblity (628dcb4)
- Added distribution management (e964032)
- Added Table of Contents (d3a6b6e)
- Updated README (f1d5b72)
---


# ModularPacks 2.5.3

**Release Date:** 2026-04-28

## Changes since 2.5.2

### Features & Improvements


### Bug Fixes
- Fixed RecipeManager to discover all plugin recipes for player on join (5d77c11)

### Refactoring & Code Quality


### All Commits
- Patched release workflow (1823600)
- Bump version minor change (44b9472)
- Fixed RecipeManager to discover all plugin recipes for player on join (5d77c11)
---


# ModularPacks 2.5.2

**Release Date:** 2026-04-27

## Changes since 2.5.1

### Features & Improvements
- Added new `Keep at Level` setting for exp pump (a027830)

### Bug Fixes
- Fixed typo in config (12a7660)

### Refactoring & Code Quality


### All Commits
- Bump version (6f88300)
- Added new `Keep at Level` setting for exp pump (a027830)
- Fixed typo in config (12a7660)
---


# ModularPacks 2.5.1

**Release Date:** 2026-04-26

## Changes since 2.5.0

### Features & Improvements
- Added Pump and Experience Pump modules (e5accb7)
- Changed Tank module to now be separate modules. One for EXP, and one for Fluids (4174ef1)

### Bug Fixes
- Fixed FluidTank and ExpTank to allow both being active in the backpack at the same time (86fd596)
- Fixed plugin.yml versioning (439c74c)
- Fixed backpack placement disregarding claims (6141226)
- Fixed LangManager to read other lang files (0f0991d)

### Refactoring & Code Quality
- Refactored some comments to use Javadoc standards (b3c167c)

### All Commits
- Bump version (1048900)
- Added Pump and Experience Pump modules (e5accb7)
- Fixed FluidTank and ExpTank to allow both being active in the backpack at the same time (86fd596)
- Changed Tank module to now be separate modules. One for EXP, and one for Fluids (4174ef1)
- Fixed plugin.yml versioning (439c74c)
- Fixed backpack placement disregarding claims (6141226)
- Refactored some comments to use Javadoc standards (b3c167c)
- Fixed LangManager to read other lang files (0f0991d)
---


# ModularPacks 2.5.0

**Release Date:** 2026-04-25

## Changes since 2.4.9

### Features & Improvements


### Bug Fixes
- Fixed ConfigManager to be more hardy with plugins (858592a)

### Refactoring & Code Quality


### All Commits
- Bump version (c54bcc1)
- Fixed ConfigManager to be more hardy with plugins (858592a)
---


# ModularPacks 2.4.9

**Release Date:** 2026-04-25

## Changes since 2.4.8

### Features & Improvements
- Changed sorting button for list menu to be visually similar to the backpack sort button (2af9c5f)
- Added new `menu` option under `backpack list` (2acc898)

### Bug Fixes
- Fixed backpack type sorting (03ffb8a)

### Refactoring & Code Quality


### All Commits
- Bump version (b109ba7)
- One less file to edit when bumping version (1c5db58)
- Fixed backpack type sorting (03ffb8a)
- Changed sorting button for list menu to be visually similar to the backpack sort button (2af9c5f)
- Added new `menu` option under `backpack list` (2acc898)
---


# ModularPacks 2.4.8

**Release Date:** 2026-04-25

## Changes since 2.4.7

### Features & Improvements
- Added update checker (70e1ff8)
- Added update checker config values (d8f52a8)

### Bug Fixes
- Fixed typo in config (260972c)

### Refactoring & Code Quality


### All Commits
- Added update checker (70e1ff8)
- Bump version (5f4ed06)
- Added update checker config values (d8f52a8)
- Fixed typo in config (260972c)
---


# ModularPacks 2.4.7

**Release Date:** 2026-04-25

## Changes since 2.4.6

### Features & Improvements
- Added autocrafting implementation (fea0f8e)
- Added autocrafting module configs (a853cd2)

### Bug Fixes
- Fixed placed backpacks to properly update active modules through their Custom Model Data (5f9c225)

### Refactoring & Code Quality


### All Commits
- Added autocrafting implementation (fea0f8e)
- Added autocrafting module configs (a853cd2)
- Replaced `mp` with `backpack` in usage (334ac01)
- Fixed placed backpacks to properly update active modules through their Custom Model Data (5f9c225)
- Bump version (8777233)
---


# ModularPacks 2.4.6

**Release Date:** 2026-04-16

## Changes since 2.4.5

### Features & Improvements


### Bug Fixes
- Fixed a potential issue involving toggling normally untoggleable modules (345adc7)

### Refactoring & Code Quality


### All Commits
- Bump version (523921c)
- Fixed a potential issue involving toggling normally untoggleable modules (345adc7)
---


# ModularPacks 2.4.5

**Release Date:** 2026-04-16

## Changes since 2.4.4

### Features & Improvements
- Added new backpack CMD rendering features (533ffb4)
- Added small CMD Flags setter (a878e42)
- Added hopefully dynamic backpack module models for the held backpack model (b4d1204)
- Improved code quality (34844dd)
- Added PDC (221179e)

### Bug Fixes
- Fixed module slots so they are now correctly indexed (e9c8261)
- Fixed colors being also stored in strings CMD (ccd6813)

### Refactoring & Code Quality


### All Commits
- Bump version (22cbe85)
- Added new backpack CMD rendering features (533ffb4)
- Added small CMD Flags setter (a878e42)
- Fixed module slots so they are now correctly indexed (e9c8261)
- Added hopefully dynamic backpack module models for the held backpack model (b4d1204)
- Fixed colors being also stored in strings CMD (ccd6813)
- Improved code quality (34844dd)
- Added PDC (221179e)
---


# ModularPacks 2.4.4

**Release Date:** 2026-03-22

## Changes since 2.4.3

### Features & Improvements


### Bug Fixes
- Fixed placed backpacks losing their backpack data when placed in fluid blocks (3ce8cad)

### Refactoring & Code Quality


### All Commits
- Bump version (ace2542)
- Fixed placed backpacks losing their backpack data when placed in fluid blocks (3ce8cad)
---


# ModularPacks 2.4.3

**Release Date:** 2026-03-22

## Changes since 2.4.2

### Features & Improvements


### Bug Fixes
- Fixed backpack color picking not selecting the correct group (d366ddb)
- Fixed backpacks from losing data when being placed in certain scenarios (efd7d66)
- Fixed default offset values for placed backpack model (a767846)

### Refactoring & Code Quality


### All Commits
- Bump version (d67334d)
- Fixed backpack color picking not selecting the correct group (d366ddb)
- Fixed backpacks from losing data when being placed in certain scenarios (efd7d66)
- Fixed default offset values for placed backpack model (a767846)
---


# ModularPacks 2.4.2

**Release Date:** 2026-03-21

## Changes since 2.4.1

### Features & Improvements
- Added placed backpack models (e281a36)

### Bug Fixes
- Fixed placed backpacks having no name (f5cc5f7)

### Refactoring & Code Quality


### All Commits
- Bump version (e97a2e5)
- Fixed placed backpacks having no name (f5cc5f7)
- Added placed backpack models (e281a36)
---


# ModularPacks 2.4.1

**Release Date:** 2026-03-21

## Changes since 2.4.0

### Features & Improvements


### Bug Fixes
- Fixed custom backpack colors reverting when being placed (64e9d9e)

### Refactoring & Code Quality


### All Commits
- Bump Version (1779ec3)
- Fixed custom backpack colors reverting when being placed (64e9d9e)
- bump version (0f1dd36)
---


# ModularPacks 2.4.0

**Release Date:** 2026-03-20

## Changes since 2.3.2

### Features & Improvements
- Added password view to host button (2c12619)
- Added more support for Custom Model Data (1cee67b)
- Added Settings button and logic (c77a559)
- Added BundleMeta check (4830a1f)
- Added new Generic screen type for API calls (d6fc68e)

### Bug Fixes
- Fixed backpack coloring desync (b4f7775)
- Fixing small inconsistencies (b200ec2)
- Fixed bug with Feeding Engine (f975577)
- Fix: Recipe command now checks configs for `modularpacks-modules` (01584a3)

### Refactoring & Code Quality
- Refactor done (1fd083a)
- Cleaned up ConfigManager (41a18d7)
- Refactored how modules are structured within the plugin (808e306)

### All Commits
- Fixed backpack coloring desync (b4f7775)
- Cleared colors revert to original model colors (ebd2439)
- Added password view to host button (2c12619)
- Added more support for Custom Model Data (1cee67b)
- Added Settings button and logic (c77a559)
- Backpack now handles custom name (d2ac15c)
- Internal package changes (fd00a36)
- Fixing small inconsistencies (b200ec2)
- Fixed bug with Feeding Engine (f975577)
- Refactor done (1fd083a)
- Attempt to clean up ScreenRouter (7d9b5a6)
- Cleaned up ConfigManager (41a18d7)
- Refactored how modules are structured within the plugin (808e306)
- Bump version (9224e88)
- Small change to SlotLayout (98864f7)
- Fix: Recipe command now checks configs for `modularpacks-modules` (01584a3)
- Added BundleMeta check (4830a1f)
- Custom modules provided by external plugins are now config-driven (969cd31)
- bump version (dec748b)
- Added new Generic screen type for API calls (d6fc68e)
---


# ModularPacks 2.4.0

**Release Date:** 2026-03-20

## Changes since 2.3.2

### Features & Improvements
- Added password view to host button (2c12619)
- Added more support for Custom Model Data (1cee67b)
- Added Settings button and logic (c77a559)
- Added BundleMeta check (4830a1f)
- Added new Generic screen type for API calls (d6fc68e)

### Bug Fixes
- Fixed backpack coloring desync (b4f7775)
- Fixing small inconsistencies (b200ec2)
- Fixed bug with Feeding Engine (f975577)
- Fix: Recipe command now checks configs for `modularpacks-modules` (01584a3)

### Refactoring & Code Quality
- Refactor done (1fd083a)
- Cleaned up ConfigManager (41a18d7)
- Refactored how modules are structured within the plugin (808e306)

### All Commits
- Fixed backpack coloring desync (b4f7775)
- Cleared colors revert to original model colors (ebd2439)
- Added password view to host button (2c12619)
- Added more support for Custom Model Data (1cee67b)
- Added Settings button and logic (c77a559)
- Backpack now handles custom name (d2ac15c)
- Internal package changes (fd00a36)
- Fixing small inconsistencies (b200ec2)
- Fixed bug with Feeding Engine (f975577)
- Refactor done (1fd083a)
- Attempt to clean up ScreenRouter (7d9b5a6)
- Cleaned up ConfigManager (41a18d7)
- Refactored how modules are structured within the plugin (808e306)
- Bump version (9224e88)
- Small change to SlotLayout (98864f7)
- Fix: Recipe command now checks configs for `modularpacks-modules` (01584a3)
- Added BundleMeta check (4830a1f)
- Custom modules provided by external plugins are now config-driven (969cd31)
- bump version (dec748b)
- Added new Generic screen type for API calls (d6fc68e)
---


# ModularPacks 2.3.2

**Release Date:** 2026-02-16

## Changes since 2.3.1

### Features & Improvements


### Bug Fixes
- Fix: repaired potential dupe exploit. (f6076b9)
- Fixed some recipe registration (894d740)

### Refactoring & Code Quality


### All Commits
- bump version (345022d)
- Fix: repaired potential dupe exploit. (f6076b9)
- Fixed some recipe registration (894d740)
---


# ModularPacks 2.3.2

**Release Date:** 2026-02-16

## Changes since 2.3.1

### Features & Improvements


### Bug Fixes
- Fix: repaired potential dupe exploit. (f6076b9)
- Fixed some recipe registration (894d740)

### Refactoring & Code Quality


### All Commits
- bump version (345022d)
- Fix: repaired potential dupe exploit. (f6076b9)
- Fixed some recipe registration (894d740)
---


# ModularPacks 2.3.1

**Release Date:** 2026-02-10

## Changes since 2.3

### Features & Improvements


### Bug Fixes


### Refactoring & Code Quality


### All Commits
- small version bump (85b4bd5)
- sort lock now persists (d0e12c6)
---


# ModularPacks 2.3

**Release Date:** 2026-02-09

## Changes since 2.2

### Features & Improvements
- Added toggle lock to sorting button

### Bug Fixes
- Fixed sorting button logic (26d37c4)

### Refactoring & Code Quality


### All Commits
- bump version (256e79c)
- Fixed sorting button logic and added toggle lock to it (26d37c4)
---


# ModularPacks 2.2

**Release Date:** 2026-02-07

## Changes since 2.1

### Features & Improvements


### Bug Fixes
- Fixed recipe registration (891d17a)

### Refactoring & Code Quality


### All Commits
- Fixed recipe registration (891d17a)
---


# ModularPacks 2.1

**Release Date:** 2026-01-28

## Changes since 2.0

### Features & Improvements
- Added backpack sharing mode config (f5faaca)

### Bug Fixes


### Refactoring & Code Quality


### All Commits
- bump version (f357af8)
- Added backpack sharing mode config (f5faaca)
---


# ModularPacks 2.1

**Release Date:** 2026-01-28

## Changes since 2.0

### Features & Improvements
- Added backpack sharing mode config (f5faaca)

### Bug Fixes


### Refactoring & Code Quality


### All Commits
- Added backpack sharing mode config (f5faaca)
---

## Changes since 1.9

### Features & Improvements


### Bug Fixes


### Refactoring & Code Quality


### All Commits
- Backpacks are now placeable! (a0da70f)