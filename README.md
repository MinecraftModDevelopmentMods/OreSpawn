# MMD OreSpawn 4.x
A re-imagining of an old MMD Mod for modern Minecraft. Now we just provide the feature placers and let the existing 
datapack infrastruture handle the rest.

Work Started: 19-AUG-2024 D. Hazelton <dshadowwolf@gmail.com>
Beta Work Completed: 29-JUL-2025 D. Hazelton <dshadowwolf@gmail.com>

## How it works
This version of MMD OreSpawn adds a set of generators that can be used similar to the vanilla ones. In fact you use 
them exactly the same, by putting the right ID in the right JSON file in a data pack. The following example replaced
the standard coal spawn as part of testing the code and uses the `vein` feature generator:
```json
{
  "type": "mmdorespawn:mmdos4_vein",
  "config": {
    "discard_chance_on_air_exposure": 0.5,
    "size": 17,
    "min_length": 100,
    "max_length": 200,
    "frequency": 1.0,
    "targets": [
      {
        "state": {
          "Name": "minecraft:coal_ore"
        },
        "target": {
          "predicate_type": "minecraft:tag_match",
          "tag": "minecraft:stone_ore_replaceables"
        }
      },
      {
        "state": {
          "Name": "minecraft:deepslate_coal_ore"
        },
        "target": {
          "predicate_type": "minecraft:tag_match",
          "tag": "minecraft:deepslate_ore_replaceables"
        }
      }
    ]
  }
}
```


