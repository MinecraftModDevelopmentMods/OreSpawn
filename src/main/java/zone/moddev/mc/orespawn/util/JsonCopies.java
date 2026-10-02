package zone.moddev.mc.orespawn.util;

import zone.moddev.mc.orespawn.util.JsonCopies;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/**
 * Copies configuration JSON without exposing the original mutable data.
 *
 * <p>Minecraft 1.13.2's Gson has no public {@code deepCopy()}. Keep that workaround here
 * so callers do not need a newer Gson or a different JSON format.</p>
 */
public final class JsonCopies {
	private JsonCopies() {
	}

	@SuppressWarnings("unchecked")
	public static <T extends JsonElement> T copy(T source) {
		if (source == null || source.isJsonNull()) {
			return (T) JsonNull.INSTANCE;
		}
		if (source.isJsonPrimitive()) {
			return source;
		}
		if (source.isJsonArray()) {
			JsonArray result = new JsonArray();
			for (JsonElement value : source.getAsJsonArray()) {
				result.add(copy(value));
			}
			return (T) result;
		}
		JsonObject result = new JsonObject();
		for (java.util.Map.Entry<String, JsonElement> entry : source.getAsJsonObject().entrySet()) {
			result.add(entry.getKey(), copy(entry.getValue()));
		}
		return (T) result;
	}

	/** Returns keys without depending on Gson's newer {@code JsonObject.keySet()} API. */
	public static java.util.Set<String> keys(JsonObject source) {
		java.util.Set<String> result = new java.util.LinkedHashSet<>();
		for (java.util.Map.Entry<String, JsonElement> entry : source.entrySet()) {
			result.add(entry.getKey());
		}
		return result;
	}
}
