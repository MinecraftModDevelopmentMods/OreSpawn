package com.mcmoddev.orespawn.json;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.SpawnLogic;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.api.DimensionLogic;
import com.mcmoddev.orespawn.api.OreSpawnAPI;
import com.mcmoddev.orespawn.util.StateUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

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

		                        int size = ore.get("size").getAsInt();
		                        int variation = ore.get("variation").getAsInt();
		                        float frequency = ore.get("frequency").getAsFloat();
		                        int minHeight = ore.get("min_height").getAsInt();
		                        int maxHeight = ore.get("max_height").getAsInt();
		                        List<Biome> biomes = new ArrayList<>();

		                        if (ore.has("biomes")) {
		                            JsonArray biomesArray = ore.get("biomes").getAsJsonArray();

		                            for (JsonElement biomeEntry : biomesArray) {
		                                Biome biome = ForgeRegistries.BIOMES.getValue(new ResourceLocation(biomeEntry.getAsString()));

		                                if (biome != null) {
		                                    biomes.add(biome);
		                                }
		                            }
		                        }

		                        dimensionLogic.addOre(state, size, variation, frequency, minHeight, maxHeight, biomes.toArray(new Biome[biomes.size()]));
		                    }
		                }

		                OreSpawn.API.registerSpawnLogic(file.getName().substring(0, file.getName().lastIndexOf(".")), spawnLogic);
		            } catch (Exception e) {
		                CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + file.getName());
		                report.getCategory().addCrashSection("OreSpawn Version", Constants.VERSION);
		                OreSpawn.LOGGER.info(report.getCompleteReport());
		            }
		        });
		    }
}