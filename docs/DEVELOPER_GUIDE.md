# OreSpawn Developer Guide

## Decide Which Integration You Need

| Goal | Recommended integration |
|---|---|
| Add ores to vanilla stone | Ore-only provider with explicit host tags |
| Let a modpack tune another mod's rules | `config/<modid>-orespawn.json` |
| Ship rocks, geomes, or custom terrain | Full packaged provider |
| Construct definitions in Java | API provider sent through NeoForge IMC |
| Offer an optional world style | Named template in a provider |
| Add covered underground oil or another fluid | Provider schema 3 fluid deposit |
| Add or place biomes without a framework dependency | Provider schema 4 biome palette |
| Replace surfaces, aquifers, snow, or ice | Provider schema 4 dimension materials |
| Inspect active geology at runtime | `GeologyProfileView` and `GeologySampler` |

Strata are optional. If no enabled terrain dimension has eligible rocks,
OreSpawn skips terrain replacement and all formation/geome settings are inert.
An ore-only provider needs only ore output blocks, dimensions, and valid host
blocks or tags.

## Provider JSON Quick Start

Put a schema-4 file in your mod jar at:

```text
src/main/resources/data/examplemod/orespawn/provider.json
```

The rule IDs must use your mod namespace, but output and host blocks may belong
to any installed mod. This minimal provider places tin in normal Overworld
stone without enabling strata:

```json
{
  "schema_version": 4,
  "provider_modid": "examplemod",
  "provider_revision": 1,
  "ores": {
    "examplemod:ore/tin": {
      "block": "examplemod:tin_ore",
      "enabled": true,
      "source_mod": "examplemod",
      "dimensions": {
        "minecraft:overworld": {
          "enabled": true,
          "min_y": -16,
          "max_y": 96,
          "frequency": 6.0,
          "min_quantity": 4,
          "max_quantity": 11,
          "pattern": "vein",
          "height_distribution": "triangle",
          "host_tags": ["minecraft:stone_ore_replaceables"]
        }
      }
    }
  }
}
```

The complete example at `examples/examplemod-orespawn.json` adds a rock, a
weighted ore output, a provider-owned fluid deposit, a geome, a biome influence,
a custom dimension, a biome palette, world materials, and a selectable template.

## Java API Quick Start

Declare OreSpawn as a required dependency in `neoforge.mods.toml`:

```toml
[[dependencies.examplemod]]
modId="orespawn"
type="required"
versionRange="[4.0.0,5.0.0)"
ordering="AFTER"
side="BOTH"
```

Submit immutable definitions during `InterModEnqueueEvent`:

```java
import zone.moddev.mc.orespawn.api.GeologyFamily;
import zone.moddev.mc.orespawn.api.OreHeightDistribution;
import zone.moddev.mc.orespawn.api.OreDimensionSelector;
import zone.moddev.mc.orespawn.api.OrePattern;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.api.WorldgenProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;

private void enqueueWorldgen(InterModEnqueueEvent event) {
    ResourceLocation tin = new ResourceLocation("examplemod", "tin_ore");
    WorldgenProvider provider = WorldgenProvider.builder("examplemod", 1)
        .ore(tin, ore -> ore
            .retrogen(false)
			.dimensionSelector(OreDimensionSelector.ALL_EXCEPT_NETHER_AND_END,
				placement -> placement
					.yRange(-16, 96)
					.attempts(6.0)
					.quantityRange(4, 11)
                .pattern(OrePattern.VEIN)
                .heightDistribution(OreHeightDistribution.TRIANGLE)
					.hostTag(new ResourceLocation("minecraft", "stone_ore_replaceables"))))
        .build();

    OreSpawnApi.enqueue(provider);
}
```

Only `zone.moddev.mc.orespawn.api` is stable. Do not call classes in
`worldgen`, `integration`, `client`, or other implementation packages.

Use `.quantity(8)` when every attempt should have a fixed budget. The selector
above preserves old OS3 behavior in every ordinary dimension except Nether and
End. Add an explicit `.dimension(overworld, ...)` as well when the Overworld
needs different settings; the explicit rule overrides the selector there.

## Pack Override Quick Start

