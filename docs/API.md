# Java API

This branch targets Minecraft 1.10.2 and Forge 12. Public examples therefore
use the public `ResourceLocation(String, String)` constructor. Forge 12 has no
`DeferredRegister`, so provider mods use OreSpawn's API-major-1
`BiomeRegistrar` while provider JSON, profiles, schemas, and biome meanings
remain identical to later ports.

Only `zone.moddev.mc.orespawn.api` is supported API. Every other package is an
implementation detail. API major version is available as
`OreSpawnApi.API_VERSION` and in the jar manifest as
`OreSpawn-API-Version`.

Provider mods must depend on the full OreSpawn mod at compile time and
runtime. Forge 1.10 declares the mandatory dependency on the mod annotation,
for example:

```java
@Mod(modid = "examplemod", name = "Example Mod", version = "1.0.0",
    dependencies = "required-after:orespawn@[4.0.6,5.0.0)")
```

Submit declarations during Forge's initialization event, before OreSpawn
freezes provider discovery during post-initialization:

```java
@Mod.EventHandler
public void init(FMLInitializationEvent event) {
    WorldgenProvider provider = WorldgenProvider.builder("examplemod", 1)
        .rock(new ResourceLocation("examplemod", "slate"), GeologyFamily.METAMORPHIC, rock -> rock
            .depth(12, 36)
            .weight(1.2)
            .oreReplaceable(true))
        .build();
    OreSpawnApi.enqueue(provider);
}
```

For a complete ore-only Java example, including dimensions, height curves,
patterns, and host tags, see `DEVELOPER_GUIDE.md`.

Definitions are immutable after `build()`. Registry references remain
`ResourceLocation` values until OreSpawn validates and bakes them. Provider
declarations are collected during Forge initialization and frozen during
post-initialization; late mutation is rejected.

Ore dimensions use `quantity(int)` for fixed budgets or
`quantityRange(min, max)` for inclusive random budgets. The compatibility
`quantity()` getter returns the rounded-up midpoint of a range; new code should
read `minQuantity()` and `maxQuantity()`. Add OS3-style ordinary-dimension
coverage with `OreDefinition.Builder.dimensionSelector(...)` and
`OreDimensionSelector.ALL_EXCEPT_NETHER_AND_END`. Explicit dimensions
override that selector and prevent duplicate placement.

The builder emits provider schema 5. Legacy provider schemas 1-4 remain
readable. Schema 4 is required for biome palettes and dimension materials;
schema 5 adds ore material identity and independent placement channels.

OreSpawn 4.1's biome directory and exact replacement layer add no public Java
API methods or descriptors. Providers continue to declare ordinary schema-4
biome palettes and dimension materials through the existing builders below.
The editor's provider-default snapshot and terminal override palette are
implementation details; add-ons must not depend on their client classes or
reserved `orespawn:ui/biome_overrides/` IDs.

Provider-owned fluid deposits are declarative and may target several dimensions:

```java
FormationDefinition formations = FormationDefinition.builder()
    .horizontalSize(FormationPreset.HUGE)
    .waviness(FormationPreset.LARGE)
    .build();
FluidDepositDefinition brine = FluidDepositDefinition.builder(
        new ResourceLocation("examplemod", "fluid_deposit/brine"),
        new ResourceLocation("examplemod", "brine"))
    .dimension(new ResourceLocation("minecraft", "overworld"), placement -> placement
        .yRange(0, 32)
        .attempts(0.05)
        .radius(4, 10)
        .verticalRadius(2, 4)
        .maxLobes(3)
        .minSolidCover(2)
        .minSolidShell(1)
        .hostTag(new ResourceLocation("forge", "stone")))
    .build();

WorldgenProvider provider = WorldgenProvider.builder("examplemod", 1)
    .fluidDeposit(brine)
    .build();
```

`OilDefinition` and template `.oil(...)` remain deprecated migration adapters
for one legacy oil rule. New integrations should use `FluidDepositDefinition`.

Ore dimension builders expose the same biome filters as provider JSON and
fluid-deposit builders. Use `.biome(...)` and `.biomeDictionary(...)` for
inclusions, with `.excludeBiome(...)` and `.excludeBiomeDictionary(...)` for
exclusions. These methods work on both explicit `.dimension(...)` rules and
`.dimensionSelector(...)` fallbacks; built definitions and their returned
filter sets are immutable.

