package com.mcmoddev.orespawn.json;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

public class OS2Reader {
	public static void loadEntries() {
        File directory = new File(".", "orespawn");
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
						JsonElement full = parser.parse(FileUtils.readFileToString(file));
						JsonArray elements = full.getAsJsonArray();
						
						BuilderLogic logic = OreSpawn.API.getLogic(FilenameUtils.getBaseName(file.getName()));
						List<DimensionBuilder> builders = new ArrayList<>();
						
		                for (JsonElement element : elements ) {
		                    JsonObject object = element.getAsJsonObject();

		                    int dimension = object.has("dimension") ? object.get("dimension").getAsInt() : OreSpawn.API.dimensionWildcard();
		                    DimensionBuilder builder = logic.newDimensionBuilder(dimension);

		                    JsonArray ores = object.get("ores").getAsJsonArray();
		                    List<SpawnBuilder> spawns = new ArrayList<>();
		                    for (JsonElement oresEntry : ores) {
		                    	SpawnBuilder spawn = builder.newSpawnBuilder(null);
		                        JsonObject ore = oresEntry.getAsJsonObject();

		                        String blockName = ore.get("block").getAsString();
		                        String blockState = ore.has("state")?ore.get("state").getAsString():"";

		                        OreBuilder oreB = spawn.newOreBuilder();
		                        if("".equals(blockState)) {
		                        	oreB.setOre(blockName);
		                        } else {
		                        	oreB.setOre(blockName,blockState);
		                        }
		                        
		                        FeatureBuilder feature = spawn.newFeatureBuilder("default");
		                        feature.addParameter("size", ore.get("size").getAsInt());
		                        feature.addParameter("variation", ore.get("variation").getAsInt());
		                        feature.addParameter("frequency", ore.get("frequency").getAsFloat());
		                        feature.addParameter("minHeight", ore.get("min_height").getAsInt());
		                        feature.addParameter("maxHeight", ore.get("max_height").getAsInt());
		                        
		                        BiomeBuilder biomes = spawn.newBiomeBuilder();
		                        
		                        if (ore.has("biomes")) {
		                            JsonArray biomesArray = ore.get("biomes").getAsJsonArray();

		                            for (JsonElement biomeEntry : biomesArray) {
		                            	biomes.whitelistBiomeByName(biomeEntry.getAsString());
		                            }
		                        }
		                        
		                        IBlockState replacement = ReplacementsRegistry.getDimensionDefault(dimension);
		                        List<IBlockState> reps = new ArrayList<>();
		                        reps.add(replacement);
		                        spawn.create(biomes, feature, reps, oreB);
		                        spawns.add(spawn);
		                    }
		                    builder.create(spawns.toArray(new SpawnBuilderImpl[spawns.size()]));
		                    builders.add( builder );
		                }
		                logic.create(builders.toArray(new DimensionBuilderImpl[builders.size()]));
		                
		                OreSpawn.API.registerLogic(logic);
		            } catch (Exception e) {
		                CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + file.getName());
		                report.getCategory().addCrashSection("OreSpawn Version", Constants.VERSION);
		                OreSpawn.LOGGER.info(report.getCompleteReport());
		            }
		        });
		    }
}