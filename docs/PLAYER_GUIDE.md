# OreSpawn Player And Server Guide

## The Short Version

OreSpawn is an engine used by other mods. On its own it changes nothing. Mods
such as Mineralogy give it rocks, ores, and sensible default settings.

For a normal game:

1. Open **OreSpawn...** while creating the world.
2. Choose **Recommended Defaults** unless you want to customise geology.
3. Open **Mods** to see which installed mods OreSpawn recognized and whether
   their provider is active. A lit cog opens that mod's own settings; a dim cog
   explains that the mod did not supply a screen.
4. Open **Help & Guide** for a plain-language tour of the controls.
5. Press **Done**, then create the world normally.

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
Use **Biomes** to inspect every installed or referenced biome, every palette in
its effective order, surface blocks, aquifer fluids, snow and ice. The picker
only accepts real installed registry entries. Saved missing targets remain
dormant instead of breaking world creation.

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
- **Ore Sources** opens the material-group editor beside **Add Block**. Compact
  scrolling lists keep relevant groups on the left and the deduplicated union
  of installed ore blocks represented by their aliases on the right. Ordinary
  one-alias, one-output entries are hidden until **Show All** is selected. Red
  groups need attention, yellow groups have a saved rule and green entries need
  no action:
  **Balanced** shares them equally, **Single** uses one, **Custom** enables a
  weighted subset, and **Keep Original** preserves every old placement rule and
  restores the output and placement selections present when the editor opened.
  Groups needing attention sort first. Saving a consolidated rule clears that
  attention state. To keep an unchanged red Keep Original policy, press
  **Accept**; it marks the group reviewed without changing its outputs or
  placement rules. It is saved only when the main editor's **Done** is used.
  **Reset All** asks for confirmation, removes pending custom groups and source
  choices, and rebuilds the built-in and inferred groups from the currently
  loaded ores. Main-editor **Cancel** still discards that reset.
  The cog beside a group opens its friendly
  name, Ore Dictionary aliases and **Placement Rules**. Outputs decide which
  block is used; Placement Rules decide the frequency, shape, depth and host
  restrictions for each managed channel. A single managed source is read-only;
  a row with multiple sources shows the choice count and can be clicked to
  cycle the source after Balanced, Single or Custom is selected. Under Keep
  Original the row is informational because every rule remains independent.
  Hover the Placement Rules help or
  a rule row to see whether it is fixed, selectable, missing, or inactive under
  Keep Original.
  Empty custom groups can be deleted immediately. A populated custom group can
  be dissolved after a second confirmation, returning all aliases to their
  inferred or curated groups. Automatically discovered groups cannot be
  deleted, while the curated Sulfur and Aluminum groups can be reset.
  Turning **Manage Vanilla Ores** on makes native vanilla rules available here
  immediately. When it is off, OreSpawn will not let a vanilla ore alias be
  moved into another group; the screen tells you to enable vanilla management
  first.
  Independent external generators
  are explained but never offered as controllable placement sources.
- **Mods** opens a paginated directory of loaded OreSpawn integrations. It
  reports the Forge mod version, native OS4 or legacy OS1-OS3 lineage, provider
  schema/revision and current status. Historical profile owners that are not
  loaded are not shown.
- **Fluid Deposits** appears after strata are enabled or when a mod or pack
  supplies a rule. Press **Add** to choose water, lava, or a fluid block from an
  installed mod. These are covered underground deposits, not exposed vanilla
  lakes. **Solid Cover** controls the roof thickness, while **Solid Shell**
  prevents a deposit from opening into a cave at its sides or underside.
- **Biomes** opens a compact directory. Managed, changed and missing entries
  appear first; **Show All** includes ordinary registered biomes belonging to
  the selected dimension. Select a
  biome to inspect every placement rule, owner, order, climate limit and
  effective surface. **Leave original behaviour** keeps it unchanged, while
  **Replace in new terrain with...** creates an exact terminal replacement.
  This affects only newly generated chunks and never unregisters the source or
  disables another mod's decoration. Use **Palettes** for mode, scope, region
  size, coverage, fallback and namespace controls. The dimension-named
  Materials control sits with the dimension selector because it is not a
  property of the selected biome. Geome Influences remains available from the
  selected-biome side. Confirmed biome,
  palette, dimension and all-biome resets stay pending until the main editor's
  **Done** action; **Cancel** discards them.

**Overworld Materials**, or the corresponding dimension-named button, applies
to every biome in that dimension's newly generated terrain. It is not limited
to the biome selected on the right and never rewrites existing chunks.
**Aquifer Fluid** changes the normal below-sea-level terrain fluid. Fluids with
native-equivalent lighting use the fast substitution path; an unusual luminous
or differently opaque fluid uses the compatible generator path and may be
slower. Minecraft 1.10.2 exposes only one generator fluid, so the later-format
**Deep Aquifer** values remain stored but their controls are disabled on this
branch. Snow and ordinary ice can also be replaced. Use **Fluid Deposits**, not
dimension materials, for occasional underground lakes or pockets.

## Rocks, Ores, And Other Mods

The material picker lists blocks from installed mods by full registry ID, for
example `minecraft:granite` or `examplemod:slate`. **Safe Only** hides doors,
machines, and other blocks that are poor choices for underground terrain.

Ore richness changes attempts per chunk. Each richness step halves or doubles
the installed default while preserving depth and deposit shape. Patterns decide
whether a deposit is compact, vein-like, clustered, cloud-like, or below a
fluid. Hosts decide which blocks, tags, or configured rock families it may
replace.

When two or more OreSpawn-managed rules represent one material, open **Ore
Sources...**. The list reports the material, dimension, candidate count and
whether it is Consolidated, Separate, Review required, Missing source, or also
has External generation. Open a material to choose one placement source for
each independent channel and enable one or more weighted output sources.

Consolidated mode uses one placement budget, so three sulfur blocks do not mean
three times as much sulfur. Each vein uses one selected output instead of a
salt-and-pepper mixture. Custom region-scale deposit engines remain on their
own placement channel and can keep one output across every chunk of a deposit.
External native generators are explained but never disabled by OreSpawn.

New worlds automatically consolidate only reviewed high-confidence MMD
conflicts. Existing worlds start with conflicts Separate so their established
frequency remains unchanged. Missing selected mods stay visible and are
restored automatically if reinstalled. These edits stay pending until the main
OreSpawn editor's **Done** is pressed; its **Cancel** discards them. Changes
affect only newly generated chunks and never retrogen existing terrain.

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

Existing Mineralogy 3 worlds are automatically pinned to **Cyano (Legacy)**
when their first OreSpawn 4 world profile is created. OreSpawn copies the old
Mineralogy layer settings into that world before generating new chunks, so
later provider defaults cannot switch the engine. New worlds use the installed
provider's recommended engine. Changing an upgraded world to **Sky** remains
an explicit choice and may produce visibly different geology in new chunks.
After upgrading, read `config/orespawn-upgrade-report.txt` and
`<world>/serverconfig/orespawn-upgrade-report.txt`. They confirm which old
OreSpawn and Mineralogy files were found, which values were preserved, and any
items that need manual review. The original files are not changed.

For a dedicated server, copy the whole world including that file and install
the same mods. Alternatively, place a prepared global profile at
`config/orespawn-worldgen.json` before creating a new server world.

The server console commands `/orespawn status`, `/orespawn reload`, and
`/orespawn dump-biomes` help pack authors diagnose active providers and IDs.
