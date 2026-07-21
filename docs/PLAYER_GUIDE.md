# OreSpawn Player And Server Guide

## The Short Version

OreSpawn is an engine used by other mods. On its own it changes nothing. Mods
such as Mineralogy give it rocks, ores, and sensible default settings.

For a normal game:

1. Open **OreSpawn...** while creating the world.
2. Choose **Recommended Defaults** unless you want to customise geology.
3. Open **Help & Guide** for a plain-language tour of the controls.
4. Press **Done**, then create the world normally.

There is no requirement to use rock strata. Ore-only mods can use OreSpawn to
place ores in ordinary vanilla stone while every geology control remains idle.
When no provider supplies rocks, **Configure Rock Strata...** starts with a
balanced editable set of vanilla stone, deepslate, granite, diorite, andesite,
tuff. Calcite and dripstone keep their special vanilla placement unless a
player deliberately adds them. You can remove the starter rocks or add blocks
from installed mods before creating the world.

## What The Main Controls Mean

- **Template** selects a complete setup supplied by OreSpawn, a mod, or a pack.
- **Sky** creates broad rock layers and geological regions called geomes. The
  surface biome influences a geome without forcing identical borders.
- **Cyano (Legacy)** uses the older classic Mineralogy layer engine.
- **Formation Reach** controls how far rock formations extend sideways.
- **Layer Thickness** controls their vertical thickness.
- **Waviness** bends layers; **Edge Detail** roughens their boundaries.
- **Continuity** controls how often a formation keeps its identity across a
  region.
- **Manage Vanilla Ores** lets OreSpawn replace vanilla ore features with the
  configured OreSpawn rules. Leave it off to keep normal Minecraft placement.
- **Fluid Deposits** appears after strata are enabled or when a mod or pack
  supplies a rule. Press **Add** to choose water, lava, or a fluid block from an
  installed mod. These are covered underground deposits, not exposed vanilla
  lakes. **Solid Cover** controls the roof thickness, while **Solid Shell**
  prevents a deposit from opening into a cave at its sides or underside.

## Rocks, Ores, And Other Mods

The material picker lists blocks from installed mods by full registry ID, for
example `minecraft:calcite` or `examplemod:slate`. **Safe Only** hides doors,
machines, and other blocks that are poor choices for underground terrain.

Ore richness changes attempts per chunk. Each richness step halves or doubles
the installed default while preserving depth and deposit shape. Patterns decide
whether a deposit is compact, vein-like, clustered, cloud-like, or below a
fluid. Hosts decide which blocks, tags, or configured rock families it may
replace.

Removing a rock from generation does not unregister its block or recipes. It
only prevents that rock appearing in newly generated terrain.

## Existing Worlds And Servers

Each world stores its final choices in:

```text
<world>/serverconfig/orespawn-worldgen.json
```

Changes normally affect only chunks generated afterward. Existing terrain is
not rewritten. Ore and flat-bedrock retrogen must be enabled deliberately;
rock strata are never retro-generated.

For a dedicated server, copy the whole world including that file and install
the same mods. Alternatively, place a prepared global profile at
`config/orespawn-worldgen.json` before creating a new server world.

The server console commands `/orespawn status`, `/orespawn reload`, and
`/orespawn dump-biomes` help pack authors diagnose active providers and IDs.
