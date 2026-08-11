# MMD OreSpawn

OreSpawn 4 is a provider-driven world-generation engine for Minecraft 1.10.2.
It gives mods and modpacks one place to configure ores, deposit shapes, optional
rock strata and geomes, provider-owned underground fluid deposits, biome
palettes and world materials, flat bedrock, and bounded ore retrogen.

Its deprecated compatibility layer imports OreSpawn 1 and OreSpawn 3
configuration and keeps existing legacy consumer jars working while translating
their rules into the OreSpawn 4 scheduler. It preserves ranged legacy block budgets,
metadata block states, exclusive legacy height ceilings, and the historical
"all dimensions except Nether and End" policy used by mods such as Base
Metals. OreSpawn never schedules both an original legacy generator and its OS4
translation.

This is not the unrelated mod that adds mobs and dimensions under the same
name.

Minecraft 1.10 loads legacy language resources using `ll_CC.lang` names (for
example, `en_US.lang`). OreSpawn intentionally uses that target-native casing;
renaming these files to the lowercase convention from newer releases prevents
the translations from loading.

## What Happens When It Is Installed?

OreSpawn is deliberately passive on its own. It does not replace stone, remove
vanilla ores, or change the Nether merely because the jar is installed. A
provider mod or a modpack profile must opt features in.

Mineralogy 6 is the first full provider. It supplies its rocks, ores, crude-oil
deposit, geomes, biome influences, and recommended settings to OreSpawn. An
ore-only provider such as Base Metals can supply ores and host tags without
enabling rock layers or biome replacement. A total-conversion provider can add
biomes and replace surfaces, aquifer fluids, snow, and ice without depending on
TerraBlender.

## Players And Server Owners

When a provider exposes world settings, use **OreSpawn...** on the Create World
screen. **Recommended Defaults** restores the settings supplied by the
installed mods and pack. The in-game **Help & Guide** explains the controls.

Important files:

| Location | Purpose |
|---|---|
| `config/orespawn-worldgen.json` | Defaults for newly created worlds |
| `<world>/serverconfig/orespawn-worldgen.json` | Complete settings snapshot for one world |
| `config/<modid>-orespawn.json` | Optional modpack override for one provider |
| `config/orespawn-migration/migration-report.txt` | Deterministic OS1/OS3 import report and required actions |
| `config/orespawn-guide/README.md` | Guide exported automatically on first load |

Profile edits affect newly generated chunks. Ore and flat-bedrock retrogen are
separate opt-in features; OreSpawn never retro-generates rock strata.

To move a configured single-player world to a dedicated server, copy the
world's `serverconfig/orespawn-worldgen.json` with the world and install the
same provider mods on the server.

## Mod And Modpack Integration

Mods can provide declarative rules in either of these ways:

- package `assets/<modid>/orespawn/provider.json` in the mod jar;
- call `OreSpawnApi.enqueue(WorldgenProvider)` during normal Forge 1.10
  initialization, before post-initialization freezes provider discovery.

Modpacks can override a provider with `config/<modid>-orespawn.json`. A present
override is authoritative and fails closed when invalid, so a broken pack file
cannot silently disable another mod's native ore generation.

Only `zone.moddev.mc.orespawn.api` is supported Java API. API major version `1`
is also recorded in the jar manifest as `OreSpawn-API-Version`.

Start with:

- [Player guide](docs/PLAYER_GUIDE.md)
- [Developer guide](docs/DEVELOPER_GUIDE.md)
- [Configuration reference](docs/CONFIGURATION.md)
- [Provider JSON guide](docs/PROVIDERS.md)
- [Java API guide](docs/API.md)
- [Biome and world-material guide](docs/BIOMES.md)
- [Schemas and examples](docs/README.md)

The full documentation bundle is packaged under `META-INF/orespawn/docs/` and
exported to `config/orespawn-guide/` without overwriting existing files.

## Building

Use Java 8 from the repository root (the local validation JDK is 1.8.0_221):

```powershell
.\gradlew.bat clean build javadoc --no-daemon
.\gradlew.bat setupDecompWorkspace --no-daemon
.\gradlew.bat eclipse --no-daemon
```

`build` runs the standard `check` lifecycle. In addition to the JUnit suite,
that lifecycle packages a test-only provider mod and verifies 2,304 exposed
surface columns per built-in normal-noise End and Nether dimension, including
underwater, immediate filler, and ceiling-underside behavior. It also proves
later vegetation, structures, and block entities
survive, validates provider-rock vanilla springs and an external ore-pattern
registration, then reopens and checks the exact saved world. The fixture is
not included in OreSpawn's published jars.

Run both `setupDecompWorkspace` and `eclipse` after importing or refreshing this
ForgeGradle 2.2 project in Eclipse. This branch uses the Gradle 4.9 wrapper,
Forge 12.18.3.2511, the `stable_29` MCP mappings, and pack format 2. Published
jars are SRG-reobfuscated for the Forge 1.10 runtime.

The publication contains five artifacts: the reobfuscated runtime jar, a
compiled `api` classifier with API 1 plus the supported OS1/OS3 facades, a
deobfuscated `deobf` development jar, sources, and Javadocs.

Machine-specific `AGENTS.md` and `agent-notes/` files are intentionally ignored.
Public developer and AI integration guidance lives in `docs/` and is included
in the built jar.

OreSpawn is licensed under LGPL-2.1.
