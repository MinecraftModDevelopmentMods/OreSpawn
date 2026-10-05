package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.util.ResourceLocation;
import zone.moddev.mc.orespawn.util.JsonCopies;

/** Stable material names and exact block-tag aliases used to group equivalent ore sources. */
final class OreMaterialGroups {
	static final String SECTION = "ore_material_groups";
	static final String TAGS = "block_tag_entries";
	static final String LEGACY_ALIASES = "ore_dictionary_entries";
	private static final ResourceLocation LEGACY_REVIEW = new ResourceLocation("orespawn", "review_required");
	private static final ResourceLocation SULFUR = new ResourceLocation("orespawn", "sulfur");
	private static final ResourceLocation ALUMINUM = new ResourceLocation("orespawn", "aluminum");

	private OreMaterialGroups() { }

	static boolean initialize(JsonObject root) {
		JsonObject before = root.has(SECTION) && root.get(SECTION).isJsonObject()
				? JsonCopies.copy(root.getAsJsonObject(SECTION)) : new JsonObject();
		JsonObject normalized = normalize(before);
		mergeCurated(normalized, SULFUR, "Sulfur", "forge:ores/sulfur", "forge:ores/sulphur");
		mergeCurated(normalized, ALUMINUM, "Aluminum", "forge:ores/aluminum", "forge:ores/aluminium");
		deduplicateAliases(normalized);
		root.add(SECTION, normalized);
		return !before.toString().equals(normalized.toString());
	}

	static JsonObject defaults() {
		JsonObject root = new JsonObject();
		mergeCurated(root, SULFUR, "Sulfur", "forge:ores/sulfur", "forge:ores/sulphur");
		mergeCurated(root, ALUMINUM, "Aluminum", "forge:ores/aluminum", "forge:ores/aluminium");
		return root;
	}

	static Inference infer(JsonObject root, Iterable<String> blockTags) {
		Map<String, ResourceLocation> owners = aliases(root);
		Set<ResourceLocation> materials = new LinkedHashSet<>();
		Set<String> exactNames = new LinkedHashSet<>();
		for (String name : blockTags) {
			if (!validTag(name)) continue;
			exactNames.add(name);
			ResourceLocation material = owners.get(name);
			if (material == null) {
				ResourceLocation tag = resource(name);
				String token = tag.getPath().substring("ores/".length());
				try { material = new ResourceLocation("orespawn", token); }
				catch (RuntimeException invalid) { material = null; }
			}
			if (material != null) materials.add(material);
		}
		List<String> exact = new ArrayList<>(exactNames);
		Collections.sort(exact);
		if (materials.size() == 1) {
			ResourceLocation material = materials.iterator().next();
			return new Inference(material, false, exact);
		}
		return new Inference(materials.size() > 1 ? provisionalMaterial(exact) : null,
				materials.size() > 1, exact);
	}

	static List<Definition> definitions(JsonObject root) {
		JsonObject groups = root.has(SECTION) && root.get(SECTION).isJsonObject()
				? root.getAsJsonObject(SECTION) : defaults();
		List<Definition> result = new ArrayList<>();
		for (Entry<String, JsonElement> entry : groups.entrySet()) {
			ResourceLocation id = resource(entry.getKey());
			if (id == null || !entry.getValue().isJsonObject()) continue;
			JsonObject value = entry.getValue().getAsJsonObject();
			List<String> names = new ArrayList<>();
			if (value.has(TAGS) && value.get(TAGS).isJsonArray()) {
				for (JsonElement element : value.getAsJsonArray(TAGS)) {
					try {
						String name = element.getAsString();
						if (validTag(name) && !names.contains(name)) names.add(name);
					} catch (RuntimeException ignored) { }
				}
			}
			Collections.sort(names);
			String display = string(value, "display_name", humanize(id.getPath()));
			result.add(new Definition(id, display, names, legacyAliases(value), curated(id)));
		}
		result.sort(Comparator.comparing((Definition value) -> value.displayName.toLowerCase(Locale.ROOT))
				.thenComparing(value -> value.id.toString()));
		return Collections.unmodifiableList(result);
	}

	static Definition definition(JsonObject root, ResourceLocation id) {
		for (Definition definition : definitions(root)) if (definition.id.equals(id)) return definition;
		return new Definition(id, humanize(id.getPath()), Collections.emptyList(),
				Collections.emptyList(), curated(id));
	}

