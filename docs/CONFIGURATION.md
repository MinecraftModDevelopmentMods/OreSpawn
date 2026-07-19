# Configuration

OreSpawn uses three JSON contracts:

| File | Schema | Purpose |
|---|---:|---|
| `config/orespawn-worldgen.json` | 4 | Installed-pack defaults |
| `<world>/serverconfig/orespawn-worldgen.json` | 3 | Self-contained world snapshot |
| `config/<modid>-orespawn.json` | 2 | Optional provider override |

A provider can also package the same schema at
`data/<modid>/orespawn/provider.json`. The effective profile for a new world is
assembled in this order: passive OreSpawn factory defaults, packaged or API
provider additions, provider override files, global pack configuration, the
selected template, then edits made in Create World. The result is saved into
the world.

The global and world files configure formations, rocks, geomes, biome rules,
terrain dimensions, ores, oil, aliases, vanilla suppression, retrogen, and
flat bedrock. Registry references use full IDs such as `minecraft:calcite`.

Important top-level controls:

- `manage_vanilla_ores`: suppress only vanilla ore features claimed by active
  configured rules.
- `suppress_all_ore_features`: wrap and suppress all standard Forge ore
  features while OreSpawn rules run. Use this only for deliberately complete
  packs.
- `retrogen.enabled`: generate rules with `retrogen:true` in loaded chunks
  whose revision marker differs. `chunks_per_tick` is clamped to 1-16.
- `flat_bedrock.enabled`: flatten 1-5 bottom layers in listed dimensions and
  the Nether ceiling. `flat_bedrock.retrogen` uses the same bounded queue.

Copying the world profile into the same `serverconfig` location in a dedicated
server world reproduces the settings when all referenced mods and blocks are
installed. Restart after manual edits. Normal profile changes affect newly
generated chunks; enabled ore/bedrock retrogen is the only explicit exception.

See `schemas/orespawn-global.schema.json`,
`schemas/orespawn-world.schema.json`, and `examples/`.
