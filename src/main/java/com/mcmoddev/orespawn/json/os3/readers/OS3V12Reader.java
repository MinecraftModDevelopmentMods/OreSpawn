package com.mcmoddev.orespawn.json.os3.readers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.BiomeBuilder;
import com.mcmoddev.orespawn.api.os3.BuilderLogic;
import com.mcmoddev.orespawn.api.os3.DimensionBuilder;
import com.mcmoddev.orespawn.api.os3.FeatureBuilder;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.impl.os3.DimensionBuilderImpl;
import com.mcmoddev.orespawn.impl.os3.SpawnBuilderImpl;
import com.mcmoddev.orespawn.json.os3.IOS3Reader;

import net.minecraft.block.state.IBlockState;

public class OS3V12Reader implements IOS3Reader {

	@Override
	public void parseJson(JsonObject entries, String fileName) {
		JsonArray elements = entries.get(ConfigNames.DIMENSIONS).getAsJsonArray();
		
		BuilderLogic logic = OreSpawn.API.getLogic(FilenameUtils.getBaseName(fileName));
		List<DimensionBuilder> builders = new ArrayList<>();

		for (JsonElement element : elements ) {
			JsonObject object = element.getAsJsonObject();

			int dimension = object.has(ConfigNames.DIMENSION) ? object.get(ConfigNames.DIMENSION).getAsInt() : OreSpawn.API.dimensionWildcard();
			DimensionBuilder builder = logic.newDimensionBuilder(dimension);
			List<SpawnBuilder> spawns = new ArrayList<>();
			
			JsonArray ores = object.get(ConfigNames.ORES).getAsJsonArray();

			for (JsonElement oresEntry : ores) {
				SpawnBuilder spawn = builder.newSpawnBuilder(null);				
				JsonObject ore = oresEntry.getAsJsonObject();
				OreBuilder oreB = spawn.newOreBuilder();				
				
				FeatureBuilder feature = spawn.newFeatureBuilder(ore.get(ConfigNames.FEATURE).getAsString());
				feature.setParameters(ore.get(ConfigNames.PARAMETERS).getAsJsonObject());

				String replaceBase = ore.get(ConfigNames.REPLACEMENT).getAsString();
				IBlockState blockRep = Helpers.getReplacement(replaceBase, dimension).get(0);

				BiomeBuilder biomes = spawn.newBiomeBuilder();

				if (ore.has(ConfigNames.BIOMES)) {
					biomes.setFromBiomeLocation(Helpers.deserializeBiomeLocationList(ore.get(ConfigNames.BIOMES).getAsJsonArray()));
				}

				parseOres( ore, spawn, spawns, biomes, feature, Arrays.asList(blockRep), oreB );
				
				List<IBlockState> repBlock = new ArrayList<>();
				repBlock.add(blockRep);

				//unlike others, in v1.2 "block" isn't required, as "blocks" may be specified instead
			}
			builder.create(spawns.toArray(new SpawnBuilderImpl[spawns.size()]));
			builders.add(builder);
		}
		
		logic.create(builders.toArray(new DimensionBuilderImpl[builders.size()]));

		OreSpawn.API.registerLogic(logic);
	}

	private void parseOres(JsonObject ore, SpawnBuilder spawn, List<SpawnBuilder> spawns, BiomeBuilder biomes, FeatureBuilder feature, List<IBlockState> repBlock, OreBuilder oreB) {
		if(ore.has(ConfigNames.BLOCK) && !ore.has(ConfigNames.BLOCKS)) {
			String oreName = ore.get(ConfigNames.BLOCK).getAsString();
			
			Helpers.handleState(ore,oreB, oreName);
			spawn.create(biomes, feature, repBlock, oreB);
			spawns.add(spawn);
		} else if(ore.has(ConfigNames.BLOCKS)) {
			List<OreBuilder> oreSpawns = new ArrayList<>();
			for( JsonElement oreSpawn : ore.getAsJsonArray(ConfigNames.BLOCKS) ) {
				JsonObject oreObj = oreSpawn.getAsJsonObject();
				if( oreObj.get(ConfigNames.BLOCK).getAsString().toLowerCase().startsWith("ore:") ) {
					oreSpawns.addAll( Helpers.loadOreDict( oreObj, spawn ) );
				} else {
					oreSpawns.add( Helpers.parseOreEntry( oreObj, spawn) );
				}
			}
			
			// configs pre-2.0 don't have this, so to duplicate classic
			// action...
			spawn.retrogen(true);
			spawn.enabled(true);
			
			spawn.create(biomes, feature, repBlock, oreSpawns.toArray(new OreBuilder[1]));
			spawns.add(spawn);
		}
	}
}
