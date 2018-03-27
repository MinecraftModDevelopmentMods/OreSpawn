package com.mcmoddev.orespawn.api.os3;

import java.util.List;
import java.util.Map;

import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.data.PresetsStorage;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

public interface OS3API {
	public void addSpawn(ISpawnEntry spawnEntry);
	public void addFeature(String featureName, IFeature feature);
	public void addReplacement(IReplacementEntry replacementEntry);

	public Map<String, IReplacementEntry> getReplacements();
	public IReplacementEntry getReplacement(String replacementName);
	public List<ISpawnEntry> getSpawns(int dimensionID);
	public ISpawnEntry getSpawn(String spawnName);
	public Map<String, ISpawnEntry> getAllSpawns();
	public List<IBlockState> getDimensionDefaultReplacements(int dimensionID);
	
	public ISpawnBuilder getSpawnBuilder();
	public IDimensionBuilder getDimensionBuilder();
	public IFeatureBuilder getFeatureBuilder();
	public IBlockBuilder getBlockBuilder();
	public IBiomeBuilder getBiomeBuilder();
	public IReplacementBuilder getReplacementBuilder();
	public boolean featureExists(String featureName);
	public boolean featureExists(ResourceLocation featureName);
	public IFeature getFeature(String featureName);
	public IFeature getFeature(ResourceLocation featureName);
	public PresetsStorage copyPresets();
	
	public void loadConfigFiles();
	public boolean hasReplacement(ResourceLocation resourceLocation);
	public boolean hasReplacement(String name);
}