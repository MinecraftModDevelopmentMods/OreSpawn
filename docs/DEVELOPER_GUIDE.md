# OreSpawn Developer Guide

## Decide Which Integration You Need

| Goal | Recommended integration |
|---|---|
| Add ores to vanilla stone | Ore-only provider with explicit host tags |
| Let a modpack tune another mod's rules | `config/<modid>-orespawn.json` |
| Ship rocks, geomes, or custom terrain | Full packaged provider |
| Construct definitions in Java | API provider sent through Forge IMC |
| Offer an optional world style | Named template in a provider |
| Add covered underground oil or another fluid | Provider schema 3 fluid deposit |
| Add or place biomes without a framework dependency | Provider schema 4 biome palette |
| Replace surfaces, aquifers, snow, or ice | Provider schema 4 dimension materials |
| Inspect active geology at runtime | `GeologyProfileView` and `GeologySampler` |
| Build a deterministic region-scale ore pattern | `OreGenerationContext` |
| Replace or blend background ore generation | `backgroundGenerationScale` |
| Share equivalent ore outputs without multiplying placement | Provider schema 5 `material` and `placement_channel` |
| Add an add-on settings screen | Client-only `WorldSettingsExtensionRegistry` |

Strata are optional. If no enabled terrain dimension has eligible rocks,
OreSpawn skips terrain replacement and all formation/geome settings are inert.
An ore-only provider needs only ore output blocks, dimensions, and valid host
blocks or tags.

## Provider JSON Quick Start

Put a schema-5 file in your mod jar at:

```text
src/main/resources/assets/examplemod/orespawn/provider.json
```

The rule IDs must use your mod namespace, but output and host blocks may belong
to any installed mod. This minimal provider places tin in normal Overworld
stone without enabling strata:

```json
{
  "schema_version": 5,
  "provider_modid": "examplemod",
  "provider_revision": 1,
  "ores": {
    "examplemod:ore/tin": {
      "block": "examplemod:tin_ore",
      "material": "orespawn:tin",
      "enabled": true,
      "source_mod": "examplemod",
      "dimensions": {
        "minecraft:overworld": {
          "enabled": true,
          "min_y": 0,
          "max_y": 96,
          "frequency": 6.0,
          "min_quantity": 4,
          "max_quantity": 11,
          "pattern": "vein",
          "placement_channel": "orespawn:standard",
          "height_distribution": "triangle",
          "host_tags": ["forge:stone"]
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

Declare OreSpawn as a mandatory dependency on the Forge 1.10 mod annotation:

```java
@Mod(modid = "examplemod", name = "Example Mod", version = "1.0.0",
    dependencies = "required-after:orespawn@[4.0.6,5.0.0)")
