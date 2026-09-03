# MMD OreSpawn

[![Discord](https://img.shields.io/discord/303440391124942858?label=Discord&logo=discord)](https://discord.moddev.zone)
[![CurseForge downloads](https://cf.way2muchnoise.eu/full_245586_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/orespawn)
[![Minecraft](https://cf.way2muchnoise.eu/versions/245586.svg)](https://www.curseforge.com/minecraft/mc-mods/orespawn/files)
[![CI](https://github.com/MinecraftModDevelopmentMods/OreSpawn/actions/workflows/ci.yml/badge.svg?branch=master-1.20.6-neo)](https://github.com/MinecraftModDevelopmentMods/OreSpawn/actions/workflows/ci.yml?query=branch%3Amaster-1.20.6-neo)

OreSpawn 4 is a provider-driven world-generation engine for Minecraft 1.20.6.
It gives mods and modpacks one place to configure ores, deposit shapes, optional
rock strata and geomes, provider-owned underground fluid deposits, biome
palettes and world materials, flat bedrock, and bounded ore retrogen.

Its OS3 compatibility layer preserves ranged legacy block budgets, exclusive
legacy height ceilings, and the historical "all dimensions except Nether and
End" policy used by mods such as Base Metals.

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
biomes and replace surfaces, aquifer fluids, snow, and ice through OreSpawn's
declarative provider contract.

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
| `config/orespawn-guide/README.md` | Guide exported automatically on first load |
| `config/orespawn-upgrade-report.txt` | Human summary produced when legacy OreSpawn rules are imported |
| `<world>/serverconfig/orespawn-upgrade-report.txt` | Human summary produced when a generated legacy Mineralogy world is pinned to its saved settings |

Profile edits affect newly generated chunks. Ore and flat-bedrock retrogen are
separate opt-in features; OreSpawn never retro-generates rock strata.
Stable Layers honours exact biome-ID geome influences on dynamic biome
registries and spreads close geome transitions across layers rather than
changing an entire vertical rock column at one boundary.

When an already-generated world has saved Mineralogy 1.10, 1.12, or 5.x mod
metadata but no OreSpawn world profile, OreSpawn reads the matching published
configuration contract and records the exact engine, numeric settings, rock
order, and white/blacklists in the new world profile. Saved-world identity
wins over stale files in the installation. A fresh world is never reclassified
merely because an old `mineralogy.cfg` or `mineralogy-common.toml` remains in
the instance.

To move a configured single-player world to a dedicated server, copy the
world's `serverconfig/orespawn-worldgen.json` with the world and install the
same provider mods on the server.

## Mod And Modpack Integration

Mods can provide declarative rules in either of these ways:

- package `data/<modid>/orespawn/provider.json` in the mod jar;
- call `OreSpawnApi.enqueue(WorldgenProvider)` during `InterModEnqueueEvent`.

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

This branch builds target-qualified version `4.0.13.120062`: the OreSpawn 4.0.13
feature set for Minecraft 1.20.6 and NeoForge.

OreSpawn 4.0.13.120062 uses NeoGradle 7.1.38 and Gradle 9.2.1. Use exact
Temurin 21.0.7+6 from the repository root:

```powershell
.\gradlew.bat clean check build javadoc verifyReleaseArtifacts writeReleaseChecksums --no-daemon --max-workers=2
.\gradlew.bat eclipse verifyEclipseProductionClasspath --no-daemon --max-workers=2
```

`build` runs the standard `check` lifecycle. In addition to the JUnit suite,
that lifecycle packages a test-only provider mod and verifies exposed,
underwater, filler, and ceiling surfaces in open and ceiling normal-noise
dimensions. It also proves later vegetation, structures, and block entities
survive, verifies identifier-weighted geology in a dynamic custom biome, then
reopens and checks the exact saved world. The fixture is not included in
OreSpawn's published jars.

Import or refresh the project with Eclipse Buildship. NeoGradle supplies the
Eclipse model and run configurations through the `eclipse` task; this branch
does not use ForgeGradle's `genEclipseRuns` task.

The audited public outputs are `OreSpawn-4.0.13.120062.jar`, its sources jar,
and its Javadoc jar. CI additionally proves a cache-cold NeoGradle bootstrap
and a same-cache offline rerun.

Machine-specific `AGENTS.md` and `agent-notes/` files are intentionally ignored.
Public developer and AI integration guidance lives in `docs/` and is included
in the built jar.

OreSpawn is licensed under LGPL-2.1.
