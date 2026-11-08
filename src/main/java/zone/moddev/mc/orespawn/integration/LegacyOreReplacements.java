package zone.moddev.mc.orespawn.integration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import zone.moddev.mc.orespawn.util.JsonCopies;

/** Keeps a saved OS1 rule in charge when its provider starts supplying a native default. */
final class LegacyOreReplacements {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String REPLACEMENTS = "legacy_ore_replacements";
	private static final String REVIEW = "legacy_ore_review";

	static final class Result {
		final Set<String> suppressed = new HashSet<>();
		boolean changed;
	}

	private LegacyOreReplacements() {}

	static Result reconcile(JsonObject profile, String provider, JsonObject ores, JsonObject defaults,
			JsonObject active, JsonObject manifest, Set<String> known) {
		Result result = new Result();
		JsonObject previous = object(manifest, REPLACEMENTS);
		JsonObject replacements = JsonCopies.copy(previous);
		JsonObject review = new JsonObject();
		Map<String, List<String>> imports = new HashMap<>();
		for (String id : known) {
			JsonObject ore = object(ores, id);
			String output = output(ore);
			if (output != null && isOs1Import(provider, id, ore)) {
				imports.computeIfAbsent(output, ignored -> new ArrayList<>()).add(id);
			}
		}
		if (imports.isEmpty() && previous.entrySet().isEmpty() && !manifest.has(REVIEW)) return result;

		Map<String, Integer> countByOutput = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
			JsonObject ore = object(defaults, entry.getKey());
			if (!isOs1Import(provider, entry.getKey(), ore)) {
				String output = output(ore);
				if (output != null) countByOutput.merge(output, 1, Integer::sum);
			}
		}
		for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
			String id = entry.getKey();
			JsonObject nativeOre = object(defaults, id);
			if (isOs1Import(provider, id, nativeOre)) continue;
			String remembered = text(previous, id);
			String output = output(nativeOre);
			List<String> matches = imports.getOrDefault(output, Collections.emptyList());
			if (remembered.isEmpty() && matches.isEmpty()) continue;

