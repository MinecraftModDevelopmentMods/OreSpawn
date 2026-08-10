package zone.moddev.mc.orespawn.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.carver.WorldCarverWrapper;
import net.minecraft.world.gen.feature.CompositeFeature;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * Small registration helpers for provider mods which want to define biomes
 * without taking a compile-time dependency on a separate biome framework.
 *
 * <p>Forge 25 predates {@code DeferredRegister}. Provider mods declare one
 * {@link BiomeRegistrar} during mod construction and use the same deferred
 * declaration pattern exposed by later OreSpawn ports.</p>
 */
public final class OreSpawnBiomes {
	private OreSpawnBiomes() {
	}

	/**
	 * Creates a registrar for a provider mod and attaches it to that mod's event
	 * bus. This method must be called during normal mod construction.
	 */
	public static BiomeRegistrar registrar(String modId) {
		return new BiomeRegistrar(modId, true);
	}

	static BiomeRegistrar registrarForTesting(String modId) {
		return new BiomeRegistrar(modId, false);
	}

	/**
	 * Registers a biome copied from an existing biome, then applies provider
	 * changes to the copied builder.
	 */
	public static BiomeReference copyAndRegister(BiomeRegistrar registrar,
			String name, Supplier<? extends Biome> source, Consumer<Biome.BiomeBuilder> edit) {
		Objects.requireNonNull(registrar, "registrar");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(edit, "edit");
		return registrar.register(name, () -> {
			Biome sourceBiome = Objects.requireNonNull(source.get(), "source biome");
			Biome.BiomeBuilder builder = new Biome.BiomeBuilder()
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
	 * climate and surface fields before the builder is built.
	 */
	public static BiomeReference blankAndRegister(BiomeRegistrar registrar,
			String name, Consumer<Biome.BiomeBuilder> configure) {
		Objects.requireNonNull(registrar, "registrar");
		Objects.requireNonNull(configure, "configure");
		return registrar.register(name, () -> {
			Biome.BiomeBuilder builder = new Biome.BiomeBuilder();
			configure.accept(builder);
			return new ProviderBiome(builder);
		});
	}

	/**
	 * Deferred biome registrar for Forge 25. Registration order follows declaration
	 * order and declarations are rejected after the registry event begins.
	 */
	public static final class BiomeRegistrar {
		private final String modId;
		private final Map<ResourceLocation, Supplier<? extends Biome>> entries = new LinkedHashMap<>();
		private boolean registering;

		private BiomeRegistrar(String modId, boolean attach) {
			this.modId = Objects.requireNonNull(modId, "modId");
			// Validate the namespace immediately and attach exactly once.
			new ResourceLocation(modId, "registrar_probe");
			if (attach) {
				FMLJavaModLoadingContext.get().getModEventBus()
						.addGenericListener(Biome.class, this::registerBiomes);
			}
		}

		private synchronized BiomeReference register(String name, Supplier<? extends Biome> factory) {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(factory, "factory");
			if (registering) {
				throw new IllegalStateException("Biome declarations are closed for " + modId);
			}
			ResourceLocation id = new ResourceLocation(modId, name);
			if (entries.putIfAbsent(id, factory) != null) {
				throw new IllegalArgumentException("Duplicate biome declaration: " + id);
			}
			return new BiomeReference(id);
		}

		private synchronized void registerBiomes(RegistryEvent.Register<Biome> event) {
			registerRegistry(event.getRegistry());
		}

		synchronized void registerForTesting(IForgeRegistry<Biome> registry) {
			registerRegistry(registry);
		}

		private void registerRegistry(IForgeRegistry<Biome> registry) {
			if (registering) {
				throw new IllegalStateException("Biome registrar invoked more than once for " + modId);
			}
			registering = true;
			for (Map.Entry<ResourceLocation, Supplier<? extends Biome>> entry : entries.entrySet()) {
				Biome biome = Objects.requireNonNull(entry.getValue().get(),
						"Biome factory returned null for " + entry.getKey());
				if (biome.getRegistryName() != null && !entry.getKey().equals(biome.getRegistryName())) {
					throw new IllegalStateException("Biome factory returned an already named biome: "
							+ biome.getRegistryName());
				}
				registry.register(biome.setRegistryName(entry.getKey()));
			}
		}
	}

	/** Supplier-compatible biome handle shared with later OreSpawn APIs. */
	public static final class BiomeReference implements Supplier<Biome> {
		private final ResourceLocation id;

		private BiomeReference(ResourceLocation id) {
			this.id = id;
		}

		/** Returns the stable registry identifier declared by this handle. */
		public ResourceLocation getId() {
			return id;
		}

		@Override
		public Biome get() {
			return get(ForgeRegistries.BIOMES);
		}

		Biome get(IForgeRegistry<Biome> registry) {
			Biome biome = registry.getValue(id);
			if (biome == null) {
				throw new IllegalStateException("Biome is not registered yet: " + id);
			}
			return biome;
		}
	}

	/** Concrete Forge 25 biome used behind the public helper contract. */
	private static final class ProviderBiome extends Biome {
		ProviderBiome(Biome.BiomeBuilder builder) {
			super(builder);
		}

		void copyContents(Biome source) {
			for (GenerationStage.Decoration stage : GenerationStage.Decoration.values()) {
				for (CompositeFeature<?, ?> feature : source.getFeatures(stage)) {
					addFeature(stage, feature);
				}
			}
			for (GenerationStage.Carving stage : GenerationStage.Carving.values()) {
				for (WorldCarverWrapper<?> carver : source.getCarvers(stage)) {
					addCarverUnchecked(stage, carver);
				}
			}
			structures.putAll(source.structures);
			for (EnumCreatureType classification : EnumCreatureType.values()) {
				for (Biome.SpawnListEntry spawn : source.getSpawns(classification)) {
					addSpawn(classification, spawn);
				}
			}
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		private void addCarverUnchecked(GenerationStage.Carving stage, WorldCarverWrapper<?> carver) {
			addCarver(stage, (WorldCarverWrapper) carver);
		}
	}
}
