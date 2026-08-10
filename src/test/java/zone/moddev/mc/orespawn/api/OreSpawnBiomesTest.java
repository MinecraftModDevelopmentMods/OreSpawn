package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Biomes;
import zone.moddev.mc.orespawn.test.Forge25TestBootstrap;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.surfacebuilders.CompositeSurfaceBuilder;
import net.minecraftforge.registries.ForgeRegistries;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OreSpawnBiomesTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Forge25TestBootstrap.registerVanilla();
	}

	@Test
	void copiesEverySourceBiomeComponentBeforeApplyingEdits() throws Exception {
		Biome source = Biomes.PLAINS;
		OreSpawnBiomes.BiomeRegistrar biomes = OreSpawnBiomes.registrarForTesting("test_copy");
		OreSpawnBiomes.BiomeReference registered = OreSpawnBiomes.copyAndRegister(
				biomes, "copied", () -> source,
				builder -> builder.temperature(1.35F).downfall(0.15F));

		biomes.registerForTesting(ForgeRegistries.BIOMES);
		Biome copy = registered.get();
		assertEquals(new ResourceLocation("test_copy", "copied"), registered.getId());
		assertEquals(source.getPrecipitation(), copy.getPrecipitation());
		assertEquals(source.getCategory(), copy.getCategory());
		assertEquals(source.getDepth(), copy.getDepth());
		assertEquals(source.getScale(), copy.getScale());
		assertEquals(1.35F, copy.getDefaultTemperature());
		assertEquals(0.15F, copy.getDownfall());
		assertEquals(source.getWaterColor(), copy.getWaterColor());
		assertEquals(source.getWaterFogColor(), copy.getWaterFogColor());
		assertEquals(source.getSurfaceBuilder(), copy.getSurfaceBuilder());
		assertEquals(source.getParent(), copy.getParent());
		for (GenerationStage.Decoration stage : GenerationStage.Decoration.values()) {
			assertEquals(source.getFeatures(stage), copy.getFeatures(stage));
		}
		for (GenerationStage.Carving stage : GenerationStage.Carving.values()) {
			assertEquals(source.getCarvers(stage), copy.getCarvers(stage));
		}
		for (EnumCreatureType classification : EnumCreatureType.values()) {
			assertEquals(source.getSpawns(classification), copy.getSpawns(classification));
		}
		assertEquals(field(source, "structures"), field(copy, "structures"));
	}

	@Test
	void buildsBlankBiomeWhenProviderSuppliesEveryRequiredField() {
		OreSpawnBiomes.BiomeRegistrar biomes = OreSpawnBiomes.registrarForTesting("test_blank");
		OreSpawnBiomes.BiomeReference registered = OreSpawnBiomes.blankAndRegister(
				biomes, "blank", builder -> builder
						.precipitation(Biome.RainType.NONE)
						.category(Biome.Category.NONE)
						.depth(0.1F)
						.scale(0.2F)
						.temperature(1.35F)
						.downfall(0.15F)
						.waterColor(0x654321)
						.waterFogColor(0x050533)
						.surfaceBuilder(new CompositeSurfaceBuilder<>(
								Biome.DEFAULT_SURFACE_BUILDER, Biome.GRASS_DIRT_GRAVEL_SURFACE)));

		biomes.registerForTesting(ForgeRegistries.BIOMES);
		Biome biome = registered.get();
		assertEquals(new ResourceLocation("test_blank", "blank"), registered.getId());
		assertEquals(Biome.RainType.NONE, biome.getPrecipitation());
		assertEquals(Biome.Category.NONE, biome.getCategory());
		assertEquals(1.35F, biome.getDefaultTemperature());
		assertEquals(0.15F, biome.getDownfall());
		assertEquals(0x654321, biome.getWaterColor());
	}

	@Test
	void duplicateNamesAreRejectedBeforeRegistryMutation() {
		OreSpawnBiomes.BiomeRegistrar biomes = OreSpawnBiomes.registrarForTesting("test_duplicate");
		OreSpawnBiomes.blankAndRegister(biomes, "same", OreSpawnBiomesTest::configureBlank);
		assertThrows(IllegalArgumentException.class,
				() -> OreSpawnBiomes.blankAndRegister(biomes, "same", OreSpawnBiomesTest::configureBlank));
	}

	@Test
	void handlesRejectEarlyGetAndRegistrarRejectsLateDeclarations() {
		OreSpawnBiomes.BiomeRegistrar biomes = OreSpawnBiomes.registrarForTesting("test_lifecycle");
		OreSpawnBiomes.BiomeReference reference = OreSpawnBiomes.blankAndRegister(
				biomes, "lifecycle", OreSpawnBiomesTest::configureBlank);
		assertThrows(IllegalStateException.class, reference::get);
		biomes.registerForTesting(ForgeRegistries.BIOMES);
		assertEquals(reference.getId(), ForgeRegistries.BIOMES.getKey(reference.get()));
		assertThrows(IllegalStateException.class,
				() -> OreSpawnBiomes.blankAndRegister(biomes, "late", OreSpawnBiomesTest::configureBlank));
		assertThrows(IllegalStateException.class,
				() -> biomes.registerForTesting(ForgeRegistries.BIOMES));
	}

	private static void configureBlank(Biome.BiomeBuilder builder) {
		builder.precipitation(Biome.RainType.NONE)
				.category(Biome.Category.NONE)
				.depth(0.1F).scale(0.2F).temperature(0.7F).downfall(0.8F)
				.waterColor(0x3f76e4).waterFogColor(0x050533)
				.surfaceBuilder(new CompositeSurfaceBuilder<>(
						Biome.DEFAULT_SURFACE_BUILDER, Biome.GRASS_DIRT_GRAVEL_SURFACE));
	}

	private static Object field(Object owner, String name) throws ReflectiveOperationException {
		Field field = Biome.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}
}
