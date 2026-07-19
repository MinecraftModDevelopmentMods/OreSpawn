# Terrain Dimensions

Standalone OreSpawn has no enabled terrain-replacement dimension. The
Overworld is the conventional geology target used by full providers such as
Mineralogy. Nether and End remain untouched unless a global/world profile
explicitly enables them. Providers may automatically opt in only dimensions
in their own namespace.

Each `terrain_dimensions` entry supplies replacement host blocks or tags and
may restrict generation to explicit biome IDs or biome namespaces. With no
biome restriction, all biomes in that dimension are eligible.

Rock entries may include a `dimensions` array. For backward compatibility, an
entry without this field belongs only to `minecraft:overworld`. A custom
dimension is disabled during baking if no valid eligible rocks resolve.

Example:

```json
"examplemod:crystal_caverns": {
  "enabled": true,
  "biome_namespaces": ["examplemod"],
  "biome_ids": [],
  "host_blocks": ["examplemod:base_rock"],
  "host_tags": []
}
```

OreSpawn resolves dimensions, hosts, tags, rocks, and biome filters before
chunk generation. An unconfigured dimension performs one table lookup and
immediately skips.
