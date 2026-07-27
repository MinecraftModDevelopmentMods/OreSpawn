# Worldgen Providers

Provider mods may contribute through Forge IMC, a packaged resource at
`data/<provider-modid>/orespawn/provider.json`, or a pack override at
`config/<provider-modid>-orespawn.json`. A valid override is authoritative. A
present malformed override leaves that provider inactive instead of silently
falling back.

Provider schema 4 supports `profile_defaults`, `rocks`, `ores`,
`fluid_deposits`, `geomes`, `biome_rules`, `terrain_dimensions`, and
`templates`, plus `biome_palettes` and `dimension_materials`. Each file requires a
matching `provider_modid`, a positive `provider_revision`, and at least one
contribution. Legacy schemas 1-3 remain accepted; schema 3 introduced fluid
deposits and schema 4 introduces biome and world-material controls.

An ore-only provider does not need rocks, geomes, or terrain dimensions. Give
each ore explicit host blocks or tags and OreSpawn will leave vanilla terrain,
including vanilla granite/diorite/andesite/tuff features, untouched. Formation
controls remain inert until a profile contains eligible rocks and an enabled
terrain-replacement dimension.

Rule IDs in `rocks` and `ores` must use the provider namespace. They are stable
ownership keys, not necessarily block IDs. Set `block` for one output or
`outputs` for a weighted list:

```json
"examplemod:ore/tin": {
  "block": "examplemod:tin_ore",
  "outputs": [
    { "block": "examplemod:tin_ore", "weight": 90 },
    { "block": "examplemod:rich_tin_ore", "weight": 10 }
  ],
  "enabled": true,
  "dimension_selectors": {
    "orespawn:all_except_nether_end": {
      "enabled": true,
      "min_y": 0,
      "max_y": 95,
      "frequency": 5.0,
      "min_quantity": 4,
      "max_quantity": 11,
      "pattern": "default",
      "host_tags": ["minecraft:stone_ore_replaceables"]
    }
  }
}
```

Fluid-deposit IDs also use the provider namespace. Their `block` may belong to
any installed mod, but it must be a real fluid block. Every enabled dimension
needs hosts and can independently set depth, attempts, lobe geometry, cover,
biome filters, and geome weights:

```json
"examplemod:fluid_deposit/brine": {
  "block": "examplemod:brine",
  "enabled": true,
  "dimensions": {
    "minecraft:overworld": {
      "enabled": true,
      "min_y": -48,
      "max_y": 32,
      "frequency": 0.05,
      "min_radius": 4,
      "max_radius": 10,
      "min_vertical_radius": 2,
      "max_vertical_radius": 4,
      "max_lobes": 3,
      "min_solid_cover": 2,
      "min_solid_shell": 1,
      "host_tags": ["minecraft:stone_ore_replaceables"]
    }
  }
}
```

An enabled ore dimension requires a Y range, expected attempts per chunk in
`frequency`, and at least one host family, host block, or host tag. Use
`quantity` for a fixed block budget, or use both `min_quantity` and
`max_quantity` for an inclusive random budget from 1 through 64. If a fixed
quantity and a complete range are both present, the range is authoritative; a
lone range bound is invalid. Host arrays accept either registry-ID strings or weighted
objects such as `{ "block": "minecraft:stone", "weight": 1.0 }` and
`{ "tag": "forge:stone", "weight": 0.5 }`. Biome include/exclude IDs and
Forge biome-dictionary names may further restrict a rule.

For OS3-compatible placement in ordinary modded dimensions, put a rule under
`dimension_selectors.orespawn:all_except_nether_end`. It applies to every
dimension except `minecraft:the_nether` and `minecraft:the_end`. An explicit
entry in `dimensions` overrides the selector for that ore in the named
dimension, including an explicit disabled rule. This prevents duplicate
generation while allowing one dimension to use different height, quantity, or
host settings.

Use `height_distribution` to select `uniform`, `triangle`,
`bottom_triangle`, or `uniform_bottom_triangle`. Set
`discard_chance_on_air_exposure` from 0 to 1 when some or all of an ore should
remain buried instead of appearing on cave walls.

Only suppress a provider mod's native ore generation when
`OreSpawnApi.isOreTakeoverActive(modid)` returns true. `PENDING` means discovery
has not frozen. `INACTIVE` is the fail-safe and native generation must remain.

Existing worlds merge newly introduced provider rule IDs but do not overwrite
world edits. Disabled and unassigned rules remain tombstones; removed provider
rules remain in the self-contained snapshot.

Biome providers can add Forge biomes normally, then declare where those biomes
belong through `biome_palettes`. The overlay wraps the dimension's existing
biome source, so it composes after vanilla or another installed source instead
of taking a compile-time dependency on it. Use `minecraft_only` scope when the
provider should leave other mods' biomes alone.
Use `required_similar_biomes` only when an output truly cannot work without a
referenced biome; ordinary compatibility hints belong in `similar_biomes`.

See `examples/examplemod-orespawn.json` for rocks, weighted ore output, a
fluid deposit, a custom dimension, biome palette, world materials, and a
selectable template.
