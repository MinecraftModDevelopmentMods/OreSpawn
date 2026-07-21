package com.mcmoddev.orespawn.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

/** Normalizes the pre-schema-5 singleton oil fields into named deposit rules. */
final class FluidDepositMigration {
	private FluidDepositMigration() {
	}

	static boolean normalize(JsonObject root) {
		boolean changed = false;
		boolean master = bool(root, "place_fluid_deposits", bool(root, "place_crude_oil", false));
		if (!root.has("place_fluid_deposits")) {
			root.addProperty("place_fluid_deposits", master);
			changed = true;
		}

		JsonObject deposits;
		if (root.has("fluid_deposits") && root.get("fluid_deposits").isJsonObject()) {
			deposits = root.getAsJsonObject("fluid_deposits");
		} else {
			deposits = new JsonObject();
			root.add("fluid_deposits", deposits);
			changed = true;
		}

		if (root.has("oil") && root.get("oil").isJsonObject()) {
			JsonObject oil = root.getAsJsonObject("oil");
			ResourceLocation block = resource(string(oil, "block", "minecraft:air"));
			if (block != null && !"minecraft:air".equals(block.toString())) {
				ResourceLocation ruleId = new ResourceLocation(block.getNamespace(),
						"fluid_deposit/" + block.getPath());
				if (!deposits.has(ruleId.toString())) {
					deposits.add(ruleId.toString(), legacyRule(block, oil));
					changed = true;
				}
			}
		}

		if (root.remove("place_crude_oil") != null) changed = true;
		if (root.remove("oil") != null) changed = true;
		return changed;
	}

	private static JsonObject legacyRule(ResourceLocation block, JsonObject oil) {
		JsonObject deposit = new JsonObject();
		deposit.addProperty("enabled", true);
		deposit.addProperty("block", block.toString());
		JsonObject dimensions = new JsonObject();
		JsonObject overworld = new JsonObject();
		overworld.addProperty("enabled", true);
		copyNumber(oil, overworld, "min_y", -48);
		copyNumber(oil, overworld, "max_y", 48);
		copyNumber(oil, overworld, "frequency", 0.08D);
		copyNumber(oil, overworld, "min_radius", 5);
		copyNumber(oil, overworld, "max_radius", 12);
		copyNumber(oil, overworld, "min_vertical_radius", 2);
		copyNumber(oil, overworld, "max_vertical_radius", 5);
		copyNumber(oil, overworld, "max_lobes", 4);
		copyNumber(oil, overworld, "min_solid_cover", 2);
		JsonArray families = new JsonArray();
		families.add("sedimentary");
		overworld.add("host_families", families);
		overworld.add("host_blocks", new JsonArray());
		overworld.add("host_tags", new JsonArray());
		overworld.add("biome_ids", new JsonArray());
		overworld.add("excluded_biome_ids", new JsonArray());
		JsonArray dictionary = new JsonArray();
		dictionary.add("OCEAN");
		overworld.add("biome_dictionary", dictionary);
		overworld.add("excluded_biome_dictionary", new JsonArray());
		overworld.add("geomes", new JsonObject());
		dimensions.add("minecraft:overworld", overworld);
		deposit.add("dimensions", dimensions);
		return deposit;
	}

	private static void copyNumber(JsonObject source, JsonObject target, String key, Number fallback) {
		if (source.has(key) && source.get(key).isJsonPrimitive()
				&& source.getAsJsonPrimitive(key).isNumber()) {
			target.add(key, source.get(key).deepCopy());
		} else {
			target.addProperty(key, fallback);
		}
	}

	private static boolean bool(JsonObject root, String key, boolean fallback) {
		try {
			return root.has(key) ? root.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static String string(JsonObject root, String key, String fallback) {
		try {
			return root.has(key) ? root.get(key).getAsString() : fallback;
		} catch (RuntimeException ignored) {
			return fallback;
		}
	}

	private static ResourceLocation resource(String value) {
		try {
			return new ResourceLocation(value);
		} catch (RuntimeException ignored) {
			return null;
		}
	}
}
