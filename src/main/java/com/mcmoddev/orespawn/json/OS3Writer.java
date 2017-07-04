package com.mcmoddev.orespawn.json;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.impl.location.*;
import com.mcmoddev.orespawn.util.StateUtil;
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
		
		JsonObject wrapper = new JsonObject();

		OreSpawn.API.getSpawns().entrySet().forEach( ent -> {
			File file = new File(basePath, ent.getKey() + ".json");

			wrapper.addProperty("version", "1.1");
			JsonArray array = new JsonArray();

			ent.getValue().getAllDimensions().entrySet().forEach( dim -> {
				JsonObject obj = new JsonObject();
				if( dim.getKey() != OreSpawn.API.dimensionWildcard() ) {
					obj.addProperty("dimension", String.format("%d", dim.getKey()));
				} else {
					obj.addProperty("dimension", "+");
				}

				JsonArray spawns = new JsonArray();
				dim.getValue().getAllSpawns().forEach( spawn -> {
					JsonObject ore = new JsonObject();
					ore.addProperty("block", spawn.getOres().get(0).getOre().getBlock().getRegistryName().toString());
					ore.addProperty("state", StateUtil.serializeState(spawn.getOres().get(0).getOre()));
					ore.add("parameters", spawn.getFeatureGen().getParameters());
					ore.addProperty("feature", spawn.getFeatureGen().getFeatureName());
					// future extension:
					// ore.addProperty("replace_block", serialize(spawn.getReplacementBlocks()));
					ore.addProperty("replace_block", "default");
					ore.add("biomes", biomeLocationToJsonObject(spawn.getBiomes()));
					spawns.add(ore);
				});
				obj.add("ores", spawns);
				wrapper.add("dimension", array);
			});
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
		} else {
			// error ?
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
			if( (value instanceof BiomeLocationSingle) || (value instanceof BiomeLocationDictionary) ) {
				rv.add(getString(value));
			} else if( value instanceof BiomeLocationComposition ) {
				rv.add(getComposition(value));
			} else {
				// error ?
			}			
		});
		return rv;
	}

	private JsonElement getString(BiomeLocation value) {
		JsonParser p = new JsonParser();
		String val = null;
		if( value instanceof BiomeLocationSingle ) {
			val = ForgeRegistries.BIOMES.getKey(((BiomeLocationSingle)value).getBiome()).toString();
		} else {
			val = ((BiomeLocationDictionary)value).getType().toString();
		}
		
		return p.parse(val);
	}
}