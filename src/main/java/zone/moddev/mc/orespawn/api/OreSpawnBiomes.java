package zone.moddev.mc.orespawn.api;

import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Small bootstrap helpers for provider mods which define data-driven biomes
 * without taking a compile-time dependency on a separate biome framework.
 *
 * <p>Biomes are dynamic registry entries in NeoForge 21.1. A provider should
 * package biome JSON or use these methods from a
 * {@link net.minecraft.core.RegistrySetBuilder} bootstrap used by datagen.
 */
public final class OreSpawnBiomes {
	private OreSpawnBiomes() {
	}

	/**
	 * Copies an existing biome and applies provider changes to the copied builder.
	 */
	public static Biome copy(Biome source, Consumer<Biome.BiomeBuilder> edit) {
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(edit, "edit");
		Biome.ClimateSettings climate = source.getModifiedClimateSettings();
		Biome.BiomeBuilder builder = new Biome.BiomeBuilder()
				.hasPrecipitation(climate.hasPrecipitation())
				.temperature(climate.temperature())
				.temperatureAdjustment(climate.temperatureModifier())
				.downfall(climate.downfall())
				.specialEffects(source.getModifiedSpecialEffects())
				.mobSpawnSettings(source.getMobSettings())
				.generationSettings(source.getGenerationSettings());
		edit.accept(builder);
		return builder.build();
	}

	/**
	 * Copies a source biome from a bootstrap lookup and registers the result.
	 */
	public static Holder.Reference<Biome> copyAndRegister(BootstrapContext<Biome> context,
			ResourceKey<Biome> targetKey, HolderGetter<Biome> sourceLookup,
			ResourceKey<Biome> sourceKey, Consumer<Biome.BiomeBuilder> edit) {
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(targetKey, "targetKey");
		Objects.requireNonNull(sourceLookup, "sourceLookup");
		Objects.requireNonNull(sourceKey, "sourceKey");
		return context.register(targetKey, copy(sourceLookup.getOrThrow(sourceKey).value(), edit));
	}

	/**
	 * Registers a biome from a fresh builder. The provider must set every required
	 * climate, effects, spawning, and generation field.
	 */
	public static Holder.Reference<Biome> blankAndRegister(BootstrapContext<Biome> context,
			ResourceKey<Biome> targetKey, Consumer<Biome.BiomeBuilder> configure) {
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(targetKey, "targetKey");
		Objects.requireNonNull(configure, "configure");
		Biome.BiomeBuilder builder = new Biome.BiomeBuilder();
		configure.accept(builder);
		return context.register(targetKey, builder.build());
	}
}
