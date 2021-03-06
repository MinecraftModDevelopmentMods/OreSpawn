package com.mcmoddev.orespawn.data;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {

	public static final String MODID = "orespawn";
	public static final String NAME = "MMD OreSpawn";
	public static final String VERSION = "3.3.1";
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
	public static final String NORMAL_CLOUD = "normal-cloud";
	public static final String CLUSTERS = "clusters";
	public static final String CRASH_SECTION = "OreSpawn Version";
	public static final String KNOWN_MODS = "already-extracted";
	public static final String RETRO_BEDROCK = "Retrogen Flat Bedrock";
	public static final String FLAT_BEDROCK = "Flatten Bedrock";
	public static final String RETRO_BEDROCK_TAG = "retro-bedrock";
	public static final String BEDROCK_LAYERS = "Bedrock Thickness";
	public static final String ORESPAWN_VERSION_CRASH_MESSAGE = "OreSpawn Version";
	public static final String PRECISION = "precision";
	public static final Path CONFDIR = Paths.get(FileBits.CONFIG_DIR, FileBits.OS3);
	public static final Path SYSCONF = Paths.get(FileBits.CONFIG_DIR, FileBits.OS3, FileBits.SYSCONF);

	public final class FormatBits {

		private FormatBits() {
		}

		public static final String MAX_SPREAD = "maxSpread";
		public static final String MEDIAN_SIZE = "medianSize";
		public static final String MIN_HEIGHT = "minHeight";
		public static final String MAX_HEIGHT = "maxHeight";
		public static final String VARIATION = "variation";
		public static final String FREQUENCY = "frequency";
		public static final String NODE_SIZE = "size";
		public static final String ATTEMPTS = "attempts";
		public static final String LENGTH = "length";
		public static final String NODE_COUNT = "numObjects";
		public static final String ATTEMPTS_MIN = "minAttempts";
		public static final String ATTEMPTS_MAX = "maxAttempts";
		public static final String STARTINGFACE = "startFacing";
		public static final String FLUID = "fluid";
	}

	public final class FileBits {

		private FileBits() {
		}

		public static final String CONFIG_DIR = "config";
		public static final String OS3 = "orespawn3";
		public static final String SYSCONF = "sysconf";
		public static final String PRESETS = "presets.json";
	}

	public final class ConfigNames {

		private ConfigNames() {
		}

		public static final String DEFAULT = "default";
		public static final String STATE_NORMAL = "normal";
		public static final String DIMENSIONS = "dimensions";
		public static final String CHANCE = "chance";
		public static final String METADATA = "metaData";
		public static final String BIOMES = "biomes";
		public static final String STATE = "state";
		public static final String REPLACEMENT = "replaces";
		public static final String FEATURE = "feature";
		public static final String PARAMETERS = "parameters";
		public static final String FILE_VERSION = "version";
		public static final String BLOCK = "blocks";
		public static final String SPAWNS = "spawns";
		public static final String RETROGEN = "retrogen";
		public static final String ENABLED = "enabled";
		public static final String NAME = "name";
		public static final String WHITELIST = "includes";
		public static final String BLACKLIST = "excludes";
		public static final String PRESETS = "presets";

		public final class DefaultFeatureProperties {

			private DefaultFeatureProperties() {
			}

			public static final String SIZE = "size";
			public static final String VARIATION = "variation";
			public static final String FREQUENCY = "frequency";
			public static final String MAXHEIGHT = "maxHeight";
			public static final String MINHEIGHT = "minHeight";
		}
	}
}
