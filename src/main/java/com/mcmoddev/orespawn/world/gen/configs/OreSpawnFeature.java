package com.mcmoddev.orespawn.world.gen.configs;

import com.mcmoddev.orespawn.OreSpawn;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;

import javax.annotation.Nonnull;
import java.util.Random;

public class OreSpawnFeature<FC extends IFeatureConfig, F extends Feature<FC>>  extends ConfiguredFeature<FC, F> {

	public OreSpawnFeature(FC config, F feature) {
		super(feature, config);
	}

	@Override
	public boolean generate(@Nonnull ISeedReader reader, @Nonnull ChunkGenerator chunkGenerator, @Nonnull Random rand, @Nonnull BlockPos pos) {
		OreSpawn.LOGGER.fatal("Inconceivably reached lowest level generator setup");
		return false;
	}
}
