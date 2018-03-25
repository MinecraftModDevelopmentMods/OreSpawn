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
import com.mcmoddev.orespawn.api.os3.IDimensionBuilder;
import com.mcmoddev.orespawn.api.os3.OS3API;
import com.mcmoddev.orespawn.api.os3.ISpawnBuilder;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.util.OS3V2PresetStorage;
import com.mcmoddev.orespawn.worldgen.OreSpawnWorldGen;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class OS3APIImpl implements OS3API {
	private final Map<String, BuilderLogic> logic;
	private OreSpawnWorldGen generator;
	private OS3V2PresetStorage presets;

	public OS3APIImpl() {
		this.logic = new HashMap<>();
		this.presets = new OS3V2PresetStorage();
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
		if (logic.containsKey(name)) {
			return logic.get(name);
		} else {
			BuilderLogic bl = new BuilderLogicImpl();
			logic.put(name, bl);
			return bl;
		}
	}

	@Override
	public void registerLogic(BuilderLogic logic) {
		// we do nothing - this is here for orthogonality, really
	}

	@Override
	public int dimensionWildcard() {
		return 0xCAFEBABE;
	}

	@Override
	public int biomeWildcard() {
		return 0xF00DF00D;
	}

	@Override
	public ImmutableMap<String, BuilderLogic> getSpawns() {
		return ImmutableMap.<String, BuilderLogic>copyOf(logic);
	}

	@Override
	public void registerSpawns() {
		Map<Integer, List<ISpawnBuilder>> spawns = OreSpawn.getSpawns();

		// build a proper tracking of data for the spawner
		for (Entry<String, BuilderLogic> ent : logic.entrySet()) {
			for (Entry<Integer, IDimensionBuilder> dL : ent.getValue().getAllDimensions().entrySet()) {
				if (spawns.containsKey(dL.getKey())) {
					spawns.get(dL.getKey()).addAll(dL.getValue().getAllSpawns());
				} else {
					spawns.put(dL.getKey(), new ArrayList<>());
					spawns.get(dL.getKey()).addAll(dL.getValue().getAllSpawns());
				}
			}

			OreSpawn.LOGGER.info("Registered spawn logic from data-file (maybe mod) %s", ent.getKey());
		}

		Random random = new Random();

		this.generator = new OreSpawnWorldGen(spawns, random.nextLong());

		GameRegistry.registerWorldGenerator(generator, 100);

	}

	@Override
	public OreSpawnWorldGen getGenerator() {
		return this.generator;
	}

	@Override
	public OS3V2PresetStorage getPresets() {
		return this.presets;
	}
}
