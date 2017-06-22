package com.mcmoddev.orespawn.data;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import net.minecraftforge.common.config.Configuration;

public class Config {
	private static Configuration configuration;
	
	private Config() {
		return;
	}
	
	public static void loadConfig() {
		configuration = new Configuration(new File(Constants.CONFIG_FILE));
		
		// Load our Boolean Values
		boolVals.put(Constants.RETROGEN_KEY, configuration.getBoolean(Constants.RETROGEN_KEY, Configuration.CATEGORY_GENERAL, false, "Do we have Retrogen active and generating anything different from the last run in already existing chunks ?"));
		boolVals.put(Constants.FORCE_RETROGEN_KEY, configuration.getBoolean(Constants.FORCE_RETROGEN_KEY, Configuration.CATEGORY_GENERAL, false, "Force all chunks to retrogen regardless of anything else"));
		boolVals.put(Constants.REPLACE_VANILLA_OREGEN,  configuration.getBoolean(Constants.REPLACE_VANILLA_OREGEN, Configuration.CATEGORY_GENERAL, false, "Replace vanilla ore-generation entirely"));
		knownKeys.add(Constants.RETROGEN_KEY);
		knownKeys.add(Constants.FORCE_RETROGEN_KEY);
		knownKeys.add(Constants.REPLACE_VANILLA_OREGEN);
	}
	
	public static boolean getBoolean(String keyname) {
		if( knownKeys.contains(keyname) && boolVals.containsKey(keyname) ) {
			if(keyname.equals(Constants.RETROGEN_KEY) || keyname.equals(Constants.FORCE_RETROGEN_KEY)) {
				return false;
			}
			return boolVals.get(keyname);
		}
		return false;
	}
	
	public static String getString(String keyname) {
		if( knownKeys.contains(keyname) && stringVals.containsKey(keyname) ) {
			return stringVals.get(keyname);
		}
		return "";
	}
	
	public static int getInt(String keyname) {
		if( knownKeys.contains(keyname) && intVals.containsKey(keyname) ) {
			return intVals.get(keyname);
		}
		return 0;
	}
	
	public static float getFloat(String keyname) {
		if( knownKeys.contains(keyname) && floatVals.containsKey(keyname) ) {
			return floatVals.get(keyname);
		}
		return 0.0f;
	}
	
	public static void saveConfig() {
		configuration.save();
	}
	
	private static final HashMap<String,Boolean> boolVals = new HashMap<>();
	private static final HashMap<String,String> stringVals = new HashMap<>();
	private static final HashMap<String,Integer> intVals = new HashMap<>();
	private static final HashMap<String,Float> floatVals = new HashMap<>();
	private static final ArrayList<String> knownKeys = new ArrayList<>();
}
