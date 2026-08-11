package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Biomes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;

/** Forge 12 contract coverage for both public biome registration routes. */
class OreSpawnBiomesTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Forge12TestBootstrap.registerVanilla();
	}

	@Test
	void copiesEverySourceBiomeComponentBeforeApplyingEdits() {
		Biome source = Biomes.PLAINS;
		OreSpawnBiomes.BiomeRegistrar biomes = OreSpawnBiomes.registrarForTesting("test_copy");
		OreSpawnBiomes.BiomeReference registered = OreSpawnBiomes.copyAndRegister(
				biomes, "copied", () -> source,
				properties -> properties.setTemperature(1.35F).setRainfall(0.15F));

		biomes.registerForTesting(ForgeRegistries.BIOMES);
		Biome copy = registered.get();
		assertEquals(new ResourceLocation("test_copy", "copied"), registered.getId());
		assertEquals(source.getBaseHeight(), copy.getBaseHeight());
		assertEquals(source.getHeightVariation(), copy.getHeightVariation());
		assertEquals(1.35F, copy.getTemperature());
		assertEquals(0.15F, copy.getRainfall());
		assertEquals(source.getWaterColor(), copy.getWaterColor());
		assertEquals(source.topBlock, copy.topBlock);
		assertEquals(source.fillerBlock, copy.fillerBlock);
		assertNotSame(source.theBiomeDecorator, copy.theBiomeDecorator);
		assertEquals(source.theBiomeDecorator.treesPerChunk, copy.theBiomeDecorator.treesPerChunk);
		for (EnumCreatureType type : EnumCreatureType.values()) {
			assertEquals(source.getSpawnableList(type), copy.getSpawnableList(type));
		}
	}

	@Test
	void buildsBlankBiomeWhenProviderSuppliesEveryRequiredField() {
		OreSpawnBiomes.BiomeRegistrar biomes = OreSpawnBiomes.registrarForTesting("test_blank");
		OreSpawnBiomes.BiomeReference registered = OreSpawnBiomes.blankAndRegister(
				biomes, "blank", properties -> properties
						.setRainDisabled().setBaseHeight(0.1F).setHeightVariation(0.2F)
						.setTemperature(1.35F).setRainfall(0.15F).setWaterColor(0x654321));

		biomes.registerForTesting(ForgeRegistries.BIOMES);
		Biome biome = registered.get();
		assertEquals(new ResourceLocation("test_blank", "blank"), registered.getId());
		assertEquals(false, biome.canRain());
		assertEquals(0.1F, biome.getBaseHeight());
		assertEquals(0.2F, biome.getHeightVariation());
		assertEquals(1.35F, biome.getTemperature());
		assertEquals(0.15F, biome.getRainfall());
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

	private static void configureBlank(Biome.BiomeProperties properties) {
		properties.setRainDisabled().setBaseHeight(0.1F).setHeightVariation(0.2F)
				.setTemperature(0.7F).setRainfall(0.8F).setWaterColor(0x3f76e4);
	}
}
