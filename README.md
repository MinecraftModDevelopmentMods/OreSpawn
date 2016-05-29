# OreSpawn
Minecraft library mod that provides better control over the spawning of ores in Minecraft.

## How it works
Ore Spawn parses all of the .json files found in **config/orespawn** and adds ore generators to the game based on those files. The JSON structure looks like this:
```javascript
{
	"dimensions":[
		{
			"dimension":"+",
			"ores":[
				{
					"blockID":"minecraft:coal_ore",
					"size":25,
					"variation":12,
					"frequency":20,
					"minHeight":0,
					"maxHeight":128,
					"biomes":[

					]
				},
				{
					"blockID":"minecraft:emerald_ore",
					"size":1,
					"variation":0,
					"frequency":8,
					"minHeight":4,
					"maxHeight":32,
					"biomes":[
						"Extreme Hills Edge",
						"Extreme Hills"
					]
				},
				{
					"__comment":"andesite",
					"blockID":"minecraft:stone",
					"blockMeta":5,
					"size":112,
					"variation":50,
					"frequency":10,
					"minHeight":0,
					"maxHeight":255,
					"biomes":[

					]
				}
			]
		},
		{
			"dimension":-1,
			"ores":[
				{
					"blockID":"minecraft:quartz_ore",
					"size":15,
					"variation":4,
					"frequency":7,
					"minHeight":0,
					"maxHeight":128,
					"biomes":[

					]
				}
			]
		}
	]
}
```
### dimensions
Array of JSON objects, one for each dimension specified in this file
### dimension
The number ID of a dimension, or "+" to add to all dimensions *that are not already specified*.
### ores
Array of JSON objects specifying ore generators for this dimension
### blockID
Text ID of a block (the same you would use in the /give command)
### blockMeta
The variant number (aka damage value) of a block (typically used for colored blocks)
### size
The number of blocks to spawn. Unlike the default Minecraft world settings JSON, this is the actually number of blocks that will spawn.
### variation
How much to randomly vary the number of blocks spawned (I recommend making this value 50% of the *size* value)
### frequency
How often, per chunk, to attempt to spawn this ore block. This value can be a fraction less than 1. If this value is between 0 and 1, then not every chunk will have a spawn in it. For example, a frequency of 0.1 means that there will be one attempt to spawn the ore per 10 chunks.
### minHeight
The lowest Y-coordinate that the ore is allowed to spawn at
### maxHeight
The highest Y-coordinate that the ore is allowed to spawn at
### biomes
If this array is not empty, then the biomes in which the ore will spawn is restricted to those specified by either name or biome ID number in this array.

# API
To use Ore Spawn with your mod, simply have your mod create a .json file in the **config/orespawn** (e.g. *config/orespawn/mymod.json*) in the pre-init or init phase of mod loading. You do not need to call any of the functions in the Ore Spawn .jar directly.

