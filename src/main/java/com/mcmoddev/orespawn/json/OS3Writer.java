package com.mcmoddev.orespawn.json;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.DimensionLogic;
import com.mcmoddev.orespawn.api.OreSpawnAPI;
import com.mcmoddev.orespawn.api.SpawnEntry;
import com.mcmoddev.orespawn.api.SpawnLogic;
import com.mcmoddev.orespawn.util.StateUtil;
import net.minecraft.world.biome.Biome;

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

    	for (Map.Entry<String, SpawnLogic> entry : OreSpawn.API.getAllSpawnLogic().entrySet()) {
            File file = new File(basePath, entry.getKey() + ".json");
            
            if (file.exists()) {
                continue;
            }
            
            Map<Integer, DimensionLogic> dimensions = entry.getValue().getAllDimensions();

            JsonObject wrapper = new JsonObject();
            wrapper.addProperty("version", 1);
            JsonArray array = new JsonArray();
            

            for (Map.Entry<Integer, DimensionLogic> dimension : dimensions.entrySet()) {
                JsonObject object = new JsonObject();

                if (dimension.getKey() != OreSpawnAPI.DIMENSION_WILDCARD) {
                    object.addProperty("dimension", dimension.getKey());
                }

                JsonArray ores = new JsonArray();
                Collection<SpawnEntry> entries = dimension.getValue().getEntries();

                for (SpawnEntry spawnEntry : entries) {
                    JsonObject ore = new JsonObject();

                    ore.addProperty("block", spawnEntry.getState().getBlock().getRegistryName().toString());

                    if (spawnEntry.getState() != spawnEntry.getState().getBlock().getDefaultState()) {
                        ore.addProperty("state", StateUtil.serializeState(spawnEntry.getState()));
                    }

                    ore.add("parameters", spawnEntry.getParameters());
                    ore.addProperty("feature", OreSpawn.FEATURES.getFeatureName(spawnEntry.getFeatureGen()));
                    ore.addProperty("replace_block", "default");
                    
                    List<Biome> biomeArray = spawnEntry.getBiomes();

                    if ( !biomeArray.equals(Collections.<Biome>emptyList())) {
                        JsonArray biomes = new JsonArray();

                        for (Biome biome : biomeArray) {
                            biomes.add(new JsonPrimitive(biome.getRegistryName().toString()));
                        }

                        ore.add("biomes", biomes);
                    }

                    ores.add(ore);
                }

                object.add("ores", ores);

                array.add(object);
            }
            wrapper.add("dimensions", array);
            String json = gson.toJson(wrapper);

            try {
                FileUtils.writeStringToFile(file, StringEscapeUtils.unescapeJson(json), Charsets.UTF_8);
            } catch (IOException e) {
                OreSpawn.LOGGER.fatal("Exception writing OreSpawn config %s - %s", file.toString(), e.getLocalizedMessage());
            }
        }
    }
}
