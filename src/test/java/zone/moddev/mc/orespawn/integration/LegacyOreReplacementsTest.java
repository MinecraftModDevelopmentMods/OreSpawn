package zone.moddev.mc.orespawn.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.util.JsonCopies;

class LegacyOreReplacementsTest {
	private static final String PROVIDER = "baseminerals";
	private static final String OLD = "baseminerals:legacy/baseminerals_lithium_ore_0";
	private static final String NATIVE = "baseminerals:legacy/lithium_ore";
	private static final String FALLBACK_POLICY = "orespawn:lithium|orespawn:all_except_nether_end";

	private final JsonObject profile = parse("{\"ores\":{},\"ore_source_policies\":{}}");
	private final JsonObject ores = profile.getAsJsonObject("ores");
	private final JsonObject policies = profile.getAsJsonObject("ore_source_policies");
	private final JsonObject defaults = new JsonObject();
	private final JsonObject manifest = new JsonObject();
	private final Set<String> known = new LinkedHashSet<>(Arrays.asList(OLD));

	LegacyOreReplacementsTest() {
		ores.add(OLD, parse("{\"block\":\"baseminerals:lithium_ore\",\"enabled\":false,"
				+ "\"source_mod\":\"baseminerals\",\"source_provider\":\"baseminerals\","
				+ "\"native_generation\":false,\"outputs\":[{\"block\":\"baseminerals:lithium_ore\"}],"
				+ "\"dimensions\":{\"minecraft:overworld\":{\"frequency\":1.5}}}"));
		defaults.add(NATIVE, parse("{\"block\":\"baseminerals:lithium_ore\",\"enabled\":true,"
				+ "\"dimensions\":{\"minecraft:overworld\":{\"frequency\":4.0}},"
				+ "\"dimension_selectors\":{\"orespawn:all_except_nether_end\":{\"frequency\":4.0}}}"));
	}

	@Test
	void remembersImportAndPreservesItsScopeAndChoicesOnEveryReload() {
		policies.add("existing", parse("{\"mode\":\"consolidated\",\"output_mode\":\"single\",\"outputs\":{\"" + OLD + "\":1}}"));
		JsonObject saved = JsonCopies.copy(profile);
		assertTrue(merge().suppressed.contains(NATIVE));
		assertEquals(OLD, manifest.getAsJsonObject("legacy_ore_replacements").get(NATIVE).getAsString());
		assertEquals(saved, profile);
		assertTrue(LegacyOreReplacements.retains(manifest, OLD, JsonCopies.keys(defaults)));
		assertFalse(merge().changed);
		// Removing a previously tracked import is not permission to add the native default.
		ores.remove(OLD);
		assertTrue(merge().suppressed.contains(NATIVE));
	}

	@Test
	void dropsOnlyTheUntouchedAddedDefaultAndItsGeneratedFallback() {
		addNative();
		policies.add(FALLBACK_POLICY, parse("{\"material\":\"orespawn:lithium\",\"domain\":\"orespawn:all_except_nether_end\","
				+ "\"mode\":\"keep_separate\",\"output_mode\":\"custom\",\"status\":\"separate\","
				+ "\"review_required\":false,\"outputs\":{\"" + NATIVE + "\":1},"
				+ "\"placement_sources\":{\"orespawn:standard\":\"" + NATIVE + "\"}}"));
		JsonObject original = JsonCopies.copy(ores.getAsJsonObject(OLD));
		assertTrue(merge().changed);
		assertFalse(ores.has(NATIVE));
		assertFalse(policies.has(FALLBACK_POLICY));
		assertEquals(original, ores.get(OLD));
		assertFalse(merge().changed);
	}

	@Test
	void retainsAnEditedNativeRuleAndAsksForReview() {
		addNative();
		ores.getAsJsonObject(NATIVE).addProperty("enabled", false);
		JsonObject before = JsonCopies.copy(profile);
		assertFalse(merge().suppressed.contains(NATIVE));
		assertEquals(before, profile);
		assertTrue(manifest.getAsJsonObject("legacy_ore_review").has(NATIVE));
		assertFalse(merge().changed);
	}

	@Test
	void retainsAnUntouchedNativeRuleWhenPlayerPolicyUsesIt() {
		addNative();
		policies.add("existing", parse("{\"mode\":\"consolidated\",\"output_mode\":\"single\",\"outputs\":{\"" + NATIVE + "\":1}}"));
		JsonObject before = JsonCopies.copy(profile);
		assertFalse(merge().suppressed.contains(NATIVE));
		assertEquals(before, profile);
		assertTrue(manifest.getAsJsonObject("legacy_ore_review").has(NATIVE));
	}

	@Test
	void doesNotGuessForMultipleImportsOrMultipleNativeRules() {
		String second = OLD.substring(0, OLD.length() - 1) + "1";
		known.add(second);
		ores.add(second, JsonCopies.copy(ores.get(OLD)));
		addNative();
		assertTrue(merge().suppressed.contains(NATIVE));
		assertTrue(ores.has(NATIVE));
		assertFalse(manifest.has("legacy_ore_replacements"));
		assertTrue(manifest.getAsJsonObject("legacy_ore_review").has(NATIVE));

		ores.remove(second);
		defaults.add("baseminerals:other_lithium", JsonCopies.copy(defaults.get(NATIVE)));
		assertTrue(merge().suppressed.contains(NATIVE));
		assertTrue(ores.has(NATIVE));
		assertFalse(manifest.has("legacy_ore_replacements"));
	}

	@Test
	void doesNotMatchAnUntrackedRuleOrAnotherOutputVariant() {
		known.clear();
		assertFalse(merge().suppressed.contains(NATIVE));
		known.add(OLD);
		defaults.getAsJsonObject(NATIVE).addProperty("metadata", 1);
		assertFalse(merge().suppressed.contains(NATIVE));
		ores.getAsJsonObject(OLD).addProperty("source_provider", "anothermod");
		defaults.getAsJsonObject(NATIVE).remove("metadata");
		assertFalse(merge().suppressed.contains(NATIVE));
		assertFalse(manifest.has("legacy_ore_replacements"));
	}

	@Test
	void recognizesStockDuplicateEvenWhenTheOldConvertedFileIsActive() {
		addNative();
		JsonObject active = new JsonObject();
		active.add(OLD, JsonCopies.copy(ores.get(OLD)));
		assertTrue(LegacyOreReplacements.reconcile(profile, PROVIDER, ores, defaults, active, manifest, known)
				.suppressed.contains(NATIVE));
		assertFalse(ores.has(NATIVE));
	}

	@Test
	void retainsADefaultChangedByAProviderOverride() {
		addNative();
		JsonObject active = JsonCopies.copy(defaults);
		active.getAsJsonObject(NATIVE).addProperty("enabled", false);
		assertFalse(LegacyOreReplacements.reconcile(profile, PROVIDER, ores, defaults, active, manifest, known)
				.suppressed.contains(NATIVE));
		assertTrue(ores.has(NATIVE));
		assertTrue(manifest.getAsJsonObject("legacy_ore_review").has(NATIVE));
	}

	private void addNative() {
		JsonObject nativeRule = JsonCopies.copy(defaults.getAsJsonObject(NATIVE));
		nativeRule.addProperty("source_provider", PROVIDER);
		ores.add(NATIVE, nativeRule);
		known.add(NATIVE);
	}

	private LegacyOreReplacements.Result merge() {
		return LegacyOreReplacements.reconcile(profile, PROVIDER, ores, defaults, defaults, manifest, known);
	}

	private static JsonObject parse(String source) {
		return new JsonParser().parse(source).getAsJsonObject();
	}
}
