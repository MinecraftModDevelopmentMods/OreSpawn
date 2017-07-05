package com.mcmoddev.orespawn.json.os3.readers;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.os3.*;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.impl.location.BiomeLocationList;
import com.mcmoddev.orespawn.impl.location.BiomeLocationSingle;
import com.mcmoddev.orespawn.impl.os3.DimensionBuilderImpl;
import com.mcmoddev.orespawn.impl.os3.SpawnBuilderImpl;
import com.mcmoddev.orespawn.json.os3.IOS3Reader;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public final class OS3V1Reader implements IOS3Reader {
	
	@Override
	public void parseJson(JsonObject entries, String fileName) {
		JsonArray elements = entries.get("dimensions").getAsJsonArray();
		
		BuilderLogic logic = OreSpawn.API.getLogic(FilenameUtils.getBaseName(fileName));
		List<DimensionBuilder> builders = new ArrayList<>();

		for (JsonElement element : elements ) {
			JsonObject object = element.getAsJsonObject();

			int dimension = object.has("dimension") ? object.get("dimension").getAsInt() : OreSpawn.API.dimensionWildcard();
			
			DimensionBuilder builder = logic.newDimensionBuilder(dimension);
			List<SpawnBuilder> spawns = new ArrayList<>();
			
			JsonArray ores = object.get("ores").getAsJsonArray();

			for (JsonElement oresEntry : ores) {
				SpawnBuilder spawn = builder.newSpawnBuilder(null);
				
				JsonObject ore = oresEntry.getAsJsonObject();

				OreBuilder oreB = spawn.newOreBuilder();
				
				String oreName = ore.get("block").getAsString();
				
				if (ore.has("state")) {
					String stateString = ore.get("state").getAsString();
					oreB.setOre(oreName, stateString);
				} else {
					oreB.setOre(oreName);
				}

				FeatureBuilder feature = spawn.newFeatureBuilder(null);
				feature.setGenerator(ore.get("feature").getAsString());
				feature.setParameters(ore.get("parameters").getAsJsonObject());

				String replaceBase = ore.get("replace_block").getAsString();
				IBlockState blockRep;

				if( "default".equals(replaceBase) ) {
					blockRep = ReplacementsRegistry.getDimensionDefault(dimension);
				} else if( replaceBase == null ) {
					blockRep = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft:stone")).getDefaultState();
				} else {
					blockRep = ReplacementsRegistry.getBlock(replaceBase);
				}

				BiomeBuilder biomes = spawn.newBiomeBuilder();

				if (ore.has("biomes")) {
					JsonArray biomesArray = ore.get("biomes").getAsJsonArray();

					if( biomesArray.size() > 0 ) {
						BiomeLocationList bL = parseBiomeList(biomesArray);
						biomes.setFromBiomeLocation(bL);
					}
				}
				
				List<IBlockState> repBlock = new ArrayList<>();
				repBlock.add(blockRep);
				spawn.create(biomes, feature, repBlock, oreB);
				spawns.add(spawn);
			}
			builder.create(spawns.toArray(new SpawnBuilderImpl[spawns.size()]));
			builders.add(builder);
		}
		
		logic.create(builders.toArray(new DimensionBuilderImpl[builders.size()]));

		OreSpawn.API.registerLogic(logic);
	}

	private BiomeLocationList parseBiomeList(JsonArray biomesArray) {
		List<BiomeLocation> biomes = new ArrayList<>();
		
		biomesArray.forEach( elem -> {
			String p = elem.getAsString();
			biomes.add(new BiomeLocationSingle(ForgeRegistries.BIOMES.getValue(new ResourceLocation(p))));
		});
		return new BiomeLocationList(ImmutableSet.<BiomeLocation>copyOf(biomes));
	}
}
