package com.mcmoddev.orespawn.json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.os3.DimensionBuilder;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.impl.location.*;
import com.mcmoddev.orespawn.util.StateUtil;

import net.minecraft.crash.CrashReport;
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
		String basePath = String.format(".%1$sconfig%1$sorespawn3", File.separator);
		writeFeatures(basePath);
		writeReplacements(basePath);
		
		OreSpawn.API.getSpawns().entrySet().forEach( ent -> {			
			JsonArray dimensions = new JsonArray();

			for( Entry<Integer, DimensionBuilder> dim : ent.getValue().getAllDimensions().entrySet() ) {
				JsonObject dimension = new JsonObject();
				
				if( dim.getKey() != OreSpawn.API.dimensionWildcard() ) {
					dimension.addProperty(ConfigNames.DIMENSION, String.format("%d", dim.getKey()));
				}

				JsonArray spawns = this.genSpawns(dim.getValue().getAllSpawns());
				
				if( spawns.size() > 0 ) {
					dimension.add(ConfigNames.ORES, spawns);
					dimensions.add(dimension);
				}
			}
			
			if( countOres(dimensions) > 0 ) {
				File file = new File(basePath, String.format("%s.json", ent.getKey()));
				JsonObject wrapper = new JsonObject();
				wrapper.addProperty(ConfigNames.FILE_VERSION, "1.2");
				wrapper.add(ConfigNames.DIMENSIONS, dimensions);
				this.writeFile(file, wrapper);
			}
		});
	}
	
	private JsonArray genSpawns(ImmutableList<SpawnBuilder> allSpawns) {
		JsonArray rv = new JsonArray();
		for( SpawnBuilder spawn : allSpawns ) {
			if( spawn.getOres().isEmpty() ||
				spawn.getOres().get(0).getOre() == null ||
				"minecraft:air".equals(spawn.getOres().get(0).getOre().getBlock().getRegistryName().toString()) ) {
				continue;
			}
			rv.add( this.genSpawn(spawn) );
		}
		return rv;
	}

	private void writeFile(File file, JsonObject wrapper) {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			FileUtils.writeStringToFile(file, gson.toJson(wrapper), Charset.defaultCharset(), false);
		} catch (IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e, String.format("Failed in config %s", file.getName()));
			report.getCategory().addCrashSection("OreSpawn Version", Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());			
		}
	}

	private JsonObject genSpawn(SpawnBuilder spawn) {
		JsonObject ore = new JsonObject();
		
		ore.addProperty(ConfigNames.BLOCKS, genBlocks(spawn.getOres()));
		ore.add(ConfigNames.PARAMETERS, spawn.getFeatureGen().getParameters());
		ore.addProperty(ConfigNames.FEATURE, spawn.getFeatureGen().getFeatureName());
		ore.addProperty(ConfigNames.REPLACEMENT, ConfigNames.DEFAULT);
		ore.add(ConfigNames.BIOMES, biomeLocationToJsonObject(spawn.getBiomes()));
		return ore;
	}

	private String genBlocks(ImmutableList<OreBuilder> ores) {
		JsonArray retval = new JsonArray();
		
		ores.forEach( ore -> {
			JsonObject obj = new JsonObject();
			obj.addProperty(ConfigNames.BLOCK, ore.getOre().getBlock().getRegistryName().toString());
			obj.addProperty(ConfigNames.STATE, StateUtil.serializeState(ore.getOre()));
			obj.addProperty(ConfigNames.CHANCE, ore.getChance());
			retval.add(obj);
		});
		return retval.toString();
	}

	private int countOres(JsonArray dims ) {
		int count = 0;
		for( JsonElement dim : dims ) {
			count += dim.getAsJsonObject().get(ConfigNames.ORES).getAsJsonArray().size();
		}
		return count;
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
		rv.add(ConfigNames.BiomeStuff.WHITELIST, getList(new BiomeLocationList(((BiomeLocationComposition)value).getInclusions())));
		rv.add(ConfigNames.BiomeStuff.BLACKLIST, getList(new BiomeLocationList(((BiomeLocationComposition)value).getExclusions())));
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