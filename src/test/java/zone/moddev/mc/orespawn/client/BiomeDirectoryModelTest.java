package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;

import net.minecraft.init.Biomes;

class BiomeDirectoryModelTest {
	@BeforeAll
	static void registerVanilla() {
		Forge12TestBootstrap.registerVanilla();
	}

	@Test
	void derivesVanillaRoutineDimensionsFromForgeBiomeTypes() {
		assertEquals("minecraft:overworld", BiomeDirectoryModel.routineDimension(Biomes.BEACH));
		assertEquals("minecraft:the_nether", BiomeDirectoryModel.routineDimension(Biomes.HELL));
		assertEquals("minecraft:the_end", BiomeDirectoryModel.routineDimension(Biomes.SKY));
	}

	@Test
	void classifiesManagedLayeredDisabledReplacedUnmanagedAndMissingBiomes() {
		JsonObject profile = root();
		addPalette(profile, "provider:first", true, "example:managed", "example:layered", "example:disabled");
		profile.getAsJsonObject("biome_palettes").getAsJsonObject("provider:first")
				.getAsJsonObject("biomes").getAsJsonObject("example:disabled")
				.addProperty("enabled", false);
		addPalette(profile, "provider:second", true, "example:layered", "missing:biome");
		BiomeReplacementRules.set(profile, "minecraft:overworld", "example:managed", "example:unmanaged");
		JsonObject defaults = new JsonObject();
		addPalette(defaultsRoot(defaults), "provider:first", true, "example:managed");

		BiomeDirectoryModel.Snapshot snapshot = BiomeDirectoryModel.assemble(profile, defaults,
				new LinkedHashSet<>(Collections.singletonList("provider")), Arrays.asList(
						loaded("example:managed", "Managed"), loaded("example:layered", "Layered"),
						loaded("example:disabled", "Disabled"), loaded("example:unmanaged", "Unmanaged")),
				Collections.singletonList("minecraft:overworld"));
		assertEquals(BiomeDirectoryModel.Status.USER_REPLACED, find(snapshot, "example:managed").status);
		assertEquals(BiomeDirectoryModel.Status.LAYERED, find(snapshot, "example:layered").status);
		assertEquals(BiomeDirectoryModel.Status.DISABLED, find(snapshot, "example:disabled").status);
		assertEquals(BiomeDirectoryModel.Status.UNMANAGED, find(snapshot, "example:unmanaged").status);
		assertEquals(BiomeDirectoryModel.Status.MISSING, find(snapshot, "missing:biome").status);
		assertEquals(4, snapshot.entries("minecraft:overworld", false).size());
		assertEquals(5, snapshot.entries("minecraft:overworld", true).size());
	}

	@Test
	void retainsProfilePaletteOrderAndMarksOnlyLastEnabledSurfaceEffective() {
		JsonObject profile = root();
		addPalette(profile, "z:first", true, "example:managed");
		addPalette(profile, "a:second", true, "example:managed");
		for (String id : Arrays.asList("z:first", "a:second")) {
			profile.getAsJsonObject("biome_palettes").getAsJsonObject(id)
					.getAsJsonObject("biomes").getAsJsonObject("example:managed")
					.add("surface", new JsonObject());
			profile.getAsJsonObject("biome_palettes").getAsJsonObject(id)
					.getAsJsonObject("biomes").getAsJsonObject("example:managed")
					.getAsJsonObject("surface").addProperty("top_block", "minecraft:stone");
		}
		BiomeDirectoryModel.Snapshot snapshot = BiomeDirectoryModel.assemble(profile,
				new JsonObject(), Collections.emptySet(),
				Collections.singletonList(loaded("example:managed", "Managed")),
				Collections.singletonList("minecraft:overworld"));
		assertEquals("z:first", snapshot.palettes.get(0).id);
		assertEquals("a:second", snapshot.palettes.get(1).id);
		assertTrue(!find(snapshot, "example:managed").placements.get(0).effectiveSurface);
		assertTrue(find(snapshot, "example:managed").placements.get(1).effectiveSurface);
	}

