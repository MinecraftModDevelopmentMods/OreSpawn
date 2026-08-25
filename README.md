[![Discord](https://img.shields.io/badge/Discord-MMD-green.svg?style=flat&logo=Discord)](https://discord.moddev.zone)
[![CurseForge downloads](https://cf.way2muchnoise.eu/full_mmd-orespawn_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/mmd-orespawn)
[![Supported Minecraft versions](https://cf.way2muchnoise.eu/versions/Minecraft_mmd-orespawn_all.svg)](https://www.curseforge.com/minecraft/mc-mods/mmd-orespawn)
[![Build, test, and audit](https://github.com/MinecraftModDevelopmentMods/OreSpawn/actions/workflows/ci.yml/badge.svg?branch=master-1.12.2)](https://github.com/MinecraftModDevelopmentMods/OreSpawn/actions/workflows/ci.yml?query=branch%3Amaster-1.12.2)

# MMD OreSpawn

OreSpawn 4 is a provider-driven world-generation engine for Minecraft 1.12.2.
It gives mods and modpacks one place to configure ores, deposit shapes, optional
rock strata and geomes, provider-owned underground fluid deposits, biome
palettes and world materials, flat bedrock, and bounded ore retrogen.

This branch builds target-qualified version `4.0.8.112021`: the OreSpawn 4.0.8
feature set for Minecraft 1.12.2 and Forge. See the
[versioning policy](docs/VERSIONS.md) for the encoding and release convention.

Its deprecated OS3 compatibility layer imports OreSpawn 3 configuration and
keeps existing OreSpawn 3 consumer jars working while translating their rules
into the OreSpawn 4 scheduler. It preserves ranged legacy block budgets,
metadata block states, exclusive legacy height ceilings, and the historical
"all dimensions except Nether and End" policy used by mods such as Base
Metals. OreSpawn never schedules both the original OS3 generator and its OS4
translation.

This is not the unrelated mod that adds mobs and dimensions under the same
name.

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
| `config/orespawn-migration/migration-report.txt` | Deterministic OS3 import report and required actions |
| `config/orespawn-guide/README.md` | Guide exported automatically on first load |

Profile edits affect newly generated chunks. Ore and flat-bedrock retrogen are
separate opt-in features; OreSpawn never retro-generates rock strata.

When an existing world records Mineralogy 3 or earlier and has no OreSpawn 4
world profile, OreSpawn preserves that world's Cyano geology contract before
new chunks generate. It distinguishes carried Mineralogy 1.10 configuration
from native Mineralogy 1.12 configuration, including the different ordered
rock families, `REALISTIC_COAL_LAYERS`, and `PLACE_MINERALOGY_ROCK`. A hybrid
file created while upgrading is interpreted using the Mineralogy version saved
with the world. Fresh worlds still use the installed provider's recommended
engine; selecting Sky for an upgraded world is an explicit choice which may
create an old/new terrain seam.

After an upgrade, read `config/orespawn-upgrade-report.txt` for translated OS3
rules and `<world>/serverconfig/orespawn-upgrade-report.txt` for the Mineralogy
handoff. The reports list the sources, selected lineage, preserved values and
anything needing review without rewriting existing chunks.

To move a configured single-player world to a dedicated server, copy the
world's `serverconfig/orespawn-worldgen.json` with the world and install the
same provider mods on the server.

## Mod And Modpack Integration

Mods can provide declarative rules in either of these ways:

- package `assets/<modid>/orespawn/provider.json` in the mod jar;
- call `OreSpawnApi.enqueue(WorldgenProvider)` during normal Forge 1.12
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
- [Versioning and release policy](docs/VERSIONS.md)
- [Schemas and examples](docs/README.md)

The full documentation bundle is packaged under `META-INF/orespawn/docs/` and
exported to `config/orespawn-guide/` without overwriting existing files.

## Building

Run Gradle with Java 17 from the repository root. Install the exact Temurin
`8.0.502+7` toolchain used to compile production code and test fixtures for
Minecraft 1.12.2; the build rejects a different Java 8 toolchain:

```powershell
.\gradlew.bat clean check build javadoc verifyReleaseArtifacts writeReleaseChecksums --no-daemon
.\gradlew.bat genEclipseRuns verifyEclipseProductionClasspath --no-daemon
```

`build` runs the standard `check` lifecycle. In addition to the JUnit suite,
that lifecycle packages a test-only provider mod and verifies 2,304 exposed
surface columns per built-in normal-noise End and Nether dimension, including
underwater, immediate filler, and ceiling-underside behavior. It also proves
later vegetation, structures, and block entities
survive, validates provider-rock vanilla springs and an external ore-pattern
registration, then reopens and checks the exact saved world. The fixture is
not included in OreSpawn's published jars.

Import or refresh the project with Eclipse Buildship, then run
`genEclipseRuns` and `verifyEclipseProductionClasspath`. This branch uses
ForgeGradle 7.0.34, the Gradle 9.6.1 wrapper, Forge 14.23.5.2859, the
`stable_39` MCP mappings, and pack format 3. Ordinary Eclipse launches exclude
tests and fixtures. Published jars are deterministic, SRG-reobfuscated for the
Forge 1.12 runtime, audited for their access transformer and contents, and
accompanied by SHA-256 checksums.

Machine-specific `AGENTS.md` and `agent-notes/` files are intentionally ignored.
Public developer and AI integration guidance lives in `docs/` and is included
in the built jar.

OreSpawn is licensed under LGPL-2.1.
