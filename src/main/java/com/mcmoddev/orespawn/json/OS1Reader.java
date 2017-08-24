package com.mcmoddev.orespawn.json;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.*;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.impl.os3.DimensionBuilderImpl;
import com.mcmoddev.orespawn.impl.os3.SpawnBuilderImpl;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.oredict.OreDictionary;

public class OS1Reader {
	private OS1Reader() {
		
	}
	public static void loadEntries(Path confDir) {
		if( !confDir.toFile().exists() ) {
			// No files to read, don't go farther as that would create the dir
			return;
		}
		File directory = new File(confDir.toString());
        JsonParser parser = new JsonParser();

		if( !directory.exists() ) {
            directory.mkdirs();
            return;
		}
		
		if( !directory.isDirectory() ) {
			OreSpawn.LOGGER.fatal("OreSpawn data directory inaccessible - "+directory+" is not a directory!");
			return;
		}
		
		File[] files = directory.listFiles();
		if( files.length == 0 ) {
			// nothing to load
			return;
		}
		
		Arrays.stream(files).filter(file -> file.getName().endsWith(".json")).forEach(
				file -> {
					try {
						JsonObject root = parser.parse(FileUtils.readFileToString(file)).getAsJsonObject();
						
						BuilderLogic logic = OreSpawn.API.getLogic(FilenameUtils.getBaseName(file.getName()));
						List<DimensionBuilder> builders = new ArrayList<>();
						
						// iterate over the dimensions
						JsonArray theDims = root.get(ConfigNames.DIMENSIONS).getAsJsonArray();
		                for (JsonElement elem : theDims ) {
		                	JsonObject dim = elem.getAsJsonObject();
		                	DimensionBuilder builder;
		                	
		                	JsonElement dimElem = dim.get(ConfigNames.DIMENSION);
		                	if( dimElem.isJsonPrimitive() && !dimElem.isJsonNull()) {
		                		builder = getBuilder( dimElem, logic );
		                		loadOres( dim.get(ConfigNames.ORES).getAsJsonArray(), builder );
		                		builders.add(builder);
		                	}
		                }
		                logic.create(builders.toArray(new DimensionBuilderImpl[builders.size()]));
		                
		                OreSpawn.API.registerLogic(logic);
					} catch(Exception e) {
		                CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + file.getName());
		                report.getCategory().addCrashSection("OreSpawn Version", Constants.VERSION);
		                OreSpawn.LOGGER.info(report.getCompleteReport());
					}
				});

	}

	private static DimensionBuilder getBuilder(JsonElement dimElem, BuilderLogic logic ) {
		if( dimElem.getAsJsonPrimitive().isNumber() ) {
			return logic.newDimensionBuilder(dimElem.getAsInt());
		} else {
			String dimId = dimElem.getAsString();
			if( "+".equals(dimId) ) {
				return logic.newDimensionBuilder();
			} else {
				return logic.newDimensionBuilder(dimElem.getAsString());
			}
		}
	}
	
	private static void loadOres(JsonArray ores, DimensionBuilder builder) {
		List<SpawnBuilder> spawns = new ArrayList<>();
		
		ores.forEach( e -> {			
			JsonObject ore = e.getAsJsonObject();
			SpawnBuilder spawn = builder.newSpawnBuilder(null);
			OreBuilder tO = spawn.newOreBuilder();
			String blockID = ore.get(ConfigNames.BLOCKID).getAsString();
			int meta = ore.has(ConfigNames.METADATA)?ore.get(ConfigNames.METADATA).getAsInt():0;
			if( meta > 0 ) {
				tO.setOre(blockID, meta);
			} else {
				tO.setOre(blockID);
			}
			FeatureBuilder feature = spawn.newFeatureBuilder(ConfigNames.DEFAULT);
			setupFeature(feature,ore);
			
			List<IBlockState> replacements = new ArrayList<>();
			replacements.add(ReplacementsRegistry.getDimensionDefault(-1));
			replacements.add(ReplacementsRegistry.getDimensionDefault(0));
			replacements.add(ReplacementsRegistry.getDimensionDefault(1));
			replacements.addAll(OreDictionary.getOres("stone").stream().filter(stack -> stack.getItem() instanceof ItemBlock).map(stack -> ((ItemBlock) stack.getItem()).getBlock()).map( OS1Reader::getDefaultBlockState ).collect(Collectors.toList()));

			BiomeBuilder biomes = spawn.newBiomeBuilder();
			if( ore.has(ConfigNames.BIOMES) ) {
				for( JsonElement bm : ore.get(ConfigNames.BIOMES).getAsJsonArray() ) {
					String biome = bm.getAsString();
					biomes.whitelistBiomeByName(biome);
				}
			}
			
			spawn.create(biomes, feature, replacements, tO);
			spawns.add(spawn);
		});

		builder.create(spawns.toArray(new SpawnBuilderImpl[spawns.size()]));
	}
	
	private static IBlockState getDefaultBlockState( Block block ) {
		return block.getDefaultState();
	}
	private static void setupFeature(FeatureBuilder feature, JsonObject ore) {
		float frequency = ore.has(ConfigNames.DefaultFeatureProperties.FREQUENCY)?ore.get(ConfigNames.DefaultFeatureProperties.FREQUENCY).getAsFloat():20.0f;
		int size = ore.has(ConfigNames.DefaultFeatureProperties.SIZE)?ore.get(ConfigNames.DefaultFeatureProperties.SIZE).getAsInt():8;
		int maxHeight = ore.has(ConfigNames.DefaultFeatureProperties.MAXHEIGHT)?ore.get(ConfigNames.DefaultFeatureProperties.MAXHEIGHT).getAsInt():255;
		int minHeight = ore.has(ConfigNames.DefaultFeatureProperties.MINHEIGHT)?ore.get(ConfigNames.DefaultFeatureProperties.MINHEIGHT).getAsInt():0;
		int variation = ore.has(ConfigNames.DefaultFeatureProperties.VARIATION)?ore.get(ConfigNames.DefaultFeatureProperties.VARIATION).getAsInt():(int)(0.5f * size);
		feature.addParameter(ConfigNames.DefaultFeatureProperties.FREQUENCY,frequency).addParameter(ConfigNames.DefaultFeatureProperties.SIZE, size)
		.addParameter(ConfigNames.DefaultFeatureProperties.MAXHEIGHT, maxHeight).addParameter(ConfigNames.DefaultFeatureProperties.MINHEIGHT, minHeight)
		.addParameter(ConfigNames.DefaultFeatureProperties.VARIATION, variation);
	}
}
