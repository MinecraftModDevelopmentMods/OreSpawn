package com.mcmoddev.orespawn.impl.os3;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;

import net.minecraft.block.state.IBlockState;

import com.mcmoddev.orespawn.api.os3.FeatureBuilder;
import com.mcmoddev.orespawn.api.os3.BiomeBuilder;
import com.mcmoddev.orespawn.api.os3.OreBuilder;

public class SpawnBuilderImpl implements SpawnBuilder {
	private BiomeLocation biomeLocs;
	private FeatureBuilder featureGen;
	private List<IBlockState> replacementBlocks;
	private List<OreBuilder> myOres;

	public SpawnBuilderImpl() {
		this.biomeLocs = null;
		this.featureGen = null;
		this.replacementBlocks = new ArrayList<>();
		this.myOres = new ArrayList<>();
	}
	
	@Override
	public FeatureBuilder newFeatureBuilder(@Nullable String featureName) {
		this.featureGen = new FeatureBuilderImpl();
		if( OreSpawn.FEATURES.getFeature(featureName) != null ) {
			this.featureGen.setGenerator(featureName);
		}
		
		return this.featureGen;
	}

	@Override
	public BiomeBuilder newBiomeBuilder() {
		return new BiomeBuilderImpl();
	}

	@Override
	public OreBuilder newOreBuilder() {
		return new OreBuilderImpl();
	}
	
	@Override
	public SpawnBuilder create(BiomeBuilder biomes, FeatureBuilder feature,
			List<IBlockState> replacements, OreBuilder... ores) {
		this.biomeLocs = biomes.getBiomes();
		this.featureGen = feature;
		this.replacementBlocks.addAll(replacements);
		int oc = (int)(100.0f / ((float)ores.length));
		if( ores.length > 1 ) {
			for(int i = 0; i < ores.length; i++) {
				OreBuilder current = ores[i];
				if( current.getChance() == 100 ) { 
					current.setChance(oc);
				}
				this.myOres.add(current);
			}
		} else {
			this.myOres.add(ores[0]);
		}
		return this;
	}

	@Override
	public BiomeLocation getBiomes() {
		return this.biomeLocs;
	}

	@Override
	public ImmutableList<OreBuilder> getOres() {
		return ImmutableList.<OreBuilder>copyOf(this.myOres);
	}

	@Override
	public ImmutableList<IBlockState> getReplacementBlocks() {
		return ImmutableList.<IBlockState>copyOf(this.replacementBlocks);
	}

	@Override
	public FeatureBuilder getFeatureGen() {
		return this.featureGen;
	}

}
