package zone.moddev.mc.orespawn.api;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.world.biome.Biome;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.fml.RegistryObject;

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
			String name, Supplier<? extends Biome> source, Consumer<Biome.Builder> edit) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(edit, "edit");
		return register.register(name, () -> {
			Biome sourceBiome = source.get();
			Biome.Builder builder = new Biome.Builder()
					.precipitation(sourceBiome.getPrecipitation())
					.biomeCategory(sourceBiome.getBiomeCategory())
					.depth(sourceBiome.getDepth())
					.scale(sourceBiome.getScale())
					.temperature(sourceBiome.getBaseTemperature())
					.temperatureAdjustment(sourceBiome.climateSettings.temperatureModifier)
					.downfall(sourceBiome.getDownfall())
					.specialEffects(sourceBiome.getSpecialEffects())
					.mobSpawnSettings(sourceBiome.getMobSettings())
					.generationSettings(sourceBiome.getGenerationSettings());
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
			String name, Consumer<Biome.Builder> configure) {
		Objects.requireNonNull(register, "register");
		Objects.requireNonNull(configure, "configure");
		return register.register(name, () -> {
			Biome.Builder builder = new Biome.Builder();
			configure.accept(builder);
			return builder.build();
		});
	}
}
