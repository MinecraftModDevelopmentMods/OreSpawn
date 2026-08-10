# Ore Patterns And Runtime Features

Every ore dimension selects one pattern. Legacy string values remain accepted;
the namespaced form is preferred.

| Pattern | ID | Shape |
|---|---|---|
| Compact | `orespawn:default` | One dense, face-connected deposit |
| Vein | `orespawn:vein` | Wandering chain of connected nodes |
| Normal cloud | `orespawn:normal_cloud` | Diffuse bounded cloud |
| Precision | `orespawn:precision` | Deterministic compact fill |
| Clusters | `orespawn:clusters` | Multiple nearby face-connected nodes |
| Under fluids | `orespawn:underfluids` | Connected deposit beneath configured fluid |

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
On Forge 14, attach a generic `RegistryEvent.Register<OrePatternType>` listener
to the mod event bus and register the named type through the event registry.

Height selection supports `uniform`, centre-peaked `triangle`, deep-biased
`bottom_triangle`, and a half-uniform `uniform_bottom_triangle`. `frequency`
is expected attempts per chunk: the integer part is guaranteed and the
fractional part is the chance of one additional attempt. `quantity` is a fixed
placement budget; `min_quantity` and `max_quantity` define an inclusive random
budget. `orespawn:all_except_nether_end` preserves OS3-style ordinary custom
dimension coverage, with explicit dimensions taking precedence.
`discard_chance_on_air_exposure` is a
number from 0 to 1; selected placements touching air are rejected with that
probability. It can reproduce buried ore behavior without reducing deposits
that remain enclosed in rock.

Compact nodes use one of 48 pre-baked orientations. Every prefix from 1 to 64
blocks is face-connected when the host material is continuous. During initial
generation deposits may cross chunk borders through Minecraft's writable
worldgen region; retrogen remains deliberately limited to the chunk being
updated.

Retrogen records a deterministic profile revision in chunk NBT under
`OreSpawn`. Only ore rules with `retrogen:true` participate. Processing is
bounded by `chunks_per_tick`; no terrain strata retrogen exists.

Flat bedrock is disabled by default. When enabled it flattens the configured
number of bottom layers and, in the Nether, the ceiling. It uses normal Forge
features and chunk events, with no reflection.
