package com.mcmoddev.orespawn.api.os3;

import java.util.List;
import java.util.Optional;

import com.mcmoddev.orespawn.api.BiomeLocation;

import net.minecraft.block.state.IBlockState;

public interface SpawnBuilder {
	FeatureBuilder FeatureBuilder( Optional<String> featureName );
	BiomeBuilder BiomeBuilder();
	OreBuilder OreBuilder();
	SpawnData create( BiomeLocation biomes, FeatureData feature, List<IBlockState> replacements, IBlockState... ores );
}
