package com.mcmoddev.orespawn.api.os3;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.util.BinaryTree;

import net.minecraft.block.state.IBlockState;

public interface SpawnBuilder {
	FeatureBuilder newFeatureBuilder( @Nullable String featureName );
	BiomeBuilder newBiomeBuilder();
	OreBuilder newOreBuilder();
	SpawnBuilder create( @Nonnull BiomeBuilder biomes, @Nonnull FeatureBuilder feature, 
			@Nonnull List<IBlockState> replacements, @Nonnull OreBuilder... ores );
	
	BiomeLocation getBiomes();
	ImmutableList<OreBuilder> getOres();
	ImmutableList<IBlockState> getReplacementBlocks();
	FeatureBuilder getFeatureGen();
	
	// added for OreSpawn 3.2 and version 1.2 of the config
	BinaryTree getOreSpawns();
	boolean enabled();
	void enabled(boolean enabled);
	boolean retrogen();
	void retrogen(boolean enabled);
}
