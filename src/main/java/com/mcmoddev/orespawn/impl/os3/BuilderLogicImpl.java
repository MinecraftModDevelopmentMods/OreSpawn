package com.mcmoddev.orespawn.impl.os3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.BuilderLogic;
import com.mcmoddev.orespawn.api.os3.DimensionBuilder;

public class BuilderLogicImpl implements BuilderLogic {
	private final Map<Integer, DimensionBuilder> dimensions;
	
	public BuilderLogicImpl() {
		this.dimensions = new HashMap<>();
	}
	
	@Override
	public DimensionBuilder newDimensionBuilder(String name) {
		switch(name.toLowerCase(Locale.ROOT)) {
		case "overworld":
			return this.newDimensionBuilder(0);
		case "nether":
		case "the nether":
			return this.newDimensionBuilder(-1);
		case "end":
		case "the end":
			return this.newDimensionBuilder(1);
		case "+":
		default:
			// assume that they want the wildcard
			return this.newDimensionBuilder();
		}
	}

	@Override
	public DimensionBuilder newDimensionBuilder(int id) {
		if( dimensions.containsKey(id) ) {
			return this.getDimension( id );
		}
		DimensionBuilder db = new DimensionBuilderImpl();
		dimensions.put(id, db);
		return db;
	}

	@Override
	public DimensionBuilder newDimensionBuilder() {
		return this.newDimensionBuilder(OreSpawn.API.dimensionWildcard());
	}

	@Override
	public BuilderLogic create(DimensionBuilder... dimensions) {
		// for future expansion/orthagonality
		return this;
	}

	@Override
	public DimensionBuilder getDimension(String name) {
		// I'd love for the following to be realistic...
		// return this.DimensionBuilder(name);
		Integer id = null;
		switch(name.toLowerCase(Locale.ROOT)) {
		case "overworld":
			id = 0;
			break;
		case "nether":
		case "the nether":
			id = -1;
			break;
		case "end":
		case "the end":
			id = 1;
			break;
		case "+":
		default:
			// assume that they want the wildcard
			id = OreSpawn.API.dimensionWildcard();
		}
		
		return this.getDimension(id);
	}

	@Override
	public DimensionBuilder getDimension(int id) {
		if( dimensions.containsKey(id) ) {
			return dimensions.get(id);
		}
		
		return null;
	}

	@Override
	public ImmutableMap<Integer, DimensionBuilder> getAllDimensions() {
		return ImmutableMap.<Integer, DimensionBuilder>copyOf(dimensions);
	}

}
