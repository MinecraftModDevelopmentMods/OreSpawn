# Biomes And World Materials

OreSpawn can place provider biomes and replace their visible world materials.
It does not register biomes for a child mod: the provider still registers
ordinary NeoForge data-driven `Biome` entries, then supplies declarative
placement and material rules to OreSpawn.

This feature is optional. Ore-only providers and existing Mineralogy profiles
with no biome palettes use Minecraft's original biome source unchanged.

## How Placement Composes

OreSpawn waits until a server level has its final `ChunkGenerator`, then wraps
the biome source already selected for that dimension. Vanilla or another
installed biome source therefore runs first. OreSpawn reads the source biome
once and applies pre-baked palette rules.

The wrapper is native to OreSpawn. This keeps simple child mods small and lets
OreSpawn compose with the biome source already selected by the pack.

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
      "deep_aquifer_fluid": "cakeworld:hot_fudge",
      "deep_aquifer_max_y": -40,
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

Biomes are loaded from `data/<modid>/worldgen/biome/` when a world loads. A
child mod may write those JSON files directly or generate them with
`DatapackBuiltinEntriesProvider`. `OreSpawnBiomes.copyAndRegister` copies a
known biome's complete builder inside a `RegistrySetBuilder` bootstrap:

```java
public static final ResourceKey<Biome> CANDY_PLAINS = ResourceKey.create(
    Registries.BIOME, new ResourceLocation("examplemod", "candy_plains"));

public static final RegistrySetBuilder BIOME_BUILDER = new RegistrySetBuilder()
    .add(Registries.BIOME, context -> {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
        OreSpawnBiomes.copyAndRegister(context, CANDY_PLAINS, biomes, Biomes.PLAINS,
            builder -> builder.temperature(0.8F).downfall(0.4F));
    });
```

`blankAndRegister` starts from an empty builder and is intended for advanced
datagen that deliberately supplies every required climate, effects, spawn, and
generation field. Both helpers create datapack content; live placement belongs
in the provider declaration. Do not use a static `DeferredRegister<Biome>`:
NeoForge 20.6 biomes belong to the dynamic world registry.

## Surfaces And Materials

Biome surfaces support:

- `top_block`: exposed ground;
- `filler_block`: material below the top;
- `underwater_block`: exposed ground below sea level;
- `ceiling_block`: optional underside material;
- `filler_depth`: 0-16 blocks.

Dimension materials support the ordinary aquifer fluid, a deep aquifer fluid
and threshold, and replacements for vanilla snow and ice. OreSpawn converts
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
