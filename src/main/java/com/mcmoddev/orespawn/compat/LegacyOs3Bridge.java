package com.mcmoddev.orespawn.compat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mcmoddev.orespawn.api.BiomeLocation;
import com.mcmoddev.orespawn.api.GeneratorParameters;
import com.mcmoddev.orespawn.api.IBlockList;
import com.mcmoddev.orespawn.api.IDimensionList;
import com.mcmoddev.orespawn.api.IFeature;
import com.mcmoddev.orespawn.api.os3.BiomeBuilder;
import com.mcmoddev.orespawn.api.os3.BuilderLogic;
import com.mcmoddev.orespawn.api.os3.DimensionBuilder;
import com.mcmoddev.orespawn.api.os3.FeatureBuilder;
import com.mcmoddev.orespawn.api.os3.IBiomeBuilder;
import com.mcmoddev.orespawn.api.os3.IBlockBuilder;
import com.mcmoddev.orespawn.api.os3.IBlockDefinition;
import com.mcmoddev.orespawn.api.os3.IDimensionBuilder;
import com.mcmoddev.orespawn.api.os3.IFeatureBuilder;
import com.mcmoddev.orespawn.api.os3.IFeatureEntry;
import com.mcmoddev.orespawn.api.os3.IReplacementBuilder;
import com.mcmoddev.orespawn.api.os3.IReplacementEntry;
import com.mcmoddev.orespawn.api.os3.ISpawnBuilder;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;
import com.mcmoddev.orespawn.api.os3.OS3API;
import com.mcmoddev.orespawn.api.os3.OreBuilder;
import com.mcmoddev.orespawn.api.os3.OreSpawnBlockMatcher;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.api.plugin.IOreSpawnPlugin;
import com.mcmoddev.orespawn.api.plugin.OreSpawnPlugin;
import com.mcmoddev.orespawn.data.FeatureRegistry;
import com.mcmoddev.orespawn.data.PresetsStorage;
import com.mcmoddev.orespawn.util.OS3V2PresetStorage;
import com.mcmoddev.orespawn.util.OreList;
import com.mcmoddev.orespawn.worldgen.OreSpawnWorldGen;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraft.item.ItemStack;
import zone.moddev.mc.orespawn.worldgen.LegacyOs3ProfileMigration;

/**
 * Deprecated OS3 binary/config bridge. It translates declarative entries to
 * provider-schema 4 and owns the only scheduler for legacy custom generators.
 */
public final class LegacyOs3Bridge {
	private static final Logger LOGGER = LogManager.getLogger("OreSpawn-OS3-Bridge");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final Map<ResourceLocation, IFeature> SAVED_FEATURES = new LinkedHashMap<>();
	private static final Map<ResourceLocation, IReplacementEntry> SAVED_REPLACEMENTS = new LinkedHashMap<>();
	private static final LegacyApi API = new LegacyApi();
	private static final FeatureRegistry FEATURES = new FeatureRegistry();
	private static final List<String> REPORT = new ArrayList<>();
	private static boolean initialized;
	private static boolean completed;
	private static Path configurationDirectory;
	private static final Map<String, JsonObject> OS1_PROGRAMMATIC = new LinkedHashMap<>();

	static {
		for (String name : new String[] { "default", "vein", "normal-cloud", "precision",
				"clusters", "underfluids" }) {
			registerSavedFeature(name, new SavedFeature());
		}
	}

	private LegacyOs3Bridge() { }

	public static OS3API api() { return API; }
	public static FeatureRegistry features() { return FEATURES; }

	private static void registerSavedFeature(String name, IFeature feature) {
		ResourceLocation id = validId(name) ? new ResourceLocation(name)
				: new ResourceLocation("orespawn", safe(name));
		if (!SAVED_FEATURES.containsKey(id)) {
			SAVED_FEATURES.put(id, feature);
		}
		if (!FEATURES.hasFeature(id.toString())) FEATURES.addFeature(id.toString(), feature);
		if (!name.contains(":") && !FEATURES.hasFeature(name)) FEATURES.addFeature(name, feature);
	}

	private static void registerSavedReplacement(IReplacementEntry replacement) {
		if (replacement != null && replacement.getRegistryName() != null
				&& !SAVED_REPLACEMENTS.containsKey(replacement.getRegistryName())) {
			SAVED_REPLACEMENTS.put(replacement.getRegistryName(), replacement);
		}
	}

	static JsonObject translateForTests(String modId, JsonObject source, Path legacyDirectory,
			Path legacyConfig) throws IOException {
		return translate(modId, modId, source, legacyDirectory, LegacyFlags.read(legacyConfig));
	}

	static JsonObject translateStandaloneForTests(String sourceId, JsonObject source, Path legacyDirectory,
			Path legacyConfig) throws IOException {
		return translate("orespawn", sourceId, source, legacyDirectory, LegacyFlags.read(legacyConfig));
	}

	static void resetProgrammaticForTests(String owner) {
		API.resetProgrammatic(owner);
		REPORT.clear();
	}

	static Map<String, JsonObject> programmaticSourcesForTests() {
		return API.programmaticSources();
	}

	static List<String> reportForTests() {
		return Collections.unmodifiableList(new ArrayList<>(REPORT));
	}

	static int[] translatedProgrammaticCountsForTests() {
		return new int[] { API.translatedSpawns.size(), API.translated322Spawns.size() };
	}

	public static synchronized void initialize(FMLPreInitializationEvent event) {
		if (initialized) return;
		initialized = true;
		registerSavedReplacement(new LegacyReplacementEntry("orespawn:default",
				Arrays.asList(Blocks.STONE.getDefaultState(), Blocks.NETHERRACK.getDefaultState(),
						Blocks.END_STONE.getDefaultState())));
		configurationDirectory = event.getModConfigurationDirectory().toPath();
		scanPlugins(event.getAsmData());
	}

	/**
	 * Finishes legacy discovery after every mod has had its pre-initialization
	 * callback. Older Base Metals versions create their OS1 configuration during
	 * pre-initialization, so scanning any earlier would silently miss a clean
	 * install on its first launch.
	 */
	public static synchronized void completeInitialization() {
		if (completed || configurationDirectory == null) return;
		completed = true;
		migrateConfigDirectory(configurationDirectory);
	}

	/** Registers a single OreSpawn 1.x declaration with the OS4 scheduler. */
	public static synchronized void registerOs1Spawn(String owner, String name, JsonObject spawn) {
		String safeOwner = validModId(owner) ? owner : "legacy";
		JsonObject source = OS1_PROGRAMMATIC.computeIfAbsent(safeOwner, ignored -> {
			JsonObject value = new JsonObject();
			value.addProperty("version", "1.0");
			value.add("spawns", new JsonObject());
			return value;
		});
		object(source, "spawns").add(name, new JsonParser().parse(spawn.toString()));
	}

	/** Records an OreSpawn 1.x file created by a legacy consumer. */
	public static synchronized void registerOs1Config(Path path) throws IOException {
		if (path == null) return;
		try (InputStream input = Files.newInputStream(path)) {
			JsonObject source = readObject(input);
			if (!hasOs1Config(source)) throw new IOException("Not an OreSpawn 1.x dimensions file: " + path);
			String file = path.getFileName() == null ? "legacy" : path.getFileName().toString();
			String owner = safe(file.replaceFirst("\\.json$", ""));
			OS1_PROGRAMMATIC.put(owner, convertOs1Source(owner, source));
			REPORT.add("os1_config_registered=" + path.toAbsolutePath());
		}
	}

	public static void generate(Random random, int chunkX, int chunkZ, World world,
			IChunkGenerator generator, IChunkProvider provider) {
		API.generate(random, new ChunkPos(chunkX, chunkZ), world, generator, provider);
	}

	private static void scanPlugins(ASMDataTable table) {
		for (ASMDataTable.ASMData data : table.getAll(OreSpawnPlugin.class.getName())) {
			try {
				Object value = Class.forName(data.getClassName()).newInstance();
				if (!(value instanceof IOreSpawnPlugin)) {
					REPORT.add("plugin_rejected=" + data.getClassName() + ":not_IOreSpawnPlugin");
					continue;
				}
				@SuppressWarnings("unchecked") Map<String, Object> info = data.getAnnotationInfo();
				String modId = String.valueOf(info.get("modid"));
				Object configuredPath = info.get("resourcePath");
				String resourcePath = configuredPath == null ? "orespawn" : String.valueOf(configuredPath);
				if (resourcePath.trim().isEmpty()) resourcePath = "orespawn";
				API.activeModId = modId;
				((IOreSpawnPlugin) value).register(API);
				API.activeModId = "legacy";
				scanEmbeddedResources(modId, resourcePath);
				REPORT.add("plugin_loaded=" + data.getClassName());
			} catch (ReflectiveOperationException | RuntimeException failure) {
				REPORT.add("plugin_failed=" + data.getClassName() + ":" + failure.getClass().getSimpleName());
				LOGGER.error("Could not load legacy OreSpawn plugin {}", data.getClassName(), failure);
			}
		}
	}

	private static void scanEmbeddedResources(String modId, String resourcePath) {
		String prefix = "assets/" + modId + "/" + resourcePath + "/";
		boolean found = false;
		try {
			net.minecraftforge.fml.common.ModContainer container = Loader.instance().getIndexedModList().get(modId);
			java.io.File source = container == null ? null : container.getSource();
			if (source != null && source.isFile()) {
				try (JarFile jar = new JarFile(source)) {
					List<String> names = new ArrayList<>(); Enumeration<JarEntry> entries = jar.entries();
					while (entries.hasMoreElements()) { String name = entries.nextElement().getName(); if (name.startsWith(prefix) && name.endsWith(".json")) names.add(name); }
					Collections.sort(names);
					for (String name : names) { try (InputStream input = jar.getInputStream(jar.getJarEntry(name))) { consumeEmbedded(modId, name, readElement(input)); found = true; } }
				}
			} else if (source != null && source.isDirectory()) {
				Path directory = source.toPath().resolve(prefix.replace('/', java.io.File.separatorChar));
				if (Files.isDirectory(directory)) {
					List<Path> files = new ArrayList<>(); try (java.util.stream.Stream<Path> walk = Files.walk(directory, 1)) { walk.filter(path -> path.toString().endsWith(".json")).forEach(files::add); }
					files.sort((left, right) -> left.getFileName().toString().compareTo(right.getFileName().toString()));
					for (Path file : files) { try (InputStream input = Files.newInputStream(file)) { consumeEmbedded(modId, prefix + file.getFileName(), readElement(input)); found = true; } }
				}
			}
		} catch (IOException | RuntimeException failure) {
			REPORT.add("resource_scan_failed=" + prefix + ":" + failure.getClass().getSimpleName());
			LOGGER.error("Could not scan legacy OreSpawn resources under {}", prefix, failure);
		}
		if (!found) {
			String path = prefix + modId + ".json";
			try (InputStream input = LegacyOs3Bridge.class.getClassLoader().getResourceAsStream(path)) {
				if (input != null) { consumeEmbedded(modId, path, readElement(input)); found = true; }
			} catch (IOException | RuntimeException failure) {
				REPORT.add("resource_failed=" + path + ":" + failure.getClass().getSimpleName());
			}
		}
		if (!found) REPORT.add("resource_missing=" + prefix);
	}

	private static void consumeEmbedded(String modId, String path, JsonElement root) {
		String file = path.substring(path.lastIndexOf('/') + 1);
		if (file.startsWith("_features") && root.isJsonArray()) {
			for (JsonElement value : root.getAsJsonArray()) {
				if (!value.isJsonObject()) continue; JsonObject feature = value.getAsJsonObject();
				String name = text(feature, "name", ""), className = text(feature, "class", "");
				if (name.isEmpty() || className.isEmpty() || FEATURES.hasFeature(name)) continue;
				try { API.registerFeatureGenerator(name, className); }
				catch (RuntimeException failure) { REPORT.add("embedded_feature_failed=" + name + ":" + failure.getClass().getSimpleName()); }
			}
		} else if (file.startsWith("_replacements")) {
			mergeReplacementElement(API.embeddedReplacements, root);
		} else if (root.isJsonObject()) {
			JsonObject combined = API.embedded.computeIfAbsent(modId, ignored -> new JsonObject());
			JsonObject combinedSpawns = object(combined, "spawns");
			for (Map.Entry<String, JsonElement> spawn : object(root.getAsJsonObject(), "spawns").entrySet()) {
				combinedSpawns.add(spawn.getKey(), new JsonParser().parse(spawn.getValue().toString()));
			}
			combined.addProperty("version", text(root.getAsJsonObject(), "version", "2.0")); combined.add("spawns", combinedSpawns);
		}
		REPORT.add("resource_loaded=" + path);
	}

