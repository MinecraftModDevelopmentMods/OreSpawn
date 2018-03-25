package com.mcmoddev.orespawn.impl.os3;

import com.mcmoddev.orespawn.api.IBlockList;
import com.mcmoddev.orespawn.api.IDimensionList;
import com.mcmoddev.orespawn.api.os3.IFeatureEntry;
import com.mcmoddev.orespawn.api.os3.IReplacementEntry;
import com.mcmoddev.orespawn.api.os3.OreSpawnBlockMatcher;
import com.mcmoddev.orespawn.impl.location.BiomeLocationComposition;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class SpawnEntry implements com.mcmoddev.orespawn.api.os3.ISpawnEntry {
	private final String spawnName;
	private final IDimensionList dimensions;
	private final IReplacementEntry replacements;
	private final IBlockList blocks;
	private final BiomeLocationComposition biomes;
	private final IFeatureEntry feature;
	private final boolean enabled;
	private final boolean retrogen;
	
	public SpawnEntry(final String spawnName, final boolean enabled, final boolean retrogen,
			final IDimensionList dimensions, final BiomeLocationComposition biomes,
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
	public boolean dimensionAllowed(int dimension) {
		return this.dimensions.matches(dimension);
	}

	@Override
	public boolean biomeAllowed(ResourceLocation biomeName) {
		return this.biomeAllowed(ForgeRegistries.BIOMES.getValue(biomeName));
	}

	@Override
	public boolean biomeAllowed(Biome biome) {
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

}
