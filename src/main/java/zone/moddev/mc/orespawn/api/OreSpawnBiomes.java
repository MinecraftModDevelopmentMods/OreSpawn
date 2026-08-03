package zone.moddev.mc.orespawn.api;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
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
	 * Registers a data-driven biome copied from an existing biome, then applies
	 * provider changes to the copied builder. Add this bootstrap to a
	 * {@code RegistrySetBuilder} used by Forge's datapack data generator.
	 */
	public static Holder.Reference<Biome> copyAndRegister(BootstrapContext<Biome> context,
			ResourceKey<Biome> target, ResourceKey<Biome> source,
			Consumer<Biome.BiomeBuilder> edit) {
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(edit, "edit");
		Biome original = context.lookup(Registries.BIOME).getOrThrow(source).value();
		return context.register(target, copy(original, edit));
	}

	/**
	 * Registers a data-driven biome from a fresh builder. The provider must set
	 * every required climate, effects, spawning, and generation field.
	 */
	public static Holder.Reference<Biome> blankAndRegister(BootstrapContext<Biome> context,
			ResourceKey<Biome> target, Consumer<Biome.BiomeBuilder> configure) {
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(configure, "configure");
		Biome.BiomeBuilder builder = new Biome.BiomeBuilder();
		configure.accept(builder);
		return context.register(target, builder.build());
	}

	/**
	 * @deprecated Minecraft 26.1 biomes are dynamic registry entries and cannot
	 * be registered with {@link DeferredRegister}. Use the
	 * {@link BootstrapContext} overload or ship the equivalent biome JSON.
	 */
	@Deprecated(since = "4.0.5", forRemoval = true)
	public static RegistryObject<Biome> copyAndRegister(DeferredRegister<Biome> register,
			String name, Supplier<? extends Biome> source, Consumer<Biome.BiomeBuilder> edit) {
		Objects.requireNonNull(register, "register");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(edit, "edit");
		throw dynamicRegistryException();
	}

	/**
	 * @deprecated Minecraft 26.1 biomes are dynamic registry entries and cannot
	 * be registered with {@link DeferredRegister}. Use the
	 * {@link BootstrapContext} overload or ship the equivalent biome JSON.
	 */
	@Deprecated(since = "4.0.5", forRemoval = true)
	public static RegistryObject<Biome> blankAndRegister(DeferredRegister<Biome> register,
			String name, Consumer<Biome.BiomeBuilder> configure) {
		Objects.requireNonNull(register, "register");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(configure, "configure");
		throw dynamicRegistryException();
	}

	private static Biome copy(Biome original, Consumer<Biome.BiomeBuilder> edit) {
		Biome.ClimateSettings climate = original.getModifiedClimateSettings();
		Biome.BiomeBuilder builder = new Biome.BiomeBuilder()
				.hasPrecipitation(climate.hasPrecipitation())
				.temperature(climate.temperature())
				.temperatureAdjustment(climate.temperatureModifier())
				.downfall(climate.downfall())
				.putAttributes(original.getAttributes())
				.specialEffects(original.getModifiedSpecialEffects())
				.mobSpawnSettings(original.getMobSettings())
				.generationSettings(original.getGenerationSettings());
		edit.accept(builder);
		return builder.build();
	}

	private static UnsupportedOperationException dynamicRegistryException() {
		return new UnsupportedOperationException(
				"Minecraft 26.1 biomes are data-driven; register them through a "
						+ "BootstrapContext/RegistrySetBuilder or a worldgen/biome JSON resource");
	}
}