	static boolean ensureDefinition(JsonObject root, ResourceLocation id, Iterable<String> blockTags) {
		List<String> exact = exactTags(blockTags);
		JsonObject groups = root.has(SECTION) && root.get(SECTION).isJsonObject()
				? root.getAsJsonObject(SECTION) : defaults();
		boolean changed = !root.has(SECTION) || !root.get(SECTION).isJsonObject();
		JsonObject value;
		if (groups.has(id.toString()) && groups.get(id.toString()).isJsonObject()) {
			value = groups.getAsJsonObject(id.toString());
		} else {
			value = new JsonObject();
			value.addProperty("display_name", defaultDisplayName(id, exact));
			value.add(TAGS, new JsonArray());
			groups.add(id.toString(), value);
			changed = true;
		}
		JsonArray aliases = value.has(TAGS)
				&& value.get(TAGS).isJsonArray()
				? value.getAsJsonArray(TAGS) : new JsonArray();
		if (!value.has(TAGS) || !value.get(TAGS).isJsonArray()) {
			value.add(TAGS, aliases);
			changed = true;
		}
		Set<String> present = new LinkedHashSet<>();
		for (JsonElement alias : aliases) {
			try { present.add(alias.getAsString()); } catch (RuntimeException ignored) { }
		}
		Map<String, ResourceLocation> owners = aliases(root);
		for (String name : exact) {
			if (!validTag(name) || present.contains(name)) continue;
			ResourceLocation owner = owners.get(name);
			if (owner != null && !owner.equals(id)) continue;
			aliases.add(new JsonPrimitive(name));
			present.add(name);
			changed = true;
		}
		root.add(SECTION, groups);
		return changed;
	}

	static boolean validTag(String name) {
		ResourceLocation tag = resource(name);
		return tag != null && name.length() <= 128
				&& tag.getPath().startsWith("ores/")
				&& tag.getPath().length() > "ores/".length();
	}

	static boolean curated(ResourceLocation id) {
		return SULFUR.equals(id) || ALUMINUM.equals(id);
	}

	private static JsonObject normalize(JsonObject groups) {
		JsonObject result = new JsonObject();
		for (Entry<String, JsonElement> entry : groups.entrySet()) {
			ResourceLocation id = resource(entry.getKey());
			if (id == null || LEGACY_REVIEW.equals(id) || !entry.getValue().isJsonObject()) continue;
			JsonObject source = entry.getValue().getAsJsonObject();
			JsonObject value = new JsonObject();
			value.addProperty("display_name", string(source, "display_name", humanize(id.getPath())));
			JsonArray names = new JsonArray();
			Set<String> seen = new LinkedHashSet<>();
			if (source.has(TAGS) && source.get(TAGS).isJsonArray()) {
				for (JsonElement element : source.getAsJsonArray(TAGS)) {
					try {
						String name = element.getAsString();
						if (validTag(name) && seen.add(name)) names.add(new JsonPrimitive(name));
					} catch (RuntimeException ignored) { }
				}
			}
			value.add(TAGS, names);
			JsonArray dormant = new JsonArray();
			for (String alias : legacyAliases(source)) dormant.add(new JsonPrimitive(alias));
			if (dormant.size() > 0) value.add(LEGACY_ALIASES, dormant);
			result.add(id.toString(), value);
		}
		return result;
	}

	private static void mergeCurated(JsonObject groups, ResourceLocation id, String display, String... aliases) {
		if (groups.has(id.toString()) && groups.get(id.toString()).isJsonObject()) return;
		JsonObject value = new JsonObject();
		value.addProperty("display_name", display);
		JsonArray names = new JsonArray();
		for (String alias : aliases) names.add(new JsonPrimitive(alias));
		value.add(TAGS, names);
		groups.add(id.toString(), value);
	}

	private static void deduplicateAliases(JsonObject groups) {
		Set<String> claimed = new LinkedHashSet<>();
		for (Entry<String, JsonElement> entry : groups.entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject value = entry.getValue().getAsJsonObject();
			JsonArray unique = new JsonArray();
			if (value.has(TAGS) && value.get(TAGS).isJsonArray()) {
				for (JsonElement element : value.getAsJsonArray(TAGS)) {
					try {
						String name = element.getAsString();
						if (validTag(name) && claimed.add(name)) unique.add(new JsonPrimitive(name));
					} catch (RuntimeException ignored) { }
				}
			}
			value.add(TAGS, unique);
		}
	}

