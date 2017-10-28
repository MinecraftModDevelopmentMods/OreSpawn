package com.mcmoddev.orespawn.json.os3.readers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.os3.BiomeBuilder;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.impl.location.BiomeLocationComposition;
import com.mcmoddev.orespawn.impl.location.BiomeLocationDictionary;
import com.mcmoddev.orespawn.impl.location.BiomeLocationList;
import com.mcmoddev.orespawn.impl.location.BiomeLocationSingle;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

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

	public static List<IBlockState> getReplacement(String replaceBase, int dimension) {
		if( ConfigNames.DEFAULT.equals(replaceBase) ) {
			return ReplacementsRegistry.getDimensionDefault(dimension);
		} else if( replaceBase == null ) {
			return ReplacementsRegistry.getDimensionDefault(0);
		} else {
			return Arrays.asList(ReplacementsRegistry.getBlock(replaceBase));
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
		BiomeLocation includes = in.has(ConfigNames.BiomeStuff.WHITELIST)?deserializeBiomeLocationList(in.get(ConfigNames.BiomeStuff.WHITELIST).getAsJsonArray()):deserializeBiomeLocationList(new JsonArray());
		BiomeLocation excludes = in.has(ConfigNames.BiomeStuff.BLACKLIST)?deserializeBiomeLocationList(in.get(ConfigNames.BiomeStuff.BLACKLIST).getAsJsonArray()):deserializeBiomeLocationList(new JsonArray());
		
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

	public static OreBuilder parseOreEntry(JsonObject oreSpawn, SpawnBuilder spawn) {
		String blockName = oreSpawn.has(ConfigNames.BLOCK)?ConfigNames.BLOCK:ConfigNames.BLOCK_V2;
		String oreName = oreSpawn.get(blockName).getAsString();
		int chance = oreSpawn.has(ConfigNames.CHANCE)?oreSpawn.get(ConfigNames.CHANCE).getAsInt():100;
		
		OreBuilder thisOre = spawn.newOreBuilder();
		
		handleState(oreSpawn, thisOre, oreName);
		
		thisOre.setChance(chance);
		
		return thisOre;
	}

	public static List<OreBuilder> loadOreDict( JsonObject oreObj, SpawnBuilder spawn) {
		String oreName = oreObj.get(ConfigNames.BLOCK).getAsString().split(":")[1];
		int chance = oreObj.has(ConfigNames.CHANCE)?oreObj.get(ConfigNames.CHANCE).getAsInt():100;
		List<OreBuilder> retval = new ArrayList<>();
		
		NonNullList<ItemStack> ores = OreDictionary.getOres(oreName);
		for( ItemStack ore : ores ) {
			OreBuilder thisOre = spawn.newOreBuilder();
			thisOre.setOre(ore.getItem(), ore.getMetadata());
			thisOre.setChance(chance);
			retval.add(thisOre);
		}
		
		return retval;
	}

	public static List<OreBuilder> loadOres(JsonArray oresArray, SpawnBuilder spawn) {
		List<OreBuilder> rV = new LinkedList<>();

		oresArray.forEach( oreEntry -> {
			JsonObject work = oreEntry.getAsJsonObject();
			String oreName = work.get(ConfigNames.BLOCK_V2).getAsString();
			OreBuilder ores = spawn.newOreBuilder();
			if ( work.has(ConfigNames.STATE) ||	work.has(ConfigNames.METADATA) ) {
				Helpers.handleState(work, ores, oreName);
				rV.add(ores);
			} else {
				if( oreName.toLowerCase().startsWith("ore:") ) {
					rV.addAll( Helpers.loadOreDict( work, spawn ) );
				} else {
					rV.add( Helpers.parseOreEntry( work, spawn ) );
				}
			}
		});
		
		return rV;
	}

}
