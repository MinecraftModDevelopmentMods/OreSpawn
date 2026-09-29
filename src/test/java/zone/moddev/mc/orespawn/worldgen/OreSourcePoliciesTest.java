package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge14TestBootstrap;
import zone.moddev.mc.orespawn.util.JsonCopies;

class OreSourcePoliciesTest {
	@BeforeAll
	static void bootstrapVanilla() {
		Forge14TestBootstrap.registerVanilla();
	}

	@Test
	void exactOreDictionaryNamesAndCuratedSpellingsProduceCanonicalMaterials() {
		OreSourcePolicies.Inference sulfur = OreSourcePolicies.inferMaterial(
				Arrays.asList("oreSulphur", "oreSulfur", "dustSulfur"));
		OreSourcePolicies.Inference aluminum = OreSourcePolicies.inferMaterial(
				Arrays.asList("oreAluminium", "oreAluminum"));

		assertEquals("orespawn:sulfur", sulfur.material.toString());
		assertEquals(Arrays.asList("oreSulfur", "oreSulphur"), sulfur.names);
		assertFalse(sulfur.reviewRequired);
		assertEquals("orespawn:aluminum", aluminum.material.toString());
	}

	@Test
	void niterAndSaltpeterRemainDistinctAndAmbiguousMembershipRequiresReview() {
		assertEquals("orespawn:niter", OreSourcePolicies.inferMaterial(
				Collections.singletonList("oreNiter")).material.toString());
		assertEquals("orespawn:saltpeter", OreSourcePolicies.inferMaterial(
				Collections.singletonList("oreSaltpeter")).material.toString());
		OreSourcePolicies.Inference ambiguous = OreSourcePolicies.inferMaterial(
				Arrays.asList("oreNiter", "oreSaltpeter"));
		assertTrue(ambiguous.material.toString().startsWith("orespawn:review/"));
		assertTrue(ambiguous.reviewRequired);
		assertEquals(null, OreSourcePolicies.inferMaterial(
				Arrays.asList("dustSulfur", "crushedSulfur")).material);
		OreSourcePolicies.Inference invalid = OreSourcePolicies.inferMaterial(
				Collections.singletonList("oreBad Path"));
		assertEquals(null, invalid.material);
		assertFalse(invalid.reviewRequired);
	}

	@Test
	void unrelatedAmbiguousAliasFamiliesReceiveDistinctStableReviewGroups() {
		JsonObject root = root();
		OreMaterialGroups.initialize(root);
		java.util.List<String> adamantineAliases = Arrays.asList(
				"oreAdamantine", "oreAdamantite", "oreAdamantium", "oreAdamant");
		java.util.List<String> reorderedAdamantineAliases = Arrays.asList(
				"oreAdamantium", "oreAdamant", "oreAdamantite", "oreAdamantine");
		java.util.List<String> mercuryAliases = Arrays.asList("oreMercury", "oreQuicksilver");

		OreSourcePolicies.Inference adamantine = OreSourcePolicies.inferMaterial(root,
				adamantineAliases);
		OreSourcePolicies.Inference reordered = OreSourcePolicies.inferMaterial(root,
				reorderedAdamantineAliases);
		OreSourcePolicies.Inference mercury = OreSourcePolicies.inferMaterial(root, mercuryAliases);

		assertTrue(adamantine.reviewRequired);
		assertTrue(mercury.reviewRequired);
		assertEquals(adamantine.material, reordered.material,
				"Alias order must not change the provisional material identity");
		assertNotEquals(adamantine.material, mercury.material,
				"Unrelated ambiguous alias families must never share a review group");

		OreMaterialGroups.ensureDefinition(root, adamantine.material, adamantineAliases);
		OreMaterialGroups.ensureDefinition(root, mercury.material, mercuryAliases);
		OreMaterialGroups.Definition adamantineDefinition = OreMaterialGroups.definition(root,
				adamantine.material);
		OreMaterialGroups.Definition mercuryDefinition = OreMaterialGroups.definition(root,
				mercury.material);
		assertEquals(Arrays.asList("oreAdamant", "oreAdamantine", "oreAdamantite",
				"oreAdamantium"), adamantineDefinition.oreDictionaryEntries);
		assertEquals(Arrays.asList("oreMercury", "oreQuicksilver"),
				mercuryDefinition.oreDictionaryEntries);
		assertEquals("Adamant, Adamantine, Adamantite, Adamantium",
				adamantineDefinition.displayName);
		assertEquals("Mercury, Quicksilver", mercuryDefinition.displayName);
		assertFalse(OreSourcePolicies.inferMaterial(root, adamantineAliases).reviewRequired,
				"Once the alias family exists, accepting it must survive a later rebuild");
	}

