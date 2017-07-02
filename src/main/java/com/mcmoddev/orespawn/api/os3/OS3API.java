package com.mcmoddev.orespawn.api.os3;

import javax.annotation.Nonnull;

import com.mcmoddev.orespawn.api.IFeature;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

public interface OS3API {
	// register replacement blocks
	void registerReplacementBlock( @Nonnull String name, @Nonnull Block itemBlock );
	void registerReplacementBlock( @Nonnull String name, @Nonnull IBlockState itemBlock );

	//register feature generators
	void registerFeatureGenerator( @Nonnull String name, @Nonnull IFeature feature );
	void registerFeatureGenerator( @Nonnull String name, @Nonnull Class<? extends IFeature> feature );

	BuilderLogic getLogic( @Nonnull String name );
	void registerLogic( @Nonnull BuilderLogic logic );
}