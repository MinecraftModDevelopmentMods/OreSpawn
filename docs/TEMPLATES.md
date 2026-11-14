# Geology Templates

Templates are named profile overlays supplied by providers. They may set
formation presets, rocks, geomes, biome rules, ores, fluid deposits, suppression,
retrogen, flat bedrock, and terrain dimensions. A template may reference any
installed registry ID and list `required_mods`.

Templates normally activate only when players select one in OreSpawn's Create
World screen. A total-conversion provider may mark a template
`auto_select:true` and set `auto_select_priority`. That template becomes the
default only for a fresh world when the global profile does not already name
`default_template`. The highest priority wins; equal priorities use lexical
template ID order and produce a setup warning. An explicit pack/server default
always wins.

Template application happens after the global pack configuration and before
world-creation edits. The selected result is copied into the world profile.
Provider template changes never rewrite an existing world.

The editor always applies template changes to the merged pack/provider baseline,
not on top of the previous template. Switching templates therefore cannot leave
stale rocks, biomes, fluids, or materials behind. Existing world snapshots never
auto-switch after installation or provider updates.

Use namespaced template IDs such as `examplemod:ancient_sea`. Translation keys
for the selector belong to the provider resource pack.
Templates may set `preserve_formation_overrides: true` when tuning stock
formations. This keeps authored world edits, disabled rules, deliberate
removals and external provider files intact. Without the option, template
overlays retain their existing behavior.
