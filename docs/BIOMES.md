# Biomes And World Materials

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

`OreSpawnBiomes.copyAndRegister` copies a known biome's complete builder before
applying small changes. This is useful for a simple content mod:

```java
RegistryObject<Biome> candyPlains = OreSpawnBiomes.copyAndRegister(
    BIOMES, "candy_plains",
    () -> ForgeRegistries.BIOMES.getValue(new ResourceLocation("minecraft", "plains")),
    builder -> builder.temperature(0.8F).downfall(0.4F));
```

`blankAndRegister` starts from an empty builder and is intended for advanced
providers that deliberately supply every required climate, effects, spawn, and
generation field. Both helpers only register content; placement belongs in the
provider declaration.

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

Provider-declared `terrain_dimensions.host_blocks` are resolved by one terrain
scan at the start of `LOCAL_MODIFICATIONS`, immediately before provider
surfaces. Matching natural blocks already present in base terrain are eligible
for geology; matching blocks authored by later structure or vegetation stages
are not. Air, fluids, bedrock, and block-entity states remain protected even if
a provider mistakenly lists their block IDs as terrain hosts.

Surface correction is generation-only. Installing or updating OreSpawn does
not rewrite already generated chunks; travel into new terrain to see a changed
provider surface definition.

Dimension materials support the ordinary aquifer fluid and replacements for
vanilla snow and ice. Minecraft 1.17.1 has one exposed generator-fluid field,
so `default_fluid` is fully supported. Later-format `deep_aquifer_fluid` and
`deep_aquifer_max_y` values remain readable and are preserved in saved profiles,
but this branch disables their editor controls, warns when a distinct deep
fluid was requested, and uses the ordinary fluid for generation. OreSpawn converts
weather products in loaded chunks and around players; it does not replace every
water or lava block after generation.

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

## World-Creation Editor

**Biomes & World Materials** is visible even when rock strata are disabled.
It lists palettes and materials by dimension, uses installed-registry pickers,
and validates IDs before world creation. The editor is creation-only in 4.0.0;
existing worlds remain editable through their self-contained server profile.

## Performance Boundaries

OreSpawn resolves dimensions, biomes, blocks, fluids, namespace filters,
climate ranges, and surfaces while the profile is baked. Runtime biome
selection uses the delegated source result, integer region hashing, primitive
weights, and cached holders. It performs no provider callback, JSON access,
registry lookup, tag lookup, logging, or per-column allocation.
