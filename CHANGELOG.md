# Changelog

## 0.3.0 - 2026-10-07

The biggest Zoomies update yet: world joins, new worlds and Distant Horizons got the attention this time, plus a built-in profiler that tells you which mods are slowing your pack down. Measured on an ~800-mod pack, which now reaches the main menu in about 12-13 minutes on a desktop test machine.

### Added
- **Pride Profiler.** Times every mod while the game loads and while a world is created, then shows a report on the main menu (and a notice after world creation) listing the slowest mods. Buttons in the pause menu and Options. `profiler.enabled`, `profiler.popup`.
- **Instant join.** Skips "Preparing spawn area": you are in the world as soon as it opens and the land around you loads like any other chunks (what Ksyxis does, but compatible with big modded loaders). `worldgen.instantJoin`.
- **Start in a village.** New worlds spawn at the nearest village; `/zoomies village` takes you to one in an existing world. Works even when OTG or Dregora already chose a spawn. `worldgen.spawnInVillage`.
- **Distant Horizons, much faster far terrain:**
  - Terrain-only LODs for land nobody has visited, instead of fully generating each chunk plus 8 neighbours. `dh.surfaceLods`, `dh.fullDetailRadius`.
  - LODs built on DH's own worker threads, each with a private biome and terrain generator, instead of queueing on the server thread. Chunks already in memory are handed over as they are; saved chunks are read from the region file on the worker. Supports vanilla-style overworlds and OTG (Dregora). `dh.offThreadLods`.
  - A fixed server time slice per tick, and a bigger slice while every player is idle. `dh.serverBudgetMs`, `dh.idleSeconds`, `dh.idleBudgetMs`.
  - Player first: no new LOD chunks while a player still has chunks waiting to load or is moving fast. `dh.playerFirst`, `dh.playerFirstSpeed`, `dh.playerFirstHoldMs`.
- **Auto-Tune.** Detects CPU threads, RAM and the GPU vendor and VRAM (Intel/AMD CPUs, NVIDIA/AMD/Intel GPUs) and sets Celeritas and Distant Horizons thread counts per hardware tier (DH capped at 8 threads so the server tick isn't starved). Never overrides a value the player changed.
- **Faster world joins:**
  - ProjectE EMC mapping runs in the background after the world opens (was 39 s of the join). `speed.projecteBackground`.
  - Ender IO alloy recipes are re-filed in the background (~9.5 s). `join.enderioAlloyBackground`.
  - Forge's ~185,000-line registry trace dump is skipped (~3 s). `join.skipRegistryDump`.
  - Lootr's block cache is remembered until the mod list changes (~11 s server freeze). `join.lootrBlockCache`.
  - Structurize reads each blueprint once (~1.3 s), CD4017BE Lib's forced GC is skipped (~1.7 s), Realistic Physics reads tag items directly (~1.3 s per dimension), Compact Machines no longer loads its dimension at every login (~1.7 s). `join.structurizeSingleRead`, `join.cd4017NoGc`, `join.realisticPhysicsTags`, `join.compactMachinesNoDimLoad`.
- **Faster new worlds:**
  - Just Stargate no longer loads all ~160 dimensions at world start to hand out gate addresses (~3 min); unloaded dimensions get theirs when they first load. `speed.jsgLazyDimensions`.
  - Advent of Ascension adds its game rules to loaded worlds only (~30 s). `speed.aoaLazyGameRules`.
  - Capsule remembers reward structures that don't load and caches every reward template.
  - Depths Update remembers its deepslate check per block.
- **Boot caches** saved to `zoomies-cache/boot/` and reused until a mod jar changes: Immersive Vehicles' model compatibility scan (~15 s) and VintageFix's texture list (~12 s). `bootCaches.mtsModelCompat`, `bootCaches.vintageFixTextureList`.
- **Warm Classes (experimental, off by default).** Saves each class as the coremods and access transformers left it and reuses it next launch; Mixin still runs live on top. Includes a verify mode and smart cache keys (only coremod configs and `-D` JVM properties count; generated files are ignored). `warmClasses.*`.
- **Runaway entity guard.** Stops any non-player entity told to move absurdly far in one tick (a flung item could freeze the server thread for good). `entities.runawayGuard`.
- Immersive Railroading: no more ~80 forced garbage collections while loading train models (~40 s per launch). `immersiverailroading.noForcedGc`.
- Gates of the Apocalypse keeps its fortress but no longer moves the world spawn onto its roof. `worldgen.fortressKeepsSpawn`.
- Pride monorail and maglev beams (IR Extras) may span between pillars.
- Zoomies screens follow the pack-wide PrideCanvas menu theme.

### Fixed
- **Out-of-memory with ~800 mods (Ender IO):** lookup sets use weak identity keys and only cover lists of 64+ entries; the Alloy Smelter lookup is flattened to one level instead of every pair and triple of ingredients (synthetic 2x/3x recipes made it cubic: gigabytes of heap). Same matches. `enderio.flatAlloyLookup`.
- Villager Backport searched for the nearest village on every new chunk (OTG worlds ran at ~1 TPS); it now decides from villages already planned. `fix.villagerBackportFastVillageCheck`.
- Distant Horizons worker threads could corrupt the shared biome cache (crash); each worker now answers biome lookups from its own provider.
- OTG ran out of array caches under DH workers (NPE); a spare cache is used when its four are taken.
- Immersive Railroading crash when placing a train (`SwaySimulator` map accessed from two threads).
- Alex's Mobs rainbow layer crash and an AE2 Web Integration crash when opening a second world.
- Warm Classes: loader-constraint violation on Cleanroom (hooks its TransformerHolder through method handles).

### Changed
- The tile-entity auto-register fix moved to PridePatches, where crash fixes live.

