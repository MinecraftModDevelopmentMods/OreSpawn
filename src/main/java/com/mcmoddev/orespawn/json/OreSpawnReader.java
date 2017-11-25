package com.mcmoddev.orespawn.json;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.json.os3.IOS3Reader;

import net.minecraft.crash.CrashReport;

public class OreSpawnReader {
	private List<JsonObject> spawns;

	public OreSpawnReader() {
		this.spawns = new LinkedList<>();
	}

	public void loadSpawnData() {
		// first we parse the OS3 format spawns, as we prefer them over other versions
		parseSpawnsV3();

	}

	private void parseSpawnsV3() {
		File directory = new File(Constants.FileBits.CONFIG_DIR, Constants.FileBits.OS3);
		File[] files;

		if (!directory.exists()) {
			return;
		}

		if (!directory.isDirectory()) {
			OreSpawn.LOGGER.fatal("OreSpawn data directory inaccessible - " + directory + " is not a directory!");
			return;
		}

		files = directory.listFiles();

		if (files.length == 0) {
			// nothing to load
			return;
		}

		loadFeaturesAndReplacements();
		loadSpawns(files);
	}

	private void loadSpawns(File[] files) {
		JsonParser parser = new JsonParser();
		Arrays.stream(files).filter(file -> file.getName().endsWith(".json")).forEach(
		file -> {
			try {
				String rawData = FileUtils.readFileToString(file, Charset.defaultCharset());

				if (rawData.isEmpty()) {
					return;
				}

				JsonElement full = parser.parse(rawData);
				JsonObject parsed = full.getAsJsonObject();

				String version = parsed.get("version").getAsString();
				IOS3Reader reader = OS3Reader.getReader(version);

				spawns.add(reader.parseJson(parsed, file.getName().substring(0, file.getName().lastIndexOf('.'))));
			} catch (Exception e) {
				CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + file.getName());
				report.getCategory().addCrashSection("OreSpawn Version", Constants.VERSION);
				OreSpawn.LOGGER.info(report.getCompleteReport());
			}
		});
	}

	private void loadFeaturesAndReplacements() {
		if (Paths.get(Constants.FileBits.CONFIG_DIR, Constants.FileBits.OS3, Constants.FileBits.SYSCONF).toFile().exists() && Paths.get(Constants.FileBits.CONFIG_DIR, Constants.FileBits.OS3, Constants.FileBits.SYSCONF).toFile().isDirectory()) {
			Arrays.stream(Paths.get(Constants.FileBits.CONFIG_DIR, Constants.FileBits.OS3, Constants.FileBits.SYSCONF).toFile().listFiles())
			.filter(file -> "json".equals(FilenameUtils.getExtension(file.getName())))
			.forEach(file -> {
				String filename = file.getName();

				if (FilenameUtils.getBaseName(filename).matches("features-.+")) {
					OreSpawn.FEATURES.loadFeaturesFile(file);
				} else if (FilenameUtils.getBaseName(filename).matches("replacements-.+")) {
					Replacements.load(file);
				}
			});
		}
	}
}
