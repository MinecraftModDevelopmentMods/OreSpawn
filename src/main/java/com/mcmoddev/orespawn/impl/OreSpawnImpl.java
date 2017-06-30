package com.mcmoddev.orespawn.impl;

import com.google.common.collect.ImmutableMap;
import com.mcmoddev.orespawn.api.DimensionLogic;
import com.mcmoddev.orespawn.api.OreSpawnAPI;
import com.mcmoddev.orespawn.api.SpawnLogic;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.worldgen.OreSpawnWorldGen;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.*;
import java.util.Map.Entry;

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
    	OreSpawn.LOGGER.info("Registered SpawnLogic for " + id);
        this.spawnLogic.put(id, spawnLogic);
    }

    public void registerSpawns() {
    	// build a proper tracking of data for the spawner
    	for( Entry<String, SpawnLogic> ent : spawnLogic.entrySet() ) {
    		for( Entry<Integer, DimensionLogic> dL : ent.getValue().getAllDimensions().entrySet()) {
				if( OreSpawn.spawns.containsKey(dL.getKey()) ) {
					OreSpawn.spawns.get(dL.getKey()).addAll(dL.getValue().getEntries());
				} else {
					OreSpawn.spawns.put(dL.getKey(), new ArrayList<>());
					OreSpawn.spawns.get(dL.getKey()).addAll(dL.getValue().getEntries());
				}
    		}
        	OreSpawn.LOGGER.info("Registered spawn logic for mod " + ent.getKey());
    	}
    	
    	Random random = new Random();
   
    	worldGenerator = new OreSpawnWorldGen(OreSpawn.spawns, random.nextLong());
    	//if (!Config.getBoolean(Constants.RETROGEN_KEY)) {
    	GameRegistry.registerWorldGenerator(worldGenerator, 100);
    	//}
            
    }
    
    public OreSpawnWorldGen getWorldGenerator() {
        return worldGenerator;
    }

	@Override
	public void addFeatureGenerator(String name, String className) {
		OreSpawn.FEATURES.addFeature(name, className);
	}
}
