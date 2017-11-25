package com.mcmoddev.orespawn.api.os3;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableMap;

public interface BuilderLogic {
	// at this level all we track are the dimensions, really
	DimensionBuilder newDimensionBuilder(@Nonnull String name);   // use "+" to mean "all overworld"
	DimensionBuilder newDimensionBuilder(int id);   // legacy support - referencing dimensions this way needs to stop
	DimensionBuilder newDimensionBuilder(); // literally all dimensions

	// register dimension spawn data created with one of the DimensionBuilders
	BuilderLogic create(@Nonnull DimensionBuilder... dimensions);

	DimensionBuilder getDimension(@Nonnull String name);
	DimensionBuilder getDimension(int id);
	ImmutableMap<Integer, DimensionBuilder> getAllDimensions();
}
