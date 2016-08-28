package mmd.orespawn.api;

public interface OreSpawnAPI {
    int DIMENSION_WILDCARD = "DIMENSION_WILDCARD".hashCode();

    SpawnLogic createSpawnLogic();

    SpawnLogic getSpawnLogic(String id);
}
