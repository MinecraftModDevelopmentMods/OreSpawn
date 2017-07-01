package com.mcmoddev.orespawn.api.os3;

import com.mcmoddev.orespawn.api.IFeature;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

public interface OS3API {
	// register replacement blocks
	void registerReplacementBlock( String name, Block itemBlock );
	void registerReplacementBlock( String name, IBlockState itemBlock );

	//register feature generators
	void registerFeatureGenerator( String name, IFeature feature );
	void registerFeatureGenerator( String name, Class<? extends IFeature> feature );

	// at this level all we track are the dimensions, really
	DimensionBuilder DimensionBuilder( String name );
	DimensionBuilder DimensionBuilder( int id ); // legacy support - referencing dimensions this way needs to stop

	// register dimension spawn data created with one of the DimensionBuilders
	void register( DimensionData... dimensions );
}