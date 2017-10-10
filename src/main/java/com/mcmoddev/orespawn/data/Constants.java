package com.mcmoddev.orespawn.data;

public class Constants {
	public static final String MODID = "orespawn";
	public static final String NAME = "MMD OreSpawn";
	public static final String VERSION = "3.1.0";
	public static final String RETROGEN_KEY = "Retrogen";
	public static final String CONFIG_FILE = "config/orespawn.cfg";
	public static final String FORCE_RETROGEN_KEY = "Force Retrogen";
	public static final String CHUNK_TAG_NAME = "MMD OreSpawn Data";
	public static final String ORE_TAG = "ores";
	public static final String FEATURES_TAG = "features";
	public static final String REPLACE_VANILLA_OREGEN = "Replace Vanilla Oregen";
	public static final String OVERWORLD = "overworld";
	public static final String THE_OVERWORLD = "the overworld";
	public static final String NETHER = "nether";
	public static final String THE_NETHER = "the nether";
	public static final String END = "end";
	public static final String THE_END = "the end";
	public static final String DEFAULT_GEN = "default";
	public static final String VEIN_GEN = "vein";
	
	public final class ConfigNames {
		private ConfigNames() {}
		public static final String DEFAULT = "default";
		public static final String STATE_NORMAL = "normal";
		public static final String DIMENSION = "dimension";
		public static final String ORES = "ores";
		public static final String DIMENSIONS = "dimensions";
		public static final String BLOCKID = "blockID";
		public static final String BLOCK = "block";
		public static final String METADATA = "metaData";
		public static final String BIOMES = "biomes";
		public static final String STATE = "state";
		public static final String REPLACEMENT = "replace_block";
		public static final String FEATURE = "feature";
		public static final String PARAMETERS = "parameters";
		public static final String FILE_VERSION = "version";
		public final class BiomeStuff {
			private BiomeStuff() {}
			public static final String WHITELIST = "inclusions";
			public static final String BLACKLIST = "exclusions";
		}
		public final class DefaultFeatureProperties {
			private DefaultFeatureProperties() {}
			public static final String SIZE = "size";
			public static final String VARIATION = "variation";
			public static final String FREQUENCY = "frequency";
			public static final String MAXHEIGHT = "maxHeight";
			public static final String MINHEIGHT = "minHeight";
		}
	}
}
