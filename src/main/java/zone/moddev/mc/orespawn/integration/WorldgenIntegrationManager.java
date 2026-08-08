package zone.moddev.mc.orespawn.integration;

import zone.moddev.mc.orespawn.util.JsonCopies;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.api.ProviderStatus;
import zone.moddev.mc.orespawn.api.WorldgenProvider;
import zone.moddev.mc.orespawn.api.OreDimensionSelector;
import zone.moddev.mc.orespawn.worldgen.OreHeightDistribution;
import zone.moddev.mc.orespawn.worldgen.RockFamily;
import zone.moddev.mc.orespawn.init.OreSpawnPatterns;

import net.minecraft.util.ResourceLocation;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Internal owner of provider discovery, validation, merging, and lifecycle. */
public final class WorldgenIntegrationManager {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Pattern MOD_ID = Pattern.compile("^[a-z][a-z0-9_.-]{1,63}$");
	private static final String FILE_SUFFIX = "-orespawn.json";
	private static final Set<String> MERGED_SECTIONS = new LinkedHashSet<>();

	static {
		Collections.addAll(MERGED_SECTIONS, "rocks", "ores", "geomes", "biome_rules",
				"terrain_dimensions", "fluid_deposits", "biome_palettes",
				"dimension_materials");
	}

	private static final Map<String, WorldgenProvider> API_PROVIDERS = new LinkedHashMap<>();
	private static final Map<String, JsonObject> RESOURCE_PROVIDERS = new LinkedHashMap<>();
	private static final Map<String, JsonObject> FILE_PROVIDERS = new LinkedHashMap<>();
	private static final Map<String, ProviderDefinition> ACTIVE_PROVIDERS = new LinkedHashMap<>();
	private static final Map<ResourceLocation, TemplateDefinition> TEMPLATES = new LinkedHashMap<>();
	private static final Set<String> INVALID_PROVIDERS = new HashSet<>();
	private static final Set<String> FILE_PROVIDER_IDS = new HashSet<>();
	private static boolean initialized;
	private static boolean frozen;
	private static boolean featureReady;

	private WorldgenIntegrationManager() {
	}

	public static synchronized void initialize() {
		initialize(FMLPaths.CONFIGDIR.get());
	}

