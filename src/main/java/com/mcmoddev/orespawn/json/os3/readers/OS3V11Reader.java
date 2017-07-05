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
import com.mcmoddev.orespawn.api.os3.BiomeBuilder;
import com.mcmoddev.orespawn.api.os3.BuilderLogic;
import com.mcmoddev.orespawn.api.os3.DimensionBuilder;
import com.mcmoddev.orespawn.api.os3.FeatureBuilder;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.impl.location.BiomeLocationComposition;
import com.mcmoddev.orespawn.impl.location.BiomeLocationDictionary;
import com.mcmoddev.orespawn.impl.location.BiomeLocationList;
import com.mcmoddev.orespawn.impl.location.BiomeLocationSingle;
import com.mcmoddev.orespawn.impl.os3.DimensionBuilderImpl;
import com.mcmoddev.orespawn.impl.os3.SpawnBuilderImpl;
import com.mcmoddev.orespawn.json.os3.IOS3Reader;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class OS3V11Reader implements IOS3Reader {

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
					if( "normal".equals(stateString) ) {
						oreB.setOre(oreName);
					} else {
						oreB.setOre(oreName, stateString);
					}
				} else {
					oreB.setOre(oreName);
				}

				FeatureBuilder feature = spawn.newFeatureBuilder(ore.get("feature").getAsString());
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
					biomes.setFromBiomeLocation(deserializeBiomeLocationList(ore.get("biomes").getAsJsonArray()));
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
	
	private BiomeLocation deserializeSingleEntry(String in) {
		if( in.contains(":") ) {
			return new BiomeLocationSingle(ForgeRegistries.BIOMES.getValue(new ResourceLocation(in)));
		} else {
			return new BiomeLocationDictionary( BiomeDictionary.Type.getType(in) );
		}
	}
	
	private BiomeLocation deserializeBiomeLocationList(JsonArray in) {
		List<BiomeLocation> myData = new ArrayList<>();
		
		in.forEach( elem -> {
			if( elem.isJsonPrimitive() ) {
				myData.add(deserializeSingleEntry(elem.getAsString()));
			} else if( elem.isJsonObject() ) {
				myData.add(deserializeBiomeLocationComposition(elem.getAsJsonObject()));
			}
		});
		
		return new BiomeLocationList(ImmutableSet.<BiomeLocation>copyOf(myData));
	}

	private BiomeLocation deserializeBiomeLocationComposition(JsonObject in) {
		BiomeLocation includes = deserializeBiomeLocationList(in.get("inclusions").getAsJsonArray());
		BiomeLocation excludes = deserializeBiomeLocationList(in.get("exclusions").getAsJsonArray());
		
		return new BiomeLocationComposition(ImmutableSet.<BiomeLocation>of(includes),
				ImmutableSet.<BiomeLocation>of(excludes));
	}

}

