package mmd.orespawn.api;

import java.util.Map;

public interface SpawnLogic {
    DimensionLogic getDimension(int dimension);

    Map<Integer, DimensionLogic> getAllDimensions();
}