Declare the canonical material separately from the output block when equivalent
ores may be supplied by several mods:

```java
ResourceLocation sulfur = new ResourceLocation("orespawn", "sulfur");
ResourceLocation standard = new ResourceLocation("orespawn", "standard");

WorldgenProvider provider = WorldgenProvider.builder("examplemod", 2)
    .ore(new ResourceLocation("examplemod", "ore/sulfur"), ore -> ore
        .material(sulfur)
        .dimension(new ResourceLocation("minecraft", "overworld"), placement -> placement
            .placementChannel(standard)
            .yRange(0, 48)
            .attempts(4.0)
            .quantity(8)
            .pattern(OrePattern.VEIN)
            .hostTag(new ResourceLocation("forge", "stone"))))
    .build();
```

`OreDefinition.material()` is optional. Built-in patterns default their
`OreDimensionDefinition.placementChannel()` to `orespawn:standard`; a custom
pattern defaults to its registered pattern-type ID. Explicit channels keep an
ordinary vein budget independent from a region-scale deposit budget while the
material group may still share the same selectable outputs.

Provider definitions are also the authoritative managed-output inventory. A
disabled provider ore remains available as an output-only candidate, but it
cannot own frequency, shape or placement. This is how a sibling mod can offer
its sulfur block without multiplying sulfur generation. Ore Dictionary-only
third-party blocks may be selected as outputs, but OreSpawn never claims to
disable their independent generators.

Global schema 8 and world-profile schema 7 persist `ore_material_groups` and
the output mode chosen for each policy. These are profile configuration, not
additional public Java descriptors. Existing provider and pattern binaries
continue to use the same API-major-1 methods. A compiled custom pattern should
pass its stable deposit identity to `tryPlace(x, y, z, outputIdentity)` so the
selected material output remains constant throughout a body and across chunks.

Create one `BiomeRegistrar` during normal mod construction. It attaches to the
calling mod's event bus and defers biome factories until Forge's biome registry
event. `OreSpawnBiomes.copyAndRegister` clones a known biome without adding a
biome-framework dependency:

```java
private static final OreSpawnBiomes.BiomeRegistrar BIOMES =
    OreSpawnBiomes.registrar("examplemod");

private static final OreSpawnBiomes.BiomeReference CANDY_PLAINS =
    OreSpawnBiomes.copyAndRegister(
    BIOMES, "candy_plains",
    () -> ForgeRegistries.BIOMES.getValue(new ResourceLocation("minecraft", "plains")),
    builder -> builder.temperature(0.8F).downfall(0.4F));
```

The returned handle implements `Supplier<Biome>`, so `.get()` remains the
consumer pattern across OreSpawn versions. Call it only after registries have
completed; `getId()` is available as soon as the declaration is made.

Then declare placement and materials through the same provider:

```java
WorldgenProvider provider = WorldgenProvider.builder("examplemod", 1)
    .biomePalette(new ResourceLocation("examplemod", "overworld"),
        new ResourceLocation("minecraft", "overworld"), palette -> palette
            .mode(BiomePlacementMode.REPLACE)
            .scope(BiomeReplacementScope.MINECRAFT_ONLY)
            .regionSize(BiomeRegionSize.LARGE)
            .coverage(1.0)
            .fallbackWeight(0.0)
            .biome(new ResourceLocation("examplemod", "candy_plains"), biome -> biome
                .weight(3.0)
                .similarBiome(new ResourceLocation("minecraft", "plains"))))
    .dimensionMaterials(new ResourceLocation("examplemod", "overworld_materials"),
        new ResourceLocation("minecraft", "overworld"), materials -> materials
            .defaultFluid(new ResourceLocation("examplemod", "lemonade"))
            .snowBlock(new ResourceLocation("examplemod", "icing"))
            .iceBlock(new ResourceLocation("examplemod", "frozen_lemonade")))
    .build();
```

Biome selection stays declarative: arbitrary provider callbacks are not called
inside chunk generation. See `BIOMES.md` for replacement modes, compatibility
filters, surface blocks, materials, and automatic total-conversion templates.

Query the active profile and sample exact production geology on the server:

```java
OreSpawnApi.getActiveProfile(server).ifPresent(profile ->
    LOGGER.info("Configured rocks: {}", profile.rockIds().size()));

OreSpawnApi.createSampler(server.overworld()).ifPresent(sampler -> {
    GeologyColumn column = sampler.sampleColumn(120, 20, 92);
    LOGGER.info("{} / {} / {}", column.biome(), column.geome(), column.rockAt(20));
});
```

