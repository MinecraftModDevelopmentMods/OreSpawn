# OreSpawn Integration Notes For Coding Agents

OreSpawn 4.0 is a required Forge mod and declarative world-generation engine.
Public API major version 1 consists only of `zone.moddev.mc.orespawn.api`. Treat
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

- Global `config/orespawn-worldgen.json`: schema 6.
- World `serverconfig/orespawn-worldgen.json`: schema 5.
- Provider files: schema 4; legacy schemas 1-3 remain accepted.
- Ore placement accepts fixed `quantity` or paired inclusive
  `min_quantity`/`max_quantity` values in the range 1-64. A complete range is
  authoritative when both forms exist.
- `dimension_selectors.orespawn:all_except_nether_end` applies to ordinary
  dimensions but never Nether or End. Explicit dimension entries override it
  per ore and must also drive vanilla-feature suppression.
- JSON Schemas and examples are under `META-INF/orespawn/docs/` in the jar.
- Schema 4 providers may declare `biome_palettes` and `dimension_materials`.
  Palettes wrap the native dimension biome source. Region presets are 128,
  256, 512, 1024, and 2048 blocks.

Lifecycle and ownership:

- Forge setup is parallel. Never mutate OreSpawn internals directly.
- A pack override file is authoritative over packaged and API definitions for
  the same provider. A malformed override fails closed.
- Provider rule IDs use the provider namespace. A rule's `block` or weighted
  output may reference any installed block.
- Definitions freeze at load completion and change only after restart or an
  operator `/orespawn reload`.
- Auto-selected templates apply only to fresh worlds with no explicit
  `default_template`. Highest priority wins, then lexical ID. Existing world
  profiles never auto-switch.

Performance constraints:

- Do not request callbacks in block-generation loops.
- Registry IDs remain `ResourceLocation` values until setup-time baking.
- Dimension, tag, alias, biome, geome, family, pattern, and block-state
  resolution occurs before generation.
- Biome palettes bake holders, climate bounds, namespace filters, weights,
  surfaces, and dimension materials. Provider callbacks never run in selection.
- Ore rules support `uniform`, `triangle`, `bottom_triangle`, and
  `uniform_bottom_triangle` height distributions plus a 0-1
  `discard_chance_on_air_exposure` value for buried deposits.
- The chunk hot path must contain no config reads, registry access, strings,
  logging, reflection, or per-block allocation.
- Cache biome filters as registry keys, never `Biome` object identities;
  dynamic-registry biome instances are not identity-stable.
- Ore and flat-bedrock retrogen are bounded and marker-based. Terrain strata
  are never retrogened.

Compatibility defaults:

- Standalone OreSpawn is passive: no rocks, terrain dimensions, fluid deposits, ore
  suppression, retrogen, or flat bedrock are enabled by default.
- The Overworld is the conventional geology target, but a provider must opt it
  in. Nether and End terrain remain untouched unless explicitly configured.
- Mineralogy 6 is a provider, not a public-API compatibility facade. Do not use
  removed `zone.moddev.mc.mineralogy.api` classes.

Common tasks are documented in `API.md`, `PROVIDERS.md`, `FEATURES.md`,
`TEMPLATES.md`, `BIOMES.md`, and `DIMENSIONS.md`. Start with
`DEVELOPER_GUIDE.md` when the task is broader than one isolated schema or API
question.
