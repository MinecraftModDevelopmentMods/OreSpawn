package com.mcmoddev.orespawn.impl;

import com.google.common.collect.ImmutableMap;
import com.mcmoddev.orespawn.api.DimensionLogic;
import com.mcmoddev.orespawn.api.OreSpawnAPI;
import com.mcmoddev.orespawn.api.SpawnEntry;
import com.mcmoddev.orespawn.api.SpawnLogic;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.worldgen.OreSpawnWorldGen;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.*;
import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.data.Constants;

public class OreSpawnImpl implements OreSpawnAPI {
    private final Map<String, SpawnLogic> spawnLogic = new HashMap<>();
    private static OreSpawnWorldGen worldGenerator;

    @Override
    public SpawnLogic createSpawnLogic() {
        return new SpawnLogicImpl();
    }

    @Override
    public SpawnLogic getSpawnLogic(String id) {
        return this.spawnLogic.get(id);
    }

    @Override
    public Map<String, SpawnLogic> getAllSpawnLogic() {
        return ImmutableMap.copyOf(this.spawnLogic);
    }

    public void registerSpawnLogic(String id, SpawnLogic spawnLogic) {
        this.spawnLogic.put(id, spawnLogic);

        Random random = new Random();

        worldGenerator = new OreSpawnWorldGen(spawnLogic.getAllDimensions(), random.nextLong());
        //if (!Config.getBoolean(Constants.RETROGEN_KEY)) {
        	GameRegistry.registerWorldGenerator(worldGenerator, 100);
        //}
        
        OreSpawn.LOGGER.info("Registered spawn logic for mod " + id);
    }

    public OreSpawnWorldGen getWorldGenerator() {
        return worldGenerator;
    }
}
