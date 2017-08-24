package com.mcmoddev.orespawn.impl.os3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;

import com.google.common.collect.ImmutableMap;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.BuilderLogic;
import com.mcmoddev.orespawn.api.os3.DimensionBuilder;
import com.mcmoddev.orespawn.api.os3.OS3API;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.worldgen.OreSpawnWorldGen;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class OS3APIImpl implements OS3API {
	private final Map<String, BuilderLogic> logic;

    public OS3APIImpl() {
    	this.logic = new HashMap<>();
    }

	@Override
	public void registerReplacementBlock(String name, Block itemBlock) {
		this.registerReplacementBlock(name, itemBlock.getDefaultState());
	}

	@Override
	public void registerReplacementBlock(String name, IBlockState itemBlock) {
		ReplacementsRegistry.addBlock(name, itemBlock);
	}

	@Override
	public void registerFeatureGenerator(String name, String className) {
		OreSpawn.FEATURES.addFeature(name, className);
	}
	
	@Override
	public void registerFeatureGenerator(String name, IFeature feature) {
		this.registerFeatureGenerator(name, feature.getClass().getName());
	}

	@Override
	public void registerFeatureGenerator(String name, Class<? extends IFeature> feature) {
		this.registerFeatureGenerator(name, feature.getName());
	}

	@Override
	public BuilderLogic getLogic(String name) {
		if( logic.containsKey(name) ) {
			return logic.get(name);
		} else {
			BuilderLogic bl = new BuilderLogicImpl();
			logic.put(name,bl);
			return bl;
		}
	}

	@Override
	public void registerLogic(BuilderLogic logic) {
		// we do nothing - this is here for orthogonality, really
	}

	@Override
	public int dimensionWildcard() {
		return "DIMENSION_WILDCARD".hashCode();
	}

	@Override
	public int biomeWildcard() {
		return "BIOME_WILDCARD".hashCode();
	}

	@Override
	public ImmutableMap<String, BuilderLogic> getSpawns() {
		return ImmutableMap.<String, BuilderLogic>copyOf(logic);
	}

	@Override
	public void registerSpawns() {
		Map<Integer,List<SpawnBuilder>> spawns = OreSpawn.getSpawns();
    	// build a proper tracking of data for the spawner
    	for( Entry<String, BuilderLogic> ent : logic.entrySet() ) {
    		for( Entry<Integer, DimensionBuilder> dL : ent.getValue().getAllDimensions().entrySet()) {
				if( spawns.containsKey(dL.getKey()) ) {
					spawns.get(dL.getKey()).addAll(dL.getValue().getAllSpawns());
				} else {
					spawns.put(dL.getKey(), new ArrayList<>());
					spawns.get(dL.getKey()).addAll(dL.getValue().getAllSpawns());
				}
    		}
        	OreSpawn.LOGGER.info("Registered spawn logic for mod {}", ent.getKey());
    	}
    	
    	Random random = new Random();
   
        OreSpawnWorldGen worldGenerator  = new OreSpawnWorldGen(spawns, random.nextLong());

    	GameRegistry.registerWorldGenerator(worldGenerator, 100);
            
	}

}
