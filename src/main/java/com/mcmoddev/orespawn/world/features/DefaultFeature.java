package com.mcmoddev.orespawn.world.features;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.data.BiomeMatcher;
import com.mcmoddev.orespawn.data.DimensionMatcher;
import com.mcmoddev.orespawn.world.gen.configs.DefaultFeatureConfig;
import com.mcmoddev.orespawn.world.gen.configs.OreSpawnFeature;
import com.mojang.serialization.Codec;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkGenerator;

import javax.annotation.Nonnull;
import java.util.Random;

public class DefaultFeature extends OreSpawnFeature<DefaultFeatureConfig> {
	private BiomeMatcher biomeMatcher;
	private DimensionMatcher dimensionMatcher;

	public DefaultFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(@Nonnull ISeedReader reader, @Nonnull ChunkGenerator chunkGenerator, @Nonnull Random rand, @Nonnull BlockPos pos, @Nonnull DefaultFeatureConfig config) {
		if (biomeMatcher == null) biomeMatcher = new BiomeMatcher(config.biomeMatch);
		if (dimensionMatcher == null) dimensionMatcher = new DimensionMatcher(config.dimensionMatch);
		if (config.parameters.frequency <= rand.nextFloat()) {
			ResourceLocation b = reader.getBiome(pos).getRegistryName();
			if (biomeMatcher.matches(b)) {
				RegistryKey<World> dx = reader.getWorld().getDimensionKey();//.getRegistryName();
				ResourceLocation d = dx.getLocation();

				if (dimensionMatcher.matches(d)) {
					return placeFeature(reader, chunkGenerator, pos, config);
				}
			}
		}
		return false;
	}

	private boolean placeFeature(ISeedReader reader, ChunkGenerator chunkGenerator, BlockPos pos, DefaultFeatureConfig config) {
		OreSpawn.LOGGER.info("placeFeature(%s, %s, %s) for %s", reader, chunkGenerator, pos, config.feature);
		return true;
	}

}
