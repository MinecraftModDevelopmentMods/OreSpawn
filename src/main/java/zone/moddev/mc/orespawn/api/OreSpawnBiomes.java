package zone.moddev.mc.orespawn.api;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.world.biome.Biome;
import net.minecraft.entity.EntityClassification;
import net.minecraft.world.gen.GenerationStage;
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
					.category(sourceBiome.getCategory())
					.depth(sourceBiome.getDepth())
					.scale(sourceBiome.getScale())
					.temperature(sourceBiome.getDefaultTemperature())
					.downfall(sourceBiome.getDownfall())
					.waterColor(sourceBiome.getWaterColor())
					.waterFogColor(sourceBiome.getWaterFogColor())
					.surfaceBuilder(sourceBiome.getSurfaceBuilder())
					.parent(sourceBiome.getParent());
			edit.accept(builder);
			ProviderBiome result = new ProviderBiome(builder);
			result.copyContents(sourceBiome);
			return result;
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
			return new ProviderBiome(builder);
		});
	}

	/** Concrete 1.15 biome used behind the unchanged public helper contract. */
	private static final class ProviderBiome extends Biome {
		ProviderBiome(Biome.Builder builder) {
			super(builder);
		}

		void copyContents(Biome source) {
			for (GenerationStage.Decoration stage : GenerationStage.Decoration.values()) {
				for (net.minecraft.world.gen.feature.ConfiguredFeature<?, ?> feature : source.getFeatures(stage)) {
					addFeature(stage, feature);
				}
			}
			for (GenerationStage.Carving stage : GenerationStage.Carving.values()) {
				for (net.minecraft.world.gen.carver.ConfiguredCarver<?> carver : source.getCarvers(stage)) {
					addCarverUnchecked(stage, carver);
				}
			}
			structures.putAll(source.structures);
			for (EntityClassification classification : EntityClassification.values()) {
				for (Biome.SpawnListEntry spawn : source.getSpawns(classification)) {
					addSpawn(classification, spawn);
				}
			}
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		private void addCarverUnchecked(GenerationStage.Carving stage,
				net.minecraft.world.gen.carver.ConfiguredCarver<?> carver) {
			addCarver(stage, (net.minecraft.world.gen.carver.ConfiguredCarver) carver);
		}
	}
}
