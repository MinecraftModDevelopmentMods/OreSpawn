package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.orespawn.test.Forge36TestBootstrap;
import zone.moddev.mc.orespawn.worldgen.BakedBiomeWorldgen.Palette;

class BiomePaletteOrderingTest {
	@BeforeAll static void bootstrapMinecraft() { Forge36TestBootstrap.registerVanilla(); }

	@Test void ordinaryPalettesKeepTheirOrderAndTheUiOverrideRunsLast() {
		JsonObject palettes = new JsonObject();
		palettes.add("provider:first", new JsonObject());
		palettes.add("orespawn:ui/biome_overrides/minecraft_overworld", new JsonObject());
		palettes.add("provider:second", new JsonObject());
		List<String> ids = BiomeWorldgenManager.orderedPaletteEntries(palettes).stream()
				.map(Entry<String, JsonElement>::getKey).collect(Collectors.toList());
		assertEquals(java.util.Arrays.asList("provider:first", "provider:second",
				"orespawn:ui/biome_overrides/minecraft_overworld"), ids);
	}

	@Test void exactReplacementRunsAfterTheProviderEvenWhenStoredFirst() {
		JsonObject root = root();
		JsonObject palettes = root.getAsJsonObject("biome_palettes");
		palettes.add("orespawn:ui/biome_overrides/minecraft_overworld",
				palette("minecraft:forest", "minecraft:desert"));
		palettes.add("provider:ordinary", palette("minecraft:desert", "minecraft:plains"));
		List<Palette> baked = BiomeWorldgenManager.bakePalettes(root, BuiltinRegistries.BIOME,
				new ResourceLocation("minecraft:overworld"), new IdentityHashMap<>());
		BiomeOverlaySource overlay = new BiomeOverlaySource(
				new ConstantBiomeProvider(biome("minecraft:plains")), baked, 78431L);
		assertEquals(2, baked.size());
		assertSame(biome("minecraft:forest"), biomeAt(overlay, new BlockPos(0, 64, 0)));
		assertSame(biome("minecraft:forest"), biomeAt(overlay, new BlockPos(-257, 64, 389)));
	}

	@Test void aMissingTargetStaysDormant() {
		JsonObject root = root();
		root.getAsJsonObject("biome_palettes").add(
				"orespawn:ui/biome_overrides/minecraft_overworld",
				palette("missing:orchard", "minecraft:plains"));
		assertTrue(BiomeWorldgenManager.bakePalettes(root, BuiltinRegistries.BIOME,
				new ResourceLocation("minecraft:overworld"), new IdentityHashMap<>()).isEmpty());
		assertTrue(root.toString().contains("missing:orchard"));
	}

	@Test void aDisabledOverrideDoesNotChangeExistingBiomeChoices() {
		JsonObject root = root();
		root.getAsJsonObject("biome_palettes").add("provider:first",
				palette("minecraft:desert", "minecraft:plains"));
		JsonObject disabled = new com.google.gson.JsonParser().parse(root.toString()).getAsJsonObject();
		JsonObject override = palette("minecraft:forest", "minecraft:desert");
		override.addProperty("enabled", false);
		disabled.getAsJsonObject("biome_palettes").add(
				"orespawn:ui/biome_overrides/minecraft_overworld", override);
		Biome source = biome("minecraft:plains");
		BiomeOverlaySource baseline = overlay(root, source);
		BiomeOverlaySource candidate = overlay(disabled, source);
		for (int z = -96; z <= 96; z += 3) {
			for (int x = -96; x <= 96; x += 3) {
				BlockPos pos = new BlockPos(x, 63, z);
				assertSame(biomeAt(baseline, pos), biomeAt(candidate, pos));
			}
		}
	}

	private static BiomeOverlaySource overlay(JsonObject root, Biome source) {
		return new BiomeOverlaySource(new ConstantBiomeProvider(source),
				BiomeWorldgenManager.bakePalettes(root, BuiltinRegistries.BIOME,
						new ResourceLocation("minecraft:overworld"), new IdentityHashMap<>()), 918273L);
	}

	private static Biome biomeAt(BiomeOverlaySource source, BlockPos pos) {
		return source.getNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2, null).value();
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
		JsonObject placement = new JsonObject();
		placement.addProperty("enabled", true);
		placement.addProperty("weight", 1.0D);
		JsonArray similar = new JsonArray();
		similar.add(new JsonPrimitive(source));
		placement.add("similar_biomes", similar);
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

	private static final class ConstantBiomeProvider extends BiomeSource {
		private final Holder<Biome> biome;
		ConstantBiomeProvider(Biome biome) { this(Holder.direct(biome)); }
		private ConstantBiomeProvider(Holder<Biome> biome) { super(Stream.of(biome)); this.biome = biome; }
		@Override public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
			return biome;
		}
		@Override protected com.mojang.serialization.Codec<? extends BiomeSource> codec() {
			return BiomeSource.CODEC;
		}
		@Override public BiomeSource withSeed(long seed) { return this; }
	}
}
