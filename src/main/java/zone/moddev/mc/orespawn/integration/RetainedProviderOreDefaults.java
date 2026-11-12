package zone.moddev.mc.orespawn.integration;

import java.util.HashSet;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

/** Conservative matching at provider merge time, never in chunk generation. */
final class RetainedProviderOreDefaults {

	private static final Set<String> BASE_METALS_LEGACY_MATERIALS = Set.of(
			"coldiron", "adamantine", "starsteel", "tin", "lead", "zinc",
			"silver", "mercury", "nickel", "platinum");

	private record Placement(String kind, String dimension, String channel) {}

	private RetainedProviderOreDefaults() {}

	static String predecessor(String owner, String currentId, JsonObject current,
			JsonObject defaults, JsonObject saved, Set<String> known) {
		String output = singletonOutput(currentId, current);
		Set<Placement> scope = placements(current);
		if (output == null || scope == null || scope.isEmpty()) return null;
		String historical = historicalPredecessor(owner, currentId, output);
		if (historical != null && known.contains(historical) && !saved.has(historical)) return historical;

		int replacements = 0;
		for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
			if (equivalentOutputAndScope(entry.getKey(), entry.getValue(), output, scope)) replacements++;
		}
		if (replacements != 1) return null;

		String retained = null;
		for (Map.Entry<String, JsonElement> entry : saved.entrySet()) {
			if (!equivalentOutputAndScope(entry.getKey(), entry.getValue(), output, scope)) continue;
			if (retained != null) return null;
			retained = entry.getKey();
		}
		return retained != null && declaredPredecessor(owner, currentId, output, retained, saved.getAsJsonObject(retained))
				? retained : null;
	}

	private static boolean declaredPredecessor(String owner, String currentId, String output, String savedId, JsonObject saved) {
		String importedPrefix = "orespawn:legacy/" + owner + "/";
		boolean imported = savedId.startsWith(importedPrefix) && savedId.length() > importedPrefix.length();
		if (saved.has("source_provider")) {
			if (!owner.equals(text(saved.get("source_provider")))) return false;
		} else if (saved.has("source_mod")) {
			String fallbackOwner = text(saved.get("source_mod"));
			if (!owner.equals(fallbackOwner) && !(imported && "legacy".equals(fallbackOwner))) return false;
		}
		if (imported) return true;
		return savedId.equals(historicalPredecessor(owner, currentId, output));
	}

	private static String historicalPredecessor(String owner, String currentId, String output) {
		// Base Metals' maintained 1.10/1.12 providers declared these IDs themselves.
		// A generic provider's "legacy/" prefix is not evidence of a replacement.
		if (!owner.equals("basemetals")) return null;
		for (String material : BASE_METALS_LEGACY_MATERIALS) {
			if (currentId.equals("basemetals:ore/" + material)
					&& output.equals("basemetals:" + material + "_ore")) return "basemetals:legacy/" + material + "_ore";
		}
		return null;
	}

	private static boolean equivalentOutputAndScope(String id, JsonElement rule, String output, Set<Placement> scope) {
		return rule.isJsonObject() && output.equals(singletonOutput(id, rule.getAsJsonObject()))
				&& scope.equals(placements(rule.getAsJsonObject()));
	}

	private static String singletonOutput(String id, JsonObject rule) {
		String output = rule.has("block") ? text(rule.get("block")) : id;
		Identifier parsed = output == null ? null : parseIdentifier(output);
		if (parsed == null || !defaultMetadata(rule)) return null;
		output = parsed.toString();
		if (rule.has("deep_output") && !output.equals(text(rule.get("deep_output")))) return null;
		if (rule.has("outputs")) {
			JsonElement values = rule.get("outputs");
			if (!values.isJsonArray() || values.getAsJsonArray().size() != 1
					|| !values.getAsJsonArray().get(0).isJsonObject()) return null;
			JsonObject only = values.getAsJsonArray().get(0).getAsJsonObject();
			if (!output.equals(text(only.get("block"))) || !defaultMetadata(only)) return null;
		}
		return output;
	}

	private static boolean defaultMetadata(JsonObject rule) {
		JsonElement metadata = rule.get("metadata");
		return metadata == null || metadata.isJsonPrimitive() && metadata.getAsJsonPrimitive().isNumber()
				&& metadata.getAsBigDecimal().signum() == 0;
	}

	private static Set<Placement> placements(JsonObject rule) {
		Set<Placement> result = new HashSet<>();
		for (String kind : Arrays.asList("dimensions", "dimension_selectors")) {
			JsonElement table = rule.get(kind);
			if (table == null) continue;
			if (!table.isJsonObject()) return null;
			for (Map.Entry<String, JsonElement> entry : table.getAsJsonObject().entrySet()) {
				if (parseIdentifier(entry.getKey()) == null || !entry.getValue().isJsonObject()) return null;
				// Disabled and zero-rate placements are saved choices too.
				result.add(new Placement(kind, entry.getKey(), channel(entry.getValue().getAsJsonObject())));
			}
		}
		return result;
	}

	private static String channel(JsonObject placement) {
		Identifier explicit = identifier(placement.get("placement_channel"));
		if (explicit != null) return explicit.toString();
		JsonElement pattern = placement.get("pattern");
		Identifier type = pattern != null && pattern.isJsonObject()
				? identifier(pattern.getAsJsonObject().get("type")) : null;
		String legacy = text(pattern);
		if (type == null && legacy != null && legacy.indexOf(':') >= 0) type = parseIdentifier(legacy);
		return type == null ? "orespawn:standard" : type.toString();
	}

	private static Identifier parseIdentifier(String value) {
		try { return value == null || value.trim().isEmpty() ? null : Identifier.parse(value); }
		catch (RuntimeException ignored) { return null; }
	}

	private static Identifier identifier(JsonElement value) {
		String name = text(value);
		return name == null ? null : parseIdentifier(name);
	}

	private static String text(JsonElement value) {
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
				? value.getAsString() : null;
	}
}
