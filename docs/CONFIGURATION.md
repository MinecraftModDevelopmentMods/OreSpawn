# Configuration Reference

OreSpawn uses three JSON contracts:

| File | Schema | Purpose |
|---|---:|---|
| `config/orespawn-worldgen.json` | 8 | Installed-pack defaults for new worlds |
| `<world>/serverconfig/orespawn-worldgen.json` | 7 | Self-contained snapshot for one world |
| `config/<modid>-orespawn.json` | 5 | Optional authoritative provider override |

A provider may package schema 5 at `assets/<modid>/orespawn/provider.json`.
Legacy provider schemas 1-4 remain accepted. Fluid deposits require schema 3;
biome palettes and dimension materials require schema 4; ore `material` and
`placement_channel` declarations require schema 5.

The profile for a new world is merged in this order: passive OreSpawn defaults,
packaged or API providers, provider override files, the global configuration,
the selected template, and Create World edits. The result is saved with the
world. Restart after editing JSON by hand.

## Top-Level Fields

| Field | Values | Meaning |
|---|---|---|
| `schema_version` | Contract-specific integer | Global 8, world 7, provider 5 |
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
| `ore_material_groups` | object keyed by stable group ID | Friendly material names and exact Ore Dictionary aliases |
| `ore_source_policies` | object keyed by material and dimension/domain | Persisted output and placement arbitration choices |
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
Migrated Mineralogy worlds also store `cyano.enabled`, the exact ordered
`cyano.igneous_rocks`, `cyano.metamorphic_rocks`, and
`cyano.sedimentary_rocks` arrays, plus `cyano.realistic_coal_layers` for the
Mineralogy 1.10 lineage. Native Mineralogy 1.12 did not have realistic coal;
its `PLACE_MINERALOGY_ROCK=false` is preserved as `cyano.enabled=false`.
These values and the old family white/blacklists are snapshotted per world and
are ignored by Sky.

The resulting values and missing registry IDs are recorded in
`<world>/serverconfig/orespawn-upgrade-report.txt`. OS3 rule and global-switch
imports are summarized in `config/orespawn-upgrade-report.txt` and retained in
machine-readable form at `config/orespawn-os3-migration-report.json`.

## Rocks And Geomes

A rock requires `enabled`, `family`, `depth_peak`, `depth_spread`, `min_y`,
`max_y`, `weight`, and `ore_replaceable`. Provider definitions should also set
`block`; global/world entries may use the rule ID as the block ID when `block`
is omitted. `dimensions` limits membership, and `geomes` multiplies selection
weight by province. A weight of zero prevents selection in that context.

`min_y` and `max_y` are inclusive actual-world height limits. Stable Layers
may shift a layer vertically to preserve its formation, family, and lithology
identity, but that shifted coordinate never makes an out-of-range world block
eligible or rejects an otherwise legal world height.

Geomes contain a non-negative `base` weight and non-negative weights for each
rock family. Keys may retain the legacy unnamespaced form or use a provider
resource ID such as `examplemod:crystal_basin`; the creation editor preserves
both forms. Biome and biome-dictionary maps multiply those geome weights.
Missing optional-mod biome IDs are ignored during baking.

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

Dimension-material rules apply to every biome in their selected dimension and
may set `default_fluid`, `deep_aquifer_fluid`, `deep_aquifer_max_y`,
`snow_block`, and `ice_block`. Fluid IDs must resolve to blocks with non-empty
fluid states. These substitutions are opt-in, affect only newly generated
terrain and perform no retrogen; a dimension with no matching rule retains its
native generator and weather materials.

For a `default_fluid` with the native fluid's opacity and emitted light,
OreSpawn records the exact native aquifer cells during terrain construction and
substitutes only those cells before decoration. Later lakes, springs and fluids
placed by decorators are not included. A fluid with different lighting uses a
slower direct-generator compatibility path so lighting remains correct.
Unsupported independent chunk generators are detected and left unchanged.

Minecraft 1.12.2 exposes one generator fluid, so this branch applies
`default_fluid` only. It retains `deep_aquifer_fluid` and
`deep_aquifer_max_y` in provider and world profiles for cross-version
portability, but the editor keeps those controls disabled and generation does
not use a distinct deep fluid.
See `BIOMES.md` for complete examples and practical guidance.

### Biome directory overrides

The editor reads all `biome_palettes` entries for a dimension in their stored
order. It does not rewrite or publicly prioritize ordinary palettes. Exact
one-to-one user replacements are encoded as
`orespawn:ui/biome_overrides/<dimension>` using the existing palette schema:
`mode: replace`, `scope: all`, `coverage: 1.0`, `fallback_weight: 0.0`, with
each target's exact sources in `similar_biomes`. OreSpawn always bakes this
reserved palette after the ordinary entries regardless of JSON order.

