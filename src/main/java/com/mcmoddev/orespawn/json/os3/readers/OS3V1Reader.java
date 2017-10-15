package com.mcmoddev.orespawn.json.os3.readers;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.*;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.impl.os3.DimensionBuilderImpl;
import com.mcmoddev.orespawn.impl.os3.SpawnBuilderImpl;
import com.mcmoddev.orespawn.json.os3.IOS3Reader;

import net.minecraft.block.state.IBlockState;

public final class OS3V1Reader implements IOS3Reader {
	
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
				String oreName = ore.get(ConfigNames.BLOCK).getAsString();
				Helpers.handleState(ore, oreB, oreName);
				
				FeatureBuilder feature = spawn.newFeatureBuilder(null);
				feature.setGenerator(ore.get(ConfigNames.FEATURE).getAsString());
				feature.setParameters(ore.get(ConfigNames.PARAMETERS).getAsJsonObject());

				String replaceBase = ore.get(ConfigNames.REPLACEMENT).getAsString();
				IBlockState blockRep = Helpers.getReplacement(replaceBase, dimension).get(0);

				BiomeBuilder biomes = spawn.newBiomeBuilder();
				Helpers.loadBiomesV1( biomes, ore );
				
				List<IBlockState> repBlock = new ArrayList<>();
				repBlock.add(blockRep);
				
				// configs pre-2.0 don't have this, so to duplicate classic
				// action...
				spawn.retrogen(true);
				spawn.enabled(true);
				
				spawn.create(biomes, feature, repBlock, oreB);
				spawns.add(spawn);
			}
			builder.create(spawns.toArray(new SpawnBuilderImpl[spawns.size()]));
			builders.add(builder);
		}
		
		logic.create(builders.toArray(new DimensionBuilderImpl[builders.size()]));

		OreSpawn.API.registerLogic(logic);
	}
}
