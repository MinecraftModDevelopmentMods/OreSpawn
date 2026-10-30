package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MmdOreConflictCatalogTest {
	@Test
	void futureProvidersOnlyBalanceWhenBothLoadedAndSharingAnExactTag() {
		Map<String, Set<String>> loaded = new LinkedHashMap<>();
		loaded.put("baseminerals", tags("forge:ores/sulfur"));
		assertFalse(MmdOreConflictCatalog.automaticallyBalance(loaded));
		loaded.put("electricadvantage", tags("forge:ores/sulfur"));
		assertTrue(MmdOreConflictCatalog.automaticallyBalance(loaded));
		loaded.put("electricadvantage", tags("forge:ores/sulphur"));
		assertFalse(MmdOreConflictCatalog.automaticallyBalance(loaded));
	}

	@Test
	void unrelatedExternalAndEnrichmentProvidersDoNotTriggerBalancing() {
		Map<String, Set<String>> loaded = new LinkedHashMap<>();
		loaded.put("basemetals", tags("forge:ores/copper"));
		loaded.put("externalgenerator", tags("forge:ores/copper"));
		loaded.put("densemetals", tags("forge:ores/copper"));
		assertFalse(MmdOreConflictCatalog.automaticallyBalance(loaded));
		loaded.put("modernmetals", tags("forge:ores/copper"));
		assertTrue(MmdOreConflictCatalog.automaticallyBalance(loaded));
	}

	@Test
	void establishedPrioritiesAndDimensionProvidersRemainDistinct() {
		assertEquals(Arrays.asList("mineralogy", "baseminerals", "electricadvantage"),
				MmdOreConflictCatalog.priority("sulfur"));
		assertEquals(Arrays.asList("baseminerals", "electricadvantage"),
				MmdOreConflictCatalog.priority("lithium"));
		assertEquals(Collections.emptyList(), MmdOreConflictCatalog.priority("copper"));
		assertTrue(MmdOreConflictCatalog.netherOnly("nethermetals"));
		assertTrue(MmdOreConflictCatalog.endOnly("endmetals"));
		assertFalse(MmdOreConflictCatalog.ordinary("densemetals"));
	}

	private static Set<String> tags(String tag) {
		return new LinkedHashSet<>(Collections.singletonList(tag));
	}
}
