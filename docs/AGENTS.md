# OreSpawn Integration Notes For Humans And Agents

OreSpawn 4.0 is a required Forge mod and declarative world-generation engine.
Public API major version 1 consists only of `com.mcmoddev.orespawn.api`. Treat
every other Java package as internal and unstable.

Integration entry points:

- Java declarations: `OreSpawnApi.enqueue(WorldgenProvider)` during
  `InterModEnqueueEvent`.
- Packaged declarations: `data/<modid>/orespawn/provider.json`.
- Pack overrides: `config/<modid>-orespawn.json`.
- Active queries: `getActiveProfile(MinecraftServer)` and
  `createSampler(ServerLevel)`.
- Native-ore takeover: disable only when `isOreTakeoverActive(modid)` is true.

Configuration contracts:

- Global `config/orespawn-worldgen.json`: schema 4.
- World `serverconfig/orespawn-worldgen.json`: schema 3.
- Provider files: schema 2; legacy ore-only schema 1 remains accepted.
- JSON Schemas and examples are under `META-INF/orespawn/docs/` in the jar.

Lifecycle and ownership:

- Forge setup is parallel. Never mutate OreSpawn internals directly.
- A pack override file is authoritative over packaged and API definitions for
  the same provider. A malformed override fails closed.
- Provider rule IDs use the provider namespace. A rule's `block` or weighted
  output may reference any installed block.
- Definitions freeze at load completion and change only after restart or an
  operator `/orespawn reload`.

Performance constraints:

- Do not request callbacks in block-generation loops.
- Registry IDs remain `ResourceLocation` values until setup-time baking.
- Dimension, tag, alias, biome, geome, family, pattern, and block-state
  resolution occurs before generation.
- The chunk hot path must contain no config reads, registry access, strings,
  logging, reflection, or per-block allocation.
- Ore and flat-bedrock retrogen are bounded and marker-based. Terrain strata
  are never retrogened.

Compatibility defaults:

- Standalone OreSpawn is passive: no rocks, terrain dimensions, oil, ore
  suppression, retrogen, or flat bedrock are enabled by default.
- The Overworld is the conventional geology target, but a provider must opt it
  in. Nether and End terrain remain untouched unless explicitly configured.
- Mineralogy 6 is a provider, not a public-API compatibility facade. Do not use
  removed `com.mcmoddev.mineralogy.api` classes.

Common tasks are documented in `API.md`, `PROVIDERS.md`, `FEATURES.md`,
`TEMPLATES.md`, and `DIMENSIONS.md`.
