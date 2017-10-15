package com.mcmoddev.orespawn.json;

import java.io.File;
import java.nio.charset.Charset;
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
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.impl.os3.DimensionBuilderImpl;
import com.mcmoddev.orespawn.impl.os3.SpawnBuilderImpl;
import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;

public class OS2Reader {
	private OS2Reader() {}
	public static void loadEntries() {
		File directory = new File(".", "orespawn");
		JsonParser parser = new JsonParser();

		if( directory.exists() && !directory.isDirectory() ) {
			OreSpawn.LOGGER.fatal("OreSpawn data directory inaccessible - "+directory+" is not a directory!");
			return;
		} else if( !directory.exists() ) {
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
						JsonElement full = parser.parse(FileUtils.readFileToString(file, Charset.defaultCharset()));
						JsonArray elements = full.getAsJsonArray();

						BuilderLogic logic = OreSpawn.API.getLogic(FilenameUtils.getBaseName(file.getName()));
						List<DimensionBuilder> builders = new ArrayList<>();

						for (JsonElement element : elements ) {
							JsonObject object = element.getAsJsonObject();

							int dimension = object.has(ConfigNames.DIMENSION) ? object.get(ConfigNames.DIMENSION).getAsInt() : OreSpawn.API.dimensionWildcard();
							DimensionBuilder builder = logic.newDimensionBuilder(dimension);

							JsonArray ores = object.get(ConfigNames.ORES).getAsJsonArray();
							List<SpawnBuilder> spawns = new ArrayList<>();
							parseOres(spawns, ores, builder, dimension);
							
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

	private static void parseOres(List<SpawnBuilder> spawns, JsonArray ores, DimensionBuilder builder, int dimension) {
		for (JsonElement oresEntry : ores) {
			SpawnBuilder spawn = builder.newSpawnBuilder(null);
			JsonObject ore = oresEntry.getAsJsonObject();

			String blockName = ore.get(ConfigNames.BLOCK).getAsString();
			String blockState = ore.has(ConfigNames.STATE)?ore.get(ConfigNames.STATE).getAsString():"";

			OreBuilder oreB = spawn.newOreBuilder();
			if("".equals(blockState)) {
				oreB.setOre(blockName);
			} else {
				oreB.setOre(blockName,blockState);
			}

			FeatureBuilder feature = spawn.newFeatureBuilder(ConfigNames.DEFAULT);
			setupFeature(feature,ore);

			BiomeBuilder biomes = spawn.newBiomeBuilder();

			if (ore.has(ConfigNames.BIOMES)) {
				JsonArray biomesArray = ore.get(ConfigNames.BIOMES).getAsJsonArray();

				for (JsonElement biomeEntry : biomesArray) {
					biomes.whitelistBiomeByName(biomeEntry.getAsString());
				}
			}

			List<IBlockState> reps = ReplacementsRegistry.getDimensionDefault(dimension);
			spawn.create(biomes, feature, reps, oreB);
			spawns.add(spawn);
		}
	}
	
	private static void setupFeature(FeatureBuilder feature, JsonObject ore) {
		feature.addParameter(ConfigNames.DefaultFeatureProperties.SIZE, ore.get(ConfigNames.DefaultFeatureProperties.SIZE).getAsInt());
		feature.addParameter(ConfigNames.DefaultFeatureProperties.VARIATION, ore.get(ConfigNames.DefaultFeatureProperties.VARIATION).getAsInt());
		feature.addParameter(ConfigNames.DefaultFeatureProperties.FREQUENCY, ore.get(ConfigNames.DefaultFeatureProperties.FREQUENCY).getAsFloat());
		feature.addParameter(ConfigNames.DefaultFeatureProperties.MINHEIGHT, ore.get(ConfigNames.DefaultFeatureProperties.MINHEIGHT).getAsInt());
		feature.addParameter(ConfigNames.DefaultFeatureProperties.MAXHEIGHT, ore.get(ConfigNames.DefaultFeatureProperties.MAXHEIGHT).getAsInt());
	}
}