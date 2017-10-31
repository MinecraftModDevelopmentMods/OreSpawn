package com.mcmoddev.orespawn.json.os3.readers;

import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.json.os3.IOS3Reader;
import com.mcmoddev.orespawn.util.OS3V2PresetStorage;

public class OS3V2Reader implements IOS3Reader {
	private OS3V2PresetStorage storage = new OS3V2PresetStorage();

	@Override
	public OS3V2PresetStorage getStorage() {
		return this.storage;
	}
	
	@Override
	public JsonObject parseJson(JsonObject entries, String fileName) {
		// do we have any presets ?
		boolean hasPresets = entries.has("presets");
		JsonObject spawns = entries.getAsJsonObject("spawns");
		JsonObject retVal = new JsonObject();

		storage.clear();
		OreSpawn.API.getPresets().copy(storage);
		
		if( hasPresets ) {
			for( Entry<String, JsonElement> preset : entries.get("presets").getAsJsonObject().entrySet() ) {
				String sectionName = preset.getKey();
				JsonObject entry = preset.getValue().getAsJsonObject();
				
				for( Entry<String, JsonElement> variables : entry.entrySet() ) {
					String itemName = variables.getKey();
					JsonElement varValue = variables.getValue();
					storage.setSymbolSection(sectionName, itemName, varValue);
				}
			}
		}
		
		// 'work' contains the reduced, no-variables-here data
		JsonObject work = new JsonObject();
		spawns.entrySet().forEach( entry -> work.add( entry.getKey(), replaceVariables(entry.getValue().getAsJsonObject())));
		
		work.entrySet().forEach( entry -> {
			JsonObject lw = entry.getValue().getAsJsonObject();
			
			if( lw.getAsJsonArray(ConfigNames.DIMENSIONS).size() <1 ) {
				JsonArray temp = lw.getAsJsonArray(ConfigNames.DIMENSIONS);
				temp.add(OreSpawn.API.dimensionWildcard());
				lw.remove(ConfigNames.DIMENSIONS);
				lw.add(ConfigNames.DIMENSIONS, temp);
			}
			
			lw.getAsJsonArray(ConfigNames.DIMENSIONS).getAsJsonArray().forEach(
					dim -> {
						JsonObject nw = new JsonObject();
						nw.addProperty("name", entry.getKey());
						entry.getValue().getAsJsonObject().entrySet().stream()
						.filter( e -> !e.getKey().equals(ConfigNames.DIMENSIONS) )
						.forEach( ent -> nw.add( ent.getKey(), ent.getValue()));
						JsonArray thisDim = getDimensionData(retVal, dim.getAsInt());
						thisDim.add(nw);
						
						JsonObject dimStore;
						if( retVal.has(ConfigNames.DIMENSIONS) ) {
							dimStore = retVal.getAsJsonObject(ConfigNames.DIMENSIONS);
						} else {
							dimStore= new JsonObject();
						}
						dimStore.add(dim.getAsString(), thisDim);
						retVal.add(ConfigNames.DIMENSIONS, dimStore);
					});
			});
		
		return retVal;
	}
}
