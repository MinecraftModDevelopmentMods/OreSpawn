package com.mcmoddev.orespawn.world.gen.configs;

import com.mcmoddev.orespawn.OreSpawn;
import com.mojang.serialization.Codec;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;

import javax.annotation.Nonnull;
import java.util.Random;

public class OreSpawnFeature<FC extends IFeatureConfig> extends Feature<FC> {
	public OreSpawnFeature(Codec<FC> configCodec) {
		super(configCodec);
	}

	@Override
	public boolean generate(@Nonnull ISeedReader reader, @Nonnull ChunkGenerator generator, @Nonnull Random rand, @Nonnull BlockPos pos, @Nonnull FC config) {
		OreSpawn.LOGGER.fatal("Inconceivably reached lowest level generator setup");
		return false;
	}
}
