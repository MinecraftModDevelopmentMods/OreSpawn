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

import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.oredict.OreDictionary;

public class OS1Reader {
	private OS1Reader() {
		
	}
	public static void loadEntries(Path confDir) {
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
		                for (JsonElement elem : root.get("dimensions").getAsJsonArray() ) {
		                	JsonObject dim = elem.getAsJsonObject();
		                	DimensionBuilder builder;
		                	
		                	if( "+".equals(dim.get("dimension").getAsString()) ) {
		                		builder = logic.DimensionBuilder();
		                	} else if( dim.get("dimension").getAsString() == ""+dim.get("dimension").getAsInt() ) {
		                		builder = logic.DimensionBuilder(dim.get("dimension").getAsInt());
		                	} else {
		                		builder = logic.DimensionBuilder(dim.get("dimension").getAsString());
		                	}

		                	loadOres( dim.get("ores").getAsJsonArray(), builder );
		                	builders.add(builder);
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

	private static void loadOres(JsonArray ores, DimensionBuilder builder) {
		List<SpawnBuilder> spawns = new ArrayList<>();
		for( JsonElement e : ores ) {
			JsonObject ore = e.getAsJsonObject();
			SpawnBuilder spawn = builder.SpawnBuilder(null);
			OreBuilder tO = spawn.OreBuilder();
			String blockID = ore.get("blockID").getAsString();
			int meta = ore.has("metaData")?ore.get("metaData").getAsInt():0;
			if( meta > 0 ) {
				tO.setOre(blockID.toString(), meta);
			} else {
				tO.setOre(blockID.toString());
			}
			float frequency = ore.has("frequency")?ore.get("frequency").getAsFloat():20.0f;
			int size = ore.has("size")?ore.get("size").getAsInt():8;
			int maxHeight = ore.has("maxHeight")?ore.get("maxHeight").getAsInt():255;
			int minHeight = ore.has("minHeight")?ore.get("minHeight").getAsInt():0;
			int variation = ore.has("variation")?ore.get("variation").getAsInt():(int)(0.5f * size);
			List<IBlockState> replacements = new ArrayList<>();
			FeatureBuilder feature = spawn.FeatureBuilder("default");
			feature.addParameter("frequency",frequency).addParameter("size", size)
			.addParameter("maxHeight", maxHeight).addParameter("minHeight", minHeight)
			.addParameter("variation", variation);
			
			replacements.add(ReplacementsRegistry.getDimensionDefault(-1));
			replacements.add(ReplacementsRegistry.getDimensionDefault(0));
			replacements.add(ReplacementsRegistry.getDimensionDefault(1));
			replacements.addAll(OreDictionary.getOres("stone").stream().filter(stack -> stack.getItem() instanceof ItemBlock).map(stack -> ((ItemBlock) stack.getItem()).getBlock()).map( block -> block.getDefaultState() ).collect(Collectors.toList()));

			BiomeBuilder biomes = spawn.BiomeBuilder();
			if( ore.has("biomes") ) {
				for( JsonElement bm : ore.get("biomes").getAsJsonArray() ) {
					String biome = bm.getAsString();
					biomes.whitelistBiomeByName(biome);
				}
			}
			
			spawn.create(biomes, feature, replacements, tO);
			spawns.add(spawn);
		}
		builder.create(spawns.toArray(new SpawnBuilderImpl[spawns.size()]));
	}
}
