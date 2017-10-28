package com.mcmoddev.orespawn.json;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
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
import com.mcmoddev.orespawn.json.os3.readers.OS3V11Reader;
import com.mcmoddev.orespawn.json.os3.readers.OS3V12Reader;
import com.mcmoddev.orespawn.json.os3.readers.OS3V1Reader;
import com.mcmoddev.orespawn.json.os3.readers.OS3V2Reader;

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
		File directory = new File("config","orespawn3");
		File[] files;

		if( !directory.exists() ) {
			return;
		}

		if( !directory.isDirectory() ) {
			OreSpawn.LOGGER.fatal("OreSpawn data directory inaccessible - "+directory+" is not a directory!");
			return;
		}

		files = directory.listFiles();
		if( files.length == 0 ) {
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
						if( rawData.isEmpty() ) return;
						JsonElement full = parser.parse(rawData);
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
						case "1.2":
							reader = new OS3V12Reader();
							break;
						case "2.0":
							reader = new OS3V2Reader();
							break;
						default:
							OreSpawn.LOGGER.error("Unknown version %s", version);
							return;
						}

						spawns.add(reader.parseJson(parsed, file.getName().substring(0, file.getName().lastIndexOf('.'))));
					} catch (Exception e) {
						CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + file.getName());
						report.getCategory().addCrashSection("OreSpawn Version", Constants.VERSION);
						OreSpawn.LOGGER.info(report.getCompleteReport());
					}
				});
	}

	private void loadFeaturesAndReplacements() {
		if( Files.exists(Paths.get("config","orespawn3","sysconf")) && Files.isDirectory(Paths.get("config","orespawn3","sysconf")) ) {
			Arrays.stream( Paths.get("config","orespawn3","sysconf").toFile().listFiles() )
			.filter( file -> "json".equals(FilenameUtils.getExtension(file.getName())))
			.forEach( file -> {
				String filename = file.getName();
				if( FilenameUtils.getBaseName(filename).matches("features-.+") ) {
					OreSpawn.FEATURES.loadFeaturesFile( file );
				} else if(FilenameUtils.getBaseName(filename).matches("replacements-.+") ) {
					Replacements.load(file);
				}
			});
		}
	}
}
