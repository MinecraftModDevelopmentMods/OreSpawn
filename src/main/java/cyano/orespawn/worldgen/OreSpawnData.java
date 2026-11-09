package cyano.orespawn.worldgen;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

/** Binary-compatible value object used by OreSpawn 1.x consumers. */
@Deprecated
public class OreSpawnData {
	public final float frequency;
	public final int spawnQuantity;
	public final int minY;
	public final int maxY;
	public final int variation;
	public final boolean restrictBiomes;
	public final Set<String> biomesByName;
	public final Block ore;
	public final int metaData;
	public static final OreSpawnData EMPTY_PLACEHOLDER = new OreSpawnData(
			Blocks.STONE, 0, 1, 127, 0F, 1, 1, Collections.<String>emptyList());

	public OreSpawnData(Block ore, int metaData, int spawnQuantity, int variation,
			float frequency, int minY, int maxY, Collection<String> biomes) {
		this.ore = ore;
		this.metaData = metaData;
		this.spawnQuantity = spawnQuantity;
		this.variation = variation;
		this.frequency = frequency;
		this.minY = minY;
		this.maxY = maxY;
		this.restrictBiomes = biomes != null && !biomes.isEmpty();
		this.biomesByName = Collections.unmodifiableSet(biomes == null
				? Collections.<String>emptySet() : new LinkedHashSet<>(biomes));
	}

	public static OreSpawnData parseOreSpawnData(JsonObject value) {
		String id = get("blockID", "minecraft:stone", value);
		Block block = Block.REGISTRY.getObject(new ResourceLocation(id));
		if (block == null) return EMPTY_PLACEHOLDER;
		Set<String> biomes = new LinkedHashSet<>();
		if (value.has("biomes") && value.get("biomes").isJsonArray()) {
			JsonArray values = value.getAsJsonArray("biomes");
			for (int index = 0; index < values.size(); index++) biomes.add(values.get(index).getAsString());
		}
		return new OreSpawnData(block, get("blockMeta", 0, value), get("size", 8, value),
				get("variation", 0, value), get("frequency", 0.5F, value),
				get("minHeight", 0, value), get("maxHeight", 256, value), biomes);
	}

	private static int get(String key, int fallback, JsonObject value) {
		return value.has(key) ? value.get(key).getAsInt() : fallback;
	}

	private static float get(String key, float fallback, JsonObject value) {
		return value.has(key) ? value.get(key).getAsFloat() : fallback;
	}

	private static String get(String key, String fallback, JsonObject value) {
		return value.has(key) ? value.get(key).getAsString() : fallback;
	}

	JsonObject bridgeSpawn(Integer dimension) {
		JsonObject spawn = new JsonObject(); spawn.addProperty("enabled", true);
		spawn.addProperty("feature", "default"); spawn.addProperty("replaces", "default");
		JsonArray blocks = new JsonArray(); JsonObject output = new JsonObject();
		ResourceLocation id = ore == null ? null : ore.getRegistryName();
		output.addProperty("name", id == null ? "minecraft:stone" : id.toString());
		output.addProperty("metadata", metaData); output.addProperty("chance", 100);
		blocks.add(output); spawn.add("blocks", blocks);
		JsonObject parameters = new JsonObject(); parameters.addProperty("size", spawnQuantity);
		parameters.addProperty("variation", variation); parameters.addProperty("frequency", frequency);
		parameters.addProperty("minHeight", minY); parameters.addProperty("maxHeight", maxY);
		spawn.add("parameters", parameters);
		JsonArray dimensions = new JsonArray(); if (dimension != null) dimensions.add(new JsonPrimitive(dimension));
		spawn.add("dimensions", dimensions);
		JsonObject biomeSelection = new JsonObject(); JsonArray included = new JsonArray();
		for (String biome : biomesByName) included.add(new JsonPrimitive(biome));
		biomeSelection.add("includes", included); spawn.add("biomes", biomeSelection);
		return spawn;
	}

	@Override public String toString() {
		return "oreSpawn: [ore=" + ore + "#" + metaData + ",frequency=" + frequency
				+ ",spawnQuantity=" + spawnQuantity + ",variation=+/-" + variation
				+ ",Y-range=" + minY + "-" + maxY + ",restrictBiomes=" + restrictBiomes
				+ ",biomes=" + biomesByName + "]";
	}
}
