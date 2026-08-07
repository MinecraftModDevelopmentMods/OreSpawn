package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import org.junit.jupiter.api.Test;

class OreSpawnBiomesTest {
	@Test
	void copiesEverySourceBiomeComponentBeforeApplyingEdits() throws Exception {
		BiomeSpecialEffects effects = effects(0x123456);
		Biome source = new Biome.BiomeBuilder()
				.hasPrecipitation(false)
				.temperature(0.45F)
				.temperatureAdjustment(Biome.TemperatureModifier.FROZEN)
				.downfall(0.75F)
				.specialEffects(effects)
				.mobSpawnSettings(MobSpawnSettings.EMPTY)
				.generationSettings(BiomeGenerationSettings.EMPTY)
				.build();
		DeferredRegister<Biome> biomes = DeferredRegister.create(Registries.BIOME, "test");

		RegistryObject<Biome> registered = OreSpawnBiomes.copyAndRegister(
				biomes, "copied", () -> source,
				builder -> builder.temperature(1.35F).downfall(0.15F));

		Biome copy = registeredValue(biomes, registered);
		assertEquals(ResourceLocation.fromNamespaceAndPath("test", "copied"), registered.getId());
		assertFalse(copy.getModifiedClimateSettings().hasPrecipitation());
		assertEquals(1.35F, copy.getModifiedClimateSettings().temperature());
		assertEquals(0.15F, copy.getModifiedClimateSettings().downfall());
		assertEquals(Biome.TemperatureModifier.FROZEN,
				copy.getModifiedClimateSettings().temperatureModifier());
		assertSame(effects, copy.getModifiedSpecialEffects());
		assertSame(source.getMobSettings(), copy.getMobSettings());
		assertSame(source.getGenerationSettings(), copy.getGenerationSettings());
	}

	@Test
	void buildsBlankBiomeWhenProviderSuppliesEveryRequiredField() throws Exception {
		BiomeSpecialEffects effects = effects(0x654321);
		DeferredRegister<Biome> biomes = DeferredRegister.create(Registries.BIOME, "test");

		RegistryObject<Biome> registered = OreSpawnBiomes.blankAndRegister(
				biomes, "blank", builder -> builder
						.hasPrecipitation(false)
						.temperature(1.35F)
						.downfall(0.15F)
						.specialEffects(effects)
						.mobSpawnSettings(MobSpawnSettings.EMPTY)
						.generationSettings(BiomeGenerationSettings.EMPTY));

		Biome biome = registeredValue(biomes, registered);
		assertEquals(ResourceLocation.fromNamespaceAndPath("test", "blank"), registered.getId());
		assertFalse(biome.getModifiedClimateSettings().hasPrecipitation());
		assertEquals(1.35F, biome.getModifiedClimateSettings().temperature());
		assertEquals(0.15F, biome.getModifiedClimateSettings().downfall());
		assertSame(effects, biome.getModifiedSpecialEffects());
		assertSame(MobSpawnSettings.EMPTY, biome.getMobSettings());
		assertSame(BiomeGenerationSettings.EMPTY, biome.getGenerationSettings());
	}

	private static BiomeSpecialEffects effects(int waterColor) {
		return new BiomeSpecialEffects.Builder()
				.fogColor(0xC0D8FF)
				.waterColor(waterColor)
				.waterFogColor(0x050533)
				.skyColor(0x78A7FF)
				.build();
	}

	@SuppressWarnings("unchecked")
	private static Biome registeredValue(DeferredRegister<Biome> register,
			RegistryObject<Biome> object) throws ReflectiveOperationException {
		Field entriesField = DeferredRegister.class.getDeclaredField("entries");
		entriesField.setAccessible(true);
		Map<RegistryObject<Biome>, Supplier<? extends Biome>> entries =
				(Map<RegistryObject<Biome>, Supplier<? extends Biome>>) entriesField.get(register);
		return entries.get(object).get();
	}
}
