package zone.moddev.mc.orespawn.worldgen;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.function.BiFunction;

import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Imports old OreSpawn 2.0 and Mineralogy 6 profiles without modifying their source files. */
final class LegacyConfigMigrator {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private LegacyConfigMigrator() {
	}

	static JsonObject migrateIfNeeded(Path target, JsonObject defaults) {
		return migrateIfNeeded(target, defaults, WorldgenIntegrationManager::findProviderOreRulesByOutput);
	}

	static JsonObject migrateIfNeeded(Path target, JsonObject defaults,
			BiFunction<String, String, List<String>> providerRules) {
		if (Files.exists(target)) return null;
		Path config = target.getParent();
		Path mineralogy = config.resolve("mineralogy-geomes.json");
		if (Files.isRegularFile(mineralogy)) {
			JsonObject imported = readObject(mineralogy);
			if (imported != null) {
				imported.addProperty("schema_version", GeomeConfig.SCHEMA_VERSION);
				imported.addProperty("migrated_from", "mineralogy-geomes.json");
				if (write(target, imported)) {
					writeReport(config, List.of("Imported Mineralogy 6 geology profile from " + mineralogy,
							"The source file was retained unchanged."));
					return imported;
				}
			}
		}

		List<Path> legacy = legacyFiles(config);
		if (legacy.isEmpty()) return null;
		JsonObject migrated = defaults.deepCopy();
		JsonObject ores = object(migrated, "ores");
		List<String> report = new ArrayList<>();
		int imported = 0;
		for (Path path : legacy) {
			JsonObject root = readObject(path);
			if (root == null || !"2.0".equals(string(root, "version", ""))
					|| !root.has("spawns") || !root.get("spawns").isJsonObject()) {
				report.add("Skipped unsupported or malformed legacy file: " + path);
				continue;
			}
			String owner = safe(path.getFileName().toString().replaceFirst("\\.json$", ""));
			for (Entry<String, JsonElement> entry : root.getAsJsonObject("spawns").entrySet()) {
				if (!entry.getValue().isJsonObject()) continue;
				JsonObject converted = convertSpawn(entry.getKey(), entry.getValue().getAsJsonObject(), report);
				if (converted != null) {
					String id = migratedRuleId(owner, entry.getKey(), converted, providerRules, report);
					ores.add(id, converted);
					imported++;
				}
			}
			report.add("Read legacy OreSpawn 2.0 file: " + path);
		}
		if (imported == 0) {
			writeReport(config, report);
			return null;
		}
		migrated.add("ores", ores);
		migrated.addProperty("migrated_from", "orespawn-2.0");
		applyLegacyFlags(config.resolve("orespawn.cfg"), migrated, report);
		report.add("Imported " + imported + " legacy spawn definitions.");
		report.add("Original files were retained unchanged. Review registry IDs and biome/dimension warnings before deleting them.");
		if (!write(target, migrated)) return null;
		writeReport(config, report);
		writeUpgradeReport(config, imported, report);
		LOGGER.info("Migrated {} legacy OreSpawn definitions into '{}'", imported, target);
		return migrated;
	}

	private static String migratedRuleId(String owner, String legacyName, JsonObject converted,
			BiFunction<String, String, List<String>> providerRules, List<String> report) {
		String legacyId = "orespawn:legacy/" + owner + "/" + safe(legacyName);
		String output = string(converted, "block", "");
		List<String> matches = providerRules.apply(owner, output);
		if (matches == null) return legacyId;
		if (matches.size() == 1) {
			String providerId = matches.get(0);
			report.add("Mapped legacy rule " + legacyName + " to provider rule " + providerId
					+ " by unique output " + output + ".");
			return providerId;
		}
		if (matches.isEmpty()) {
			report.add("Warning: installed provider " + owner + " has no ore rule matching legacy output "
					+ output + "; retained " + legacyId + ".");
		} else {
			report.add("Warning: installed provider " + owner + " has ambiguous ore rules " + matches
					+ " for legacy output " + output + "; retained " + legacyId + ".");
		}
		return legacyId;
	}

