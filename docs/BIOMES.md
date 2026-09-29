# Biomes And World Materials

Minecraft 1.10.2 uses Forge 12's static biome registry and set-based
`BiomeProvider` contract. OreSpawn adapts those target APIs internally while
keeping the API-major-1 provider JSON, profile, and save contracts unchanged.

OreSpawn can place provider biomes and replace their visible world materials
without requiring TerraBlender. It does not register biomes for a child mod:
the provider still registers ordinary Forge `Biome` objects, then supplies
declarative placement and material rules to OreSpawn.

This feature is optional. Ore-only providers and existing Mineralogy profiles
with no biome palettes use Minecraft's original biome source unchanged.

## How Placement Composes

OreSpawn waits until a server level has its final `ChunkGenerator`, then wraps
the biome source already selected for that dimension. Vanilla, TerraBlender,
Biomes O' Plenty, or another framework therefore runs first. OreSpawn reads the
source biome once and applies pre-baked palette rules.

The wrapper is native to OreSpawn and has no TerraBlender compile-time or
runtime dependency. This keeps simple child mods small while still allowing a
pack that already uses TerraBlender to compose safely.

Each palette has:

- `dimension`: the full dimension ID;
- `mode`: `augment` keeps the source as a weighted fallback, while `replace`
  chooses only provider biomes when a rule applies;
- `scope`: `minecraft_only`, `selected_namespaces`, or `all`;
- `region_size`: `tiny`, `small`, `average`, `large`, or `huge`, corresponding
  to 128, 256, 512, 1024, or 2048 block regions;
- `coverage`: the proportion of eligible regions touched by the palette;
- `fallback_weight`: source-biome weight in augment mode;
- namespace include/exclude lists and weighted output biome entries.

Biome entries may restrict temperature/downfall and list similar source biomes.
`similar_biomes` is optional compatibility: missing IDs are ignored.
`required_similar_biomes` is strict: if one is absent, the output is disabled
and OreSpawn warns once while baking.

## Provider JSON Example

```json
{
  "schema_version": 4,
  "provider_modid": "cakeworld",
  "provider_revision": 1,
  "biome_palettes": {
    "cakeworld:overworld": {
      "dimension": "minecraft:overworld",
      "enabled": true,
      "mode": "replace",
      "scope": "minecraft_only",
      "region_size": "large",
      "coverage": 1.0,
      "fallback_weight": 0.0,
      "include_namespaces": [],
      "exclude_namespaces": [],
      "biomes": {
        "cakeworld:candy_plains": {
          "enabled": true,
          "weight": 3.0,
          "similar_biomes": ["minecraft:plains"],
          "required_similar_biomes": [],
          "min_temperature": 0.2,
          "max_temperature": 1.2,
          "min_downfall": 0.0,
          "max_downfall": 0.8,
          "surface": {
            "top_block": "cakeworld:icing",
            "filler_block": "cakeworld:chocolate_sponge",
            "underwater_block": "cakeworld:biscuit_sand",
            "filler_depth": 3
          }
        }
      }
    }
  },
  "dimension_materials": {
    "cakeworld:overworld": {
      "dimension": "minecraft:overworld",
      "enabled": true,
      "default_fluid": "cakeworld:lemonade",
      "snow_block": "cakeworld:icing",
      "ice_block": "cakeworld:frozen_lemonade"
    }
  }
}
```

Provider-owned rule IDs use the provider namespace. Output biomes and blocks
must be installed registry IDs. Fluid material IDs must resolve to blocks whose
default states contain real fluids.

## Registration Helper

Forge 12 predates `DeferredRegister`. Declare one OreSpawn registrar during mod
construction, then use `copyAndRegister` to copy a known biome before applying
small changes:

```java
private static final OreSpawnBiomes.BiomeRegistrar BIOMES =
    OreSpawnBiomes.registrar("examplemod");

private static final OreSpawnBiomes.BiomeReference CANDY_PLAINS =
    OreSpawnBiomes.copyAndRegister(
    BIOMES, "candy_plains",
    () -> ForgeRegistries.BIOMES.getValue(new ResourceLocation("minecraft", "plains")),
    builder -> builder.temperature(0.8F).downfall(0.4F));
```

`blankAndRegister` starts from an empty builder and is intended for advanced
providers that deliberately supply every required climate, effects, spawn, and
generation field. Both helpers return a supplier-compatible handle, reject
duplicate or late declarations, and only register content; placement belongs
in the provider declaration.

## Surfaces And Materials

Biome surfaces support:

- `top_block`: exposed ground;
- `filler_block`: material below the top;
- `underwater_block`: exposed ground below sea level;
- `ceiling_block`: optional underside material;
- `filler_depth`: 0-16 blocks.

Provider surfaces run during `LOCAL_MODIFICATIONS`: after Minecraft has built
base surfaces and lakes, but before structures and vegetation. That ordering
lets OreSpawn replace the actual exposed ground while preserving later trees,
plants, authored structures, and block entities. In ceiling dimensions,
`ceiling_block` applies to the roof underside and does not replace the roof top.

