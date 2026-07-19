# Worldgen Providers

Provider mods may contribute through Forge IMC, a packaged resource at
`data/<provider-modid>/orespawn/provider.json`, or a pack override at
`config/<provider-modid>-orespawn.json`. A valid override is authoritative. A
present malformed override leaves that provider inactive instead of silently
falling back.

Provider schema 2 supports `profile_defaults`, `rocks`, `ores`, `geomes`,
`biome_rules`, `terrain_dimensions`, and `templates`. Each file requires a
matching `provider_modid`, a positive `provider_revision`, and at least one
contribution. Legacy schema 1 remains accepted for ore-only providers.

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
  "dimensions": {}
}
```

An enabled ore dimension requires a Y range, expected attempts per chunk in
`frequency`, a block budget in `quantity`, and at least one host family, host
block, or host tag. Host arrays accept either registry-ID strings or weighted
objects such as `{ "block": "minecraft:stone", "weight": 1.0 }` and
`{ "tag": "forge:stone", "weight": 0.5 }`. Biome include/exclude IDs and
Forge biome-dictionary names may further restrict a rule.

Only suppress a provider mod's native ore generation when
`OreSpawnApi.isOreTakeoverActive(modid)` returns true. `PENDING` means discovery
has not frozen. `INACTIVE` is the fail-safe and native generation must remain.

Existing worlds merge newly introduced provider rule IDs but do not overwrite
world edits. Disabled and unassigned rules remain tombstones; removed provider
rules remain in the self-contained snapshot.

See `examples/examplemod-orespawn.json` for rocks, weighted ore output, a
custom dimension, and a selectable template.
