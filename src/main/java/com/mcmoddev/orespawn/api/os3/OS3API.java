package com.mcmoddev.orespawn.api.os3;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableMap;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.worldgen.OreSpawnWorldGen;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

public interface OS3API {
	int dimensionWildcard();
	int biomeWildcard();
	
	// register replacement blocks
	void registerReplacementBlock( @Nonnull String name, @Nonnull Block itemBlock );
	void registerReplacementBlock( @Nonnull String name, @Nonnull IBlockState itemBlock );

	//register feature generators
	void registerFeatureGenerator( @Nonnull String name, @Nonnull IFeature feature );
	void registerFeatureGenerator( @Nonnull String name, @Nonnull Class<? extends IFeature> feature );
	void registerFeatureGenerator( @Nonnull String name, @Nonnull String className );
	
	BuilderLogic getLogic( @Nonnull String name );
	void registerLogic( @Nonnull BuilderLogic logic );
	ImmutableMap<String, BuilderLogic> getSpawns();
	void registerSpawns();
	
	OreSpawnWorldGen getGenerator();
}