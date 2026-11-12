package zone.moddev.mc.orespawn.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RetainedProviderOreDefaultsTest {

	private Map<String, Object> providers;
	private Map<String, Object> previous;
	private JsonObject provider;
	private JsonObject profile;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void installOnlyTheFixtureProvider() throws Exception {
		var field = WorldgenIntegrationManager.class.getDeclaredField("ACTIVE_PROVIDERS");
		field.setAccessible(true);
		providers = (Map<String, Object>) field.get(null);
		previous = new LinkedHashMap<>(providers);
		providers.clear();
		provider = fixture("current-basemetals-ores.json");
		profile = fixture("retained-basemetals-ores.json");
		JsonObject policy = JsonParser.parseString("""
				{"mode":"keep_separate","outputs":{"basemetals:tin_ore":1.0},
				 "placement_sources":{"orespawn:standard":"basemetals:legacy/tin_ore"}}
				""").getAsJsonObject();
		JsonObject policies = new JsonObject();
		policies.add("orespawn:tin|minecraft:overworld", policy);
		profile.add("ore_source_policies", policies);
		activate("basemetals", provider);
	}

	@AfterEach
	void restoreOtherProviderTests() {
		if (providers != null && previous != null) {
			providers.clear();
			providers.putAll(previous);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {"coldiron", "adamantine", "starsteel", "tin", "lead", "zinc", "silver", "mercury", "nickel", "platinum"})
	void retainsEachSavedBaseMetalsRuleAndAddsACompleteDisabledAlternative(String material) {
		JsonObject before = profile.deepCopy();
		JsonObject providerBefore = provider.deepCopy();
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		JsonObject alternative = ores(profile).getAsJsonObject("basemetals:ore/" + material);
		assertFalse(alternative.get("enabled").getAsBoolean(), material + " must not get a second placement pass");
		JsonObject expected = ores(provider).getAsJsonObject("basemetals:ore/" + material).deepCopy();
		expected.addProperty("enabled", false);
		expected.addProperty("source_provider", "basemetals");
		assertEquals(expected, alternative, "The alternative remains complete and editable");
		assertSavedRules(before);
		assertEquals(before.get("ore_source_policies"), profile.get("ore_source_policies"));
		assertEquals(providerBefore, provider, "Merging must not change packaged provider defaults");
	}

	@Test
	void disabledAndZeroFrequencySavedChoicesAreNotReenabled() {
		JsonObject saved = ores(profile).getAsJsonObject("basemetals:legacy/coldiron_ore");
		saved.addProperty("enabled", false);
		JsonObject placement = saved.getAsJsonObject("dimensions").getAsJsonObject("minecraft:the_nether");
		placement.addProperty("enabled", false);
		placement.addProperty("frequency", 0);
		JsonObject before = profile.deepCopy();
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		assertFalse(ores(profile).getAsJsonObject("basemetals:ore/coldiron").get("enabled").getAsBoolean());
		assertSavedRules(before);
	}

	@Test
	void aUserMayEnableTheAlternativeAndThatChoiceSurvivesRepeatedMerges() {
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		ores(profile).getAsJsonObject("basemetals:ore/coldiron").addProperty("enabled", true);
		JsonObject chosen = profile.deepCopy();
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		assertEquals(chosen, profile);
		assertTrue(ores(profile).getAsJsonObject("basemetals:ore/coldiron").get("enabled").getAsBoolean());
	}

	@Test
	void anExistingExplicitModernRuleIsNeverChanged() {
		JsonObject chosen = ores(provider).getAsJsonObject("basemetals:ore/coldiron").deepCopy();
		chosen.getAsJsonObject("dimensions").getAsJsonObject("minecraft:the_nether").addProperty("frequency", 0.125);
		ores(profile).add("basemetals:ore/coldiron", chosen.deepCopy());
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		assertEquals(chosen, ores(profile).get("basemetals:ore/coldiron"));
	}

	@Test
	void anIntentionallyRemovedKnownAlternativeIsNotRecreated() {
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		ores(profile).remove("basemetals:ore/coldiron");
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		assertFalse(ores(profile).has("basemetals:ore/coldiron"));
	}

	@Test
	void deletingAKnownHistoricalRuleDoesNotEnableItsReplacement() {
		ores(profile).remove("basemetals:legacy/coldiron_ore");
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		assertFalse(ores(profile).has("basemetals:legacy/coldiron_ore"));
		assertFalse(ores(profile).getAsJsonObject("basemetals:ore/coldiron").get("enabled").getAsBoolean());
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 2})
	void unrelatedMatchingRowsDoNotOverrideAKnownHistoricalDeletion(int count) {
		ores(profile).remove("basemetals:legacy/coldiron_ore");
		Map<String, JsonObject> custom = new LinkedHashMap<>();
		for (int index = 0; index < count; index++) {
			JsonObject row = ores(provider).getAsJsonObject("basemetals:ore/coldiron").deepCopy();
			row.addProperty("source_provider", "anothermod");
			String id = "anothermod:custom/coldiron_" + index;
			custom.put(id, row.deepCopy());
			ores(profile).add(id, row);
		}
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		assertFalse(ores(profile).getAsJsonObject("basemetals:ore/coldiron").get("enabled").getAsBoolean());
		custom.forEach((id, row) -> assertEquals(row, ores(profile).get(id)));
	}

	@Test
	void aSecondCurrentDepositDoesNotOverrideAnExplicitHistoricalDeletion() throws Exception {
		ores(profile).remove("basemetals:legacy/coldiron_ore");
		ores(provider).add("basemetals:ore/extra_coldiron", ores(provider).get("basemetals:ore/coldiron").deepCopy());
		activate("basemetals", provider);
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		assertFalse(ores(profile).getAsJsonObject("basemetals:ore/coldiron").get("enabled").getAsBoolean());
		assertTrue(ores(profile).getAsJsonObject("basemetals:ore/extra_coldiron").get("enabled").getAsBoolean());
	}

	@Test
	void aRuleThatWasNeverKnownStillGetsItsNormalDefault() {
		ores(profile).remove("basemetals:legacy/coldiron_ore");
		profile.remove("providers");
		assertCurrentColdIronEnabled();
	}

	@Test
	void freshProfilesKeepAllCurrentProviderDefaults() {
		profile = new JsonObject();
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		for (var entry : ores(provider).entrySet()) {
			JsonObject expected = entry.getValue().getAsJsonObject().deepCopy();
			expected.addProperty("source_provider", "basemetals");
			assertEquals(expected, ores(profile).get(entry.getKey()));
		}
	}

	@Test
	void aCustomRuleOrOrphanMarkerAloneDoesNotDeclareALegacyReplacement() {
		JsonObject old = ores(profile).remove("basemetals:legacy/coldiron_ore").getAsJsonObject();
		// No historical deletion is recorded in this unrelated-rule fixture.
		profile.remove("providers");
		old.addProperty("orphaned_provider", true);
		ores(profile).add("basemetals:custom/extra_coldiron", old);
		assertCurrentColdIronEnabled();
	}

	@Test
	void foreignOwnerProvenanceDoesNotDisableThisProvidersDefaults() {
		JsonObject old = ores(profile).remove("basemetals:legacy/coldiron_ore").getAsJsonObject();
		profile.remove("providers");
		old.addProperty("source_provider", "anothermod");
		ores(profile).add("anothermod:legacy/coldiron_ore", old);
		assertCurrentColdIronEnabled();
	}

	@Test
	void conflictingExplicitOwnerIsNotTreatedAsAnOwnedImport() {
		ores(profile).getAsJsonObject("basemetals:legacy/coldiron_ore").addProperty("source_provider", "anothermod");
		assertCurrentColdIronEnabled();
	}

	@Test
	void oreSpawnImporterIdsAlsoCarryPositiveOwnerProvenance() {
		JsonObject old = ores(profile).remove("basemetals:legacy/coldiron_ore").getAsJsonObject();
		profile.remove("providers");
		old.remove("source_provider");
		ores(profile).add("orespawn:legacy/basemetals/coldiron_ore", old);
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		assertFalse(ores(profile).getAsJsonObject("basemetals:ore/coldiron").get("enabled").getAsBoolean());
	}

	@ParameterizedTest
	@ValueSource(strings = {"orespawn:legacy/examplemod/diamond", "examplemod:legacy/diamond"})
	void genericImportsAreRecognisedButAnArbitraryProvidersLegacyPrefixIsNot(String savedId) throws Exception {
		JsonObject current = ores(provider).getAsJsonObject("basemetals:ore/coldiron").deepCopy();
		current.addProperty("block", "minecraft:diamond_ore");
		current.addProperty("source_mod", "examplemod");
		JsonObject defaults = new JsonObject();
		JsonObject currentOres = new JsonObject();
		currentOres.add("examplemod:ore/diamond", current);
		defaults.add("ores", currentOres);
		providers.clear();
		activate("examplemod", defaults);
		JsonObject saved = current.deepCopy();
		JsonObject savedOres = new JsonObject();
		savedOres.add(savedId, saved);
		profile = new JsonObject();
		profile.add("ores", savedOres);
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		assertEquals(!savedId.startsWith("orespawn:legacy/"),
				ores(profile).getAsJsonObject("examplemod:ore/diamond").get("enabled").getAsBoolean());
		assertEquals(saved, ores(profile).get(savedId));
	}

	@ParameterizedTest
	@ValueSource(strings = {"legacy", "basemetals", "anothermod"})
	void fallbackOwnershipMustNotConflictWithTheImportedOwner(String fallbackOwner) {
		JsonObject old = ores(profile).remove("basemetals:legacy/coldiron_ore").getAsJsonObject();
		profile.remove("providers");
		old.remove("source_provider");
		old.addProperty("source_mod", fallbackOwner);
		ores(profile).add("orespawn:legacy/basemetals/coldiron_ore", old);
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		assertEquals(fallbackOwner.equals("anothermod"),
				ores(profile).getAsJsonObject("basemetals:ore/coldiron").get("enabled").getAsBoolean());
	}

	@Test
	void explicitProviderOwnershipTakesPrecedenceOverTheFallbackOwner() {
		ores(profile).getAsJsonObject("basemetals:legacy/coldiron_ore").addProperty("source_mod", "anothermod");
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		assertFalse(ores(profile).getAsJsonObject("basemetals:ore/coldiron").get("enabled").getAsBoolean());
	}

	@Test
	void separateDimensionAndPlacementChannelDepositsRemainEnabled() {
		JsonObject old = ores(profile).getAsJsonObject("basemetals:legacy/coldiron_ore");
		JsonObject placement = old.getAsJsonObject("dimensions").remove("minecraft:the_nether").getAsJsonObject();
		old.getAsJsonObject("dimensions").add("minecraft:the_end", placement);
		assertCurrentColdIronEnabled();
		old.getAsJsonObject("dimensions").remove("minecraft:the_end");
		old.getAsJsonObject("dimensions").add("minecraft:the_nether", placement);
		placement.addProperty("placement_channel", "anothermod:enrichment");
		assertCurrentColdIronEnabled();
	}

	@Test
	void multipleCurrentPlacementsForOneOutputAreNotGuessedAway() throws Exception {
		ores(provider).add("basemetals:ore/extra_coldiron", ores(provider).get("basemetals:ore/coldiron").deepCopy());
		activate("basemetals", provider);
		assertCurrentColdIronEnabled();
		assertTrue(ores(profile).getAsJsonObject("basemetals:ore/extra_coldiron").get("enabled").getAsBoolean());
	}

	@Test
	void multipleLegacyPlacementsForOneOutputAreNotGuessedAway() {
		ores(profile).add("basemetals:legacy/extra_coldiron", ores(profile).get("basemetals:legacy/coldiron_ore").deepCopy());
		assertCurrentColdIronEnabled();
	}

	@Test
	void weightedMixedOutputsAndMetadataVariantsAreNotEquivalent() {
		JsonObject old = ores(profile).getAsJsonObject("basemetals:legacy/coldiron_ore");
		JsonArray outputs = old.getAsJsonArray("outputs");
		JsonObject extra = outputs.get(0).getAsJsonObject().deepCopy();
		extra.addProperty("block", "basemetals:tin_ore");
		outputs.add(extra);
		assertCurrentColdIronEnabled();
		outputs.remove(1);
		old.addProperty("metadata", 1);
		assertCurrentColdIronEnabled();
	}

	@Test
	void differentDeepOutputsOrNamespacedPatternChannelsAreNotEquivalent() {
		JsonObject old = ores(profile).getAsJsonObject("basemetals:legacy/coldiron_ore");
		old.addProperty("deep_output", "basemetals:tin_ore");
		assertCurrentColdIronEnabled();
		old.remove("deep_output");
		JsonObject placement = old.getAsJsonObject("dimensions").getAsJsonObject("minecraft:the_nether");
		placement.remove("placement_channel");
		placement.add("pattern", JsonParser.parseString("{\"type\":\"anothermod:enrichment\"}"));
		assertCurrentColdIronEnabled();
	}

	@Test
	void emptyOrMalformedPlacementScopesAreNotEquivalent() {
		JsonObject old = ores(profile).getAsJsonObject("basemetals:legacy/coldiron_ore");
		old.add("dimensions", new JsonObject());
		assertCurrentColdIronEnabled();
	}

	private void assertCurrentColdIronEnabled() {
		WorldgenIntegrationManager.mergeProviderDefinitions(profile);
		assertTrue(ores(profile).getAsJsonObject("basemetals:ore/coldiron").get("enabled").getAsBoolean());
		ores(profile).remove("basemetals:ore/coldiron");
		profile.remove("providers");
		profile.remove("ore_providers");
	}

	private void assertSavedRules(JsonObject before) {
		for (var entry : ores(before).entrySet()) {
			JsonObject expected = entry.getValue().getAsJsonObject().deepCopy();
			JsonObject actual = ores(profile).getAsJsonObject(entry.getKey()).deepCopy();
			expected.remove("orphaned_provider");
			actual.remove("orphaned_provider");
			assertEquals(expected, actual, entry.getKey());
		}
	}

	private void activate(String owner, JsonObject defaults) throws Exception {
		var type = Class.forName(WorldgenIntegrationManager.class.getName() + "$ProviderDefinition");
		var constructor = type.getDeclaredConstructor(String.class, int.class, JsonObject.class);
		constructor.setAccessible(true);
		providers.put(owner, constructor.newInstance(owner, 1, defaults));
	}

	private static JsonObject ores(JsonObject root) { return root.getAsJsonObject("ores"); }

	private static JsonObject fixture(String name) throws Exception {
		try (var input = RetainedProviderOreDefaultsTest.class.getResourceAsStream("/provider-merge/" + name)) {
			assertNotNull(input);
			try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		}
	}
}
