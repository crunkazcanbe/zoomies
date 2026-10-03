# Zoomies

Load-speed and frame-rate tricks for big Minecraft 1.12.2 modpacks. Same results, less waiting.

## About

Zoomies is a performance mod for Minecraft 1.12.2 (Cleanroom or Forge). Big modpacks spend minutes repeating the same slow work: walking long lists, unzipping every mod jar, rebuilding things that never change. Zoomies finds those spots by measuring real launches and gives the same answer by a faster route. Most tricks target one specific mod and do nothing if that mod isn't installed.

It was built for the Pride modpack, a 1.12.2 pack of about 750 mods, and the numbers below were measured there. It works in any 1.12.2 pack, big or small.

Every trick has its own switch in `config/zoomies.cfg`. A trick that is switched off is never patched into the game at all. The few tricks that change results (for example, fewer far-away particles) are visual only and never touch the world.

There is also an optional check mode (`safety.verify`). It runs the original slow code next to some of the fast versions and logs any difference.

## What it speeds up

Each entry says what it speeds up, how, the config key that controls it and its default, and the time saved where the source records a measurement. Numbers come from the Pride pack, so your pack will see different numbers.

### Startup & loading

| # | Speeds up | How | Config key (default) | Measured |
|---|---|---|---|---|
| 1 | **Forge ore-dictionary recipe checks.** Forge checks "does this item fit this ore slot?" by walking every item under that ore name. Tinkers' Construct, Ender IO and other recipe-heavy mods ask millions of times during loading. | Answers from an index (item → allowed metadata, or "any"). The index is rebuilt whenever the ore list changes size. Gives the same result as Forge's loop. | `oreIndex.enabled` (true) | n/a |
| 2 | **Ender IO alloy recipes.** Ender IO splits each alloy recipe into every combination of its ingredients and checks for duplicates by scanning a plain list of every combination made so far. | Files combinations in buckets keyed by the items they hold, so Ender IO's own equality check only runs inside one small bucket. | `enderio.alloyDedupe` (true) | 173 s → 51 s of loading |
| 3 | **Ender IO recipe lookup tree.** Building the tree checks "already filed here?" with `list.contains`, so it slows down with the square of the recipe count. | Keeps an identity set next to each list. Recipe classes with their own `equals` still use the original walk. If a list was changed some other way, the set is rebuilt first. | `enderio.lookupSets` (true) | ~44 s (left after the alloy fix) |
| 4 | **Extra Utilities 2 crusher recipes.** On every ingot, ore or dust registration, XU2 copied every ore name in the game and re-checked every dust, compiling two regexes per dust. | A registration can only complete the one metal it names, so only that metal's dust/ore/ingot trio is checked. The recipe is added at the same moment the original would have found it. | `extrautils2.oreRegisterIndex` (true) | 40 s of load |
| 5 | **Better With Mods ore lists.** `BWOreDictionary.listContains` walks the ore list for masses of items during loading. | Keeps each item's first position in the list (per damage value and for "any damage") and applies the same NBT rule, so it lands on the same entry the walk would. Rebuilt when the list size changes. | `betterwithmods.oreIndex` (true) | ~17 s |
| 6 | **Thaumcraft aspect generation.** For every item it gives aspects to, Thaumcraft walked all crafting-recipe keys (tens of thousands) to find the few that make that item. | Hands it only the recipes whose output item matches, from an index built once (rebuilt if the recipe count changes). Thaumcraft's own checks still run, so results are identical. | `thaumcraft.recipeIndex` (true) | 11% of the whole main-thread load |
| 7 | **Texture stitching.** Minecraft places each sprite by trying every free slot in turn, which slows down badly with tens of thousands of sprites. | Packs sprites in rows ("shelves"), tallest first, into a power-of-two sheet in one pass. If they wouldn't fit within the graphics card's limit, Minecraft's own packer runs instead. | `textures.fastStitch` (true) | ~15% of a big pack's load |
| 8 | **Tails Legacy part lookup.** Tails walks every file of every mod jar, once per resource namespace, on every resource reload. Some jars hold 30,000 files. | Files each jar's entries once by `assets/<namespace>/<first folder>/` and only walks the folder it asked for. Tails' own checks still run. | `tails.zipIndex` (true) | 23 s of load |
| 9 | **FVTM addon-pack search.** FVTM looked for addon packs by decompressing every jar in the mods folder start to finish, just to compare file names. | Reads each zip's table of contents (free) and only decompresses the few matching files. Same results, same order. | `fvtm.zipIndex` (true) | ~28 s |
| 10 | **GVCLib 3D model loading** (the gun mods). Every line of every OBJ model is checked against slow regex patterns. | Uses hand-written checks that give identical answers, with no regex engine. | `models.fastObjLines` (true) | ~20 s |
| 11 | **Immersive Vehicles resource lookups.** Every resource question for its packs went through `getResourceAsStream`, which walks all mod jars one at a time. Most answers are "not here", and every miss walked every jar. | Reads every jar's table of contents once (compact 64-bit name hashes) and answers misses instantly. Repeat misses are also remembered. If the classpath has a folder or an unreadable entry, the index turns itself off and only the repeat-miss cache stays. | `textures.resourceMissCache` (true) | n/a (seen in 3 of 3 samples of a 5.7-minute reload) |
| 12 | **Custom NPCs texture scan.** At startup Custom NPCs unzips every texture in every mod, just for the NPC editor's texture picker. | While the game is loading, skips only that scan and runs it in the background once the main menu is up. If you open the picker before then, it scans as usual. | `customnpcs.deferTextureScan` (true) | ~70 s |
| 13 | **Loading-screen frame limiter.** RenderLib's frame limiter made every loading-screen refresh wait up to 33 ms. | Skips the wait during mod loading only. Menus and gameplay keep their normal frame limit. | `loadingScreen.noFrameLimit` (true) | ~20% of the whole launch |
| 14 | **Extra resource reloads during startup.** Immersive Vehicles and UnlimitedChiselWorks force a full resource reload just to add their own resource pack, even though the game reloads everything again at the end of loading. | Adds the new pack directly to the resource manager without running every reload listener. If that fails, the full reload runs as before. **Off by default.** | `reloads.packOnly` (**false**) | 20 s (Immersive Vehicles 15 s, UnlimitedChiselWorks 5 s) |
| 15 | **AbyssalCraft supporter-list download.** AbyssalCraft downloads its supporter list at every startup with no time limit. | Uses a saved copy from `config/zoomies-cache/` and refreshes it in the background for next time. The first fetch uses short time limits (5 s connect, 10 s read). | `network.abyssalcraftPatronsCache` (true) | 64 s stuck in one launch |
| 16 | **Hanging network requests.** Update checks and downloads that set no time limit can freeze loading for a minute on a slow site. | Sets Java's default connect and read timeouts for every request that doesn't set its own. A download that keeps receiving data is never cut off. `0` = Java's default (wait forever). | `network.connectTimeoutSeconds` (5), `network.readTimeoutSeconds` (10) | n/a |
| 17 | **Java class cache between launches.** | When the game runs on IBM Semeru (OpenJ9) with `-Xshareclasses:name=pride,cacheDir=<folder>`, classes and compiled code are cached on disk and reused by later launches. Zoomies checks the cache is really active and tells you once if it isn't. On normal Java nothing happens and nothing breaks. **Off by default** because it needs that special Java. | `java.openj9Cache` (**false**) | n/a |