	@Test
	void legacySharedReviewGroupIsRemovedSoItsAliasFamiliesCanBeRediscovered() {
		JsonObject root = root();
		JsonObject groups = new JsonObject();
		JsonObject legacy = new JsonObject();
		legacy.addProperty("display_name", "Review required");
		JsonArray aliases = new JsonArray();
		aliases.add(new JsonPrimitive("oreAdamantine"));
		aliases.add(new JsonPrimitive("oreMercury"));
		legacy.add("ore_dictionary_entries", aliases);
		groups.add("orespawn:review_required", legacy);
		root.add(OreMaterialGroups.SECTION, groups);

		assertTrue(OreMaterialGroups.initialize(root));
		assertFalse(root.getAsJsonObject(OreMaterialGroups.SECTION)
				.has("orespawn:review_required"));
	}

	@Test
	void freshCataloguedMmdConflictConsolidatesUsingReviewedPriority() {
		JsonObject root = rootWithSulfurConflict();
		assertTrue(OreSourcePolicies.initialize(root, false));
		JsonObject policy = policy(root);

		assertEquals("consolidated", policy.get("mode").getAsString());
		assertEquals("consolidated", policy.get("status").getAsString());
		assertEquals("balanced", policy.get("output_mode").getAsString());
		assertEquals(2, policy.getAsJsonObject("outputs").entrySet().size());
		assertTrue(policy.getAsJsonObject("outputs").has("mineralogy:sulfur"));
		assertTrue(policy.getAsJsonObject("outputs").has("baseminerals:sulfur"));
		assertEquals("mineralogy:sulfur", policy.getAsJsonObject("placement_sources")
				.get("orespawn:standard").getAsString());
	}

	@Test
	void upgradedWorldConflictPreservesEverySourceUntilUserChooses() {
		JsonObject root = rootWithSulfurConflict();
		OreSourcePolicies.initialize(root, true);
		JsonObject policy = policy(root);

		assertEquals("keep_separate", policy.get("mode").getAsString());
		assertEquals(2, policy.getAsJsonObject("outputs").entrySet().size());
	}

	@Test
	void thirdPartyConflictIsNotAutomaticallyConsolidated() {
		JsonObject root = root();
		addOre(root, "otherone:sulfur", "otherone", "minecraft:iron_ore", "minecraft:overworld");
		addOre(root, "othertwo:sulfur", "othertwo", "minecraft:gold_ore", "minecraft:overworld");
		OreSourcePolicies.initialize(root, false);

		JsonObject policy = policy(root);
		assertEquals("keep_separate", policy.get("mode").getAsString());
		assertEquals("review_required", policy.get("status").getAsString());
	}

	@Test
	void missingSelectionsRemainPersistedAndSnapshotsAreImmutable() {
		JsonObject root = rootWithSulfurConflict();
		OreSourcePolicies.initialize(root, false);
		root.getAsJsonObject("ores").remove("mineralogy:sulfur");
		OreSourcePolicies.initialize(root, false);

		JsonObject policy = policy(root);
		assertTrue(policy.getAsJsonObject("outputs").has("mineralogy:sulfur"));
		assertEquals("missing_source", policy.get("status").getAsString());
		boolean missing = false;
		for (JsonElement element : policy.getAsJsonArray("candidates")) {
			JsonObject candidate = element.getAsJsonObject();
			if ("mineralogy:sulfur".equals(candidate.get("source_id").getAsString())) {
				missing = !candidate.get("loaded").getAsBoolean();
			}
		}
		assertTrue(missing);
		OreSourcePolicies.Snapshot snapshot = OreSourcePolicies.snapshot(root);
		assertThrows(UnsupportedOperationException.class, () -> snapshot.groups().clear());
		assertThrows(UnsupportedOperationException.class,
				() -> snapshot.groups().get(0).outputs.put("replacement", 2.0D));

		addOre(root, "mineralogy:sulfur", "mineralogy", "minecraft:iron_ore",
				"minecraft:overworld");
		OreSourcePolicies.initialize(root, false);
		for (JsonElement element : policy(root).getAsJsonArray("candidates")) {
			JsonObject candidate = element.getAsJsonObject();
			if ("mineralogy:sulfur".equals(candidate.get("source_id").getAsString())) {
				assertTrue(candidate.get("loaded").getAsBoolean());
			}
		}
	}

