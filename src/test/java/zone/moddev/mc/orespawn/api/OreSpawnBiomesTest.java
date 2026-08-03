package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import net.minecraft.world.level.biome.Biomes;

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
}
