package com.mcmoddev.orespawn.json.os3.readers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.json.os3.IOS3Reader;
import com.mcmoddev.orespawn.util.OS3V2PresetStorage;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.api.os3.BiomeBuilder;
import com.mcmoddev.orespawn.api.os3.BuilderLogic;
import com.mcmoddev.orespawn.api.os3.DimensionBuilder;
import com.mcmoddev.orespawn.api.os3.FeatureBuilder;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;

public class OS3V2Reader implements IOS3Reader {
	private static final OS3V2PresetStorage storage = new OS3V2PresetStorage();

	@Override
	public void parseJson(JsonObject entries, String fileName) {
		// do we have any presets ?
		boolean hasPresets = entries.has("presets");
		JsonObject spawns = entries.getAsJsonObject("spawns");
		BuilderLogic logic = OreSpawn.API.getLogic( fileName.substring(0, fileName.length() - 5) );

		if( hasPresets ) {
			for( Entry<String, JsonElement> preset : entries.get("presets").getAsJsonObject().entrySet() ) {
				String sectionName = preset.getKey();
				JsonObject entry = preset.getValue().getAsJsonObject();
				
				for( Entry<String, JsonElement> variables : entry.entrySet() ) {
					String itemName = variables.getKey();
					JsonObject varValue = variables.getValue().getAsJsonObject();
					storage.setSymbolSection(sectionName, itemName, varValue);
				}
			}
		}
		
		for( Entry<String, JsonElement> spawnEntry : spawns.entrySet() ) {
			parseSpawn( spawnEntry.getValue().getAsJsonObject(), spawnEntry.getKey(), logic );
		}
	}

	private void parseSpawn(JsonObject spawn, String spawnName, BuilderLogic fileLogic) {
		boolean enabled = spawn.has(ConfigNames.V2.ENABLED)?spawn.get(ConfigNames.V2.ENABLED).getAsBoolean():true;
		boolean retrogen = spawn.has(ConfigNames.V2.RETROGEN)?spawn.get(ConfigNames.V2.RETROGEN).getAsBoolean():false;
		String generator = spawn.get(ConfigNames.FEATURE).toString();
		JsonArray dimensions = replaceVariablesArray(spawn.get(ConfigNames.DIMENSIONS).getAsJsonArray());
		JsonArray blocks = replaceVariablesArray(spawn.get(ConfigNames.BLOCKS).getAsJsonArray());
		JsonArray biomes = replaceVariablesArray(spawn.get(ConfigNames.BIOMES).getAsJsonArray());
		JsonObject parameters = replaceVariablesObject(spawn.get(ConfigNames.PARAMETERS).getAsJsonObject());


		for( JsonElement dimElem : dimensions ) {
			DimensionBuilder thisDim = fileLogic.getDimension(dimElem.getAsInt());
			List<IBlockState> replacements = getReplacements(spawn.get(ConfigNames.REPLACEMENT).toString(), dimElem.getAsInt());
			SpawnBuilder thisSpawn = thisDim.newSpawnBuilder(spawnName);
			BiomeBuilder spawnBiomes = thisSpawn.newBiomeBuilder();
			if( biomes.size() > 0 ) {
				spawnBiomes.setFromBiomeLocation(Helpers.deserializeBiomeLocationList(biomes));
			}
			List<OreBuilder> spawnedOres = parseOres( blocks, thisSpawn );

			FeatureBuilder thisGenerator = thisSpawn.newFeatureBuilder(generator);
			thisGenerator.setParameters(parameters);
			thisSpawn.enabled(enabled);
			thisSpawn.retrogen(retrogen);
			thisSpawn.create(spawnBiomes, thisGenerator, replacements, spawnedOres.toArray(new OreBuilder[0]));
			thisDim.create(thisSpawn);
			fileLogic.create(thisDim);
		}
	}

	private List<IBlockState> getReplacements(String configField, int dimension) {
		String work = configField.toLowerCase();
		
		if( work.equals(ConfigNames.DEFAULT)) {
			return Arrays.asList( ReplacementsRegistry.getDimensionDefault(dimension) );
		} else if( work.startsWith("ore:") ) {
			NonNullList<ItemStack> ores = OreDictionary.getOres(work.substring(4));
			List<IBlockState> reps = new ArrayList<>();
			ores.forEach( ore -> reps.add(Block.getBlockFromItem(ore.getItem()).getDefaultState()));
			return reps;
		} else {
			return Arrays.asList( ForgeRegistries.BLOCKS.getValue(new ResourceLocation(configField)).getDefaultState());
		}
	}

	private List<OreBuilder> parseOres(JsonArray oreEntry, SpawnBuilder builder) {
		List<OreBuilder> rv = new ArrayList<>();
		
		for( JsonElement baseElem : oreEntry ) {
			JsonObject ore = baseElem.getAsJsonObject();
			
			String oreName = ore.get(ConfigNames.BLOCK).toString();
			OreBuilder oB = builder.newOreBuilder();
			if((ore.has(ConfigNames.STATE) && 
					!ore.get(ConfigNames.STATE).toString().equalsIgnoreCase(ConfigNames.STATE_NORMAL)) || 
					ore.has(ConfigNames.METADATA) ) {
				Helpers.handleState( ore, oB, oreName );
			} else {
				if( oreName.toLowerCase().startsWith("ore:") ) {
					rv.addAll( Helpers.loadOreDict( ore, builder) ); 
				} else {
					oB = Helpers.parseOreEntry( ore, builder );
				}
			}
			rv.add(oB);
		}
		return rv;
	}

	/**
	 * Interpolate variables in - an object with a variable reference should just be the reference
	 * @param inputObject
	 * @return
	 */
	private JsonObject replaceVariablesObject(JsonObject inputObject) {
		for( Entry<String,JsonElement> entry : inputObject.entrySet() ) {
			if( entry.getKey().toLowerCase().matches(ConfigNames.V2.VAR_MARK) ) {
				return getVarValue(entry.getValue().toString()).getAsJsonObject();
			}
		}
		return new JsonObject();
	}

	/**
	 * Interpolate variables into an array
	 * @param inputArray
	 * @return
	 */
	private JsonArray replaceVariablesArray(JsonArray inputArray) {
		JsonArray temp = new JsonArray();
		for( JsonElement elem : inputArray ) {
			if( elem.isJsonObject() ) {
				temp.add( replaceVariablesObject(elem.getAsJsonObject()) );
			} else {
				temp.add(elem);
			}
		}
		return temp;
	}

	private JsonElement getVarValue(String input) {
		if( input.startsWith("#/") ) {
			// we need to parse this properly
			String sectionName = input.substring("#/presets/".length()).split("/")[0];
			String itemName = input.substring("#/presets/".length()).split("/")[1];
			return storage.getSymbolSection(sectionName, itemName);
		} else {
			return new JsonPrimitive(input);
		}
	}

}