```

Submit immutable definitions during Forge's initialization event:

```java
import zone.moddev.mc.orespawn.api.GeologyFamily;
import zone.moddev.mc.orespawn.api.OreHeightDistribution;
import zone.moddev.mc.orespawn.api.OreDimensionSelector;
import zone.moddev.mc.orespawn.api.OrePattern;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.api.WorldgenProvider;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod.EventHandler
public void init(FMLInitializationEvent event) {
    ResourceLocation tin = new ResourceLocation("examplemod", "tin_ore");
    WorldgenProvider provider = WorldgenProvider.builder("examplemod", 1)
        .ore(tin, ore -> ore
            .material(new ResourceLocation("orespawn", "tin"))
            .retrogen(false)
			.dimensionSelector(OreDimensionSelector.ALL_EXCEPT_NETHER_AND_END,
				placement -> placement
					.yRange(0, 96)
					.attempts(6.0)
					.quantityRange(4, 11)
                .pattern(OrePattern.VEIN)
                .placementChannel(new ResourceLocation("orespawn", "standard"))
                .heightDistribution(OreHeightDistribution.TRIANGLE)
					.biome(new ResourceLocation("minecraft", "plains"))
					.biomeDictionary("FOREST")
					.excludeBiome(new ResourceLocation("minecraft", "roofed_forest"))
					.excludeBiomeDictionary("SPOOKY")
					.hostTag(new ResourceLocation("forge", "stone"))))
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
Ore dimension builders support the same exact-ID and biome-dictionary include
and exclude filters as provider JSON and fluid-deposit builders.

Give equivalent ores the same canonical `.material(...)`. A placement channel
identifies an independent generation engine: built-in patterns default to
`orespawn:standard`, while a custom pattern defaults to its pattern-type ID.
OreSpawn can then consolidate one placement budget while retaining weighted
whole-vein output choice. Do not use a block ID as a material merely because
the names happen to match; use a stable semantic material ID.

## Region-scale custom patterns

OreSpawn 4.1 supplies compiled patterns with `OreGenerationContext`, a subtype
of the original placement context. Its world seed, dimension ID and current
chunk coordinates let an add-on derive a stable region/deposit identity that
does not depend on chunk generation order. `geologySampler()` provides the
active OreSpawn geology where one is configured.

Render only the part of a deterministic body that intersects `chunkX()` and
`chunkZ()`. All reads and writes must still pass through `inside(...)`,
`isFluid(...)` and `tryPlace(...)`; Forge 1.10 deliberately restricts those
operations to the current chunk. Perform definition parsing and expensive
setup in the registered pattern compiler, not its placement callback.

When placing a multi-chunk body, call
`tryPlace(x, y, z, stableBodyIdentity)`. The overload is binary-compatible with
existing patterns and makes OreSpawn select one output source for the entire
body. The identity must be stable for the deposit and independent of chunk
generation order.

## Add-on world settings

Register one optional client configuration screen during client
initialization. The preferred form identifies the owning mod directly:

```java
WorldSettingsExtensionRegistry.registerConfigScreen(
    "examplemod",
    parent -> new ExampleDepositSettingsScreen(parent));
```

The existing form remains available to already-compiled add-ons, and its
resource namespace becomes the owner:

```java
WorldSettingsExtensionRegistry.register(
    new ResourceLocation("examplemod", "deposit_settings"),
    "button.examplemod.deposit_settings",
    parent -> new ExampleDepositSettingsScreen(parent));
```

The registry is client-only. OreSpawn lists the loaded mod once in its
paginated **Mods** directory, renders a packaged cog, and supplies that
directory as the parent. The extension owns its complete screen and should
return to that parent from Done; OreSpawn redirects inherited vanilla Escape
there as well. OreSpawn synchronizes its pending editor session before opening
the directory. Only one configuration screen may be
registered per owning mod, so two resource paths in the same namespace are a
deterministic duplicate rather than extra main-screen rows.

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
lookups, strings, logging, reflection, or avoidable allocation. Compiled custom
patterns are the intentional extension point; they receive only baked settings,
the allocation-free placement context and cached generation identity/sampler.

Biome filters retain their exact registry IDs. Minecraft 1.10.2 uses a static
Forge-backed biome registry, so generation carries those stable IDs alongside
the selected biome instances. Fluid deposits perform one keyed surface-biome
lookup per chunk invocation and no registry lookup in the placement loop.

Biome palettes wrap the dimension's already-selected biome provider and bake
static-registry biomes, climate ranges, namespace filters, weights, surfaces,
and world materials at server activation. No TerraBlender API is called. A
dimension without a palette or material rule keeps the original generator path.

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
and reload behavior. On Forge 12 it also exercises the registered spring
wrapper with a non-Forge-stone provider rock and registers an external ore
pattern beside every built-in type. It also verifies OreSpawn's Forge 12 biome
registrar rejects duplicate and late declarations.
Run `gradlew check` (or `gradlew build`, which includes it)
before publishing any change to biome registration, palettes, surfaces,
feature ordering, height handling, or profile persistence.