	private static void migrateConfigDirectory(Path configDirectory) {
		Path legacyDirectory = configDirectory.resolve("orespawn3");
		Path os1Directory = configDirectory.resolve("orespawn");
		Path bridgeMarker = configDirectory.resolve(".orespawn-legacy-bridge-migrated");
		if (!hasLegacyConfig(legacyDirectory) && !hasLegacyConfig(os1Directory)
				&& API.embedded.isEmpty() && !API.hasProgrammaticRegistrations()
				&& OS1_PROGRAMMATIC.isEmpty()) {
			return;
		}
		LegacyFlags flags = LegacyFlags.read(configDirectory.resolve("orespawn.cfg"));
		try {
			REPORT.add("profile_migration=" + LegacyOs3ProfileMigration.apply(configDirectory,
					flags.replaceVanilla, flags.disableStandard, flags.retrogen,
					flags.forceRetrogen, flags.flatBedrock, flags.retrogenBedrock,
					flags.bedrockLayers).name().toLowerCase(java.util.Locale.ROOT));
		} catch (IOException failure) {
			REPORT.add("profile_migration_failed=" + failure.getClass().getSimpleName());
			LOGGER.error("Could not migrate OS3 global world-generation settings", failure);
		}
		Map<String, JsonObject> programmaticSources = API.programmaticSources();
		if (Files.isRegularFile(bridgeMarker)) {
			LOGGER.info("OS3 provider migration already completed; retaining migrated files unchanged");
			return;
		}
		Map<String, JsonObject> sources = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> source : OS1_PROGRAMMATIC.entrySet()) {
			mergeLegacySource(sources, source.getKey(), source.getValue());
		}
		for (Map.Entry<String, JsonObject> source : programmaticSources.entrySet()) {
			mergeLegacySource(sources, source.getKey(), source.getValue());
		}
		for (Map.Entry<String, JsonObject> source : API.embedded.entrySet()) {
			mergeLegacySource(sources, source.getKey(), source.getValue());
		}
		if (Files.isDirectory(legacyDirectory)) {
			try (DirectoryStream<Path> files = Files.newDirectoryStream(legacyDirectory, "*.json")) {
				List<Path> sorted = new ArrayList<>(); for (Path file : files) sorted.add(file);
				sorted.sort((left, right) -> left.getFileName().toString().compareTo(right.getFileName().toString()));
				for (Path file : sorted) {
					String modId = file.getFileName().toString().replaceFirst("\\.json$", "");
					try (InputStream input = Files.newInputStream(file)) {
						mergeLegacySource(sources, modId, readObject(input));
						REPORT.add("config_source=" + file.toAbsolutePath());
					} catch (IOException | RuntimeException failure) {
						REPORT.add("config_rejected=" + file.getFileName() + ":" + failure.getClass().getSimpleName());
					}
				}
			} catch (IOException failure) {
				REPORT.add("config_scan_failed=" + failure.getClass().getSimpleName());
			}
		}
		if (Files.isDirectory(os1Directory)) {
			try (DirectoryStream<Path> files = Files.newDirectoryStream(os1Directory, "*.json")) {
				List<Path> sorted = new ArrayList<>(); for (Path file : files) sorted.add(file);
				sorted.sort((left, right) -> left.getFileName().toString().compareTo(right.getFileName().toString()));
				for (Path file : sorted) {
					String modId = file.getFileName().toString().replaceFirst("\\.json$", "");
					try (InputStream input = Files.newInputStream(file)) {
						JsonObject source = readObject(input);
						if (hasOs1Config(source)) {
							mergeLegacySource(sources, modId, convertOs1Source(modId, source));
							REPORT.add("os1_config_source=" + file.toAbsolutePath());
						} else {
							REPORT.add("os1_config_rejected=" + file.getFileName() + ":missing_dimensions");
						}
					} catch (IOException | RuntimeException failure) {
						REPORT.add("os1_config_rejected=" + file.getFileName() + ":" + failure.getClass().getSimpleName());
					}
				}
			} catch (IOException failure) {
				REPORT.add("os1_config_scan_failed=" + failure.getClass().getSimpleName());
			}
		}

