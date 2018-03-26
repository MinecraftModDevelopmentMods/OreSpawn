package com.mcmoddev.orespawn.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.mcmoddev.orespawn.OreSpawn;

import net.minecraft.crash.CrashReport;

public class PresetsStorage {
	private final Map<String, Map<String, JsonElement>> storage;
	private static final String ORE_SPAWN_VERSION = "OreSpawn Version";

	public PresetsStorage() {
		storage = new TreeMap<>();
	}

	public void setSymbolSection(String sectionName, String itemName, JsonElement value) {
		Map<String, JsonElement> temp = storage.getOrDefault(sectionName, new HashMap<String, JsonElement>());
		temp.put(itemName, value);
		storage.put(sectionName, temp);
	}

	public JsonElement getSymbolSection(String sectionName, String itemName) {
		if (storage.containsKey(sectionName) && storage.get(sectionName).containsKey(itemName)) {
			return storage.get(sectionName).get(itemName);
		} else {
			return new JsonPrimitive(itemName);
		}
	}

	public void copy(PresetsStorage dest) {
		storage.entrySet().stream()
		.forEach(ensm -> {
			String section = ensm.getKey();
			ensm.getValue().entrySet().forEach(ensje -> dest.setSymbolSection(section, ensje.getKey(), ensje.getValue()));
		});
	}

	public void clear() {
		this.storage.clear();
	}

	public void load(Path inputFile) {
		JsonParser p = new JsonParser();
		JsonElement parsed = null;
		
		try( BufferedReader r = Files.newBufferedReader(inputFile)) {
			parsed = p.parse(r);
		} catch (IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e, "Failed reading presets from" + inputFile.toString());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		} catch(JsonIOException | JsonSyntaxException e) {
			CrashReport report = CrashReport.makeCrashReport(e, "JSON Parsing Error in " + inputFile.toString());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		}
		
		if(parsed != null) {
			parsed.getAsJsonObject().entrySet().stream()
			.forEach( entry -> {
				String section = entry.getKey();
				entry.getValue().getAsJsonObject().entrySet().stream()
				.forEach( sect -> this.setSymbolSection(section, sect.getKey(), sect.getValue()));
			});
		}		
	}

	public JsonElement get(String asString) {
		Pattern p = Pattern.compile("\\$\\.(\\w+)\\.(\\w+)");
		Matcher m = p.matcher(asString);
		if(m.matches()) {
			return this.getSymbolSection(m.group(1), m.group(2));
		}
		JsonPrimitive pm = new JsonPrimitive("Unknown Variable "+asString);
		return pm;
	}

}
