package com.mcmoddev.orespawn.data;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.codec.CharEncoding;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.IFeature;

import net.minecraft.crash.CrashReport;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;

public class FeatureRegistry {
	private static final String ORE_SPAWN_VERSION = "OreSpawn Version";
	private static final IForgeRegistry<IFeature> registry = new RegistryBuilder<IFeature>()
			.setName(new ResourceLocation("orespawn", "feature_registry"))
			.setType(IFeature.class)
			.setMaxID(4096)  // 12 bits should be enough... hell, 8 bits would be, IMNSHO
			.create();

	public FeatureRegistry() {
	}

	public Map<String, IFeature> getFeatures() {
		Map<String,IFeature> tempMap = new TreeMap<>();
		registry.getEntries().stream()
		.forEach(e -> tempMap.put(e.getKey().toString(), e.getValue()));
		
		return Collections.unmodifiableMap(tempMap);
	}

	public String getFeatureName(IFeature feature) {
		return feature.getRegistryName().toString();
	}

	public IFeature getFeature(String name) {
		return getFeature(new ResourceLocation(name));
	}
	
	public IFeature getFeature(ResourceLocation featureResourceLocation) {
		ResourceLocation defaultGen = new ResourceLocation(Constants.DEFAULT_GEN);
		if (registry.containsKey(featureResourceLocation)) {
			return registry.getValue(featureResourceLocation);
		} else {
			return registry.getValue(defaultGen);
		}
	}

	public boolean hasFeature(String name) {
		return hasFeature(new ResourceLocation(name));
	}

	public boolean hasFeature(ResourceLocation featureResourceLocation) {
		return registry.containsKey(featureResourceLocation);
	}
	public boolean hasFeature(IFeature feature) {
		return registry.containsKey(feature.getRegistryName());
	}

	public void addFeature(String name, IFeature feature) {
		feature.setRegistryName(new ResourceLocation(name));
		registry.register(feature);
	}

	public void addFeature(JsonObject entry) {
		addFeature(entry.get("name").getAsString(), entry.get("class").getAsString());
	}

	public void addFeature(String name, String className) {
		IFeature feature = getInstance(className);

		if (feature != null && !hasFeature(name)) {
			addFeature(name, feature);
		}
	}

	private static IFeature getInstance(String className) {
		Class<?> featureClazz;
		Constructor<?> featureCons;
		IFeature feature;

		try {
			featureClazz = Class.forName(className);
			featureCons = featureClazz.getConstructor();
			feature = (IFeature)featureCons.newInstance();
		} catch (Exception e) {
			CrashReport report = CrashReport.makeCrashReport(e, "Failed to load and instantiate an instance of the feature generator named " + className + " that was specified as a feature generator");
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
			return null;
		}

		return feature;
	}

	public void loadFeaturesFile(File file) {
		JsonParser parser = new JsonParser();
		String rawJson;
		JsonArray elements;

		try {
			rawJson = FileUtils.readFileToString(file, Charset.defaultCharset());
		} catch (IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + file.getName());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
			return;
		}

		elements = parser.parse(rawJson).getAsJsonArray();

		for (JsonElement elem : elements) {
			addFeature(elem.getAsJsonObject());
		}
	}

	public void writeFeatures(File file) {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		JsonArray root = new JsonArray();

		registry.getEntries().stream()
		.map( ent -> { 
			JsonObject e = new JsonObject(); 
			e.addProperty("name", ent.getKey().getResourcePath());
			e.addProperty("class", ent.getValue().getClass().getName());
			return e;
		})
		.forEach( root::add );

		String json = gson.toJson(root);

		try {
			FileUtils.writeStringToFile(file, StringEscapeUtils.unescapeJson(json), CharEncoding.UTF_8);
		} catch (IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e, "Failed writing config " + file.getName());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
		}
	}
}
