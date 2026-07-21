# Geology Templates

Templates are named profile overlays supplied by providers. They may set
formation presets, rocks, geomes, biome rules, ores, fluid deposits, suppression,
retrogen, flat bedrock, and terrain dimensions. A template may reference any
installed registry ID and list `required_mods`.

Templates never activate merely because a provider is installed. Players
select one in OreSpawn's Create World screen. Dedicated servers may set
`default_template` in `config/orespawn-worldgen.json`.

Template application happens after the global pack configuration and before
world-creation edits. The selected result is copied into the world profile.
Provider template changes never rewrite an existing world.

Use namespaced template IDs such as `examplemod:ancient_sea`. Translation keys
for the selector belong to the provider resource pack.