		Map<String, JsonObject> providers = new LinkedHashMap<>();
		for (Map.Entry<String, JsonObject> source : sources.entrySet()) {
			if (!validModId(source.getKey())) {
				REPORT.add("config_rejected=" + source.getKey() + ":invalid_owner");
				continue;
			}
			try {
				boolean owned = isModLoaded(source.getKey());
				String providerModId = owned ? source.getKey() : "orespawn";
				JsonObject provider = translate(providerModId, source.getKey(), source.getValue(), legacyDirectory, flags);
				if (!owned) REPORT.add("standalone_config_mapped=" + source.getKey() + ":provider=orespawn");
				mergeProvider(providers, providerModId, provider, source.getKey());
			} catch (RuntimeException | IOException failure) {
				REPORT.add("translation_failed=" + source.getKey() + ":" + failure.getClass().getSimpleName()
						+ ":" + String.valueOf(failure.getMessage()));
				LOGGER.error("Could not translate OS3 provider {}", source.getKey(), failure);
			}
		}
		for (Map.Entry<String, JsonObject> provider : providers.entrySet()) {
			try {
				writeAtomicIfChanged(configDirectory.resolve(provider.getKey() + "-orespawn.json"), provider.getValue());
			} catch (IOException failure) {
				REPORT.add("provider_write_failed=" + provider.getKey() + ":" + failure.getClass().getSimpleName());
				LOGGER.error("Could not write migrated OS3 provider {}", provider.getKey(), failure);
				writeReport(configDirectory.resolve("orespawn-os3-migration-report.json"));
				return;
			}
		}
		writeReport(configDirectory.resolve("orespawn-os3-migration-report.json"));
		try {
			writeMarker(bridgeMarker);
		} catch (IOException failure) {
			REPORT.add("migration_marker_failed=" + failure.getClass().getSimpleName());
			LOGGER.error("Could not mark OS3 provider migration complete", failure);
		}
	}

	static JsonObject translateOs1ForTests(String owner, JsonObject source) {
		return convertOs1Source(owner, source);
	}

	private static boolean hasOs1Config(JsonObject source) {
		return source != null && source.has("dimensions") && source.get("dimensions").isJsonArray();
	}

	/** Converts the OreSpawn 1.x per-dimension format into the bridge's OS3-like
	 * intermediate representation. Keeping one final translator guarantees that
	 * OS1, OS3 embedded resources and programmatic registrations share one OS4
	 * scheduler and can never independently generate the same declaration. */
	private static JsonObject convertOs1Source(String owner, JsonObject source) {
		JsonObject converted = new JsonObject();
		converted.addProperty("version", "1.0");
		JsonObject spawns = new JsonObject(); converted.add("spawns", spawns);
		Set<Integer> explicit = new LinkedHashSet<>();
		for (JsonElement element : array(source, "dimensions")) {
			if (!element.isJsonObject()) continue;
			JsonElement value = element.getAsJsonObject().get("dimension");
			if (value != null && !value.isJsonNull() && !(value.isJsonPrimitive()
					&& value.getAsJsonPrimitive().isString() && "+".equals(value.getAsString()))) {
				try { explicit.add(value.getAsInt()); }
				catch (RuntimeException failure) { REPORT.add("os1_dimension_ignored=" + value); }
			}
		}
		int ordinal = 0;
		for (JsonElement element : array(source, "dimensions")) {
			if (!element.isJsonObject()) continue;
			JsonObject dimension = element.getAsJsonObject();
			JsonElement configured = dimension.get("dimension");
			Set<Integer> dimensions = new LinkedHashSet<>();
			if (configured != null && configured.isJsonPrimitive()
					&& configured.getAsJsonPrimitive().isString() && "+".equals(configured.getAsString())) {
				Integer[] registered = DimensionManager.getStaticDimensionIDs();
				Arrays.sort(registered);
				for (Integer id : registered) if (!explicit.contains(id)) dimensions.add(id);
				REPORT.add("os1_plus_dimensions=" + owner + ":" + dimensions);
			} else if (configured != null) {
				try { dimensions.add(configured.getAsInt()); }
				catch (RuntimeException failure) { REPORT.add("os1_dimension_ignored=" + configured); }
			}
			for (JsonElement oreElement : array(dimension, "ores")) {
				if (!oreElement.isJsonObject()) continue;
				JsonObject old = oreElement.getAsJsonObject();
				String output = text(old, "blockID", "");
				if (!validId(output)) {
					REPORT.add("os1_spawn_ignored=" + owner + ":invalid_output:" + output);
					continue;
				}
				JsonObject spawn = new JsonObject(); spawn.addProperty("enabled", true);
				spawn.addProperty("feature", "default"); spawn.addProperty("replaces", "default");
				JsonArray blocks = new JsonArray(); JsonObject block = new JsonObject();
				block.addProperty("name", output); block.addProperty("chance", 100);
				if (old.has("blockMeta")) block.addProperty("metadata", old.get("blockMeta").getAsInt());
				blocks.add(block); spawn.add("blocks", blocks);
				JsonObject parameters = new JsonObject();
				parameters.addProperty("size", integer(old, "size", 8));
				parameters.addProperty("variation", integer(old, "variation", 0));
				parameters.addProperty("frequency", decimal(old, "frequency", 0.5D));
				parameters.addProperty("minHeight", integer(old, "minHeight", 0));
				// OS1's maximum was exclusive; the shared translator deliberately
				// converts it to OS4's inclusive max_y exactly once.
				parameters.addProperty("maxHeight", integer(old, "maxHeight", 256));
				spawn.add("parameters", parameters);
				JsonArray selectedDimensions = new JsonArray();
				for (Integer id : dimensions) selectedDimensions.add(new JsonPrimitive(id));
				spawn.add("dimensions", selectedDimensions);
				spawn.add("biomes", convertOs1Biomes(old.get("biomes"), owner, output));
				String base = safe(output.replace(':', '_'));
				String name = base + "_" + ordinal++;
				while (spawns.has(name)) name = base + "_" + ordinal++;
				spawns.add(name, spawn);
			}
		}
		return converted;
	}

	private static JsonObject convertOs1Biomes(JsonElement source, String owner, String output) {
		JsonObject result = new JsonObject(); JsonArray includes = new JsonArray();
		result.add("includes", includes);
		if (source == null || !source.isJsonArray() || source.getAsJsonArray().size() == 0) return result;
		for (JsonElement value : source.getAsJsonArray()) {
			Biome biome = null;
			if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
				biome = Biome.getBiome(value.getAsInt());
			} else {
				String requested = value.getAsString();
				if (validId(requested)) biome = ForgeRegistries.BIOMES.getValue(new ResourceLocation(requested));
				if (biome == null) for (Map.Entry<ResourceLocation, Biome> entry : ForgeRegistries.BIOMES.getEntries()) {
					if (entry.getKey().getResourcePath().equalsIgnoreCase(requested)
							|| entry.getValue().getBiomeName().equalsIgnoreCase(requested)) { biome = entry.getValue(); break; }
				}
			}
			ResourceLocation id = biome == null ? null : biome.getRegistryName();
			if (id != null) includes.add(new JsonPrimitive(id.toString()));
			else REPORT.add("os1_biome_unresolved=" + owner + ":" + output + ":" + value);
		}
		if (includes.size() == 0) {
			// A non-empty but entirely unresolved restriction must match nothing,
			// never broaden into an unrestricted rule.
			includes.add(new JsonPrimitive("orespawn:unresolved_legacy_biome"));
		}
		return result;
	}

	private static void mergeLegacySource(Map<String, JsonObject> sources, String owner, JsonObject incoming) {
		JsonObject target = sources.computeIfAbsent(owner, ignored -> new JsonObject());
		target.addProperty("version", text(incoming, "version", "2.0"));
		JsonObject targetSpawns = object(target, "spawns");
		for (Map.Entry<String, JsonElement> spawn : object(incoming, "spawns").entrySet()) {
			targetSpawns.add(spawn.getKey(), new JsonParser().parse(spawn.getValue().toString()));
		}
		target.add("spawns", targetSpawns);
	}

	private static boolean hasLegacyConfig(Path directory) {
		if (!Files.isDirectory(directory)) return false;
		try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json")) {
			return files.iterator().hasNext();
		} catch (IOException failure) {
			REPORT.add("config_presence_failed=" + failure.getClass().getSimpleName());
			return false;
		}
	}

	private static void mergeProvider(Map<String, JsonObject> providers, String providerModId,
			JsonObject incoming, String sourceId) {
		JsonObject target = providers.get(providerModId);
		if (target == null) {
			providers.put(providerModId, incoming);
			return;
		}
		JsonObject targetOres = object(target, "ores");
		for (Map.Entry<String, JsonElement> ore : object(incoming, "ores").entrySet()) {
			if (targetOres.has(ore.getKey())) {
				throw new IllegalArgumentException("duplicate synthesized ore id " + ore.getKey()
						+ " from " + sourceId);
			}
			targetOres.add(ore.getKey(), new JsonParser().parse(ore.getValue().toString()));
		}
		target.add("ores", targetOres);
	}

	private static JsonObject translate(String providerModId, String sourceId, JsonObject source, Path legacyDirectory,
			LegacyFlags flags) throws IOException {
		JsonObject provider = new JsonObject();
		provider.addProperty("schema_version", 4);
		provider.addProperty("provider_modid", providerModId);
		provider.addProperty("provider_revision", 1);
		provider.add("rocks", new JsonObject());
		JsonObject ores = new JsonObject(); provider.add("ores", ores);
		provider.add("fluid_deposits", new JsonObject()); provider.add("geomes", new JsonObject());
		provider.add("biome_rules", new JsonObject()); provider.add("terrain_dimensions", new JsonObject());
		provider.add("biome_palettes", new JsonObject()); provider.add("dimension_materials", new JsonObject());
		provider.add("templates", new JsonObject());
		JsonObject replacements = readReplacements(legacyDirectory);
		JsonObject spawns = object(source, "spawns");
		for (Map.Entry<String, JsonElement> entry : spawns.entrySet()) {
			if (!entry.getValue().isJsonObject()) { REPORT.add("spawn_ignored=" + sourceId + ":" + entry.getKey() + ":not_object"); continue; }
			JsonObject migrated = translateSpawn(sourceId, entry.getKey(), entry.getValue().getAsJsonObject(), replacements,
					flags);
			if (migrated != null) {
				String path = "legacy/" + (providerModId.equals(sourceId) ? "" : safe(sourceId) + "/")
						+ safe(entry.getKey());
				ores.add(new ResourceLocation(providerModId, path).toString(), migrated);
			}
		}
		REPORT.add("provider_translated=" + sourceId + ":owner=" + providerModId + ":ores=" + ores.entrySet().size());
		return provider;
	}

	private static JsonObject translateSpawn(String modId, String name, JsonObject spawn,
			JsonObject replacements, LegacyFlags flags) {
		JsonArray blocks = array(spawn, "blocks");
		if (blocks.size() == 0) { REPORT.add("spawn_ignored=" + modId + ":" + name + ":no_blocks"); return null; }
		JsonObject first = blocks.get(0).getAsJsonObject();
		String output = text(first, "name", "");
		if (!validId(output)) { REPORT.add("spawn_ignored=" + modId + ":" + name + ":invalid_output"); return null; }
		JsonObject ore = new JsonObject(); ore.addProperty("enabled", bool(spawn, "enabled", true));
		ore.addProperty("block", output); ore.addProperty("source_mod", modId);
		ore.addProperty("native_generation", false);
		ore.addProperty("retrogen", bool(spawn, "retrogen", false));
		copyMetadata(first, ore);
		if (flags.replaceVanilla && "minecraft".equals(new ResourceLocation(output).getResourceDomain())) {
			ore.addProperty("suppress_vanilla", true);
		}
		JsonArray outputs = new JsonArray();
		for (JsonElement element : blocks) {
			if (!element.isJsonObject()) continue; JsonObject old = element.getAsJsonObject();
			String block = text(old, "name", ""); if (!validId(block)) continue;
			JsonObject value = new JsonObject(); value.addProperty("block", block);
			value.addProperty("weight", Math.max(1, integer(old, "chance", 100)));
			copyMetadata(old, value); outputs.add(value);
		}
		ore.add("outputs", outputs);
		JsonObject dimensions = new JsonObject(); JsonObject selectors = new JsonObject();
		DimensionSelection selection = dimensions(spawn.get("dimensions"));
		if (selection.defaultOverworld) {
			JsonObject placement = placement(modId, name, spawn, replacements, flags);
			if (placement == null) return null;
			selectors.add("orespawn:all_except_nether_end", placement);
		} else {
			for (int dimension : selection.ids) {
				JsonObject placement = placement(modId, name, spawn, replacements, flags);
				if (placement == null) return null;
				dimensions.add(dimensionId(dimension), placement);
			}
		}
		if (dimensions.entrySet().size() > 0) ore.add("dimensions", dimensions);
		if (selectors.entrySet().size() > 0) ore.add("dimension_selectors", selectors);
		if (dimensions.entrySet().size() == 0 && selectors.entrySet().size() == 0) {
			REPORT.add("spawn_ignored=" + modId + ":" + name + ":no_dimensions"); return null;
		}
		if (bool(spawn, "retrogen", false)) REPORT.add("retrogen_requested=" + modId + ":" + name);
		return ore;
	}

	private static JsonObject placement(String modId, String name, JsonObject spawn,
			JsonObject replacements, LegacyFlags flags) {
		String feature = normalizePattern(text(spawn, "feature", "default"));
		JsonObject parameters = parameters(feature, object(spawn, "parameters")); JsonObject result = new JsonObject();
		result.addProperty("enabled", bool(spawn, "enabled", true));
		int minY = clamp(integer(parameters, "minHeight", 0), 0, 255);
		int exclusiveMaxY = clamp(integer(parameters, "maxHeight", 256), 0, 256);
		if (exclusiveMaxY <= minY) {
			REPORT.add("spawn_ignored=" + modId + ":" + name + ":empty_height_range=" + minY + ".." + exclusiveMaxY);
			return null;
		}
		result.addProperty("min_y", minY); result.addProperty("max_y", exclusiveMaxY - 1);
		double frequency = legacyFrequency(feature, parameters);
		result.addProperty("frequency", clampedFrequency(modId, name, frequency));
		addQuantity(result, modId, name, feature, parameters);
		String pattern = feature;
		if ("normal_cloud".equals(pattern) || "default".equals(pattern) || "vein".equals(pattern)
				|| "precision".equals(pattern) || "clusters".equals(pattern) || "underfluids".equals(pattern)) {
			result.addProperty("pattern", "orespawn:" + pattern);
		} else {
			result.addProperty("pattern", "orespawn:default");
			REPORT.add("custom_feature_scheduled=" + feature);
		}
		result.addProperty("height_distribution", "uniform");
		result.addProperty("spread", clamp(integer(parameters, "maxSpread", 8), 0, 64));
		result.addProperty("vertical_spread", clamp(integer(parameters, "variation", 4), 0, 64));
		result.addProperty("node_size", clamp(integer(parameters, "nodeSize",
				integer(parameters, "size", 4)), 1, 32));
		result.addProperty("length", clamp(integer(parameters, "length", 16), 1, 64));
		String fluid = text(parameters, "fluid", "water");
		result.addProperty("fluid", fluid.indexOf(':') >= 0 ? fluid : "minecraft:" + fluid);
		JsonArray hosts = replacementHosts(text(spawn, "replaces", "default"), replacements);
		if ("default".equals(text(spawn, "replaces", "default"))) {
			for (String configured : flags.nonstandardHosts) addLegacyState(hosts, configured, "");
		}
		if ("default".equals(text(spawn, "replaces", "default")) && isModLoaded("mineralogy")) {
			appendMineralogyRockHosts(hosts);
		}
		hosts = uniqueHosts(hosts);
		if (hosts.size() == 0) {
			hosts.add(new JsonPrimitive("minecraft:stone")); hosts.add(new JsonPrimitive("minecraft:netherrack"));
			hosts.add(new JsonPrimitive("minecraft:end_stone"));
		}
		result.add("host_blocks", hosts);
		result.add("host_tags", new JsonArray()); result.add("host_families", new JsonArray());
		copyBiomeSelectors(spawn, result);
		return result;
	}

	private static JsonArray uniqueHosts(JsonArray hosts) {
		JsonArray result = new JsonArray();
		Set<String> seen = new LinkedHashSet<>();
		for (JsonElement host : hosts) {
			String identity = host.toString();
			if (seen.add(identity)) result.add(new JsonParser().parse(identity));
		}
		return result;
	}

	private static JsonObject parameters(String feature, JsonObject configured) {
		JsonObject result = new JsonObject();
		if ("default".equals(feature)) {
			result.addProperty("minHeight", 0); result.addProperty("maxHeight", 256);
			result.addProperty("variation", 16); result.addProperty("frequency", 0.5D);
			result.addProperty("size", 8);
		} else if ("vein".equals(feature)) {
			result.addProperty("minHeight", 0); result.addProperty("maxHeight", 256);
			result.addProperty("variation", 16); result.addProperty("frequency", 50);
			result.addProperty("attemptsMin", 4); result.addProperty("attemptsMax", 8);
			result.addProperty("length", 16); result.addProperty("size", 3);
		} else if ("normal_cloud".equals(feature)) {
			result.addProperty("maxSpread", 16); result.addProperty("size", 8);
			result.addProperty("minHeight", 8); result.addProperty("maxHeight", 24);
			result.addProperty("variation", 4); result.addProperty("frequency", 25);
			result.addProperty("attemptsMin", 4); result.addProperty("attemptsMax", 4);
		} else if ("precision".equals(feature)) {
			result.addProperty("numObjects", 4); result.addProperty("minHeight", 16);
			result.addProperty("maxHeight", 80); result.addProperty("size", 8);
		} else if ("clusters".equals(feature)) {
			result.addProperty("maxSpread", 16); result.addProperty("size", 8);
			result.addProperty("numObjects", 8); result.addProperty("minHeight", 8);
			result.addProperty("maxHeight", 24); result.addProperty("variation", 4);
			result.addProperty("frequency", 25); result.addProperty("attemptsMin", 4);
			result.addProperty("attemptsMax", 8);
		} else if ("underfluids".equals(feature)) {
			result.addProperty("minHeight", 0); result.addProperty("maxHeight", 256);
			result.addProperty("variation", 16); result.addProperty("attemptsMin", 4);
			result.addProperty("attemptsMax", 4); result.addProperty("size", 8);
			result.addProperty("fluid", "water");
		}
		for (Map.Entry<String, JsonElement> entry : configured.entrySet()) {
			result.add(entry.getKey(), new JsonParser().parse(entry.getValue().toString()));
		}
		return result;
	}

	private static String normalizePattern(String value) {
		String result = value == null ? "default" : value.trim().toLowerCase(java.util.Locale.ROOT)
				.replace('-', '_');
		if ("cloud".equals(result)) return "normal_cloud";
		if ("cluster".equals(result)) return "clusters";
		if ("under_fluid".equals(result)) return "underfluids";
		return result;
	}

	private static double legacyFrequency(String feature, JsonObject parameters) {
		if ("default".equals(feature)) return decimal(parameters, "frequency", 0.5D);
		if ("precision".equals(feature)) return integer(parameters, "numObjects", 4);
		double min = integer(parameters, "attemptsMin", 1);
		double max = integer(parameters, "attemptsMax", (int) min);
		double attempts = (min + max) / 2.0D;
		if ("underfluids".equals(feature)) return attempts;
		return attempts * decimal(parameters, "frequency", 100.0D) / 100.0D;
	}

	private static double clampedFrequency(String modId, String name, double value) {
		double clamped = clamp(value, 0.0D, 64.0D);
		if (!Double.isFinite(value) || value != clamped) {
			REPORT.add("frequency_clamped=" + modId + ":" + name + ":" + value + "->" + clamped);
		}
		return Double.isFinite(clamped) ? clamped : 0.0D;
	}

	private static void addQuantity(JsonObject result, String modId, String name,
			String feature, JsonObject parameters) {
		long size = integer(parameters, "size", 8);
		long variation = Math.max(0, integer(parameters, "variation", 0));
		long minimum = size;
		long maximum = size;
		if ("vein".equals(feature)) {
			long length = Math.max(1, integer(parameters, "length", 16));
			minimum = Math.max(1, length - variation) * size;
			maximum = (length + variation - (variation > 0 ? 1 : 0)) * size;
		} else if ("clusters".equals(feature)) {
			long nodes = Math.max(1, integer(parameters, "numObjects", 8));
			minimum = Math.max(1, size - variation) * Math.max(1, nodes - variation);
			maximum = (size + variation - (variation > 0 ? 1 : 0))
					* (nodes + variation - (variation > 0 ? 1 : 0));
		} else if (!"precision".equals(feature) && variation > 0) {
			minimum = size - variation;
			maximum = size + variation - 1;
		}
		int min = clampQuantity(modId, name, minimum);
		int max = clampQuantity(modId, name, maximum);
		if (min > max) { int swap = min; min = max; max = swap; }
		if (min == max) result.addProperty("quantity", min);
		else { result.addProperty("min_quantity", min); result.addProperty("max_quantity", max); }
	}

	private static int clampQuantity(String modId, String name, long value) {
		int clamped = (int) Math.max(1L, Math.min(64L, value));
		if (value != clamped) REPORT.add("quantity_clamped=" + modId + ":" + name + ":" + value + "->" + clamped);
		return clamped;
	}

	private static void copyBiomeSelectors(JsonObject spawn, JsonObject placement) {
		JsonObject biomes = object(spawn, "biomes");
		JsonArray ids = new JsonArray(), excludedIds = new JsonArray();
		JsonArray dictionary = new JsonArray(), excludedDictionary = new JsonArray();
		copySelectors(biomes.get("includes"), ids, dictionary);
		copySelectors(biomes.get("whitelist"), ids, dictionary);
		copySelectors(biomes.get("excludes"), excludedIds, excludedDictionary);
		copySelectors(biomes.get("blacklist"), excludedIds, excludedDictionary);
		placement.add("biome_ids", ids); placement.add("excluded_biome_ids", excludedIds);
		placement.add("biome_dictionary", dictionary); placement.add("excluded_biome_dictionary", excludedDictionary);
		placement.add("geomes", new JsonObject());
	}

	private static void copySelectors(JsonElement source, JsonArray ids, JsonArray dictionary) {
		if (source == null || !source.isJsonArray()) return;
		for (JsonElement value : source.getAsJsonArray()) {
			String text = value.getAsString();
			if (validId(text)) ids.add(new JsonPrimitive(text));
			else if (text.matches("[A-Za-z0-9_]+")) dictionary.add(new JsonPrimitive(text.toUpperCase(java.util.Locale.ROOT)));
			else REPORT.add("biome_selector_ignored=" + text);
		}
	}

	private static DimensionSelection dimensions(JsonElement source) {
		Set<Integer> result = new LinkedHashSet<>();
		if (source == null || source.isJsonNull()) return new DimensionSelection(true, result);
		if (source.isJsonArray()) {
			for (JsonElement value : source.getAsJsonArray()) result.add(value.getAsInt());
			return new DimensionSelection(result.isEmpty(), result);
		}
		if (source.isJsonObject()) {
			JsonObject object = source.getAsJsonObject();
			JsonArray included = array(object, "includes");
			if (included.size() == 0) return new DimensionSelection(true, result);
			for (JsonElement value : included) result.add(value.getAsInt());
			for (JsonElement value : array(object, "excludes")) result.remove(value.getAsInt());
			if (result.isEmpty()) REPORT.add("dimension_selection_empty=" + source);
			return new DimensionSelection(false, result);
		}
		result.add(source.getAsInt()); return new DimensionSelection(false, result);
	}

	private static final class DimensionSelection {
		final boolean defaultOverworld; final Set<Integer> ids;
		DimensionSelection(boolean defaultOverworld, Set<Integer> ids) {
			this.defaultOverworld = defaultOverworld; this.ids = ids;
		}
	}

	private static String dimensionId(int dimension) {
		if (dimension == 0) return "minecraft:overworld";
		if (dimension == -1) return "minecraft:the_nether";
		if (dimension == 1) return "minecraft:the_end";
		return "legacy:dimension_" + dimension;
	}

	private static JsonObject readReplacements(Path legacyDirectory) throws IOException {
		JsonObject result = new JsonParser().parse(API.embeddedReplacements.toString()).getAsJsonObject();
		Path system = legacyDirectory.resolve("sysconf");
		if (!Files.isDirectory(system)) return result;
		try (DirectoryStream<Path> files = Files.newDirectoryStream(system, "replacements-*.json")) {
			for (Path file : files) try (InputStream input = Files.newInputStream(file)) {
				JsonElement root = new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8));
				mergeReplacementElement(result, root);
			}
		}
		return result;
	}

	private static void mergeReplacementElement(JsonObject result, JsonElement root) {
		if (root.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
				if (entry.getValue().isJsonArray()) result.add(entry.getKey(), new JsonParser().parse(entry.getValue().toString()));
			}
		} else if (root.isJsonArray()) {
			for (JsonElement element : root.getAsJsonArray()) {
				if (!element.isJsonObject()) continue; JsonObject entry = element.getAsJsonObject();
				String name = text(entry, "name", "");
				if (!name.isEmpty()) {
					JsonArray values = result.has(name) && result.get(name).isJsonArray()
							? result.getAsJsonArray(name) : new JsonArray();
					values.add(new JsonParser().parse(entry.toString())); result.add(name, values);
				}
			}
		}
	}

	private static JsonArray replacementHosts(String name, JsonObject replacements) {
		JsonArray result = new JsonArray();
		if (!replacements.has(name) || !replacements.get(name).isJsonArray()) {
			if ("default".equals(name)) {
				result.add(new JsonPrimitive("minecraft:stone")); result.add(new JsonPrimitive("minecraft:netherrack"));
				result.add(new JsonPrimitive("minecraft:end_stone"));
			}
			return result;
		}
		for (JsonElement element : replacements.getAsJsonArray(name)) {
			if (!element.isJsonObject()) continue;
			JsonObject replacement = element.getAsJsonObject();
			String block = text(replacement, "blockName", text(replacement, "name", ""));
			if (replacement.has("metadata")) block += "@" + replacement.get("metadata").getAsInt();
			addLegacyState(result, block, text(replacement, "blockState", text(replacement, "state", "")));
		}
		return result;
	}

	private static void addLegacyState(JsonArray result, String block, String serializedState) {
		int explicit = -1;
		int at = block == null ? -1 : block.lastIndexOf('@');
		if (at > 0) {
			try { explicit = Integer.parseInt(block.substring(at + 1)); block = block.substring(0, at); }
			catch (NumberFormatException ignored) { }
		}
		if ("minecraft:granite".equals(block)) { block = "minecraft:stone"; explicit = 1; }
		else if ("minecraft:diorite".equals(block)) { block = "minecraft:stone"; explicit = 3; }
		else if ("minecraft:andesite".equals(block)) { block = "minecraft:stone"; explicit = 5; }
		if (!validId(block)) return;
		int value = explicit >= 0 ? explicit : metadata(block, serializedState);
		if (value == 0) result.add(new JsonPrimitive(block));
		else { JsonObject host = new JsonObject(); host.addProperty("block", block); host.addProperty("metadata", clamp(value, 0, 15)); result.add(host); }
	}

	private static void appendMineralogyRockHosts(JsonArray result) {
		Set<String> known = new LinkedHashSet<>();
		for (JsonElement value : result) {
			known.add(value.isJsonObject() ? text(value.getAsJsonObject(), "block", "") : value.getAsString());
		}
		for (String oreName : OreDictionary.getOreNames()) {
			if (!oreName.startsWith("stone")) continue;
			for (ItemStack stack : OreDictionary.getOres(oreName, false)) {
				Block block = Block.getBlockFromItem(stack.getItem());
				ResourceLocation id = block == null ? null : block.getRegistryName();
				if (id != null && "mineralogy".equals(id.getResourceDomain()) && known.add(id.toString())) {
					result.add(new JsonPrimitive(id.toString()));
				}
			}
		}
		REPORT.add("mineralogy_hosts_imported=" + known.stream().filter(value -> value.startsWith("mineralogy:")).count());
	}

	private static void copyMetadata(JsonObject old, JsonObject target) {
		if (old.has("metadata")) target.addProperty("metadata", clamp(old.get("metadata").getAsInt(), 0, 15));
		else if (old.has("state")) target.addProperty("metadata", metadata(text(old, "name", ""), old.get("state").getAsString()));
	}

	private static int metadata(String block, String state) {
		if (!"minecraft:stone".equals(block)) return 0;
		String lower = state == null ? "" : state.toLowerCase(java.util.Locale.ROOT);
		if (lower.contains("smooth_granite")) return 2; if (lower.contains("granite")) return 1;
		if (lower.contains("smooth_diorite")) return 4; if (lower.contains("diorite")) return 3;
		if (lower.contains("smooth_andesite")) return 6; if (lower.contains("andesite")) return 5;
		return 0;
	}

	private static void writeAtomicIfChanged(Path destination, JsonObject value) throws IOException {
		byte[] data = (GSON.toJson(value) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
		if (Files.isRegularFile(destination) && Arrays.equals(Files.readAllBytes(destination), data)) {
			REPORT.add("provider_unchanged=" + destination.getFileName()); return;
		}
		if (Files.isRegularFile(destination)) {
			Path backup = destination.resolveSibling(destination.getFileName() + ".os3-backup");
			if (!Files.exists(backup)) Files.copy(destination, backup);
		}
		Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
		Files.write(temporary, data);
		try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
		catch (IOException failure) { Files.deleteIfExists(temporary); throw failure; }
		REPORT.add("provider_written=" + destination.getFileName());
	}

	private static void writeMarker(Path destination) throws IOException {
		Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
		Files.write(temporary, ("provider_schema=4" + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
		try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
		catch (IOException failure) { Files.deleteIfExists(temporary); throw failure; }
	}

	private static void writeReport(Path destination) {
		try {
			JsonObject report = new JsonObject(); report.addProperty("format", 1); report.addProperty("idempotent", true);
			JsonArray rows = new JsonArray(); for (String row : REPORT) rows.add(new JsonPrimitive(row)); report.add("entries", rows);
			writeAtomicIfChanged(destination, report);
			writeHumanUpgradeReport(destination.resolveSibling("orespawn-upgrade-report.txt"));
		} catch (IOException failure) { LOGGER.error("Could not write OS3 migration report", failure); }
	}

	private static void writeHumanUpgradeReport(Path destination) throws IOException {
		Set<String> sources = new LinkedHashSet<>();
		Set<String> providers = new LinkedHashSet<>();
		Set<String> warnings = new LinkedHashSet<>();
		Set<String> details = new LinkedHashSet<>(REPORT);
		for (String row : REPORT) {
			String lower = row.toLowerCase(java.util.Locale.ROOT);
			if (lower.startsWith("config_source=") || lower.startsWith("os1_config_source=")
					|| lower.startsWith("resource_loaded=") || lower.startsWith("os1_config_registered=")) {
				sources.add(row);
			}
			if ((lower.startsWith("provider_written=") || lower.startsWith("provider_unchanged="))
					&& !lower.contains("migration-report")) providers.add(row);
			if (lower.contains("_failed=") || lower.contains("_rejected=")
					|| lower.contains("_ignored=") || lower.contains("_unresolved=")
					|| lower.contains("_clamped=") || lower.startsWith("resource_missing=")) {
				warnings.add(row);
			}
		}
		List<String> lines = new ArrayList<>();
		lines.add("OreSpawn 4.0.8.110021 Upgrade Report");
		lines.add("================================");
		lines.add("");
		lines.add("RESULT: Legacy OreSpawn configuration was consumed and translated for OS4.");
		lines.add("- Legacy sources read: " + sources.size());
		lines.add("- OS4 provider files written or verified: " + providers.size());
		lines.add("- Unique items requiring review: " + warnings.size());
		lines.add("");
		lines.add(warnings.isEmpty()
				? "WARNINGS: None reported during translation."
				: "WARNINGS: Review the entries containing rejected, ignored, unresolved, clamped, missing, or failed below.");
		lines.add("");
		lines.add("Detailed migration entries");
		for (String row : details) lines.add("- " + row);
		lines.add("");
		lines.add("Machine-readable details: " + destination.resolveSibling("orespawn-os3-migration-report.json").toAbsolutePath());
		lines.add("Original legacy configuration files were retained unchanged.");
		byte[] data = (String.join(System.lineSeparator(), lines) + System.lineSeparator())
				.getBytes(StandardCharsets.UTF_8);
		if (Files.isRegularFile(destination) && Arrays.equals(Files.readAllBytes(destination), data)) return;
		Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
		Files.write(temporary, data);
		try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
		catch (IOException failure) { Files.deleteIfExists(temporary); throw failure; }
	}

	private static JsonObject readObject(InputStream input) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			JsonElement value = new JsonParser().parse(reader); if (!value.isJsonObject()) throw new IOException("root is not an object");
			return value.getAsJsonObject();
		}
	}

	private static JsonElement readElement(InputStream input) throws IOException {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			JsonElement value = new JsonParser().parse(reader);
			if (value == null || value.isJsonNull()) throw new IOException("empty JSON resource");
			return value;
		}
	}

	private static boolean validModId(String value) { return value != null && value.matches("[a-z][a-z0-9_.-]{1,63}"); }
	private static boolean isModLoaded(String modId) {
		try { return Loader.isModLoaded(modId); }
		catch (RuntimeException unavailable) { return false; }
	}
	private static boolean validId(String value) { return value != null && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"); }
	private static String safe(String value) { return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_"); }
	private static JsonObject object(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonObject() ? value.getAsJsonObject(key) : new JsonObject(); }
	private static JsonArray array(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonArray() ? value.getAsJsonArray(key) : new JsonArray(); }
	private static String text(JsonObject value, String key, String fallback) { return value.has(key) ? value.get(key).getAsString() : fallback; }
	private static boolean bool(JsonObject value, String key, boolean fallback) { return value.has(key) ? value.get(key).getAsBoolean() : fallback; }
	private static int integer(JsonObject value, String key, int fallback) { return value.has(key) ? value.get(key).getAsInt() : fallback; }
	private static double decimal(JsonObject value, String key, double fallback) { return value.has(key) ? value.get(key).getAsDouble() : fallback; }
	private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
	private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

	private static IBlockState state(Object value, int metadata) {
		Block block = null;
		if (value instanceof IBlockState) return (IBlockState) value;
		if (value instanceof Block) block = (Block) value;
		else if (value instanceof ResourceLocation) block = ForgeRegistries.BLOCKS.getValue((ResourceLocation) value);
		else if (value instanceof String && validId((String) value)) block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation((String) value));
		return block == null || block == Blocks.AIR ? null : block.getStateFromMeta(clamp(metadata, 0, 15));
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static IBlockState state(Object value, Object serializedState) {
		if (serializedState instanceof Integer) return state(value, ((Integer) serializedState).intValue());
		IBlockState result = state(value, 0);
		if (result == null || !(serializedState instanceof String)) return result;
		for (String assignment : ((String) serializedState).split(",")) {
			String[] parts = assignment.trim().split("=", 2);
			if (parts.length != 2) continue;
			for (net.minecraft.block.properties.IProperty property : result.getPropertyKeys()) {
				if (!property.getName().equals(parts[0].trim())) continue;
				com.google.common.base.Optional parsed = property.parseValue(parts[1].trim());
				if (parsed.isPresent()) result = result.withProperty(property, (Comparable) parsed.get());
				break;
			}
		}
		return result;
	}

	private static final class LegacyApi implements OS3API {
		private final Map<String, IReplacementEntry> replacements = new LinkedHashMap<>();
		private final Map<String, ISpawnEntry> spawns = new LinkedHashMap<>();
		private final Map<String, String> spawnOwners = new LinkedHashMap<>();
		private final Set<String> translatedSpawns = new LinkedHashSet<>();
		private final Map<String, BuilderLogic> logics = new LinkedHashMap<>();
		private final Map<String, String> logicOwners = new LinkedHashMap<>();
		private final Set<LegacySpawnBuilder322> translated322Spawns =
				Collections.newSetFromMap(new java.util.IdentityHashMap<LegacySpawnBuilder322, Boolean>());
		private final Map<Path, List<String>> entriesByFile = new LinkedHashMap<>();
		private final Map<String, JsonObject> embedded = new LinkedHashMap<>();
		private final JsonObject embeddedReplacements = new JsonObject();
		private final OS3V2PresetStorage oldPresets = new OS3V2PresetStorage();
		private final PresetsStorage presets = new PresetsStorage();
		private String activeModId = "legacy";

		boolean hasProgrammaticRegistrations() { return !spawns.isEmpty() || !logics.isEmpty(); }

		Map<String, JsonObject> programmaticSources() {
			Map<String, JsonObject> result = new LinkedHashMap<>();
			translatedSpawns.clear();
			translated322Spawns.clear();
			for (Map.Entry<String, ISpawnEntry> registered : spawns.entrySet()) {
				if (!(registered.getValue() instanceof LegacySpawnEntry)) continue;
				LegacySpawnEntry spawn = (LegacySpawnEntry) registered.getValue();
				JsonObject migrated = spawn.toLegacyJson(this);
				if (migrated == null) {
					REPORT.add("programmatic_custom_scheduled=" + registered.getKey());
					continue;
				}
				String owner = spawnOwners.getOrDefault(registered.getKey(), activeModId);
				JsonObject source = result.computeIfAbsent(owner, ignored -> new JsonObject());
				source.addProperty("version", "2.0");
				JsonObject sourceSpawns = object(source, "spawns");
				sourceSpawns.add(registered.getKey(), migrated);
				source.add("spawns", sourceSpawns);
				translatedSpawns.add(registered.getKey());
				REPORT.add("programmatic_provider_rule=" + owner + ":" + registered.getKey());
			}
			for (Map.Entry<String, BuilderLogic> registered : logics.entrySet()) {
				if (!(registered.getValue() instanceof LegacyBuilderLogic)) continue;
				String owner = logicOwners.getOrDefault(registered.getKey(), activeModId);
				JsonObject source = result.computeIfAbsent(owner, ignored -> new JsonObject());
				source.addProperty("version", "2.0");
				JsonObject sourceSpawns = object(source, "spawns");
				((LegacyBuilderLogic) registered.getValue()).contributeProviderRules(
						owner, sourceSpawns, translated322Spawns, this);
				source.add("spawns", sourceSpawns);
			}
			return result;
		}

		@Override public int dimensionWildcard() { return Integer.MIN_VALUE; }
		@Override public int biomeWildcard() { return Integer.MIN_VALUE; }
		@Override public void registerReplacementBlock(String name, Block block) { registerReplacementBlock(name, block.getDefaultState()); }
		@Override public void registerReplacementBlock(String name, IBlockState state) {
			LegacyReplacementEntry entry = new LegacyReplacementEntry(name, Collections.singletonList(state));
			replacements.put(name, entry);
			rememberReplacement(name, entry.getEntries());
		}
		@Override public void registerFeatureGenerator(String name, IFeature feature) { addFeature(name, feature); }
		@Override public void registerFeatureGenerator(String name, Class<? extends IFeature> feature) {
			try { Constructor<? extends IFeature> constructor = feature.getDeclaredConstructor(); constructor.setAccessible(true); addFeature(name, constructor.newInstance()); }
			catch (ReflectiveOperationException failure) { throw new IllegalArgumentException(failure); }
		}
		@Override public void registerFeatureGenerator(String name, String className) {
			try { registerFeatureGenerator(name, Class.forName(className).asSubclass(IFeature.class)); }
			catch (ClassNotFoundException failure) { throw new IllegalArgumentException(failure); }
		}
		@Override public BuilderLogic getLogic(String name) {
			BuilderLogic existing = logics.get(name);
			if (existing != null) return existing;
			BuilderLogic created = new LegacyBuilderLogic(name);
			logics.put(name, created);
			logicOwners.put(name, activeModId);
			return created;
		}
		@Override public void registerLogic(BuilderLogic logic) {
			for (BuilderLogic existing : logics.values()) if (existing == logic) return;
			String key = activeModId + ":logic_" + logics.size();
			logics.put(key, logic);
			logicOwners.put(key, activeModId);
		}
		@Override public ImmutableMap<String, BuilderLogic> getSpawns() { return ImmutableMap.copyOf(logics); }
		@Override public void registerSpawns() { REPORT.add("programmatic_322_registered=" + logics.size()); }
		@Override public OreSpawnWorldGen getGenerator() { return new OreSpawnWorldGen(Collections.emptyMap(), 0L); }
		@Override public OS3V2PresetStorage getPresets() { return oldPresets; }

		@Override public void addSpawn(ISpawnEntry spawnEntry) {
			if (spawnEntry == null || spawnEntry.getSpawnName() == null) throw new IllegalArgumentException("Unnamed OS3 spawn");
			if (spawns.putIfAbsent(spawnEntry.getSpawnName(), spawnEntry) != null) throw new IllegalArgumentException("Duplicate OS3 spawn " + spawnEntry.getSpawnName());
			spawnOwners.put(spawnEntry.getSpawnName(), activeModId);
		}
		@Override public void addFeature(String featureName, IFeature feature) {
			FEATURES.addFeature(featureName, feature);
			registerSavedFeature(featureName, feature);
			REPORT.add("programmatic_feature=" + featureName);
		}
		@Override public void addReplacement(IReplacementEntry replacementEntry) {
			String name = replacementEntry.getRegistryName() == null ? "replacement_" + replacements.size() : replacementEntry.getRegistryName().toString();
			replacements.put(name, replacementEntry);
			rememberReplacement(name, replacementEntry.getEntries());
			registerSavedReplacement(replacementEntry);
		}
		@Override public Map<String, IReplacementEntry> getReplacements() { return Collections.unmodifiableMap(replacements); }
		@Override public IReplacementEntry getReplacement(String replacementName) { return replacements.get(replacementName); }
		@Override public List<ISpawnEntry> getSpawns(int dimensionID) {
			List<ISpawnEntry> result = new ArrayList<>(); for (ISpawnEntry spawn : spawns.values()) if (spawn.dimensionAllowed(dimensionID)) result.add(spawn); return result;
		}
		@Override public ISpawnEntry getSpawn(String spawnName) { return spawns.get(spawnName); }
		@Override public Map<String, ISpawnEntry> getAllSpawns() { return Collections.unmodifiableMap(spawns); }
		@Override public List<IBlockState> getDimensionDefaultReplacements(int dimensionID) {
			return Collections.singletonList(dimensionID == -1 ? Blocks.NETHERRACK.getDefaultState()
					: dimensionID == 1 ? Blocks.END_STONE.getDefaultState() : Blocks.STONE.getDefaultState());
		}
		@Override public ISpawnBuilder getSpawnBuilder() { return builder(ISpawnBuilder.class); }
		@Override public IDimensionBuilder getDimensionBuilder() { return builder(IDimensionBuilder.class); }
		@Override public IFeatureBuilder getFeatureBuilder() { return builder(IFeatureBuilder.class); }
		@Override public IBlockBuilder getBlockBuilder() { return builder(IBlockBuilder.class); }
		@Override public IBiomeBuilder getBiomeBuilder() { return builder(IBiomeBuilder.class); }
		@Override public IReplacementBuilder getReplacementBuilder() { return builder(IReplacementBuilder.class); }
		@Override public boolean featureExists(String featureName) { return FEATURES.hasFeature(featureName); }
		@Override public boolean featureExists(ResourceLocation featureName) { return FEATURES.hasFeature(featureName); }
		@Override public IFeature getFeature(String featureName) { return FEATURES.getFeature(featureName); }
		@Override public IFeature getFeature(ResourceLocation featureName) { return FEATURES.getFeature(featureName); }
		@Override public PresetsStorage copyPresets() { PresetsStorage copy = new PresetsStorage(); copy.copy(presets); return copy; }
		@Override public void loadConfigFiles() { }
		@Override public boolean hasReplacement(ResourceLocation name) { return hasReplacement(name.toString()); }
		@Override public boolean hasReplacement(String name) { return replacements.containsKey(name); }
		@Override public void mapEntryToFile(Path path, String entryName) { entriesByFile.computeIfAbsent(path, key -> new ArrayList<>()).add(entryName); }
		@Override public List<String> getSpawnsForFile(String fileName) {
			for (Map.Entry<Path, List<String>> entry : entriesByFile.entrySet()) if (entry.getKey().getFileName().toString().equals(fileName)) return entry.getValue();
			return Collections.emptyList();
		}
		@Override public Map<Path, List<String>> getSpawnsByFile() { return Collections.unmodifiableMap(entriesByFile); }

		private <T> T builder(Class<T> type) {
			return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, new LegacyBuilderHandler(this, type)));
		}

		void generate(Random random, ChunkPos pos, World world, IChunkGenerator generator, IChunkProvider provider) {
			for (Map.Entry<String, ISpawnEntry> registered : spawns.entrySet()) {
				if (translatedSpawns.contains(registered.getKey())) continue;
				ISpawnEntry spawn = registered.getValue();
				if (spawn.isEnabled() && spawn.dimensionAllowed(world.provider.getDimension())) spawn.generate(random, world, generator, provider, pos);
			}
			for (BuilderLogic value : logics.values()) if (value instanceof LegacyBuilderLogic) {
				((LegacyBuilderLogic) value).generate(random, pos, world, generator, provider,
						translated322Spawns);
			}
		}

		private void rememberReplacement(String name, List<IBlockState> states) {
			JsonArray entries = new JsonArray();
			for (IBlockState blockState : states) {
				if (blockState == null || blockState.getBlock().getRegistryName() == null) continue;
				JsonObject entry = new JsonObject();
				entry.addProperty("name", blockState.getBlock().getRegistryName().toString());
				int metadata = blockState.getBlock().getMetaFromState(blockState);
				if (metadata != 0) entry.addProperty("metadata", metadata);
				entries.add(entry);
			}
			embeddedReplacements.add(name, entries);
		}

		private void resetProgrammatic(String owner) {
			replacements.clear();
			spawns.clear();
			spawnOwners.clear();
			translatedSpawns.clear();
			logics.clear();
			logicOwners.clear();
			translated322Spawns.clear();
			entriesByFile.clear();
			embedded.clear();
			embeddedReplacements.entrySet().clear();
			activeModId = owner;
		}
	}

	private static final class LegacyBuilderHandler implements java.lang.reflect.InvocationHandler {
		private final LegacyApi api;
		private final Class<?> type;
		private final Map<String, Object> values = new LinkedHashMap<>();
		private final List<IBlockDefinition> blocks = new ArrayList<>();
		private final List<IBlockState> replacementStates = new ArrayList<>();
		private final Set<Integer> dimensionIncludes = new LinkedHashSet<>();
		private final Set<Integer> dimensionExcludes = new LinkedHashSet<>();
		private final Set<Biome> biomeIncludes = new LinkedHashSet<>();
		private final Set<Biome> biomeExcludes = new LinkedHashSet<>();
		private Object blockSource;
		private Object blockState;
		private int blockChance = 100;
		private boolean dimensionAll;
		private boolean dimensionOverworld = true;
		private boolean dimensionDenied;
		private boolean biomeAll;
		private String featureName;
		private IFeature feature;
		private JsonObject featureParameters = new JsonObject();
		private boolean featureUseDefaults;

		LegacyBuilderHandler(LegacyApi api, Class<?> type) { this.api = api; this.type = type; }

		@Override public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
			String name = method.getName();
			Object[] arguments = args == null ? new Object[0] : args;
			if (method.getDeclaringClass() == Object.class) {
				if ("toString".equals(name)) return "LegacyOS3" + type.getSimpleName();
				if ("hashCode".equals(name)) return System.identityHashCode(proxy);
				if ("equals".equals(name)) return proxy == arguments[0];
			}
			if (type == IBlockBuilder.class && name.startsWith("setFrom")) {
				captureBlock(name, arguments);
				return proxy;
			}
			if ("setChance".equals(name)) { blockChance = (Integer) arguments[0]; return proxy; }
			if (type == IFeatureBuilder.class && "setFeature".equals(name)) {
				if (arguments[0] instanceof IFeature) feature = (IFeature) arguments[0];
				else {
					featureName = arguments[0].toString();
					feature = api.getFeature(featureName);
				}
				return proxy;
			}
			if (type == IFeatureBuilder.class && "setParameter".equals(name)) {
				JsonElement parameter = arguments[1] instanceof JsonElement
						? new JsonParser().parse(arguments[1].toString()) : GSON.toJsonTree(arguments[1]);
				featureParameters.add((String) arguments[0], parameter);
				return proxy;
			}
			if (type == IFeatureBuilder.class && "setUseFeatureDefaults".equals(name)) {
				featureUseDefaults = true;
				return proxy;
			}
			if (type == IDimensionBuilder.class) {
				if ("addWhitelistEntry".equals(name)) { dimensionIncludes.add((Integer) arguments[0]); dimensionOverworld = false; }
				else if ("addBlacklistEntry".equals(name)) { dimensionExcludes.add((Integer) arguments[0]); dimensionOverworld = false; }
				else if ("setAcceptAll".equals(name)) { dimensionAll = true; dimensionOverworld = false; dimensionDenied = false; }
				else if ("setAcceptAllOverworld".equals(name)) { dimensionAll = true; dimensionOverworld = true; dimensionDenied = false; }
				else if ("setDenyAll".equals(name)) { dimensionDenied = true; dimensionAll = false; dimensionOverworld = false; }
				if (!"create".equals(name)) return proxy;
			}
			if (type == IBiomeBuilder.class) {
				if ("addWhitelistEntry".equals(name)) addBiome(biomeIncludes, arguments[0]);
				else if ("addBlacklistEntry".equals(name)) addBiome(biomeExcludes, arguments[0]);
				else if ("setAcceptAll".equals(name)) biomeAll = true;
				if (!"create".equals(name)) return proxy;
			}
			if (type == ISpawnBuilder.class && name.startsWith("addBlock")) {
				if (arguments.length == 1 && arguments[0] instanceof IBlockDefinition) blocks.add((IBlockDefinition) arguments[0]);
				else blocks.add(blockDefinition(name, arguments));
				return proxy;
			}
			if (type == IReplacementBuilder.class && "addEntry".equals(name)) {
				IBlockState state = argumentState(arguments);
				if (state != null) replacementStates.add(state);
				return proxy;
			}
			if (type == IReplacementBuilder.class && "setFromName".equals(name)) {
				String entryName = String.valueOf(arguments[0]);
				values.put(name, entryName);
				IReplacementEntry existing = api.getReplacement(entryName);
				if (existing == null) throw new IllegalArgumentException("Unknown OS3 replacement " + entryName);
				replacementStates.addAll(existing.getEntries());
				return proxy;
			}
			if ("hasEntries".equals(name)) return !replacementStates.isEmpty();
			if ("create".equals(name)) return create(proxy);
			if (name.startsWith("set")) {
				values.put(name, arguments.length == 1 ? arguments[0] : Arrays.asList(arguments));
				return proxy;
			}
			if (method.getReturnType().isInstance(proxy)) return proxy;
			return primitiveDefault(method.getReturnType());
		}

		private Object create(Object proxy) {
			if (type == IBlockBuilder.class) return new LegacyBlockDefinition(state(blockSource, blockState), blockChance);
			if (type == IDimensionBuilder.class) {
				if (dimensionDenied) return LegacyDimensionList.none();
				if (dimensionAll || (dimensionIncludes.isEmpty() && dimensionExcludes.isEmpty())) {
					return new LegacyDimensionList(Collections.emptySet(), dimensionExcludes, true, dimensionOverworld);
				}
				if (!dimensionIncludes.isEmpty()) return new LegacyDimensionList(dimensionIncludes, dimensionExcludes, false, false);
				return new LegacyDimensionList(Collections.emptySet(), dimensionExcludes, true, false);
			}
			if (type == IBiomeBuilder.class) return biomeAll
					? LegacyBiomeLocation.all()
					: new LegacyBiomeLocation(biomeIncludes, biomeExcludes,
							biomeIncludes.isEmpty() && !biomeExcludes.isEmpty());
			if (type == IFeatureBuilder.class) {
				JsonObject merged = feature == null || feature.getDefaultParameters() == null
						? new JsonObject() : new JsonParser().parse(feature.getDefaultParameters().toString()).getAsJsonObject();
				if (!featureUseDefaults) for (Map.Entry<String, JsonElement> parameter : featureParameters.entrySet()) {
					merged.add(parameter.getKey(), new JsonParser().parse(parameter.getValue().toString()));
				}
				return new LegacyFeatureEntry(featureName, feature, merged);
			}
			if (type == IReplacementBuilder.class) {
				String name = stringValue("setName", stringValue("setFromName", "replacement_" + api.replacements.size()));
				return new LegacyReplacementEntry(name, replacementStates);
			}
			if (type == ISpawnBuilder.class) {
				String name = stringValue("setName", api.activeModId + ":spawn_" + api.spawns.size());
				LegacySpawnEntry result = new LegacySpawnEntry(name,
						(IDimensionList) values.getOrDefault("setDimensions", LegacyDimensionList.all()),
						(BiomeLocation) values.getOrDefault("setBiomes", LegacyBiomeLocation.all()),
						booleanValue("setEnabled", false), booleanValue("setRetrogen", false),
						(IReplacementEntry) values.get("setReplacement"),
						(IFeatureEntry) values.get("setFeature"), new LegacyBlockList(blocks));
				return result;
			}
			return proxy;
		}

		private void captureBlock(String method, Object[] arguments) {
			blockSource = arguments.length == 0 ? null : arguments[0];
			blockState = arguments.length > 1 && !method.endsWith("WithChance") ? arguments[1] : null;
			if (method.endsWith("WithChance")) {
				blockChance = (Integer) arguments[arguments.length - 1];
				blockState = arguments.length > 2 ? arguments[1] : null;
			}
		}

		private LegacyBlockDefinition blockDefinition(String method, Object[] arguments) {
			int chance = method.contains("WithChance") ? (Integer) arguments[arguments.length - 1] : 100;
			Object serialized = method.contains("WithChance")
					? (arguments.length > 2 ? arguments[1] : null)
					: (arguments.length > 1 ? arguments[1] : null);
			return new LegacyBlockDefinition(state(arguments[0], serialized), chance);
		}

		private IBlockState argumentState(Object[] arguments) {
			return state(arguments.length == 0 ? null : arguments[0], arguments.length > 1 ? arguments[1] : null);
		}

		private static void addBiome(Set<Biome> target, Object value) {
			Biome biome = value instanceof Biome ? (Biome) value
					: ForgeRegistries.BIOMES.getValue(value instanceof ResourceLocation
							? (ResourceLocation) value : new ResourceLocation(String.valueOf(value)));
			if (biome != null) target.add(biome);
		}

		private String stringValue(String key, String fallback) { Object value = values.get(key); return value == null ? fallback : String.valueOf(value); }
		private boolean booleanValue(String key, boolean fallback) { Object value = values.get(key); return value instanceof Boolean ? (Boolean) value : fallback; }
	}

	private static final class LegacyBlockDefinition implements IBlockDefinition {
		private final IBlockState block; private final int chance;
		LegacyBlockDefinition(IBlockState block, int chance) { this.block = block; this.chance = Math.max(0, chance); }
		@Override public IBlockState getBlock() { return block; }
		@Override public int getChance() { return chance; }
		@Override public boolean isValid() { return block != null && block.getBlock() != Blocks.AIR; }
	}

	private static final class LegacyBlockList implements IBlockList {
		private final List<IBlockDefinition> blocks = new ArrayList<>(); private int total;
		LegacyBlockList(List<IBlockDefinition> values) { for (IBlockDefinition value : values) addBlock(value); }
		@Override public void addBlock(IBlockDefinition block) { if (block != null && block.isValid()) { blocks.add(block); total += Math.max(0, block.getChance()); } }
		@Override public IBlockState getRandomBlock(Random random) {
			if (blocks.isEmpty()) return null; int selected = random.nextInt(Math.max(1, total));
			for (IBlockDefinition block : blocks) { selected -= Math.max(0, block.getChance()); if (selected < 0) return block.getBlock(); }
			return blocks.get(blocks.size() - 1).getBlock();
		}
		@Override public void startNewSpawn() { }
		@Override public void dump() { }
		@Override public int count() { return blocks.size(); }
	}

	private static final class LegacyDimensionList implements IDimensionList {
		private final Set<Integer> allowed;
		private final Set<Integer> denied;
		private final boolean all;
		private final boolean overworldOnly;
		private LegacyDimensionList(Set<Integer> allowed, Set<Integer> denied, boolean all, boolean overworldOnly) {
			this.allowed = Collections.unmodifiableSet(new LinkedHashSet<>(allowed));
			this.denied = Collections.unmodifiableSet(new LinkedHashSet<>(denied));
			this.all = all;
			this.overworldOnly = overworldOnly;
		}
		static LegacyDimensionList all() { return new LegacyDimensionList(Collections.emptySet(), Collections.emptySet(), true, true); }
		static LegacyDimensionList none() { return new LegacyDimensionList(Collections.emptySet(), Collections.emptySet(), false, false); }
		static LegacyDimensionList only(int id) { return only(Collections.singleton(id)); }
		static LegacyDimensionList only(Set<Integer> ids) { return new LegacyDimensionList(ids, Collections.emptySet(), false, false); }
		@Override public boolean matches(int dimensionId) {
			if (denied.contains(dimensionId)) return false;
			if (all) return !overworldOnly || (dimensionId != -1 && dimensionId != 1);
			return allowed.contains(dimensionId);
		}
		@Override public JsonObject serialize() {
			JsonObject result = new JsonObject();
			result.addProperty("accept_all", all);
			result.addProperty("overworld_only", overworldOnly);
			JsonArray ids = new JsonArray(); for (Integer id : allowed) ids.add(new JsonPrimitive(id)); result.add("includes", ids);
			JsonArray excluded = new JsonArray(); for (Integer id : denied) excluded.add(new JsonPrimitive(id)); result.add("excludes", excluded);
			return result;
		}
	}

	private static final class LegacyBiomeLocation implements BiomeLocation {
		private final Set<Biome> included;
		private final Set<Biome> excluded;
		private final Set<String> includedTypes;
		private final Set<String> excludedTypes;
		private final boolean all;
		LegacyBiomeLocation(Set<Biome> included, Set<Biome> excluded, boolean all) {
			this(included, excluded, Collections.emptySet(), Collections.emptySet(), all);
		}
		LegacyBiomeLocation(Set<Biome> included, Set<Biome> excluded, Set<String> includedTypes,
				Set<String> excludedTypes, boolean all) {
			this.included = Collections.unmodifiableSet(new LinkedHashSet<>(included));
			this.excluded = Collections.unmodifiableSet(new LinkedHashSet<>(excluded));
			this.includedTypes = Collections.unmodifiableSet(new LinkedHashSet<>(includedTypes));
			this.excludedTypes = Collections.unmodifiableSet(new LinkedHashSet<>(excludedTypes));
			this.all = all;
		}
		static LegacyBiomeLocation all() {
			return new LegacyBiomeLocation(Collections.emptySet(), Collections.emptySet(), true);
		}
		@Override public boolean matches(Biome biome) {
			if (excluded.contains(biome) || matchesType(biome, excludedTypes)) return false;
			return all || included.contains(biome) || matchesType(biome, includedTypes);
		}
		@Override public JsonElement serialize() {
			JsonObject result = new JsonObject();
			JsonArray includes = new JsonArray();
			for (Biome biome : included) if (biome.getRegistryName() != null) includes.add(new JsonPrimitive(biome.getRegistryName().toString()));
			for (String type : includedTypes) includes.add(new JsonPrimitive(type));
			JsonArray excludes = new JsonArray();
			for (Biome biome : excluded) if (biome.getRegistryName() != null) excludes.add(new JsonPrimitive(biome.getRegistryName().toString()));
			for (String type : excludedTypes) excludes.add(new JsonPrimitive(type));
			result.add("includes", includes);
			result.add("excludes", excludes);
			return result;
		}
		private static boolean matchesType(Biome biome, Set<String> names) {
			for (String name : names) {
				if (net.minecraftforge.common.BiomeDictionary.isBiomeOfType(biome,
						net.minecraftforge.common.BiomeDictionary.Type.getType(name))) return true;
			}
			return false;
		}
	}

	private static final class LegacyFeatureEntry implements IFeatureEntry {
		private final String name; private final IFeature feature; private final JsonObject parameters;
		LegacyFeatureEntry(String name, IFeature feature, JsonObject parameters) { this.name = name; this.feature = feature; this.parameters = parameters; }
		@Override public IFeature getFeature() { return feature; }
		@Override public String getFeatureName() { return name; }
		@Override public JsonObject getFeatureParameters() { return parameters; }
		@Override public void setParameter(String key, String value) { parameters.addProperty(key, value); }
		@Override public void setParameter(String key, int value) { parameters.addProperty(key, value); }
		@Override public void setParameter(String key, boolean value) { parameters.addProperty(key, value); }
		@Override public void setParameter(String key, float value) { parameters.addProperty(key, value); }
	}

	private static final class LegacyReplacementEntry implements IReplacementEntry {
		private final OreSpawnBlockMatcher matcher; private final List<IBlockState> entries;
		private ResourceLocation registryName;
		LegacyReplacementEntry(String name, List<IBlockState> entries) {
			this.entries = Collections.unmodifiableList(new ArrayList<>(entries)); this.matcher = new OreSpawnBlockMatcher(entries);
			if (validId(name)) setRegistryName(new ResourceLocation(name)); else setRegistryName(new ResourceLocation("legacy", safe(name)));
		}
		@Override public IReplacementEntry setRegistryName(ResourceLocation name) { this.registryName = name; return this; }
		@Override public ResourceLocation getRegistryName() { return registryName; }
		@Override public OreSpawnBlockMatcher getMatcher() { return matcher; }
		@Override public List<IBlockState> getEntries() { return entries; }
	}

	private static final class SavedFeature implements IFeature {
		@Override public void generate(World world, IChunkGenerator generator,
				IChunkProvider provider, GeneratorParameters parameters) { }
		@Override public void generate(World world, IChunkGenerator generator,
				IChunkProvider provider, ISpawnEntry spawn, ChunkPos pos) { }
		@Override public void setRandom(Random random) { }
		@Override public JsonObject getDefaultParameters() { return new JsonObject(); }
	}

	private static final class SavedFeatureAlias implements IFeature {
		private final IFeature delegate;
		SavedFeatureAlias(IFeature delegate) { this.delegate = delegate; }
		@Override public void generate(World world, IChunkGenerator generator,
				IChunkProvider provider, GeneratorParameters parameters) {
			delegate.generate(world, generator, provider, parameters);
		}
		@Override public void generate(World world, IChunkGenerator generator,
				IChunkProvider provider, ISpawnEntry spawn, ChunkPos pos) {
			delegate.generate(world, generator, provider, spawn, pos);
		}
		@Override public void setRandom(Random random) { delegate.setRandom(random); }
		@Override public JsonObject getDefaultParameters() { return delegate.getDefaultParameters(); }
	}

	private static final class LegacySpawnEntry implements ISpawnEntry {
		private final String name; private final IDimensionList dimensions; private final BiomeLocation biomes;
		private final boolean enabled, retrogen; private final IReplacementEntry replacement; private final IFeatureEntry feature; private final IBlockList blocks;
		LegacySpawnEntry(String name, IDimensionList dimensions, BiomeLocation biomes, boolean enabled,
				boolean retrogen, IReplacementEntry replacement, IFeatureEntry feature, IBlockList blocks) {
			this.name = name; this.dimensions = dimensions; this.biomes = biomes; this.enabled = enabled; this.retrogen = retrogen;
			this.replacement = replacement; this.feature = feature; this.blocks = blocks;
		}
		@Override public boolean isEnabled() { return enabled; }
		@Override public boolean isRetrogen() { return retrogen; }
		@Override public String getSpawnName() { return name; }
		@Override public boolean dimensionAllowed(int dimension) { return dimensions == null || dimensions.matches(dimension); }
		@Override public boolean biomeAllowed(ResourceLocation biome) { Biome value = ForgeRegistries.BIOMES.getValue(biome); return value != null && biomeAllowed(value); }
		@Override public boolean biomeAllowed(Biome biome) { return biomes == null || biomes.matches(biome); }
		@Override public IFeatureEntry getFeature() { return feature; }
		@Override public OreSpawnBlockMatcher getMatcher() { return replacement == null ? new OreSpawnBlockMatcher(Blocks.STONE.getDefaultState()) : replacement.getMatcher(); }
		@Override public IBlockList getBlocks() { return blocks; }
		@Override public IDimensionList getDimensions() { return dimensions; }
		@Override public BiomeLocation getBiomes() { return biomes; }
		@Override public void generate(Random random, World world, IChunkGenerator generator, IChunkProvider provider, ChunkPos pos) {
			if (feature == null || feature.getFeature() == null) return; feature.getFeature().setRandom(random);
			feature.getFeature().generate(world, generator, provider, this, pos);
		}

		JsonObject toLegacyJson(LegacyApi api) {
			if (feature == null || !(blocks instanceof LegacyBlockList)) return null;
			String featureName = feature.getFeatureName();
			if (featureName == null) return null;
			String normalized = normalizePattern(featureName.contains(":")
					? featureName.substring(featureName.indexOf(':') + 1) : featureName);
			if (!("default".equals(normalized) || "vein".equals(normalized)
					|| "normal_cloud".equals(normalized) || "precision".equals(normalized)
					|| "clusters".equals(normalized) || "underfluids".equals(normalized))) return null;
			if (!(dimensions instanceof LegacyDimensionList)) return null;
			LegacyDimensionList dimensionList = (LegacyDimensionList) dimensions;
			if ((!dimensionList.all && dimensionList.allowed.isEmpty())
					|| (dimensionList.all && !dimensionList.overworldOnly)
					|| !dimensionList.denied.isEmpty()) return null;

			JsonObject result = new JsonObject();
			result.addProperty("enabled", enabled);
			result.addProperty("retrogen", retrogen);
			result.addProperty("feature", normalized);
			result.addProperty("replaces", replacement == null || replacement.getRegistryName() == null
					? "default" : replacement.getRegistryName().toString());
			JsonArray dimensionIds = new JsonArray();
			if (!dimensionList.all) for (Integer id : dimensionList.allowed) dimensionIds.add(new JsonPrimitive(id));
			result.add("dimensions", dimensionIds);
			JsonObject biomeFilter = new JsonObject();
			JsonArray included = new JsonArray();
			JsonArray excluded = new JsonArray();
			if (biomes instanceof LegacyBiomeLocation) {
				LegacyBiomeLocation location = (LegacyBiomeLocation) biomes;
				for (Biome biome : location.included) if (biome.getRegistryName() != null) included.add(new JsonPrimitive(biome.getRegistryName().toString()));
				for (String type : location.includedTypes) included.add(new JsonPrimitive(type));
				for (Biome biome : location.excluded) if (biome.getRegistryName() != null) excluded.add(new JsonPrimitive(biome.getRegistryName().toString()));
				for (String type : location.excludedTypes) excluded.add(new JsonPrimitive(type));
			}
			biomeFilter.add("includes", included);
			biomeFilter.add("excludes", excluded);
			result.add("biomes", biomeFilter);
			result.add("parameters", new JsonParser().parse(feature.getFeatureParameters().toString()));
			JsonArray outputs = new JsonArray();
			for (IBlockDefinition definition : ((LegacyBlockList) blocks).blocks) {
				IBlockState state = definition.getBlock();
				if (state == null || state.getBlock().getRegistryName() == null) continue;
				JsonObject output = new JsonObject();
				output.addProperty("name", state.getBlock().getRegistryName().toString());
				int metadata = state.getBlock().getMetaFromState(state);
				if (metadata != 0) output.addProperty("metadata", metadata);
				output.addProperty("chance", definition.getChance());
				outputs.add(output);
			}
			if (outputs.size() == 0) return null;
			result.add("blocks", outputs);
			if (replacement != null && replacement.getRegistryName() != null) {
				api.rememberReplacement(replacement.getRegistryName().toString(), replacement.getEntries());
			}
			return result;
		}
	}

	private static final class LegacyFlags {
		private static final Pattern PROPERTY = Pattern.compile("^[BIS]:\\\"?([^\\\"=]+)\\\"?=(.*)$");
		boolean replaceVanilla;
		boolean disableStandard;
		boolean retrogen;
		boolean forceRetrogen;
		boolean flatBedrock;
		boolean retrogenBedrock;
		int bedrockLayers = 1;
		final List<String> nonstandardHosts = new ArrayList<>();

		static LegacyFlags read(Path path) {
			LegacyFlags result = new LegacyFlags();
			if (!Files.isRegularFile(path)) return result;
			try {
				for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
					Matcher match = PROPERTY.matcher(raw.trim());
					if (!match.matches()) continue;
					String key = match.group(1).trim(); String value = match.group(2).trim();
					if ("Replace Vanilla Oregen".equals(key)) result.replaceVanilla = Boolean.parseBoolean(value);
					else if ("disable_standard_ore_generation".equals(key)) result.disableStandard = Boolean.parseBoolean(value);
					else if ("Retrogen".equals(key)) result.retrogen = Boolean.parseBoolean(value);
					else if ("Force Retrogen".equals(key) || "force_ore_generation".equals(key)) result.forceRetrogen |= Boolean.parseBoolean(value);
					else if ("Flatten Bedrock".equals(key)) result.flatBedrock = Boolean.parseBoolean(value);
					else if ("Retrogen Flat Bedrock".equals(key)) result.retrogenBedrock = Boolean.parseBoolean(value);
					else if ("Bedrock Thickness".equals(key)) {
						try { result.bedrockLayers = clamp(Integer.parseInt(value), 1, 4); }
						catch (NumberFormatException ignored) { REPORT.add("legacy_flag_clamped=Bedrock Thickness:" + value); }
					} else if ("nonstandard_spawn_blocks".equals(key)) {
						for (String host : value.split("[;,]")) if (!host.trim().isEmpty()) result.nonstandardHosts.add(host.trim());
					} else if ("ignore_missing_blocks".equals(key)) {
						REPORT.add("legacy_flag_preserved=ignore_missing_blocks:" + value);
					}
				}
				REPORT.add("legacy_flags=manage_vanilla:" + result.replaceVanilla
						+ ",suppress_all:" + result.disableStandard + ",retrogen:" + result.retrogen
						+ ",force:" + result.forceRetrogen + ",flat_bedrock:" + result.flatBedrock
						+ ",retrogen_bedrock:" + result.retrogenBedrock + ",layers:" + result.bedrockLayers);
			} catch (IOException failure) {
				REPORT.add("legacy_flags_failed=" + failure.getClass().getSimpleName());
			}
			return result;
		}
	}

	private static Object primitiveDefault(Class<?> type) {
		if (!type.isPrimitive()) return null; if (type == boolean.class) return false; if (type == char.class) return '\0';
		if (type == byte.class) return (byte) 0; if (type == short.class) return (short) 0; if (type == int.class) return 0;
		if (type == long.class) return 0L; if (type == float.class) return 0F; return 0D;
	}

	private static final class LegacyBuilderLogic implements BuilderLogic {
		private final String name; private final Map<Integer, DimensionBuilder> dimensions = new LinkedHashMap<>();
		LegacyBuilderLogic(String name) { this.name = name; }
		@Override public DimensionBuilder newDimensionBuilder(String value) {
			String normalized = value == null ? "+" : value.trim().toLowerCase(java.util.Locale.ROOT);
			if ("+".equals(normalized)) return LegacyDimensionBuilder322.overworldOnly();
			if ("overworld".equals(normalized) || "the_overworld".equals(normalized)) return newDimensionBuilder(0);
			if ("nether".equals(normalized) || "the_nether".equals(normalized)) return newDimensionBuilder(-1);
			if ("end".equals(normalized) || "the_end".equals(normalized)) return newDimensionBuilder(1);
			return newDimensionBuilder(Integer.parseInt(normalized));
		}
		@Override public DimensionBuilder newDimensionBuilder(int id) { return LegacyDimensionBuilder322.specific(id); }
		@Override public DimensionBuilder newDimensionBuilder() { return LegacyDimensionBuilder322.allDimensions(); }
		@Override public BuilderLogic create(DimensionBuilder... values) { for (DimensionBuilder value : values) if (value instanceof LegacyDimensionBuilder322) dimensions.put(((LegacyDimensionBuilder322) value).id, value); return this; }
		@Override public DimensionBuilder getDimension(String value) {
			String normalized = value == null ? "+" : value.trim().toLowerCase(java.util.Locale.ROOT);
			if ("+".equals(normalized)) return getDimension(Integer.MIN_VALUE);
			if ("overworld".equals(normalized) || "the_overworld".equals(normalized)) return getDimension(0);
			if ("nether".equals(normalized) || "the_nether".equals(normalized)) return getDimension(-1);
			if ("end".equals(normalized) || "the_end".equals(normalized)) return getDimension(1);
			return getDimension(Integer.parseInt(normalized));
		}
		@Override public DimensionBuilder getDimension(int id) { return dimensions.get(id); }
		@Override public ImmutableMap<Integer, DimensionBuilder> getAllDimensions() { return ImmutableMap.copyOf(dimensions); }
		void contributeProviderRules(String owner, JsonObject target,
				Set<LegacySpawnBuilder322> translated, LegacyApi api) {
			int ordinal = 0;
			for (DimensionBuilder dimension : dimensions.values()) {
				if (!(dimension instanceof LegacyDimensionBuilder322)) continue;
				LegacyDimensionBuilder322 legacyDimension = (LegacyDimensionBuilder322) dimension;
				for (SpawnBuilder spawn : dimension.getAllSpawns()) {
					if (!(spawn instanceof LegacySpawnBuilder322)) continue;
					LegacySpawnBuilder322 legacySpawn = (LegacySpawnBuilder322) spawn;
					String ruleName = legacySpawn.name == null || legacySpawn.name.trim().isEmpty()
							? safe(name) + "_spawn_" + ordinal : safe(legacySpawn.name);
					while (target.has(ruleName)) ruleName = safe(legacySpawn.name) + "_" + (++ordinal);
					String replacementName = owner + ":programmatic/" + safe(name) + "/" + ruleName;
					JsonObject migrated = legacySpawn.toLegacyJson(legacyDimension, replacementName, api);
					if (migrated == null) {
						REPORT.add("programmatic_322_custom_scheduled=" + owner + ":" + name + ":" + ruleName);
					} else {
						target.add(ruleName, migrated);
						translated.add(legacySpawn);
						REPORT.add("programmatic_322_provider_rule=" + owner + ":" + name + ":" + ruleName);
					}
					ordinal++;
				}
			}
		}
		void generate(Random random, ChunkPos pos, World world, IChunkGenerator generator,
				IChunkProvider provider, Set<LegacySpawnBuilder322> translated) {
			int id = world.provider.getDimension();
			for (DimensionBuilder dimension : dimensions.values()) {
				if (!(dimension instanceof LegacyDimensionBuilder322)
						|| !((LegacyDimensionBuilder322) dimension).matches(id)) continue;
				for (SpawnBuilder spawn : dimension.getAllSpawns()) {
					if (!(spawn instanceof LegacySpawnBuilder322) || !spawn.enabled() || (spawn.hasExtendedDimensions() && !spawn.extendedDimensionsMatch(id))) continue;
					LegacySpawnBuilder322 legacy = (LegacySpawnBuilder322) spawn;
					if (translated.contains(legacy)) continue;
					IFeature feature = legacy.feature == null ? null : legacy.feature.getGenerator(); if (feature == null) continue;
					feature.setRandom(random); feature.generate(world, generator, provider,
							new GeneratorParameters(pos, legacy.ores, legacy.replacements, legacy.biomes, legacy.feature.getParameters()));
				}
			}
		}
		@Override public String toString() { return "LegacyBuilderLogic[" + name + "]"; }
	}

	private static final class LegacyDimensionBuilder322 implements DimensionBuilder {
		private final int id;
		private final boolean overworldOnly;
		private final boolean allDimensions;
		private final List<SpawnBuilder> spawns = new ArrayList<>();
		private LegacyDimensionBuilder322(int id, boolean overworldOnly, boolean allDimensions) {
			this.id = id; this.overworldOnly = overworldOnly; this.allDimensions = allDimensions;
		}
		static LegacyDimensionBuilder322 specific(int id) { return new LegacyDimensionBuilder322(id, false, false); }
		static LegacyDimensionBuilder322 overworldOnly() { return new LegacyDimensionBuilder322(Integer.MIN_VALUE, true, false); }
		static LegacyDimensionBuilder322 allDimensions() { return new LegacyDimensionBuilder322(Integer.MIN_VALUE, false, true); }
		boolean matches(int dimension) {
			return allDimensions || (overworldOnly ? dimension != -1 && dimension != 1 : dimension == id);
		}
		@Override public SpawnBuilder newSpawnBuilder(String name) { return new LegacySpawnBuilder322(name); }
		@Override public DimensionBuilder create(SpawnBuilder... values) { spawns.addAll(Arrays.asList(values)); return this; }
		@Override public ImmutableList<SpawnBuilder> getSpawnByName(String name) { ImmutableList.Builder<SpawnBuilder> result = ImmutableList.builder(); for (SpawnBuilder spawn : spawns) if (spawn instanceof LegacySpawnBuilder322 && java.util.Objects.equals(name, ((LegacySpawnBuilder322) spawn).name)) result.add(spawn); return result.build(); }
		@Override public ImmutableList<SpawnBuilder> getAllSpawns() { return ImmutableList.copyOf(spawns); }
	}

	private static final class LegacySpawnBuilder322 implements SpawnBuilder {
		private final String name; private BiomeLocation biomes = LegacyBiomeLocation.all(); private FeatureBuilder feature;
		private List<IBlockState> replacements = Collections.singletonList(Blocks.STONE.getDefaultState()); private final OreList ores = new OreList();
		private List<OreBuilder> oreBuilders = new ArrayList<>(); private boolean enabled = true, retrogen; private int[] include, exclude;
		LegacySpawnBuilder322(String name) { this.name = name; }
		@Override public FeatureBuilder newFeatureBuilder(String featureName) { return new LegacyFeatureBuilder322(featureName); }
		@Override public BiomeBuilder newBiomeBuilder() { return new LegacyBiomeBuilder322(); }
		@Override public OreBuilder newOreBuilder() { return new LegacyOreBuilder(); }
		@Override public SpawnBuilder create(BiomeBuilder biomes, FeatureBuilder feature, List<IBlockState> replacements, OreBuilder... ores) {
			return create(biomes, feature, replacements, null, ores);
		}
		@Override public SpawnBuilder create(BiomeBuilder biomes, FeatureBuilder feature, List<IBlockState> replacements, JsonObject exDim, OreBuilder... values) {
			this.biomes = biomes.getBiomes(); this.feature = feature; this.replacements = new ArrayList<>(replacements);
			this.oreBuilders = Arrays.asList(values); this.ores.build(oreBuilders);
			if (exDim != null) { this.include = ints(exDim.get("includes")); this.exclude = ints(exDim.get("excludes")); }
			return this;
		}
		@Override public BiomeLocation getBiomes() { return biomes; }
		@Override public ImmutableList<OreBuilder> getOres() { return ImmutableList.copyOf(oreBuilders); }
		@Override public ImmutableList<IBlockState> getReplacementBlocks() { return ImmutableList.copyOf(replacements); }
		@Override public FeatureBuilder getFeatureGen() { return feature; }
		@Override public OreBuilder getRandomOre(Random random) { return ores.getRandomOre(random); }
		@Override public OreList getOreSpawns() { return ores; }
		@Override public boolean enabled() { return enabled; }
		@Override public void enabled(boolean value) { enabled = value; }
		@Override public boolean retrogen() { return retrogen; }
		@Override public void retrogen(boolean value) { retrogen = value; }
		@Override public boolean hasExtendedDimensions() { return include != null || exclude != null; }
		@Override public boolean extendedDimensionsMatch(int dimension) { return (include == null || include.length == 0 || contains(include, dimension)) && (exclude == null || !contains(exclude, dimension)); }

		JsonObject toLegacyJson(LegacyDimensionBuilder322 dimension, String replacementName, LegacyApi api) {
			if (feature == null || feature.getFeatureName() == null || oreBuilders.isEmpty()) return null;
			String featureName = feature.getFeatureName();
			String normalized = normalizePattern(featureName.contains(":")
					? featureName.substring(featureName.indexOf(':') + 1) : featureName);
			if (!("default".equals(normalized) || "vein".equals(normalized)
					|| "normal_cloud".equals(normalized) || "precision".equals(normalized)
					|| "clusters".equals(normalized) || "underfluids".equals(normalized))) return null;

			JsonArray dimensions = representableDimensions(dimension);
			if (dimensions == null) return null;
			JsonObject result = new JsonObject();
			result.addProperty("enabled", enabled);
			result.addProperty("retrogen", retrogen);
			result.addProperty("feature", normalized);
			result.addProperty("replaces", replacementName);
			result.add("dimensions", dimensions);
			result.add("biomes", biomes == null ? LegacyBiomeLocation.all().serialize() : biomes.serialize());
			result.add("parameters", feature.getParameters() == null
					? new JsonObject() : new JsonParser().parse(feature.getParameters().toString()));

			JsonArray outputs = new JsonArray();
			for (OreBuilder ore : oreBuilders) {
				IBlockState state = ore.getOre();
				if (state == null || state.getBlock().getRegistryName() == null) continue;
				JsonObject output = new JsonObject();
				output.addProperty("name", state.getBlock().getRegistryName().toString());
				int metadata = state.getBlock().getMetaFromState(state);
				if (metadata != 0) output.addProperty("metadata", metadata);
				output.addProperty("chance", Math.max(0, ore.getChance()));
				outputs.add(output);
			}
			if (outputs.size() == 0) return null;
			result.add("blocks", outputs);
			api.rememberReplacement(replacementName, replacements);
			return result;
		}

		private JsonArray representableDimensions(LegacyDimensionBuilder322 dimension) {
			Set<Integer> selected = new LinkedHashSet<>();
			if (!dimension.allDimensions && !dimension.overworldOnly) {
				if (!extendedDimensionsMatch(dimension.id)) return null;
				selected.add(dimension.id);
			} else if (include != null && include.length > 0) {
				for (int id : include) {
					if (dimension.overworldOnly && (id == -1 || id == 1)) continue;
					if (exclude == null || !contains(exclude, id)) selected.add(id);
				}
				if (selected.isEmpty()) return null;
			} else if (dimension.overworldOnly) {
				for (int denied : exclude == null ? new int[0] : exclude) {
					if (denied != -1 && denied != 1) return null;
				}
				return new JsonArray();
			} else {
				// OS4's declarative selector cannot losslessly express every registered
				// dimension minus an open-ended blacklist. Keep this on the one legacy scheduler.
				return null;
			}
			JsonArray result = new JsonArray();
			for (Integer id : selected) result.add(new JsonPrimitive(id));
			return result;
		}
	}

	private static final class LegacyFeatureBuilder322 implements FeatureBuilder {
		private String name; private IFeature feature; private JsonObject parameters = new JsonObject();
		LegacyFeatureBuilder322(String name) { this.name = name; feature = FEATURES.getFeature(name); }
		@Override public FeatureBuilder setGenerator(String name) { this.name = name; feature = FEATURES.getFeature(name); return this; }
		@Override public FeatureBuilder addParameter(String key, boolean value) { parameters.addProperty(key, value); return this; }
		@Override public FeatureBuilder addParameter(String key, int value) { parameters.addProperty(key, value); return this; }
		@Override public FeatureBuilder addParameter(String key, float value) { parameters.addProperty(key, value); return this; }
		@Override public FeatureBuilder addParameter(String key, String value) { parameters.addProperty(key, value); return this; }
		@Override public FeatureBuilder setParameters(JsonObject value) { parameters = new JsonParser().parse(value.toString()).getAsJsonObject(); return this; }
		@Override public FeatureBuilder setDefaultParameters() { if (feature != null) parameters = new JsonParser().parse(feature.getDefaultParameters().toString()).getAsJsonObject(); return this; }
		@Override public IFeature getGenerator() { return feature; }
		@Override public JsonObject getParameters() { return parameters; }
		@Override public String getFeatureName() { return name; }
	}

	private static final class LegacyBiomeBuilder322 implements BiomeBuilder {
		private final Set<Biome> included = new LinkedHashSet<>(), excluded = new LinkedHashSet<>();
		private final Set<String> includedTypes = new LinkedHashSet<>(), excludedTypes = new LinkedHashSet<>();
		private BiomeLocation value;
		@Override public BiomeBuilder whitelistBiome(Biome biome) { included.add(biome); return this; }
		@Override public BiomeBuilder whitelistBiomeByName(String name) { Biome biome = validId(name) ? ForgeRegistries.BIOMES.getValue(new ResourceLocation(name)) : null; if (biome != null) included.add(biome); return this; }
		@Override public BiomeBuilder whitelistBiomeByDictionary(String type) { includedTypes.add(type.toUpperCase(java.util.Locale.ROOT)); return this; }
		@Override public BiomeBuilder blacklistBiome(Biome biome) { excluded.add(biome); return this; }
		@Override public BiomeBuilder blacklistBiomeByName(String name) { Biome biome = validId(name) ? ForgeRegistries.BIOMES.getValue(new ResourceLocation(name)) : null; if (biome != null) excluded.add(biome); return this; }
		@Override public BiomeBuilder blacklistBiomeByDictionary(String type) { excludedTypes.add(type.toUpperCase(java.util.Locale.ROOT)); return this; }
		@Override public BiomeBuilder setFromBiomeLocation(BiomeLocation biomes) { value = biomes; return this; }
		@Override public BiomeLocation getBiomes() { return value == null
				? new LegacyBiomeLocation(included, excluded, includedTypes, excludedTypes,
						included.isEmpty() && includedTypes.isEmpty()) : value; }
	}

	private static final class LegacyOreBuilder implements OreBuilder {
		private IBlockState ore; private int chance = 100;
		@Override public OreBuilder setOre(String name) { ore = state(name, 0); return this; }
		@Override public OreBuilder setOre(String name, String serializedState) { ore = state(name, metadata(name, serializedState)); return this; }
		@Override public OreBuilder setOre(String name, int metadata) { ore = state(name, metadata); return this; }
		@Override public OreBuilder setOre(Block block) { ore = block.getDefaultState(); return this; }
		@Override public OreBuilder setOre(Block block, String serializedState) { ore = state(block, metadata(block.getRegistryName().toString(), serializedState)); return this; }
		@Override public OreBuilder setOre(net.minecraft.item.Item item, int metadata) { ore = state(Block.getBlockFromItem(item), metadata); return this; }
		@Override public OreBuilder setOre(net.minecraft.item.ItemStack item) { ore = state(Block.getBlockFromItem(item.getItem()), item.getMetadata()); return this; }
		@Override public OreBuilder setOre(String name, String serializedState, int chance) { setOre(name, serializedState); return setChance(chance); }
		@Override public OreBuilder setOre(String name, int metadata, int chance) { setOre(name, metadata); return setChance(chance); }
		@Override public OreBuilder setOre(Block block, String serializedState, int chance) { setOre(block, serializedState); return setChance(chance); }
		@Override public OreBuilder setOre(net.minecraft.item.Item item, int metadata, int chance) { setOre(item, metadata); return setChance(chance); }
		@Override public OreBuilder setOre(net.minecraft.item.ItemStack item, int chance) { setOre(item); return setChance(chance); }
		@Override public OreBuilder setChance(int value) { chance = value; return this; }
		@Override public IBlockState getOre() { return ore; }
		@Override public int getChance() { return chance; }
	}

	private static int[] ints(JsonElement element) { if (element == null || !element.isJsonArray()) return null; int[] result = new int[element.getAsJsonArray().size()]; for (int i = 0; i < result.length; i++) result[i] = element.getAsJsonArray().get(i).getAsInt(); return result; }
	private static boolean contains(int[] values, int target) { for (int value : values) if (value == target) return true; return false; }
}
