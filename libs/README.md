# Compile-time jars (not included)

Zoomies compiles against these, but doesn't ship them. Drop them in this folder before `./gradlew build`:

| file | from |
|---|---|
| `mixinbooter-api.jar` | [MixinBooter](https://github.com/CleanroomMC/MixinBooter) (any 8.x+ build) |
| `sponge-mixin.jar` | [SpongePowered Mixin](https://github.com/SpongePowered/Mixin) 0.8.x (the one MixinBooter bundles) |
| `endercore.jar` | [EnderCore](https://www.curseforge.com/minecraft/mc-mods/endercore) 1.12.2 |
| `enderio.jar` | [Ender IO](https://www.curseforge.com/minecraft/mc-mods/ender-io) 1.12.2 (CEu or original) |

None of them are needed at runtime except MixinBooter (or CleanroomMC, which includes it). Ender IO is optional: without it that one trick simply doesn't apply.
