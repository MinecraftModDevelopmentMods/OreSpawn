package com.mcmoddev.orespawn.world.gen.configs;

import com.mcmoddev.orespawn.data.BiomeMatcher;
import com.mcmoddev.orespawn.data.DimensionMatcher;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.Feature;

import javax.annotation.Nonnull;
import java.util.Random;

public class DefaultFeature extends OreSpawnFeature {
	private final DefaultFeatureConfig myConfig;
	private final BiomeMatcher biomeMatcher;
	private final DimensionMatcher dimensionMatcher;

	public DefaultFeature(DefaultFeatureConfig config) {
		super(config, Feature.ORE);
		myConfig = config;
		biomeMatcher = new BiomeMatcher(config.biomeMatch);
		dimensionMatcher = new DimensionMatcher(config.dimensionMatch);
	}

	@Override
	public boolean generate(@Nonnull ISeedReader reader, @Nonnull ChunkGenerator chunkGenerator, @Nonnull Random rand, @Nonnull BlockPos pos) {
		if (myConfig.parameters.frequency <= rand.nextFloat()) {
			ResourceLocation b = reader.getBiome(pos).getRegistryName();
			if (biomeMatcher.matches(b)) {
				ResourceLocation d = reader.getWorld().getDimensionKey().getRegistryName();
				if (dimensionMatcher.matches(d)) {
					return placeFeature(reader, chunkGenerator, pos);
				}
			}
		}
		return false;
	}

	private boolean placeFeature(ISeedReader reader, ChunkGenerator chunkGenerator, BlockPos pos) {
		return true;
	}

}