	@Test
	void lithiumUsesItsReviewedMmdPriority() {
		JsonObject root = root();
		addOre(root, "electricadvantage:lithium", "electricadvantage",
				"minecraft:gold_ore", "orespawn:lithium", "minecraft:overworld", null);
		addOre(root, "baseminerals:lithium", "baseminerals",
				"minecraft:iron_ore", "orespawn:lithium", "minecraft:overworld", null);
		OreSourcePolicies.initialize(root, false);
		JsonObject policy = policy(root);
		assertEquals("consolidated", policy.get("mode").getAsString());
		assertTrue(policy.getAsJsonObject("outputs").has("baseminerals:lithium"));
	}

	@Test
	void placementChannelsStayIndependentWhileOutputsRemainMaterialWide() {
		JsonObject root = rootWithSulfurConflict();
		addOre(root, "electricadvantage:sulfur_district", "electricadvantage",
				"minecraft:coal_ore", "orespawn:sulfur", "minecraft:overworld",
				"realisticdeposits:district");
		OreSourcePolicies.initialize(root, false);
		JsonObject policy = policy(root);
		assertEquals(2, policy.getAsJsonObject("placement_sources").entrySet().size());
		assertEquals("mineralogy:sulfur", policy.getAsJsonObject("placement_sources")
				.get("orespawn:standard").getAsString());
		assertEquals("electricadvantage:sulfur_district", policy.getAsJsonObject("placement_sources")
				.get("realisticdeposits:district").getAsString());
		assertEquals(3, policy.getAsJsonObject("outputs").entrySet().size());
	}

	@Test
	void catalogExcludesScienceAndSeparatesDenseEnrichment() {
		JsonObject root = root();
		addOre(root, "mineralogy:sulfur", "mineralogy", "minecraft:iron_ore",
				"minecraft:overworld");
		addOre(root, "densemetals:dense_sulfur", "densemetals", "minecraft:gold_ore",
				"minecraft:overworld");
		addOre(root, "basesciences:sulfur", "basesciences", "minecraft:coal_ore",
				"minecraft:overworld");
		OreSourcePolicies.initialize(root, false);
		JsonObject policy = policy(root);
		assertEquals("keep_separate", policy.get("mode").getAsString());
		assertEquals(2, policy.getAsJsonArray("candidates").size());
		assertFalse(policy.getAsJsonObject("outputs").has("densemetals:dense_sulfur"));
		boolean enrichment = false;
		for (JsonElement element : policy.getAsJsonArray("candidates")) {
			JsonObject candidate = element.getAsJsonObject();
			enrichment |= "densemetals:dense_sulfur".equals(
					candidate.get("source_id").getAsString())
					&& candidate.get("enrichment").getAsBoolean();
		}
		assertTrue(enrichment);
	}

	@Test
	void netherAndEndCatalogCandidatesCannotLeakIntoOtherDomains() {
		JsonObject root = root();
		addOre(root, "nethermetals:sulfur", "nethermetals", "minecraft:gold_ore",
				"orespawn:sulfur", "minecraft:overworld", null);
		addOre(root, "endmetals:sulfur", "endmetals", "minecraft:coal_ore",
				"orespawn:sulfur", "minecraft:overworld", null);
		OreSourcePolicies.initialize(root, false);
		assertTrue(root.getAsJsonObject(OreSourcePolicies.SECTION).entrySet().isEmpty());

		addOre(root, "nethermetals:sulfur", "nethermetals", "minecraft:gold_ore",
				"orespawn:sulfur", "minecraft:the_nether", null);
		addOre(root, "baseminerals:sulfur", "baseminerals", "minecraft:iron_ore",
				"orespawn:sulfur", "minecraft:the_nether", null);
		OreSourcePolicies.initialize(root, false);
		JsonObject policy = policy(root);
		assertEquals("minecraft:the_nether", policy.get("domain").getAsString());
	}

