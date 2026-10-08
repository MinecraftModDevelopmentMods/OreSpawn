package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

class OreSourcePoliciesTest {
	private static final ResourceLocation SULFUR = id("orespawn:sulfur");
	private static final ResourceLocation OVERWORLD = id("minecraft:overworld");

	@Test
	void newWorldBalancesOnlyTwoLoadedOrdinaryOwnersWithAnExactSharedTag() {
		OreSourcePolicies.Group one = group();
		one.add(candidate("mineralogy:sulfur", "mineralogy", "mineralogy:sulfur_ore",
				"forge:ores/sulfur", true, true, false, false));
		assertSeparate(OreSourcePolicies.createPolicy(one, false));

		OreSourcePolicies.Group differentTag = group();
		differentTag.add(candidate("mineralogy:sulfur", "mineralogy", "mineralogy:sulfur_ore",
				"forge:ores/sulfur", true, true, false, false));
		differentTag.add(candidate("electricadvantage:sulfur", "electricadvantage",
				"electricadvantage:sulfur_ore", "forge:ores/sulphur", true, true, false, false));
		assertSeparate(OreSourcePolicies.createPolicy(differentTag, false));

		OreSourcePolicies.Group shared = group();
		shared.add(candidate("mineralogy:sulfur", "mineralogy", "mineralogy:sulfur_ore",
				"forge:ores/sulfur", true, true, false, false));
		shared.add(candidate("electricadvantage:sulfur", "electricadvantage",
				"electricadvantage:sulfur_ore", "forge:ores/sulfur", true, true, false, false));
		JsonObject balanced = OreSourcePolicies.createPolicy(shared, false);
		assertEquals("consolidated", balanced.get("mode").getAsString());
		assertEquals("balanced", balanced.get("output_mode").getAsString());
		assertEquals(2, balanced.getAsJsonObject("outputs").size());
		assertEquals(1, balanced.getAsJsonObject("placement_sources").size());
		assertEquals("mineralogy:sulfur", balanced.getAsJsonObject("placement_sources")
				.get("orespawn:standard").getAsString());
	}

	@Test
	void oldWorldKeepsOriginalAndExternalOrEnrichmentDoesNotTriggerBalance() {
		OreSourcePolicies.Group group = group();
		group.add(candidate("mineralogy:sulfur", "mineralogy", "mineralogy:sulfur_ore",
				"forge:ores/sulfur", true, true, false, false));
		group.add(candidate("electricadvantage:sulfur", "electricadvantage",
				"electricadvantage:sulfur_ore", "forge:ores/sulfur", true, true, false, false));
		assertSeparate(OreSourcePolicies.createPolicy(group, true));

		OreSourcePolicies.Group onlyExternal = group();
		onlyExternal.add(candidate("mineralogy:sulfur", "mineralogy", "mineralogy:sulfur_ore",
				"forge:ores/sulfur", true, true, false, false));
		onlyExternal.add(candidate("outside:sulfur", "outside", "outside:sulfur_ore",
				"forge:ores/sulfur", true, false, true, false));
		assertSeparate(OreSourcePolicies.createPolicy(onlyExternal, false));

		OreSourcePolicies.Group enrichment = group();
		enrichment.add(candidate("mineralogy:sulfur", "mineralogy", "mineralogy:sulfur_ore",
				"forge:ores/sulfur", true, true, false, false));
		enrichment.add(candidate("densemetals:sulfur", "densemetals", "densemetals:sulfur_ore",
				"forge:ores/sulfur", true, true, false, true));
		assertSeparate(OreSourcePolicies.createPolicy(enrichment, false));
	}

	@Test
	void unloadedFutureProviderCreatesNeitherAnOutputNorAPlacementBudget() {
		OreSourcePolicies.Group group = group();
		group.add(candidate("mineralogy:sulfur", "mineralogy", "mineralogy:sulfur_ore",
				"forge:ores/sulfur", true, true, false, false));
		group.add(candidate("baseminerals:sulfur", "baseminerals", "baseminerals:sulfur_ore",
				"forge:ores/sulfur", false, false, false, false));
		JsonObject policy = OreSourcePolicies.createPolicy(group, false);
		assertSeparate(policy);
		assertFalse(policy.getAsJsonObject("outputs").has("baseminerals:sulfur"));
		assertEquals(1, policy.getAsJsonObject("placement_sources").size());
		assertTrue(policy.getAsJsonObject("placement_sources").has("orespawn:standard"));
	}

	private static void assertSeparate(JsonObject policy) {
		assertEquals("keep_separate", policy.get("mode").getAsString());
	}

	private static OreSourcePolicies.Group group() {
		return new OreSourcePolicies.Group(SULFUR, OVERWORLD);
	}

	private static OreSourcePolicies.Candidate candidate(String source, String owner, String block,
			String tag, boolean loaded, boolean active, boolean external, boolean enrichment) {
		return new OreSourcePolicies.Candidate(source, owner, owner, "1", id(block), 0,
				SULFUR, OVERWORLD, OreSourcePolicies.STANDARD, Collections.singletonList(tag),
				loaded, active, external, enrichment, false, false);
	}

	private static ResourceLocation id(String value) {
		return new ResourceLocation(value);
	}
}
