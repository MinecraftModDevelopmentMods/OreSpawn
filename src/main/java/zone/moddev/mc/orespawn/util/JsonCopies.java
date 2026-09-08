package zone.moddev.mc.orespawn.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Gson-version-neutral defensive copying for configuration data.
 *
	 * <p>Minecraft 1.11.2 bundles a Gson version where {@code deepCopy()},
	 * primitive {@code JsonArray.add} overloads, and {@code JsonObject.size()}
	 * are not public API.
 * public. Keeping the compatibility shim here avoids changing any public JSON
 * contracts or relying on a newer Gson at runtime.</p>
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

	/** Returns the member count without depending on newer Gson APIs. */
	public static int size(JsonObject source) {
		return source.entrySet().size();
	}

	public static int size(java.util.Collection<?> source) {
		return source.size();
	}

	public static int size(java.util.Map<?, ?> source) {
		return source.size();
	}

	public static void add(JsonObject target, String key, JsonElement value) {
		target.add(key, value);
	}

	public static <T> boolean add(java.util.Collection<T> target, T value) {
		return target.add(value);
	}

	public static void add(JsonArray target, JsonElement value) {
		target.add(value);
	}

	public static void add(JsonArray target, String value) {
		target.add(value == null ? JsonNull.INSTANCE : new JsonPrimitive(value));
	}

	public static void add(JsonArray target, Number value) {
		target.add(value == null ? JsonNull.INSTANCE : new JsonPrimitive(value));
	}

	public static void add(JsonArray target, Boolean value) {
		target.add(value == null ? JsonNull.INSTANCE : new JsonPrimitive(value));
	}

	public static void add(JsonArray target, Character value) {
		target.add(value == null ? JsonNull.INSTANCE : new JsonPrimitive(value));
	}
}