	private static Map<String, ResourceLocation> aliases(JsonObject root) {
		Map<String, ResourceLocation> result = new LinkedHashMap<>();
		for (Definition definition : definitions(root)) {
			for (String name : definition.blockTagEntries) result.putIfAbsent(name, definition.id);
		}
		return result;
	}

	private static List<String> legacyAliases(JsonObject value) {
		Set<String> result = new LinkedHashSet<>();
		if (value.has(LEGACY_ALIASES) && value.get(LEGACY_ALIASES).isJsonArray()) {
			for (JsonElement element : value.getAsJsonArray(LEGACY_ALIASES)) {
				try {
					String alias = element.getAsString();
					if (alias.length() <= 128 && alias.matches("ore[A-Z][A-Za-z0-9_]+")) result.add(alias);
				} catch (RuntimeException ignored) { }
			}
		}
		return new ArrayList<>(result);
	}

	private static ResourceLocation resource(String value) {
		try { return value == null || value.trim().isEmpty() ? null : new ResourceLocation(value); }
		catch (RuntimeException ignored) { return null; }
	}

	private static String string(JsonObject root, String key, String fallback) {
		try { return root.has(key) ? root.get(key).getAsString() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static String humanize(String path) {
		String value = path.replace('_', ' ').replace('/', ' ');
		return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private static ResourceLocation provisionalMaterial(List<String> exact) {
		String token = exact.isEmpty() ? "unknown"
				: resource(exact.get(0)).getPath().substring("ores/".length());
		return new ResourceLocation("orespawn", "review/" + token + '-' + stableAliasHash(exact));
	}

	private static String stableAliasHash(List<String> exact) {
		long hash = 0xcbf29ce484222325L;
		for (String name : exact) {
			for (int index = 0; index < name.length(); index++) {
				hash ^= name.charAt(index);
				hash *= 0x100000001b3L;
			}
			hash ^= 0xffL;
			hash *= 0x100000001b3L;
		}
		String value = Long.toHexString(hash);
		StringBuilder padded = new StringBuilder(16);
		for (int index = value.length(); index < 16; index++) padded.append('0');
		return padded.append(value).toString();
	}

	private static List<String> exactTags(Iterable<String> blockTags) {
		Set<String> unique = new LinkedHashSet<>();
		for (String name : blockTags) if (validTag(name)) unique.add(name);
		List<String> result = new ArrayList<>(unique);
		Collections.sort(result);
		return result;
	}

	private static String defaultDisplayName(ResourceLocation id, List<String> exact) {
		if (!"orespawn".equals(id.getNamespace()) || !id.getPath().startsWith("review/")) {
			return humanize(id.getPath());
		}
		StringBuilder display = new StringBuilder();
		for (String name : exact) {
			if (display.length() > 0) display.append(", ");
			display.append(humanize(resource(name).getPath().substring("ores/".length())));
		}
		return display.length() == 0 ? "Review required" : display.toString();
	}

	static final class Definition {
		final ResourceLocation id;
		final String displayName;
		final List<String> blockTagEntries;
		final List<String> dormantOreDictionaryEntries;
		final boolean curated;

		Definition(ResourceLocation id, String displayName, List<String> blockTagEntries,
				List<String> dormantOreDictionaryEntries, boolean curated) {
			this.id = id;
			this.displayName = displayName;
			this.blockTagEntries = Collections.unmodifiableList(new ArrayList<>(blockTagEntries));
			this.dormantOreDictionaryEntries = Collections.unmodifiableList(new ArrayList<>(dormantOreDictionaryEntries));
			this.curated = curated;
		}
	}

	static final class Inference {
		final ResourceLocation material;
		final boolean reviewRequired;
		final List<String> names;

		Inference(ResourceLocation material, boolean reviewRequired, List<String> names) {
			this.material = material;
			this.reviewRequired = reviewRequired;
			this.names = Collections.unmodifiableList(new ArrayList<>(names));
		}
	}
}
