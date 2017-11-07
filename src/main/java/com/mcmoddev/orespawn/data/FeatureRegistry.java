package com.mcmoddev.orespawn.data;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

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
import com.mcmoddev.orespawn.impl.features.ClusterGenerator;
import com.mcmoddev.orespawn.impl.features.DefaultFeatureGenerator;
import com.mcmoddev.orespawn.impl.features.NormalCloudGenerator;
import com.mcmoddev.orespawn.impl.features.PrecisionGenerator;
import com.mcmoddev.orespawn.impl.features.VeinGenerator;

import net.minecraft.crash.CrashReport;

public class FeatureRegistry {
	private static final String ORE_SPAWN_VERSION = "OreSpawn Version";
	private Map<String, IFeature> features;
	private Map<IFeature, String> featuresInverse;
	
	public FeatureRegistry() {
		features = new HashMap<>();
		featuresInverse = new HashMap<>();
		IFeature defaultGen = new DefaultFeatureGenerator();
		features.put(Constants.DEFAULT_GEN, defaultGen);
		featuresInverse.put(defaultGen, Constants.DEFAULT_GEN);
		IFeature veinGen = new VeinGenerator();
		features.put(Constants.VEIN_GEN, veinGen);
		featuresInverse.put(veinGen, Constants.VEIN_GEN);
		IFeature normalCloudGen = new NormalCloudGenerator();
		features.put(Constants.NORMAL_CLOUD, normalCloudGen);
		featuresInverse.put(normalCloudGen, Constants.NORMAL_CLOUD);
		IFeature clusterGen = new ClusterGenerator();
		features.put(Constants.CLUSTERS, clusterGen);
		featuresInverse.put(clusterGen, Constants.CLUSTERS);
		IFeature precision = new PrecisionGenerator();
		features.put(Constants.PRECISION, precision);
		featuresInverse.put(precision, Constants.PRECISION);
	}
	
	public Map<String, IFeature> getFeatures() {
		return Collections.unmodifiableMap(features);
	}
	
	public String getFeatureName(IFeature feature) {
		if( this.hasFeature(feature) ) {
			return this.featuresInverse.get(feature);
		} else {
			return Constants.DEFAULT_GEN;
		}
	}

	public IFeature getFeature(String name) {
		if( this.hasFeature(name) ) {
			return this.features.get(name);
		} else {
			return this.features.get(Constants.DEFAULT_GEN);
		}
	}
	
	public boolean hasFeature(String name) {
		return features.containsKey(name);
	}
	
	public boolean hasFeature(IFeature feature) {
		return featuresInverse.containsKey(feature);
	}

	public void addFeature(String name, IFeature feature ) {
		this.addFeature(name, feature.getClass().getName());
	}
	
	public void addFeature(JsonObject entry) {
		this.addFeature(entry.get("name").getAsString(), entry.get("class").getAsString());
	}
	
	public void addFeature(String name, String className) {
		IFeature feature = getInstance(className);
		if( feature != null && !features.containsKey(name) ) {
				features.put(name, feature);
				featuresInverse.put(feature, name);
		}		
	}
	
	private IFeature getInstance(String className) {
		Class<?> featureClazz;
		Constructor<?> featureCons;
		IFeature feature = null;
		try {
			featureClazz = Class.forName(className);
			featureCons = featureClazz.getConstructor();
			feature = (IFeature)featureCons.newInstance();
		} catch(Exception e) {
			CrashReport report = CrashReport.makeCrashReport(e, "Failed to load and instantiate an instance of the feature generator named "+className+" that was specified as a feature generator");
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
			return null;
		}
		return feature; 
	}
	
	public void loadFeaturesFile(File file) {
		JsonParser parser = new JsonParser();
		String rawJson = "[]";
		JsonArray elements;
		try {
			rawJson = FileUtils.readFileToString(file, Charset.defaultCharset());
		} catch(IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + file.getName());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
			return;
		}
		
		elements = parser.parse(rawJson).getAsJsonArray();
		
		for( JsonElement elem : elements ) {
			this.addFeature(elem.getAsJsonObject());
		}		
	}
	
	public void writeFeatures(File file) {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		JsonArray root = new JsonArray();
		       
		if( !features.equals(Collections.<String,IFeature>emptyMap()) ) {
            for( Entry<String, IFeature> feature : features.entrySet() ) {
            	JsonObject entry = new JsonObject();
            	entry.addProperty("name", feature.getKey());
            	entry.addProperty("class", feature.getValue().getClass().getName());
            	root.add(entry);
            }
 		}

		String json = gson.toJson(root);
        try {
            FileUtils.writeStringToFile(file, StringEscapeUtils.unescapeJson(json), CharEncoding.UTF_8);
        } catch (IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e, "Failed writing config " + file.getName());
			report.getCategory().addCrashSection(ORE_SPAWN_VERSION, Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
			return;
        }		
	}
}
