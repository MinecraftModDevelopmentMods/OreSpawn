package zone.moddev.mc.orespawn.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;

class WorldgenIntegrationManagerTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		Forge12TestBootstrap.registerVanilla();
	}

	@Test
	void acceptsAValidFluidProvider() {
		assertDoesNotThrow(() -> WorldgenIntegrationManager.validateProvider(
				"examplemod", provider("minecraft:water", true, 3)));
	}

	@Test
	void rejectsImpossibleFluidOutputs() {
		assertThrows(JsonSyntaxException.class, () -> WorldgenIntegrationManager.validateProvider(
				"examplemod", provider("minecraft:air", true, 3)));
		assertThrows(JsonSyntaxException.class, () -> WorldgenIntegrationManager.validateProvider(
				"examplemod", provider("minecraft:stone", true, 3)));
		assertThrows(JsonSyntaxException.class, () -> WorldgenIntegrationManager.validateProvider(
				"examplemod", provider("missingmod:not_here", true, 3)));
	}

	@Test
	void rejectsHostlessAndPreSchemaThreeFluidRules() {
		assertThrows(JsonSyntaxException.class, () -> WorldgenIntegrationManager.validateProvider(
				"examplemod", provider("minecraft:water", false, 3)));
		assertThrows(JsonSyntaxException.class, () -> WorldgenIntegrationManager.validateProvider(
				"examplemod", provider("minecraft:water", true, 2)));
	}

	@Test
	void rejectsDuplicateFluidOwnership() {
		Map<String, String> owners = new HashMap<>();
		JsonObject first = provider("minecraft:water", true, 3);
		WorldgenIntegrationManager.claimOwnedEntries("examplemod", first, owners);
		assertThrows(JsonSyntaxException.class,
				() -> WorldgenIntegrationManager.claimOwnedEntries("anothermod", first, owners));
	}

	@Test
	void legacyOilTemplateAdaptsTheOnlyFluidDeposit() {
		JsonObject root = provider("minecraft:water", true, 3);
		JsonObject profile = new JsonObject();
		JsonObject oil = new JsonObject();
		oil.addProperty("frequency", 0.25D);
		oil.addProperty("min_radius", 7);
		profile.add("oil", oil);
		profile.addProperty("place_crude_oil", true);

		WorldgenIntegrationManager.applyLegacyTemplateOil(root, profile);

		JsonObject dimension = root.getAsJsonObject("fluid_deposits")
				.getAsJsonObject("examplemod:fluid_deposit/test")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld");
		assertEquals(0.25D, dimension.get("frequency").getAsDouble());
		assertEquals(7, dimension.get("min_radius").getAsInt());
		assertTrue(root.get("place_fluid_deposits").getAsBoolean());
		assertFalse(root.has("place_crude_oil"));
		assertFalse(root.has("oil"));
	}

	@Test
	void validatesFixedAndRangedOreQuantitiesWithRangePrecedence() {
		JsonObject fixed = new JsonObject();
		fixed.addProperty("quantity", 8);
		assertEquals(8, WorldgenIntegrationManager.validateQuantityRange(fixed)[0]);
		assertEquals(8, WorldgenIntegrationManager.validateQuantityRange(fixed)[1]);

		JsonObject ranged = new JsonObject();
		ranged.addProperty("quantity", 64);
		ranged.addProperty("min_quantity", 4);
		ranged.addProperty("max_quantity", 11);
		assertEquals(4, WorldgenIntegrationManager.validateQuantityRange(ranged)[0]);
		assertEquals(11, WorldgenIntegrationManager.validateQuantityRange(ranged)[1]);

		JsonObject incomplete = new JsonObject();
		incomplete.addProperty("min_quantity", 4);
		assertThrows(JsonSyntaxException.class,
				() -> WorldgenIntegrationManager.validateQuantityRange(incomplete));
	}

	@Test
	void validatesBiomePaletteAndDimensionMaterialsOnlyInSchemaFour() {
		JsonObject provider = biomeProvider(4, "minecraft:water");
		assertDoesNotThrow(() -> WorldgenIntegrationManager.validateProvider("examplemod", provider));

		JsonObject oldSchema = biomeProvider(3, "minecraft:water");
		assertThrows(JsonSyntaxException.class,
				() -> WorldgenIntegrationManager.validateProvider("examplemod", oldSchema));

		JsonObject solidFluid = biomeProvider(4, "minecraft:stone");
		assertThrows(JsonSyntaxException.class,
				() -> WorldgenIntegrationManager.validateProvider("examplemod", solidFluid));
	}

	@Test
	void sourceArbitrationFieldsRequireProviderSchemaFive() {
		assertDoesNotThrow(() -> WorldgenIntegrationManager.validateProvider(
				"examplemod", oreSourceProvider(5)));
		assertThrows(JsonSyntaxException.class, () -> WorldgenIntegrationManager.validateProvider(
				"examplemod", oreSourceProvider(4)));
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void providerCanDeferNewEntriesForExistingWorldsWithoutChangingNewWorldDefaults()
			throws Exception {
		JsonObject root = new JsonObject();
		root.addProperty("schema_version", 4);
		root.addProperty("provider_modid", "realisticdeposits");
		root.addProperty("provider_revision", 1);
		root.addProperty("merge_new_entries_into_existing_worlds", false);
		JsonObject ores = new JsonObject();
		ores.add("realisticdeposits:deposit/minecraft/iron_ore", new JsonObject());
		root.add("ores", ores);

		Class<?> definitionType = Class.forName(
				"zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager$ProviderDefinition");
		Constructor<?> constructor = definitionType.getDeclaredConstructor(
				String.class, int.class, JsonObject.class);
		constructor.setAccessible(true);
		Object provider = constructor.newInstance("realisticdeposits", 1, root);
		Field activeField = WorldgenIntegrationManager.class.getDeclaredField("ACTIVE_PROVIDERS");
		activeField.setAccessible(true);
		Map active = (Map) activeField.get(null);
		Map saved = new LinkedHashMap(active);
		try {
			active.clear();
			active.put("realisticdeposits", provider);
			JsonObject existingWorld = new JsonObject();
			WorldgenIntegrationManager.mergeProviderDefinitionsIntoExistingWorld(existingWorld);
			assertFalse(existingWorld.getAsJsonObject("ores")
					.has("realisticdeposits:deposit/minecraft/iron_ore"));

			JsonObject newWorld = new JsonObject();
			WorldgenIntegrationManager.mergeProviderDefinitions(newWorld);
			assertTrue(newWorld.getAsJsonObject("ores")
					.has("realisticdeposits:deposit/minecraft/iron_ore"));
		} finally {
			active.clear();
			active.putAll(saved);
		}
	}

	private static JsonObject provider(String block, boolean withHost, int schema) {
		JsonObject root = new JsonObject();
		root.addProperty("schema_version", schema);
		root.addProperty("provider_modid", "examplemod");
		root.addProperty("provider_revision", 1);
		JsonObject rule = new JsonObject();
		rule.addProperty("enabled", true);
		rule.addProperty("min_y", -48);
		rule.addProperty("max_y", 32);
		rule.addProperty("frequency", 0.05D);
		rule.addProperty("min_radius", 4);
		rule.addProperty("max_radius", 10);
		rule.addProperty("min_vertical_radius", 2);
		rule.addProperty("max_vertical_radius", 4);
		rule.addProperty("max_lobes", 3);
		rule.addProperty("min_solid_cover", 2);
		rule.add("host_families", new JsonArray());
		rule.add("host_blocks", new JsonArray());
		JsonArray tags = new JsonArray();
		if (withHost) tags.add(new JsonPrimitive("forge:stone"));
		rule.add("host_tags", tags);
		JsonObject dimensions = new JsonObject();
		dimensions.add("minecraft:overworld", rule);
		JsonObject deposit = new JsonObject();
		deposit.addProperty("enabled", true);
		deposit.addProperty("block", block);
		deposit.add("dimensions", dimensions);
		JsonObject deposits = new JsonObject();
		deposits.add("examplemod:fluid_deposit/test", deposit);
		root.add("fluid_deposits", deposits);
		return root;
	}

	private static JsonObject biomeProvider(int schema, String fluid) {
		JsonObject root = new JsonObject();
		root.addProperty("schema_version", schema);
		root.addProperty("provider_modid", "examplemod");
		root.addProperty("provider_revision", 1);

		JsonObject placement = new JsonObject();
		placement.addProperty("enabled", true);
		placement.addProperty("weight", 1.0D);
		placement.add("similar_biomes", new JsonArray());
		placement.add("required_similar_biomes", new JsonArray());
		JsonObject biomes = new JsonObject();
		biomes.add("minecraft:plains", placement);
		JsonObject palette = new JsonObject();
		palette.addProperty("dimension", "minecraft:overworld");
		palette.addProperty("enabled", true);
		palette.addProperty("mode", "augment");
		palette.addProperty("scope", "minecraft_only");
		palette.addProperty("region_size", "average");
		palette.addProperty("coverage", 1.0D);
		palette.addProperty("fallback_weight", 1.0D);
		palette.add("include_namespaces", new JsonArray());
		palette.add("exclude_namespaces", new JsonArray());
		palette.add("biomes", biomes);
		JsonObject palettes = new JsonObject();
		palettes.add("examplemod:palette/test", palette);
		root.add("biome_palettes", palettes);

		JsonObject material = new JsonObject();
		material.addProperty("dimension", "minecraft:overworld");
		material.addProperty("enabled", true);
		material.addProperty("default_fluid", fluid);
		JsonObject materials = new JsonObject();
		materials.add("examplemod:materials/test", material);
		root.add("dimension_materials", materials);
		return root;
	}

	private static JsonObject oreSourceProvider(int schema) {
		JsonObject root = new JsonObject();
		root.addProperty("schema_version", schema);
		root.addProperty("provider_modid", "examplemod");
		root.addProperty("provider_revision", 1);
		JsonObject rule = new JsonObject();
		// This test exercises only the schema gate. Pattern decoding is covered by
		// the provider validation tests that install the OreSpawn pattern registry.
		rule.addProperty("enabled", false);
		rule.addProperty("min_y", 0);
		rule.addProperty("max_y", 64);
		rule.addProperty("frequency", 1.0D);
		rule.addProperty("quantity", 8);
		rule.addProperty("placement_channel", "orespawn:standard");
		JsonArray hosts = new JsonArray();
		hosts.add(new JsonPrimitive("minecraft:stone"));
		rule.add("host_blocks", hosts);
		JsonObject ore = new JsonObject();
		ore.addProperty("block", "minecraft:iron_ore");
		ore.addProperty("material", "orespawn:iron");
		JsonObject dimensions = new JsonObject();
		dimensions.add("minecraft:overworld", rule);
		ore.add("dimensions", dimensions);
		JsonObject ores = new JsonObject();
		ores.add("examplemod:iron", ore);
		root.add("ores", ores);
		return root;
	}
}
