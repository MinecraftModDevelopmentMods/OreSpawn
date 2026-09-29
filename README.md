[![Discord](https://img.shields.io/badge/Discord-MMD-green.svg?style=flat&logo=Discord)](https://discord.moddev.zone)
[![CurseForge downloads](https://cf.way2muchnoise.eu/full_mmd-orespawn_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/mmd-orespawn)
[![Supported Minecraft versions](https://cf.way2muchnoise.eu/versions/Minecraft_mmd-orespawn_all.svg)](https://www.curseforge.com/minecraft/mc-mods/mmd-orespawn)
[![Build, test, and audit](https://github.com/MinecraftModDevelopmentMods/OreSpawn/actions/workflows/ci.yml/badge.svg?branch=master-1.10.2)](https://github.com/MinecraftModDevelopmentMods/OreSpawn/actions/workflows/ci.yml?query=branch%3Amaster-1.10.2)

# MMD OreSpawn

OreSpawn 4 is a provider-driven world-generation engine for Minecraft 1.10.2.
It gives mods and modpacks one place to configure ores, deposit shapes, optional
rock strata and geomes, provider-owned underground fluid deposits, biome
palettes and world materials, flat bedrock, and bounded ore retrogen.

This branch builds target-qualified version `4.1.0.110021`: the OreSpawn 4.1.0
feature set for Minecraft 1.10.2 and Forge. It adds stable generation identity,
non-loading geology sampling, per-resource background control, existing-world
provider merge policy, an Ore Sources arbiter for equivalent provider ores and
a biome directory with exact new-terrain replacement overrides, plus a client
world-settings extension point for add-ons such as Realistic Deposits
while retaining API major 1. See the
[versioning policy](docs/VERSIONS.md) for the encoding and release convention.

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
installed mods and pack. **Mods** opens a directory of every loaded mod that
OreSpawn recognizes through a current provider, legacy compatibility path, or
client configuration hook. It shows each mod's version, integration lineage,
provider revision and status; its cog opens an add-on-owned configuration
screen when one is available. The in-game **Help & Guide** explains the other
controls.

On the ORES tab, **Ore Sources...** opens one material-group editor. Compact,
independently scrolling lists keep relevant material groups on the left and the
selected group's deduplicated ore blocks on the right. Harmless one-alias,
one-output entries are hidden by default and available through **Show All**.
Red groups need attention, yellow groups have a saved rule and green entries
need no action. Each group's cog opens its friendly name, exact Ore Dictionary
aliases and clearly labelled Placement Rules. Outputs choose the ore block;
empty custom groups can be deleted, while populated custom groups use a
confirmed **Dissolve Group** action that returns every alias to its inferred or
curated group. Automatically discovered groups are factual and cannot be
deleted. Placement Rules choose the frequency,
shape, depth and host restrictions for each managed channel. New worlds balance
all eligible outputs in reviewed MMD conflicts while running one placement
budget, so sulfur/sulphur providers do not multiply abundance. **Single**
chooses one output, **Custom** allows a weighted subset, and **Keep Original**
preserves every active rule and restores the selections that were present when
the editor was opened. A red Keep Original group exposes **Accept** so its
unchanged policy can be marked reviewed; this remains pending until the main
editor's **Done** is used. **Reset All** requires confirmation, removes custom
grouping and output choices from the pending edit, then rebuilds the built-in
and inferred groups from the currently loaded ores. It also remains pending
until the main editor's **Done** is used, while Cancel discards it. A Placement Rules row reports how many managed
sources are available, can cycle among them only in a consolidated mode, and
its help explains that the choice is inactive under Keep Original.
Existing worlds start in
Keep Original until their owner explicitly changes a group. Third-party generators remain outside
OreSpawn's control and are clearly reported rather than disabled.
Native vanilla outputs become controllable placement sources only while
**Manage Vanilla Ores** is enabled. Ore Sources updates immediately when that
option changes, and Group Settings prevents moving a vanilla ore alias while
management is off, with a prompt to enable it first.

**Biomes** opens a directory rather than editing only the first palette. It
shows every provider/profile palette in its stored order, the effective surface
rule, owner, climate constraints and geome influences. Dimension materials sit
beside the dimension selector because they apply to every biome in that
dimension's newly generated terrain, not to the selected biome. Light-equivalent
aquifer fluids use a provenance mask and fast pre-decoration substitution;
unusual fluids with different lighting retain a compatible, potentially slower
generator path.
Routine unmanaged biomes are hidden behind **Show All**, which remains filtered
to the selected dimension using Forge's Nether/End biome types and explicit
provider placement declarations. A loaded biome may be
left unchanged or replaced exactly in newly generated terrain; missing targets
remain dormant and resume if their mod returns. The final user replacement
layer always runs after ordinary palettes, while existing chunks are never
rewritten. Provider, palette, dimension and all-biome resets remain pending
until the main editor's **Done** action; **Cancel** discards them.

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

When an existing Forge 1.10 world records Mineralogy 3 in `level.dat` and has
no OreSpawn 4 world profile yet, OreSpawn automatically snapshots that world's
Cyano geology contract. It reads the retained `config/mineralogy.cfg` when
available, preserves the exact ordered rock families and realistic-coal choice,
and writes `geology_mode: legacy` before any new chunks generate. New worlds
still use the installed provider's recommended engine. Switching an upgraded
world to Sky is an explicit choice and can create a visible old/new chunk seam.
OreSpawn also writes `config/orespawn-upgrade-report.txt` for legacy OS rules
and `<world>/serverconfig/orespawn-upgrade-report.txt` for the Mineralogy
handoff, so owners can see exactly which sources, settings, and rock lists were
preserved and whether anything needs review.

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
- [Versioning and release policy](docs/VERSIONS.md)
- [Schemas and examples](docs/README.md)

The full documentation bundle is packaged under `META-INF/orespawn/docs/` and
exported to `config/orespawn-guide/` without overwriting existing files.

## Building

Run Gradle with exact Temurin `17.0.1+12` from the repository root. Install
exact Temurin `25.0.3+9` for ForgeGradle's Mavenizer and exact Temurin
`8.0.502+7` for Minecraft 1.10.2 production and fixture compilation. Java 17
remains the Gradle runtime and production bytecode remains Java 8; the build
rejects a different Java 8 toolchain. Hosted CI also proves an online bootstrap
from an empty Gradle home followed by an offline replay from that same cache:

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
not included in OreSpawn's published jars. It also exercises multiple palettes
in one dimension, an unmanaged loaded biome, exact terminal replacement in new
chunks, unchanged old chunks, and fresh/reload profile and surface parity.

Import or refresh this ForgeGradle 7 project through Eclipse Buildship, then run
`genEclipseRuns`; do not run the removed ForgeGradle 2
`setupDecompWorkspace` task or generate legacy Java-only Eclipse metadata. This
branch uses the Gradle 9.6.1 wrapper, ForgeGradle 7.0.34, Renamer 1.1.5, Forge
12.18.3.2511, the `stable` / `29-1.10.2` MCP mappings, and pack format 2.
Published jars are SRG-reobfuscated for the Forge 1.10 runtime.

The publication contains exactly three artifacts: the reobfuscated runtime jar,
sources, and Javadocs. Callable compiled `api` and deobfuscated `deobf` jars are
retained for local development under `build/libs-dev`; they are excluded from
Maven, CurseForge, GitHub Releases, and the release checksum bundle.

Machine-specific `AGENTS.md` and `agent-notes/` files are intentionally ignored.
Public developer and AI integration guidance lives in `docs/` and is included
in the built jar.

OreSpawn is licensed under LGPL-2.1.