	static synchronized void initialize(Path configDirectory) {
		RESOURCE_PROVIDERS.clear();
		FILE_PROVIDERS.clear();
		FILE_PROVIDER_IDS.clear();
		INVALID_PROVIDERS.clear();
		initialized = true;
		frozen = false;
		scanPackagedProviders();
		if (!Files.isDirectory(configDirectory)) {
			rebuildActiveProviders();
			return;
		}

		List<Path> files = new ArrayList<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDirectory, "*" + FILE_SUFFIX)) {
			for (Path path : stream) {
				files.add(path);
			}
		} catch (IOException e) {
			LOGGER.warn("Could not scan OreSpawn provider files in '{}'", configDirectory, e);
			rebuildActiveProviders();
			return;
		}
		files.sort(Comparator.comparing(path -> path.getFileName().toString()));
		for (Path path : files) {
			loadProviderFile(path);
		}
		rebuildActiveProviders();
	}

	public static synchronized void processImcMessages() {
		InterModComms.getMessages(OreSpawn.MODID,
				OreSpawnApi.IMC_WORLDGEN_PROVIDER::equals).forEach(message -> {
			try {
				Object value = message.getMessageSupplier().get();
				if (!(value instanceof WorldgenProvider)) {
					throw new IllegalArgumentException("message is not a WorldgenProvider");
				}
				WorldgenProvider provider = (WorldgenProvider) value;
				if (!provider.modId().equals(message.getSenderModId())) {
					throw new IllegalArgumentException("sender does not own provider ID " + provider.modId());
				}
				if (API_PROVIDERS.putIfAbsent(provider.modId(), provider) != null) {
					throw new IllegalArgumentException("provider was submitted more than once");
				}
			} catch (RuntimeException e) {
				INVALID_PROVIDERS.add(message.getSenderModId());
				LOGGER.error("Rejected OreSpawn API provider from '{}'", message.getSenderModId(), e);
			}
		});
		rebuildActiveProviders();
	}

	public static synchronized void freeze() {
		rebuildActiveProviders();
		frozen = true;
		for (ProviderDefinition provider : ACTIVE_PROVIDERS.values()) {
			LOGGER.info("OreSpawn worldgen provider '{}' revision {} is active with {} rocks, {} ores, {} fluid deposits, {} biome palettes, and {} templates",
					provider.modId, provider.revision, provider.section("rocks").size(),
					provider.section("ores").size(), provider.section("fluid_deposits").size(),
					provider.section("biome_palettes").size(),
					provider.section("templates").size());
		}
	}

	public static synchronized void markFeatureReady() {
		featureReady = true;
	}

	public static synchronized ProviderStatus getProviderStatus(String providerModId) {
		if (!initialized || (!frozen && (ACTIVE_PROVIDERS.containsKey(providerModId)
				|| API_PROVIDERS.containsKey(providerModId) || FILE_PROVIDER_IDS.contains(providerModId)))) {
			return ProviderStatus.PENDING;
		}
		return featureReady && frozen && ACTIVE_PROVIDERS.containsKey(providerModId)
				&& !INVALID_PROVIDERS.contains(providerModId) ? ProviderStatus.ACTIVE : ProviderStatus.INACTIVE;
	}

	public static synchronized boolean isOreTakeoverActive(String providerModId) {
		ProviderDefinition provider = ACTIVE_PROVIDERS.get(providerModId);
		return getProviderStatus(providerModId) == ProviderStatus.ACTIVE
				&& provider != null && !provider.section("ores").entrySet().isEmpty();
	}

	public static synchronized Set<String> activeProviderIds() {
		return Collections.unmodifiableSet(new LinkedHashSet<>(ACTIVE_PROVIDERS.keySet()));
	}

	/** Merge new provider-owned defaults without overwriting pack or world values. */
	public static synchronized boolean mergeProviderDefinitions(JsonObject target) {
		JsonObject manifests = object(target, "providers");
		JsonObject legacyOreManifests = object(target, "ore_providers");
		boolean changed = false;

		for (ProviderDefinition provider : ACTIVE_PROVIDERS.values()) {
			JsonObject manifest = object(manifests, provider.modId);
			if (!bool(manifest, "profile_defaults_applied", false)
					&& provider.root.has("profile_defaults")
					&& provider.root.get("profile_defaults").isJsonObject()) {
				mergeOverlay(target, provider.root.getAsJsonObject("profile_defaults"));
				manifest.addProperty("profile_defaults_applied", true);
				changed = true;
			}
			if (integer(manifest, "provider_revision", -1) != provider.revision) {
				changed = true;
			}
			for (String sectionName : MERGED_SECTIONS) {
				String targetName = "biome_rules".equals(sectionName) ? "biomes" : sectionName;
				JsonObject targetSection = object(target, targetName);
				JsonObject providerSection = provider.section(sectionName);
				String knownKey = "known_" + sectionName;
				Set<String> known = stringSet(manifest.get(knownKey));
				Set<String> current = JsonCopies.keys(providerSection);

				for (Entry<String, JsonElement> entry : providerSection.entrySet()) {
					String id = entry.getKey();
					if (!targetSection.has(id) && !known.contains(id)) {
						JsonObject value = JsonCopies.copy(entry.getValue().getAsJsonObject());
						if (!"biome_rules".equals(sectionName)) {
							value.addProperty("source_provider", provider.modId);
						}
						targetSection.add(id, value);
						changed = true;
					}
					known.add(id);
					if (targetSection.has(id) && targetSection.get(id).isJsonObject()) {
						targetSection.getAsJsonObject(id).remove("orphaned_provider");
					}
				}

				for (String knownId : known) {
					if (!current.contains(knownId) && targetSection.has(knownId)
							&& targetSection.get(knownId).isJsonObject()) {
						targetSection.getAsJsonObject(knownId).addProperty("orphaned_provider", true);
						changed = true;
					}
				}
				manifest.add(knownKey, sortedArray(known));
				target.add(targetName, targetSection);
			}

			manifest.addProperty("provider_revision", provider.revision);
			manifest.add("known_templates", sortedArray(JsonCopies.keys(provider.section("templates"))));
			manifests.add(provider.modId, manifest);
			JsonObject legacy = object(legacyOreManifests, provider.modId);
			legacy.addProperty("provider_revision", provider.revision);
			legacy.add("known_ores", JsonCopies.copy(manifest.get("known_ores")));
			legacyOreManifests.add(provider.modId, legacy);
		}

		for (Entry<String, JsonElement> manifestEntry : manifests.entrySet()) {
			if (ACTIVE_PROVIDERS.containsKey(manifestEntry.getKey()) || !manifestEntry.getValue().isJsonObject()) {
				continue;
			}
			JsonObject manifest = manifestEntry.getValue().getAsJsonObject();
			for (String sectionName : MERGED_SECTIONS) {
				String targetName = "biome_rules".equals(sectionName) ? "biomes" : sectionName;
				JsonObject targetSection = object(target, targetName);
				for (String knownId : stringSet(manifest.get("known_" + sectionName))) {
					if (!"biome_rules".equals(sectionName)
							&& targetSection.has(knownId) && targetSection.get(knownId).isJsonObject()
							&& !bool(targetSection.getAsJsonObject(knownId), "orphaned_provider", false)) {
						targetSection.getAsJsonObject(knownId).addProperty("orphaned_provider", true);
						changed = true;
					}
				}
				target.add(targetName, targetSection);
			}
		}

		target.add("providers", manifests);
		target.add("ore_providers", legacyOreManifests);
		return changed;
	}

	public static synchronized List<TemplateDefinition> templates() {
		return Collections.unmodifiableList(new ArrayList<>(TEMPLATES.values()));
	}

	public static synchronized ResourceLocation autoSelectedTemplate() {
		TemplateDefinition selected = null;
		for (TemplateDefinition candidate : TEMPLATES.values()) {
			if (!candidate.available || !candidate.autoSelect) continue;
			if (selected == null || candidate.autoSelectPriority > selected.autoSelectPriority
					|| (candidate.autoSelectPriority == selected.autoSelectPriority
							&& candidate.id.toString().compareTo(selected.id.toString()) < 0)) {
				if (selected != null && candidate.autoSelectPriority == selected.autoSelectPriority) {
					LOGGER.warn("OreSpawn auto-select templates '{}' and '{}' share priority {}; using lexical order",
							selected.id, candidate.id, candidate.autoSelectPriority);
				}
				selected = candidate;
			}
		}
		return selected == null ? null : selected.id;
	}

	public static synchronized JsonObject applyTemplate(JsonObject base, ResourceLocation templateId) {
		TemplateDefinition template = TEMPLATES.get(templateId);
		if (template == null || !template.available) {
			throw new IllegalArgumentException("Unknown or unavailable OreSpawn template: " + templateId);
		}
		JsonObject result = JsonCopies.copy(base);
		mergeOverlay(result, template.profile);
		applyLegacyTemplateOil(result, template.profile);
		result.addProperty("selected_template", templateId.toString());
		return result;
	}

	/** Internal migration lookup. Null means the owner is not an active installed provider. */
	public static synchronized List<String> findProviderOreRulesByOutput(String providerModId, String outputBlock) {
		ProviderDefinition provider = ACTIVE_PROVIDERS.get(providerModId);
		if (provider == null) return null;
		List<String> matches = new ArrayList<>();
		for (Entry<String, JsonElement> entry : provider.section("ores").entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject ore = entry.getValue().getAsJsonObject();
			boolean matchesOutput = outputBlock.equals(string(ore, "block", ""));
			if (!matchesOutput && ore.has("outputs") && ore.get("outputs").isJsonArray()) {
				for (JsonElement candidate : ore.getAsJsonArray("outputs")) {
					if (candidate.isJsonObject()
							&& outputBlock.equals(string(candidate.getAsJsonObject(), "block", ""))) {
						matchesOutput = true;
						break;
					}
				}
			}
			if (matchesOutput) matches.add(entry.getKey());
		}
		matches.sort(String::compareTo);
		return Collections.unmodifiableList(matches);
	}

	static void applyLegacyTemplateOil(JsonObject result, JsonObject templateProfile) {
		if (!templateProfile.has("oil") || !templateProfile.get("oil").isJsonObject()) {
			return;
		}
		JsonObject deposits = object(result, "fluid_deposits");
		if (deposits.size() != 1) {
			return;
		}
		JsonElement depositElement = deposits.entrySet().iterator().next().getValue();
		if (!depositElement.isJsonObject()) {
			return;
		}
		JsonObject dimensions = object(depositElement.getAsJsonObject(), "dimensions");
		JsonObject dimension = null;
		if (dimensions.has("minecraft:overworld")
				&& dimensions.get("minecraft:overworld").isJsonObject()) {
			dimension = dimensions.getAsJsonObject("minecraft:overworld");
		} else if (dimensions.size() == 1
				&& dimensions.entrySet().iterator().next().getValue().isJsonObject()) {
			dimension = dimensions.entrySet().iterator().next().getValue().getAsJsonObject();
		}
		if (dimension == null) {
			return;
		}
		JsonObject legacy = templateProfile.getAsJsonObject("oil");
		for (String key : new String[] { "min_y", "max_y", "frequency", "min_radius", "max_radius",
				"min_vertical_radius", "max_vertical_radius", "max_lobes", "min_solid_cover" }) {
			if (legacy.has(key)) {
				dimension.add(key, JsonCopies.copy(legacy.get(key)));
			}
		}
		result.addProperty("place_fluid_deposits", true);
		result.remove("place_crude_oil");
		result.remove("oil");
	}

	private static void loadProviderFile(Path path) {
		String fileName = path.getFileName().toString();
		String providerId = fileName.substring(0, fileName.length() - FILE_SUFFIX.length());
		FILE_PROVIDER_IDS.add(providerId);
		if (!MOD_ID.matcher(providerId).matches() || !ModList.get().isLoaded(providerId)) {
			return;
		}
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement element = new JsonParser().parse(reader);
			if (!element.isJsonObject()) {
				throw new JsonSyntaxException("root is not an object");
			}
			JsonObject root = element.getAsJsonObject();
			validateProvider(providerId, root);
			FILE_PROVIDERS.put(providerId, JsonCopies.copy(root));
		} catch (IOException | RuntimeException e) {
			INVALID_PROVIDERS.add(providerId);
			LOGGER.error("Rejected OreSpawn provider file '{}'; {} native generation must remain enabled",
					path, providerId, e);
		}
	}

	private static void scanPackagedProviders() {
		ModList mods = ModList.get();
		if (mods == null) {
			return;
		}
		mods.forEachModFile(WorldgenIntegrationManager::scanPackagedProvider);
	}

	private static void scanPackagedProvider(IModFile file) {
		for (IModInfo info : file.getModInfos()) {
			String providerId = info.getModId();
			Path path = file.findResource("data/" + providerId + "/orespawn/provider.json");
			if (!Files.isRegularFile(path)) {
				continue;
			}
			FILE_PROVIDER_IDS.add(providerId);
			try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonElement element = new JsonParser().parse(reader);
				if (!element.isJsonObject()) {
					throw new JsonSyntaxException("root is not an object");
				}
				JsonObject root = element.getAsJsonObject();
				validateProvider(providerId, root);
				RESOURCE_PROVIDERS.put(providerId, JsonCopies.copy(root));
			} catch (IOException | RuntimeException e) {
				INVALID_PROVIDERS.add(providerId);
				LOGGER.error("Rejected packaged OreSpawn provider '{}' from '{}'", providerId, path, e);
			}
		}
	}

	private static void rebuildActiveProviders() {
		ACTIVE_PROVIDERS.clear();
		TEMPLATES.clear();
		Map<String, String> owners = new HashMap<>();
		Set<String> providerIds = new LinkedHashSet<>();
		providerIds.addAll(API_PROVIDERS.keySet());
		providerIds.addAll(RESOURCE_PROVIDERS.keySet());
		providerIds.addAll(FILE_PROVIDER_IDS);
		List<String> sorted = new ArrayList<>(providerIds);
		Collections.sort(sorted);

		for (String providerId : sorted) {
			if (INVALID_PROVIDERS.contains(providerId)) {
				continue;
			}
			JsonObject root = FILE_PROVIDERS.get(providerId);
			if (root == null) {
				root = RESOURCE_PROVIDERS.get(providerId);
			}
			if (root == null) {
				WorldgenProvider apiProvider = API_PROVIDERS.get(providerId);
				root = apiProvider == null ? null : apiProvider.toJson();
			}
			if (root == null || !ModList.get().isLoaded(providerId)) {
				continue;
			}
			try {
				validateProvider(providerId, root);
				claimOwnedEntries(providerId, root, owners);
				ProviderDefinition provider = new ProviderDefinition(providerId,
						integer(root, "provider_revision", -1), JsonCopies.copy(root));
				ACTIVE_PROVIDERS.put(providerId, provider);
				readTemplates(provider);
			} catch (RuntimeException e) {
				INVALID_PROVIDERS.add(providerId);
				LOGGER.error("Rejected OreSpawn worldgen provider '{}'", providerId, e);
			}
		}
	}

	static void validateProvider(String providerId, JsonObject root) {
		int schema = integer(root, "schema_version", -1);
		if (schema != 1 && schema != 2 && schema != 3 && schema != 4) {
			throw new JsonSyntaxException("unsupported schema_version");
		}
		if (!providerId.equals(string(root, "provider_modid", ""))) {
			throw new JsonSyntaxException("provider_modid does not match the provider source");
		}
		if (integer(root, "provider_revision", -1) < 1) {
			throw new JsonSyntaxException("provider_revision must be at least 1");
		}
		if (schema == 1) {
			for (String section : new String[] { "rocks", "geomes", "biome_rules",
					"terrain_dimensions", "fluid_deposits", "biome_palettes",
					"dimension_materials", "templates", "profile_defaults" }) {
				if (root.has(section)) {
					throw new JsonSyntaxException("provider schema 1 is ore-only; found " + section);
				}
			}
			if (optionalObject(root, "ores").size() == 0) {
				throw new JsonSyntaxException("provider schema 1 must declare ores");
			}
		}
		if (schema < 3 && root.has("fluid_deposits")) {
			throw new JsonSyntaxException("fluid_deposits requires provider schema 3");
		}
		if (schema < 4 && (root.has("biome_palettes") || root.has("dimension_materials"))) {
			throw new JsonSyntaxException("biome palettes and dimension materials require provider schema 4");
		}

		int entries = 0;
		for (String section : MERGED_SECTIONS) {
			JsonObject values = optionalObject(root, section);
			entries += values.size();
			for (Entry<String, JsonElement> entry : values.entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					throw new JsonSyntaxException(section + " entry is not an object: " + entry.getKey());
				}
				if (!"biome_rules".equals(section)) {
					validateOwnedId(providerId, entry.getKey(), section);
				} else {
					new ResourceLocation(entry.getKey());
				}
				if ("rocks".equals(section)) {
					validateRock(entry.getKey(), entry.getValue().getAsJsonObject());
				} else if ("ores".equals(section)) {
					validateOre(entry.getKey(), entry.getValue().getAsJsonObject());
				} else if ("geomes".equals(section)) {
					validateGeome(entry.getKey(), entry.getValue().getAsJsonObject());
				} else if ("biome_rules".equals(section)) {
					validateWeights(entry.getValue().getAsJsonObject());
				} else if ("terrain_dimensions".equals(section)) {
					validateTerrainDimension(entry.getKey(), entry.getValue().getAsJsonObject());
				} else if ("fluid_deposits".equals(section)) {
					validateFluidDeposit(entry.getKey(), entry.getValue().getAsJsonObject());
				} else if ("biome_palettes".equals(section)) {
					validateBiomePalette(entry.getKey(), entry.getValue().getAsJsonObject());
				} else if ("dimension_materials".equals(section)) {
					validateDimensionMaterials(entry.getKey(), entry.getValue().getAsJsonObject());
				}
			}
		}
		JsonObject templates = optionalObject(root, "templates");
		entries += templates.size();
		for (Entry<String, JsonElement> entry : templates.entrySet()) {
			validateOwnedId(providerId, entry.getKey(), "templates");
			validateTemplate(entry.getKey(), entry.getValue());
		}
		if (root.has("profile_defaults") && !root.get("profile_defaults").isJsonObject()) {
			throw new JsonSyntaxException("profile_defaults is not an object");
		}
		if (entries == 0) {
			throw new JsonSyntaxException("provider declares no world-generation entries");
		}
	}

	private static void validateOwnedId(String providerId, String idText, String section) {
		ResourceLocation id = new ResourceLocation(idText);
		if (!providerId.equals(id.getNamespace())) {
			throw new JsonSyntaxException(section + " entry is outside provider namespace: " + id);
		}
	}

	private static void validateRock(String idText, JsonObject rock) {
		String blockId = string(rock, "block", idText);
		Block block = block(blockId);
		if (block == null || block == Blocks.AIR) {
			throw new JsonSyntaxException("unknown rock block: " + blockId);
		}
		RockFamily.fromConfigName(string(rock, "family", ""));
		int minY = integer(rock, "min_y", 0);
		int maxY = integer(rock, "max_y", 255);
		if (minY < -2048 || maxY > 2048 || minY > maxY
				|| decimal(rock, "weight", 1.0D) < 0.0D) {
			throw new JsonSyntaxException("invalid rock range or weight: " + idText);
		}
		validateIds(rock.get("dimensions"));
		validateWeights(optionalObject(rock, "geomes"));
	}

	private static void validateOre(String idText, JsonObject ore) {
		String blockId = string(ore, "block", idText);
		Block output = block(blockId);
		if (output == null || output == Blocks.AIR) {
			throw new JsonSyntaxException("unknown ore block: " + blockId);
		}
		if (ore.has("outputs")) validateOutputs(idText, ore.get("outputs"));
		JsonObject dimensions = optionalObject(ore, "dimensions");
		JsonObject selectors = optionalObject(ore, "dimension_selectors");
		if (dimensions.size() == 0 && selectors.size() == 0) {
			throw new JsonSyntaxException("ore has no dimensions or dimension selectors: " + idText);
		}
		for (Entry<String, JsonElement> entry : dimensions.entrySet()) {
			new ResourceLocation(entry.getKey());
			validateOreRule(idText, entry);
		}
		for (Entry<String, JsonElement> entry : selectors.entrySet()) {
			OreDimensionSelector.fromId(new ResourceLocation(entry.getKey()));
			validateOreRule(idText, entry);
		}
	}

	private static void validateOreRule(String idText, Entry<String, JsonElement> entry) {
			if (!entry.getValue().isJsonObject()) {
				throw new JsonSyntaxException("ore dimension is not an object: " + entry.getKey());
			}
			JsonObject rule = entry.getValue().getAsJsonObject();
			if (!bool(rule, "enabled", true)) return;
			int minY = integer(rule, "min_y", Integer.MIN_VALUE);
			int maxY = integer(rule, "max_y", Integer.MIN_VALUE);
			double frequency = decimal(rule, "frequency", -1.0D);
			double discardChance = decimal(rule, "discard_chance_on_air_exposure", 0.0D);
			int[] quantities = validateQuantityRange(rule);
			int minQuantity = quantities[0];
			int maxQuantity = quantities[1];
			if (minY < -2048 || maxY > 2048 || minY > maxY || frequency < 0.0D
					|| frequency > 64.0D || !Double.isFinite(discardChance)
					|| discardChance < 0.0D || discardChance > 1.0D
					|| minQuantity < 1 || minQuantity > maxQuantity || maxQuantity > 64) {
				throw new JsonSyntaxException("invalid ore placement for " + idText + " in " + entry.getKey());
			}
			try {
				OreSpawnPatterns.decode(rule);
			} catch (IllegalArgumentException e) {
				throw new JsonSyntaxException("invalid ore pattern for " + idText + ": " + e.getMessage());
			}
			OreHeightDistribution.fromConfigName(string(rule, "height_distribution",
					OreHeightDistribution.UNIFORM.configName));
			int spread = integer(rule, "spread", 8);
			int vertical = integer(rule, "vertical_spread", Math.max(1, spread / 2));
			int node = integer(rule, "node_size", 4);
			if (spread < 0 || spread > 64 || vertical < 0 || vertical > 64 || node < 1 || node > 32) {
				throw new JsonSyntaxException("invalid ore pattern dimensions for " + idText);
			}
			boolean hosts = validBlocks(rule.get("host_blocks")) || validateIds(rule.get("host_tags"));
			if (rule.has("host_families") && rule.get("host_families").isJsonArray()) {
				for (JsonElement family : rule.getAsJsonArray("host_families")) {
					RockFamily.fromConfigName(family.getAsString());
					hosts = true;
				}
			}
			if (!hosts) {
				throw new JsonSyntaxException("enabled ore dimension has no valid hosts: " + idText);
			}
	}

	static int[] validateQuantityRange(JsonObject rule) {
		boolean hasMinQuantity = rule.has("min_quantity");
		boolean hasMaxQuantity = rule.has("max_quantity");
		if (hasMinQuantity != hasMaxQuantity) {
			throw new JsonSyntaxException("ore quantity range needs both bounds");
		}
		int minQuantity = hasMinQuantity ? integer(rule, "min_quantity", -1)
				: integer(rule, "quantity", -1);
		int maxQuantity = hasMaxQuantity ? integer(rule, "max_quantity", -1) : minQuantity;
		if (minQuantity < 1 || minQuantity > maxQuantity || maxQuantity > 64) {
			throw new JsonSyntaxException("ore quantity must be within 1..64");
		}
		return new int[] { minQuantity, maxQuantity };
	}

	private static void validateOutputs(String idText, JsonElement element) {
		if (!element.isJsonArray() || element.getAsJsonArray().size() == 0) {
			throw new JsonSyntaxException("outputs is empty for " + idText);
		}
		for (JsonElement value : element.getAsJsonArray()) {
			if (!value.isJsonObject()) throw new JsonSyntaxException("weighted output is not an object: " + idText);
			JsonObject output = value.getAsJsonObject();
			String blockId = string(output, "block", "");
			Block block = block(blockId);
			if (block == null || block == Blocks.AIR || decimal(output, "weight", 1.0D) <= 0.0D
					|| integer(output, "min_y", -2048) > integer(output, "max_y", 2048)) {
				throw new JsonSyntaxException("invalid weighted output for " + idText + ": " + blockId);
			}
		}
	}

	private static void validateGeome(String id, JsonObject geome) {
		if (decimal(geome, "base", 1.0D) < 0.0D) {
			throw new JsonSyntaxException("invalid geome base weight: " + id);
		}
		JsonObject families = optionalObject(geome, "families");
		for (RockFamily family : RockFamily.values()) {
			if (decimal(families, family.configName, 1.0D) < 0.0D) {
				throw new JsonSyntaxException("invalid family weight in geome: " + id);
			}
		}
	}

	private static void validateTerrainDimension(String id, JsonObject dimension) {
		if (!bool(dimension, "enabled", true)) {
			return;
		}
		boolean hosts = validBlocks(dimension.get("host_blocks")) || validateIds(dimension.get("host_tags"));
		validateIds(dimension.get("biome_ids"));
		if (dimension.has("biome_namespaces")) {
			if (!dimension.get("biome_namespaces").isJsonArray()) {
				throw new JsonSyntaxException("biome_namespaces is not an array: " + id);
			}
			for (JsonElement namespace : dimension.getAsJsonArray("biome_namespaces")) {
				if (!MOD_ID.matcher(namespace.getAsString()).matches()) {
					throw new JsonSyntaxException("invalid biome namespace in terrain dimension: " + id);
				}
			}
		}
		if (!hosts) {
			throw new JsonSyntaxException("enabled terrain dimension has no valid hosts: " + id);
		}
	}

	private static void validateFluidDeposit(String id, JsonObject deposit) {
		String blockId = string(deposit, "block", "");
		Block output = block(blockId);
		if (output == null || output == Blocks.AIR || output.defaultBlockState().getFluidState().isEmpty()) {
			throw new JsonSyntaxException("fluid deposit output is not a fluid block: " + blockId);
		}
		JsonObject dimensions = requiredObject(deposit, "dimensions");
		if (dimensions.size() == 0) {
			throw new JsonSyntaxException("fluid deposit has no dimensions: " + id);
		}
		for (Entry<String, JsonElement> entry : dimensions.entrySet()) {
			new ResourceLocation(entry.getKey());
			if (!entry.getValue().isJsonObject()) {
				throw new JsonSyntaxException("fluid deposit dimension is not an object: " + entry.getKey());
			}
			JsonObject rule = entry.getValue().getAsJsonObject();
			if (!bool(rule, "enabled", true)) continue;
			int minY = integer(rule, "min_y", Integer.MIN_VALUE);
			int maxY = integer(rule, "max_y", Integer.MIN_VALUE);
			double frequency = decimal(rule, "frequency", -1.0D);
			int minRadius = integer(rule, "min_radius", -1);
			int maxRadius = integer(rule, "max_radius", -1);
			int minVertical = integer(rule, "min_vertical_radius", -1);
			int maxVertical = integer(rule, "max_vertical_radius", -1);
			int maxLobes = integer(rule, "max_lobes", -1);
			int cover = integer(rule, "min_solid_cover", -1);
			int shell = integer(rule, "min_solid_shell", 1);
			if (minY < -2048 || maxY > 2048 || minY > maxY
					|| !Double.isFinite(frequency) || frequency < 0.0D || frequency > 64.0D
					|| minRadius < 1 || minRadius > maxRadius || maxRadius > 64
					|| minVertical < 1 || minVertical > maxVertical || maxVertical > 64
					|| maxLobes < 1 || maxLobes > 16 || cover < 0 || cover > 64
					|| shell < 0 || shell > 64) {
				throw new JsonSyntaxException("invalid fluid deposit placement for " + id
						+ " in " + entry.getKey());
			}
			boolean hosts = validBlocks(rule.get("host_blocks")) || validateIds(rule.get("host_tags"));
			if (rule.has("host_families") && rule.get("host_families").isJsonArray()) {
				for (JsonElement family : rule.getAsJsonArray("host_families")) {
					RockFamily.fromConfigName(family.getAsString());
					hosts = true;
				}
			}
			if (!hosts) {
				throw new JsonSyntaxException("enabled fluid deposit dimension has no valid hosts: " + id);
			}
			validateIds(rule.get("biome_ids"));
			validateIds(rule.get("excluded_biome_ids"));
			validateStringList(rule.get("biome_dictionary"));
			validateStringList(rule.get("excluded_biome_dictionary"));
			validateWeights(optionalObject(rule, "geomes"));
		}
	}

	private static void validateBiomePalette(String id, JsonObject palette) {
		new ResourceLocation(string(palette, "dimension", ""));
		if (!bool(palette, "enabled", true)) return;
		String mode = string(palette, "mode", "augment");
		if (!"augment".equals(mode) && !"replace".equals(mode)) {
			throw new JsonSyntaxException("invalid biome placement mode: " + id);
		}
		String scope = string(palette, "scope", "minecraft_only");
		if (!"all".equals(scope) && !"minecraft_only".equals(scope)
				&& !"selected_namespaces".equals(scope)) {
			throw new JsonSyntaxException("invalid biome replacement scope: " + id);
		}
		String region = string(palette, "region_size", "average");
		if (!java.util.Arrays.asList("tiny", "small", "average", "large", "huge").contains(region)) {
			throw new JsonSyntaxException("invalid biome region size: " + id);
		}
		double coverage = decimal(palette, "coverage", 1.0D);
		double fallback = decimal(palette, "fallback_weight", 1.0D);
		if (!Double.isFinite(coverage) || coverage < 0.0D || coverage > 1.0D
				|| !Double.isFinite(fallback) || fallback < 0.0D) {
			throw new JsonSyntaxException("invalid biome palette coverage or fallback weight: " + id);
		}
		validateNamespaces(palette.get("include_namespaces"), id);
		validateNamespaces(palette.get("exclude_namespaces"), id);
		if ("selected_namespaces".equals(scope)
				&& (!palette.has("include_namespaces")
						|| palette.getAsJsonArray("include_namespaces").size() == 0)) {
			throw new JsonSyntaxException("selected-namespace biome palette has no namespaces: " + id);
		}
		JsonObject biomes = requiredObject(palette, "biomes");
		if (biomes.size() == 0) {
			throw new JsonSyntaxException("enabled biome palette has no biomes: " + id);
		}
		for (Entry<String, JsonElement> entry : biomes.entrySet()) {
			ResourceLocation biomeId = new ResourceLocation(entry.getKey());
			if (!entry.getValue().isJsonObject()) {
				throw new JsonSyntaxException("biome placement is not an object: " + biomeId);
			}
			JsonObject placement = entry.getValue().getAsJsonObject();
			double weight = decimal(placement, "weight", 1.0D);
			double minTemperature = decimal(placement, "min_temperature", -2.0D);
			double maxTemperature = decimal(placement, "max_temperature", 2.0D);
			double minDownfall = decimal(placement, "min_downfall", 0.0D);
			double maxDownfall = decimal(placement, "max_downfall", 1.0D);
			if (!Double.isFinite(weight) || weight < 0.0D
					|| minTemperature > maxTemperature || minDownfall > maxDownfall
					|| minDownfall < 0.0D || maxDownfall > 1.0D) {
				throw new JsonSyntaxException("invalid biome placement weights or climate: " + biomeId);
			}
			validateIds(placement.get("similar_biomes"));
			validateIds(placement.get("required_similar_biomes"));
			if (placement.has("surface")) {
				validateBiomeSurface(biomeId.toString(), requiredObject(placement, "surface"));
			}
		}
	}

	private static void validateBiomeSurface(String id, JsonObject surface) {
		for (String key : new String[] { "top_block", "filler_block", "underwater_block",
				"ceiling_block" }) {
			if (!surface.has(key)) continue;
			Block value = block(surface.get(key).getAsString());
			if (value == null || value == Blocks.AIR) {
				throw new JsonSyntaxException("unknown biome surface block for " + id + ": "
						+ surface.get(key).getAsString());
			}
		}
		int fillerDepth = integer(surface, "filler_depth", 3);
		if (fillerDepth < 0 || fillerDepth > 16) {
			throw new JsonSyntaxException("invalid biome filler depth: " + id);
		}
	}

	private static void validateDimensionMaterials(String id, JsonObject materials) {
		new ResourceLocation(string(materials, "dimension", ""));
		if (!bool(materials, "enabled", true)) return;
		boolean any = false;
		for (String key : new String[] { "default_fluid", "deep_aquifer_fluid" }) {
			if (!materials.has(key)) continue;
			Block value = block(materials.get(key).getAsString());
			if (value == null || value == Blocks.AIR || value.defaultBlockState().getFluidState().isEmpty()) {
				throw new JsonSyntaxException("dimension material is not a fluid block for " + id + ": "
						+ materials.get(key).getAsString());
			}
			any = true;
		}
		for (String key : new String[] { "snow_block", "ice_block" }) {
			if (!materials.has(key)) continue;
			Block value = block(materials.get(key).getAsString());
			if (value == null || value == Blocks.AIR) {
				throw new JsonSyntaxException("unknown dimension material block for " + id + ": "
						+ materials.get(key).getAsString());
			}
			any = true;
		}
		if (!any) throw new JsonSyntaxException("enabled dimension materials are empty: " + id);
	}

	private static void validateNamespaces(JsonElement element, String id) {
		if (element == null) return;
		if (!element.isJsonArray()) {
			throw new JsonSyntaxException("biome namespace list is not an array: " + id);
		}
		for (JsonElement namespace : element.getAsJsonArray()) {
			if (!MOD_ID.matcher(namespace.getAsString()).matches()) {
				throw new JsonSyntaxException("invalid biome namespace in palette: " + id);
			}
		}
	}

	private static void validateStringList(JsonElement element) {
		if (element == null) return;
		if (!element.isJsonArray()) {
			throw new JsonSyntaxException("value is not an array");
		}
		for (JsonElement value : element.getAsJsonArray()) {
			if (value.getAsString().trim().isEmpty()) {
				throw new JsonSyntaxException("list contains an empty value");
			}
		}
	}

	private static void validateTemplate(String id, JsonElement element) {
		if (!element.isJsonObject()) {
			throw new JsonSyntaxException("template is not an object: " + id);
		}
		JsonObject template = element.getAsJsonObject();
		requiredObject(template, "profile");
		if (template.has("required_mods") && template.get("required_mods").isJsonArray()) {
			for (JsonElement mod : template.getAsJsonArray("required_mods")) {
				if (!MOD_ID.matcher(mod.getAsString()).matches()) {
					throw new JsonSyntaxException("invalid required mod ID in template: " + id);
				}
			}
		}
	}

	private static void validateWeights(JsonObject weights) {
		for (Entry<String, JsonElement> entry : weights.entrySet()) {
			normalizeGeomeId(entry.getKey());
			if (entry.getValue().getAsDouble() < 0.0D) {
				throw new JsonSyntaxException("negative geome weight: " + entry.getKey());
			}
		}
	}

	static void claimOwnedEntries(String provider, JsonObject root, Map<String, String> owners) {
		for (String section : new String[] { "rocks", "ores", "fluid_deposits",
				"biome_palettes", "dimension_materials" }) {
			for (String id : JsonCopies.keys(optionalObject(root, section))) {
				String key = section + ':' + id;
				String previous = owners.putIfAbsent(key, provider);
				if (previous != null) {
					throw new JsonSyntaxException(id + " is already owned by " + previous);
				}
			}
		}
	}

	private static void readTemplates(ProviderDefinition provider) {
		for (Entry<String, JsonElement> entry : provider.section("templates").entrySet()) {
			ResourceLocation id = new ResourceLocation(entry.getKey());
			JsonObject json = entry.getValue().getAsJsonObject();
			boolean available = true;
			if (json.has("required_mods") && json.get("required_mods").isJsonArray()) {
				for (JsonElement mod : json.getAsJsonArray("required_mods")) {
					available &= ModList.get().isLoaded(mod.getAsString());
				}
			}
			TEMPLATES.put(id, new TemplateDefinition(id,
					string(json, "name_key", "orespawn.template." + id.getNamespace() + '.' + id.getPath()),
					string(json, "description_key", "orespawn.template." + id.getNamespace() + '.'
							+ id.getPath() + ".description"),
					JsonCopies.copy(json.getAsJsonObject("profile")), available,
					bool(json, "auto_select", false),
					integer(json, "auto_select_priority", 0)));
		}
	}

	private static void mergeOverlay(JsonObject target, JsonObject overlay) {
		for (Entry<String, JsonElement> entry : overlay.entrySet()) {
			if (entry.getValue().isJsonObject() && target.has(entry.getKey())
					&& target.get(entry.getKey()).isJsonObject()) {
				mergeOverlay(target.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject());
			} else {
				target.add(entry.getKey(), JsonCopies.copy(entry.getValue()));
			}
		}
	}

	private static ResourceLocation normalizeGeomeId(String value) {
		return value.indexOf(':') >= 0 ? new ResourceLocation(value) : new ResourceLocation(OreSpawn.MODID, value);
	}

	private static Block block(String idText) {
		try {
			return ForgeRegistries.BLOCKS.getValue(new ResourceLocation(idText));
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static boolean validBlocks(JsonElement element) {
		if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() == 0) {
			return false;
		}
		for (JsonElement value : element.getAsJsonArray()) {
			String id = value.isJsonObject() ? string(value.getAsJsonObject(), "block", "")
					: value.getAsString();
			Block block = block(id);
			if (block == null || block == Blocks.AIR) {
				throw new JsonSyntaxException("unknown host block: " + id);
			}
			if (value.isJsonObject() && (decimal(value.getAsJsonObject(), "weight", 1.0D) < 0.0D
					|| decimal(value.getAsJsonObject(), "weight", 1.0D) > 1.0D))
				throw new JsonSyntaxException("invalid host block weight: " + id);
		}
		return true;
	}

	private static boolean validateIds(JsonElement element) {
		if (element == null) {
			return false;
		}
		if (!element.isJsonArray()) {
			throw new JsonSyntaxException("registry ID list is not an array");
		}
		for (JsonElement value : element.getAsJsonArray()) {
			String id = value.isJsonObject() ? string(value.getAsJsonObject(), "tag", "")
					: value.getAsString();
			new ResourceLocation(id);
			if (value.isJsonObject() && (decimal(value.getAsJsonObject(), "weight", 1.0D) < 0.0D
					|| decimal(value.getAsJsonObject(), "weight", 1.0D) > 1.0D))
				throw new JsonSyntaxException("invalid tag weight: " + id);
		}
		return element.getAsJsonArray().size() > 0;
	}

	private static JsonObject requiredObject(JsonObject root, String key) {
		if (!root.has(key) || !root.get(key).isJsonObject()) {
			throw new JsonSyntaxException("missing object '" + key + "'");
		}
		return root.getAsJsonObject(key);
	}

	private static JsonObject optionalObject(JsonObject root, String key) {
		return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
	}

	private static JsonObject object(JsonObject root, String key) {
		if (!root.has(key) || !root.get(key).isJsonObject()) {
			JsonObject value = new JsonObject();
			root.add(key, value);
			return value;
		}
		return root.getAsJsonObject(key);
	}

	private static Set<String> stringSet(JsonElement element) {
		Set<String> result = new LinkedHashSet<>();
		if (element != null && element.isJsonArray()) {
			for (JsonElement value : element.getAsJsonArray()) {
				result.add(value.getAsString());
			}
		}
		return result;
	}

	private static JsonArray sortedArray(Set<String> values) {
		List<String> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		JsonArray result = new JsonArray();
		for (String value : sorted) {
			result.add(value);
		}
		return result;
	}

	private static int integer(JsonObject root, String key, int fallback) {
		return root.has(key) ? root.get(key).getAsInt() : fallback;
	}

	private static double decimal(JsonObject root, String key, double fallback) {
		return root.has(key) ? root.get(key).getAsDouble() : fallback;
	}

	private static String string(JsonObject root, String key, String fallback) {
		return root.has(key) ? root.get(key).getAsString() : fallback;
	}

	private static boolean bool(JsonObject root, String key, boolean fallback) {
		return root.has(key) ? root.get(key).getAsBoolean() : fallback;
	}

	private static final class ProviderDefinition {
		final String modId;
		final int revision;
		final JsonObject root;
		ProviderDefinition(String modId, int revision, JsonObject root) {
			this.modId = modId;
			this.revision = revision;
			this.root = root;
		}
		JsonObject section(String name) {
			return optionalObject(root, name);
		}
	}

	public static final class TemplateDefinition {
		private final ResourceLocation id;
		private final String nameKey;
		private final String descriptionKey;
		private final JsonObject profile;
		private final boolean available;
		private final boolean autoSelect;
		private final int autoSelectPriority;

		TemplateDefinition(ResourceLocation id, String nameKey, String descriptionKey,
				JsonObject profile, boolean available, boolean autoSelect, int autoSelectPriority) {
			this.id = id;
			this.nameKey = nameKey;
			this.descriptionKey = descriptionKey;
			this.profile = profile;
			this.available = available;
			this.autoSelect = autoSelect;
			this.autoSelectPriority = autoSelectPriority;
		}

		public ResourceLocation id() { return id; }
		public String nameKey() { return nameKey; }
		public String descriptionKey() { return descriptionKey; }
		public boolean available() { return available; }
		public boolean autoSelect() { return autoSelect; }
		public int autoSelectPriority() { return autoSelectPriority; }
	}
}
