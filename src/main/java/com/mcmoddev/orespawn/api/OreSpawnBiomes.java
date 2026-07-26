package com.mcmoddev.orespawn.api;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Small registration helpers for provider mods which want to define biomes
 * without taking a compile-time dependency on a separate biome framework.
 */
public final class OreSpawnBiomes {
	private OreSpawnBiomes() {
	}

	/**
	 * Registers a biome copied from an existing biome, then applies provider
	 * changes to the copied builder.
	 */
	public static RegistryObject<Biome> copyAndRegister(DeferredRegister<Biome> register,
			String name, Supplier<? extends Biome> source, Consumer<Biome.BiomeBuilder> edit) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(edit, "edit");
		return register.register(name, () -> {
			Biome.BiomeBuilder builder = Biome.BiomeBuilder.from(source.get());
			edit.accept(builder);
			return builder.build();
		});
	}

	/**
	 * Registers a biome from a fresh builder. The provider must set all required
	 * climate, effects, spawning, and generation fields before the builder is
	 * built.
	 */
	public static RegistryObject<Biome> blankAndRegister(DeferredRegister<Biome> register,
			String name, Consumer<Biome.BiomeBuilder> configure) {
		Objects.requireNonNull(register, "register");
		Objects.requireNonNull(configure, "configure");
		return register.register(name, () -> {
			Biome.BiomeBuilder builder = new Biome.BiomeBuilder();
			configure.accept(builder);
			return builder.build();
		});
	}
}
