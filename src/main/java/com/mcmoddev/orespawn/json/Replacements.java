package com.mcmoddev.orespawn.json;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.data.ReplacementsRegistry;
import com.mcmoddev.orespawn.util.StateUtil;

import net.minecraft.block.state.IBlockState;
import net.minecraft.crash.CrashReport;

public class Replacements {
	private Replacements() {
		
	}
	public static void load(File file) {
		JsonParser parser = new JsonParser();
		String rawJson = "[]";
		JsonArray elements;
		try {
			rawJson = FileUtils.readFileToString(file);
		} catch(IOException e) {
			CrashReport report = CrashReport.makeCrashReport(e, "Failed reading config " + file.getName());
			report.getCategory().addCrashSection("OreSpawn Version", Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());
			return;
		}
		
		elements = parser.parse(rawJson).getAsJsonArray();
		
		for( JsonElement elem : elements ) {
			JsonObject obj = elem.getAsJsonObject();
			String name = obj.get("name").getAsString();
			String blockName = obj.get("blockName").getAsString();
			String blockState = obj.get("blockState").getAsString();
			ReplacementsRegistry.addBlock(name, blockName, blockState);
		}		
	}
	
	public static void save(File file) {
		Map<String,IBlockState> blocks = ReplacementsRegistry.getBlocks();
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		
		       
		if( blocks != null ) {
			JsonArray root = new JsonArray();
            for( Entry<String, IBlockState> block : blocks.entrySet() ) {
            	JsonObject entry = new JsonObject();
            	entry.addProperty("name", block.getKey());
            	entry.addProperty("blockName", block.getValue().getBlock().getRegistryName().toString());
            	entry.addProperty("blockState", StateUtil.serializeState(block.getValue()));
            	root.add(entry);
            }
    		String json = gson.toJson(root);
            try {
                FileUtils.writeStringToFile(file, StringEscapeUtils.unescapeJson(json), Charsets.UTF_8);
            } catch (IOException e) {
            	OreSpawn.LOGGER.fatal("Error writing "+file.toString()+" - "+e.getLocalizedMessage());
            }		
 		}
	}
}
