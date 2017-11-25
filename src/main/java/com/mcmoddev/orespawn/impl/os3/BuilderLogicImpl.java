package com.mcmoddev.orespawn.impl.os3;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.BuilderLogic;
import com.mcmoddev.orespawn.api.os3.DimensionBuilder;
import com.mcmoddev.orespawn.data.Constants;

public class BuilderLogicImpl implements BuilderLogic {
	private final Map<Integer, DimensionBuilder> dimensions;

	public BuilderLogicImpl() {
		this.dimensions = new HashMap<>();
	}

	@Override
	public DimensionBuilder newDimensionBuilder(String name) {
		int id = this.dimensionNameToId(name);

		if (id == OreSpawn.API.dimensionWildcard()) {
			return this.newDimensionBuilder();
		}

		return this.newDimensionBuilder(id);
	}

	@Override
	public DimensionBuilder newDimensionBuilder(int id) {
		if (dimensions.containsKey(id)) {
			return this.getDimension(id);
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
		Integer id = this.dimensionNameToId(name);
		return this.getDimension(id);
	}

	@Override
	public DimensionBuilder getDimension(int id) {
		if (dimensions.containsKey(id)) {
			return dimensions.get(id);
		}

		return null;
	}

	@Override
	public ImmutableMap<Integer, DimensionBuilder> getAllDimensions() {
		return ImmutableMap.<Integer, DimensionBuilder>copyOf(dimensions);
	}


	private int dimensionNameToId(String name) {
		switch (name.toLowerCase(Locale.ROOT)) {
			case Constants.OVERWORLD:
			case Constants.THE_OVERWORLD:
				return 0;

			case Constants.NETHER:
			case Constants.THE_NETHER:
				return -1;

			case Constants.END:
			case Constants.THE_END:
				return 1;

			case "+":
			default:
				return OreSpawn.API.dimensionWildcard();
		}
	}

}
