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

public class OS2Writer {
    public void writeSpawnEntries() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        for (Map.Entry<String, SpawnLogic> entry : OreSpawn.API.getAllSpawnLogic().entrySet()) {
            File file = new File("." + File.separator + "orespawn", entry.getKey() + ".json");
            
            if (file.exists()) {
                continue;
            }
            
            Map<Integer, DimensionLogic> dimensions = entry.getValue().getAllDimensions();

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

                    ore.addProperty("size", spawnEntry.getSize());
                    ore.addProperty("variation", spawnEntry.getVariation());
                    ore.addProperty("frequency", spawnEntry.getFrequency());
                    ore.addProperty("min_height", spawnEntry.getMinHeight());
                    ore.addProperty("max_height", spawnEntry.getMaxHeight());

                    List<Biome> biomeArray = spawnEntry.getBiomes();

                    if (biomeArray != Collections.EMPTY_LIST) {
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

            String json = gson.toJson(array);

            try {
                FileUtils.writeStringToFile(file, StringEscapeUtils.unescapeJson(json), Charsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
