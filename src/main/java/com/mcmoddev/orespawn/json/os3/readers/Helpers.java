package com.mcmoddev.orespawn.json.os3.readers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.impl.location.BiomeLocationComposition;
import com.mcmoddev.orespawn.impl.location.BiomeLocationDictionary;
import com.mcmoddev.orespawn.impl.location.BiomeLocationList;
import com.mcmoddev.orespawn.impl.location.BiomeLocationSingle;

import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

public class Helpers {

	private Helpers() {}

	private static BiomeLocation deserializeSingleEntry(String in) {
		if (in.contains(":")) {
			String[] parts = in.split(":");
			return new BiomeLocationSingle(ForgeRegistries.BIOMES.getValue(new ResourceLocation(parts[0], parts[1])));
		} else {
			return new BiomeLocationDictionary(BiomeDictionary.Type.getType(in));
		}
	}

	private static BiomeLocation deserializeBiomeLocationList(JsonArray in) {
		List<BiomeLocation> myData = new ArrayList<>();

		if (in.size() == 0) {
			return new BiomeLocationList(ImmutableSet.copyOf(Collections.emptySet()));
		}

		in.forEach(elem -> {
			if (elem.isJsonPrimitive()) {
				myData.add(deserializeSingleEntry(elem.getAsString()));
			} else if (elem.isJsonObject()) {
				myData.add(deserializeBiomeLocationComposition(elem.getAsJsonObject()));
			}
		});

		return new BiomeLocationList(ImmutableSet.copyOf(myData));
	}

	public static BiomeLocationComposition deserializeBiomeLocationComposition(JsonObject in) {
		JsonArray includeArr = in.getAsJsonArray(ConfigNames.BiomeStuff.WHITELIST);
		JsonArray excludeArr = in.getAsJsonArray(ConfigNames.BiomeStuff.BLACKLIST);

		if (includeArr == null) {
			includeArr = new JsonArray();
		}

		if (excludeArr == null) {
			excludeArr = new JsonArray();
		}

		BiomeLocation includes = null;
		BiomeLocation excludes = null;

		if (includeArr.size() > 0) {
			includes = deserializeBiomeLocationList(includeArr);
		}

		if (excludeArr.size() > 0) {
			excludes = deserializeBiomeLocationList(excludeArr);
		}

		return new BiomeLocationComposition((includes == null) ? ImmutableSet.copyOf(Collections.emptySet()) : ImmutableSet.of(includes),
		        (excludes == null) ? ImmutableSet.copyOf(Collections.emptySet()) : ImmutableSet.of(excludes));
	}

	private static void handleState(JsonObject ore, OreBuilder oreB, String oreName) {
		if (ore.has(ConfigNames.STATE)) {
			String stateString = ore.get(ConfigNames.STATE).getAsString();

			if (ConfigNames.STATE_NORMAL.equals(stateString)) {
				oreB.setOre(oreName);
			} else {
				oreB.setOre(oreName, stateString);
			}
		} else {
			if (ore.has(ConfigNames.METADATA)) {
				oreB.setOre(oreName, ore.get(ConfigNames.METADATA).getAsInt());
			} else {
				oreB.setOre(oreName);
			}
		}
	}

	private static OreBuilder parseOreEntry(JsonObject oreSpawn, SpawnBuilder spawn) {
		String blockName = oreSpawn.has(ConfigNames.BLOCK) ? ConfigNames.BLOCK : ConfigNames.BLOCK_V2;
		String oreName = oreSpawn.get(blockName).getAsString();
		int chance = oreSpawn.has(ConfigNames.CHANCE) ? oreSpawn.get(ConfigNames.CHANCE).getAsInt() : 100;

		OreBuilder thisOre = spawn.newOreBuilder();

		handleState(oreSpawn, thisOre, oreName);

		thisOre.setChance(chance);

		return thisOre;
	}

	private static List<OreBuilder> loadOreDict(JsonObject oreObj, SpawnBuilder spawn) {
		String oreName = oreObj.get(ConfigNames.BLOCK).getAsString().split(":")[1];
		int chance = oreObj.has(ConfigNames.CHANCE) ? oreObj.get(ConfigNames.CHANCE).getAsInt() : 100;
		List<OreBuilder> retval = new ArrayList<>();

		NonNullList<ItemStack> ores = OreDictionary.getOres(oreName);

		for (ItemStack ore : ores) {
			OreBuilder thisOre = spawn.newOreBuilder();
			thisOre.setOre(ore.getItem(), ore.getMetadata());
			thisOre.setChance(chance);
			retval.add(thisOre);
		}

		return retval;
	}

	public static List<OreBuilder> loadOres(JsonArray oresArray, SpawnBuilder spawn) {
		List<OreBuilder> rV = new LinkedList<>();

		oresArray.forEach(oreEntry -> {
			JsonObject work = oreEntry.getAsJsonObject();
			String oreName = work.get(ConfigNames.BLOCK_V2).getAsString();
			OreBuilder ores = spawn.newOreBuilder();
			
			if (work.has(ConfigNames.STATE) ||	work.has(ConfigNames.METADATA)) {
				Helpers.handleState(work, ores, oreName);
				rV.add(ores);
			} else {
				if (oreName.toLowerCase().startsWith("ore:")) {
					rV.addAll(Helpers.loadOreDict(work, spawn));
				} else {
					rV.add(Helpers.parseOreEntry(work, spawn));
				}
			}
		});

		return rV;
	}

}
