package com.mcmoddev.orespawn.api.os3;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.data.PresetsStorage;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

public interface OS3API {

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

    void mapEntryToFile(Path p, String entryName);

    List<String> getSpawnsForFile(String fileName);

    Map<Path, List<String>> getSpawnsByFile();
}
