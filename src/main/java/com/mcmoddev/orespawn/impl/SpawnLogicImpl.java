package com.mcmoddev.orespawn.impl;

import com.google.common.collect.ImmutableMap;
import com.mcmoddev.orespawn.api.DimensionLogic;
import com.mcmoddev.orespawn.api.SpawnLogic;

import java.util.HashMap;
import java.util.Map;

public class SpawnLogicImpl implements SpawnLogic {
    private final Map<Integer, DimensionLogic> dimensionLogic = new HashMap<>();

    @Override
    public DimensionLogic getDimension(int dimension) {
        DimensionLogic logic = this.dimensionLogic.get(dimension);

        if (logic == null) {
            this.dimensionLogic.put(dimension, logic = new DimensionLogicImpl(this));
        }

        return logic;
    }

    @Override
    public Map<Integer, DimensionLogic> getAllDimensions() {
        return ImmutableMap.copyOf(this.dimensionLogic);
    }
}
