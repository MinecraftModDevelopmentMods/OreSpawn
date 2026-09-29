package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Palette;

class BiomePaletteOrderingTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		Forge12TestBootstrap.registerVanilla();
	}

	@Test
	void ordinaryOrderIsStableAndUiOverridesAlwaysRunLast() {
		JsonObject section = new JsonObject();
		section.add("provider:first", new JsonObject());
		section.add("orespawn:ui/biome_overrides/minecraft_overworld", new JsonObject());
		section.add("provider:second", new JsonObject());
		List<String> ids = BiomeWorldgenManager.orderedPaletteEntries(section).stream()
				.map(Entry<String, JsonElement>::getKey).collect(Collectors.toList());
		assertEquals(java.util.Arrays.asList("provider:first", "provider:second",
				"orespawn:ui/biome_overrides/minecraft_overworld"), ids);
	}

	@Test
	void terminalOverrideRunsAfterOrdinaryPalettesEvenWhenStoredFirst() {
		JsonObject root = root();
		JsonObject palettes = root.getAsJsonObject("biome_palettes");
		palettes.add("orespawn:ui/biome_overrides/minecraft_overworld",
				palette("minecraft:forest", "minecraft:desert"));
		palettes.add("provider:ordinary", palette("minecraft:desert", "minecraft:plains"));

		List<Palette> baked = BiomeWorldgenManager.bakePalettes(root,
				new ResourceLocation("minecraft:overworld"), new IdentityHashMap<>());
		Biome plains = biome("minecraft:plains");
		Biome forest = biome("minecraft:forest");
		BiomeOverlaySource overlay = new BiomeOverlaySource(
				new ConstantBiomeProvider(plains), baked, 78431L);

		assertEquals(2, baked.size());
		assertSame(forest, overlay.getBiome(new BlockPos(0, 64, 0)));
		assertSame(forest, overlay.getBiome(new BlockPos(-257, 64, 389)));
	}

	@Test
	void missingTargetLeavesSourceUnchangedAndReactivatesWhenTargetReturns() {
		JsonObject missingRoot = root();
		missingRoot.getAsJsonObject("biome_palettes").add(
				"orespawn:ui/biome_overrides/minecraft_overworld",
				palette("missing:orchard", "minecraft:plains"));
		List<Palette> missing = BiomeWorldgenManager.bakePalettes(missingRoot,
				new ResourceLocation("minecraft:overworld"), new IdentityHashMap<>());
		Biome plains = biome("minecraft:plains");

		assertTrue(missing.isEmpty());
		assertTrue(missingRoot.toString().contains("missing:orchard"),
				"dormant target must remain persisted");
		JsonObject restoredRoot = root();
		restoredRoot.getAsJsonObject("biome_palettes").add(
				"orespawn:ui/biome_overrides/minecraft_overworld",
				palette("minecraft:desert", "minecraft:plains"));
		List<Palette> restored = BiomeWorldgenManager.bakePalettes(restoredRoot,
				new ResourceLocation("minecraft:overworld"), new IdentityHashMap<>());
		assertSame(biome("minecraft:desert"), new BiomeOverlaySource(
				new ConstantBiomeProvider(plains), restored, 11L)
				.getBiome(new BlockPos(64, 70, 64)));
	}

	@Test
	void absentOrDisabledOverrideProducesIdenticalBiomeCoordinates() {
		JsonObject untouched = root();
		untouched.getAsJsonObject("biome_palettes").add("provider:first",
				palette("minecraft:desert", "minecraft:plains"));
		JsonObject disabled = new com.google.gson.JsonParser().parse(untouched.toString())
				.getAsJsonObject();
		JsonObject disabledOverride = palette("minecraft:forest", "minecraft:desert");
		disabledOverride.addProperty("enabled", false);
		disabled.getAsJsonObject("biome_palettes").add(
				"orespawn:ui/biome_overrides/minecraft_overworld", disabledOverride);
		Biome plains = biome("minecraft:plains");
		BiomeOverlaySource baseline = overlay(untouched, plains, 918273L);
		BiomeOverlaySource candidate = overlay(disabled, plains, 918273L);
		for (int z = -96; z <= 96; z += 3) {
			for (int x = -96; x <= 96; x += 3) {
				assertSame(baseline.getBiome(new BlockPos(x, 63, z)),
						candidate.getBiome(new BlockPos(x, 63, z)), x + "," + z);
			}
		}
	}

	private static BiomeOverlaySource overlay(JsonObject root, Biome source, long seed) {
		return new BiomeOverlaySource(new ConstantBiomeProvider(source),
				BiomeWorldgenManager.bakePalettes(root,
						new ResourceLocation("minecraft:overworld"), new IdentityHashMap<>()), seed);
	}

	private static JsonObject root() {
		JsonObject root = new JsonObject();
		root.add("biome_palettes", new JsonObject());
		return root;
	}

	private static JsonObject palette(String target, String source) {
		JsonObject palette = new JsonObject();
		palette.addProperty("dimension", "minecraft:overworld");
		palette.addProperty("enabled", true);
		palette.addProperty("mode", "replace");
		palette.addProperty("scope", "all");
		palette.addProperty("region_size", "tiny");
		palette.addProperty("coverage", 1.0D);
		palette.addProperty("fallback_weight", 0.0D);
		palette.add("include_namespaces", new JsonArray());
		palette.add("exclude_namespaces", new JsonArray());
		JsonObject placement = new JsonObject();
		placement.addProperty("enabled", true);
		placement.addProperty("weight", 1.0D);
		JsonArray sources = new JsonArray();
		sources.add(new com.google.gson.JsonPrimitive(source));
		placement.add("similar_biomes", sources);
		placement.add("required_similar_biomes", new JsonArray());
		JsonObject biomes = new JsonObject();
		biomes.add(target, placement);
		palette.add("biomes", biomes);
		return palette;
	}

	private static Biome biome(String id) {
		Biome biome = ForgeRegistries.BIOMES.getValue(new ResourceLocation(id));
		if (biome == null) throw new AssertionError("Missing test biome " + id);
		return biome;
	}

	private static final class ConstantBiomeProvider extends BiomeProvider {
		private final Biome biome;

		ConstantBiomeProvider(Biome biome) { this.biome = biome; }

		@Override public Biome getBiome(BlockPos pos, Biome fallback) { return biome; }
		@Override public Biome[] getBiomesForGeneration(Biome[] reuse, int x, int z,
				int width, int length) { return filled(reuse, width, length); }
		@Override public Biome[] getBiomes(Biome[] reuse, int x, int z,
				int width, int length, boolean cacheFlag) { return filled(reuse, width, length); }
		@Override public List<Biome> getBiomesToSpawnIn() { return Collections.singletonList(biome); }

		private Biome[] filled(Biome[] reuse, int width, int length) {
			Biome[] result = reuse != null && reuse.length >= width * length
					? reuse : new Biome[width * length];
			Arrays.fill(result, 0, width * length, biome);
			return result;
		}
	}
}
