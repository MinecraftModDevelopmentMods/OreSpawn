package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeGenerationSettings;
import net.minecraft.world.biome.BiomeAmbience;
import net.minecraft.world.biome.MobSpawnInfo;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.RegistryObject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OreSpawnBiomesTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Bootstrap.bootStrap();
	}

	@Test
	void copiesEverySourceBiomeComponentBeforeApplyingEdits() throws Exception {
		BiomeAmbience effects = effects(0x123456);
		Biome source = new Biome.Builder()
				.precipitation(Biome.RainType.NONE)
				.biomeCategory(Biome.Category.ICY)
				.depth(0.1F)
				.scale(0.2F)
				.temperature(0.45F)
				.temperatureAdjustment(Biome.TemperatureModifier.FROZEN)
				.downfall(0.75F)
				.specialEffects(effects)
				.mobSpawnSettings(MobSpawnInfo.EMPTY)
				.generationSettings(BiomeGenerationSettings.EMPTY)
				.build();
		DeferredRegister<Biome> biomes = DeferredRegister.create(ForgeRegistries.BIOMES, "test");

		RegistryObject<Biome> registered = OreSpawnBiomes.copyAndRegister(
				biomes, "copied", () -> source,
				builder -> builder.temperature(1.35F).downfall(0.15F));

		Biome copy = registeredValue(biomes, registered);
		assertEquals(new ResourceLocation("test", "copied"), registered.getId());
		assertEquals(Biome.RainType.NONE, copy.getPrecipitation());
		assertEquals(Biome.Category.ICY, field(copy, "biomeCategory"));
		assertEquals(1.35F, copy.getBaseTemperature());
		assertEquals(0.15F, copy.getDownfall());
		assertEquals(Biome.TemperatureModifier.FROZEN,
				((Biome.Climate) field(copy, "climateSettings")).temperatureModifier);
		assertSame(effects, copy.getSpecialEffects());
		assertSame(source.getMobSettings(), copy.getMobSettings());
		assertSame(source.getGenerationSettings(), copy.getGenerationSettings());
	}

	@Test
	void buildsBlankBiomeWhenProviderSuppliesEveryRequiredField() throws Exception {
		BiomeAmbience effects = effects(0x654321);
		DeferredRegister<Biome> biomes = DeferredRegister.create(ForgeRegistries.BIOMES, "test");

		RegistryObject<Biome> registered = OreSpawnBiomes.blankAndRegister(
				biomes, "blank", builder -> builder
						.precipitation(Biome.RainType.NONE)
						.biomeCategory(Biome.Category.NONE)
						.depth(0.1F)
						.scale(0.2F)
						.temperature(1.35F)
						.downfall(0.15F)
						.specialEffects(effects)
						.mobSpawnSettings(MobSpawnInfo.EMPTY)
						.generationSettings(BiomeGenerationSettings.EMPTY));

		Biome biome = registeredValue(biomes, registered);
		assertEquals(new ResourceLocation("test", "blank"), registered.getId());
		assertEquals(Biome.RainType.NONE, biome.getPrecipitation());
		assertEquals(Biome.Category.NONE, field(biome, "biomeCategory"));
		assertEquals(1.35F, biome.getBaseTemperature());
		assertEquals(0.15F, biome.getDownfall());
		assertSame(effects, biome.getSpecialEffects());
		assertSame(MobSpawnInfo.EMPTY, biome.getMobSettings());
		assertSame(BiomeGenerationSettings.EMPTY, biome.getGenerationSettings());
	}

	private static BiomeAmbience effects(int waterColor) {
		return new BiomeAmbience.Builder()
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

	private static Object field(Object owner, String name) throws ReflectiveOperationException {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}
}