			if (remembered.isEmpty() && (matches.size() != 1 || countByOutput.getOrDefault(output, 0) != 1)) {
				flag(review, manifest, provider, id, "ambiguous_import");
				result.suppressed.add(id);
				continue;
			}
			String legacyId = remembered.isEmpty() ? matches.get(0) : remembered;
			if (id.equals(legacyId) || !known.contains(legacyId)) continue;
			if (active.has(id) && !entry.getValue().equals(active.get(id))) {
				flag(review, manifest, provider, id, "provider_override_differs");
				continue;
			}
			if (ores.has(id)) {
				// Never undo an edited rule or a selection that depends on it.
				JsonObject expected = JsonCopies.copy(nativeOre);
				expected.addProperty("source_provider", provider);
				JsonObject saved = JsonCopies.copy(object(ores, id));
				saved.remove("orphaned_provider");
				List<String> disposablePolicies = stockPolicyReferences(profile, id, legacyId);
				if (!known.contains(id) || !ores.has(legacyId) || !ores.get(id).isJsonObject()
						|| !expected.equals(saved) || disposablePolicies == null) {
					flag(review, manifest, provider, id, "saved_rule_or_policy_differs");
					continue;
				}
				ores.remove(id);
				JsonObject policies = object(profile, "ore_source_policies");
				for (String key : disposablePolicies) policies.remove(key);
				result.changed = true;
				LOGGER.info("Kept imported ore rule {} and removed untouched duplicate {}", legacyId, id);
			}
			replacements.addProperty(id, legacyId);
			result.suppressed.add(id);
			// The provider still supplies a counterpart for the imported rule.
			JsonObject imported = object(ores, legacyId);
			if (imported.has("orphaned_provider")) {
				imported.remove("orphaned_provider");
				result.changed = true;
			}
		}
		result.changed |= update(manifest, REPLACEMENTS, replacements);
		result.changed |= update(manifest, REVIEW, review);
		return result;
	}

	static boolean retains(JsonObject manifest, String id, Set<String> currentDefaults) {
		JsonObject replacements = object(manifest, REPLACEMENTS);
		for (Map.Entry<String, JsonElement> entry : replacements.entrySet()) {
			if (currentDefaults.contains(entry.getKey()) && id.equals(text(replacements, entry.getKey()))) {
				return true;
			}
		}
		return false;
	}

	private static boolean isOs1Import(String provider, String id, JsonObject ore) {
		String block = text(ore, "block");
		if (!block.startsWith(provider + ":") || !provider.equals(text(ore, "source_mod"))
				|| !"false".equals(text(ore, "native_generation"))) return false;
		if ("os1".equals(text(ore, "legacy_import"))) return true;
		// Older OS1 imports used the namespaced output plus an ordinal. OS3 and
		// authored rules do not qualify by block name alone.
		String prefix = provider + ":legacy/" + block.replace(':', '_');
		return provider.equals(text(ore, "source_provider")) && id.startsWith(prefix)
				&& id.substring(prefix.length()).matches("_[0-9]+");
	}

	private static String output(JsonObject ore) {
		String block = text(ore, "block");
		String variant = metadata(ore);
		if (block.isEmpty() || variant == null) return null;
		if (ore.has("outputs")) {
			JsonElement value = ore.get("outputs");
			if (!value.isJsonArray()) return null;
			JsonArray outputs = value.getAsJsonArray();
			if (outputs.size() != 1 || !outputs.get(0).isJsonObject()) return null;
			JsonObject only = outputs.get(0).getAsJsonObject();
			if (!block.equals(text(only, "block")) || !variant.equals(metadata(only))) return null;
		}
		return block + "#" + variant;
	}

	private static String metadata(JsonObject ore) {
		if (!ore.has("metadata")) return "0";
		String meta = text(ore, "metadata");
		return meta.matches("[0-9]|1[0-5]") ? meta : null;
	}

	/** Only remove the fallback policy generated for a lone native rule. */
	private static List<String> stockPolicyReferences(JsonObject profile, String nativeId, String legacyId) {
		List<String> disposable = new ArrayList<>();
		JsonObject imported = object(object(profile, "ores"), legacyId);
		for (Map.Entry<String, JsonElement> entry : object(profile, "ore_source_policies").entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject policy = entry.getValue().getAsJsonObject();
			JsonObject outputs = object(policy, "outputs");
			JsonObject placements = object(policy, "placement_sources");
			if (!outputs.has(nativeId) && !placements.entrySet().stream()
					.anyMatch(place -> nativeId.equals(text(placements, place.getKey())))) continue;
			String domain = text(policy, "domain");
			if (!entry.getKey().equals(text(policy, "material") + "|" + domain) || policy.has("dormant")
					|| object(imported, "dimensions").has(domain) || object(imported, "dimension_selectors").has(domain)
					|| !"keep_separate".equals(text(policy, "mode")) || !"custom".equals(text(policy, "output_mode"))
					|| !"separate".equals(text(policy, "status")) || !"false".equals(text(policy, "review_required"))
					|| outputs.entrySet().size() != 1 || !unitWeight(outputs.get(nativeId))
					|| placements.entrySet().size() != 1
					|| !nativeId.equals(text(placements, "orespawn:standard"))) return null;
			disposable.add(entry.getKey());
		}
		return disposable;
	}

	private static boolean unitWeight(JsonElement value) {
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
				&& value.getAsDouble() == 1.0D;
	}

	private static void flag(JsonObject review, JsonObject manifest, String provider, String id, String reason) {
		review.addProperty(id, reason);
		if (!reason.equals(text(object(manifest, REVIEW), id))) {
			LOGGER.warn("Review legacy ore overlap for provider {} and rule {} ({}); saved settings were kept",
					provider, id, reason);
		}
	}

	private static boolean update(JsonObject manifest, String key, JsonObject value) {
		if (value.equals(object(manifest, key))) return false;
		if (value.entrySet().isEmpty()) manifest.remove(key);
		else manifest.add(key, value);
		return true;
	}

	private static JsonObject object(JsonObject root, String key) {
		JsonElement value = root.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
	}

	private static String text(JsonObject root, String key) {
		JsonElement value = root.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
	}
}
