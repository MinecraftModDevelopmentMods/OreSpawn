package com.mcmoddev.orespawn.json.os3.readers;

import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.json.os3.IOS3Reader;

public class OS3V2Reader implements IOS3Reader {
	@Override
	public JsonObject parseJson(JsonObject entries, String fileName) {
		// do we have any presets ?
		boolean hasPresets = entries.has("presets");
		JsonObject spawns = entries.getAsJsonObject("spawns");
		JsonObject retVal = new JsonObject();

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
		
		// normalized data looks like the v1.2 format in a lot of ways
		// {
		//   "dimensions": {
		//     "dimension_id_as_string":
		//       [
		//         ... entry set ...
		//       ]
		//    }
		// }
		
		work.entrySet().forEach( entry -> {
			JsonObject lw = entry.getValue().getAsJsonObject();
			lw.getAsJsonArray(ConfigNames.DIMENSIONS).getAsJsonArray().forEach(
					dim -> {
						JsonObject nw = new JsonObject();
						entry.getValue().getAsJsonObject().entrySet().stream()
						.filter( e -> !e.getKey().equals(ConfigNames.DIMENSIONS) )
						.forEach( ent -> nw.add( ent.getKey(), ent.getValue()));
						JsonArray thisDim = getDimensionData(retVal, dim.getAsInt());
						thisDim.add(nw);
						if( retVal.has(ConfigNames.DIMENSIONS) ) {
							JsonObject temp = retVal.getAsJsonObject(ConfigNames.DIMENSIONS);
							temp.add(dim.getAsString(), thisDim);
							retVal.add(ConfigNames.DIMENSIONS, temp);
						} else {
							JsonObject temp = new JsonObject();
							temp.add(dim.getAsString(), thisDim);
							retVal.add(ConfigNames.DIMENSIONS, temp);
						}
					});
			});
		
		return retVal;
	}
}
