package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceCandidate;
import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceGroup;

class OreSourcePlacementScreenTest {
	@Test
	void placementRulesExcludeExternalMissingAndOutputOnlyCandidates() {
		OreSourceCandidate managed = candidate("managed", "orespawn:standard", true, true, false);
		OreSourceCandidate second = candidate("second", "orespawn:standard", true, true, false);
		OreSourceCandidate external = candidate("external", "orespawn:standard", true, true, true);
		OreSourceCandidate missing = candidate("missing", "orespawn:standard", false, true, false);
		OreSourceCandidate outputOnly = candidate("output", "realisticdeposits:district", true, false, false);
		OreSourceGroup group = group("consolidated",
				Arrays.asList(managed, second, external, missing, outputOnly),
				Collections.singletonMap("lost:channel", "gone"));

		List<OreSourceCandidate> standard = OreSourceListScreen.placementCandidates(
				group, "orespawn:standard");
		assertEquals(Arrays.asList(managed, second), standard);
		assertTrue(OreSourceGroupSettingsScreen.placementSelectable(group, "orespawn:standard"));
		assertFalse(OreSourceGroupSettingsScreen.placementSelectable(group,
				"realisticdeposits:district"));
		assertEquals(Arrays.asList("lost:channel", "orespawn:standard"),
				OreSourceGroupSettingsScreen.placementChannels(group));
	}

	@Test
	void keepOriginalAndSingleManagedSourcesRemainReadOnly() {
		OreSourceCandidate first = candidate("first", "orespawn:standard", true, true, false);
		OreSourceCandidate second = candidate("second", "orespawn:standard", true, true, false);
		assertFalse(OreSourceGroupSettingsScreen.placementSelectable(
				group("consolidated", Collections.singletonList(first), Collections.emptyMap()),
				"orespawn:standard"));
		assertFalse(OreSourceGroupSettingsScreen.placementSelectable(
				group("keep_separate", Arrays.asList(first, second), Collections.emptyMap()),
				"orespawn:standard"));
		assertTrue(group("keep_separate", Collections.singletonList(first),
				Collections.emptyMap()).hasManagedPlacementSource());
		OreSourceCandidate outputOnly = candidate("output", "orespawn:standard",
				true, false, false);
		assertFalse(group("keep_separate", Collections.singletonList(outputOnly),
				Collections.emptyMap()).hasManagedPlacementSource());
	}

	@Test
	void customGroupActionsAndPlacementChoiceCountsDescribeTheCurrentState() {
		OreSourceCandidate first = candidate("first", "orespawn:standard", true, true, false);
		OreSourceCandidate second = candidate("second", "orespawn:standard", true, true, false);
		OreSourceGroup empty = customGroup(Collections.emptyList(), Collections.emptyList());
		OreSourceGroup populated = customGroup(Collections.singletonList("oreTest"),
				Arrays.asList(first, second));
		OreSourceGroup discovered = group("keep_separate", "review_required",
				Collections.singletonList("oreTest"), Arrays.asList(first, second));

		assertTrue(empty.isCustom());
		assertTrue(empty.canDeleteEmpty());
		assertFalse(empty.canDissolve());
		assertTrue(populated.isCustom());
		assertFalse(populated.canDeleteEmpty());
		assertTrue(populated.canDissolve());
		assertFalse(discovered.isCustom());
		assertFalse(discovered.canDeleteEmpty());
		assertFalse(discovered.canDissolve());
	}

	@Test
	void groupColoursSeparateRoutineResolvedAndUnresolvedEntries() {
		OreSourceCandidate first = candidate("first", "first:ore", "orespawn:standard",
				true, true, false);
		OreSourceCandidate second = candidate("second", "second:ore", "orespawn:standard",
				true, true, false);
		OreSourceGroup routine = group("keep_separate", "separate",
				Collections.singletonList("oreIron"), Collections.singletonList(first));
		OreSourceGroup resolved = group("consolidated", "consolidated",
				Arrays.asList("oreSulfur", "oreSulphur"), Arrays.asList(first, second));
		OreSourceGroup unresolved = group("keep_separate", "review_required",
				Arrays.asList("oreSulfur", "oreSulphur"), Arrays.asList(first, second));

		assertTrue(routine.isRoutineSingleSource());
		assertFalse(OreSourceListScreen.showByDefault(routine));
		assertEquals(0x55FF55, OreSourceListScreen.groupRowColor(routine));
		assertFalse(resolved.needsAttention());
		assertTrue(OreSourceListScreen.showByDefault(resolved));
		assertEquals(0xFFFF55, OreSourceListScreen.groupRowColor(resolved));
		assertTrue(unresolved.needsAttention());
		assertTrue(unresolved.needsReview());
		assertFalse(resolved.needsReview());
		assertTrue(OreSourceListScreen.showByDefault(unresolved));
		assertEquals(0xFF5555, OreSourceListScreen.groupRowColor(unresolved));
	}

	private static OreSourceCandidate candidate(String source, String channel,
			boolean loaded, boolean active, boolean external) {
		return candidate(source, "owner:ore", channel, loaded, active, external);
	}

	private static OreSourceCandidate candidate(String source, String registry, String channel,
			boolean loaded, boolean active, boolean external) {
		return new OreSourceCandidate(source, "owner", "Owner", "1", registry, 0,
				channel, Collections.singletonList("oreTest"), loaded, active,
				external, false, false);
	}

	private static OreSourceGroup group(String mode, List<OreSourceCandidate> candidates,
			java.util.Map<String, String> placements) {
		return new OreSourceGroup("orespawn:test|minecraft:overworld", "orespawn:test",
				"minecraft:overworld", "Test", Collections.singletonList("oreTest"), false,
				mode, "balanced", "consolidated", candidates,
				Collections.unmodifiableMap(new LinkedHashMap<>()),
				Collections.unmodifiableMap(new LinkedHashMap<>(placements)));
	}

	private static OreSourceGroup group(String mode, String status, List<String> aliases,
			List<OreSourceCandidate> candidates) {
		return new OreSourceGroup("orespawn:test|minecraft:overworld", "orespawn:test",
				"minecraft:overworld", "Test", aliases, false, mode, "balanced", status,
				candidates, Collections.unmodifiableMap(new LinkedHashMap<>()),
				Collections.emptyMap());
	}

	private static OreSourceGroup customGroup(List<String> aliases,
			List<OreSourceCandidate> candidates) {
		return new OreSourceGroup("orespawn:custom/custom_group|minecraft:overworld",
				"orespawn:custom/custom_group", "minecraft:overworld", "Custom Group",
				aliases, false, "keep_separate", "custom", "review_required", candidates,
				Collections.emptyMap(), Collections.emptyMap());
	}
}
