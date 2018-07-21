package com.mcmoddev.orespawn.json;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;

import net.minecraft.crash.CrashReport;

public class OreSpawnWriter {
	/*
	 * Write out the configs as the system knows them to the 'forced-saves' directory
	 */
	public static void saveConfigs() {
		Map<Path, List<String>> configs = OreSpawn.API.getSpawnsByFile();
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		configs.entrySet().stream()
		.forEach( ent -> {
			saveSingle(ent.getKey(), gson);
		});
	}
	
	private static void saveSingle(Path filePath, Gson gson) {
		JsonObject root = new JsonObject();
		root.addProperty(ConfigNames.FILE_VERSION, "2.0");
		JsonObject spawns = new JsonObject();
		String k = filePath.getFileName().toString();
		Path saveDir = Constants.CONFDIR.resolve("forced-saves");
		Path conf = saveDir.resolve(k);
		if(!saveDir.toFile().exists()) {
			saveDir.toFile().mkdirs();
		}
		try(BufferedWriter p = Files.newBufferedWriter(conf, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
			OreSpawn.API.getSpawnsForFile(k).stream()
			.forEach( spawnName -> {
				JsonObject thisSpawn = new JsonObject();
				ISpawnEntry spawnEntry = OreSpawn.API.getSpawn(spawnName);
				thisSpawn.addProperty(ConfigNames.ENABLED, spawnEntry.isEnabled());
				thisSpawn.addProperty(ConfigNames.RETROGEN, spawnEntry.isRetrogen());
				thisSpawn.addProperty(ConfigNames.FEATURE, spawnEntry.getFeature().getFeatureName());
				thisSpawn.add(ConfigNames.DIMENSIONS, spawnEntry.getDimensions().serialize());
				thisSpawn.add(ConfigNames.BIOMES, spawnEntry.getBiomes().serialize());
				thisSpawn.add(ConfigNames.REPLACEMENT, spawnEntry.getMatcher().serialize());
				thisSpawn.add(ConfigNames.PARAMETERS, spawnEntry.getFeature().getFeatureParameters());
				spawns.add(spawnName, thisSpawn);
			});
			root.add(ConfigNames.SPAWNS, spawns);
			p.write(gson.toJson(root));
		} catch (IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e, "Failed writing config data " + conf.toString());
			report.getCategory().addCrashSection("OreSpawn Version", Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		}
		
	}
	
	public static void saveSingle(String fileName) {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		saveSingle(Constants.CONFDIR.resolve(fileName), gson);
	}
}
