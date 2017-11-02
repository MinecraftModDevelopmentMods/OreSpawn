package com.mcmoddev.orespawn.impl.os3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.util.OreList;

import net.minecraft.block.state.IBlockState;

import com.mcmoddev.orespawn.api.os3.FeatureBuilder;
import com.mcmoddev.orespawn.api.os3.BiomeBuilder;
import com.mcmoddev.orespawn.api.os3.OreBuilder;

public class SpawnBuilderImpl implements SpawnBuilder {
	private BiomeLocation biomeLocs;
	private FeatureBuilder featureGen;
	private List<IBlockState> replacementBlocks;
	private List<OreBuilder> myOres;
	private OreList oreList;
	private boolean enabled = true;
	private boolean retrogen = false;
	
	public SpawnBuilderImpl() {
		this.biomeLocs = null;
		this.featureGen = null;
		this.replacementBlocks = new ArrayList<>();
		this.myOres = new ArrayList<>();
	}
	
	@Override
	public FeatureBuilder newFeatureBuilder(@Nullable String featureName) {
		String featName;
		this.featureGen = new FeatureBuilderImpl();
		if( OreSpawn.FEATURES.getFeature(featureName) == null || featureName == null) {
			featName = "default";
		} else {
			featName = featureName;
		}
		
		this.featureGen.setGenerator(featName);
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
		
		if( ores.length > 1 ) {
			for(int i = 0; i < ores.length; i++) {
				this.myOres.add(ores[i]);
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

	private void buildSpawnList() {
		if( this.oreList != null ) return;

		this.oreList = new OreList();
		
		this.oreList.build(Collections.unmodifiableList(this.myOres));
	}

	@Override
	public boolean enabled() {
		return this.enabled;
	}

	@Override
	public void enabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public boolean retrogen() {
		return this.retrogen;
	}

	@Override
	public void retrogen(boolean enabled) {
		this.retrogen = enabled;
	}

	@Override
	public OreBuilder getRandomOre(Random rand) {
		if( this.oreList == null )
			this.buildSpawnList();
		
		return this.oreList.getRandomOre(rand);
	}

	@Override
	public OreList getOreSpawns() {
		if( this.oreList == null )
			this.buildSpawnList();
		
		return this.oreList;
	}
}
