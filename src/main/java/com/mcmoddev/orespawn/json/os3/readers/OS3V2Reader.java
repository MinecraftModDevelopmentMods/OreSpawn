package com.mcmoddev.orespawn.json.os3.readers;

import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
		JsonObject spawns = entries.getAsJsonObject("spawns");
		JsonObject retVal = new JsonObject();

		storage.clear();
		OreSpawn.API.getPresets().copy(storage);

		loadLocalPresets(entries);

		// 'work' contains the reduced, no-variables-here data
		JsonObject work = new JsonObject();
		spawns.entrySet().forEach(entry -> work.add(entry.getKey(), replaceVariables(entry.getValue().getAsJsonObject())));

		work.entrySet().forEach(entry -> {
			JsonObject lw = setHandleDimensions(entry.getValue().getAsJsonObject());

			lw.getAsJsonArray(ConfigNames.DIMENSIONS).getAsJsonArray().forEach(
			dim -> {
				JsonObject nw = new JsonObject();
				nw.addProperty("name", entry.getKey());

				if (lw.has(ConfigNames.DIMENSION))
					nw.add(ConfigNames.DIMENSION, lw.getAsJsonObject(ConfigNames.DIMENSION));

				entry.getValue().getAsJsonObject().entrySet().stream()
				.filter(e -> !e.getKey().equals(ConfigNames.DIMENSIONS))
				.forEach(ent -> nw.add(ent.getKey(), ent.getValue()));
				JsonArray thisDim = getDimensionData(retVal, dim.getAsInt());
				thisDim.add(nw);

				JsonObject dimStore;
				if (retVal.has(ConfigNames.DIMENSIONS)) {
					dimStore = retVal.getAsJsonObject(ConfigNames.DIMENSIONS);
				} else {
					dimStore = new JsonObject();
				}
				dimStore.add(dim.getAsString(), thisDim);
				retVal.add(ConfigNames.DIMENSIONS, dimStore);
			});
		});

		return retVal;
	}

	private JsonObject setHandleDimensions(JsonObject spawnEntry) {
		JsonObject lw = spawnEntry;

		if (lw.get(ConfigNames.DIMENSIONS).isJsonArray()) {
			if (lw.getAsJsonArray(ConfigNames.DIMENSIONS).size() < 1) {
				JsonArray temp = lw.getAsJsonArray(ConfigNames.DIMENSIONS);
				temp.add(new JsonPrimitive(OreSpawn.API.dimensionWildcard()));
				lw.remove(ConfigNames.DIMENSIONS);
				lw.add(ConfigNames.DIMENSIONS, temp);
			}
		} else {
			JsonObject dimSet = lw.getAsJsonObject(ConfigNames.DIMENSIONS);
			lw.add(ConfigNames.DIMENSION, dimSet);
			lw.remove(ConfigNames.DIMENSIONS);
			JsonArray temp = new JsonArray();
			temp.add(new JsonPrimitive(OreSpawn.API.dimensionWildcard()));
			lw.add(ConfigNames.DIMENSIONS, temp);
		}

		return lw;
	}

	private void loadLocalPresets(JsonObject entries) {
		boolean hasPresets = entries.has("presets");

		if (hasPresets) {
			for (Entry<String, JsonElement> preset : entries.get("presets").getAsJsonObject().entrySet()) {
				String sectionName = preset.getKey();
				JsonObject entry = preset.getValue().getAsJsonObject();

				for (Entry<String, JsonElement> variables : entry.entrySet()) {
					String itemName = variables.getKey();
					JsonElement varValue = variables.getValue();
					storage.setSymbolSection(sectionName, itemName, varValue);
				}
			}
		}
	}
}