	@Test
	void customPatternsDefaultToTheirOwnPlacementChannel() {
		JsonObject custom = new JsonObject();
		JsonObject pattern = new JsonObject();
		pattern.addProperty("type", "realisticdeposits:district");
		pattern.add("settings", new JsonObject());
		custom.add("pattern", pattern);
		assertEquals("realisticdeposits:district",
				OreSourcePolicies.placementChannel(custom).toString());
		pattern.addProperty("type", "orespawn:registered_custom");
		assertEquals("orespawn:registered_custom",
				OreSourcePolicies.placementChannel(custom).toString());

		JsonObject builtIn = new JsonObject();
		builtIn.addProperty("pattern", "vein");
		assertEquals("orespawn:standard", OreSourcePolicies.placementChannel(builtIn).toString());
	}

	@Test
	void sameVanillaOutputWithComplementaryPlacementRulesIsNotAConflict() {
		JsonObject root = root();
		addOre(root, "minecraft:gold_ore", "minecraft", "minecraft:gold_ore",
				"orespawn:gold", "minecraft:overworld", null);
		addOre(root, "orespawn:vanilla_gold_badlands", "minecraft", "minecraft:gold_ore",
				"orespawn:gold", "minecraft:overworld", null);

		OreSourcePolicies.initialize(root, false);

		assertEquals(1, root.getAsJsonObject(OreSourcePolicies.SECTION).entrySet().size());
		OreSourcePolicies.GroupView group = OreSourcePolicies.snapshot(root).groups().get(0);
		assertEquals("separate", group.status,
				"Complementary normal and Badlands rules are one harmless material group");
		assertEquals(1, group.outputs.size());
	}

	@Test
	void sameOutputAcrossIndependentPlacementChannelsIsNotAConflict() {
		JsonObject root = root();
		addOre(root, "minecraft:iron_ore", "minecraft", "minecraft:iron_ore",
				"orespawn:iron", "minecraft:overworld", null);
		addOre(root, "realisticdeposits:overworld_iron", "realisticdeposits", "minecraft:iron_ore",
				"orespawn:iron", "minecraft:overworld", "realisticdeposits:stratiform_seam");

		OreSourcePolicies.initialize(root, false);

		assertEquals(1, root.getAsJsonObject(OreSourcePolicies.SECTION).entrySet().size());
		OreSourcePolicies.GroupView group = OreSourcePolicies.snapshot(root).groups().get(0);
		assertEquals("separate", group.status,
				"A placement engine using the same output remains an independent channel");
		assertEquals(1, group.outputs.size());
		assertEquals(2, group.placements.size());
	}

	@Test
	void differentOwnersInTheSameChannelStillCompeteForOnePlacementBudget() {
		JsonObject root = root();
		addOre(root, "otherone:sulfur", "otherone", "minecraft:gold_ore",
				"orespawn:sulfur", "minecraft:overworld", null);
		addOre(root, "othertwo:sulfur", "othertwo", "minecraft:gold_ore",
				"orespawn:sulfur", "minecraft:overworld", null);

		OreSourcePolicies.initialize(root, false);

		assertEquals(1, root.getAsJsonObject(OreSourcePolicies.SECTION).entrySet().size());
		assertEquals("review_required", policy(root).get("status").getAsString());
	}

	@Test
	void duplicateRegistryStatesAppearAsOneSelectableOutput() {
		JsonObject root = root();
		addOre(root, "otherone:sulfur", "otherone", "minecraft:gold_ore",
				"orespawn:sulfur", "minecraft:overworld", null);
		addOre(root, "othertwo:sulfur", "othertwo", "minecraft:gold_ore",
				"orespawn:sulfur", "minecraft:overworld", null);

		OreSourcePolicies.initialize(root, false);

		assertEquals(1, policy(root).getAsJsonObject("outputs").entrySet().size());
		assertEquals(2, policy(root).getAsJsonArray("candidates").size(),
				"the placement candidates remain auditable even when their output state is shared");
	}

