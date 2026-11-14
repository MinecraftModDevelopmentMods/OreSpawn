# Natural rock formations (1.13.2)

Vanilla granite/diorite/andesite patches are country-rock decoration, not
metal ores. `OreSpawnApi.isNativeStonePatchPass()` is a thread-local read-only
pass query so expanded natural-rock blocks can decline that decoration while
remaining ordinary metal-ore hosts. Each wrapped feature restores the previous
pass state when it returns, including after an exception. World reset also clears
the flag. Height caches hold only weak world references. Old profiles without formations
never activate it. Early placement also accepts plain/natural vanilla stone
variants spilled in from a neighbour, but not polished stone, infested blocks,
ore, structures, fluids or tile entities. This makes the formation substrate
independent of whether that native patch ran before or after its chunk.

Provider schema 6 adds `rock_formations`. Older provider schemas and the ordinary
`rocks` layer catalogue keep their existing behavior. A formation is a natural
substrate body, not an ore promise. Every declaration captures an explicit
`algorithm_revision`, enabled state, priority, deterministic salt, geometry and
resolved output materials. Definitions are copied into a new world's profile.

Existing profiles with no section stay without formations, including later
chunks. An existing unprofiled world also starts without them. There is no
formation retrogen. Explicit pack/world entries win over provider defaults.
Providers can supply their own defaults and tuning templates. This does not
add formations to the existing Mineralogy 1.13 release or provide a Realistic
Deposits port; those mods must declare formations before the feature can use them.
Disabling physical terrain or choosing Cyano suppresses
the formation pass. The exact vanilla Overworld generator supports normal,
Amplified and Large Biomes worlds, with the same lava-lake
protection in each. Custom generators, including subclasses of the vanilla
generator, are left unchanged and receive no formation prediction or native patch protection.

Built-in compilers: `orespawn:alkaline_complex`, `granitic_contact`,
`ultramafic_alteration`, `barren_pipe`, `quartz_vein`, `weathering_profile`.
The built-in algorithm revision is 1. All default rules are Overworld-only.
Bodies stay within their deterministic region; coordinates use floor division,
including negative regions. Weathering evaluates warm, wet climate and the
parent below the profile. It does not turn any clay into a bauxite setting.

`WorldgenProvider.Builder.rockFormation(id, type, definition)` declares a body.
`RockFormationRegistry.register(type, compiler)` registers an immutable compiler
during initialization. Compilation resolves blocks, families and settings once;
the registry freezes on first compilation. A prepared column returns either a
baked `FormationMaterial` or null for unchanged country rock. No world mutation
belongs in a compiler. A compiler must be deterministic and must not obtain
chunks, resolve registries or read configuration while sampling.

The early terrain pass and `GeologyColumn.rockAt`/`familyAt` share that prepared
evaluator. `formationAt(y)` is an additive, read-only query exposing the winning
formation ID, algorithm revision, immediate parent state and material. An empty
family marks vein fill or regolith that does not belong to an ordinary layer family.
The query describes planned natural substrate: caves, fluids, structures, ores
and subsequent decorations can leave protected gaps in the realised body.

Placement only exchanges the exact predicted original country rock in the
current chunk, before OreSpawn ores. It preserves tile entities, structure
bounding boxes, caves, fluids and other ores. No compensation fills protected
gaps. Reload requires a restart for formation geometry or policy changes.

Formation integrations require OreSpawn 4.1.7 or newer. Older provider
definitions continue to use their existing API and schema contracts.

Material blocks use flattened IDs, such as `minecraft:granite`. Optional
`properties` select an exact state, for example `{"axis":"x"}` for a log.
Legacy metadata is not a substitute for a flattened block ID.