	@Test
	void returnedCollectionsAndDefinitionsAreDetached() {
		JsonObject profile = root();
		addPalette(profile, "provider:first", true, "example:managed");
		BiomeDirectoryModel.Snapshot snapshot = BiomeDirectoryModel.assemble(profile,
				new JsonObject(), Collections.emptySet(),
				Collections.singletonList(loaded("example:managed", "Managed")),
				Collections.singletonList("minecraft:overworld"));
		assertThrows(UnsupportedOperationException.class, () -> snapshot.entries.clear());
		snapshot.palettes.get(0).definition.addProperty("enabled", false);
		assertTrue(profile.getAsJsonObject("biome_palettes").getAsJsonObject("provider:first")
				.get("enabled").getAsBoolean());
	}

	@Test
	void showAllFiltersRoutineBiomesByDimensionAndRetainsExplicitPlacements() {
		JsonObject profile = root();
		addPalette(profile, "provider:nether", true, "example:declared_nether");
		profile.getAsJsonObject("biome_palettes").getAsJsonObject("provider:nether")
				.addProperty("dimension", "minecraft:the_nether");
		BiomeDirectoryModel.Snapshot snapshot = BiomeDirectoryModel.assemble(profile,
				new JsonObject(), Collections.singleton("provider"), Arrays.asList(
						loaded("minecraft:beaches", "Beach", "minecraft:overworld"),
						loaded("minecraft:hell", "Hell", "minecraft:the_nether"),
						loaded("minecraft:sky", "The End", "minecraft:the_end"),
						loaded("example:declared_nether", "Declared Nether", "minecraft:overworld")),
				Arrays.asList("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));

		assertTrue(contains(snapshot, "minecraft:overworld", "minecraft:beaches"));
		assertFalse(contains(snapshot, "minecraft:overworld", "minecraft:hell"));
		assertFalse(contains(snapshot, "minecraft:overworld", "minecraft:sky"));
		assertFalse(contains(snapshot, "minecraft:overworld", "example:declared_nether"));
		assertTrue(contains(snapshot, "minecraft:the_nether", "minecraft:hell"));
		assertTrue(contains(snapshot, "minecraft:the_nether", "example:declared_nether"));
		assertFalse(contains(snapshot, "minecraft:the_nether", "minecraft:beaches"));
		assertTrue(contains(snapshot, "minecraft:the_end", "minecraft:sky"));
		assertFalse(contains(snapshot, "minecraft:the_end", "minecraft:beaches"));
	}

	private static BiomeDirectoryModel.BiomeEntry find(BiomeDirectoryModel.Snapshot snapshot, String id) {
		return snapshot.entries.stream().filter(entry -> id.equals(entry.id)).findFirst().orElseThrow(AssertionError::new);
	}

	private static BiomeDirectoryModel.LoadedBiome loaded(String id, String name) {
		return new BiomeDirectoryModel.LoadedBiome(id, name, "Example", "1");
	}

	private static BiomeDirectoryModel.LoadedBiome loaded(String id, String name, String dimension) {
		return new BiomeDirectoryModel.LoadedBiome(id, name, "Example", "1", dimension);
	}

	private static boolean contains(BiomeDirectoryModel.Snapshot snapshot, String dimension, String id) {
		return snapshot.entries(dimension, true).stream().anyMatch(entry -> id.equals(entry.id));
	}

	private static JsonObject root() {
		JsonObject root = new JsonObject();
		root.add("biome_palettes", new JsonObject());
		return root;
	}

	private static JsonObject defaultsRoot(JsonObject palettes) {
		JsonObject root = new JsonObject();
		root.add("biome_palettes", palettes);
		return root;
	}

	private static void addPalette(JsonObject root, String id, boolean enabled, String... biomes) {
		JsonObject section = root.has("biome_palettes") ? root.getAsJsonObject("biome_palettes") : root;
		JsonObject palette = new JsonObject();
		palette.addProperty("dimension", "minecraft:overworld");
		palette.addProperty("enabled", enabled);
		palette.addProperty("source_provider", "provider");
		JsonObject entries = new JsonObject();
		for (String biome : biomes) {
			JsonObject placement = new JsonObject();
			placement.addProperty("enabled", true);
			placement.addProperty("weight", 1.0D);
			placement.add("similar_biomes", new JsonArray());
			entries.add(biome, placement);
		}
		palette.add("biomes", entries);
		section.add(id, palette);
	}
}
