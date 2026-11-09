package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class BiomeReplacementRulesTest {
	@Test
	void storesExactMappingsInTheReservedTerminalPalette() {
		JsonObject root = new JsonObject();
		BiomeReplacementRules.set(root, "minecraft:overworld", "minecraft:desert", "example:orchard");
		Map<String, String> mappings = BiomeReplacementRules.read(root, "minecraft:overworld");
		assertEquals("example:orchard", mappings.get("minecraft:desert"));
		JsonObject palette = root.getAsJsonObject("biome_palettes")
				.getAsJsonObject(BiomeReplacementRules.paletteId("minecraft:overworld"));
		assertEquals("replace", palette.get("mode").getAsString());
		assertEquals("all", palette.get("scope").getAsString());
		assertEquals(1.0D, palette.get("coverage").getAsDouble());
		assertEquals(0.0D, palette.get("fallback_weight").getAsDouble());
	}

	@Test
	void flattensChainsAndRepointsInboundMappings() {
		JsonObject root = new JsonObject();
		BiomeReplacementRules.set(root, "minecraft:overworld", "example:a", "example:b");
		BiomeReplacementRules.set(root, "minecraft:overworld", "example:b", "example:c");
		Map<String, String> mappings = BiomeReplacementRules.read(root, "minecraft:overworld");
		assertEquals("example:c", mappings.get("example:a"));
		assertEquals("example:c", mappings.get("example:b"));
	}

	@Test
	void rejectsSelfMappingsAndCycles() {
		JsonObject root = new JsonObject();
		assertThrows(IllegalArgumentException.class, () -> BiomeReplacementRules.set(root,
				"minecraft:overworld", "example:a", "example:a"));
		BiomeReplacementRules.set(root, "minecraft:overworld", "example:a", "example:b");
		BiomeReplacementRules.set(root, "minecraft:overworld", "example:b", "example:c");
		assertThrows(IllegalArgumentException.class, () -> BiomeReplacementRules.set(root,
				"minecraft:overworld", "example:c", "example:a"));
	}

	@Test
	void removingLastMappingRemovesOnlyTheReservedPalette() {
		JsonObject root = new JsonObject();
		BiomeReplacementRules.set(root, "minecraft:overworld", "example:a", "missing:target");
		BiomeReplacementRules.remove(root, "minecraft:overworld", "example:a");
		assertTrue(BiomeReplacementRules.read(root, "minecraft:overworld").isEmpty());
		assertFalse(root.getAsJsonObject("biome_palettes")
				.has(BiomeReplacementRules.paletteId("minecraft:overworld")));
	}
}
