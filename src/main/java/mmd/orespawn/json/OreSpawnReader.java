package mmd.orespawn.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mmd.orespawn.OreSpawn;
import mmd.orespawn.api.DimensionLogic;
import mmd.orespawn.api.OreSpawnAPI;
import mmd.orespawn.api.SpawnLogic;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum OreSpawnReader {
    INSTANCE;

    public void readSpawnEntries() {
        File directory = new File(".", "orespawn");
        JsonParser parser = new JsonParser();

        if (!directory.exists()) {
            directory.mkdirs();
            return;
        }

        if (!directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();

        if (files == null) {
            return;
        }

        Arrays.stream(files).filter(file -> file.getName().endsWith(".json")).forEach(file -> {
            try {
                JsonElement element = parser.parse(FileUtils.readFileToString(file));
                JsonArray array = element.getAsJsonArray();

                SpawnLogic spawnLogic = OreSpawn.API.createSpawnLogic();

                for (JsonElement arrayEntry : array) {
                    JsonObject object = arrayEntry.getAsJsonObject();

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
                            boolean foundState = false;

                            for (IBlockState validState : block.getBlockState().getValidStates()) {
                                String string = validState.toString();
                                string = string.substring(string.indexOf("[") + 1, string.length() - (string.endsWith("]") ? 1 : 0));
                                if (string.equals(block.getRegistryName().toString())) {
                                    string = "";
                                }

                                if (stateString.equals(string)) {
                                    state = validState;
                                    foundState = true;
                                    break;
                                }
                            }

                            if (!foundState) {
                                throw new RuntimeException("Invalid state " + stateString + " for block " + block.getRegistryName());
                            }
                        }

                        int size = ore.get("size").getAsInt();
                        int variation = ore.get("variation").getAsInt();
                        int frequency = ore.get("frequency").getAsInt();
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
                report.getCategory().addCrashSection("OreSpawn Version", OreSpawn.VERSION);
                OreSpawn.LOGGER.info(report.getCompleteReport());
            }
        });
    }
}
