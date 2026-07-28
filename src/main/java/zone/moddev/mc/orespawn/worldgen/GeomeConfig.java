package zone.moddev.mc.orespawn.worldgen;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Collections;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import zone.moddev.mc.orespawn.OreSpawnConfig;
import zone.moddev.mc.orespawn.OreSpawnConfig.OreGenerationSettings;
import zone.moddev.mc.orespawn.api.OreSpawnOreIntegration;
import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager;
import zone.moddev.mc.orespawn.worldgen.BakedGeomeConfig.GeomeDefinition;
import zone.moddev.mc.orespawn.worldgen.BakedGeomeConfig.RockEntry;
import zone.moddev.mc.orespawn.worldgen.FormationSettings.Algorithm;
import zone.moddev.mc.orespawn.worldgen.FormationSettings.Preset;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class GeomeConfig {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = Paths.get("config", "orespawn-worldgen.json");
	private static final Path CONFIG_BACKUP_PATH = Paths.get("config", "orespawn-worldgen.v1.bak");
	private static final Path CONFIG_V2_BACKUP_PATH = Paths.get("config", "orespawn-worldgen.v2.bak");
	private static final Path CONFIG_V3_BACKUP_PATH = Paths.get("config", "orespawn-worldgen.v3.bak");
	private static final Path CONFIG_V4_BACKUP_PATH = Paths.get("config", "orespawn-worldgen.v4.bak");
	private static final Path CONFIG_V5_BACKUP_PATH = Paths.get("config", "orespawn-worldgen.v5.bak");
	private static final Path BIOME_DEFAULTS_BACKUP_PATH = Paths.get("config",
			"orespawn-worldgen.pre-biome-revision-3.bak");
	private static final Path WORLDGEN_ALIAS_DEFAULTS_BACKUP_PATH = Paths.get("config",
			"orespawn-worldgen.pre-alias-revision-1.bak");
	private static final Path ORE_DEFAULTS_BACKUP_PATH = Paths.get("config",
			"orespawn-worldgen.pre-ore-revision-9.bak");
	private static final Path CONFIG_TEMP_PATH = Paths.get("config", "orespawn-worldgen.json.tmp");
	private static final Path PROVIDER_DEFAULTS_BACKUP_PATH = Paths.get("config",
			"orespawn-worldgen.pre-provider-defaults.bak");
	public static final int SCHEMA_VERSION = 6;
	private static final int BIOME_DEFAULTS_REVISION = 3;
	private static final int WORLDGEN_ALIAS_DEFAULTS_REVISION = 1;
	private static final int ORE_DEFAULTS_REVISION = 10;
	private static final String LEGACY_MINERALOGY_VANILLA_ORE_PREFIX = "mineralogy:ore/minecraft/";
	private static final String[] VANILLA_ORE_IDS = {
			"coal_ore", "iron_ore", "copper_ore", "gold_ore", "redstone_ore",
			"diamond_ore", "lapis_ore", "emerald_ore", "nether_gold_ore",
			"nether_quartz_ore", "ancient_debris"
	};

	private static volatile BakedGeomeConfig bakedConfig = null;
	private static volatile Map<ResourceKey<Level>, BakedGeomeConfig> bakedConfigs = Collections.emptyMap();
	private static volatile Map<ResourceKey<Level>, BakedTerrainDimension> terrainDimensions = Collections.emptyMap();
	private static JsonObject globalConfigRoot = null;
	private static JsonObject globalBaseConfigRoot = null;
	private static volatile WorldGeologyProfile globalProfile = null;
	private static volatile WorldGeologyProfile globalBaseProfile = null;

	private GeomeConfig() {
		throw new IllegalAccessError("Not an instantiable class");
	}

	public static synchronized BakedGeomeConfig bake() {
		JsonObject root = loadConfig();
		boolean changed = OreSpawnOreIntegration.mergeProviderOres(root);
		changed |= FluidDepositMigration.normalize(root);
		if (changed) {
			writeProviderMerge(root);
		}
		globalBaseConfigRoot = root.deepCopy();
		globalBaseProfile = WorldGeologyProfile.fromGlobalConfig(root,
				OreSpawnConfig.geologyMode(), OreSpawnConfig.placeCrudeOil());
		root = applyFreshWorldTemplate(root);
		globalConfigRoot = root.deepCopy();
		globalProfile = WorldGeologyProfile.fromGlobalConfig(root,
				OreSpawnConfig.geologyMode(), OreSpawnConfig.placeCrudeOil());
		bakeDimensions(root);
		return bakedConfig;
	}

	public static synchronized BakedGeomeConfig applyWorldProfile(WorldGeologyProfile profile) {
		JsonObject effective = profile.toGeomeConfigJson();
		bakeDimensions(effective);
		return bakedConfig;
	}

	public static JsonObject globalConfigSnapshot() {
		if (globalConfigRoot == null) {
			bake();
		}
		return globalConfigRoot.deepCopy();
	}

	public static WorldGeologyProfile globalProfile() {
		if (globalProfile == null) {
			bake();
		}
		return globalProfile;
	}

	public static BakedGeomeConfig baked() {
		if (bakedConfig == null) {
			return bake();
		}
		return bakedConfig;
	}

	public static BakedGeomeConfig baked(ResourceKey<Level> dimension) {
		if (bakedConfig == null) {
			bake();
		}
		return bakedConfigs.get(dimension);
	}

	static BakedTerrainDimension terrainDimension(ResourceKey<Level> dimension) {
		if (bakedConfig == null) {
			bake();
		}
		return terrainDimensions.get(dimension);
	}

	public static JsonObject globalBaseConfigSnapshot() {
		if (globalBaseConfigRoot == null) bake();
		return globalBaseConfigRoot.deepCopy();
	}

	public static WorldGeologyProfile globalBaseProfile() {
		if (globalBaseProfile == null) bake();
		return globalBaseProfile;
	}

	static boolean hasTerrainReplacement(ResourceKey<Level> dimension) {
		return terrainDimension(dimension) != null;
	}

	private static JsonObject loadConfig() {
		JsonObject defaults = defaultConfig();
		JsonObject migratedLegacy = LegacyConfigMigrator.migrateIfNeeded(CONFIG_PATH, defaults);
		if (migratedLegacy != null) {
			return migratedLegacy;
		}
		if (!Files.exists(CONFIG_PATH)) {
			writeDefaultConfig(defaults);
			return defaults;
		}

		JsonObject root;
		try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
			JsonElement element = new JsonParser().parse(reader);
			if (!element.isJsonObject()) {
				LOGGER.warn("OreSpawn geome config '{}' is not a JSON object; using defaults", CONFIG_PATH);
				return defaults;
			}
			root = element.getAsJsonObject();
		} catch (IOException | JsonSyntaxException | IllegalStateException e) {
			LOGGER.warn("Could not read OreSpawn geome config '{}'; using defaults", CONFIG_PATH, e);
			return defaults;
		}

		try {
			int schemaVersion = getInt(root, "schema_version", 1);
			if (schemaVersion < SCHEMA_VERSION) {
				JsonObject migrated = schemaVersion <= 1 ? migrateV1(root) : root.deepCopy();
				migrated = migrateToV6(migrated, defaults);
				writeMigratedConfig(migrated,
						schemaVersion <= 1 ? CONFIG_BACKUP_PATH
								: schemaVersion == 2 ? CONFIG_V2_BACKUP_PATH
										: schemaVersion == 3 ? CONFIG_V3_BACKUP_PATH
												: schemaVersion == 4 ? CONFIG_V4_BACKUP_PATH
														: CONFIG_V5_BACKUP_PATH);
				return migrated;
			}
			if (schemaVersion > SCHEMA_VERSION) {
				LOGGER.warn("OreSpawn geome config schema {} is newer than supported schema {}; reading known fields only",
						schemaVersion, SCHEMA_VERSION);
				return root;
			}
			if (getInt(root, "biome_defaults_revision", 0) < BIOME_DEFAULTS_REVISION) {
				JsonObject refreshed = refreshBiomeDefaults(root, defaults);
				writeBiomeDefaultsRefresh(refreshed);
				root = refreshed;
			}
			if (getInt(root, "worldgen_alias_defaults_revision", 0) < WORLDGEN_ALIAS_DEFAULTS_REVISION) {
				JsonObject refreshed = refreshWorldgenAliasDefaults(root, defaults);
				writeWorldgenAliasDefaultsRefresh(refreshed);
				root = refreshed;
			}
			if (getInt(root, "ore_defaults_revision", 0) < ORE_DEFAULTS_REVISION) {
				JsonObject refreshed = refreshOreDefaults(root, defaults);
				writeOreDefaultsRefresh(refreshed);
				root = refreshed;
			}
			return root;
		} catch (JsonSyntaxException | IllegalStateException e) {
			LOGGER.warn("Could not read OreSpawn geome config '{}'; using defaults", CONFIG_PATH, e);
			return defaults;
		}
	}

	private static void writeDefaultConfig(JsonObject defaults) {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(defaults, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("Could not create default OreSpawn geome config '{}'", CONFIG_PATH, e);
		}
	}

	private static void writeProviderMerge(JsonObject merged) {
		if (writeUpdatedConfig(merged, PROVIDER_DEFAULTS_BACKUP_PATH)) {
			LOGGER.info("Merged newly discovered OreSpawn provider defaults into '{}'", CONFIG_PATH);
		} else {
			LOGGER.warn("Using merged OreSpawn provider defaults in memory because '{}' could not be updated",
					CONFIG_PATH);
		}
	}

	private static void bakeDimensions(JsonObject root) {
		Map<ResourceKey<Level>, BakedTerrainDimension> terrain = readTerrainDimensions(root);
		Map<ResourceKey<Level>, BakedGeomeConfig> configs = new LinkedHashMap<>();
		Map<ResourceKey<Level>, BakedTerrainDimension> usableTerrain = new LinkedHashMap<>();
		for (Entry<ResourceKey<Level>, BakedTerrainDimension> entry : terrain.entrySet()) {
			BakedGeomeConfig config = bake(root, entry.getKey().location());
			if (config != null) {
				configs.put(entry.getKey(), config);
				usableTerrain.put(entry.getKey(), entry.getValue());
			}
		}
		VanillaSpringCompatibility.refresh(configs.get(Level.OVERWORLD));
		bakedConfigs = Collections.unmodifiableMap(configs);
		terrainDimensions = Collections.unmodifiableMap(usableTerrain);
		bakedConfig = configs.get(Level.OVERWORLD);
		if (bakedConfig == null) {
			bakedConfig = bake(root, Level.OVERWORLD.location());
		}
	}

	private static BakedGeomeConfig bake(JsonObject root, ResourceLocation dimension) {
		LinkedHashMap<String, Integer> geomeIndexes = new LinkedHashMap<>();
		GeomeDefinition[] geomes = readGeomes(root, geomeIndexes);
		FormationSettings formations = readFormationSettings(root);
		double geomeScale = getDouble(root, "geome_scale", 384.0D);
		double biomeInfluence = getDouble(root, "biome_influence", 1.15D);
		double regionalNoiseInfluence = getDouble(root, "regional_noise_influence", 0.90D);
		double boundaryNoiseInfluence = getDouble(root, "boundary_noise_influence", 0.45D);

		Map<String, double[]> biomeRules = readWeightRules(root, "biomes", geomeIndexes);
		Map<String, double[]> dictionaryRules = readWeightRules(root, "biome_dictionary", geomeIndexes);
		Map<ResourceLocation, ResourceLocation> worldgenAliases = readWorldgenAliases(root);
		RockEntry[] rocks = readRocks(root, geomeIndexes, worldgenAliases, dimension);
		if (rocks.length == 0 && !Level.OVERWORLD.location().equals(dimension)) {
			LOGGER.warn("Disabling OreSpawn terrain replacement in '{}' because it has no eligible rocks", dimension);
			return null;
		}
		Map<Biome, double[]> biomeWeights = bakeBiomeWeights(geomeIndexes, biomeRules, dictionaryRules);

		LOGGER.info("Baked OreSpawn geome config for '{}' with {} geomes, {} rock entries, {} biome profiles, and {} formations",
				dimension, geomes.length, rocks.length, biomeWeights.size(), formations.algorithm.configName);
		return new BakedGeomeConfig(geomes, geomeScale, biomeInfluence, regionalNoiseInfluence,
				boundaryNoiseInfluence, biomeWeights, rocks, formations);
	}

	private static JsonObject applyFreshWorldTemplate(JsonObject root) {
		String configured = getString(root, "default_template", "").trim();
		ResourceLocation selected = null;
		if (!configured.isEmpty()) {
			try {
				selected = new ResourceLocation(configured);
			} catch (RuntimeException e) {
				LOGGER.warn("Ignoring invalid OreSpawn default template '{}'", configured);
			}
		}
		if (selected == null) selected = WorldgenIntegrationManager.autoSelectedTemplate();
		if (selected == null) return root;
		try {
			return WorldgenIntegrationManager.applyTemplate(root, selected);
		} catch (RuntimeException e) {
			LOGGER.warn("Ignoring unavailable OreSpawn default template '{}'", selected);
			return root;
		}
	}

	private static Map<ResourceKey<Level>, BakedTerrainDimension> readTerrainDimensions(JsonObject root) {
		JsonObject definitions = getObject(root, "terrain_dimensions", defaultTerrainDimensions());
		Map<ResourceKey<Level>, BakedTerrainDimension> result = new LinkedHashMap<>();
		for (Entry<String, JsonElement> entry : definitions.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				LOGGER.warn("Ignoring terrain dimension '{}' because it is not an object", entry.getKey());
				continue;
			}
			JsonObject json = entry.getValue().getAsJsonObject();
			if (!getBoolean(json, "enabled", true)) {
				continue;
			}
			try {
				ResourceLocation id = new ResourceLocation(entry.getKey());
				Set<Block> hosts = Collections.newSetFromMap(new IdentityHashMap<Block, Boolean>());
				addTerrainHostBlocks(hosts, json.get("host_blocks"));
				addTerrainHostTags(hosts, json.get("host_tags"));
				if (hosts.isEmpty()) {
					LOGGER.warn("Ignoring terrain dimension '{}' because no replacement hosts resolved", id);
					continue;
				}
				Set<ResourceLocation> biomeIds = resourceLocations(json.get("biome_ids"));
				Set<String> namespaces = strings(json.get("biome_namespaces"));
				ResourceKey<Level> key = ResourceKey.create(Registry.DIMENSION_REGISTRY, id);
				result.put(key, new BakedTerrainDimension(key, biomeIds, namespaces, hosts));
			} catch (RuntimeException e) {
				LOGGER.warn("Ignoring invalid OreSpawn terrain dimension '{}'", entry.getKey());
			}
		}
		return result;
	}

	private static void addTerrainHostBlocks(Set<Block> target, JsonElement element) {
		for (ResourceLocation id : resourceLocations(element)) {
			Block block = ForgeRegistries.BLOCKS.getValue(id);
			if (block != null && block != Blocks.AIR) {
				target.add(block);
			}
		}
	}

	private static void addTerrainHostTags(Set<Block> target, JsonElement element) {
		for (ResourceLocation id : resourceLocations(element)) {
			TagKey<Block> tag = TagKey.create(Registry.BLOCK_REGISTRY, id);
			for (Block block : ForgeRegistries.BLOCKS.getValues()) {
				if (block.defaultBlockState().is(tag)) {
					target.add(block);
				}
			}
		}
	}

	private static Set<ResourceLocation> resourceLocations(JsonElement element) {
		Set<ResourceLocation> result = new LinkedHashSet<>();
		if (element != null && element.isJsonArray()) {
			for (JsonElement value : element.getAsJsonArray()) {
				result.add(new ResourceLocation(value.getAsString()));
			}
		}
		return result;
	}

	private static Set<String> strings(JsonElement element) {
		Set<String> result = new LinkedHashSet<>();
		if (element != null && element.isJsonArray()) {
			for (JsonElement value : element.getAsJsonArray()) {
				result.add(value.getAsString());
			}
		}
		return result;
	}

	private static boolean rockAppliesToDimension(JsonObject rock, ResourceLocation dimension) {
		if (!rock.has("dimensions")) {
			return Level.OVERWORLD.location().equals(dimension);
		}
		if (!rock.get("dimensions").isJsonArray()) {
			return false;
		}
		for (JsonElement value : rock.getAsJsonArray("dimensions")) {
			try {
				if (dimension.equals(new ResourceLocation(value.getAsString()))) {
					return true;
				}
			} catch (RuntimeException ignored) {
				// Provider/config validation reports malformed registry IDs once at setup.
			}
		}
		return false;
	}

	private static FormationSettings readFormationSettings(JsonObject root) {
		JsonObject defaults = defaultFormationConfig();
		JsonObject json = getObject(root, "formations", defaults);
		JsonObject custom = getObject(json, "custom", defaults.getAsJsonObject("custom"));

		Algorithm algorithm = Algorithm.STABLE_LAYERS;
		String algorithmName = getString(json, "algorithm", algorithm.configName);
		try {
			algorithm = Algorithm.fromConfigName(algorithmName);
		} catch (IllegalArgumentException e) {
			LOGGER.warn("Unknown OreSpawn formation algorithm '{}'; using stable_layers", algorithmName);
		}

		Preset horizontal = readPreset(json, "horizontal_size");
		Preset thickness = readPreset(json, "vertical_thickness");
		Preset waviness = readPreset(json, "waviness");
		Preset irregularity = readPreset(json, "edge_irregularity");
		Preset continuity = readPreset(json, "formation_continuity");

		boolean stableLayers = algorithm == Algorithm.STABLE_LAYERS;
		double stratumWavelength = stableLayers
				? waviness == Preset.CUSTOM
						? getBoundedDouble(custom, "waviness_wavelength", 256.0D, 32.0D, 2048.0D)
						: horizontal == Preset.CUSTOM
								? getBoundedDouble(custom, "stratum_wavelength", 256.0D, 32.0D, 2048.0D)
								: horizontal.stableWavinessWavelength
				: horizontal == Preset.CUSTOM
						? getBoundedDouble(custom, "stratum_wavelength", 256.0D, 16.0D, 8192.0D)
						: horizontal.stratumWavelength;
		double familyRegionWavelength = horizontal == Preset.CUSTOM
				? getBoundedDouble(custom, "family_region_wavelength", 100.0D, 16.0D, 8192.0D)
				: horizontal.familyRegionWavelength;
		int verticalThickness = thickness == Preset.CUSTOM
				? getBoundedInt(custom, "vertical_thickness", 8, 1, 192)
				: thickness.verticalThickness;
		double wavinessAmplitude = waviness == Preset.CUSTOM
				? getBoundedDouble(custom, "waviness_amplitude", stableLayers ? 48.0D : 60.0D, 0.0D, 512.0D)
				: stableLayers ? waviness.stableWavinessAmplitude : waviness.wavinessAmplitude;
		double edgeWavelength = stableLayers
				? irregularity == Preset.CUSTOM
						? getBoundedDouble(custom, "edge_wavelength", 64.0D, 8.0D, 512.0D)
						: irregularity.stableEdgeWavelength
				: 64.0D;
		double edgeAmplitude = !stableLayers
				? 0.0D
				: irregularity == Preset.CUSTOM
						? getBoundedDouble(custom, "edge_amplitude", 12.0D, 0.0D, 256.0D)
						: irregularity.stableEdgeAmplitude;
		int edgeOctaves = irregularity == Preset.CUSTOM
				? getBoundedInt(custom, "edge_octaves", stableLayers ? 2 : 4, 1, 8)
				: stableLayers ? irregularity.stableEdgeOctaves : irregularity.edgeOctaves;
		double formationContinuity = continuity == Preset.CUSTOM
				? getBoundedDouble(custom, "continuity", 0.85D, 0.0D, 1.0D)
				: continuity.continuity;

		return new FormationSettings(algorithm, stratumWavelength, familyRegionWavelength,
				verticalThickness, wavinessAmplitude, edgeWavelength, edgeAmplitude,
				edgeOctaves, formationContinuity);
	}

	private static Preset readPreset(JsonObject json, String key) {
		String name = getString(json, key, Preset.AVERAGE.configName);
		try {
			return Preset.fromConfigName(name);
		} catch (IllegalArgumentException e) {
			LOGGER.warn("Unknown OreSpawn formation preset '{}' for '{}'; using average", name, key);
			return Preset.AVERAGE;
		}
	}

	private static GeomeDefinition[] readGeomes(JsonObject root, LinkedHashMap<String, Integer> geomeIndexes) {
		JsonObject geomeRoot = GsonHelper.getAsJsonObject(root, "geomes", defaultConfig().getAsJsonObject("geomes"));
		List<GeomeDefinition> geomes = new ArrayList<>();
		for (Entry<String, JsonElement> entry : geomeRoot.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				LOGGER.warn("Ignoring OreSpawn geome '{}' because it is not an object", entry.getKey());
				continue;
			}
			String geomeName;
			try {
				geomeName = normalizeGeomeName(entry.getKey());
			} catch (RuntimeException e) {
				LOGGER.warn("Ignoring invalid OreSpawn geome ID '{}'", entry.getKey());
				continue;
			}
			if (geomeIndexes.containsKey(geomeName)) {
				LOGGER.warn("Ignoring duplicate OreSpawn geome '{}' after ID normalization", entry.getKey());
				continue;
			}

			JsonObject json = entry.getValue().getAsJsonObject();
			double[] familyWeights = new double[RockFamily.values().length];
			for (RockFamily family : RockFamily.values()) {
				familyWeights[family.ordinal()] = 1.0D;
			}

			JsonObject families = GsonHelper.getAsJsonObject(json, "families", new JsonObject());
			for (RockFamily family : RockFamily.values()) {
				familyWeights[family.ordinal()] = getNonNegativeDouble(families, family.configName,
						familyWeights[family.ordinal()]);
			}

			geomeIndexes.put(geomeName, geomes.size());
			geomes.add(new GeomeDefinition(geomeName, getDouble(json, "base", 1.0D), familyWeights));
		}

		if (geomes.isEmpty()) {
			throw new JsonSyntaxException("OreSpawn geome config must define at least one geome");
		}

		return geomes.toArray(new GeomeDefinition[geomes.size()]);
	}

	private static Map<String, double[]> readWeightRules(JsonObject root, String section,
			Map<String, Integer> geomeIndexes) {
		Map<String, double[]> rules = new LinkedHashMap<>();
		if (!root.has(section) || !root.get(section).isJsonObject()) {
			return rules;
		}

		for (Entry<String, JsonElement> entry : root.getAsJsonObject(section).entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				LOGGER.warn("Ignoring OreSpawn geome {} rule '{}' because it is not an object", section,
						entry.getKey());
				continue;
			}
			rules.put(entry.getKey(), readGeomeWeights(entry.getValue().getAsJsonObject(), geomeIndexes, 0.0D));
		}
		return rules;
	}

	private static RockEntry[] readRocks(JsonObject root, Map<String, Integer> geomeIndexes,
			Map<ResourceLocation, ResourceLocation> worldgenAliases, ResourceLocation dimension) {
		JsonObject rockRoot = getObject(root, "rocks", defaultConfig().getAsJsonObject("rocks"));
		List<RockEntry> rocks = new ArrayList<>();
		Map<BlockState, ResourceLocation> configuredStates = new HashMap<BlockState, ResourceLocation>();
		for (Entry<String, JsonElement> entry : rockRoot.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				LOGGER.warn("Ignoring OreSpawn geome rock '{}' because it is not an object", entry.getKey());
				continue;
			}

			JsonObject json = entry.getValue().getAsJsonObject();
			if (!getBoolean(json, "enabled", true)) {
				continue;
			}
			if (!rockAppliesToDimension(json, dimension)) {
				continue;
			}

			double weight = getNonNegativeDouble(json, "weight", 1.0D);
			if (weight <= 0.0D) {
				continue;
			}

			ResourceLocation ruleId;
			ResourceLocation id;
			try {
				ruleId = new ResourceLocation(entry.getKey());
				id = new ResourceLocation(getString(json, "block", entry.getKey()));
			} catch (RuntimeException e) {
				LOGGER.warn("Ignoring invalid OreSpawn geome rock rule or block id '{}'", entry.getKey());
				continue;
			}

			Block block = ForgeRegistries.BLOCKS.getValue(id);
			if (block == null || block == Blocks.AIR) {
				LOGGER.warn("Ignoring unknown OreSpawn geome rock block '{}'", id);
				continue;
			}

			RockFamily family;
			try {
				family = RockFamily.fromConfigName(getString(json, "family", ""));
			} catch (RuntimeException e) {
				LOGGER.warn("Ignoring OreSpawn geome rock '{}' with invalid family", id);
				continue;
			}

			int minY = getBoundedInt(json, "min_y", BakedGeomeConfig.MIN_Y,
					BakedGeomeConfig.MIN_Y, BakedGeomeConfig.MAX_Y);
			int maxY = getBoundedInt(json, "max_y", BakedGeomeConfig.MAX_Y,
					BakedGeomeConfig.MIN_Y, BakedGeomeConfig.MAX_Y);
			if (minY > maxY) {
				LOGGER.warn("Ignoring OreSpawn geome rock '{}' because min_y {} is above max_y {}", id, minY, maxY);
				continue;
			}

			JsonObject geomeWeightsJson = GsonHelper.getAsJsonObject(json, "geomes", new JsonObject());
			double[] geomeWeights = readGeomeWeights(geomeWeightsJson, geomeIndexes, 1.0D);
			BlockState state = GeologyBlockAliases.aliasState(id, block.defaultBlockState(), worldgenAliases);
			ResourceLocation previousId = configuredStates.putIfAbsent(state, ruleId);
			if (previousId != null) {
				LOGGER.warn("Ignoring duplicate OreSpawn geome rock rule '{}' because block '{}' resolves to the same state as '{}'",
						ruleId, id, previousId);
				continue;
			}

			rocks.add(new RockEntry(state,
					family,
					getBoundedInt(json, "depth_peak", 48, BakedGeomeConfig.MIN_Y, BakedGeomeConfig.MAX_Y),
					getBoundedInt(json, "depth_spread", 48, 1, 512),
					minY,
					maxY,
					weight,
					getBoolean(json, "ore_replaceable", true),
					geomeWeights));
		}

		if (rocks.isEmpty() && Level.OVERWORLD.location().equals(dimension)) {
			boolean passive = rockRoot.size() == 0
					&& getObject(root, "terrain_dimensions", defaultTerrainDimensions()).size() == 0;
			if (passive) {
				LOGGER.info("No OreSpawn terrain provider is active; using an internal vanilla-stone sampler fallback");
			} else {
				LOGGER.warn("OreSpawn geome config produced no valid rock entries; falling back to vanilla stone");
			}
			double[] weights = new double[geomeIndexes.size()];
			for (int i = 0; i < weights.length; i++) {
				weights[i] = 1.0D;
			}
			rocks.add(new RockEntry(Blocks.STONE.defaultBlockState(), RockFamily.SEDIMENTARY, 64, 64,
					BakedGeomeConfig.MIN_Y, BakedGeomeConfig.MAX_Y, 1.0D, true, weights));
		}
		return rocks.toArray(new RockEntry[rocks.size()]);
	}

	private static JsonObject migrateV1(JsonObject original) {
		JsonObject migrated = original.deepCopy();
		migrated.add("formations", legacyFormationConfig());

		if (migrated.has("rocks") && migrated.get("rocks").isJsonObject()) {
			for (Entry<String, JsonElement> entry : migrated.getAsJsonObject("rocks").entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				JsonObject rock = entry.getValue().getAsJsonObject();
				addIfMissing(rock, "enabled", true);
				addIfMissing(rock, "min_y", BakedGeomeConfig.MIN_Y);
				addIfMissing(rock, "max_y", BakedGeomeConfig.MAX_Y);
				addIfMissing(rock, "ore_replaceable", true);
			}
		}
		return refreshBiomeDefaults(migrated, defaultConfig());
	}

	private static JsonObject migrateToV6(JsonObject original, JsonObject defaults) {
		JsonObject migrated = getInt(original, "biome_defaults_revision", 0) < BIOME_DEFAULTS_REVISION
				? refreshBiomeDefaults(original, defaults) : original.deepCopy();
		FluidDepositMigration.normalize(migrated);
		for (String key : new String[] { "geology_mode", "place_fluid_deposits", "fluid_deposits",
				"manage_vanilla_ores", "ore_defaults_revision", "cyano", "ores",
				"ore_providers", "providers", "worldgen_aliases", "default_template",
				"terrain_dimensions", "biome_palettes", "dimension_materials" }) {
			if (!migrated.has(key)) {
				migrated.add(key, defaults.get(key).deepCopy());
			}
		}
		migrated = refreshWorldgenAliasDefaults(migrated, defaults);
		migrated.addProperty("schema_version", SCHEMA_VERSION);
		return migrated;
	}

	private static JsonObject refreshBiomeDefaults(JsonObject original, JsonObject defaults) {
		JsonObject refreshed = original.deepCopy();
		mergeMissingEntries(refreshed, defaults, "biomes");
		mergeMissingEntries(refreshed, defaults, "biome_dictionary");
		refreshed.addProperty("biome_defaults_revision", BIOME_DEFAULTS_REVISION);
		return refreshed;
	}

	static JsonObject refreshWorldgenAliasDefaults(JsonObject original, JsonObject defaults) {
		JsonObject refreshed = original.deepCopy();
		mergeMissingEntries(refreshed, defaults, "worldgen_aliases");
		if (refreshed.has("rocks") && refreshed.get("rocks").isJsonObject()) {
			JsonObject rocks = refreshed.getAsJsonObject("rocks");
			JsonObject defaultAliases = defaults.getAsJsonObject("worldgen_aliases");
			JsonObject normalized = new JsonObject();
			for (Entry<String, JsonElement> entry : rocks.entrySet()) {
				String id = entry.getKey();
				if (defaultAliases.has(id)) {
					try {
						id = defaultAliases.get(id).getAsString();
					} catch (RuntimeException ignored) { }
				}
				if (!normalized.has(id)) {
					normalized.add(id, entry.getValue().deepCopy());
				}
			}
			refreshed.add("rocks", normalized);
		}
		refreshed.addProperty("worldgen_alias_defaults_revision", WORLDGEN_ALIAS_DEFAULTS_REVISION);
		return refreshed;
	}

	static JsonObject refreshOreDefaults(JsonObject original, JsonObject defaults) {
		JsonObject refreshed = original.deepCopy();
		if (!refreshed.has("manage_vanilla_ores")) {
			refreshed.addProperty("manage_vanilla_ores", false);
		}
		normalizeLegacyMineralogyVanillaOres(refreshed);
		mergeMissingEntries(refreshed, defaults, "ores");
		upgradeOrePatternDefaults(refreshed);
		refreshed.addProperty("ore_defaults_revision", ORE_DEFAULTS_REVISION);
		return refreshed;
	}

	static boolean needsWorldOreDefaultsRefresh(JsonObject root) {
		return getInt(root, "ore_defaults_revision", 0) < ORE_DEFAULTS_REVISION;
	}

	static JsonObject refreshWorldOreDefaults(JsonObject original) {
		JsonObject refreshed = original.deepCopy();
		normalizeLegacyMineralogyVanillaOres(refreshed);
		upgradeOrePatternDefaults(refreshed);
		refreshed.addProperty("ore_defaults_revision", ORE_DEFAULTS_REVISION);
		return refreshed;
	}

	static int oreDefaultsRevision() {
		return ORE_DEFAULTS_REVISION;
	}

	private static void normalizeLegacyMineralogyVanillaOres(JsonObject root) {
		if (!root.has("ores") || !root.get("ores").isJsonObject()) return;
		JsonObject ores = root.getAsJsonObject("ores");
		for (String oreId : VANILLA_ORE_IDS) {
			String legacyId = LEGACY_MINERALOGY_VANILLA_ORE_PREFIX + oreId;
			if (!ores.has(legacyId) || !ores.get(legacyId).isJsonObject()) continue;
			String canonicalId = "minecraft:" + oreId;
			JsonObject legacy = ores.getAsJsonObject(legacyId).deepCopy();

			JsonObject probeOres = new JsonObject();
			probeOres.add(canonicalId, legacy.deepCopy());
			JsonObject probeRoot = new JsonObject();
			probeRoot.add("ores", probeOres);
			boolean untouchedDefault = upgradeOrePatternDefaults(probeRoot);
			if (!ores.has(canonicalId) || !untouchedDefault) {
				legacy.remove("orphaned_provider");
				ores.add(canonicalId, legacy);
			}
			ores.remove(legacyId);
		}
	}

	private static boolean upgradeOrePatternDefaults(JsonObject root) {
		boolean upgraded = false;
		upgraded |= upgradeOreRule(root, "minecraft:coal_ore", "minecraft:overworld",
				0, 256, 20.0D, 17, "cluster", 0, 96, 12.0D);
		upgraded |= upgradeOreRule(root, "minecraft:coal_ore", "minecraft:overworld",
				0, 96, 6.0D, 17, "cluster", 0, 96, 12.0D);
		upgraded |= upgradeOreRule(root, "minecraft:iron_ore", "minecraft:overworld",
				-64, 256, 20.0D, 9, "vein", -64, 256, 34.0D);
		upgraded |= upgradeOreRule(root, "minecraft:copper_ore", "minecraft:overworld",
				-16, 112, 16.0D, 10, "cloud", -16, 112, 13.0D);
		upgraded |= upgradeOreRule(root, "minecraft:diamond_ore", "minecraft:overworld",
				-64, 16, 4.0D, 8, "cluster", -64, 16, 2.6D);
		upgraded |= upgradeOreRule(root, "minecraft:diamond_ore", "minecraft:overworld",
				-64, 16, 2.0D, 8, "cluster", -64, 16, 2.6D);
		upgraded |= upgradeOreRule(root, "minecraft:lapis_ore", "minecraft:overworld",
				-64, 64, 4.0D, 7, "cloud", -64, 64, 3.4D);
		upgraded |= upgradeOreRule(root, "minecraft:emerald_ore", "minecraft:overworld",
				-16, 319, 8.0D, 3, "cluster", -16, 128, 0.55D);
		upgraded |= upgradeOreRule(root, "minecraft:emerald_ore", "minecraft:overworld",
				-16, 128, 3.0D, 3, "cluster", -16, 128, 0.55D);
		upgraded |= upgradeOreRule(root, "minecraft:nether_gold_ore", "minecraft:the_nether",
				0, 127, 10.0D, 10, "cluster", 0, 127, 6.0D);
		upgraded |= upgradeOreRule(root, "minecraft:ancient_debris", "minecraft:the_nether",
				8, 120, 2.0D, 3, "cluster", 8, 120, 1.0D);
		upgraded |= upgradeOreRule(root, "minecraft:coal_ore", "minecraft:overworld",
				0, 96, 12.0D, 17, "cluster", 0, 96, 6.25D);
		upgraded |= upgradeOreRule(root, "minecraft:iron_ore", "minecraft:overworld",
				-64, 256, 34.0D, 9, "vein", -64, 256, 26.0D);
		upgraded |= upgradeOreRule(root, "minecraft:copper_ore", "minecraft:overworld",
				-16, 112, 13.0D, 10, "cloud", -16, 112, 13.0D);
		upgraded |= upgradeOreRule(root, "minecraft:gold_ore", "minecraft:overworld",
				-64, 32, 4.5D, 9, "vein", -64, 32, 2.85D);
		upgraded |= upgradeOreRule(root, "minecraft:redstone_ore", "minecraft:overworld",
				-64, 15, 8.0D, 8, "vein", -64, 15, 4.7D);
		upgraded |= upgradeOreRule(root, "minecraft:diamond_ore", "minecraft:overworld",
				-64, 16, 2.6D, 8, "cluster", -64, 16, 1.8D);
		upgraded |= upgradeOreRule(root, "minecraft:lapis_ore", "minecraft:overworld",
				-64, 64, 3.4D, 7, "cloud", -64, 64, 3.25D);
		upgraded |= upgradeOreRule(root, "minecraft:emerald_ore", "minecraft:overworld",
				-16, 128, 0.55D, 3, "cluster", -16, 128, 0.35D);
		upgraded |= upgradeOreRule(root, "minecraft:nether_gold_ore", "minecraft:the_nether",
				0, 127, 6.0D, 10, "cluster", 0, 127, 5.2D);
		upgraded |= upgradeOreRule(root, "minecraft:nether_quartz_ore", "minecraft:the_nether",
				0, 127, 16.0D, 14, "vein", 0, 127, 11.2D);
		upgraded |= upgradeOreRule(root, "minecraft:ancient_debris", "minecraft:the_nether",
				8, 120, 1.0D, 3, "cluster", 8, 120, 1.25D);
		upgraded |= upgradeOreFidelityRule(root, "minecraft:coal_ore", "minecraft:overworld",
				0, 96, 6.25D, 17, "cluster", OreHeightDistribution.TRIANGLE, 0.0D,
				6.27D, OreHeightDistribution.TRIANGLE, 0.87D);
		upgraded |= upgradeOreFidelityRule(root, "minecraft:iron_ore", "minecraft:overworld",
				-64, 256, 26.0D, 9, "vein", OreHeightDistribution.TRIANGLE, 0.0D,
				25.5D, OreHeightDistribution.TRIANGLE, 0.80D);
		upgraded |= upgradeOreFidelityRule(root, "minecraft:copper_ore", "minecraft:overworld",
				-16, 112, 13.0D, 10, "cloud", OreHeightDistribution.TRIANGLE, 0.0D,
				12.65D, OreHeightDistribution.TRIANGLE, 0.27D);
		upgraded |= upgradeOreFidelityRule(root, "minecraft:gold_ore", "minecraft:overworld",
				-64, 32, 2.85D, 9, "vein", OreHeightDistribution.TRIANGLE, 0.0D,
				2.95D, OreHeightDistribution.TRIANGLE, 0.94D);
		upgraded |= upgradeOreFidelityRule(root, "minecraft:redstone_ore", "minecraft:overworld",
				-64, 15, 4.7D, 8, "vein", OreHeightDistribution.TRIANGLE, 0.0D,
				4.68D, OreHeightDistribution.UNIFORM_BOTTOM_TRIANGLE, 0.78D);
		upgraded |= upgradeOreFidelityRule(root, "minecraft:diamond_ore", "minecraft:overworld",
				-64, 16, 1.8D, 8, "cluster", OreHeightDistribution.TRIANGLE, 0.0D,
				1.83D, OreHeightDistribution.BOTTOM_TRIANGLE, 0.94D);
		upgraded |= upgradeOreFidelityRule(root, "minecraft:lapis_ore", "minecraft:overworld",
				-64, 64, 3.25D, 7, "cloud", OreHeightDistribution.TRIANGLE, 0.0D,
				3.38D, OreHeightDistribution.TRIANGLE, 0.80D);
		upgraded |= upgradeOreFidelityRule(root, "minecraft:emerald_ore", "minecraft:overworld",
				-16, 128, 0.35D, 3, "cluster", OreHeightDistribution.TRIANGLE, 0.0D,
				0.40D, OreHeightDistribution.TRIANGLE, 0.65D);
		upgraded |= upgradeOreFidelityRule(root, "minecraft:emerald_ore", "minecraft:overworld",
				-16, 128, 0.40D, 3, "cluster", OreHeightDistribution.TRIANGLE, 0.65D,
				0.33D, OreHeightDistribution.TRIANGLE, 0.65D);
		return upgraded;
	}

	private static boolean upgradeOreFidelityRule(JsonObject root, String oreId, String dimensionId,
			int minY, int maxY, double oldFrequency, int quantity, String pattern,
			OreHeightDistribution oldDistribution, double oldDiscardChance,
			double newFrequency, OreHeightDistribution newDistribution, double newDiscardChance) {
		if (!root.has("ores") || !root.get("ores").isJsonObject()) return false;
		JsonObject ores = root.getAsJsonObject("ores");
		if (!ores.has(oreId) || !ores.get(oreId).isJsonObject()) return false;
		JsonObject ore = ores.getAsJsonObject(oreId);
		if (!ore.has("dimensions") || !ore.get("dimensions").isJsonObject()
				|| !ore.getAsJsonObject("dimensions").has(dimensionId)
				|| !ore.getAsJsonObject("dimensions").get(dimensionId).isJsonObject()) return false;
		JsonObject rule = ore.getAsJsonObject("dimensions").getAsJsonObject(dimensionId);
		if (getInt(rule, "min_y", Integer.MIN_VALUE) == minY
				&& getInt(rule, "max_y", Integer.MIN_VALUE) == maxY
				&& Math.abs(getDouble(rule, "frequency", -1.0D) - oldFrequency) < 0.000001D
				&& getInt(rule, "quantity", -1) == quantity
				&& sameOrePattern(rule, pattern)
				&& oldDistribution.configName.equals(getString(rule, "height_distribution",
						oldDistribution.configName))
				&& Math.abs(getDouble(rule, "discard_chance_on_air_exposure", 0.0D)
						- oldDiscardChance) < 0.000001D) {
			rule.addProperty("frequency", newFrequency);
			rule.addProperty("height_distribution", newDistribution.configName);
			rule.addProperty("discard_chance_on_air_exposure", newDiscardChance);
			return true;
		}
		return false;
	}

	private static boolean upgradeOreRule(JsonObject root, String oreId, String dimensionId,
			int oldMinY, int oldMaxY, double oldFrequency, int oldQuantity, String oldPattern,
			int newMinY, int newMaxY, double newFrequency) {
		if (!root.has("ores") || !root.get("ores").isJsonObject()) {
			return false;
		}
		JsonObject ores = root.getAsJsonObject("ores");
		if (!ores.has(oreId) || !ores.get(oreId).isJsonObject()) {
			return false;
		}
		JsonObject ore = ores.getAsJsonObject(oreId);
		if (!ore.has("dimensions") || !ore.get("dimensions").isJsonObject()
				|| !ore.getAsJsonObject("dimensions").has(dimensionId)
				|| !ore.getAsJsonObject("dimensions").get(dimensionId).isJsonObject()) {
			return false;
		}
		JsonObject rule = ore.getAsJsonObject("dimensions").getAsJsonObject(dimensionId);
		if (getInt(rule, "min_y", Integer.MIN_VALUE) == oldMinY
				&& getInt(rule, "max_y", Integer.MIN_VALUE) == oldMaxY
				&& Math.abs(getDouble(rule, "frequency", -1.0D) - oldFrequency) < 0.000001D
				&& getInt(rule, "quantity", -1) == oldQuantity
				&& sameOrePattern(rule, oldPattern)) {
			rule.addProperty("min_y", newMinY);
			rule.addProperty("max_y", newMaxY);
			rule.addProperty("frequency", newFrequency);
			return true;
		}
		return false;
	}

	private static boolean sameOrePattern(JsonObject rule, String expected) {
		try {
			return OrePattern.fromConfigName(getString(rule, "pattern", ""))
					== OrePattern.fromConfigName(expected);
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	private static void mergeMissingEntries(JsonObject targetRoot, JsonObject defaultsRoot, String key) {
		JsonObject defaults = defaultsRoot.getAsJsonObject(key);
		if (!targetRoot.has(key)) {
			targetRoot.add(key, defaults.deepCopy());
			return;
		}
		if (!targetRoot.get(key).isJsonObject()) {
			LOGGER.warn("Could not add updated OreSpawn {} defaults because the existing value is not an object", key);
			return;
		}

		JsonObject target = targetRoot.getAsJsonObject(key);
		for (Entry<String, JsonElement> entry : defaults.entrySet()) {
			if (!target.has(entry.getKey())) {
				target.add(entry.getKey(), entry.getValue().deepCopy());
			}
		}
	}

	private static void writeMigratedConfig(JsonObject migrated, Path backupPath) {
		if (writeUpdatedConfig(migrated, backupPath)) {
			LOGGER.info("Migrated OreSpawn geome config to schema {}. The previous file is preserved at '{}'",
					SCHEMA_VERSION, backupPath);
		} else {
			LOGGER.warn("Could not persist OreSpawn geome config migration; using migrated settings in memory");
		}
	}

	private static void writeBiomeDefaultsRefresh(JsonObject refreshed) {
		if (writeUpdatedConfig(refreshed, BIOME_DEFAULTS_BACKUP_PATH)) {
			LOGGER.info("Updated OreSpawn biome defaults to revision {}. The previous file is preserved at '{}'",
					BIOME_DEFAULTS_REVISION, BIOME_DEFAULTS_BACKUP_PATH);
		} else {
			LOGGER.warn("Could not persist updated OreSpawn biome defaults; using refreshed settings in memory");
		}
	}

	private static void writeWorldgenAliasDefaultsRefresh(JsonObject refreshed) {
		if (writeUpdatedConfig(refreshed, WORLDGEN_ALIAS_DEFAULTS_BACKUP_PATH)) {
			LOGGER.info("Updated OreSpawn matching-vanilla worldgen aliases to revision {}. "
					+ "The previous file is preserved at '{}'",
					WORLDGEN_ALIAS_DEFAULTS_REVISION, WORLDGEN_ALIAS_DEFAULTS_BACKUP_PATH);
		} else {
			LOGGER.warn("Could not persist updated OreSpawn worldgen aliases; using refreshed settings in memory");
		}
	}

	private static void writeOreDefaultsRefresh(JsonObject refreshed) {
		if (writeUpdatedConfig(refreshed, ORE_DEFAULTS_BACKUP_PATH)) {
			LOGGER.info("Updated OreSpawn managed-ore defaults to revision {}. "
					+ "The previous file is preserved at '{}'",
					ORE_DEFAULTS_REVISION, ORE_DEFAULTS_BACKUP_PATH);
		} else {
			LOGGER.warn("Could not persist updated OreSpawn ore defaults; using refreshed settings in memory");
		}
	}

	private static boolean writeUpdatedConfig(JsonObject updated, Path backupPath) {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			if (!Files.exists(backupPath)) {
				Files.copy(CONFIG_PATH, backupPath);
			}
			try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_TEMP_PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(updated, writer);
			}
			try {
				Files.move(CONFIG_TEMP_PATH, CONFIG_PATH, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(CONFIG_TEMP_PATH, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException atomicFailure) {
				try {
					Files.move(CONFIG_TEMP_PATH, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException fallbackFailure) {
					fallbackFailure.addSuppressed(atomicFailure);
					throw fallbackFailure;
				}
			}
			return true;
		} catch (IOException e) {
			try {
				Files.deleteIfExists(CONFIG_TEMP_PATH);
			} catch (IOException ignored) {
				// Keep the original migration failure as the useful diagnostic.
			}
			LOGGER.warn("Could not write updated OreSpawn geome config", e);
			return false;
		}
	}

	private static Map<ResourceLocation, ResourceLocation> readWorldgenAliases(JsonObject root) {
		Map<ResourceLocation, ResourceLocation> aliases = new LinkedHashMap<>();
		JsonObject aliasRoot = GsonHelper.getAsJsonObject(root, "worldgen_aliases",
				defaultConfig().getAsJsonObject("worldgen_aliases"));
		for (Entry<String, JsonElement> entry : aliasRoot.entrySet()) {
			ResourceLocation sourceId;
			ResourceLocation targetId;
			try {
				sourceId = new ResourceLocation(entry.getKey());
				targetId = new ResourceLocation(entry.getValue().getAsString());
			} catch (RuntimeException e) {
				LOGGER.warn("Ignoring invalid OreSpawn worldgen alias '{}'", entry.getKey());
				continue;
			}

			Block target = ForgeRegistries.BLOCKS.getValue(targetId);
			if (target == null || target == Blocks.AIR) {
				LOGGER.warn("Ignoring OreSpawn worldgen alias '{}' -> '{}' because the target block is unknown",
						sourceId, targetId);
				aliases.put(sourceId, sourceId);
				continue;
			}
			aliases.put(sourceId, targetId);
		}
		return aliases;
	}

	private static Map<Biome, double[]> bakeBiomeWeights(Map<String, Integer> geomeIndexes,
			Map<String, double[]> biomeRules, Map<String, double[]> dictionaryRules) {
		Map<Biome, double[]> result = new IdentityHashMap<>();
		for (Biome biome : ForgeRegistries.BIOMES.getValues()) {
			double[] weights = new double[geomeIndexes.size()];
			for (int i = 0; i < weights.length; i++) {
				weights[i] = 1.0D;
			}

			ResourceLocation biomeId = ForgeRegistries.BIOMES.getKey(biome);
			if (biomeId != null) {
				merge(weights, biomeRules.get(biomeId.toString()));
				ResourceKey<Biome> biomeKey = ResourceKey.create(Registry.BIOME_REGISTRY, biomeId);
				for (BiomeDictionary.Type type : BiomeDictionary.getTypes(biomeKey)) {
					merge(weights, dictionaryRules.get(type.getName()));
				}
			}
			applyBiomeHeuristic(weights, geomeIndexes, biomeId, biome);
			result.put(biome, weights);
		}
		return result;
	}

	private static void applyBiomeHeuristic(double[] weights, Map<String, Integer> geomeIndexes,
			ResourceLocation biomeId, Biome biome) {
		String biomeName = biomeId == null ? "" : biomeId.getPath();
		float temperature = biome.getBaseTemperature();
		float downfall = biome.getDownfall();

		if (biomeName.contains("ocean") || biomeName.contains("river") || biomeName.contains("beach")
				|| biomeName.contains("shore") || biomeName.contains("coast")
				|| biomeName.contains("island") || biomeName.contains("tropic")) {
			add(weights, geomeIndexes, "coastal_shelf", 2.25D);
			add(weights, geomeIndexes, "sedimentary_basin", 0.75D);
		}
		if (biomeName.contains("desert") || biomeName.contains("badlands") || biomeName.contains("savanna")
				|| biomeName.contains("dune") || biomeName.contains("wasteland")
				|| biomeName.contains("scrubland") || biomeName.contains("shrubland")
				|| (temperature > 0.95F && downfall < 0.25F)) {
			add(weights, geomeIndexes, "arid_basin", 2.5D);
		}
		if (biomeName.contains("swamp") || biomeName.contains("marsh") || biomeName.contains("wetland")
				|| biomeName.contains("bayou") || biomeName.contains("bog") || biomeName.contains("mire")
				|| biomeName.contains("floodplain") || biomeName.contains("rainforest") || downfall > 0.85F) {
			add(weights, geomeIndexes, "wetland_basin", 2.0D);
		}
		if (biomeName.contains("mountain") || biomeName.contains("hill") || biomeName.contains("peak")
				|| biomeName.contains("slope") || biomeName.contains("windswept") || biomeName.contains("stony")
				|| biomeName.contains("highland") || biomeName.contains("cliff") || biomeName.contains("crag")
				|| biomeName.contains("rocky")) {
			add(weights, geomeIndexes, "mountain_belt", 2.5D);
		}
		if (biomeName.contains("volcano") || biomeName.contains("volcanic")) {
			add(weights, geomeIndexes, "volcanic_arc", 4.0D);
			add(weights, geomeIndexes, "mountain_belt", 0.75D);
		}
		if (biomeName.contains("frozen") || biomeName.contains("snowy") || biomeName.contains("ice")
				|| biomeName.contains("tundra") || biomeName.contains("muskeg") || biomeName.contains("cold")
				|| temperature < 0.15F) {
			add(weights, geomeIndexes, "glacial_highland", 1.75D);
		}
		if (biomeName.contains("plains") || biomeName.contains("forest") || biomeName.contains("taiga")
				|| biomeName.contains("meadow") || biomeName.contains("grove") || biomeName.contains("woods")
				|| biomeName.contains("woodland") || biomeName.contains("field")
				|| biomeName.contains("pasture") || biomeName.contains("prairie")
				|| biomeName.contains("orchard")) {
			add(weights, geomeIndexes, "stable_craton", 1.25D);
		}
		if (biomeName.contains("cave") || biomeName.contains("grotto") || biomeName.contains("karst")) {
			add(weights, geomeIndexes, "sedimentary_basin", 1.5D);
		}
	}

	private static double[] readGeomeWeights(JsonObject json, Map<String, Integer> geomeIndexes, double defaultWeight) {
		double[] weights = new double[geomeIndexes.size()];
		for (int i = 0; i < weights.length; i++) {
			weights[i] = defaultWeight;
		}

		for (Entry<String, JsonElement> entry : json.entrySet()) {
			Integer index = null;
			try {
				index = geomeIndexes.get(normalizeGeomeName(entry.getKey()));
			} catch (RuntimeException ignored) {
				// The warning below includes the original user-facing value.
			}
			if (index == null) {
				LOGGER.warn("Ignoring unknown OreSpawn geome weight '{}'", entry.getKey());
				continue;
			}
			try {
				double value = entry.getValue().getAsDouble();
				if (!Double.isFinite(value) || value < 0.0D) {
					throw new NumberFormatException("weight must be finite and non-negative");
				}
				weights[index] = value;
			} catch (RuntimeException e) {
				LOGGER.warn("Ignoring invalid OreSpawn geome weight '{}' for '{}'", entry.getValue(), entry.getKey());
			}
		}
		return weights;
	}

	private static void merge(double[] target, double[] source) {
		if (source == null) {
			return;
		}
		for (int i = 0; i < target.length && i < source.length; i++) {
			target[i] += source[i];
		}
	}

	private static void add(double[] weights, Map<String, Integer> indexes, String geome, double value) {
		Integer index = indexes.get(normalizeGeomeName(geome));
		if (index != null) {
			weights[index] += value;
		}
	}

	static String normalizeGeomeName(String geome) {
		return geome.indexOf(':') >= 0 ? new ResourceLocation(geome).toString()
				: new ResourceLocation("orespawn", geome).toString();
	}

	private static double getDouble(JsonObject json, String key, double fallback) {
		if (!json.has(key)) {
			return fallback;
		}
		try {
			double value = json.get(key).getAsDouble();
			return Double.isFinite(value) ? value : fallback;
		} catch (RuntimeException e) {
			LOGGER.warn("Invalid OreSpawn geome config number for '{}'; using {}", key, fallback);
			return fallback;
		}
	}

	private static double getNonNegativeDouble(JsonObject json, String key, double fallback) {
		double value = getDouble(json, key, fallback);
		if (value < 0.0D) {
			LOGGER.warn("OreSpawn geome config '{}' must be non-negative; using {}", key, fallback);
			return fallback;
		}
		return value;
	}

	private static double getBoundedDouble(JsonObject json, String key, double fallback, double min, double max) {
		double value = getDouble(json, key, fallback);
		if (value < min || value > max) {
			double clamped = Math.max(min, Math.min(max, value));
			LOGGER.warn("OreSpawn geome config '{}' value {} is outside {}..{}; using {}",
					key, value, min, max, clamped);
			return clamped;
		}
		return value;
	}

	private static int getInt(JsonObject json, String key, int fallback) {
		if (!json.has(key)) {
			return fallback;
		}
		try {
			return json.get(key).getAsInt();
		} catch (RuntimeException e) {
			LOGGER.warn("Invalid OreSpawn geome config integer for '{}'; using {}", key, fallback);
			return fallback;
		}
	}

	private static int getBoundedInt(JsonObject json, String key, int fallback, int min, int max) {
		int value = getInt(json, key, fallback);
		if (value < min || value > max) {
			int clamped = Math.max(min, Math.min(max, value));
			LOGGER.warn("OreSpawn geome config '{}' value {} is outside {}..{}; using {}",
					key, value, min, max, clamped);
			return clamped;
		}
		return value;
	}

	private static boolean getBoolean(JsonObject json, String key, boolean fallback) {
		if (!json.has(key)) {
			return fallback;
		}
		try {
			return json.get(key).getAsBoolean();
		} catch (RuntimeException e) {
			LOGGER.warn("Invalid OreSpawn geome config boolean for '{}'; using {}", key, fallback);
			return fallback;
		}
	}

	private static String getString(JsonObject json, String key, String fallback) {
		if (!json.has(key)) {
			return fallback;
		}
		try {
			return json.get(key).getAsString();
		} catch (RuntimeException e) {
			LOGGER.warn("Invalid OreSpawn geome config string for '{}'; using '{}'", key, fallback);
			return fallback;
		}
	}

	private static JsonObject getObject(JsonObject json, String key, JsonObject fallback) {
		if (!json.has(key)) {
			return fallback;
		}
		if (!json.get(key).isJsonObject()) {
			LOGGER.warn("Invalid OreSpawn geome config object for '{}'; using defaults", key);
			return fallback;
		}
		return json.getAsJsonObject(key);
	}

	private static void addIfMissing(JsonObject json, String key, boolean value) {
		if (!json.has(key)) {
			json.addProperty(key, value);
		}
	}

	private static void addIfMissing(JsonObject json, String key, int value) {
		if (!json.has(key)) {
			json.addProperty(key, value);
		}
	}

	private static JsonObject defaultConfig() {
		JsonObject root = new JsonObject();
		root.addProperty("schema_version", SCHEMA_VERSION);
		root.addProperty("biome_defaults_revision", BIOME_DEFAULTS_REVISION);
		root.addProperty("worldgen_alias_defaults_revision", WORLDGEN_ALIAS_DEFAULTS_REVISION);
		root.addProperty("ore_defaults_revision", ORE_DEFAULTS_REVISION);
		root.addProperty("geology_mode", OreSpawnConfig.geologyMode().name().toLowerCase(java.util.Locale.ROOT));
		root.addProperty("place_fluid_deposits", true);
		root.addProperty("manage_vanilla_ores", false);
		root.addProperty("suppress_all_ore_features", false);
		root.addProperty("default_template", "");
		root.addProperty("geome_scale", 384.0D);
		root.addProperty("biome_influence", 1.15D);
		root.addProperty("regional_noise_influence", 0.90D);
		root.addProperty("boundary_noise_influence", 0.45D);
		root.add("formations", defaultFormationConfig());
		root.add("worldgen_aliases", new JsonObject());

		JsonObject geomes = new JsonObject();
		addGeome(geomes, "stable_craton", 1.0D, 0.9D, 1.0D, 1.4D, 0.25D);
		addGeome(geomes, "mountain_belt", 1.0D, 0.55D, 2.8D, 1.35D, 0.55D);
		addGeome(geomes, "volcanic_arc", 0.9D, 0.35D, 0.75D, 1.25D, 3.6D);
		addGeome(geomes, "sedimentary_basin", 1.0D, 3.2D, 0.45D, 0.35D, 0.15D);
		addGeome(geomes, "coastal_shelf", 0.9D, 3.0D, 0.35D, 0.25D, 0.25D);
		addGeome(geomes, "arid_basin", 0.9D, 2.8D, 0.35D, 0.45D, 0.35D);
		addGeome(geomes, "wetland_basin", 0.8D, 2.5D, 0.45D, 0.25D, 0.15D);
		addGeome(geomes, "glacial_highland", 0.8D, 0.75D, 2.0D, 1.25D, 0.35D);
		root.add("geomes", geomes);

		root.add("biomes", defaultBiomeRules());

		JsonObject dictionaryRules = new JsonObject();
		addWeights(dictionaryRules, "MOUNTAIN", "mountain_belt", 3.0D, "stable_craton", 0.75D);
		addWeights(dictionaryRules, "HILLS", "mountain_belt", 1.5D, "stable_craton", 0.75D);
		addWeights(dictionaryRules, "OCEAN", "coastal_shelf", 3.0D, "sedimentary_basin", 1.0D);
		addWeights(dictionaryRules, "RIVER", "coastal_shelf", 1.8D, "sedimentary_basin", 1.4D);
		addWeights(dictionaryRules, "BEACH", "coastal_shelf", 3.0D);
		addWeights(dictionaryRules, "SANDY", "arid_basin", 2.0D, "sedimentary_basin", 1.0D);
		addWeights(dictionaryRules, "DRY", "arid_basin", 1.6D);
		addWeights(dictionaryRules, "WET", "wetland_basin", 1.8D, "sedimentary_basin", 0.8D);
		addWeights(dictionaryRules, "SWAMP", "wetland_basin", 3.0D);
		addWeights(dictionaryRules, "SNOWY", "glacial_highland", 2.4D, "mountain_belt", 0.6D);
		addWeights(dictionaryRules, "COLD", "glacial_highland", 1.2D);
		addWeights(dictionaryRules, "HOT", "arid_basin", 0.8D, "volcanic_arc", 0.35D);
		addWeights(dictionaryRules, "MESA", "arid_basin", 3.0D, "sedimentary_basin", 2.0D);
		addWeights(dictionaryRules, "FOREST", "stable_craton", 1.2D);
		addWeights(dictionaryRules, "PLAINS", "stable_craton", 1.1D, "sedimentary_basin", 0.6D);
		addWeights(dictionaryRules, "SAVANNA", "arid_basin", 1.5D, "stable_craton", 0.75D);
		addWeights(dictionaryRules, "CONIFEROUS", "stable_craton", 1.0D, "glacial_highland", 0.5D);
		addWeights(dictionaryRules, "JUNGLE", "wetland_basin", 1.5D, "stable_craton", 1.0D);
		addWeights(dictionaryRules, "LUSH", "wetland_basin", 1.0D, "stable_craton", 0.5D);
		addWeights(dictionaryRules, "MUSHROOM", "coastal_shelf", 1.0D, "volcanic_arc", 0.75D);
		addWeights(dictionaryRules, "PLATEAU", "mountain_belt", 1.0D, "stable_craton", 0.5D);
		addWeights(dictionaryRules, "PEAK", "mountain_belt", 3.0D, "glacial_highland", 0.5D);
		addWeights(dictionaryRules, "SLOPE", "mountain_belt", 2.0D, "stable_craton", 0.5D);
		addWeights(dictionaryRules, "UNDERGROUND", "sedimentary_basin", 1.5D, "mountain_belt", 0.5D);
		addWeights(dictionaryRules, "WASTELAND", "arid_basin", 2.0D, "sedimentary_basin", 0.75D);
		addWeights(dictionaryRules, "WATER", "coastal_shelf", 0.75D, "sedimentary_basin", 1.0D);
		addWeights(dictionaryRules, "DENSE", "stable_craton", 1.0D, "wetland_basin", 0.5D);
		addWeights(dictionaryRules, "SPARSE", "stable_craton", 0.75D, "arid_basin", 0.5D);
		addWeights(dictionaryRules, "DEAD", "stable_craton", 0.75D, "arid_basin", 0.5D);
		addWeights(dictionaryRules, "MAGICAL", "stable_craton", 1.0D);
		addWeights(dictionaryRules, "SPOOKY", "stable_craton", 0.75D, "wetland_basin", 0.5D);
		root.add("biome_dictionary", dictionaryRules);

		root.add("rocks", new JsonObject());
		root.add("cyano", defaultCyanoConfig());
		root.add("fluid_deposits", new JsonObject());
		root.add("ores", defaultOreConfig());
		root.add("ore_providers", new JsonObject());
		root.add("providers", new JsonObject());
		root.add("terrain_dimensions", new JsonObject());
		root.add("biome_palettes", new JsonObject());
		root.add("dimension_materials", new JsonObject());
		JsonObject retrogen = new JsonObject();
		retrogen.addProperty("enabled", false);
		retrogen.addProperty("force", false);
		retrogen.addProperty("revision", 0);
		retrogen.addProperty("chunks_per_tick", 1);
		root.add("retrogen", retrogen);
		JsonObject bedrock = new JsonObject();
		bedrock.addProperty("enabled", false);
		bedrock.addProperty("retrogen", false);
		bedrock.addProperty("layers", 1);
		JsonArray bedrockDimensions = new JsonArray();
		bedrockDimensions.add("minecraft:overworld");
		bedrockDimensions.add("minecraft:the_nether");
		bedrock.add("dimensions", bedrockDimensions);
		root.add("flat_bedrock", bedrock);
		return root;
	}

	public static JsonObject defaultEditorGeology() {
		JsonObject defaults = defaultConfig();
		JsonObject result = new JsonObject();
		for (String key : new String[] { "geomes", "biomes", "biome_dictionary" }) {
			result.add(key, defaults.get(key).deepCopy());
		}
		return result;
	}

	private static JsonObject defaultTerrainDimensions() {
		JsonObject dimensions = new JsonObject();
		JsonObject overworld = new JsonObject();
		overworld.addProperty("enabled", true);
		overworld.add("biome_ids", new JsonArray());
		overworld.add("biome_namespaces", new JsonArray());
		JsonArray hosts = new JsonArray();
		hosts.add("minecraft:stone");
		hosts.add("minecraft:deepslate");
		overworld.add("host_blocks", hosts);
		overworld.add("host_tags", new JsonArray());
		dimensions.add("minecraft:overworld", overworld);
		return dimensions;
	}

	private static JsonObject defaultCyanoConfig() {
		JsonObject cyano = new JsonObject();
		cyano.addProperty("geome_size", OreSpawnConfig.geomeSize());
		cyano.addProperty("rock_layer_noise", OreSpawnConfig.rockLayerNoise());
		cyano.addProperty("rock_layer_thickness", OreSpawnConfig.geomLayerThickness());
		return cyano;
	}

	private static JsonObject defaultOreConfig() {
		JsonObject ores = new JsonObject();
		addVanillaOverworldOre(ores, "coal_ore", "deepslate_coal_ore", 0, 96, 6.27D, 17,
				OrePattern.CLUSTER, 10, 4, 6);
		setOreFidelity(ores, "coal_ore", OreHeightDistribution.TRIANGLE, 0.87D);
		addVanillaOverworldOre(ores, "iron_ore", "deepslate_iron_ore", -64, 256, 25.5D, 9,
				OrePattern.VEIN, 8, 4, 4);
		setOreFidelity(ores, "iron_ore", OreHeightDistribution.TRIANGLE, 0.80D);
		addVanillaOverworldOre(ores, "copper_ore", "deepslate_copper_ore", -16, 112, 12.65D, 10,
				OrePattern.CLOUD, 6, 4, 4);
		setOreFidelity(ores, "copper_ore", OreHeightDistribution.TRIANGLE, 0.27D);
		addVanillaOverworldOre(ores, "gold_ore", "deepslate_gold_ore", -64, 32, 2.95D, 9,
				OrePattern.VEIN, 8, 4, 4);
		setOreFidelity(ores, "gold_ore", OreHeightDistribution.TRIANGLE, 0.94D);
		addVanillaOverworldOre(ores, "redstone_ore", "deepslate_redstone_ore", -64, 15, 4.68D, 8,
				OrePattern.VEIN, 8, 4, 4);
		setOreFidelity(ores, "redstone_ore", OreHeightDistribution.UNIFORM_BOTTOM_TRIANGLE, 0.78D);
		addVanillaOverworldOre(ores, "diamond_ore", "deepslate_diamond_ore", -64, 16, 1.83D, 8,
				OrePattern.CLUSTER, 6, 3, 4);
		setOreFidelity(ores, "diamond_ore", OreHeightDistribution.BOTTOM_TRIANGLE, 0.94D);
		addVanillaOverworldOre(ores, "lapis_ore", "deepslate_lapis_ore", -64, 64, 3.38D, 7,
				OrePattern.CLOUD, 8, 4, 4);
		setOreFidelity(ores, "lapis_ore", OreHeightDistribution.TRIANGLE, 0.80D);
		addVanillaOverworldOre(ores, "emerald_ore", "deepslate_emerald_ore", -16, 128, 0.33D, 3,
				OrePattern.CLUSTER, 8, 5, 3);
		setOreFidelity(ores, "emerald_ore", OreHeightDistribution.TRIANGLE, 0.65D);
		setOreGeomeWeights(ores, "coal_ore", "sedimentary_basin", 1.8D,
				"coastal_shelf", 1.5D, "wetland_basin", 1.4D, "volcanic_arc", 0.45D);
		setOreGeomeWeights(ores, "iron_ore", "mountain_belt", 1.4D,
				"volcanic_arc", 1.25D, "stable_craton", 1.15D);
		setOreGeomeWeights(ores, "copper_ore", "mountain_belt", 1.5D, "volcanic_arc", 1.6D);
		setOreGeomeWeights(ores, "gold_ore", "mountain_belt", 1.5D,
				"volcanic_arc", 1.7D, "arid_basin", 1.25D);
		setOreGeomeWeights(ores, "diamond_ore", "stable_craton", 1.35D, "volcanic_arc", 1.2D);
		setOreGeomeWeights(ores, "lapis_ore", "mountain_belt", 1.6D, "glacial_highland", 1.25D);
		setOreGeomeWeights(ores, "emerald_ore", "mountain_belt", 4.0D,
				"glacial_highland", 2.0D, "stable_craton", 0.15D, "sedimentary_basin", 0.05D,
				"coastal_shelf", 0.05D, "arid_basin", 0.15D, "wetland_basin", 0.05D,
				"volcanic_arc", 0.35D);
		addVanillaNetherOre(ores, "nether_gold_ore", 0, 127, 5.2D, 10, OrePattern.CLUSTER, 8, 5, 5);
		addVanillaNetherOre(ores, "nether_quartz_ore", 0, 127, 11.2D, 14, OrePattern.VEIN, 8, 4, 4);
		addVanillaNetherOre(ores, "ancient_debris", 8, 120, 1.25D, 3, OrePattern.CLUSTER, 12, 8, 3);
		return ores;
	}

	private static void addVanillaOverworldOre(JsonObject ores, String id, String deepId,
			int minY, int maxY, double frequency, int quantity, OrePattern pattern,
			int spread, int verticalSpread, int nodeSize) {
		JsonObject ore = vanillaOre(id);
		ore.addProperty("deep_output", "minecraft:" + deepId);
		ore.addProperty("deep_output_max_y", -1);
		JsonObject rule = oreRule(minY, maxY, frequency, quantity, pattern, spread, verticalSpread, nodeSize);
		rule.addProperty("height_distribution", OreHeightDistribution.TRIANGLE.configName);
		JsonArray families = new JsonArray();
		for (RockFamily family : RockFamily.values()) {
			families.add(family.configName);
		}
		rule.add("host_families", families);
		JsonArray tags = new JsonArray();
		tags.add("minecraft:stone_ore_replaceables");
		tags.add("minecraft:deepslate_ore_replaceables");
		rule.add("host_tags", tags);
		ore.getAsJsonObject("dimensions").add("minecraft:overworld", rule);
		ores.add("minecraft:" + id, ore);
	}

	private static void addVanillaNetherOre(JsonObject ores, String id, int minY, int maxY,
			double frequency, int quantity, OrePattern pattern, int spread, int verticalSpread, int nodeSize) {
		JsonObject ore = vanillaOre(id);
		JsonObject rule = oreRule(minY, maxY, frequency, quantity, pattern, spread, verticalSpread, nodeSize);
		JsonArray tags = new JsonArray();
		tags.add("minecraft:base_stone_nether");
		rule.add("host_tags", tags);
		ore.getAsJsonObject("dimensions").add("minecraft:the_nether", rule);
		ores.add("minecraft:" + id, ore);
	}

	private static JsonObject vanillaOre(String id) {
		JsonObject ore = new JsonObject();
		ore.addProperty("enabled", true);
		ore.addProperty("source_mod", "minecraft");
		ore.addProperty("native_generation", true);
		ore.add("dimensions", new JsonObject());
		return ore;
	}

	private static JsonObject oreRule(int minY, int maxY, double frequency, int quantity,
			OrePattern pattern, int spread, int verticalSpread, int nodeSize) {
		JsonObject rule = new JsonObject();
		rule.addProperty("enabled", true);
		rule.addProperty("min_y", minY);
		rule.addProperty("max_y", maxY);
		rule.addProperty("frequency", frequency);
		rule.addProperty("quantity", quantity);
		rule.addProperty("pattern", pattern.configName);
		rule.addProperty("height_distribution", OreHeightDistribution.UNIFORM.configName);
		rule.addProperty("spread", spread);
		rule.addProperty("vertical_spread", verticalSpread);
		rule.addProperty("node_size", nodeSize);
		return rule;
	}

	private static void setOreGeomeWeights(JsonObject ores, String oreId, Object... values) {
		JsonObject rule = ores.getAsJsonObject("minecraft:" + oreId)
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");
		JsonObject weights = new JsonObject();
		for (int i = 0; i < values.length; i += 2) {
			weights.addProperty((String) values[i], (Double) values[i + 1]);
		}
		rule.add("geomes", weights);
	}

	private static void setOreFidelity(JsonObject ores, String oreId,
			OreHeightDistribution distribution, double discardChanceOnAirExposure) {
		JsonObject rule = ores.getAsJsonObject("minecraft:" + oreId)
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");
		rule.addProperty("height_distribution", distribution.configName);
		rule.addProperty("discard_chance_on_air_exposure", discardChanceOnAirExposure);
	}

	private static void addDefaultOre(JsonObject ores, String id, OreGenerationSettings settings) {
		JsonObject ore = new JsonObject();
		ore.addProperty("enabled", true);
		ore.addProperty("source_mod", "orespawn");
		JsonObject dimensions = new JsonObject();
		JsonObject overworld = new JsonObject();
		overworld.addProperty("enabled", true);
		overworld.addProperty("min_y", settings.minY());
		overworld.addProperty("max_y", settings.maxY());
		overworld.addProperty("frequency", settings.frequency());
		overworld.addProperty("quantity", settings.quantity());
		overworld.addProperty("pattern", OrePattern.VEIN.configName);
		overworld.addProperty("height_distribution", OreHeightDistribution.UNIFORM.configName);
		overworld.addProperty("spread", 8);
		overworld.addProperty("vertical_spread", 4);
		overworld.addProperty("node_size", 4);
		JsonArray families = new JsonArray();
		for (RockFamily family : RockFamily.values()) {
			families.add(family.configName);
		}
		overworld.add("host_families", families);
		JsonArray tags = new JsonArray();
		tags.add("minecraft:stone_ore_replaceables");
		tags.add("minecraft:deepslate_ore_replaceables");
		overworld.add("host_tags", tags);
		dimensions.add("minecraft:overworld", overworld);
		ore.add("dimensions", dimensions);
		ores.add(id, ore);
	}

	private static JsonObject defaultFormationConfig() {
		JsonObject formations = new JsonObject();
		formations.addProperty("algorithm", Algorithm.STABLE_LAYERS.configName);
		formations.addProperty("horizontal_size", Preset.AVERAGE.configName);
		formations.addProperty("vertical_thickness", Preset.AVERAGE.configName);
		formations.addProperty("waviness", Preset.AVERAGE.configName);
		formations.addProperty("edge_irregularity", Preset.AVERAGE.configName);
		formations.addProperty("formation_continuity", Preset.AVERAGE.configName);
		formations.add("custom", customFormationConfig(
				256.0D, 100.0D, 8, 48.0D, 2, 0.85D,
				256.0D, 64.0D, 12.0D));
		return formations;
	}

	private static JsonObject legacyFormationConfig() {
		JsonObject formations = new JsonObject();
		formations.addProperty("algorithm", Algorithm.SKY_V1.configName);
		formations.addProperty("horizontal_size", Preset.CUSTOM.configName);
		formations.addProperty("vertical_thickness", Preset.CUSTOM.configName);
		formations.addProperty("waviness", Preset.CUSTOM.configName);
		formations.addProperty("edge_irregularity", Preset.CUSTOM.configName);
		formations.addProperty("formation_continuity", Preset.CUSTOM.configName);
		formations.add("custom", customFormationConfig(
				OreSpawnConfig.rockLayerNoise() * 8.0D,
				OreSpawnConfig.geomeSize(),
				OreSpawnConfig.geomLayerThickness(),
				60.0D,
				4,
				0.0D,
				256.0D,
				64.0D,
				0.0D));
		return formations;
	}

	private static JsonObject customFormationConfig(double stratumWavelength, double familyRegionWavelength,
			int verticalThickness, double wavinessAmplitude, int edgeOctaves, double continuity,
			double wavinessWavelength, double edgeWavelength, double edgeAmplitude) {
		JsonObject custom = new JsonObject();
		custom.addProperty("stratum_wavelength", stratumWavelength);
		custom.addProperty("family_region_wavelength", familyRegionWavelength);
		custom.addProperty("vertical_thickness", verticalThickness);
		custom.addProperty("waviness_wavelength", wavinessWavelength);
		custom.addProperty("waviness_amplitude", wavinessAmplitude);
		custom.addProperty("edge_wavelength", edgeWavelength);
		custom.addProperty("edge_amplitude", edgeAmplitude);
		custom.addProperty("edge_octaves", edgeOctaves);
		custom.addProperty("continuity", continuity);
		return custom;
	}

	private static void addGeome(JsonObject geomes, String name, double base, double sedimentary,
			double metamorphic, double intrusive, double volcanic) {
		JsonObject geome = new JsonObject();
		geome.addProperty("base", base);
		JsonObject families = new JsonObject();
		families.addProperty(RockFamily.SEDIMENTARY.configName, sedimentary);
		families.addProperty(RockFamily.METAMORPHIC.configName, metamorphic);
		families.addProperty(RockFamily.IGNEOUS_INTRUSIVE.configName, intrusive);
		families.addProperty(RockFamily.IGNEOUS_VOLCANIC.configName, volcanic);
		geome.add("families", families);
		geomes.add(name, geome);
	}

	private static void addVanillaBiomeDefaults(JsonObject biomes) {
		addWeights(biomes, "minecraft:ocean", "coastal_shelf", 4.0D);
		addWeights(biomes, "minecraft:deep_ocean", "coastal_shelf", 4.0D, "sedimentary_basin", 1.5D);
		addWeights(biomes, "minecraft:warm_ocean", "coastal_shelf", 4.0D, "sedimentary_basin", 1.0D);
		addWeights(biomes, "minecraft:lukewarm_ocean", "coastal_shelf", 4.0D, "sedimentary_basin", 1.0D);
		addWeights(biomes, "minecraft:deep_lukewarm_ocean", "coastal_shelf", 4.0D, "sedimentary_basin", 1.5D);
		addWeights(biomes, "minecraft:cold_ocean", "coastal_shelf", 4.0D, "glacial_highland", 0.8D);
		addWeights(biomes, "minecraft:deep_cold_ocean", "coastal_shelf", 4.0D, "sedimentary_basin", 1.5D,
				"glacial_highland", 0.8D);
		addWeights(biomes, "minecraft:frozen_ocean", "coastal_shelf", 4.0D, "glacial_highland", 1.5D);
		addWeights(biomes, "minecraft:deep_frozen_ocean", "coastal_shelf", 4.0D, "sedimentary_basin", 1.5D,
				"glacial_highland", 1.5D);
		addWeights(biomes, "minecraft:river", "coastal_shelf", 2.0D, "sedimentary_basin", 2.0D);
		addWeights(biomes, "minecraft:frozen_river", "coastal_shelf", 2.0D, "sedimentary_basin", 2.0D,
				"glacial_highland", 1.5D);
		addWeights(biomes, "minecraft:beach", "coastal_shelf", 4.0D);
		addWeights(biomes, "minecraft:snowy_beach", "coastal_shelf", 3.5D, "glacial_highland", 1.5D);
		addWeights(biomes, "minecraft:stony_shore", "coastal_shelf", 2.5D, "mountain_belt", 1.5D);
		addWeights(biomes, "minecraft:plains", "stable_craton", 2.0D, "sedimentary_basin", 1.0D);
		addWeights(biomes, "minecraft:sunflower_plains", "stable_craton", 2.0D, "sedimentary_basin", 1.0D);
		addWeights(biomes, "minecraft:snowy_plains", "glacial_highland", 3.5D, "sedimentary_basin", 0.8D);
		addWeights(biomes, "minecraft:ice_spikes", "glacial_highland", 4.0D, "mountain_belt", 1.2D);
		addWeights(biomes, "minecraft:forest", "stable_craton", 2.0D);
		addWeights(biomes, "minecraft:flower_forest", "stable_craton", 2.0D, "sedimentary_basin", 0.5D);
		addWeights(biomes, "minecraft:birch_forest", "stable_craton", 2.0D);
		addWeights(biomes, "minecraft:old_growth_birch_forest", "stable_craton", 2.3D);
		addWeights(biomes, "minecraft:dark_forest", "stable_craton", 2.0D, "wetland_basin", 0.5D);
		addWeights(biomes, "minecraft:taiga", "stable_craton", 1.5D, "glacial_highland", 0.75D);
		addWeights(biomes, "minecraft:snowy_taiga", "glacial_highland", 2.5D, "stable_craton", 1.0D);
		addWeights(biomes, "minecraft:old_growth_pine_taiga", "stable_craton", 2.0D,
				"glacial_highland", 0.8D);
		addWeights(biomes, "minecraft:old_growth_spruce_taiga", "stable_craton", 2.0D,
				"glacial_highland", 1.0D);
		addWeights(biomes, "minecraft:desert", "arid_basin", 4.0D, "sedimentary_basin", 1.5D);
		addWeights(biomes, "minecraft:savanna", "arid_basin", 2.5D, "stable_craton", 0.75D);
		addWeights(biomes, "minecraft:savanna_plateau", "arid_basin", 2.5D, "mountain_belt", 2.5D);
		addWeights(biomes, "minecraft:windswept_savanna", "mountain_belt", 5.0D, "arid_basin", 1.5D);
		addWeights(biomes, "minecraft:badlands", "arid_basin", 3.5D, "sedimentary_basin", 3.0D);
		addWeights(biomes, "minecraft:wooded_badlands", "arid_basin", 3.0D, "sedimentary_basin", 2.5D,
				"mountain_belt", 0.8D);
		addWeights(biomes, "minecraft:eroded_badlands", "arid_basin", 3.0D, "sedimentary_basin", 3.0D,
				"mountain_belt", 1.2D);
		addWeights(biomes, "minecraft:windswept_hills", "mountain_belt", 4.0D, "stable_craton", 1.0D);
		addWeights(biomes, "minecraft:windswept_forest", "mountain_belt", 3.0D, "stable_craton", 1.3D);
		addWeights(biomes, "minecraft:windswept_gravelly_hills", "mountain_belt", 4.0D,
				"glacial_highland", 0.8D);
		addWeights(biomes, "minecraft:meadow", "stable_craton", 1.7D, "mountain_belt", 1.2D);
		addWeights(biomes, "minecraft:grove", "glacial_highland", 1.8D, "mountain_belt", 1.4D);
		addWeights(biomes, "minecraft:snowy_slopes", "glacial_highland", 3.0D, "mountain_belt", 2.4D);
		addWeights(biomes, "minecraft:jagged_peaks", "mountain_belt", 4.8D, "glacial_highland", 2.0D);
		addWeights(biomes, "minecraft:frozen_peaks", "glacial_highland", 3.6D, "mountain_belt", 3.0D);
		addWeights(biomes, "minecraft:stony_peaks", "mountain_belt", 4.5D, "volcanic_arc", 0.8D);
		addWeights(biomes, "minecraft:dripstone_caves", "sedimentary_basin", 2.8D, "coastal_shelf", 1.0D);
		addWeights(biomes, "minecraft:lush_caves", "wetland_basin", 2.5D, "sedimentary_basin", 1.2D);
		addWeights(biomes, "minecraft:swamp", "wetland_basin", 4.0D, "sedimentary_basin", 1.5D);
		addWeights(biomes, "minecraft:jungle", "wetland_basin", 1.5D, "stable_craton", 1.5D);
		addWeights(biomes, "minecraft:sparse_jungle", "stable_craton", 1.5D, "wetland_basin", 1.0D);
		addWeights(biomes, "minecraft:bamboo_jungle", "wetland_basin", 2.0D, "stable_craton", 1.2D);
		addWeights(biomes, "minecraft:mushroom_fields", "coastal_shelf", 2.0D, "volcanic_arc", 1.5D,
				"stable_craton", 1.0D);
	}

	static JsonObject defaultBiomeRules() {
		JsonObject biomeRules = new JsonObject();
		addVanillaBiomeDefaults(biomeRules);
		addBiomesOPlentyDefaults(biomeRules);
		addBiomesYoullGoDefaults(biomeRules);
		return biomeRules;
	}

	private static void addBiomesOPlentyDefaults(JsonObject biomes) {
		String[] stable = { "cherry_blossom_grove", "clover_patch", "dead_forest", "field",
				"forested_field", "lavender_field", "lavender_forest", "maple_woods", "old_growth_dead_forest",
				"old_growth_woodland", "ominous_woods", "orchard", "origin_valley", "pasture", "pumpkin_patch",
				"redwood_forest", "seasonal_forest", "woodland" };
		addBOPWeights(biomes, stable, "stable_craton", 2.0D, "sedimentary_basin", 0.5D);
		addWeights(biomes, bop("bamboo_grove"), "wetland_basin", 3.0D, "stable_craton", 1.5D);
		addWeights(biomes, bop("grassland"), "stable_craton", 1.5D, "arid_basin", 1.0D,
				"sedimentary_basin", 1.0D);
		addWeights(biomes, bop("prairie"), "stable_craton", 1.5D, "arid_basin", 1.0D,
				"sedimentary_basin", 1.0D);

		String[] mountains = { "crag", "highland", "jade_cliffs", "rainbow_hills" };
		addBOPWeights(biomes, mountains, "mountain_belt", 3.8D, "stable_craton", 0.8D);
		addWeights(biomes, bop("highland_moor"), "mountain_belt", 2.5D, "wetland_basin", 1.5D);
		addWeights(biomes, bop("rocky_rainforest"), "mountain_belt", 2.0D, "wetland_basin", 3.0D);
		addWeights(biomes, bop("rocky_shrubland"), "mountain_belt", 2.0D, "arid_basin", 2.5D);
		addWeights(biomes, bop("volcano"), "volcanic_arc", 6.0D, "mountain_belt", 1.0D);
		addWeights(biomes, bop("volcanic_plains"), "volcanic_arc", 4.0D, "arid_basin", 0.8D);

		String[] dry = { "dryland", "lush_desert", "lush_savanna", "mediterranean_forest", "scrubland",
				"shrubland", "wasteland", "wooded_scrubland", "wooded_wasteland" };
		addBOPWeights(biomes, dry, "arid_basin", 3.0D, "sedimentary_basin", 1.0D);
		addWeights(biomes, bop("cold_desert"), "arid_basin", 3.0D, "glacial_highland", 2.0D,
				"sedimentary_basin", 1.0D);
		addWeights(biomes, bop("dune_beach"), "coastal_shelf", 4.0D, "arid_basin", 2.0D,
				"sedimentary_basin", 1.0D);

		String[] wet = { "bayou", "bog", "floodplain", "fungal_jungle", "marsh", "mystic_grove",
				"rainforest", "wetland" };
		addBOPWeights(biomes, wet, "wetland_basin", 3.5D, "sedimentary_basin", 1.5D);
		addWeights(biomes, bop("muskeg"), "wetland_basin", 2.5D, "glacial_highland", 2.0D,
				"sedimentary_basin", 1.0D);
		addWeights(biomes, bop("glowing_grotto"), "wetland_basin", 2.0D, "sedimentary_basin", 2.0D);
		addWeights(biomes, bop("tropics"), "coastal_shelf", 2.0D, "wetland_basin", 2.0D,
				"stable_craton", 0.5D);

		String[] cold = { "boreal_forest", "coniferous_forest", "fir_clearing", "snowy_coniferous_forest",
				"snowy_fir_clearing", "snowy_maple_woods", "tundra" };
		addBOPWeights(biomes, cold, "glacial_highland", 2.0D, "stable_craton", 1.0D);
	}

	private static String bop(String path) {
		return "biomesoplenty:" + path;
	}

	private static void addBOPWeights(JsonObject biomes, String[] names, Object... values) {
		for (String name : names) {
			addWeights(biomes, bop(name), values);
		}
	}

	private static void addBiomesYoullGoDefaults(JsonObject biomes) {
		String[] stable = { "allium_fields", "amaranth_fields", "autumnal_forest", "autumnal_valley",
				"cherry_blossom_forest", "coconino_meadow", "ebony_woods", "firecracker_shrubland",
				"forgotten_forest", "fragment_forest", "jacaranda_forest", "orchard", "prairie",
				"red_oak_forest", "redwood_thicket", "rose_fields", "temperate_grove", "twilight_meadow" };
		addBYGWeights(biomes, stable, "stable_craton", 2.0D, "sedimentary_basin", 0.5D);

		String[] coldWoodland = { "aspen_forest", "autumnal_taiga", "borealis_grove", "cika_woods",
				"coniferous_forest", "frosted_coniferous_forest", "frosted_taiga", "maple_taiga",
				"weeping_witch_forest", "zelkova_forest" };
		addBYGWeights(biomes, coldWoodland, "glacial_highland", 2.0D, "stable_craton", 1.0D);

		String[] savannas = { "araucaria_savanna", "baobab_savanna" };
		addBYGWeights(biomes, savannas, "arid_basin", 2.5D, "stable_craton", 0.8D);
		String[] deserts = { "atacama_desert", "mojave_desert", "windswept_desert" };
		addBYGWeights(biomes, deserts, "arid_basin", 4.0D, "sedimentary_basin", 1.5D);
		String[] badlands = { "red_rock_valley", "sierra_badlands", "windswept_dunes" };
		addBYGWeights(biomes, badlands, "arid_basin", 3.5D, "sedimentary_basin", 3.0D);

		String[] wetlands = { "bayou", "cypress_swamplands", "white_mangrove_marshes" };
		addBYGWeights(biomes, wetlands, "wetland_basin", 4.0D, "sedimentary_basin", 1.5D);
		String[] rainforests = { "temperate_rainforest", "tropical_rainforest" };
		addBYGWeights(biomes, rainforests, "wetland_basin", 3.5D, "stable_craton", 1.0D,
				"sedimentary_basin", 0.8D);

		addWeights(biomes, byg("lush_stacks"), "coastal_shelf", 4.0D, "wetland_basin", 1.2D,
				"sedimentary_basin", 1.0D);
		addWeights(biomes, byg("dead_sea"), "coastal_shelf", 4.0D, "sedimentary_basin", 1.8D,
				"arid_basin", 1.2D);
		addWeights(biomes, byg("windswept_beach"), "coastal_shelf", 4.0D, "mountain_belt", 1.0D);
		addWeights(biomes, byg("rainbow_beach"), "coastal_shelf", 4.0D, "stable_craton", 0.5D);

		addWeights(biomes, byg("black_forest"), "mountain_belt", 2.5D, "stable_craton", 1.5D);
		addWeights(biomes, byg("canadian_shield"), "mountain_belt", 3.5D, "glacial_highland", 1.5D,
				"stable_craton", 0.5D);
		addWeights(biomes, byg("crag_gardens"), "mountain_belt", 3.5D, "wetland_basin", 1.5D);
		addWeights(biomes, byg("dacite_ridges"), "mountain_belt", 4.0D, "volcanic_arc", 2.0D,
				"glacial_highland", 1.0D);
		addWeights(biomes, byg("guiana_shield"), "mountain_belt", 3.5D, "wetland_basin", 2.0D);
		addWeights(biomes, byg("howling_peaks"), "mountain_belt", 5.0D, "glacial_highland", 1.0D);
		addWeights(biomes, byg("skyris_vale"), "mountain_belt", 3.0D, "glacial_highland", 1.0D,
				"stable_craton", 1.0D);

		addWeights(biomes, byg("cardinal_tundra"), "glacial_highland", 3.5D,
				"sedimentary_basin", 1.0D);
		addWeights(biomes, byg("shattered_glacier"), "glacial_highland", 5.0D, "mountain_belt", 2.0D);
		addWeights(biomes, byg("basalt_barrera"), "volcanic_arc", 6.0D, "coastal_shelf", 2.0D,
				"mountain_belt", 1.0D);
		addWeights(biomes, byg("dacite_shore"), "volcanic_arc", 4.0D, "coastal_shelf", 3.0D,
				"mountain_belt", 1.0D);
	}

	private static String byg(String path) {
		return "byg:" + path;
	}

	private static void addBYGWeights(JsonObject biomes, String[] names, Object... values) {
		for (String name : names) {
			addWeights(biomes, byg(name), values);
		}
	}

	private static void addDefaultRocks(JsonObject rocks) {
		addRock(rocks, "minecraft:andesite", RockFamily.IGNEOUS_VOLCANIC, 68, 42, 1.0D,
				"volcanic_arc", 3.0D, "mountain_belt", 1.2D);
		addRock(rocks, "minecraft:basalt", RockFamily.IGNEOUS_VOLCANIC, 72, 36, 1.2D,
				"volcanic_arc", 4.0D, "coastal_shelf", 0.7D);
		addRock(rocks, "orespawn:rhyolite", RockFamily.IGNEOUS_VOLCANIC, 70, 36, 1.0D,
				"volcanic_arc", 3.5D, "mountain_belt", 0.8D);
		addRock(rocks, "orespawn:basaltic_glass", RockFamily.IGNEOUS_VOLCANIC, 78, 24, 0.75D,
				"volcanic_arc", 4.5D);
		addRock(rocks, "orespawn:scoria", RockFamily.IGNEOUS_VOLCANIC, 80, 22, 0.85D,
				"volcanic_arc", 4.0D);
		addRock(rocks, "minecraft:tuff", RockFamily.IGNEOUS_VOLCANIC, 74, 30, 0.9D,
				"volcanic_arc", 3.5D, "arid_basin", 0.8D);
		addRock(rocks, "orespawn:pumice", RockFamily.IGNEOUS_VOLCANIC, 82, 20, 0.65D,
				"volcanic_arc", 4.0D);

		addRock(rocks, "minecraft:diorite", RockFamily.IGNEOUS_INTRUSIVE, 36, 42, 1.0D,
				"stable_craton", 1.3D, "mountain_belt", 1.0D);
		addRock(rocks, "minecraft:granite", RockFamily.IGNEOUS_INTRUSIVE, 30, 48, 1.1D,
				"stable_craton", 2.0D, "mountain_belt", 1.4D);
		addRock(rocks, "orespawn:pegmatite", RockFamily.IGNEOUS_INTRUSIVE, 26, 36, 0.8D,
				"stable_craton", 1.6D, "mountain_belt", 1.2D);
		addRock(rocks, "orespawn:diabase", RockFamily.IGNEOUS_INTRUSIVE, 30, 40, 1.0D,
				"stable_craton", 1.2D, "volcanic_arc", 1.2D);
		addRock(rocks, "orespawn:gabbro", RockFamily.IGNEOUS_INTRUSIVE, 20, 44, 1.1D,
				"stable_craton", 1.2D, "mountain_belt", 1.2D);
		addRock(rocks, "orespawn:peridotite", RockFamily.IGNEOUS_INTRUSIVE, 10, 34, 0.85D,
				"mountain_belt", 1.5D, "volcanic_arc", 1.0D);

		addRock(rocks, "orespawn:shale", RockFamily.SEDIMENTARY, 62, 36, 1.15D,
				"sedimentary_basin", 3.5D, "wetland_basin", 2.0D, "coastal_shelf", 1.8D);
		addRock(rocks, "orespawn:conglomerate", RockFamily.SEDIMENTARY, 58, 32, 0.9D,
				"sedimentary_basin", 2.2D, "mountain_belt", 0.8D);
		addRock(rocks, "orespawn:dolomite", RockFamily.SEDIMENTARY, 55, 38, 0.9D,
				"coastal_shelf", 2.2D, "sedimentary_basin", 1.8D);
		addRock(rocks, "orespawn:limestone", RockFamily.SEDIMENTARY, 60, 40, 1.0D,
				"coastal_shelf", 3.0D, "sedimentary_basin", 1.6D);
		addRock(rocks, "orespawn:siltstone", RockFamily.SEDIMENTARY, 66, 34, 1.0D,
				"sedimentary_basin", 2.4D, "wetland_basin", 1.8D);
		addRock(rocks, "orespawn:rock_salt", RockFamily.SEDIMENTARY, 54, 28, 0.6D,
				"arid_basin", 4.0D, "coastal_shelf", 1.2D);
		addRock(rocks, "orespawn:chert", RockFamily.SEDIMENTARY, 48, 32, 0.65D,
				"coastal_shelf", 2.0D, "sedimentary_basin", 1.4D);
		addRock(rocks, "orespawn:gypsum", RockFamily.SEDIMENTARY, 52, 26, 0.65D,
				"arid_basin", 3.5D, "coastal_shelf", 1.0D);
		addRock(rocks, "orespawn:chalk", RockFamily.SEDIMENTARY, 68, 24, 0.65D,
				"coastal_shelf", 3.0D);
		addRock(rocks, "minecraft:sandstone", RockFamily.SEDIMENTARY, 72, 28, 0.55D,
				"arid_basin", 2.5D, "coastal_shelf", 0.8D);

		addRock(rocks, "orespawn:marble", RockFamily.METAMORPHIC, 32, 36, 0.85D,
				"mountain_belt", 1.8D, "stable_craton", 1.2D);
		addRock(rocks, "orespawn:slate", RockFamily.METAMORPHIC, 36, 34, 1.0D,
				"mountain_belt", 2.0D, "sedimentary_basin", 0.7D);
		addRock(rocks, "orespawn:schist", RockFamily.METAMORPHIC, 24, 34, 1.0D,
				"mountain_belt", 2.8D);
		addRock(rocks, "orespawn:gneiss", RockFamily.METAMORPHIC, 18, 38, 1.0D,
				"mountain_belt", 2.6D, "stable_craton", 1.2D);
		addRock(rocks, "orespawn:phyllite", RockFamily.METAMORPHIC, 34, 34, 0.9D,
				"mountain_belt", 2.0D);
		addRock(rocks, "orespawn:amphibolite", RockFamily.METAMORPHIC, 18, 34, 0.9D,
				"mountain_belt", 2.4D, "volcanic_arc", 0.8D);
		addRock(rocks, "orespawn:hornfels", RockFamily.METAMORPHIC, 24, 28, 0.75D,
				"volcanic_arc", 2.0D, "mountain_belt", 1.4D);
		addRock(rocks, "orespawn:quartzite", RockFamily.METAMORPHIC, 30, 34, 0.85D,
				"mountain_belt", 1.8D, "stable_craton", 1.0D);
		addRock(rocks, "orespawn:novaculite", RockFamily.METAMORPHIC, 28, 30, 0.65D,
				"mountain_belt", 1.6D);
	}

	private static void addWeights(JsonObject parent, String key, Object... values) {
		JsonObject weights = new JsonObject();
		for (int i = 0; i + 1 < values.length; i += 2) {
			weights.addProperty((String) values[i], (Double) values[i + 1]);
		}
		parent.add(key, weights);
	}

	private static void addRock(JsonObject rocks, String id, RockFamily family, int peak, int spread,
			double weight, Object... geomeWeights) {
		JsonObject rock = new JsonObject();
		rock.addProperty("enabled", true);
		rock.addProperty("family", family.configName);
		rock.addProperty("depth_peak", peak);
		rock.addProperty("depth_spread", spread);
		rock.addProperty("min_y", BakedGeomeConfig.MIN_Y);
		rock.addProperty("max_y", BakedGeomeConfig.MAX_Y);
		rock.addProperty("weight", weight);
		rock.addProperty("ore_replaceable", true);
		JsonObject geomes = new JsonObject();
		for (int i = 0; i + 1 < geomeWeights.length; i += 2) {
			geomes.addProperty((String) geomeWeights[i], (Double) geomeWeights[i + 1]);
		}
		rock.add("geomes", geomes);
		rocks.add(id, rock);
	}

	private static JsonObject defaultWorldgenAliases() {
		JsonObject aliases = new JsonObject();
		for (Entry<ResourceLocation, ResourceLocation> entry : GeologyBlockAliases.defaultAliases().entrySet()) {
			aliases.addProperty(entry.getKey().toString(), entry.getValue().toString());
		}
		return aliases;
	}
}