	private static JsonObject convertSpawn(String name, JsonObject source, List<String> report) {
		JsonArray blocks = array(source, "blocks");
		if (blocks.size() == 0) {
			report.add("Skipped " + name + ": no output blocks.");
			return null;
		}
		JsonObject ore = new JsonObject();
		ore.addProperty("enabled", bool(source, "enabled", true));
		ore.addProperty("source_mod", "legacy");
		ore.addProperty("native_generation", false);
		ore.addProperty("suppress_vanilla", true);
		ore.addProperty("retrogen", bool(source, "retrogen", false));
		JsonArray outputs = new JsonArray();
		String primary = null;
		for (JsonElement element : blocks) {
			if (!element.isJsonObject()) continue;
			JsonObject old = element.getAsJsonObject();
			String block = modernBlock(string(old, "name", ""), string(old, "state", ""));
			if (block.isEmpty()) continue;
			if (primary == null) primary = block;
			JsonObject output = new JsonObject();
			output.addProperty("block", block);
			output.addProperty("weight", Math.max(0, integer(old, "chance", 100)));
			outputs.add(output);
		}
		if (primary == null) return null;
		ore.addProperty("block", primary);
		ore.add("outputs", outputs);

		JsonObject parameters = source.has("parameters") && source.get("parameters").isJsonObject()
				? source.getAsJsonObject("parameters") : new JsonObject();
		String feature = normalizePattern(string(source, "feature", "default"));
		int minY = integer(parameters, "minHeight", 0);
		int exclusiveMaxY = integer(parameters, "maxHeight", 256);
		if (exclusiveMaxY <= minY) {
			report.add("Skipped " + name + ": empty legacy height range " + minY + ".." + exclusiveMaxY + ".");
			return null;
		}
		JsonObject rule = new JsonObject();
		rule.addProperty("enabled", true);
		rule.addProperty("min_y", minY);
		rule.addProperty("max_y", exclusiveMaxY - 1);
		rule.addProperty("frequency", Math.max(0.0D, decimal(parameters, "frequency",
				decimal(parameters, "attemptsMin", 1.0D))));
		int size = integer(parameters, "size", integer(parameters, "nodeSize", 8));
		int variation = Math.max(0, integer(parameters, "variation", 4));
		if ("default".equals(feature) && variation > 0) {
			int minQuantity = clampLegacyQuantity(name, (long) size - variation, report);
			int maxQuantity = clampLegacyQuantity(name, (long) size + variation - 1L, report);
			if (minQuantity > maxQuantity) {
				report.add("Skipped " + name + ": invalid legacy quantity range.");
				return null;
			}
			rule.addProperty("min_quantity", minQuantity);
			rule.addProperty("max_quantity", maxQuantity);
		} else {
			rule.addProperty("quantity", clampLegacyQuantity(name, size, report));
		}
		rule.addProperty("pattern", feature);
		rule.addProperty("spread", Math.max(0, integer(parameters, "maxSpread", 8)));
		rule.addProperty("vertical_spread", "default".equals(feature)
				? Math.max(1, integer(parameters, "maxSpread", 8) / 2) : variation);
		rule.addProperty("node_size", Math.max(1, integer(parameters, "nodeSize", 4)));
		rule.addProperty("length", Math.max(1, integer(parameters, "length", 16)));
		rule.addProperty("fluid", qualifyFluid(string(parameters, "fluid", "water")));
		addLegacyHosts(source.get("replaces"), rule);
		addBiomeRules(source.get("biomes"), rule);

		JsonObject dimensions = new JsonObject();
		JsonObject selectors = new JsonObject();
		JsonElement dimensionElement = source.get("dimensions");
		if (dimensionElement == null || (dimensionElement.isJsonArray()
				&& dimensionElement.getAsJsonArray().size() == 0)) {
			selectors.add("orespawn:all_except_nether_end", rule);
		} else if (dimensionElement.isJsonArray()) {
			for (JsonElement dimension : dimensionElement.getAsJsonArray()) {
				String id = legacyDimension(dimension);
				if (id != null) dimensions.add(id, rule.deepCopy());
				else report.add("Skipped unknown numeric dimension for " + name + ": " + dimension);
			}
		} else if (dimensionElement.isJsonObject()) {
			JsonObject whitelist = dimensionElement.getAsJsonObject();
			JsonArray values = whitelist.has("includes") ? array(whitelist, "includes")
					: array(whitelist, "whitelist");
			for (JsonElement dimension : values) {
				String id = legacyDimension(dimension);
				if (id != null) dimensions.add(id, rule.deepCopy());
			}
		}
		if (dimensions.size() == 0 && selectors.size() == 0) {
			report.add("Skipped " + name + ": no 1.18 dimension mapping could be inferred.");
			return null;
		}
		if (dimensions.size() > 0) ore.add("dimensions", dimensions);
		if (selectors.size() > 0) ore.add("dimension_selectors", selectors);
		return ore;
	}

	private static int clampLegacyQuantity(String name, long value, List<String> report) {
		int clamped = (int) Math.max(1L, Math.min(64L, value));
		if (clamped != value) {
			report.add("Clamped legacy quantity for " + name + " from " + value + " to " + clamped + ".");
		}
		return clamped;
	}

