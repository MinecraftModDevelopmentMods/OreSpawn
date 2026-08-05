package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Optional;

import com.mojang.serialization.Lifecycle;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import org.junit.jupiter.api.Test;

class OreSpawnBiomesTest {
	@Test
	void copiesBiomeThroughDynamicRegistryBootstrap() {
		HolderLookup.Provider vanilla = VanillaRegistries.createLookup();
		MappedRegistry<Biome> generated = generatedBiomes();
		BootstrapContext<Biome> context = context(vanilla, generated);
		ResourceKey<Biome> target = ResourceKey.create(Registries.BIOME,
				new ResourceLocation("test", "candy_plains"));
		HolderGetter<Biome> biomes = vanilla.lookupOrThrow(Registries.BIOME);
		Biome plains = biomes.getOrThrow(Biomes.PLAINS).value();

		Holder.Reference<Biome> registered = OreSpawnBiomes.copyAndRegister(
				context, target, biomes, Biomes.PLAINS,
				builder -> builder.temperature(1.35F).downfall(0.15F));

		Biome copy = registered.value();
		assertSame(copy, generated.getHolder(target).orElseThrow().value());
		assertEquals(1.35F, copy.getModifiedClimateSettings().temperature());
		assertEquals(0.15F, copy.getModifiedClimateSettings().downfall());
		assertEquals(plains.getModifiedSpecialEffects(), copy.getModifiedSpecialEffects());
		assertSame(plains.getMobSettings(), copy.getMobSettings());
		assertSame(plains.getGenerationSettings(), copy.getGenerationSettings());
	}

	@Test
	void registersBlankBiomeThroughDynamicRegistryBootstrap() {
		HolderLookup.Provider vanilla = VanillaRegistries.createLookup();
		MappedRegistry<Biome> generated = generatedBiomes();
		BootstrapContext<Biome> context = context(vanilla, generated);
		ResourceKey<Biome> target = ResourceKey.create(Registries.BIOME,
				new ResourceLocation("test", "blank_candy_plains"));
		HolderGetter<Biome> biomes = vanilla.lookupOrThrow(Registries.BIOME);
		Biome plains = biomes.getOrThrow(Biomes.PLAINS).value();

		Holder.Reference<Biome> registered = OreSpawnBiomes.blankAndRegister(
				context, target, builder -> builder
						.hasPrecipitation(false)
						.temperature(1.35F)
						.downfall(0.15F)
						.specialEffects(plains.getModifiedSpecialEffects())
						.mobSpawnSettings(plains.getMobSettings())
						.generationSettings(plains.getGenerationSettings()));

		Biome blank = registered.value();
		assertSame(blank, generated.getHolder(target).orElseThrow().value());
		assertFalse(blank.getModifiedClimateSettings().hasPrecipitation());
		assertEquals(1.35F, blank.getModifiedClimateSettings().temperature());
		assertEquals(0.15F, blank.getModifiedClimateSettings().downfall());
		assertEquals(plains.getModifiedSpecialEffects(), blank.getModifiedSpecialEffects());
		assertSame(plains.getMobSettings(), blank.getMobSettings());
		assertSame(plains.getGenerationSettings(), blank.getGenerationSettings());
	}

	private static MappedRegistry<Biome> generatedBiomes() {
		return new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
	}

	private static BootstrapContext<Biome> context(HolderLookup.Provider vanilla,
			MappedRegistry<Biome> generated) {
		return new BootstrapContext<>() {
			@Override
			public Holder.Reference<Biome> register(ResourceKey<Biome> key, Biome value,
					Lifecycle lifecycle) {
				return generated.register(key, value,
						new RegistrationInfo(Optional.empty(), lifecycle));
			}

			@Override
			public <S> HolderGetter<S> lookup(
					ResourceKey<? extends Registry<? extends S>> key) {
				return vanilla.lookupOrThrow(key);
			}
		};
	}
}