	@Test
	void snapshotsOrderFriendlyMaterialGroupsDeterministically() {
		JsonObject root = root();
		addOre(root, "example:zinc", "example", "minecraft:gold_ore",
				"orespawn:zinc", "minecraft:overworld", null);
		addOre(root, "example:copper", "example", "minecraft:iron_ore",
				"orespawn:copper", "minecraft:overworld", null);
		OreSourcePolicies.initialize(root, true);

		java.util.List<OreSourcePolicies.GroupView> groups = OreSourcePolicies.snapshot(root).groups();
		assertEquals("orespawn:copper", groups.get(0).material.toString());
		assertEquals("orespawn:zinc", groups.get(1).material.toString());
	}

	@Test
	void separateLegacyPolicyForOneLoadedOutputIsHiddenWithoutBeingDeleted() {
		JsonObject root = root();
		addOre(root, "minecraft:gold_ore", "minecraft", "minecraft:gold_ore",
				"orespawn:gold", "minecraft:overworld", null);
		addOre(root, "orespawn:vanilla_gold_badlands", "minecraft", "minecraft:iron_ore",
				"orespawn:gold", "minecraft:overworld", null);
		OreSourcePolicies.initialize(root, true);
		JsonObject policies = root.getAsJsonObject(OreSourcePolicies.SECTION);
		assertEquals(1, policies.entrySet().size());

		root.getAsJsonObject("ores").getAsJsonObject("orespawn:vanilla_gold_badlands")
				.addProperty("block", "minecraft:gold_ore");
		OreSourcePolicies.initialize(root, true);

		assertEquals(1, policies.entrySet().size(), "Existing profile data must not be rewritten away");
		assertEquals(1, OreSourcePolicies.snapshot(root).groups().size(),
				"All Groups must retain harmless single-output material groups");
	}

	@Test
	void inactiveManagedProviderRemainsAnOutputWithoutOwningPlacement() {
		JsonObject root = root();
		addOre(root, "mineralogy:sulfur", "mineralogy", "minecraft:iron_ore",
				"orespawn:sulfur", "minecraft:overworld", null);
		addOre(root, "electricadvantage:sulfur", "electricadvantage", "minecraft:gold_ore",
				"orespawn:sulfur", "minecraft:overworld", null);
		root.getAsJsonObject("ores").getAsJsonObject("electricadvantage:sulfur")
				.addProperty("enabled", false);

		OreSourcePolicies.initialize(root, false);

		JsonObject policy = policy(root);
		assertEquals("balanced", policy.get("output_mode").getAsString());
		assertEquals(2, policy.getAsJsonObject("outputs").entrySet().size());
		assertEquals("mineralogy:sulfur", policy.getAsJsonObject("placement_sources")
				.get("orespawn:standard").getAsString());
		boolean outputOnly = false;
		for (JsonElement element : policy.getAsJsonArray("candidates")) {
			JsonObject candidate = element.getAsJsonObject();
			if ("electricadvantage:sulfur".equals(candidate.get("source_id").getAsString())) {
				outputOnly = candidate.get("loaded").getAsBoolean()
						&& !candidate.get("placement_active").getAsBoolean()
						&& !candidate.get("external").getAsBoolean();
			}
		}
		assertTrue(outputOnly);
	}