	private static void addLegacyHosts(JsonElement replaces, JsonObject rule) {
		JsonArray hosts = new JsonArray();
		if (replaces == null || (replaces.isJsonPrimitive() && "default".equals(replaces.getAsString()))) {
			hosts.add("minecraft:stone");
			hosts.add("minecraft:deepslate");
			hosts.add("minecraft:netherrack");
			hosts.add("minecraft:end_stone");
		} else if (replaces.isJsonArray()) {
			for (JsonElement element : replaces.getAsJsonArray()) {
				if (!element.isJsonObject()) continue;
				String name = modernBlock(string(element.getAsJsonObject(), "name", ""),
						string(element.getAsJsonObject(), "state", ""));
				if (!name.isEmpty() && !name.startsWith("ore:")) hosts.add(name);
			}
		}
		rule.add("host_blocks", hosts);
		rule.add("host_tags", new JsonArray());
		rule.add("host_families", new JsonArray());
	}

	private static void addBiomeRules(JsonElement element, JsonObject rule) {
		if (element == null || !element.isJsonObject()) return;
		JsonObject biomes = element.getAsJsonObject();
		addBiomeList(biomes, rule, "includes", "biome_ids", "biome_dictionary");
		addBiomeList(biomes, rule, "whitelist", "biome_ids", "biome_dictionary");
		addBiomeList(biomes, rule, "excludes", "excluded_biome_ids", "excluded_biome_dictionary");
		addBiomeList(biomes, rule, "blacklist", "excluded_biome_ids", "excluded_biome_dictionary");
	}

	private static void addBiomeList(JsonObject source, JsonObject target, String sourceKey,
			String idsKey, String dictionaryKey) {
		if (!source.has(sourceKey) || !source.get(sourceKey).isJsonArray()) return;
		JsonArray ids = target.has(idsKey) ? target.getAsJsonArray(idsKey) : new JsonArray();
		JsonArray dictionary = target.has(dictionaryKey) ? target.getAsJsonArray(dictionaryKey) : new JsonArray();
		for (JsonElement value : source.getAsJsonArray(sourceKey)) {
			String text = value.getAsString();
			if (text.indexOf(':') >= 0) ids.add(text);
			else dictionary.add(text.toUpperCase(Locale.ROOT));
		}
		if (ids.size() > 0) target.add(idsKey, ids);
		if (dictionary.size() > 0) target.add(dictionaryKey, dictionary);
	}

	private static void applyLegacyFlags(Path path, JsonObject target, List<String> report) {
		if (!Files.isRegularFile(path)) return;
		try {
			String text = Files.readString(path, StandardCharsets.UTF_8);
			target.addProperty("manage_vanilla_ores", legacyBoolean(text, "Replace Vanilla Oregen", false));
			target.addProperty("suppress_all_ore_features", legacyBoolean(text, "Replace All Generation", false));
			JsonObject retrogen = new JsonObject();
			retrogen.addProperty("enabled", legacyBoolean(text, "Retrogen", false));
			retrogen.addProperty("force", legacyBoolean(text, "Force Retrogen", false));
			target.add("retrogen", retrogen);
			JsonObject bedrock = new JsonObject();
			bedrock.addProperty("enabled", legacyBoolean(text, "Flatten Bedrock", false));
			bedrock.addProperty("retrogen", legacyBoolean(text, "Retrogen Flat Bedrock", false));
			bedrock.addProperty("layers", legacyInteger(text, "Bedrock Thickness", 1));
			target.add("flat_bedrock", bedrock);
			report.add("Imported compatible flags from " + path);
		} catch (IOException e) {
			report.add("Could not read legacy flags from " + path + ": " + e.getMessage());
		}
	}

