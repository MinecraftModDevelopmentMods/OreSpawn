package com.mcmoddev.orespawn.json;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.DimensionLogic;
import com.mcmoddev.orespawn.api.OreSpawnAPI;
import com.mcmoddev.orespawn.api.SpawnLogic;
import com.mcmoddev.orespawn.data.Constants;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class OS1Reader {
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
						
						SpawnLogic spawnLogic = OreSpawn.API.createSpawnLogic();
						
						// iterate over the dimensions
		                for (JsonElement elem : root.get("dimensions").getAsJsonArray() ) {
		                	JsonObject dim = elem.getAsJsonObject();
		                	int theDim;
		                	if( dim.get("dimension").getAsString().equals("+") ) {
		                		theDim = OreSpawnAPI.DIMENSION_WILDCARD;
		                	} else {
		                		theDim = dim.get("dimension").getAsInt();
		                	}
		                	
		                	DimensionLogic dimensionLogic = spawnLogic.getDimension(theDim);
		                	
		                	// now iterate the ores
		                	for( JsonElement oElem : dim.get("ores").getAsJsonArray() ) {
		                		loadOre( oElem.getAsJsonObject(), dimensionLogic );
		                	}
		                }
		                OreSpawn.API.registerSpawnLogic(file.getName().substring(0, file.getName().lastIndexOf(".")), spawnLogic);
					} catch(Exception e) {
		                CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + file.getName());
		                report.getCategory().addCrashSection("OreSpawn Version", Constants.VERSION);
		                OreSpawn.LOGGER.info(report.getCompleteReport());
					}
				});

	}

	private static void loadOre(JsonObject oElem, DimensionLogic dimensionLogic) {
		String blockID = oElem.get("blockID").getAsString();
		String modId = blockID.contains(":")?blockID.substring(0, blockID.indexOf(":")):"minecraft";
		String name = blockID.contains(":")?blockID.substring(blockID.indexOf(":")+1):blockID;
		int meta = oElem.has("metaData")?oElem.get("metaData").getAsInt():0;
		ResourceLocation blockKey = new ResourceLocation(blockID);
		
		if( !Block.REGISTRY.containsKey(blockKey) ) {
			OreSpawn.LOGGER.warn("Asked to spawn block "+modId+":"+name+" that does not exist");
			return;
		}
		
		Block b = Block.REGISTRY.getObject(blockKey);
		@SuppressWarnings("deprecation")
		IBlockState bs = meta==0?b.getDefaultState():b.getStateFromMeta(meta);
		
		float frequency = oElem.has("frequency")?oElem.get("frequency").getAsFloat():20.0f;
		int size = oElem.has("size")?oElem.get("size").getAsInt():8;
		int maxHeight = oElem.has("maxHeight")?oElem.get("maxHeight").getAsInt():255;
		int minHeight = oElem.has("minHeight")?oElem.get("minHeight").getAsInt():0;
		int variation = oElem.has("variation")?oElem.get("variation").getAsInt():(int)(0.5f * size);
		List<Biome> biomes;
		
		if( oElem.has("biomes") && oElem.get("biomes").getAsJsonArray().size() > 0 ) {
            JsonArray biomesArray = oElem.get("biomes").getAsJsonArray();
            biomes = new ArrayList<>();
            
            for (JsonElement biomeEntry : biomesArray) {
                Biome biome = ForgeRegistries.BIOMES.getValue(new ResourceLocation(biomeEntry.getAsString()));

                if (biome != null) {
                    biomes.add(biome);
                }
            }
		} else {
			biomes = Collections.EMPTY_LIST;
		}
		
        dimensionLogic.addOre(bs, size, variation, frequency, minHeight, maxHeight, biomes.toArray(new Biome[biomes.size()]));
	}
}
