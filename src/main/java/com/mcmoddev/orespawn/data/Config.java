package com.mcmoddev.orespawn.data;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mcmoddev.orespawn.OreSpawn;

import net.minecraft.crash.CrashReport;
import net.minecraftforge.common.config.Configuration;

public class Config {

	private static Configuration configuration;

	private Config() {
	}

	public static void loadConfig() {
		configuration = new Configuration(new File(Constants.CONFIG_FILE));

		// Load our Boolean Values
		boolVals.put(Constants.RETROGEN_KEY, configuration.getBoolean(Constants.RETROGEN_KEY,
				Configuration.CATEGORY_GENERAL, false,
				"Do we have Retrogen active and generating anything different from the last run in already existing chunks ?"));
		boolVals.put(Constants.FORCE_RETROGEN_KEY,
				configuration.getBoolean(Constants.FORCE_RETROGEN_KEY,
						Configuration.CATEGORY_GENERAL, false,
						"Force all chunks to retrogen regardless of anything else"));
		boolVals.put(Constants.REPLACE_VANILLA_OREGEN,
				configuration.getBoolean(Constants.REPLACE_VANILLA_OREGEN,
						Configuration.CATEGORY_GENERAL, false,
						"Replace vanilla ore-generation entirely"));
		boolVals.put(Constants.FLAT_BEDROCK,
				configuration.getBoolean(Constants.FLAT_BEDROCK, Configuration.CATEGORY_GENERAL,
						false, "Flatten the bedrock during world generation"));
		boolVals.put(Constants.RETRO_BEDROCK, configuration.getBoolean(Constants.RETRO_BEDROCK,
				Configuration.CATEGORY_GENERAL, false, "Retroactively flatten bedrock"));
		intVals.put(Constants.BEDROCK_LAYERS,
				configuration.getInt(Constants.BEDROCK_LAYERS, Configuration.CATEGORY_GENERAL, 1, 1,
						4, "How thick should the shell of bedrock be?"));
		knownKeys.add(Constants.RETROGEN_KEY);
		knownKeys.add(Constants.FORCE_RETROGEN_KEY);
		knownKeys.add(Constants.REPLACE_VANILLA_OREGEN);
		knownKeys.add(Constants.KNOWN_MODS);
		knownKeys.add(Constants.FLAT_BEDROCK);
		knownKeys.add(Constants.RETRO_BEDROCK);
		knownKeys.add(Constants.BEDROCK_LAYERS);

		loadExtractedConfigs();
	}

	private static void loadExtractedConfigs() {
		final Path p = FileSystems.getDefault().getPath("config", "orespawn3", "sysconf",
				"known-configs.json");

		if (!p.toFile().exists()) {
			return;
		}

		final File in = p.toFile();
		String rawData;

		try {
			rawData = FileUtils.readFileToString(in, Charset.defaultCharset());
		} catch (IOException e) {
			return;
		}

		if (rawData.isEmpty()) {
			return;
		}

		final JsonArray data = new JsonParser().parse(rawData).getAsJsonArray();
		data.forEach(item -> addKnownMod(item.getAsString()));
	}

	public static List<String> getKnownMods() {
		return ImmutableList.copyOf(extractedConfigs);
	}

	public static void addKnownMod(final String modId) {
		extractedConfigs.add(modId);
	}

	public static boolean getBoolean(final String keyname) {
		if (knownKeys.contains(keyname) && boolVals.containsKey(keyname)) {
			return boolVals.get(keyname);
		}

		return false;
	}

	public static String getString(final String keyname) {
		if (knownKeys.contains(keyname) && stringVals.containsKey(keyname)) {
			return stringVals.get(keyname);
		}

		return "";
	}

	public static int getInt(final String keyname) {
		if (knownKeys.contains(keyname) && intVals.containsKey(keyname)) {
			return intVals.get(keyname);
		}

		return 0;
	}

	public static float getFloat(final String keyname) {
		if (knownKeys.contains(keyname) && floatVals.containsKey(keyname)) {
			return floatVals.get(keyname);
		}

		return 0.0f;
	}

	public static void saveConfig() {
		if (!extractedConfigs.isEmpty()) {
			saveKnownConfigs();
		}

		configuration.save();
	}

	private static void saveKnownConfigs() {
		final Gson gson = new GsonBuilder().setPrettyPrinting().create();
		final Path p = FileSystems.getDefault().getPath("config", "orespawn3", "sysconf",
				"known-configs.json");

		if (!p.toFile().getParentFile().exists()) {
			p.toFile().mkdirs();
		}

		final File in = p.toFile();

		final JsonArray data = new JsonArray();

		extractedConfigs.forEach(data::add);

		try {
			FileUtils.writeStringToFile(in, gson.toJson(data), Charset.defaultCharset());
		} catch (final IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e,
					"Failed saving list of already extracted mod configs");
			report.getCategory().addCrashSection("OreSpawn Version", Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		}
	}

	private static final HashMap<String, Boolean> boolVals = new HashMap<>();
	private static final HashMap<String, String> stringVals = new HashMap<>();
	private static final HashMap<String, Integer> intVals = new HashMap<>();
	private static final HashMap<String, Float> floatVals = new HashMap<>();
	private static final List<String> knownKeys = new ArrayList<>();
	private static final List<String> extractedConfigs = new ArrayList<>();
}
