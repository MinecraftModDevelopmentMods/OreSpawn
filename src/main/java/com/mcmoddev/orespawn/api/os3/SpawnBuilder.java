package com.mcmoddev.orespawn.api.os3;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.api.BiomeLocation;

import net.minecraft.block.state.IBlockState;

public interface SpawnBuilder {
	FeatureBuilder FeatureBuilder( @Nullable String featureName );
	BiomeBuilder BiomeBuilder();
	OreBuilder OreBuilder();
	SpawnBuilder create( @Nonnull BiomeBuilder biomes, @Nonnull FeatureBuilder feature, 
			@Nonnull List<IBlockState> replacements, @Nonnull OreBuilder... ores );
	
	BiomeLocation getBiomes();
	ImmutableList<OreBuilder> getOres();
	ImmutableList<IBlockState> getReplacementBlocks();
	FeatureBuilder getFeatureGen();
}
