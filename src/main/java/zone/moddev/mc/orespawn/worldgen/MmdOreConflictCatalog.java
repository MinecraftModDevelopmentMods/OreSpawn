package zone.moddev.mc.orespawn.worldgen;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Names known MMD ore providers; it never creates an ore or a placement rule. */
final class MmdOreConflictCatalog {
	private static final Set<String> ORDINARY = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
			"basemetals", "modernmetals", "basegems", "baseminerals", "fantasymetals",
			"electricadvantage", "steamadvantage", "poweradvantage", "mineralogy")));
	private static final Map<String, List<String>> PRIORITIES = priorities();

	private MmdOreConflictCatalog() { }

	static boolean ordinary(String owner) {
		return ORDINARY.contains(owner);
	}

	static boolean enrichment(String owner) {
		return "densemetals".equals(owner);
	}

	static boolean netherOnly(String owner) {
		return "nethermetals".equals(owner);
	}

	static boolean endOnly(String owner) {
		return "endmetals".equals(owner);
	}

	static List<String> priority(String material) {
		return PRIORITIES.getOrDefault(material, Collections.emptyList());
	}

	/** Automatic consolidation requires two loaded ordinary MMD providers sharing an exact ore tag. */
	static boolean automaticallyBalance(Map<String, ? extends Set<String>> tagsByOwner) {
		Map<String, Set<String>> ownersByTag = new HashMap<>();
		for (Map.Entry<String, ? extends Set<String>> entry : tagsByOwner.entrySet()) {
			if (!ordinary(entry.getKey())) continue;
			for (String tag : entry.getValue()) {
				if (!OreMaterialGroups.validTag(tag)) continue;
				ownersByTag.computeIfAbsent(tag, ignored -> new HashSet<>()).add(entry.getKey());
			}
		}
		for (Set<String> owners : ownersByTag.values()) if (owners.size() >= 2) return true;
		return false;
	}

	private static Map<String, List<String>> priorities() {
		Map<String, List<String>> result = new LinkedHashMap<>();
		result.put("sulfur", Collections.unmodifiableList(Arrays.asList(
				"mineralogy", "baseminerals", "electricadvantage")));
		result.put("lithium", Collections.unmodifiableList(Arrays.asList(
				"baseminerals", "electricadvantage")));
		return Collections.unmodifiableMap(result);
	}
}
