package com.mcmoddev.orespawn.api.os3;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.data.PresetsStorage;
import com.mcmoddev.orespawn.util.OS3V2PresetStorage;
import com.mcmoddev.orespawn.worldgen.OreSpawnWorldGen;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

/** Binary-compatible union of OS3 3.2.2 and 3.3.1 API descriptors. */
public interface OS3API {
	int dimensionWildcard();
	int biomeWildcard();
	void registerReplacementBlock(String name, Block block);
	void registerReplacementBlock(String name, IBlockState state);
	void registerFeatureGenerator(String name, IFeature feature);
	void registerFeatureGenerator(String name, Class<? extends IFeature> feature);
	void registerFeatureGenerator(String name, String className);
	BuilderLogic getLogic(String name);
	void registerLogic(BuilderLogic logic);
	ImmutableMap<String, BuilderLogic> getSpawns();
	void registerSpawns();
	OreSpawnWorldGen getGenerator();
	OS3V2PresetStorage getPresets();

	void addSpawn(ISpawnEntry spawnEntry);
	void addFeature(String featureName, IFeature feature);
	void addReplacement(IReplacementEntry replacementEntry);
	Map<String, IReplacementEntry> getReplacements();
	IReplacementEntry getReplacement(String replacementName);
	List<ISpawnEntry> getSpawns(int dimensionID);
	ISpawnEntry getSpawn(String spawnName);
	Map<String, ISpawnEntry> getAllSpawns();
	List<IBlockState> getDimensionDefaultReplacements(int dimensionID);
	ISpawnBuilder getSpawnBuilder();
	IDimensionBuilder getDimensionBuilder();
	IFeatureBuilder getFeatureBuilder();
	IBlockBuilder getBlockBuilder();
	IBiomeBuilder getBiomeBuilder();
	IReplacementBuilder getReplacementBuilder();
	boolean featureExists(String featureName);
	boolean featureExists(ResourceLocation featureName);
	IFeature getFeature(String featureName);
	IFeature getFeature(ResourceLocation featureName);
	PresetsStorage copyPresets();
	void loadConfigFiles();
	boolean hasReplacement(ResourceLocation resourceLocation);
	boolean hasReplacement(String name);
	void mapEntryToFile(Path path, String entryName);
	List<String> getSpawnsForFile(String fileName);
	Map<Path, List<String>> getSpawnsByFile();
}
