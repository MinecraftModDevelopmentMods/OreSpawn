# Java API

Only `zone.moddev.mc.orespawn.api` is supported API. Every other package is an
implementation detail. API major version is available as
`OreSpawnApi.API_VERSION` and in the jar manifest as
`OreSpawn-API-Version`.

Provider mods must depend on the full OreSpawn mod at compile time and
runtime. In `mods.toml` use a mandatory dependency, for example:

```toml
[[dependencies.examplemod]]
modId="orespawn"
mandatory=true
versionRange="[4.0.6,5.0.0)"
ordering="AFTER"
side="BOTH"
```

Submit declarations during `InterModEnqueueEvent`:

```java
WorldgenProvider provider = WorldgenProvider.builder("examplemod", 1)
    .rock(Identifier.parse("examplemod:slate"), GeologyFamily.METAMORPHIC, rock -> rock
        .depth(12, 36)
        .weight(1.2)
        .oreReplaceable(true))
    .build();
OreSpawnApi.enqueue(provider);
```

For a complete ore-only Java example, including dimensions, height curves,
patterns, and host tags, see `DEVELOPER_GUIDE.md`.

Definitions are immutable after `build()`. Registry references remain
`Identifier` values until OreSpawn validates and bakes them. Provider
messages are processed through Forge IMC and frozen at load completion; direct
cross-mod mutation during parallel setup is unsupported.

Ore dimensions use `quantity(int)` for fixed budgets or
`quantityRange(min, max)` for inclusive random budgets. The compatibility
`quantity()` getter returns the rounded-up midpoint of a range; new code should
read `minQuantity()` and `maxQuantity()`. Add OS3-style ordinary-dimension
coverage with `OreDefinition.Builder.dimensionSelector(...)` and
`OreDimensionSelector.ALL_EXCEPT_NETHER_AND_END`. Explicit dimensions
override that selector and prevent duplicate placement.

The builder emits provider schema 4. Legacy provider schemas 1-3 remain
readable. Schema 4 is required for biome palettes and dimension materials.

Provider-owned fluid deposits are declarative and may target several dimensions:

```java
FormationDefinition formations = FormationDefinition.builder()
    .horizontalSize(FormationPreset.HUGE)
    .waviness(FormationPreset.LARGE)
    .build();
FluidDepositDefinition brine = FluidDepositDefinition.builder(
        Identifier.parse("examplemod:fluid_deposit/brine"),
        Identifier.parse("examplemod:brine"))
    .dimension(Identifier.parse("minecraft:overworld"), placement -> placement
        .yRange(-48, 32)
        .attempts(0.05)
        .radius(4, 10)
        .verticalRadius(2, 4)
        .maxLobes(3)
        .minSolidCover(2)
        .minSolidShell(1)
        .hostTag(Identifier.parse("minecraft:stone_ore_replaceables")))
    .build();

WorldgenProvider provider = WorldgenProvider.builder("examplemod", 1)
    .fluidDeposit(brine)
    .build();
```

`OilDefinition` and template `.oil(...)` remain deprecated migration adapters
for one legacy oil rule. New integrations should use `FluidDepositDefinition`.

Minecraft 26.1.2 biomes are dynamic registry entries. Ship them as
`data/<modid>/worldgen/biome/<name>.json`, or generate that data through a
`RegistrySetBuilder`. `OreSpawnBiomes.copyAndRegister` is an optional bootstrap
helper for cloning a known biome while generating the datapack entry.

Ore dimension builders expose the same biome filters as provider JSON and
fluid-deposit builders. Use `.biome(...)` and `.biomeDictionary(...)` for
inclusions, with `.excludeBiome(...)` and `.excludeBiomeDictionary(...)` for
exclusions. These methods work on both explicit `.dimension(...)` rules and
`.dimensionSelector(...)` fallbacks; built definitions and their returned
filter sets are immutable.

Register custom biomes with Forge as usual:

```java
public static final ResourceKey<Biome> CANDY_PLAINS = ResourceKey.create(
    Registries.BIOME, Identifier.parse("examplemod:candy_plains"));

public static void bootstrapBiomes(BootstrapContext<Biome> context) {
    OreSpawnBiomes.copyAndRegister(
        context, CANDY_PLAINS, Biomes.PLAINS,
        builder -> builder.temperature(0.8F).downfall(0.4F));
}
```

Add that bootstrap to the `RegistrySetBuilder` supplied to Forge's
`DatapackBuiltinEntriesProvider`. Do not put biomes in a `DeferredRegister`:
that registration happens before the live datapack biome registry exists.

Then declare placement and materials through the same provider:

```java
WorldgenProvider provider = WorldgenProvider.builder("examplemod", 1)
    .biomePalette(Identifier.parse("examplemod:overworld"),
        Identifier.parse("minecraft:overworld"), palette -> palette
            .mode(BiomePlacementMode.REPLACE)
            .scope(BiomeReplacementScope.MINECRAFT_ONLY)
            .regionSize(BiomeRegionSize.LARGE)
            .coverage(1.0)
            .fallbackWeight(0.0)
            .biome(Identifier.parse("examplemod:candy_plains"), biome -> biome
                .weight(3.0)
                .similarBiome(Identifier.parse("minecraft:plains"))))
    .dimensionMaterials(Identifier.parse("examplemod:overworld_materials"),
        Identifier.parse("minecraft:overworld"), materials -> materials
            .defaultFluid(Identifier.parse("examplemod:lemonade"))
            .snowBlock(Identifier.parse("examplemod:icing"))
            .iceBlock(Identifier.parse("examplemod:frozen_lemonade")))
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
    GeologyColumn column = sampler.sampleColumn(120, -40, 92);
    LOGGER.info("{} / {} / {}", column.biome(), column.geome(), column.rockAt(20));
});
```

`sampleColumn` performs one biome/dominant-geome classification and reuses its
transition scores for every Y query. Pass the first-free surface height returned
by `Level.getHeight`; OreSpawn classifies the stable quart-biome cell at the
highest occupied block immediately below it, matching chunk geology generation
without display-oriented fuzzy biome zoom. `rockAt` therefore matches Stable
Layers when a close geome transition is staggered by layer, even when later
surface work changes the final heightmap slightly. Sampling is read-only and is
intended for gameplay decisions, diagnostics, and compatible generation outside
OreSpawn's block loops.
Callbacks inside OreSpawn generation loops are intentionally unsupported.

Custom pattern mods create a Forge `DeferredRegister<OrePatternType>` using
`OreSpawnPatternRegistry.REGISTRY_NAME`. An `OrePatternType` contains a codec
and a compiler from decoded settings to `CompiledOrePattern`. Reference it from
an ore dimension with `pattern(patternId, settingsJson)`. OreSpawn decodes and
compiles once while baking the profile; only the compiled placement function
runs during generation.

`OreSpawnOreIntegration` remains as a deprecated facade for early ore-provider
integrations. New code should use `OreSpawnApi`.
