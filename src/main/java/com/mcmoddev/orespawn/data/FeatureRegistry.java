package com.mcmoddev.orespawn.data;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.IFeature;

import net.minecraft.util.ResourceLocation;

/** Union of the two published OS3 feature-registry facades. */
public class FeatureRegistry {
	private final Map<String, IFeature> features = new LinkedHashMap<>();
	public Map<String, IFeature> getFeatures() { return Collections.unmodifiableMap(features); }
	public String getFeatureName(IFeature feature) { for (Map.Entry<String, IFeature> e : features.entrySet()) if (e.getValue() == feature) return e.getKey(); return null; }
	public IFeature getFeature(String name) { return features.get(name); }
	public IFeature getFeature(ResourceLocation name) { return getFeature(name.toString()); }
	public boolean hasFeature(String name) { return features.containsKey(name); }
	public boolean hasFeature(ResourceLocation name) { return hasFeature(name.toString()); }
	public boolean hasFeature(IFeature feature) { return features.containsValue(feature); }
	public void addFeature(String name, IFeature feature) { if (features.putIfAbsent(name, feature) != null) throw new IllegalArgumentException("Duplicate OS3 feature " + name); }
	public void addFeature(JsonObject feature) { }
	public void addFeature(String name, String className) { try { addFeature(name, (IFeature) Class.forName(className).newInstance()); } catch (ReflectiveOperationException e) { throw new IllegalArgumentException(e); } }
	public void loadFeaturesFile(File file) { }
	public void writeFeatures(File file) { }
}
