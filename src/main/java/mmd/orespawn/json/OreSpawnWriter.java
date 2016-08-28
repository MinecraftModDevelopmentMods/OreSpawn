package mmd.orespawn.json;

import com.google.gson.*;
import mmd.orespawn.OreSpawn;
import mmd.orespawn.api.DimensionLogic;
import mmd.orespawn.api.SpawnEntry;
import mmd.orespawn.api.OreSpawnAPI;
import mmd.orespawn.api.SpawnLogic;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public enum OreSpawnWriter {
    INSTANCE;

    public void writeSpawnEntries() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        Loader.instance().getActiveModList().stream().map(ModContainer::getModId).forEach(id -> {
            SpawnLogic logic = OreSpawn.API.getSpawnLogic(id);

            if (logic != null) {
                Map<Integer, DimensionLogic> dimensions = logic.getAllDimensions();

                JsonArray array = new JsonArray();

                for (Map.Entry<Integer, DimensionLogic> dimension : dimensions.entrySet()) {
                    JsonObject object = new JsonObject();

                    if (dimension.getKey() != OreSpawnAPI.DIMENSION_WILDCARD) {
                        object.addProperty("dimension", dimension.getKey());
                    }

                    JsonArray ores = new JsonArray();
                    Collection<SpawnEntry> entries = dimension.getValue().getEntries();

                    for (SpawnEntry entry : entries) {
                        JsonObject ore = new JsonObject();

                        ore.addProperty("block", entry.getState().getBlock().getRegistryName().toString());

                        if (entry.getState() != entry.getState().getBlock().getDefaultState()) {
                            ore.addProperty("state", this.serializeBlockState(entry.getState()));
                        }

                        ore.addProperty("size", entry.getSize());
                        ore.addProperty("variation", entry.getVariation());
                        ore.addProperty("frequency", entry.getFrequency());
                        ore.addProperty("min_height", entry.getMinHeight());
                        ore.addProperty("max_height", entry.getMaxHeight());

                        Biome[] biomeArray = entry.getBiomes();

                        if (biomeArray != null && biomeArray.length != 0) {
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
                    FileUtils.writeStringToFile(new File("." + File.separator + "orespawn", id + ".json"), StringEscapeUtils.unescapeJson(json), Charsets.UTF_8);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private String serializeBlockState(IBlockState state) {
        String string = state.toString();
        string = string.substring(string.indexOf("[") + 1, string.length() - (string.endsWith("]") ? 1 : 0));
        if (string.equals(state.getBlock().getRegistryName().toString())) {
            string = "normal";
        }
        return string;
    }
}
