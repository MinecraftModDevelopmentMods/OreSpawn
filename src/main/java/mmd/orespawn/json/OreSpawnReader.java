package mmd.orespawn.json;

import com.google.gson.*;
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

    @Deprecated //planned for removal in 2.1.0
    public void convertOldSpawnEntries() {
        File directory = new File(".", "config" + File.separator + "orespawn");
        JsonParser parser = new JsonParser();

        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();

        if (files == null) {
            return;
        }

        Arrays.stream(files).filter(file -> file.getName().endsWith(".json")).forEach(file -> {
            try {
                OreSpawn.LOGGER.info("Converting JSON " + file.getName());

                JsonElement element = parser.parse(FileUtils.readFileToString(file));
                JsonObject object = element.getAsJsonObject();
                JsonArray dimensions = object.get("dimensions").getAsJsonArray();

                SpawnLogic spawnLogic = OreSpawn.API.createSpawnLogic();

                for (JsonElement dimensionsEntry : dimensions) {
                    JsonObject dimension = dimensionsEntry.getAsJsonObject();

                    int newDimension = OreSpawnAPI.DIMENSION_WILDCARD;

                    JsonPrimitive id = dimension.get("dimension").getAsJsonPrimitive();
                    if (id.isNumber()) {
                        newDimension = id.getAsInt();
                    }

                    DimensionLogic dimensionLogic = spawnLogic.getDimension(newDimension);

                    JsonArray ores = dimension.get("ores").getAsJsonArray();

                    for (JsonElement oreEntry : ores) {
                        JsonObject ore = oreEntry.getAsJsonObject();

                        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(ore.get("blockID").getAsString()));

                        if (block == null) {
                            continue;
                        }

                        IBlockState state = block.getDefaultState();

                        if (ore.has("blockMeta")) {
                            state = block.getStateFromMeta(ore.get("blockMeta").getAsInt());
                        }

                        int size = ore.get("size").getAsInt();
                        int variation = ore.get("variation").getAsInt();
                        int frequency = ore.get("frequency").getAsInt();
                        int minHeight = ore.get("minHeight").getAsInt();
                        int maxHeight = ore.get("maxHeight").getAsInt();
                        List<Biome> biomes = new ArrayList<>();

                        if (ore.has("biomes")) {
                            JsonArray biomesArray = ore.get("biomes").getAsJsonArray();

                            for (JsonElement biomeEntry : biomesArray) {
                                String biome = biomeEntry.getAsString();

                                try {
                                    int biomeID = Integer.parseInt(biome);
                                    Biome result = Biome.getBiome(biomeID);

                                    if (result != null) {
                                        biomes.add(result);
                                    }
                                } catch (NumberFormatException e) {
                                    for (Biome result : ForgeRegistries.BIOMES) {
                                        if (result.getBiomeName().equals(biome)) {
                                            biomes.add(result);
                                            break;
                                        }
                                    }
                                }
                            }
                        }

                        dimensionLogic.addOre(state, size, variation, frequency, minHeight, maxHeight, biomes.toArray(new Biome[biomes.size()]));
                    }

                }

                OreSpawn.API.registerSpawnLogic(file.getName().substring(0, file.getName().lastIndexOf(".")), spawnLogic);
                file.delete();
            } catch (Exception e) {
                CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + file.getName());
                report.getCategory().addCrashSection("OreSpawn Version", OreSpawn.VERSION);
                OreSpawn.LOGGER.info(report.getCompleteReport());
            }
        });

        directory.delete();
    }
}
