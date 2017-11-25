package com.mcmoddev.orespawn.api.os3;

import java.util.List;
import java.util.Random;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.util.OreList;

import net.minecraft.block.state.IBlockState;

public interface SpawnBuilder {
	FeatureBuilder newFeatureBuilder(@Nullable String featureName);
	BiomeBuilder newBiomeBuilder();
	OreBuilder newOreBuilder();
	SpawnBuilder create(@Nonnull BiomeBuilder biomes, @Nonnull FeatureBuilder feature,
	    @Nonnull List<IBlockState> replacements, @Nonnull OreBuilder... ores);
	SpawnBuilder create(@Nonnull BiomeBuilder biomes, @Nonnull FeatureBuilder feature,
	    @Nonnull List<IBlockState> replacements, JsonObject exDim,
	    @Nonnull OreBuilder... ores);

	BiomeLocation getBiomes();
	ImmutableList<OreBuilder> getOres();
	ImmutableList<IBlockState> getReplacementBlocks();
	FeatureBuilder getFeatureGen();

	// added for OreSpawn 3.2 and version 1.2 of the config
	OreBuilder getRandomOre(Random rand);
	OreList getOreSpawns();
	boolean enabled();
	void enabled(boolean enabled);
	boolean retrogen();
	void retrogen(boolean enabled);
	boolean hasExtendedDimensions();
	boolean extendedDimensionsMatch(int dimension);
}