`sampleColumn` performs one biome/geome classification and reuses it for every
Y query. Sampling is read-only, does not load or generate the requested chunk,
and is intended for gameplay decisions, diagnostics, and compatible generation
outside OreSpawn's block loops.

OreSpawn 4.1 adds `OreGenerationContext` as a binary-compatible subtype of the
original `OrePlacementContext`. Every context supplied by OreSpawn implements
the extended type. A custom compiled pattern can obtain stable region-scale
identity without retaining a world object:

```java
if (!(context instanceof OreGenerationContext)) return false;
OreGenerationContext generation = (OreGenerationContext) context;
long seed = generation.worldSeed();
ResourceLocation dimension = generation.dimension();
int chunkX = generation.chunkX();
int chunkZ = generation.chunkZ();
Optional<GeologySampler> geology = generation.geologySampler();
```

The world seed, dimension and chunk coordinates are identical for ordinary
generation and supported retrogen. `geologySampler()` is empty when that
dimension has no active OreSpawn geology configuration. It otherwise returns
the already-prepared, allocation-light sampler for the active world. Large
patterns must independently render only the slice intersecting the current
chunk and continue to use `inside(...)` and `tryPlace(...)` for safe writes.
Do not retain the sampler or placement context beyond the current call.

For a body that intersects several chunks, call
`tryPlace(x, y, z, stableBodyIdentity)` instead. This Java 8 default overload
preserves the original three-coordinate method and therefore existing pattern
binaries. OreSpawn uses the supplied identity to choose one output source for
the complete body rather than independently selecting an output per chunk.

Large-deposit add-ons may make one OreSpawn rule the controller for a resource
in a dimension. Set `.backgroundGenerationScale(value)` on that rule to scale
other OreSpawn-managed rules and vanilla generation for the same primary
output. The controller rule remains unscaled, omitted values mean `1.0`, zero
fully suppresses background generation, and the lowest value wins when several
controllers target the same resource. Vanilla decisions use a stable hash of
world, dimension, chunk and resource rather than mutable event order.

When a rule declares `material`, background scaling applies only to that
material's `orespawn:standard` channel; it does not scale another custom
placement engine. Undeclared legacy rules retain the existing block-based
background behaviour.

Providers whose entries capture structural configuration can call
`.mergeNewEntriesIntoExistingWorlds(false)`. New worlds still receive the
complete provider, while an existing saved world profile does not silently gain
definitions introduced after that world was created. The historical default is
`true`.

Forge 12 custom-pattern mods attach a generic
`RegistryEvent.Register<OrePatternType>` listener to their mod event bus and
register named values into `OreSpawnPatternRegistry.REGISTRY_NAME`. An
`OrePatternType` contains a codec and a compiler from decoded settings to
`CompiledOrePattern`. Reference it from an ore dimension with
`pattern(patternId, settingsJson)`. OreSpawn decodes and compiles once while
baking the profile; only the compiled placement function runs during
generation.

## Client world-settings extensions

An add-on may contribute one configuration screen to OreSpawn's **Mods**
directory without depending on OreSpawn implementation classes. Prefer the
owning-mod registration form during client initialization:

```java
WorldSettingsExtensionRegistry.registerConfigScreen(
    "examplemod",
    parent -> new ExampleDepositSettingsScreen(parent));
```

The original three-argument form remains binary compatible. Its resource
namespace is treated as the owning mod ID:

```java
WorldSettingsExtensionRegistry.register(
    new ResourceLocation("examplemod", "deposit_settings"),
    "button.examplemod.deposit_settings",
    parent -> new ExampleDepositSettingsScreen(parent));
```

OreSpawn owns directory layout and passes the Mods directory as the factory
parent. Add-ons should use that parent for Done; OreSpawn also redirects the
inherited vanilla Escape action there before the player returns to the main
geology screen. Exactly one screen may be owned by
each mod; duplicate ownership, blank legacy translation keys and null factories
are rejected deterministically. The legacy translation key and extension
accessors remain available to existing binaries, although the directory now
uses its packaged cog action. These types are client-only and must not be
loaded from a dedicated-server initialization path.

`OreSpawnOreIntegration` remains as a deprecated facade for early ore-provider
integrations. New code should use `OreSpawnApi`.