	private static boolean legacyBoolean(String text, String key, boolean fallback) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
				"(?im)^\\s*B:\\Q" + key + "\\E\\s*=\\s*(true|false)\\s*$").matcher(text);
		return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
	}

	private static int legacyInteger(String text, String key, int fallback) {
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
				"(?im)^\\s*I:\\Q" + key + "\\E\\s*=\\s*(-?\\d+)\\s*$").matcher(text);
		return matcher.find() ? Integer.parseInt(matcher.group(1)) : fallback;
	}

	private static List<Path> legacyFiles(Path config) {
		List<Path> result = new ArrayList<>();
		for (Path directory : List.of(config.resolve("orespawn3"), config.resolve("orespawn"))) {
			if (!Files.isDirectory(directory)) continue;
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
				for (Path path : stream) result.add(path);
			} catch (IOException ignored) {
			}
		}
		result.sort(Comparator.comparing(Path::toString));
		return result;
	}

	private static JsonObject readObject(Path path) {
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement value = new JsonParser().parse(reader);
			return value.isJsonObject() ? value.getAsJsonObject() : null;
		} catch (IOException | RuntimeException e) {
			LOGGER.warn("Could not read legacy OreSpawn configuration '{}'", path, e);
			return null;
		}
	}

	private static boolean write(Path target, JsonObject value) {
		Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
		try {
			Files.createDirectories(target.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				GSON.toJson(value, writer);
			}
			try {
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException e) {
			LOGGER.warn("Could not write migrated OreSpawn configuration '{}'", target, e);
			return false;
		}
	}

	private static void writeReport(Path config, List<String> lines) {
		try {
			Path directory = config.resolve("orespawn-migration");
			Files.createDirectories(directory);
			Files.write(directory.resolve("migration-report.txt"), lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.warn("Could not write OreSpawn migration report", e);
		}
	}

	private static void writeUpgradeReport(Path config, int imported, List<String> detail) {
		List<String> lines = new ArrayList<>();
		lines.add("OreSpawn 4.0.9.2602001 Upgrade Report");
		lines.add("================================");
		lines.add("");
		lines.add("RESULT: Legacy OreSpawn settings were imported into the OS4 profile.");
		lines.add("- Spawn definitions imported: " + imported);
		lines.add("- Detailed translation report: "
				+ config.resolve("orespawn-migration/migration-report.txt").toAbsolutePath());
		for (String entry : detail) {
			if (entry.startsWith("Warning:") || entry.startsWith("Skipped")
					|| entry.startsWith("Clamped")) lines.add("- " + entry);
		}
		lines.add("");
		lines.add("Original legacy configuration files were retained unchanged.");
		writeTextAtomically(config.resolve("orespawn-upgrade-report.txt"), lines);
	}

	private static void writeTextAtomically(Path path, List<String> lines) {
		Path temporary = path.resolveSibling(path.getFileName().toString() + ".tmp");
		try {
			Files.createDirectories(path.getParent());
			byte[] bytes = (String.join(System.lineSeparator(), lines) + System.lineSeparator())
					.getBytes(StandardCharsets.UTF_8);
			if (Files.isRegularFile(path) && Arrays.equals(Files.readAllBytes(path), bytes)) return;
			Files.write(temporary, bytes);
			try {
				Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
			LOGGER.warn("Could not write OreSpawn upgrade report '{}'", path, e);
		}
	}

	private static JsonObject object(JsonObject root, String key) {
		if (!root.has(key) || !root.get(key).isJsonObject()) root.add(key, new JsonObject());
		return root.getAsJsonObject(key);
	}

	private static JsonArray array(JsonObject root, String key) {
		return root.has(key) && root.get(key).isJsonArray() ? root.getAsJsonArray(key) : new JsonArray();
	}

	private static String normalizePattern(String value) {
		String normalized = value.toLowerCase(Locale.ROOT).replace('-', '_');
		if ("normal_cloud".equals(normalized) || "cloud".equals(normalized)) return "normal_cloud";
		if ("cluster".equals(normalized)) return "clusters";
		if ("under_fluid".equals(normalized)) return "underfluids";
		return normalized;
	}

	private static String modernBlock(String name, String state) {
		if ("minecraft:stone".equals(name)) {
			if (state.contains("andesite")) return "minecraft:andesite";
			if (state.contains("diorite")) return "minecraft:diorite";
			if (state.contains("granite")) return "minecraft:granite";
		}
		return name.indexOf(':') >= 0 ? name : name.isEmpty() ? "" : "minecraft:" + name;
	}

	private static String legacyDimension(JsonElement value) {
		if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
			String text = value.getAsString();
			if (text.indexOf(':') >= 0) return text;
		}
		try {
			int id = value.getAsInt();
			if (id == -1) return "minecraft:the_nether";
			if (id == 0) return "minecraft:overworld";
			if (id == 1) return "minecraft:the_end";
		} catch (RuntimeException ignored) {
		}
		return null;
	}

	private static String qualifyFluid(String value) {
		return value.indexOf(':') >= 0 ? value : "minecraft:" + value;
	}

	private static String safe(String value) {
		String safe = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._/-]", "_");
		return safe.replaceAll("_+", "_");
	}

	private static String string(JsonObject root, String key, String fallback) {
		try { return root.has(key) ? root.get(key).getAsString() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static int integer(JsonObject root, String key, int fallback) {
		try { return root.has(key) ? root.get(key).getAsInt() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static double decimal(JsonObject root, String key, double fallback) {
		try { return root.has(key) ? root.get(key).getAsDouble() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static boolean bool(JsonObject root, String key, boolean fallback) {
		try { return root.has(key) ? root.get(key).getAsBoolean() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}
}
