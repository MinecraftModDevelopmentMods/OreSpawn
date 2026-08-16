# Configuration Reference

OreSpawn uses three JSON contracts:

| File | Schema | Purpose |
|---|---:|---|
| `config/orespawn-worldgen.json` | 6 | Installed-pack defaults for new worlds |
| `<world>/serverconfig/orespawn-worldgen.json` | 5 | Self-contained snapshot for one world |
| `config/<modid>-orespawn.json` | 4 | Optional authoritative provider override |

A provider may package schema 4 at `data/<modid>/orespawn/provider.json`.
Legacy provider schemas 1-3 remain accepted. Fluid deposits require schema 3;
biome palettes and dimension materials require schema 4.

The profile for a new world is merged in this order: passive OreSpawn defaults,
packaged or API providers, provider override files, the global configuration,
the selected template, and Create World edits. The result is saved with the
world. Restart after editing JSON by hand.

An existing generated world follows a stricter safety order. Its existing
`serverconfig/orespawn-worldgen.json` always wins. If none exists, saved legacy
Mineralogy mod metadata may select the matching 1.10, 1.12, or 5.x config
contract before the world profile is first written. Installed-pack defaults,
Create World choices, and unrelated stale legacy files cannot override that
saved-world identity. See `MIGRATION.md` and the generated per-world upgrade
report for the exact decision.

## Top-Level Fields

| Field | Values | Meaning |
|---|---|---|
| `schema_version` | Contract-specific integer | Global 6, world 5, provider 4 |
| `geology_mode` | `geome`, `legacy` | Sky/geome engine or Cyano legacy engine |
| `place_fluid_deposits` | boolean | Master switch for configured fluid-deposit rules |
| `manage_vanilla_ores` | boolean | Lets OreSpawn suppress and replace claimed vanilla ore features |
| `suppress_all_ore_features` | boolean | Suppresses all standard Forge ore features; use only in complete packs |
| `default_template` | registry ID or empty string | Template selected for newly created server worlds |
| `formations` | object | Shape controls used only when terrain strata are active |
| `rocks` | object keyed by rule ID | Eligible rock definitions |
| `geomes` | object keyed by geome ID | Geological province weights |
| `biomes` | object keyed by biome ID | Explicit biome-to-geome weights |
| `biome_dictionary` | object keyed by Forge biome type | Fallback biome-to-geome weights |
| `terrain_dimensions` | object keyed by dimension ID | Dimensions and hosts eligible for terrain replacement |
| `biome_palettes` | object keyed by provider-owned rule ID | Optional native-biome overlays and surfaces |
| `dimension_materials` | object keyed by provider-owned rule ID | Aquifer fluid, snow, and ice substitutions |
| `ores` | object keyed by rule ID | Ore outputs and per-dimension placement |
| `fluid_deposits` | object keyed by rule ID | Provider-owned fluids and per-dimension placement |
| `retrogen` | object | Bounded ore retrogen controls |
| `flat_bedrock` | object | Opt-in flat bedrock controls |
| `worldgen_aliases` | ID-to-ID object | Replacement output aliases resolved while baking |

## Enumerated Values

| Setting | Accepted values |
|---|---|
| Formation algorithm | `stable_layers`, `sky_v1` |
| Formation preset | `tiny`, `small`, `average`, `large`, `huge`, `custom` |
| Rock family | `sedimentary`, `metamorphic`, `igneous_intrusive`, `igneous_volcanic` |
| Ore pattern | `default`, `vein`, `normal_cloud`, `precision`, `clusters`, `underfluids` |
| Legacy pattern aliases | `cluster`, `cloud` |
| Height distribution | `uniform`, `triangle`, `bottom_triangle`, `uniform_bottom_triangle` |
| Biome placement mode | `augment`, `replace` |
| Biome replacement scope | `all`, `minecraft_only`, `selected_namespaces` |
| Biome region size | `tiny`, `small`, `average`, `large`, `huge` |

`sky_v1` exists to preserve migrated worlds. Use `stable_layers` for new packs.

## Formations

Each of `horizontal_size`, `vertical_thickness`, `waviness`,
`edge_irregularity`, and `formation_continuity` accepts any formation preset.
When a control is `custom`, its value comes from `formations.custom`:

| Custom field | Range | Purpose |
|---|---:|---|
| `stratum_wavelength` | 16-8192 | Horizontal persistence of formations |
| `family_region_wavelength` | 16-8192 | Scale of broad family provinces |
| `vertical_thickness` | 1-255 | Typical layer thickness |
| `waviness_wavelength` | 32-2048 | Horizontal distance over which layers bend |
| `waviness_amplitude` | 0-512 | Maximum broad vertical displacement |
| `edge_wavelength` | 8-512 | Scale of boundary detail |
| `edge_amplitude` | 0-256 | Strength of boundary detail |
| `edge_octaves` | 1-8 | Number of boundary-detail scales |
| `continuity` | 0-1 | Proportion of formations retaining global identity |

For Stable Layers, the Edge Detail presets use these
`wavelength / amplitude / octaves` values:

| Preset | Edge detail |
|---|---:|
| Tiny | `48 / 4 / 1` |
| Small | `64 / 12 / 2` |
| Average | `96 / 24 / 3` |
| Large | `128 / 48 / 4` |
| Huge | `192 / 96 / 5` |

Average is calibrated to retain visible variation at later layer contacts.
Custom profiles keep their explicit values; these numbers are only used by the
named presets and as defaults for new Custom settings.

Cyano settings use `cyano.geome_size` (4-32767),
`cyano.rock_layer_noise` (1-32767), and `cyano.rock_layer_thickness` (1-255).
They are ignored by Sky.

Profiles created from a legacy Mineralogy world also retain
`cyano.enabled`, `cyano.realistic_coal_layers`, the three effective
`*_rocks` arrays, the six original `*_whitelist`/`*_blacklist` arrays, and
source/version fields. These are migration snapshots, not new settings that a
fresh pack needs to author.

## Rocks And Geomes

A rock requires `enabled`, `family`, `depth_peak`, `depth_spread`, `min_y`,
`max_y`, `weight`, and `ore_replaceable`. Provider definitions should also set
`block`; global/world entries may use the rule ID as the block ID when `block`
is omitted. `dimensions` limits membership, and `geomes` multiplies selection
weight by province. A weight of zero prevents selection in that context.

Geomes contain a non-negative `base` weight and non-negative weights for each
rock family. Biome and biome-dictionary maps multiply those geome weights.
Missing optional-mod biome IDs are ignored during baking.
Exact biome-ID maps remain effective when the target uses a dynamic biome
registry. With Stable Layers, a close contest between two geomes transitions
at a deterministic position per layer so the whole underground column does
not change on one sheer plane.

Terrain dimensions require `enabled`, `host_blocks`, and `host_tags`.
`biome_ids` and `biome_namespaces` can narrow a custom dimension. The Overworld
is conventional but not automatic; Nether and End terrain remain untouched
unless a profile explicitly opts them in.

## Biome Palettes And World Materials

Biome palettes are independent of rock strata. Each palette names a
`dimension`, placement `mode`, replacement `scope`, `region_size`, `coverage`,
`fallback_weight`, optional namespace filters, and one or more weighted biome
entries. Region presets are 128, 256, 512, 1024, and 2048 blocks.

`augment` keeps the source biome as a weighted fallback. `replace` selects only
eligible palette biomes. Scope controls which source namespaces may be changed:
`minecraft_only` protects modded biomes by default, `selected_namespaces`
requires `include_namespaces`, and `all` permits every namespace except those
in `exclude_namespaces`.

Each biome entry may set `similar_biomes`, `required_similar_biomes`,
temperature/downfall ranges, and a surface object. Optional similar biomes are
ignored when absent. If a required similar biome is absent, that output entry
is disabled with one setup warning. Surface fields are `top_block`,
`filler_block`, `underwater_block`, `ceiling_block`, and `filler_depth`.

Dimension-material rules may set `default_fluid`, `deep_aquifer_fluid`,
`deep_aquifer_max_y`, `snow_block`, and `ice_block`. Fluid IDs must resolve to
blocks with non-empty fluid states. These substitutions are opt-in; a dimension
with no matching rule retains its native generator and weather materials.
See `BIOMES.md` for complete examples and practical guidance.

## Ore Fields