Only loaded biomes can be selected in the GUI, although an already saved rule
whose target becomes unavailable is retained. That dormant rule leaves the
source unchanged and automatically becomes active when the target is loaded
again. Replacement chains are stored as terminal mappings; self-replacements
and cycles are invalid. These rules control new terrain only and do not disable
external biome generators, decorators, structures, mobs or registry entries.

Biome reset operations use an internal immutable snapshot of currently loaded
provider defaults. A biome reset restores that biome's active provider entries;
a palette reset restores the matching active provider palette; dimension/all
resets remove user/profile-only biome palettes and restore active provider
palettes and materials. Definitions owned by providers which are currently
missing remain untouched. The pending editor copy is written to global defaults
or the world profile only when the main editor's **Done** action succeeds.

## Ore Fields

An ore has `enabled`, one output `block` or weighted `outputs`, and at least one
entry in `dimensions` or `dimension_selectors`. Optional fields include
`material`, `native_generation`, `suppress_vanilla`, `retrogen`, `deep_output`,
and `deep_output_max_y`. Schema 5 `material` is a canonical material ID such as
`orespawn:sulfur`; it lets several rules share outputs without sharing placement
budgets.

Each enabled ore dimension uses:

| Field | Range/default | Meaning |
|---|---|---|
| `min_y`, `max_y` | 0-255 in the 1.12.2 editor | Inclusive placement range; metadata block states remain separate from height |
| `frequency` | 0-64 | Expected attempts per chunk |
| `quantity` | 1-64 | Fixed block budget for each attempt |
| `min_quantity`, `max_quantity` | 1-64 | Inclusive random block-budget range; both fields are required |
| `pattern` | pattern name or codec object | Deposit shape |
| `placement_channel` | registry ID | Independent placement engine; built-ins default to `orespawn:standard`, custom patterns to their pattern-type ID |
| `height_distribution` | one of four values | Vertical probability curve |
| `discard_chance_on_air_exposure` | 0-1 | Chance to omit candidates touching cave air |
| `spread` | 0-64 | Horizontal pattern reach |
| `vertical_spread` | 0-64 | Vertical pattern reach |
| `node_size` | 1-32 | Cluster node size |
| `length` | 1-64 | Pattern path length where supported |
| `fluid` | registry ID | Fluid used by `underfluids` |

At least one of `host_families`, `host_blocks`, or `host_tags` must be present.
Hosts may be plain registry IDs or weighted objects such as
`{"tag":"forge:stone","weight":0.75}`. Optional
`geomes`, biome include/exclude IDs, and biome-dictionary include/exclude arrays
further narrow placement.

On Minecraft 1.12.2, a plain block ID accepts every metadata state belonging to
that block. To accept ordinary stone only, use
`{"block":"minecraft:stone","metadata":0}` in `host_blocks`; omitting
`metadata` also permits granite, diorite, and andesite states stored under the
same `minecraft:stone` block ID.

`frequency` is expected attempts per chunk: the integer part is guaranteed and
the fractional part is the chance of one additional attempt. A fixed
`quantity` or sampled quantity range is a placement budget, not a promise that
every candidate finds a valid host. A complete range overrides `quantity` if
both are present.

The selector `orespawn:all_except_nether_end` covers every dimension except
the vanilla Nether and End. Explicit rules in `dimensions` override selector
rules for the same ore and dimension, including explicit disabled rules.

## Ore Source Policies

OreSpawn builds one immutable source catalog while providers and the Forge Ore
Dictionary are being baked. Explicit `material` declarations win. Otherwise,
only exact Ore Dictionary names of the form `oreX` are inferred; curated
spelling aliases include sulfur/sulphur and aluminum/aluminium. Niter and
Saltpeter remain distinct, and ambiguous entries are marked **Review required**
instead of being guessed. Each ambiguous alias family receives its own stable
provisional group ID, so unrelated families cannot be combined merely because
both need review. Old shared `orespawn:review_required` data is split by fresh
discovery on load. Registry, dictionary and policy work never runs in a
chunk-generation loop.

