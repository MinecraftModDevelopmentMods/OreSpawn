package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.util.ResourceLocation;

/** Encodes exact new-terrain biome replacements in a terminal UI palette. */
final class BiomeReplacementRules {
	static final String PREFIX = "orespawn:ui/biome_overrides/";

	private BiomeReplacementRules() { }

	static String paletteId(String dimension) {
		return PREFIX + dimension.toLowerCase(java.util.Locale.ROOT)
				.replace(':', '_').replaceAll("[^a-z0-9_./-]", "_");
	}

	static boolean isOverridePalette(String id) {
		return id != null && id.startsWith(PREFIX);
	}

	static Map<String, String> read(JsonObject root, String dimension) {
		JsonObject palettes = object(root, "biome_palettes", false);
		if (palettes == null) return Collections.emptyMap();
		JsonElement value = palettes.get(paletteId(dimension));
		if (value == null || !value.isJsonObject()) return Collections.emptyMap();
		JsonObject palette = value.getAsJsonObject();
		Map<String, String> result = new LinkedHashMap<>();
		JsonObject biomes = object(palette, "biomes", false);
		if (biomes == null) return Collections.emptyMap();
		for (Entry<String, JsonElement> target : biomes.entrySet()) {
			if (!target.getValue().isJsonObject()) continue;
			JsonElement sources = target.getValue().getAsJsonObject().get("similar_biomes");
			if (sources == null || !sources.isJsonArray()) continue;
			for (JsonElement source : sources.getAsJsonArray()) {
				try { result.put(new ResourceLocation(source.getAsString()).toString(),
						new ResourceLocation(target.getKey()).toString()); }
				catch (RuntimeException ignored) { }
			}
		}
		return Collections.unmodifiableMap(result);
	}

	static void set(JsonObject root, String dimension, String source, String target) {
		String normalizedSource = new ResourceLocation(source).toString();
		String normalizedTarget = new ResourceLocation(target).toString();
		if (normalizedSource.equals(normalizedTarget)) {
			throw new IllegalArgumentException("A biome cannot replace itself");
		}
		Map<String, String> mappings = new LinkedHashMap<>(read(root, dimension));
		String terminal = terminal(mappings, normalizedTarget, normalizedSource);
		for (Entry<String, String> entry : new ArrayList<>(mappings.entrySet())) {
			if (normalizedSource.equals(entry.getValue())) mappings.put(entry.getKey(), terminal);
		}
		mappings.put(normalizedSource, terminal);
		flatten(mappings);
		write(root, dimension, mappings);
	}

	static void remove(JsonObject root, String dimension, String source) {
		Map<String, String> mappings = new LinkedHashMap<>(read(root, dimension));
		mappings.remove(source);
		write(root, dimension, mappings);
	}

	static void clearDimension(JsonObject root, String dimension) {
		JsonObject palettes = object(root, "biome_palettes", false);
		if (palettes != null) palettes.remove(paletteId(dimension));
	}

	private static String terminal(Map<String, String> mappings, String start, String rejected) {
		Set<String> seen = new LinkedHashSet<>();
		String current = start;
		while (mappings.containsKey(current)) {
			if (!seen.add(current) || rejected.equals(current)) {
				throw new IllegalArgumentException("Biome replacement cycle");
			}
			current = mappings.get(current);
		}
		if (rejected.equals(current)) throw new IllegalArgumentException("Biome replacement cycle");
		return current;
	}

	private static void flatten(Map<String, String> mappings) {
		for (String source : new ArrayList<>(mappings.keySet())) {
			mappings.put(source, terminal(mappings, mappings.get(source), source));
		}
	}

	private static void write(JsonObject root, String dimension, Map<String, String> mappings) {
		JsonObject palettes = object(root, "biome_palettes", true);
		String id = paletteId(dimension);
		if (mappings.isEmpty()) {
			palettes.remove(id);
			return;
		}
		JsonObject palette = new JsonObject();
		palette.addProperty("dimension", dimension);
		palette.addProperty("enabled", true);
		palette.addProperty("mode", "replace");
		palette.addProperty("scope", "all");
		palette.addProperty("region_size", "tiny");
		palette.addProperty("coverage", 1.0D);
		palette.addProperty("fallback_weight", 0.0D);
		palette.add("include_namespaces", new JsonArray());
		palette.add("exclude_namespaces", new JsonArray());
		JsonObject biomes = new JsonObject();
		Map<String, List<String>> byTarget = new LinkedHashMap<>();
		List<String> sources = new ArrayList<>(mappings.keySet());
		Collections.sort(sources);
		for (String source : sources) {
			byTarget.computeIfAbsent(mappings.get(source), ignored -> new ArrayList<>()).add(source);
		}
		List<String> targets = new ArrayList<>(byTarget.keySet());
		Collections.sort(targets);
		for (String target : targets) {
			JsonObject placement = new JsonObject();
			placement.addProperty("enabled", true);
			placement.addProperty("weight", 1.0D);
			JsonArray similar = new JsonArray();
			for (String source : byTarget.get(target)) similar.add(new JsonPrimitive(source));
			placement.add("similar_biomes", similar);
			placement.add("required_similar_biomes", new JsonArray());
			placement.addProperty("min_temperature", -2.0D);
			placement.addProperty("max_temperature", 2.0D);
			placement.addProperty("min_downfall", 0.0D);
			placement.addProperty("max_downfall", 1.0D);
			biomes.add(target, placement);
		}
		palette.add("biomes", biomes);
		palettes.add(id, palette);
	}

	private static JsonObject object(JsonObject root, String key, boolean create) {
		if (root.has(key) && root.get(key).isJsonObject()) return root.getAsJsonObject(key);
		if (!create) return null;
		JsonObject value = new JsonObject();
		root.add(key, value);
		return value;
	}
}
