# OreSpawn Player And Server Guide

## The Short Version

OreSpawn is an engine used by other mods. On its own it changes nothing. Mods
such as Mineralogy give it rocks, ores, and sensible default settings.

For a normal game:

1. Open **OreSpawn...** while creating the world.
2. Choose **Recommended Defaults** unless you want to customise geology.
3. Open **Help & Guide** for a plain-language tour of the controls.
4. Press **Done**, then create the world normally.

Hover over unfamiliar controls for a short explanation. The same explanations
are collected in **Help & Guide**, so a setting can be learned either while
editing it or one topic at a time.

There is no requirement to use rock strata. Ore-only mods can use OreSpawn to
place ores in ordinary vanilla stone while every geology control remains idle.
When no provider supplies rocks, **Configure Rock Strata...** starts with a
balanced editable set of vanilla stone, granite, diorite, and andesite. Other
vanilla terrain blocks keep their normal placement unless a player deliberately
adds them. You can remove the starter rocks or add blocks from installed mods
before creating the world.

Mods can also offer new biomes and world materials without enabling strata.
Use **Biomes & World Materials** to inspect installed dimension palettes,
surface blocks, aquifer fluids, snow, and ice. The picker only accepts real
installed registry entries. Missing optional compatibility biomes are skipped
safely instead of breaking world creation.

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
- **Biomes & World Materials** controls broad biome regions and what their
  surfaces, underground water, snow, and ice are made from. **Augment** mixes
  new biomes into the existing source; **Replace** creates a complete provider
  style. Namespace scope protects other biome mods unless a pack deliberately
  opts them in.

**World Materials** applies across an entire dimension. **Aquifer Fluid**
changes the normal below-sea-level fluid. Minecraft 1.13.2 exposes only that
single generator fluid, so the later-format **Deep Aquifer** values remain
stored but their controls are disabled on this branch. Snow and ordinary ice
can also be replaced. Use **Fluid Deposits**, not World Materials, for
occasional underground lakes or pockets.

## Rocks, Ores, And Other Mods

The material picker lists blocks from installed mods by full registry ID, for
example `minecraft:granite` or `examplemod:slate`. **Safe Only** hides doors,
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

### Upgrading a Mineralogy world

If the world was already generated with Mineralogy 1.10, 1.12, or 5.x and has
no OreSpawn world profile yet, OreSpawn reads the Mineralogy version saved in
the world and the matching old configuration. It preserves the selected
engine, numeric settings, rock order, and lists rather than silently applying
new-world defaults. Look for:

```text
<world>/serverconfig/orespawn-upgrade-report.txt
```

The report explains what was detected and retained, including any missing rock
IDs or fallback values. OreSpawn leaves the old configuration and generated
chunks untouched. A fresh world does not inherit this behavior merely because
an old Mineralogy config is still present in the instance.