Copy `examples/examplemod-orespawn.json` to:

```text
config/examplemod-orespawn.json
```

Change `provider_modid` to the exact mod ID and keep every owned rule ID in
that namespace. A present pack override is authoritative. If it is malformed,
OreSpawn marks that provider inactive rather than falling back to packaged or
API values. This fail-safe lets the provider retain native generation.

## Ownership And Takeover

A provider that normally generates its own ores should keep doing so until:

```java
OreSpawnApi.isOreTakeoverActive("examplemod")
```

returns `true`. `PENDING` means provider discovery has not frozen. `INACTIVE`
means the provider file, registry blocks, hosts, dimensions, or ownership rules
did not validate. Never disable native generation for either state.

## Configuration Values At A Glance

- Geology modes: `geome` (Sky) and `legacy` (Cyano).
- Formation algorithms: `stable_layers` and migration-only `sky_v1`.
- Presets: `tiny`, `small`, `average`, `large`, `huge`, `custom`.
- Families: `sedimentary`, `metamorphic`, `igneous_intrusive`,
  `igneous_volcanic`.
- Patterns: `default`, `vein`, `normal_cloud`, `precision`, `clusters`,
  `underfluids`; legacy aliases `cluster` and `cloud` are accepted.
- Height distributions: `uniform`, `triangle`, `bottom_triangle`,
  `uniform_bottom_triangle`.
- `frequency`: expected attempts per chunk from 0 to 64. The integer part is
  guaranteed and the fraction is the chance of one extra attempt.
- `quantity`: fixed block-placement budget per attempt from 1 to 64.
- `min_quantity` and `max_quantity`: paired inclusive random budget; a complete
  range takes precedence over a fixed quantity.
- `dimension_selectors.orespawn:all_except_nether_end`: OS3-compatible fallback
  for ordinary dimensions; explicit dimension rules override it.
- Air-exposure discard: 0 keeps exposed candidates; 1 rejects all candidates
  touching cave air.
- Biome placement: `augment` or `replace`; scope is `all`, `minecraft_only`, or
  `selected_namespaces`; region sizes are 128-2048 block presets.

See `CONFIGURATION.md` and the JSON Schemas for every field and numeric range.

## Runtime And Performance Rules

Provider files and API definitions freeze before generation. OreSpawn resolves
registry IDs, tags, dimensions, geomes, aliases, and block states while baking.
The generation loop must not contain provider callbacks, config reads, registry
lookups, strings, logging, reflection, or avoidable allocation.

Biome filters are baked as `ResourceKey<Biome>` values. Do not compare baked
`Biome` instances by identity: worldgen can supply equivalent values from a
dynamic registry. Fluid deposits perform one keyed surface-biome lookup per
chunk invocation and no registry lookup in the placement loop.

Biome palettes wrap the dimension's already-selected biome source and bake
registry holders, climate ranges, namespace filters, weights, surfaces, and
world materials at server activation. A dimension without a palette or material
rule keeps the original generator path.

Definitions normally change after a restart. `/orespawn reload` is intended for
operator-controlled profile reloads. Existing chunks are unchanged unless
bounded ore or bedrock retrogen is enabled.

## Distribution Checklist

1. Validate the provider file against `schemas/orespawn-provider.schema.json`.
2. Test without OreSpawn if your mod declares it optional; otherwise declare a
   mandatory dependency.
3. Keep native ore generation enabled until takeover status is active.
4. Test every configured dimension and host tag.
5. For biome providers, test required/optional similar-biome behavior both with
   and without compatibility mods.
6. Confirm the provider appears in `/orespawn status`.
7. Test a new world; profile edits do not rewrite already generated terrain.

OreSpawn's own standard `check` lifecycle includes a consumer-style surface
integration test. A separate test provider creates independently marked
Grass/Dirt, underwater, filler, and roof columns in open and ceiling
normal-noise dimensions. The gate verifies biome and chunk edges, late tree,
vegetation, structure and chest sentinels, the roof underside, and exact save
reload behavior. Run `gradlew check` (or `gradlew build`, which includes it)
before publishing any change to biome registration, palettes, surfaces,
feature ordering, height handling, or profile persistence.
