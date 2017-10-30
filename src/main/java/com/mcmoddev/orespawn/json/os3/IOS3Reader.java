package com.mcmoddev.orespawn.json.os3;

import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.data.Constants.ConfigNames;
import com.mcmoddev.orespawn.util.OS3V2PresetStorage;

public interface IOS3Reader {

	default OS3V2PresetStorage getStorage() { return null; }
	
	JsonObject parseJson(JsonObject entries, String fileName);
	

	default void copyOverSingleBlock(JsonObject ore, JsonObject oreOut) {
		JsonObject blockEntry = new JsonObject();
		for( Entry<String, JsonElement> prop : ore.entrySet() ) {
			switch( prop.getKey() ) {
			case ConfigNames.BLOCK:
			case ConfigNames.BLOCKID:
				blockEntry.addProperty(ConfigNames.BLOCK_V2, prop.getValue().getAsString());
				break;
			case ConfigNames.METADATA:
				blockEntry.addProperty(ConfigNames.METADATA, prop.getValue().getAsNumber());
				break;
			case ConfigNames.STATE:
				blockEntry.addProperty(ConfigNames.STATE, prop.getValue().getAsString());
				break;
			case ConfigNames.CHANCE:
				blockEntry.addProperty(ConfigNames.CHANCE, prop.getValue().getAsNumber());
				break;
			case ConfigNames.REPLACEMENT:
				oreOut.addProperty(ConfigNames.REPLACEMENT_V2, prop.getValue().getAsString());
				break;
			default:
				if( !oreOut.has(prop.getKey()) ) 
					oreOut.add(prop.getKey(), prop.getValue());	
			}
		}

		if( !blockEntry.has(ConfigNames.CHANCE) )
			blockEntry.addProperty(ConfigNames.CHANCE, 100);
		
		if( !oreOut.has(ConfigNames.BLOCKS) ) {
			oreOut.add(ConfigNames.BLOCKS, new JsonArray());
		}
		
		JsonArray temp = oreOut.getAsJsonArray(ConfigNames.BLOCKS);
		temp.add(blockEntry);
		oreOut.add(ConfigNames.BLOCKS, temp);
	}

	default void normalizeBlockData( JsonObject ore, JsonObject oreOut ) {
		JsonObject blockEntry = new JsonObject();
		String blockName;
		
		if( ore.has(ConfigNames.STATE) )
			blockEntry.addProperty(ConfigNames.STATE, ore.get(ConfigNames.STATE).getAsString());
		else if( ore.has(ConfigNames.METADATA) )
			blockEntry.addProperty(ConfigNames.METADATA, ore.get(ConfigNames.METADATA).getAsInt());
		
		if( ore.has(ConfigNames.BLOCKID) )
			blockName = ore.get(ConfigNames.BLOCKID).getAsString();
		else if( ore.has(ConfigNames.BLOCK) )
			blockName = ore.get(ConfigNames.BLOCK).getAsString();
		else
			blockName = "i_am_a_dumbass";
		
		blockEntry.addProperty(ConfigNames.BLOCK_V2, blockName);
		
		oreOut.add(ConfigNames.BLOCKS, blockEntry);
	}
	
	default JsonArray getDimensionData(JsonObject retVal, int dimension) {
		if( !retVal.has("dimensions") ) {
			retVal.add("dimensions", new JsonObject());
		}
		
		if( !retVal.getAsJsonObject("dimensions").has(String.format("%d", dimension))) {
			retVal.getAsJsonObject("dimensions").add(String.format("%d", dimension), new JsonArray());
		}
		
		return retVal.getAsJsonObject("dimensions").getAsJsonArray(String.format("%d", dimension));
	}
	
	/**
	 * called with a JsonObject that has to be iterated, copied and all variable references replaced with the data they
	 * refer to
	 * @param objReplace object to do variable replacement on
	 * @return copy of objReplace with all variables interpolated in
	 */
	default JsonObject replaceVariables( JsonObject objReplace ) {
		JsonObject retVal = new JsonObject();
		
		objReplace.entrySet().forEach( entry -> retVal.add( entry.getKey(), replaceVariablesBase(entry.getValue())));
		
		return retVal;
	}
	
	default JsonElement replaceVariablesBase(JsonElement value) {
		if( value.isJsonPrimitive() && value.getAsString().startsWith("$.") ) {
			return replaceVariablePrimitive(value);
		} else if( value.isJsonPrimitive() ) {
			return value;
		} else if( value.isJsonArray() ) {
			return replaceVariableArray(value);
		} else if( value.isJsonObject() ) {
			return replaceVariables(value.getAsJsonObject());
		} else {
			return value;
		}
	}

	default JsonElement replaceVariableArray(JsonElement value) {
		JsonArray retVal = new JsonArray();
		
		value.getAsJsonArray().forEach( item -> retVal.add( replaceVariablesBase( item )));
		return retVal;
	}

	// variable looks like: "$.<section>.<item>"
	default JsonElement replaceVariablePrimitive(JsonElement value) {
		String rawVal = value.getAsString().substring(2);
		String[] bits = rawVal.split("\\.");
		String section = bits[0];
		String item = bits[1];
		return getStorage().getSymbolSection(section, item);
	}

}
