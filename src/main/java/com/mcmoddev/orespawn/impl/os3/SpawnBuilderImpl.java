package com.mcmoddev.orespawn.impl.os3;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.os3.*;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.util.OreList;
import net.minecraft.block.state.IBlockState;
import org.apache.commons.lang3.ArrayUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class SpawnBuilderImpl implements SpawnBuilder {
	private BiomeLocation biomeLocs;
	private FeatureBuilder featureGen;
	private List<IBlockState> replacementBlocks;
	private List<OreBuilder> myOres;
	private OreList oreList;
	private boolean enabled = true;
	private boolean retrogen = false;
	private boolean extendedDimensionsBool = false;
	private DimensionList extendedDimensions;

	SpawnBuilderImpl() {
		this.biomeLocs = null;
		this.featureGen = null;
		this.replacementBlocks = new ArrayList<>();
		this.myOres = new ArrayList<>();
	}

	@Override
	public FeatureBuilder newFeatureBuilder(@Nullable String featureName) {
		String featName;
		this.featureGen = new FeatureBuilderImpl();

		if (OreSpawn.FEATURES.getFeature(featureName) == null || featureName == null) {
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
	public SpawnBuilder create(@Nonnull BiomeBuilder biomes, @Nonnull FeatureBuilder feature,
	    @Nonnull List<IBlockState> replacements, @Nonnull OreBuilder... ores) {
		this.biomeLocs = biomes.getBiomes();
		this.featureGen = feature;
		this.replacementBlocks.addAll(replacements);

		if (ores.length > 1) {
			this.myOres.addAll(Arrays.asList(ores));
		} else {
			this.myOres.add(ores[0]);
		}

		return this;
	}

	@Override
	public SpawnBuilder create(@Nonnull BiomeBuilder biomes, @Nonnull FeatureBuilder feature,
	    @Nonnull List<IBlockState> replacements, JsonObject exDim,
	    @Nonnull OreBuilder... ores) {
		this.create(biomes, feature, replacements, ores);

		this.setupDimensionWhitelist(exDim);
		return this;
	}

	private void setupDimensionWhitelist(JsonObject exDim) {
		JsonArray whitelist = exDim.getAsJsonArray(Constants.ConfigNames.DimensionStuff.INCLUDE);
		JsonArray blacklist = exDim.getAsJsonArray(Constants.ConfigNames.DimensionStuff.EXCLUDE);
		List<Integer> tempW = new ArrayList<> ();
		List<Integer> tempB = new ArrayList<> ();

		if (whitelist != null) {
			whitelist.forEach(it -> tempW.add(it.getAsInt()));
		}

		if (blacklist != null) {
			blacklist.forEach(it -> tempB.add(it.getAsInt()));
		}

		this.extendedDimensionsBool = true;
		this.extendedDimensions = new DimensionListImpl();
		this.extendedDimensions.create(ArrayUtils.toPrimitive(tempW.toArray(new Integer[0])),
		    ArrayUtils.toPrimitive(tempB.toArray(new Integer[0])));
	}

	@Override
	public BiomeLocation getBiomes() {
		return this.biomeLocs;
	}

	@Override
	public ImmutableList<OreBuilder> getOres() {
		return ImmutableList.copyOf(this.myOres);
	}

	@Override
	public ImmutableList<IBlockState> getReplacementBlocks() {
		return ImmutableList.copyOf(this.replacementBlocks);
	}

	@Override
	public FeatureBuilder getFeatureGen() {
		return this.featureGen;
	}

	private void buildSpawnList() {
		if (this.oreList != null) {
			return;
		}

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
	public boolean hasExtendedDimensions() {
		return this.extendedDimensionsBool;
	}

	@Override
	public boolean extendedDimensionsMatch(int dimension) {
		return !this.extendedDimensionsBool || this.extendedDimensions.match(dimension);
	}

	@Override
	public OreBuilder getRandomOre(Random rand) {
		if (this.oreList == null) {
			this.buildSpawnList();
		}

		return this.oreList.getRandomOre(rand);
	}

	@Override
	public OreList getOreSpawns() {
		if (this.oreList == null) {
			this.buildSpawnList();
		}

		return this.oreList;
	}
}
