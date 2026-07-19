# Ore Patterns And Runtime Features

Every ore dimension selects one pattern. Legacy string values remain accepted;
the namespaced form is preferred.

| Pattern | ID | Shape |
|---|---|---|
| Compact | `orespawn:default` | Dense rounded deposit |
| Vein | `orespawn:vein` | Wandering connected vein |
| Normal cloud | `orespawn:normal_cloud` | Diffuse bounded cloud |
| Precision | `orespawn:precision` | Deterministic compact fill |
| Clusters | `orespawn:clusters` | Multiple nearby nodes |
| Under fluids | `orespawn:underfluids` | Deposit beneath configured fluid |

Built-in settings are `spread` (0-64), `vertical_spread` (0-64), `node_size`
(1-32), `length` (1-64), and a fluid registry ID. The legacy flat fields and
the codec object below are equivalent:

```json
"pattern": {
  "type": "orespawn:clusters",
  "settings": {
    "spread": 12,
    "vertical_spread": 5,
    "node_size": 3,
    "length": 20,
    "fluid": "minecraft:water"
  }
}
```

Other mods may register `OrePatternType` values in the Forge registry named by
`OreSpawnPatternRegistry.REGISTRY_NAME`. Each type supplies a Mojang `Codec`
and compiles decoded settings into a `CompiledOrePattern`. Compilation occurs
during profile baking. The generation loop invokes only the compiled object.
Third-party codec settings are preserved and shown read-only in OreSpawn's UI.

Height selection supports `uniform` and `triangle`. `frequency` is expected
attempts per chunk: the integer part is guaranteed and the fractional part is
the chance of one additional attempt. `quantity` is the placement budget for
each attempt.

Retrogen records a deterministic profile revision in chunk NBT under
`OreSpawn`. Only ore rules with `retrogen:true` participate. Processing is
bounded by `chunks_per_tick`; no terrain strata retrogen exists.

Flat bedrock is disabled by default. When enabled it flattens the configured
number of bottom layers and, in the Nether, the ceiling. It uses normal Forge
features and chunk events, with no reflection.
