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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;

import org.junit.jupiter.api.Test;

class OreSpawnBiomesTest {
	@Test
	void copiesBiomeThroughDynamicRegistryBootstrap() {
		HolderLookup.Provider vanilla = VanillaRegistries.createLookup();
		MappedRegistry<Biome> generated = new MappedRegistry<>(Registries.BIOME,
				Lifecycle.stable());
		BootstrapContext<Biome> context = new BootstrapContext<>() {
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
		ResourceKey<Biome> target = ResourceKey.create(Registries.BIOME,
				Identifier.parse("test:candy_plains"));
		Biome plains = vanilla.getOrThrow(Biomes.PLAINS).value();

		Holder.Reference<Biome> registered = OreSpawnBiomes.copyAndRegister(
				context, target, Biomes.PLAINS,
				builder -> builder.temperature(1.35F).downfall(0.15F));

		Biome copy = registered.value();
		assertSame(copy, generated.get(target).orElseThrow().value());
		assertEquals(1.35F, copy.getModifiedClimateSettings().temperature());
		assertEquals(0.15F, copy.getModifiedClimateSettings().downfall());
		assertEquals(plains.getAttributes(), copy.getAttributes());
		assertEquals(plains.getModifiedSpecialEffects(), copy.getModifiedSpecialEffects());
		assertSame(plains.getMobSettings(), copy.getMobSettings());
		assertSame(plains.getGenerationSettings(), copy.getGenerationSettings());
	}

	@Test
	void buildsBlankBiomeThroughDynamicRegistryBootstrap() {
		MappedRegistry<Biome> generated = new MappedRegistry<>(Registries.BIOME,
				Lifecycle.stable());
		BootstrapContext<Biome> context = context(generated);
		ResourceKey<Biome> target = ResourceKey.create(Registries.BIOME,
				Identifier.parse("test:blank"));
		BiomeSpecialEffects effects = new BiomeSpecialEffects.Builder()
				.waterColor(0x654321)
				.build();

		Holder.Reference<Biome> registered = OreSpawnBiomes.blankAndRegister(
				context, target, builder -> builder
						.hasPrecipitation(false)
						.temperature(1.35F)
						.downfall(0.15F)
						.specialEffects(effects)
						.mobSpawnSettings(MobSpawnSettings.EMPTY)
						.generationSettings(BiomeGenerationSettings.EMPTY));

		Biome biome = registered.value();
		assertSame(biome, generated.get(target).orElseThrow().value());
		assertFalse(biome.getModifiedClimateSettings().hasPrecipitation());
		assertEquals(1.35F, biome.getModifiedClimateSettings().temperature());
		assertEquals(0.15F, biome.getModifiedClimateSettings().downfall());
		assertSame(effects, biome.getModifiedSpecialEffects());
		assertSame(MobSpawnSettings.EMPTY, biome.getMobSettings());
		assertSame(BiomeGenerationSettings.EMPTY, biome.getGenerationSettings());
	}

	private static BootstrapContext<Biome> context(MappedRegistry<Biome> generated) {
		HolderLookup.Provider vanilla = VanillaRegistries.createLookup();
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
