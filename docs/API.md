# Java API

Only `com.mcmoddev.orespawn.api` is supported API. Every other package is an
implementation detail. API major version is available as
`OreSpawnApi.API_VERSION` and in the jar manifest as
`OreSpawn-API-Version`.

Provider mods must depend on the full OreSpawn mod at compile time and
runtime. In `mods.toml` use a mandatory dependency, for example:

```toml
[[dependencies.examplemod]]
modId="orespawn"
mandatory=true
versionRange="[4.0.0,5.0.0)"
ordering="AFTER"
side="BOTH"
```

Submit declarations during `InterModEnqueueEvent`:

```java
WorldgenProvider provider = WorldgenProvider.builder("examplemod", 1)
    .rock(new ResourceLocation("examplemod", "slate"), GeologyFamily.METAMORPHIC, rock -> rock
        .depth(12, 36)
        .weight(1.2)
        .oreReplaceable(true))
    .build();
OreSpawnApi.enqueue(provider);
```

Definitions are immutable after `build()`. Registry references remain
`ResourceLocation` values until OreSpawn validates and bakes them. Provider
messages are processed through Forge IMC and frozen at load completion; direct
cross-mod mutation during parallel setup is unsupported.

The builder emits provider schema 2. Legacy provider schema 1 is intentionally
limited to ore-only JSON files.

Formation and oil settings use the same declarative style when building a
template:

```java
FormationDefinition formations = FormationDefinition.builder()
    .horizontalSize(FormationPreset.HUGE)
    .waviness(FormationPreset.LARGE)
    .build();
OilDefinition oil = OilDefinition.builder()
    .yRange(-48, 32)
    .minSolidCover(2)
    .build();
```

Query the active profile and sample exact production geology on the server:

```java
OreSpawnApi.getActiveProfile(server).ifPresent(profile ->
    LOGGER.info("Configured rocks: {}", profile.rockIds().size()));

OreSpawnApi.createSampler(server.overworld()).ifPresent(sampler -> {
    GeologyColumn column = sampler.sampleColumn(120, -40, 92);
    LOGGER.info("{} / {} / {}", column.biome(), column.geome(), column.rockAt(20));
});
```

`sampleColumn` performs one biome/geome classification and reuses it for every
Y query. Sampling is read-only and is intended for gameplay decisions,
diagnostics, and compatible generation outside OreSpawn's block loops.
Callbacks inside OreSpawn generation loops are intentionally unsupported.

Custom pattern mods create a Forge `DeferredRegister<OrePatternType>` using
`OreSpawnPatternRegistry.REGISTRY_NAME`. An `OrePatternType` contains a codec
and a compiler from decoded settings to `CompiledOrePattern`. Reference it from
an ore dimension with `pattern(patternId, settingsJson)`. OreSpawn decodes and
compiles once while baking the profile; only the compiled placement function
runs during generation.

`OreSpawnOreIntegration` remains as a deprecated facade for early ore-provider
integrations. New code should use `OreSpawnApi`.
