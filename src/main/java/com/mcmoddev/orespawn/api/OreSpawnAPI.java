package com.mcmoddev.orespawn.api;

import java.util.Map;

import com.mcmoddev.orespawn.api.SpawnLogic;
import com.mcmoddev.orespawn.worldgen.OreSpawnWorldGen;

public interface OreSpawnAPI {
    int DIMENSION_WILDCARD = "DIMENSION_WILDCARD".hashCode();

    SpawnLogic createSpawnLogic();

    SpawnLogic getSpawnLogic(String id);

    Map<String, SpawnLogic> getAllSpawnLogic();
    
    void registerSpawnLogic(String name, SpawnLogic logic);
    
    OreSpawnWorldGen getWorldGenerator();
    
    void registerSpawns();
}
