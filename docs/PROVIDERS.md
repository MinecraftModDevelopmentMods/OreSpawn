# Worldgen Providers

Provider mods may contribute through Forge IMC, a packaged resource at
`assets/<provider-modid>/orespawn/provider.json`, or a pack override at
`config/<provider-modid>-orespawn.json`. A valid override is authoritative. A
present malformed override leaves that provider inactive instead of silently
falling back.

Provider schema 5 supports `profile_defaults`, `rocks`, `ores`,
`fluid_deposits`, `geomes`, `biome_rules`, `terrain_dimensions`, and
`templates`, plus `biome_palettes` and `dimension_materials`. Each file requires a
matching `provider_modid`, a positive `provider_revision`, and at least one
contribution. Legacy schemas 1-4 remain accepted; schema 3 introduced fluid
deposits, schema 4 introduced biome and world-material controls, and schema 5
adds canonical ore materials and independent placement channels.

An ore-only provider does not need rocks, geomes, or terrain dimensions. Give
each ore explicit host blocks or tags and OreSpawn will leave vanilla terrain,
including vanilla granite/diorite/andesite features, untouched. Formation
controls remain inert until a profile contains eligible rocks and an enabled
terrain-replacement dimension.

Rule IDs in `rocks` and `ores` must use the provider namespace. They are stable
ownership keys, not necessarily block IDs. Set `block` for one output or
`outputs` for a weighted list:

```json
"examplemod:ore/tin": {
  "block": "examplemod:tin_ore",
  "material": "orespawn:tin",
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
      "placement_channel": "orespawn:standard",
      "host_tags": ["forge:stone"]
    }
  }
}
```

`material` identifies what an ore represents; it is not an output registry ID.
Rules for one material and dimension share selectable output sources, while
`placement_channel` keeps ordinary veins independent from custom deposit
engines. Built-in patterns default to `orespawn:standard`; custom patterns
default to their registered pattern-type ID. A schema-5 provider should declare
these fields explicitly when it expects Ore Sources arbitration.

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
      "min_y": 0,
      "max_y": 32,
      "frequency": 0.05,
      "min_radius": 4,
      "max_radius": 10,
      "min_vertical_radius": 2,
      "max_vertical_radius": 4,
      "max_lobes": 3,
      "min_solid_cover": 2,
      "min_solid_shell": 1,
      "host_tags": ["forge:stone"]
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

## Reviewed MMD Ore Sources

OreSpawn ships a deliberately narrow compatibility catalog. BaseMetals,
ModernMetals, BaseGems, BaseMinerals, FantasyMetals and Advantage-family ores
may participate as ordinary interchangeable outputs when their material is
unambiguous. NetherMetals and EndMetals stay in their respective dimension
domains. DenseMetals is enrichment, not an interchangeable output, and
BaseSciences has no ordinary ore-source role.

Reviewed fresh-world conflicts start Balanced with all eligible output blocks.
Mineralogy, then BaseMinerals, then ElectricAdvantage is the initial placement
and Single-mode priority for sulfur; BaseMinerals, then ElectricAdvantage is the
corresponding lithium priority. No other conflict is automatically consolidated
without an explicit catalog row. Exact Ore Dictionary `oreX` inference recognizes the curated
sulfur/sulphur and aluminum/aluminium aliases but deliberately keeps Niter and
Saltpeter distinct. Multiple unrelated entries are marked for review rather
than fuzzy-matched from block names.

Provider definitions that are disabled for placement remain managed
output-only candidates. This permits a sibling mod's block to participate in a
Balanced, Single, or Custom material group without contributing a second
frequency budget. Duplicate rule or alias references to the same registry ID
and metadata are displayed once.

Ore Dictionary membership does not prove that OreSpawn controls a mod's native
generator. Such candidates are displayed as external generation and are never
disabled. Providers should keep native generation enabled until takeover is
active, then let their OreSpawn rules participate normally.

Existing worlds normally merge newly introduced provider rule IDs but do not
overwrite world edits. A Java provider may opt out with
`mergeNewEntriesIntoExistingWorlds(false)` when its rules capture structural
add-on configuration that must begin only in newly created worlds. Disabled and
unassigned rules remain tombstones; removed provider rules remain in the
self-contained snapshot.

Biome providers can add Forge biomes normally, then declare where those biomes
belong through `biome_palettes`. The overlay wraps the dimension's existing
biome source, so it composes after vanilla or another Forge 1.10 biome provider
instead of taking a compile-time dependency on it. Use
`minecraft_only` scope when the provider should leave other mods' biomes alone.
Use `required_similar_biomes` only when an output truly cannot work without a
referenced biome; ordinary compatibility hints belong in `similar_biomes`.

See `examples/examplemod-orespawn.json` for rocks, weighted ore output, a
fluid deposit, a custom dimension, biome palette, world materials, and a
selectable template.