Each `ore_material_groups` entry has a persistent registry-style group ID, a
`display_name`, and `ore_dictionary_entries`. Entries must be exact `oreX`
names and may belong to only one group. Renaming a group does not change its
ID. OreSpawn supplies Sulfur (`oreSulfur`, `oreSulphur`) and Aluminum
(`oreAluminum`, `oreAluminium`) groups; Niter and Saltpeter deliberately remain
separate. The UI can create custom groups and confirm moving an alias from
another group. An empty custom group can be deleted immediately; a populated
custom group requires a confirmed dissolution that returns each alias to its
deterministic inferred or curated group. Automatically discovered groups
cannot be deleted because they represent the loaded Ore Dictionary. Curated
Sulfur and Aluminum groups instead offer Reset Defaults.

Each `ore_source_policies` key combines the group ID with an exact dimension or
existing dimension-selector ID. `mode` is `consolidated` or `keep_separate`.
Consolidated policies also store `output_mode` as `balanced`, `single`, or
`custom`, selected output rule IDs with positive weights, and one active
placement-source rule per placement channel. Balanced uses equal weights,
Single has exactly one output, and Custom accepts any non-empty positively
weighted subset. The UI calls `keep_separate` **Keep Original**. Returning to
Keep Original restores the output weights and placement-source selections from
the profile snapshot taken when the editor was opened. If Keep Original itself
still needs review, **Accept** leaves those restored values unchanged and only
marks the policy reviewed. Like every Ore Sources edit, it is persisted only by
the main editor's **Done** action.

**Reset All** is a confirmed pending action. It removes custom material groups
and source-policy choices, restores the built-in aliases, and rediscovers the
inferred groups from the ores currently loaded. It does not rewrite generated
chunks. The reset reaches the world profile and future-world defaults only when
the main editor's **Done** action succeeds; its **Cancel** action discards it.

The Ore Sources screen keeps compact scrollable group and output lists visible
together. Its default view hides harmless one-alias, one-output entries; **Show
All** reveals them. Red groups need attention, yellow groups have a saved rule,
and green entries need no action. A saved consolidated rule or an explicitly
accepted Keep Original policy therefore clears a multi-output group's attention
state without hiding the resolved group. Use a
group's cog for its friendly name, Ore Dictionary aliases and **Placement
Rules**. Output selection controls which registered block represents
the material. Placement Rules separately control the managed rule that supplies
frequency, shape, depth and host restrictions for each channel. `orespawn:standard`
is shown as **Standard veins**; exact custom-channel IDs remain available in
tooltips. A sole active managed source is read-only, multiple active managed
sources are selectable, and independent external generators are never offered
as placement owners. Each row shows the selected source and the available
choice count. It cycles only in a consolidated mode; under Keep Original it is
informational because the original rules remain independent. The compact help
and row tooltips explain fixed, selectable, missing and inactive states without
reducing the alias-list height.

Native vanilla ore rules are active placement sources only when
`manage_vanilla_ores` is enabled. Changing that option refreshes the pending
Ore Sources snapshot before the editor opens. While it is disabled, Group
Settings refuses to move a native vanilla ore's Ore Dictionary alias into
another material group and explains that vanilla management must be enabled
first.

Consolidation runs the chosen placement rule's pattern, hosts, filters, height
and frequency once; selected candidates contribute output bundles only. An
inactive managed provider may therefore supply an output block without adding
a placement budget. Output choice is stable for a whole ordinary vein, and
custom patterns can supply a stable body identity so one deposit uses one
source across chunk boundaries. External Ore Dictionary members can be chosen
as outputs, but their independent native generators remain uncontrolled.

New worlds consolidate only reviewed high-confidence MMD catalog conflicts and
start those groups in Balanced mode with all eligible outputs selected.
Upgraded worlds initialize discovered conflicts as `keep_separate`, preserving
their historical placement frequency and outputs until changed through **Ore
Sources...**. Policy edits affect newly generated chunks only and never cause
retrogen. Missing selected candidates remain in the profile; remaining outputs
are reweighted, while a channel with no remaining selected placement source is
disabled and reported rather than silently reassigned.

DenseMetals entries are shown as enrichment rather than interchangeable output.
BaseSciences has no ordinary ore-source role. An Ore Dictionary member whose
generator is not controlled by OreSpawn is reported as external generation and
is never suppressed, so total abundance may still be increased by that mod.

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

Registry IDs use `namespace:path`, for example `minecraft:granite`. Validate
files with the schemas in `schemas/` and compare them with `examples/`.
Enabled fluid outputs must additionally resolve to real non-air fluid blocks,
and each enabled fluid dimension must have a valid host rule. Missing blocks,
ordinary solid blocks, and hostless rules are rejected before generation.

Copying a world's `serverconfig/orespawn-worldgen.json` to the same location in
a dedicated server world reproduces its choices when the same referenced mods,
blocks, biomes, dimensions, and tags are installed.
