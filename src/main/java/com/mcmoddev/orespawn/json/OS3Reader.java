package com.mcmoddev.orespawn.json;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.json.os3.IOS3Reader;
import com.mcmoddev.orespawn.json.os3.readers.*;

import net.minecraft.crash.CrashReport;

public class OS3Reader {

	private OS3Reader() {

	}
	private static void loadFeatures(File file) {
		OreSpawn.FEATURES.loadFeaturesFile(file);
	}

	public static void loadEntries() {
		File directory = new File("config" + File.separator + "orespawn3", "os3");
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
					if( "_features.json".equals(file.getName()) ) {
						// this contains the map of features, don't bother with it
						loadFeatures(file);
						return;
					} else if( "_replacements.json".equals(file.getName())) {
						Replacements.load(file);
						return;
					}

					try {
						JsonElement full = parser.parse(FileUtils.readFileToString(file, Charset.defaultCharset()));
						JsonObject parsed = full.getAsJsonObject();

						IOS3Reader reader = null;
						String version = parsed.get("version").getAsString();
						switch( version ) {
						case "1":
							reader = new OS3V1Reader();
							break;
						case "1.1":
							reader = new OS3V11Reader();
							break;
						default:
							OreSpawn.LOGGER.error("Unknown version %s", version);
							return;
						}

						reader.parseJson(parsed, file.getName().substring(0, file.getName().lastIndexOf('.')));
					} catch (Exception e) {
						CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + file.getName());
						report.getCategory().addCrashSection("OreSpawn Version", Constants.VERSION);
						OreSpawn.LOGGER.info(report.getCompleteReport());
					}
				});

	}
	
	public static void loadFromJson(String modName, JsonObject json) {
		String version = json.get("version").getAsString();
		IOS3Reader reader = null;

		switch( version ) {
		case "1":
			reader = new OS3V1Reader();
			break;
		case "1.1":
			reader = new OS3V11Reader();
			break;
		default:
			OreSpawn.LOGGER.error("Unknown version %s", version);
			return;
		}

		reader.parseJson(json, modName);
	}
}
