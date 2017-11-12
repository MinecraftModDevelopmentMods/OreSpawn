package com.mcmoddev.orespawn.json.os3.readers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.json.os3.IOS3Reader;

public final class OS3V1Reader implements IOS3Reader {
	
	@Override
	public JsonObject parseJson(JsonObject entries, String fileName) {
		JsonArray elements = entries.get(ConfigNames.DIMENSIONS).getAsJsonArray();

		JsonObject retVal = new JsonObject();
		
		for (JsonElement element : elements ) {
			JsonObject object = element.getAsJsonObject();
			JsonArray dimData;
			int dimension = object.has(ConfigNames.DIMENSION) ? object.get(ConfigNames.DIMENSION).getAsInt() : OreSpawn.API.dimensionWildcard();

			dimData = getDimensionData(retVal, dimension);
			
			JsonArray ores = object.get(ConfigNames.ORES).getAsJsonArray();
			
			for (JsonElement oresEntry : ores) {
				JsonObject ore = oresEntry.getAsJsonObject();
				ore.addProperty("retrogen", true);
				ore.addProperty("enabled", true);
				
				
				JsonObject oreOut = handleVersionDifferences( ore, entries.get(ConfigNames.FILE_VERSION).getAsString() )
				dimData.add(oreOut);
			}
			retVal.getAsJsonObject("dimensions").add(Integer.toString(dimension), dimData);
		}
		
		return retVal;
	}

	private JsonObject handleVersionDifferences ( JsonObject ore, String version ) {
		JsonObject returnValue = new JsonObject();

		if( "1".equals(version) || "1.1".equals(version) ) {
			copyOverSingleBlock(ore, returnValue);
		}

		switch( version ) {
			case "1.2":
				ore.entrySet().forEach( prop -> returnValue.add(prop.getKey(), prop.getValue()));
				break;
			case "1.1":
				JsonArray biomes = ore.has(ConfigNames.BIOMES)?ore.getAsJsonArray(ConfigNames.BIOMES):new JsonArray();
				JsonObject biomeObj = new JsonObject();
				biomeObj.add(biomes.size()<1?ConfigNames.BiomeStuff.BLACKLIST:ConfigNames.BiomeStuff.WHITELIST, biomes);
				returnValue.add("biomes", biomeObj);
				returnValue.add("biomes", new JsonObject());
				returnValue.addProperty("name", getBlockName(ore));
				break;
			case "1":
				returnValue.add("biomes", new JsonObject());
				returnValue.addProperty("name", getBlockName(ore));
				break;
			default:
				break;
		}
		return returnValue;
	}

}
