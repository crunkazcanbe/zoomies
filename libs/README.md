# Compile-time jars (not included)

Zoomies compiles against these, but doesn't ship them. Drop them in this folder before `./gradlew build`:

| file | from |
|---|---|
| `mixinbooter-api.jar` | [MixinBooter](https://github.com/CleanroomMC/MixinBooter) (any 8.x+ build) |
| `sponge-mixin.jar` | [SpongePowered Mixin](https://github.com/SpongePowered/Mixin) 0.8.x (the one MixinBooter bundles) |
| `endercore.jar` | [EnderCore](https://www.curseforge.com/minecraft/mc-mods/endercore) 1.12.2 |
| `enderio.jar` | [Ender IO](https://www.curseforge.com/minecraft/mc-mods/ender-io) 1.12.2 (CEu or original) |
| `renderlib.jar` | [RenderLib](https://www.curseforge.com/minecraft/mc-mods/renderlib) 1.12.2 (optional at runtime) |
| `cnpc.jar` | [Custom NPCs Unofficial](https://www.curseforge.com/minecraft/mc-mods/custom-npcs-unofficial) 1.12.2 (optional at runtime) |
| `bwm.jar` | [Better With Mods / BetterWithEverything](https://www.curseforge.com/minecraft/mc-mods/better-with-mods) 1.12.2 (optional at runtime) |
| `celeritas.jar` | [Celeritas](https://github.com/taumc/celeritas) 1.12.2 (optional: adds the Zoomies page to its video settings) |

None of them are needed at runtime except MixinBooter (or CleanroomMC, which includes it). Ender IO is optional: without it that one trick simply doesn't apply.
- abyssalcraft.jar — AbyssalCraft 1.12.2-2.0.0-BETA-7 (MixinAbyssalPatrons target)
