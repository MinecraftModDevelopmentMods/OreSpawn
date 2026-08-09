package zone.moddev.mc.orespawn.api;

import com.mojang.serialization.Codec;

import com.google.gson.JsonObject;

/** Shared bounded settings understood by OreSpawn's six built-in patterns. */
public final class StandardPatternSettings {
	public static final Codec<StandardPatternSettings> CODEC = Codec.of(element -> {
		JsonObject json = element == null || !element.isJsonObject()
				? new JsonObject() : element.getAsJsonObject();
		return new StandardPatternSettings(integer(json, "spread", 8),
				integer(json, "vertical_spread", 4), integer(json, "node_size", 4),
				integer(json, "length", 16), string(json, "fluid", "minecraft:water"));
	});

	private final int spread;
	private final int verticalSpread;
	private final int nodeSize;
	private final int length;
	private final String fluid;

	public StandardPatternSettings(int spread, int verticalSpread, int nodeSize, int length, String fluid) {
		this.spread = bounded(spread, 0, 64);
		this.verticalSpread = bounded(verticalSpread, 0, 64);
		this.nodeSize = bounded(nodeSize, 1, 32);
		this.length = bounded(length, 1, 64);
		this.fluid = fluid;
	}

	public int spread() { return spread; }
	public int verticalSpread() { return verticalSpread; }
	public int nodeSize() { return nodeSize; }
	public int length() { return length; }
	public String fluid() { return fluid; }

	private static int bounded(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static int integer(JsonObject json, String key, int fallback) {
		return json.has(key) ? json.get(key).getAsInt() : fallback;
	}

	private static String string(JsonObject json, String key, String fallback) {
		return json.has(key) ? json.get(key).getAsString() : fallback;
	}
}
