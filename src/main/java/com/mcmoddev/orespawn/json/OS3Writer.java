package com.mcmoddev.orespawn.json;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.impl.location.*;
import com.mcmoddev.orespawn.util.StateUtil;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class OS3Writer {
	private void writeFeatures(String base) {
		File file = new File(base, "_features.json");
		OreSpawn.FEATURES.writeFeatures(file);
	}

	private void writeReplacements(String base) {
		File file = new File(base, "_replacements.json");
		Replacements.save(file);
	}

	public void writeSpawnEntries() {
		String basePath = String.format(".%sorespawn%sos3", File.separator, File.separator);
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		writeFeatures(basePath);
		writeReplacements(basePath);
		
		OreSpawn.API.getSpawns().entrySet().forEach( ent -> {
			File file = new File(basePath, ent.getKey() + ".json");
			
			JsonObject wrapper = new JsonObject();
			wrapper.addProperty("version", "1.1");
			
			JsonArray dimensions = new JsonArray();

			ent.getValue().getAllDimensions().entrySet().forEach( dim -> {
				JsonObject dimension = new JsonObject();
				if( dim.getKey() != OreSpawn.API.dimensionWildcard() ) {
					dimension.addProperty("dimension", String.format("%d", dim.getKey()));
				}

				JsonArray spawns = new JsonArray();

				dim.getValue().getAllSpawns().forEach( spawn -> {
					if( spawn.getOres().size() == 0 ) {
						return;
					}

					JsonObject ore = new JsonObject();
					ImmutableList<OreBuilder> ob = spawn.getOres();
					OreBuilder sob = ob.get(0);
					IBlockState sobs = sob.getOre();
					Block sobsb = sobs.getBlock();
					ResourceLocation sobsbrl = ForgeRegistries.BLOCKS.getKey(sobsb);
					String blockName = sobsbrl.toString();
					
					ore.addProperty("block", blockName);
					String state = StateUtil.serializeState(sobs);
					if( !"normal".equals(state) ) {
						ore.addProperty("state", StateUtil.serializeState(sobs));
					}
					ore.add("parameters", spawn.getFeatureGen().getParameters());
					ore.addProperty("feature", spawn.getFeatureGen().getFeatureName());
					// future extension:
					// ore.addProperty("replace_block", serialize(spawn.getReplacementBlocks()));
					ore.addProperty("replace_block", "default");
					ore.add("biomes", biomeLocationToJsonObject(spawn.getBiomes()));
					spawns.add(ore);
				});
				dimension.add("ores", spawns);
				dimensions.add(dimension);
			});
			wrapper.add("dimensions", dimensions);
			String json = gson.toJson(wrapper);
	        try {
	            FileUtils.writeStringToFile(file, StringEscapeUtils.unescapeJson(json), Charsets.UTF_8);
	        } catch (IOException e) {
	            OreSpawn.LOGGER.fatal("Exception writing OreSpawn config %s - %s", file.toString(), e.getLocalizedMessage());
	        }
		});
	}
	

	private JsonElement biomeLocationToJsonObject(BiomeLocation value) {
		if( (value instanceof BiomeLocationSingle) || (value instanceof BiomeLocationDictionary) ) {
			return getString(value);
		} else if( value instanceof BiomeLocationList ) {
			return getList(value);
		} else if( value instanceof BiomeLocationComposition ) {
			return getComposition(value);
		}
		
		return null;
	}

	private JsonElement getComposition(BiomeLocation value) {
		JsonObject rv = new JsonObject();
		rv.add("inclusions", getList(new BiomeLocationList(((BiomeLocationComposition)value).getInclusions())));
		rv.add("exclusions", getList(new BiomeLocationList(((BiomeLocationComposition)value).getExclusions())));
		return rv;
	}

	private JsonArray getList(BiomeLocation value) {
		JsonArray rv = new JsonArray();
		((BiomeLocationList)value).getLocations().forEach( loc -> {
			if( (loc instanceof BiomeLocationSingle) || (loc instanceof BiomeLocationDictionary) ) {
				rv.add(getString(loc));
			} else if( loc instanceof BiomeLocationComposition ) {
				rv.add(getComposition(loc));
			}			
		});
		return rv;
	}

	private JsonElement getString(BiomeLocation value) {
		String val = null;
		if( value instanceof BiomeLocationSingle ) {
			val = ForgeRegistries.BIOMES.getKey(((BiomeLocationSingle)value).getBiome()).toString();
		} else {
			val = ((BiomeLocationDictionary)value).getType().toString();
		}
		return new JsonPrimitive(val);
	}
}