### World creation

| # | Speeds up | How | Config key (default) | Measured |
|---|---|---|---|---|
| 18 | **Loading every dimension at world start.** A world start loads every registered dimension (122 in the Pride pack). | Loads only the dimensions in `worldgen.startDimensions` at first. The rest load the first time anything needs them (travel, or a mod asking), which Forge already supports. The overworld always loads. | `worldgen.lazyDimensions` (true), `worldgen.startDimensions` (`0,-1,1`) | n/a |
| 19 | **Animania advancements.** Animania rebuilt all ~3,860 advancements every time any dimension loaded (126 times in the Pride pack). | Builds them once per advancement manager, which every dimension shares, so the result is identical. | `worldgen.animaniaAdvancementsOnce` (true) | ~113 s of a ~3-minute world creation |
| 20 | **Treasure2 structure templates.** Every template went through the whole data fixer (every mod's walkers) on every world load, although all 45 templates are already in the current 1.12.2 format. | Templates saved as current 1.12.2 (DataVersion 1343 or newer) skip the fixer. Older ones still go through it. | `treasure2.skipFixer` (true) | Most of a new world's 13-minute setup |
| 21 | **Railcraft ore generation.** Railcraft asks "is generation enabled for this ore?" for every block it considers replacing, and each call runs a regex. | Remembers the answer per ore name. It never changes after the config loads. | `railcraft.worldGenCache` (true) | n/a (showed up in the fresh-world server profile) |
| 22 | **Stale startup caches.** Some mods save a slow startup calculation to disk (ProjectE's EMC values, RealmCoin prices). The risk is a stale file after the mod list changes. | Fingerprints the mod list (mod id, version and jar size). When it changes, Zoomies deletes those saved files so they are rebuilt once. Otherwise they are reused. | `caches.invalidateOnModChange` (true) | ProjectE EMC cache: ~40 s of every world start |

### In-game FPS (client)

| # | Speeds up | How | Config key (default) | Measured |
|---|---|---|---|---|
| 23 | **ItemPhysic dropped items.** ItemPhysic checks its burn/swim/fuel lists for every dropped item every tick. Each check walks the whole list and calls every mod's fuel handler. | For plain stacks (no NBT), remembers the answer per item and metadata for each list. The memory is dropped if a list's size changes. Applies on the client and the server. | `droppedItems.itemPhysicListCache` (true) | ~30% of the render thread with a few hundred items on the ground |
| 24 | **Celeritas Dynamic Lights on dropped items.** Every dropped item's brightness was worked out every client tick. | Works it out every 4th tick (0.2 s) and reuses it in between. The difference isn't visible. | `droppedItems.dynLightThrottle` (true) | ~15% of the render thread |
| 25 | **Better Weather rain height.** "Is it raining here?" scans the block column from the top, for every entity, rain sound and raindrop, every tick. | Remembers each column's rain height for 10 ticks (0.5 s), with separate memory per world and per thread (client and server). | `weather.rainHeightMemo` (true) | ~7% of the render thread |
| 26 | **Particle level of detail.** | Draws every particle within 16 blocks, 1 in 2 up to 32 blocks, 1 in 4 up to 64 blocks, and none beyond. Only drawing changes; nothing in the world does. Distances are configurable. Switches live. | `particles.lod` (true), `particles.fullDistance` (16), `particles.halfDistance` (32), `particles.quarterDistance` (64) | n/a |
| 27 | **Tiny far-away mobs.** | Doesn't draw a mob that would be smaller than `entities.minPixels` on screen. Never skips players, named or glowing mobs, bosses, or whatever you're riding. | `entities.screenSizeCull` (true), `entities.minPixels` (3) | n/a |
| 28 | **Tiny far-away machines** (animated blocks / TESRs). | Same screen-size check for machine renderers. Never within 16 blocks, and never for renderers with an infinite render box such as beacon beams. | `machines.screenSizeCull` (true) | n/a |
| 29 | **No-op graphics calls.** Old renderers call `translate(0,0,0)`, `rotate(0)` and `scale(1,1,1)` thousands of times a frame. Each call goes to the graphics driver but changes nothing. | Skips exactly those calls. Anything that really moves, turns or scales still goes through. Switches live. | `graphics.skipNoopTransforms` (true) | n/a |
| — | **BuildCraft facades tab cap.** BuildCraft lists a facade for every block (82,637 in the Pride pack); opening the tab froze the game and EMI indexed them all. The tab shows only the first `creative.bcFacadeLimit` (default 64; -1 = all) — every facade is still craftable. | `creative.bcFacadeLimit` = 64 |

### Server TPS

| # | Speeds up | How | Config key (default) | Measured |
|---|---|---|---|---|
| 30 | **Autosave stutter.** Minecraft saves the whole world every 45 s, which is a known stutter in big packs. | Makes the interval configurable (minimum 1 s). | `performance.autosaveSeconds` (45) | n/a |
| 31 | **Capsule reward structures.** When loot chests in new chunks pick a reward structure, Capsule ran it through the whole data fixer the first time each one was used. With its large reward pool this happened again and again. | Structures already saved as current 1.12.2 skip the fixer. Older ones still go through it. | `capsule.skipFixer` (true) | 38% of the server thread while new chunks fill their chests |

ItemPhysic (#23) and Better Weather (#25) also save server time, because both run on the server thread too.

### Other

| # | What | Config key (default) |
|---|---|---|
| 32 | **Texture size cap.** A pack's main texture sheet can sit right at the graphics card's 16384×16384 limit, so giant vehicle textures could cause "Unable to fit" crashes on some launches but not others. Minecraft can already scale down any single texture above a size limit, but it passes no limit. Zoomies passes one. Normal 16–64 px textures are untouched. `0` = no limit. | `textures.maxTileSize` (128) |
| 33 | **Loading-time estimate.** Times every loading stage and saves the timeline to `config/zoomies-timeline.txt`. On the next launch, loading screens that ask (for example PrideCanvas) can show "about 3m 20s left". The estimate is matched per stage. | `eta.enabled` (true) |
| 34 | **Optimization Discovery Mode.** `/zoomies discover [seconds]` (5–300, default 30) watches the game with low-cost counters, then opens a report. The report shows which Zoomies tricks would help the current scene and has a switch for each. | n/a (counters are idle until you run it) |
| 35 | **Render cost report.** `/zoomies profile` times every machine and entity renderer. `/zoomies report` lists them worst first, with the owning mod, ms per frame, % of frame, calls per frame and µs per call. `/zoomies stop` ends profiling. | n/a |
| 36 | **Safety check mode.** Runs the original code for 1 in every `safety.verifyEvery` answers and logs any difference as `[Zoomies] MISMATCH`. Covers the ore-dictionary index, the Ender IO alloy fix and the Better With Mods index. You can also turn it on by creating an empty file named `zoomies-verify` in the game folder. | `safety.verify` (false), `safety.verifyEvery` (64) |

**Total: 31 speed tricks (#1–31), plus 5 other features.**

## Video options

Zoomies also adds OptiFine-style options that Celeritas and Celeritas Extra don't have. These are visual options, not speed tricks. They are listed here because they share the same config file. They show up as a "Zoomies" page in Celeritas's video settings, or as a "Zoomies..." button in the vanilla Video Settings screen if Celeritas isn't installed.

| Key (default) | What it does |
|---|---|
| `graphics.anisotropic` (1) | Anisotropic filtering: 1 = off, or 2/4/8/16. Needs mipmaps on. |
| `graphics.fxaa` (false) | FXAA anti-aliasing, using Minecraft's own FXAA shader. |
| `graphics.smoothMipmaps` (false) | Blends between texture detail levels far away (less shimmer). |
| `graphics.dynamicFov` (true) | Off = sprinting, speed effects and bows no longer stretch the view. |
| `graphics.smoothZoom` (true) | Smoother, slower camera while zooming. Hold the zoom key (`` ` `` by default) and scroll to adjust the zoom. |
| `textures.customSky` (true) | OptiFine Custom Sky layers from resource packs (`optifine/sky/world0/`). |
| `textures.randomMobs` (true) | OptiFine Random Entities: numbered mob texture variants from resource packs. |
| `textures.betterGrass` (false) | OptiFine Better Grass (Fast): grass, snowy grass and mycelium sides use the top texture. |
| `details.entityShadows` (true) | Round shadows under mobs and items. |
| `details.heldItemTooltips` (true) | Item name above the hotbar when you switch items. |
| `details.capes` (true) | Shows your own cape. |
| `details.clearWater` (false) | See much further underwater. |
| `details.weather` (true) | Off = never see rain or snow. Visual only: it still rains on the server. |
| `details.timeLock` (0) | Locks the sky to an hour of the day (1–24, 0 = off). Visual only. |
| `details.lagometer` (false) | Frame-time graph and FPS in the bottom-left corner. |

## Requirements

- Minecraft **1.12.2**
- **Cleanroom**, or **Forge 14.23.5** (built against 14.23.5.2860) with **MixinBooter** 8.x or newer. Cleanroom includes MixinBooter.
- Optional: **Celeritas**, which adds the Zoomies page to its video settings.

Every mod-specific trick only applies when its target mod is installed. No other mod is required.

## Install

1. Install MixinBooter if you're on Forge (Cleanroom already has it).
2. Drop `Zoomies-1.12.2-<version>.jar` into your `mods/` folder.
3. Start the game. `config/zoomies.cfg` is created on first launch.

## Config

All settings live in **`config/zoomies.cfg`**. The file is written on first start, with an explanation and the default above every line. When an update adds new options, the file is rewritten to include them and your values are kept. Delete a line to get its default back. Restart the game after changing a switch that patches code. The particle, culling, no-op, sky, mob-texture and video options switch live from the in-game settings screen.

| Section | Keys |
|---|---|
| general | `general.enabled` (true, master switch: false = Zoomies does nothing), `general.logTimings` (true, writes timings to the log, search for `[Zoomies]`) |
| threads | `threads.count` (auto), `threads.reserve` (2), `threads.priority` (normal), `threads.max` (64). Reserved for tricks that use worker threads; no current trick uses them. |
| safety | `safety.verify` (false), `safety.verifyEvery` (64) |
| one key per trick | see the tables above |

## Building

```
./gradlew build
```

The jar ends up in `build/libs/`. The project targets Java 8 (build with a JDK 8). Zoomies compiles against several mod jars that aren't included. Put them in `libs/` first. The list is in `libs/README.md` and in `build.gradle`.

## License

MIT License, © 2026 crunkazcanbe.

## Credits

Made by crunkazcanbe, with Claude.


## Compile-only jars

The build compiles against these jars in `libs/` (other authors' mods / APIs). They are not included in this repo — get them from their official pages and drop them in `libs/` before building:

- `abyssalcraft.jar`
- `animania.jar`
- `betterweather.jar`
- `bwm.jar`
- `capsule.jar`
- `celeritas.jar`
- `celeritasdynlights.jar`
- `cnpc.jar`
- `creativecore.jar`
- `endercore.jar`
- `enderio.jar`
- `fcl.jar`
- `fvtm.jar`
- `gottschcore.jar`
- `gvclib.jar`
- `mixinbooter-api.jar`
- `mts.jar`
- `railcraft.jar`
- `renderlib.jar`
- `sponge-mixin.jar`
- `thaumcraft.jar`
- `treasure2.jar`
- `ucw.jar`
- `xu2.jar`
