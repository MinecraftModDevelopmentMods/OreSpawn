package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

class OreMaterialGroupsTest {
	@Test
	void curatedAliasesUseExactBlockTags() {
		JsonObject root = new JsonObject();
		OreMaterialGroups.initialize(root);
		assertEquals(Identifier.parse("orespawn:sulfur"), OreMaterialGroups.infer(root,
				Arrays.asList("c:ores/sulfur", "c:ores/sulphur")).material);
		assertFalse(OreMaterialGroups.infer(root,
				Arrays.asList("c:ores/sulfur", "c:ores/sulphur")).reviewRequired);
		assertEquals(Identifier.parse("orespawn:aluminum"), OreMaterialGroups.infer(root,
				Arrays.asList("c:ores/aluminum", "c:ores/aluminium")).material);
	}

	@Test
	void importedOreDictionaryAliasesStayDormantWithoutExactTags() {
		JsonObject root = new JsonObject();
		JsonObject groups = new JsonObject();
		JsonObject oldGroup = new JsonObject();
		oldGroup.addProperty("display_name", "Rare stone");
		JsonArray aliases = new JsonArray();
		aliases.add("oreRareStone");
		oldGroup.add(OreMaterialGroups.LEGACY_ALIASES, aliases);
		groups.add("example:rare_stone", oldGroup);
		root.add(OreMaterialGroups.SECTION, groups);
		OreMaterialGroups.initialize(root);
		OreMaterialGroups.Definition definition = OreMaterialGroups.definition(root,
				Identifier.parse("example:rare_stone"));
		assertEquals(Collections.singletonList("oreRareStone"), definition.dormantOreDictionaryEntries);
		assertTrue(definition.blockTagEntries.isEmpty());
		assertEquals(null, OreMaterialGroups.infer(root, Collections.singletonList("oreRareStone")).material);
		assertEquals(null, OreMaterialGroups.infer(root,
				Collections.singletonList("example:rare_stone_ore")).material);
	}

	@Test
	void exactSharedTagIsStableAndGroupOwnershipIsExplicit() {
		JsonObject root = new JsonObject();
		OreMaterialGroups.initialize(root);
		Identifier inferred = OreMaterialGroups.infer(root,
				Collections.singletonList("c:ores/copper")).material;
		assertEquals(Identifier.parse("orespawn:copper"), inferred);
		assertTrue(OreMaterialGroups.ensureDefinition(root, inferred,
				Collections.singletonList("c:ores/copper")));
		assertEquals(Collections.singletonList("c:ores/copper"),
				OreMaterialGroups.definition(root, inferred).blockTagEntries);
		assertFalse(OreMaterialGroups.ensureDefinition(root, inferred,
				Collections.singletonList("c:ores/copper")));
	}
}
