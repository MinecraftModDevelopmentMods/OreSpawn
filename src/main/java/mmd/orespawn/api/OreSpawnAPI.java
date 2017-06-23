package mmd.orespawn.api;

import java.util.Map;

import com.mcmoddev.orespawn.api.SpawnLogic;

public interface OreSpawnAPI {
    int DIMENSION_WILDCARD = "DIMENSION_WILDCARD".hashCode();

    SpawnLogic createSpawnLogic();

    SpawnLogic getSpawnLogic(String id);

    Map<String, SpawnLogic> getAllSpawnLogic();
    
    void registerSpawnLogic(String name, SpawnLogic logic);
}
