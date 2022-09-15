package com.mcmoddev.orespawn.data;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {
	public static final String VERSION = "4.0.0";
	public static final String CRASH_SECTION = "OreSpawn Version";
	public static final Path SYSCONF = Paths.get(FileBits.CONFIG_DIR, FileBits.OS4, FileBits.SYSCONF);
	public static final Path JSONPATH = Paths.get(FileBits.CONFIG_DIR, FileBits.OS4);
	public static class ConfigNames {

		public static final String REPLACEMENT = "replaces";
		public static final String FEATURE = "feature";
		public static final String DIMENSIONS = "dimensions";
		public static final String BIOMES = "biomes";
		public static final String BLOCKS = "blocks";
		public static final String PARAMETERS = "parameters";
		public static final String VERSION = "version";
		public static final String SPAWNS = "spawns";
		public static final String ENABLED = "enabled";
		public static final String RETROGEN = "retrogen";
	}
	public static class FileBits {
		public static final String CONFIG_DIR = "config";
		public static final String OS4 = "mmd-orespawn-4";
		public static final String SYSCONF = "sysconf";
		public static final String PRESETS = "presets.json";
		public static final String ALLOWED_MODS = "active_mods.json";
		public static final String DISK = "__DISK__";
		public static final String RESOURCE = "__RESOURCE__";
		public static final String RESOURCE_PATH = "/assets/orespawn-data";
	}
	public enum FileTypes {
		FEATURES, SPAWN, PRESETS, REPLACEMENTS
	}
}
