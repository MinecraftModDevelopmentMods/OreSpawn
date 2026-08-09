package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.entity.EntityClassification;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.surfacebuilders.SurfaceBuilder;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OreSpawnBiomesTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Bootstrap.register();
	}

	@Test
	void copiesEverySourceBiomeComponentBeforeApplyingEdits() throws Exception {
		Biome source = Biomes.PLAINS;
		DeferredRegister<Biome> biomes = DeferredRegister.create(ForgeRegistries.BIOMES, "test");
		RegistryObject<Biome> registered = OreSpawnBiomes.copyAndRegister(
				biomes, "copied", () -> source,
				builder -> builder.temperature(1.35F).downfall(0.15F));

		Biome copy = registeredValue(biomes, registered);
		assertEquals(new ResourceLocation("test", "copied"), registered.getId());
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
		for (EntityClassification classification : EntityClassification.values()) {
			assertEquals(source.getSpawns(classification), copy.getSpawns(classification));
		}
		assertEquals(field(source, "structures"), field(copy, "structures"));
	}

	@Test
	void buildsBlankBiomeWhenProviderSuppliesEveryRequiredField() throws Exception {
		DeferredRegister<Biome> biomes = DeferredRegister.create(ForgeRegistries.BIOMES, "test");
		RegistryObject<Biome> registered = OreSpawnBiomes.blankAndRegister(
				biomes, "blank", builder -> builder
						.precipitation(Biome.RainType.NONE)
						.category(Biome.Category.NONE)
						.depth(0.1F)
						.scale(0.2F)
						.temperature(1.35F)
						.downfall(0.15F)
						.waterColor(0x654321)
						.waterFogColor(0x050533)
						.surfaceBuilder(SurfaceBuilder.DEFAULT,
								SurfaceBuilder.GRASS_DIRT_GRAVEL_CONFIG));

		Biome biome = registeredValue(biomes, registered);
		assertEquals(new ResourceLocation("test", "blank"), registered.getId());
		assertEquals(Biome.RainType.NONE, biome.getPrecipitation());
		assertEquals(Biome.Category.NONE, biome.getCategory());
		assertEquals(1.35F, biome.getDefaultTemperature());
		assertEquals(0.15F, biome.getDownfall());
		assertEquals(0x654321, biome.getWaterColor());
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
		Field field = Biome.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}
}
