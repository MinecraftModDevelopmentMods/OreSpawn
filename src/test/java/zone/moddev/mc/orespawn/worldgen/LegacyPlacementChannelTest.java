package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

class LegacyPlacementChannelTest {

	@Test
	void namespacedBuiltInPatternsUseTheOrdinaryChannel() {
		for (String name : new String[] {
				"default", "vein", "normal_cloud", "precision", "clusters", "underfluids" }) {
			JsonObject rule = rule("orespawn:" + name);
			String before = rule.toString();

			assertEquals(OreSourcePolicies.STANDARD, OreSourcePolicies.placementChannel(rule), name);
			assertEquals(before, rule.toString(), "Channel inference must not rewrite imported settings");
		}
	}

	@Test
	void builtInPatternObjectsUseTheSameOrdinaryChannel() {
		for (String name : new String[] {
				"default", "vein", "normal_cloud", "precision", "clusters", "underfluids" }) {
			JsonObject rule = objectRule("orespawn:" + name);
			String before = rule.toString();

			assertEquals(OreSourcePolicies.STANDARD, OreSourcePolicies.placementChannel(rule), name);
			assertEquals(before, rule.toString());
		}
	}

	@Test
	void thirdPartyAndUnrecognisedOreSpawnPatternsKeepTheirOwnChannels() {
		for (String pattern : new String[] {
				"realisticdeposits:stratiform_seam", "example:default", "example:vein",
				"orespawn:registered_custom" }) {
			ResourceLocation expected = new ResourceLocation(pattern);

			assertEquals(expected, OreSourcePolicies.placementChannel(rule(pattern)));
			assertEquals(expected, OreSourcePolicies.placementChannel(objectRule(pattern)));
		}
	}

	@Test
	void explicitChannelsAlwaysWinIncludingTheOldDefaultName() {
		for (String channel : new String[] {
				"example:custom", "orespawn:default", "orespawn:standard" }) {
			for (JsonObject rule : new JsonObject[] {
					rule("orespawn:default"), objectRule("orespawn:vein"),
					rule("realisticdeposits:stratiform_seam") }) {
				rule.addProperty("placement_channel", channel);
				String before = rule.toString();

				assertEquals(new ResourceLocation(channel), OreSourcePolicies.placementChannel(rule));
				assertEquals(before, rule.toString());
			}
		}
	}

	@Test
	void bareLegacyNamesAndMissingPatternsKeepTheirExistingFallback() {
		assertEquals(OreSourcePolicies.STANDARD, OreSourcePolicies.placementChannel(new JsonObject()));

		for (String name : new String[] { "default", "vein", "cloud", "cluster", "under-fluid" }) {
			assertEquals(OreSourcePolicies.STANDARD, OreSourcePolicies.placementChannel(rule(name)));
		}
	}

	@Test
	void oldSourceSelectionsOnlyMatchTheirOriginalPattern() {
		JsonObject profile = selectionProfile("orespawn:vein");
		String before = profile.toString();

		assertTrue(selected(profile).isEmpty(), "An unrelated old channel must not activate a source");
		assertEquals(before, profile.toString());
	}

	@Test
	void aNewExplicitChannelDoesNotResurrectAnOldSelection() {
		JsonObject profile = selectionProfile("orespawn:default");
		profile.getAsJsonObject("ores").getAsJsonObject("orespawn:legacy/iron")
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
				.addProperty("placement_channel", "orespawn:standard");

		assertTrue(selected(profile).isEmpty());
	}

	@Test
	void aNewStandardSelectionTakesPrecedenceWithoutFallingBack() {
		JsonObject profile = selectionProfile("orespawn:default");
		profile.getAsJsonObject(OreSourcePolicies.SECTION).getAsJsonObject("orespawn:iron|minecraft:overworld")
				.getAsJsonObject("placement_sources").addProperty("orespawn:standard", "missing:iron");

		assertTrue(selected(profile).isEmpty());
	}

	@Test
	void aSavedInferredChannelStillSelectsTheSameSourceWithoutRewritingIt() {
		JsonObject profile = selectionProfile("orespawn:default");
		String before = profile.toString();
		Map<ResourceLocation, ResourceLocation> choices = selected(profile);

		assertEquals(1, choices.size());
		assertEquals(new ResourceLocation("orespawn:legacy/iron"),
				choices.get(new ResourceLocation("orespawn:default")));
		assertEquals(before, profile.toString());
	}

	private static JsonObject selectionProfile(String savedChannel) {
		JsonObject dimension = rule("orespawn:default");
		JsonObject dimensions = new JsonObject();
		dimensions.add("minecraft:overworld", dimension);
		JsonObject ore = new JsonObject();
		ore.add("dimensions", dimensions);
		JsonObject ores = new JsonObject();
		ores.add("orespawn:legacy/iron", ore);

		JsonObject placements = new JsonObject();
		placements.addProperty(savedChannel, "orespawn:legacy/iron");
		JsonObject policy = new JsonObject();
		policy.addProperty("mode", "consolidated");
		policy.add("placement_sources", placements);
		JsonObject policies = new JsonObject();
		policies.add("orespawn:iron|minecraft:overworld", policy);

		JsonObject profile = new JsonObject();
		profile.add("ores", ores);
		profile.add(OreSourcePolicies.SECTION, policies);
		return profile;
	}

	private static Map<ResourceLocation, ResourceLocation> selected(JsonObject profile) {
		Map<ResourceLocation, ResourceLocation> available = new LinkedHashMap<>();
		available.put(new ResourceLocation("orespawn:legacy/iron"), OreSourcePolicies.STANDARD);
		return OreSpawnOreGeneration.selectedPlacementRules(profile,
				new ResourceLocation("orespawn:iron"), WorldIds.OVERWORLD, available);
	}

	private static JsonObject rule(String pattern) {
		JsonObject rule = new JsonObject();
		rule.addProperty("pattern", pattern);
		return rule;
	}

	private static JsonObject objectRule(String type) {
		JsonObject pattern = new JsonObject();
		pattern.addProperty("type", type);
		pattern.add("settings", new JsonObject());
		JsonObject rule = new JsonObject();
		rule.add("pattern", pattern);
		return rule;
	}
}
