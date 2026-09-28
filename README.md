# Zoomies 🐕💨

**Load-speed tricks for big Minecraft 1.12.2 modpacks** (Forge or CleanroomMC).

Big packs spend minutes doing the same slow thing over and over. Zoomies finds those spots with real
measurements and answers them faster. **Same results, less waiting.** Every trick has its own switch.

> Early and growing. Each trick is checked against the original code (see *Safety*), but back up your world.
> Made with [Claude](https://claude.com/claude-code) (Anthropic), working with [@crunkazcanbe](https://github.com/crunkazcanbe).

## Download
**Latest:** [releases/latest](https://github.com/crunkazcanbe/zoomies/releases/latest). Needs MixinBooter (or CleanroomMC, which has it built in).

## The tricks

### 🔎 Ore-dictionary index
Forge checks *"does this item fit this ore slot?"* by walking **every item** registered under the ore name.
Recipe-heavy mods (Tinkers' smeltery, Ender IO, …) ask that millions of times while loading. Zoomies answers
from an index (item → variants) instead, and rebuilds it whenever a mod adds more ores.

### ⚗️ Ender IO alloy recipes
Ender IO splits each alloy recipe into every combination of its ingredients and skipped duplicates by scanning
every combination made so far. Zoomies files them in buckets by which items they hold, and Ender IO's own
comparison runs only inside the matching bucket.
**Measured in a ~650-mod pack: 173 s → 51 s.**

## ⚙️ Config — `config/zoomies.cfg`
Written on first start with an explanation over every line. Restart after changing.

| section | what |
|---|---|
| `general` | master switch, log each trick's timing |
| `threads` | how many threads parallel tricks may use (`auto` = all hardware threads minus a reserve), max, priority |
| `safety` | spot-check tricks against the original code |
| one per trick | on/off |

A trick switched off is never patched in at all.

## 🛡️ Safety
Turn on `safety.verify` (or create an empty file named `zoomies-verify` in the game folder) and Zoomies also runs
the original slow code for 1 in every `safety.verifyEvery` answers and logs any disagreement as
`[Zoomies] MISMATCH`. Our test pack: zero mismatches.

## 🧰 Measuring your own pack
`tools/` has the two scripts used to find these spots:
- `mcloadtime` — reads Forge's own stopwatch (`logs/debug.log`) and ranks the slowest stages and mods.
- `mcprofile` — during a launch, snapshots the loading threads and reports the hottest code per stage.

## Building
Put the compile-time jars listed in [libs/README.md](libs/README.md) in `libs/`, then
`JAVA_HOME=<a JDK 8> ./gradlew build`. The jar lands in `build/libs/`.

## License
MIT.