	@Test
	void staleMissingExternalDuplicateDoesNotKeepManagedOutputRed() {
		JsonObject root = root();
		addOre(root, "mineralogy:sulfur", "mineralogy", "minecraft:iron_ore",
				"orespawn:sulfur", "minecraft:overworld", null);
		addOre(root, "electricadvantage:sulfur", "electricadvantage", "minecraft:gold_ore",
				"orespawn:sulfur", "minecraft:overworld", null);
		root.getAsJsonObject("ores").getAsJsonObject("electricadvantage:sulfur")
				.addProperty("enabled", false);
		OreSourcePolicies.initialize(root, false);

		JsonObject policy = policy(root);
		JsonObject external = null;
		for (JsonElement element : policy.getAsJsonArray("candidates")) {
			JsonObject candidate = element.getAsJsonObject();
			if ("electricadvantage:sulfur".equals(candidate.get("source_id").getAsString())) {
				external = JsonCopies.copy(candidate);
			}
		}
		assertTrue(external != null);
		external.addProperty("source_id", "external/minecraft:gold_ore/0");
		external.addProperty("loaded", false);
		external.addProperty("placement_active", false);
		external.addProperty("external", true);
		policy.getAsJsonArray("candidates").add(external);
		policy.addProperty("mode", "keep_separate");
		policy.addProperty("output_mode", "single");
		policy.addProperty("review_required", false);
		policy.addProperty("status", "external_generation");

		assertTrue(OreSourcePolicies.initialize(root, false));
		assertEquals("separate", policy(root).get("status").getAsString());
		assertEquals("separate", OreSourcePolicies.snapshot(root).groups().get(0).status);
		assertEquals(3, policy(root).getAsJsonArray("candidates").size(),
				"The missing historical candidate remains available for deterministic restoration");
	}

	@Test
	void materialGroupsInferExactNamesAndPreserveCuratedEdits() {
		JsonObject root = root();
		JsonObject groups = new JsonObject();
		JsonObject sulfur = new JsonObject();
		sulfur.addProperty("display_name", "Brimstone");
		JsonArray aliases = new JsonArray();
		aliases.add(new JsonPrimitive("oreSulfur"));
		sulfur.add("ore_dictionary_entries", aliases);
		groups.add("orespawn:sulfur", sulfur);
		root.add(OreMaterialGroups.SECTION, groups);
		addOre(root, "example:cobalt", "example", "minecraft:coal_ore",
				"minecraft:overworld");
		root.getAsJsonObject("ores").getAsJsonObject("example:cobalt").remove("material");
		// The test block has no cobalt dictionary entry; exercise inference directly.
		OreSourcePolicies.Inference cobalt = OreSourcePolicies.inferMaterial(root,
				Collections.singletonList("oreCobalt"));
		assertEquals("orespawn:cobalt", cobalt.material.toString());

		OreMaterialGroups.initialize(root);
		JsonObject savedSulfur = root.getAsJsonObject(OreMaterialGroups.SECTION)
				.getAsJsonObject("orespawn:sulfur");
		assertEquals("Brimstone", savedSulfur.get("display_name").getAsString());
		assertEquals(1, savedSulfur.getAsJsonArray("ore_dictionary_entries").size(),
				"curated aliases return only through the explicit Reset Defaults action");
	}

	private static JsonObject rootWithSulfurConflict() {
		JsonObject root = root();
		addOre(root, "mineralogy:sulfur", "mineralogy", "minecraft:iron_ore", "minecraft:overworld");
		addOre(root, "baseminerals:sulfur", "baseminerals", "minecraft:gold_ore", "minecraft:overworld");
		return root;
	}

	private static JsonObject root() {
		JsonObject root = new JsonObject();
		root.add("ores", new JsonObject());
		return root;
	}

	private static void addOre(JsonObject root, String id, String owner, String block, String dimension) {
		addOre(root, id, owner, block, "orespawn:sulfur", dimension, null);
	}

	private static void addOre(JsonObject root, String id, String owner, String block,
			String material, String dimension, String channel) {
		JsonObject ore = new JsonObject();
		ore.addProperty("enabled", true);
		ore.addProperty("block", block);
		ore.addProperty("material", material);
		ore.addProperty("source_provider", owner);
		JsonObject dimensions = new JsonObject();
		JsonObject rule = new JsonObject();
		rule.addProperty("enabled", true);
		rule.addProperty("pattern", "vein");
		if (channel != null) rule.addProperty("placement_channel", channel);
		dimensions.add(dimension, rule);
		ore.add("dimensions", dimensions);
		root.getAsJsonObject("ores").add(id, ore);
	}

	private static JsonObject policy(JsonObject root) {
		JsonObject policies = root.getAsJsonObject(OreSourcePolicies.SECTION);
		assertEquals(1, policies.entrySet().size());
		return policies.entrySet().iterator().next().getValue().getAsJsonObject();
	}
}
