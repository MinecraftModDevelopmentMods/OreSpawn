package com.mcmoddev.orespawn.json.OS3Readers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.DimensionLogic;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.OreSpawnAPI;
import com.mcmoddev.orespawn.api.SpawnLogic;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.util.StateUtil;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.common.BiomeDictionary.Type;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class OS3V11Reader implements IOS3Reader {

	@Override
	public void parseJson(JsonObject entries, String fileName) {
		Map<String, IFeature> features = OreSpawn.FEATURES.getFeatures();
		JsonArray elements = entries.get("dimensions").getAsJsonArray();

		SpawnLogic spawnLogic = OreSpawn.API.createSpawnLogic();

		for (JsonElement element : elements ) {
			JsonObject object = element.getAsJsonObject();

			int dimension = object.has("dimension") ? object.get("dimension").getAsInt() : OreSpawnAPI.DIMENSION_WILDCARD;
			DimensionLogic dimensionLogic = spawnLogic.getDimension(dimension);

			JsonArray ores = object.get("ores").getAsJsonArray();

			for (JsonElement oresEntry : ores) {
				JsonObject ore = oresEntry.getAsJsonObject();

				Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ore.get("block").getAsString()));

				if (block == null) {
					continue;
				}

				IBlockState state = block.getDefaultState();

				if (ore.has("state")) {
					String stateString = ore.get("state").getAsString();
					state = StateUtil.deserializeState(block, stateString);

					if (state == null) {
						throw new RuntimeException("Invalid state " + stateString + " for block " + block.getRegistryName());
					}
				}

				JsonObject oreParams = ore.get("parameters").getAsJsonObject();

				String feature = ore.get("feature").getAsString();
				if( !features.containsKey(feature) ) {
					OreSpawn.LOGGER.warn("I don't know feature %s but was told to use it here, skipping this entry!");
					continue;
				}

				IFeature featureGen = features.get(feature);

				String replaceBase = ore.get("replace_block").getAsString();
				IBlockState blockRep;

				if( "default".equals(replaceBase) ) {
					blockRep = ReplacementsRegistry.getDimensionDefault(dimension);
				} else if( replaceBase == null ) {
					// this shouldn't happen often
					blockRep = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft:stone")).getDefaultState();
				} else {
					blockRep = ReplacementsRegistry.getBlock(replaceBase);
				}

				List<Biome> biomes = new ArrayList<>();
				List<Biome> blacklisted = new ArrayList<>();
				
				if (ore.has("biomes")) {
					JsonObject biomesStore = ore.get("biomes").getAsJsonObject();

					// first we loop through the list of included biomes, if any
					if( biomesStore.has("include") ) {
						biomesStore.get("include").getAsJsonArray().forEach( item -> {
							if( item.isJsonObject() ) {
								JsonObject temp = item.getAsJsonObject();
								if( temp.has("inclusions") ) {
									temp.get("inclusions").getAsJsonArray().forEach( dictName -> biomes.addAll(BiomeDictionary.getBiomes(Type.getType(dictName.getAsString()))));
								}
								
								if( temp.has("exclusions") ) {
									temp.get("exclusions").getAsJsonArray().forEach( dictName -> blacklisted.addAll(BiomeDictionary.getBiomes(Type.getType(dictName.getAsString()))));
								}
							} else {
								biomes.add(ForgeRegistries.BIOMES.getValue(new ResourceLocation(item.getAsString())));
							}
						});
					}
					
					if( biomesStore.has("exclude") ) {
						biomesStore.get("exclude").getAsJsonArray().forEach( item -> {
							String temp = item.getAsString();
							if( temp.contains(":") ) {
								blacklisted.add(ForgeRegistries.BIOMES.getValue(new ResourceLocation(temp)));
							} else {
								blacklisted.addAll(BiomeDictionary.getBiomes(Type.getType(temp)));
							}
						});
					}
					
					if( blacklisted.size() > 0 ) {
						if( biomes.size() == 0 ) {
							ForgeRegistries.BIOMES.getKeys().forEach(RL -> biomes.add(ForgeRegistries.BIOMES.getValue(RL)));
						}
						blacklisted.forEach(biome -> { if( biomes.contains(biome) ) { biomes.remove(biome); } });
					}
				}

				dimensionLogic.addOre(state, oreParams, biomes.toArray(new Biome[biomes.size()]), featureGen, blockRep);
			}
		}

		OreSpawn.API.registerSpawnLogic(fileName, spawnLogic);
/*
 * "biomes": {
  "include": [ "minecraft:swamp", {
       "inclusions": [ "HOT", "LUSH" ],
       "exclusions": [ "SPARSE" ]
   }],
  "exclude": [ "biomesoplenty:marsh"]
}
 */
	}

}
