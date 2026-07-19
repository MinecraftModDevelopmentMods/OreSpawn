# Troubleshooting

## Provider remains inactive

Check that the provider mod is loaded, file name and `provider_modid` match,
`provider_revision` is positive, provider rule IDs use its namespace, all
output blocks resolve, all pattern codecs decode, and every enabled ore or
terrain dimension has hosts. A malformed override deliberately prevents
fallback to packaged or API declarations.

## OreSpawn installed alone does nothing

That is the intended passive default. Install a provider such as Mineralogy,
add a provider file, or explicitly configure rocks/ores and terrain dimensions.

## Custom terrain does not appear

Confirm the dimension is enabled in `terrain_dimensions`, its hosts resolve,
at least one enabled rock includes that dimension, and biome restrictions
match. OreSpawn does not replace Nether or End terrain by default.

## Changes do not affect terrain

Restart after editing JSON or changing provider/API declarations. Travel to
new chunks. Ore retrogen and flat-bedrock retrogen must be explicitly enabled;
geological strata are never retrogened.

## Server differs from a client test world

Copy `<world>/serverconfig/orespawn-worldgen.json`, not merely the global
client config. Install the same provider mods and blocks on the server.

## Native ores duplicate

Provider mods suppress native generation only after
`OreSpawnApi.isOreTakeoverActive(modid)` is true. Keep native generation for
`PENDING` and `INACTIVE`. For pack-wide vanilla or modded suppression, review
`manage_vanilla_ores` and `suppress_all_ore_features` carefully.

## Operator diagnostics

- `/orespawn status` shows active mode, rule/provider counts, and retrogen queue.
- `/orespawn reload` reloads providers and the active world profile.
- `/orespawn retrogen [radius]` queues currently loaded chunks only.
- `/orespawn dump-biomes` writes `config/orespawn-biomes.txt`.