An ore has `enabled`, one output `block` or weighted `outputs`, and at least one
entry in `dimensions` or `dimension_selectors`. Optional fields include `native_generation`,
`suppress_vanilla`, `retrogen`, `deep_output`, and `deep_output_max_y`.

Each enabled ore dimension uses:

| Field | Range/default | Meaning |
|---|---|---|
| `min_y`, `max_y` | -2048 to 2048 | Inclusive placement range |
| `frequency` | 0-64 | Expected attempts per chunk |
| `quantity` | 1-64 | Fixed block budget for each attempt |
| `min_quantity`, `max_quantity` | 1-64 | Inclusive random block-budget range; both fields are required |
| `pattern` | pattern name or codec object | Deposit shape |
| `height_distribution` | one of four values | Vertical probability curve |
| `discard_chance_on_air_exposure` | 0-1 | Chance to omit candidates touching cave air |
| `spread` | 0-64 | Horizontal pattern reach |
| `vertical_spread` | 0-64 | Vertical pattern reach |
| `node_size` | 1-32 | Cluster node size |
| `length` | 1-64 | Pattern path length where supported |
| `fluid` | registry ID | Fluid used by `underfluids` |

At least one of `host_families`, `host_blocks`, or `host_tags` must be present.
Hosts may be plain registry IDs or weighted objects such as
`{"tag":"minecraft:stone_ore_replaceables","weight":0.75}`. Optional
`geomes`, biome include/exclude IDs, and biome-dictionary include/exclude arrays
further narrow placement.

`frequency` is expected attempts per chunk: the integer part is guaranteed and
the fractional part is the chance of one additional attempt. A fixed
`quantity` or sampled quantity range is a placement budget, not a promise that
every candidate finds a valid host. A complete range overrides `quantity` if
both are present.

The selector `orespawn:all_except_nether_end` covers every dimension except
the vanilla Nether and End. Explicit rules in `dimensions` override selector
rules for the same ore and dimension, including explicit disabled rules.

## Fluid Deposits, Retrogen, And Bedrock

Each `fluid_deposits` entry has a stable provider-namespaced rule ID, an
`enabled` flag, one output fluid `block`, and one or more `dimensions`. The
output must resolve to a non-air block whose default state has a non-empty
fluid state. OreSpawn does not provide a default water, lava, or oil rule.
Players who enable standalone rock strata can create a world-owned rule from
the **Fluid Deposits** screen by choosing any installed fluid block. The UI
starts it as a covered Overworld deposit and keeps every value editable.

An enabled dimension supports `min_y`, `max_y`, `frequency`, `min_radius`,
`max_radius`, `min_vertical_radius`, `max_vertical_radius`, `max_lobes`, and
`min_solid_cover`. `min_solid_shell` defaults to `1` and requires that many
solid blocks around the sides and underside of each generated lobe; the larger
of it and `min_solid_cover` is used above the lobe. A candidate that intersects
cave air is rejected before any fluid is written. The rule also requires at
least one `host_families`, `host_blocks`,
or `host_tags` entry. Optional `biome_ids`, `excluded_biome_ids`,
`biome_dictionary`, `excluded_biome_dictionary`, and `geomes` narrow the rule.
`frequency` uses the same expected-attempts-per-chunk meaning as ores.

`retrogen.enabled` is off by default. `revision` is a non-negative marker,
`force` deliberately revisits marked chunks, and `chunks_per_tick` is 1-16.
Only ore rules with `retrogen:true` participate. Terrain strata are never
retro-generated.

`flat_bedrock.enabled` and `flat_bedrock.retrogen` are off by default.
`layers` is 1-5 and `dimensions` is an array of full dimension IDs.

## Validation And Server Copying

Registry IDs use `namespace:path`, for example `minecraft:calcite`. Validate
files with the schemas in `schemas/` and compare them with `examples/`.
Enabled fluid outputs must additionally resolve to real non-air fluid blocks,
and each enabled fluid dimension must have a valid host rule. Missing blocks,
ordinary solid blocks, and hostless rules are rejected before generation.

Copying a world's `serverconfig/orespawn-worldgen.json` to the same location in
a dedicated server world reproduces its choices when the same referenced mods,
blocks, biomes, dimensions, and tags are installed.
