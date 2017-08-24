package com.mcmoddev.orespawn.json.os3.readers;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.os3.BiomeBuilder;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.impl.location.BiomeLocationComposition;
import com.mcmoddev.orespawn.impl.location.BiomeLocationDictionary;
import com.mcmoddev.orespawn.impl.location.BiomeLocationList;
import com.mcmoddev.orespawn.impl.location.BiomeLocationSingle;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class Helpers {

	private Helpers() {}
	
	public static void loadBiomesV1(BiomeBuilder biomes, JsonObject ore) {
		if (ore.has(ConfigNames.BIOMES)) {
			JsonArray biomesArray = ore.get(ConfigNames.BIOMES).getAsJsonArray();

			if( biomesArray.size() > 0 ) {
				BiomeLocationList bL = parseBiomeList(biomesArray);
				biomes.setFromBiomeLocation(bL);
			}
		}
	}

	public static IBlockState getReplacement(String replaceBase, int dimension) {
		if( ConfigNames.DEFAULT.equals(replaceBase) ) {
			return ReplacementsRegistry.getDimensionDefault(dimension);
		} else if( replaceBase == null ) {
			return ForgeRegistries.BLOCKS.getValue(new ResourceLocation("minecraft:stone")).getDefaultState();
		} else {
			return ReplacementsRegistry.getBlock(replaceBase);
		}
	}

	public static BiomeLocationList parseBiomeList(JsonArray biomesArray) {
		List<BiomeLocation> biomes = new ArrayList<>();
		
		biomesArray.forEach( elem -> {
			String p = elem.getAsString();
			biomes.add(new BiomeLocationSingle(ForgeRegistries.BIOMES.getValue(new ResourceLocation(p))));
		});
		return new BiomeLocationList(ImmutableSet.<BiomeLocation>copyOf(biomes));
	}

	public static BiomeLocation deserializeSingleEntry(String in) {
		if( in.contains(":") ) {
			return new BiomeLocationSingle(ForgeRegistries.BIOMES.getValue(new ResourceLocation(in)));
		} else {
			return new BiomeLocationDictionary( BiomeDictionary.Type.getType(in) );
		}
	}
	
	public static BiomeLocation deserializeBiomeLocationList(JsonArray in) {
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

	public static BiomeLocation deserializeBiomeLocationComposition(JsonObject in) {
		BiomeLocation includes = deserializeBiomeLocationList(in.get(ConfigNames.BiomeStuff.WHITELIST).getAsJsonArray());
		BiomeLocation excludes = deserializeBiomeLocationList(in.get(ConfigNames.BiomeStuff.BLACKLIST).getAsJsonArray());
		
		return new BiomeLocationComposition(ImmutableSet.<BiomeLocation>of(includes),
				ImmutableSet.<BiomeLocation>of(excludes));
	}

	public static void handleState(JsonObject ore, OreBuilder oreB, String oreName) {
		if (ore.has(ConfigNames.STATE)) {
			String stateString = ore.get(ConfigNames.STATE).getAsString();
			if( ConfigNames.STATE_NORMAL.equals(stateString) ) {
				oreB.setOre(oreName);
			} else {
				oreB.setOre(oreName, stateString);
			}
		} else {
			if(ore.has(ConfigNames.METADATA)) {
				oreB.setOre(oreName, ore.get(ConfigNames.METADATA).getAsInt());
			} else {
				oreB.setOre(oreName);
			}
		}
	}

}