Surface correction is generation-only. Installing or updating OreSpawn does
not rewrite already generated chunks; travel into new terrain to see a changed
provider surface definition.

Provider-declared `terrain_dimensions.host_blocks` are resolved by the single
terrain scan at the start of Forge 1.10's early generation coordinator,
immediately before provider surfaces. Matching natural blocks already present
in base terrain are eligible for geology; matching blocks authored later by
structures or vegetation are not. Air, liquids, bedrock, and block-entity
states remain protected even if a provider mistakenly lists their block IDs.

Dimension materials apply to every biome in one dimension and only to newly
generated terrain. They support the ordinary aquifer fluid and replacements
for vanilla snow and ice. For a fluid with the same opacity and emitted light
as the native generator fluid, OreSpawn records only native aquifer cells while
the chunk is being built, lets Minecraft generate and light the terrain with
its native fluid, then substitutes exactly those cells before decoration. This
does not touch later lakes, springs or decorator fluids and creates no reload
retrogen. A fluid with different lighting uses the compatible direct-generator
path so its light remains correct; that path can be slower.

Minecraft 1.10.2 has one exposed generator-fluid field, so `default_fluid` is
fully supported. Later-format `deep_aquifer_fluid` and
`deep_aquifer_max_y` values remain readable and are preserved in saved
profiles, but this branch disables their editor controls, warns when a distinct
deep fluid was requested, and uses the ordinary fluid for generation. OreSpawn
converts weather products in loaded chunks and around players; it does not
replace every water or lava block after generation. Unsupported independent
chunk generators are reported and left unchanged.

## Templates And Total Conversions

A total-conversion mod may bundle an automatic template:

```json
"templates": {
  "cakeworld:cake_world": {
    "required_mods": ["cakeworld"],
    "auto_select": true,
    "auto_select_priority": 100,
    "profile": {
      "selected_template": "cakeworld:cake_world"
    }
  }
}
```

Automatic selection occurs only for fresh worlds when no explicit global
`default_template` exists. Existing world profiles never change automatically.
If several providers request automatic selection, the highest priority wins,
then lexical template ID order.

## Biome Directory And Exact Overrides

**Biomes** is visible even when rock strata are disabled. At normal window
sizes it keeps a compact biome list and the selected biome's details together;
at the minimum supported width it uses list and detail pages without losing the
selection or pending edits. The default list contains provider-managed,
modified, disabled and missing entries. **Show All** also displays routine
registered biomes associated with the selected dimension. OreSpawn uses Forge's
Nether/End biome types for ordinary loaded entries and exact provider placement
declarations when they exist. Explicit replacements and missing entries remain
visible in their configured dimension.

The detail pane reports the friendly name, registry ID, mod owner, status and
effective placement-rule count. Every profile palette is shown in its stored
sequential order instead of only the first palette for the dimension. Placement
details expose enabled state, weight, source-biome and required-biome limits,
climate range, top/filler/underwater/ceiling blocks, filler depth, owner and
effective order. When several enabled palettes define a surface for one biome,
the directory identifies the effective last rule without silently reordering
the profile.

**Leave original behaviour** makes no exact replacement. **Replace in new
terrain with...** accepts any loaded source and target biome, including an
external biome. Provider ownership remains available in the biome details but
does not produce a warning for a valid replacement. OreSpawn stores these
choices in the reserved
`orespawn:ui/biome_overrides/<dimension>` palette. It is a 100% `replace/all`
palette with zero fallback and always bakes after every ordinary palette,
regardless of JSON insertion order. Each source has one terminal target;
chains are flattened, inbound mappings follow a subsequently replaced target,
and cycles or self-replacements are rejected. A missing target remains in the
profile but leaves its source unchanged until the target mod returns.

Exact replacements affect only chunks generated after the edit. They do not
unregister a biome, suppress another mod's decorators, alter spawn lists or
rewrite existing chunks. Arbitrary weighted injection of loaded biomes is not
offered; only provider-declared palette outputs retain weighted placement.

The palette-settings page exposes mode, scope, region size, coverage, fallback
weight and namespace include/exclude lists. Dimension materials and geome
influences remain reachable from the directory. Reset Biome clears its exact
replacement and restores active provider placements for that biome; palette,
dimension and all-biome resets restore loaded-provider defaults and remove
user/profile-only palettes while preserving entries owned by missing providers.
All edits and resets remain pending until the main editor's **Done** action;
**Cancel** discards them byte-for-byte.

## Performance Boundaries

OreSpawn resolves dimensions, biomes, blocks, fluids, namespace filters,
climate ranges, and surfaces while the profile is baked. Runtime biome
selection uses the delegated source result, integer region hashing, primitive
weights, and pre-baked biome instances. It performs no provider callback, JSON access,
registry lookup, tag lookup, logging, or per-column allocation.
