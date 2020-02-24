[![](https://img.shields.io/badge/Discord-MMD-green.svg?style=flat&logo=Discord)](https://discord.mcmoddev.com)
[![](https://cf.way2muchnoise.eu/full_mmd-orespawn_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/mmd-orespawn)
[![](https://cf.way2muchnoise.eu/versions/Minecraft_mmd-orespawn_all.svg)](https://www.curseforge.com/minecraft/mc-mods/mmd-orespawn)
[![Build Status](https://ci.mcmoddev.com/job/OreSpawn/job/OreSpawn%201.12/badge/icon)](https://ci.mcmoddev.com/job/OreSpawn/job/OreSpawn%201.12/)

# OreSpawn
Minecraft library mod that provides better control over the spawning of ores in Minecraft.
If you're looking for a place to report bugs for the mod that adds extra mobs, the "DangerZone", etc... go [to the site for that mod](http://www.orespawn.com/) as this is not it.

## How it works
Ore Spawn parses all of the .json files found in `orespawn` and adds ore generators to the game based on those files. The JSON structure looks like this:

```json
[
  {
    "dimension": -1,
    "ores": [
      {
        "block": "minecraft:quartz_ore",
        "size": 15,
        "variation": 4,
        "frequency": 7,
        "min_height": 0,
        "max_height": 128
      }
    ]
  },
  {
    "ores": [
      {
        "block": "minecraft:coal_ore",
        "size": 25,
        "variation": 12,
        "frequency": 20,
        "min_height": 0,
        "max_height": 128
      },
      {
        "block": "minecraft:iron_ore",
        "size": 8,
        "variation": 4,
        "frequency": 20,
        "min_height": 0,
        "max_height": 64
      },
      {
        "block": "minecraft:gold_ore",
        "size": 8,
        "variation": 2,
        "frequency": 2,
        "min_height": 0,
        "max_height": 32
      },
      {
        "block": "minecraft:diamond_ore",
        "size": 6,
        "variation": 3,
        "frequency": 8,
        "min_height": 0,
        "max_height": 16
      },
      {
        "block": "minecraft:lapis_ore",
        "size": 5,
        "variation": 2,
        "frequency": 1,
        "min_height": 0,
        "max_height": 32
      },
      {
        "block": "minecraft:emerald_ore",
        "size": 1,
        "variation": 0,
        "frequency": 8,
        "min_height": 4,
        "max_height": 32,
        "biomes": [
          "minecraft:extreme_hills",
          "minecraft:smaller_extreme_hills"
        ]
      },
      {
        "block": "minecraft:dirt",
        "size": 112,
        "variation": 50,
        "frequency": 10,
        "min_height": 0,
        "max_height": 255
      },
      {
        "block": "minecraft:gravel",
        "size": 112,
        "variation": 50,
        "frequency": 8,
        "min_height": 0,
        "max_height": 255
      },
      {
        "block": "minecraft:stone",
        "state": "variant=granite",
        "size": 112,
        "variation": 50,
        "frequency": 10,
        "min_height": 0,
        "max_height": 255
      },
      {
        "block": "minecraft:stone",
        "state": "variant=diorite",
        "size": 112,
        "variation": 50,
        "frequency": 10,
        "min_height": 0,
        "max_height": 255
      },
      {
        "block": "minecraft:stone",
        "state": "variant=andesite",
        "size": 112,
        "variation": 50,
        "frequency": 10,
        "min_height": 0,
        "max_height": 255
      }
    ]
  }
]
```

### dimension
The number ID of a dimension. Don't specify any dimension to target all dimensions *that are not already specified*.
### ores
Array of JSON objects specifying ore generators for this dimension
### block
Text ID of a block (the same you would use in the /give command)
### state
The state of a block (typically used for colored blocks)
### size
The number of blocks to spawn. Unlike the default Minecraft world settings JSON, this is the actually number of blocks that will spawn.
### variation
How much to randomly vary the number of blocks spawned (I recommend making this value 50% of the *size* value)
### frequency
How often, per chunk, to attempt to spawn this ore block. This value can be a fraction less than 1. If this value is between 0 and 1, then not every chunk will have a spawn in it. For example, a frequency of 0.1 means that there will be one attempt to spawn the ore per 10 chunks.
### min_height
The lowest Y-coordinate that the ore is allowed to spawn at
### max_height
The highest Y-coordinate that the ore is allowed to spawn at
### biomes
If this array is not empty, then the biomes in which the ore will spawn is restricted to those specified by ID in this array.

# API
Adding OreSpawn support to your mod is not hard. Look at `VanillaOreSpawn.java` for an example.
