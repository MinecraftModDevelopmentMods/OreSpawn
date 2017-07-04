package com.mcmoddev.orespawn.api.os3;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableMap;

public interface BuilderLogic {
	// at this level all we track are the dimensions, really
	DimensionBuilder DimensionBuilder( @Nonnull String name ); // use "+" to mean "all overworld"
	DimensionBuilder DimensionBuilder( @Nonnull int id ); // legacy support - referencing dimensions this way needs to stop
	DimensionBuilder DimensionBuilder(); // literally all dimensions
	
	// register dimension spawn data created with one of the DimensionBuilders
	BuilderLogic create( @Nonnull DimensionBuilder... dimensions );
	
	DimensionBuilder getDimension( @Nonnull String name );
	DimensionBuilder getDimension( @Nonnull int id );
	ImmutableMap<Integer,DimensionBuilder> getAllDimensions();
}
