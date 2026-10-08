package zone.moddev.mc.orespawn.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/** Keeps configuration copies independent of the Minecraft-bundled Gson version. */
public final class JsonCopies {
	private JsonCopies() {
	}

	@SuppressWarnings("unchecked")
	public static <T extends JsonElement> T copy(T source) {
		if (source == null || source.isJsonNull()) return (T) JsonNull.INSTANCE;
		if (source.isJsonPrimitive()) return source;
		if (source.isJsonArray()) {
			JsonArray result = new JsonArray();
			for (JsonElement value : source.getAsJsonArray()) result.add(copy(value));
			return (T) result;
		}
		JsonObject result = new JsonObject();
		for (java.util.Map.Entry<String, JsonElement> entry : source.getAsJsonObject().entrySet()) {
			result.add(entry.getKey(), copy(entry.getValue()));
		}
		return (T) result;
	}

	public static java.util.Set<String> keys(JsonObject source) {
		java.util.Set<String> result = new java.util.LinkedHashSet<>();
		for (java.util.Map.Entry<String, JsonElement> entry : source.entrySet()) {
			result.add(entry.getKey());
		}
		return result;
	}
}
