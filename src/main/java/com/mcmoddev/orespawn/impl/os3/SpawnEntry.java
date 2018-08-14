package com.mcmoddev.orespawn.impl.os3;

import java.util.Random;

import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.IBlockList;
import com.mcmoddev.orespawn.api.IDimensionList;
import com.mcmoddev.orespawn.api.os3.IFeatureEntry;
import com.mcmoddev.orespawn.api.os3.IReplacementEntry;
import com.mcmoddev.orespawn.api.os3.OreSpawnBlockMatcher;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class SpawnEntry implements com.mcmoddev.orespawn.api.os3.ISpawnEntry {

	private final String spawnName;
	private final IDimensionList dimensions;
	private final IReplacementEntry replacements;
	private final IBlockList blocks;
	private final BiomeLocation biomes;
	private final IFeatureEntry feature;
	private final boolean enabled;
	private final boolean retrogen;

	public SpawnEntry(final String spawnName, final boolean enabled, final boolean retrogen,
			final IDimensionList dimensions, final BiomeLocation biomes,
			final IReplacementEntry replacements, final IBlockList blocks,
			final IFeatureEntry feature) {
		this.spawnName = spawnName;
		this.enabled = enabled;
		this.retrogen = retrogen;
		this.dimensions = dimensions;
		this.biomes = biomes;
		this.replacements = replacements;
		this.blocks = blocks;
		this.feature = feature;
	}

	@Override
	public boolean isRetrogen() {
		return this.retrogen;
	}

	@Override
	public boolean isEnabled() {
		return this.enabled;
	}

	@Override
	public String getSpawnName() {
		return this.spawnName;
	}

	@Override
	public boolean dimensionAllowed(final int dimension) {
		return this.dimensions.matches(dimension);
	}

	@Override
	public boolean biomeAllowed(final ResourceLocation biomeName) {
		return this.biomeAllowed(ForgeRegistries.BIOMES.getValue(biomeName));
	}

	@Override
	public boolean biomeAllowed(final Biome biome) {
		return this.biomes.matches(biome);
	}

	@Override
	public IFeatureEntry getFeature() {
		return this.feature;
	}

	@Override
	public OreSpawnBlockMatcher getMatcher() {
		return this.replacements.getMatcher();
	}

	@Override
	public IBlockList getBlocks() {
		return this.blocks;
	}

	@Override
	public void generate(final Random random, final World world, final IChunkGenerator chunkGenerator,
			final IChunkProvider chunkProvider, final ChunkPos pos) {
		this.feature.getFeature().setRandom(random);
		this.feature.getFeature().generate(world, chunkGenerator, chunkProvider, this, pos);
	}

	@Override
	public IDimensionList getDimensions() {
		return this.dimensions;
	}

	@Override
	public BiomeLocation getBiomes() {
		return this.biomes;
	}
}
