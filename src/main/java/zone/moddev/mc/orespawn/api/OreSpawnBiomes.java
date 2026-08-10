package zone.moddev.mc.orespawn.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeDecorator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * Forge 1.12 biome registration helpers for provider mods. The registrar keeps
 * the same deferred declaration semantics as later OreSpawn ports while using
 * Forge 14 registry events and {@link Biome.BiomeProperties}.
 */
public final class OreSpawnBiomes {
	private OreSpawnBiomes() {
	}

	public static BiomeRegistrar registrar(String modId) {
		return new BiomeRegistrar(modId, true);
	}

	static BiomeRegistrar registrarForTesting(String modId) {
		return new BiomeRegistrar(modId, false);
	}

	public static BiomeReference copyAndRegister(BiomeRegistrar registrar,
			String name, Supplier<? extends Biome> source,
			Consumer<Biome.BiomeProperties> edit) {
		Objects.requireNonNull(registrar, "registrar");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(edit, "edit");
		return registrar.register(name, () -> {
			Biome sourceBiome = Objects.requireNonNull(source.get(), "source biome");
			Biome.BiomeProperties properties = copiedProperties(name, sourceBiome);
			edit.accept(properties);
			ProviderBiome result = new ProviderBiome(properties);
			result.copyContents(sourceBiome);
			return result;
		});
	}

	public static BiomeReference blankAndRegister(BiomeRegistrar registrar,
			String name, Consumer<Biome.BiomeProperties> configure) {
		Objects.requireNonNull(registrar, "registrar");
		Objects.requireNonNull(configure, "configure");
		return registrar.register(name, () -> {
			Biome.BiomeProperties properties = new Biome.BiomeProperties(name);
			configure.accept(properties);
			return new ProviderBiome(properties);
		});
	}

	private static Biome.BiomeProperties copiedProperties(String name, Biome source) {
		Biome.BiomeProperties properties = new Biome.BiomeProperties(name)
				.setBaseHeight(source.getBaseHeight())
				.setHeightVariation(source.getHeightVariation())
				.setTemperature(source.getDefaultTemperature())
				.setRainfall(source.getRainfall())
				.setWaterColor(source.getWaterColor());
		if (!source.canRain()) properties.setRainDisabled();
		if (source.getEnableSnow()) properties.setSnowEnabled();
		if (source.getRegistryName() != null) {
			properties.setBaseBiome(source.getRegistryName().toString());
		}
		return properties;
	}

	public static final class BiomeRegistrar {
		private final String modId;
		private final Map<ResourceLocation, Supplier<? extends Biome>> entries = new LinkedHashMap<>();
		private boolean registering;

		private BiomeRegistrar(String modId, boolean attach) {
			this.modId = Objects.requireNonNull(modId, "modId");
			new ResourceLocation(modId, "registrar_probe");
			if (attach) MinecraftForge.EVENT_BUS.register(this);
		}

		private synchronized BiomeReference register(String name,
				Supplier<? extends Biome> factory) {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(factory, "factory");
			if (registering) {
				throw new IllegalStateException("Biome declarations are closed for " + modId);
			}
			ResourceLocation id = new ResourceLocation(modId, name);
			if (entries.containsKey(id)) {
				throw new IllegalArgumentException("Duplicate biome declaration: " + id);
			}
			entries.put(id, factory);
			return new BiomeReference(id);
		}

		@SubscribeEvent
		public synchronized void registerBiomes(RegistryEvent.Register<Biome> event) {
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
				if (biome.getRegistryName() != null
						&& !entry.getKey().equals(biome.getRegistryName())) {
					throw new IllegalStateException("Biome factory returned an already named biome: "
							+ biome.getRegistryName());
				}
				registry.register(biome.setRegistryName(entry.getKey()));
			}
		}
	}

	public static final class BiomeReference implements Supplier<Biome> {
		private final ResourceLocation id;

		private BiomeReference(ResourceLocation id) {
			this.id = id;
		}

		public ResourceLocation getId() {
			return id;
		}

		@Override
		public Biome get() {
			return get(ForgeRegistries.BIOMES);
		}

		Biome get(IForgeRegistry<Biome> registry) {
			Biome biome = registry.getValue(id);
			if (biome == null) throw new IllegalStateException("Biome is not registered yet: " + id);
			return biome;
		}
	}

	private static final class ProviderBiome extends Biome {
		ProviderBiome(Biome.BiomeProperties properties) {
			super(properties);
		}

		void copyContents(Biome source) {
			topBlock = source.topBlock;
			fillerBlock = source.fillerBlock;
			decorator = copyDecorator(source.decorator);
			for (EnumCreatureType type : EnumCreatureType.values()) {
				getSpawnableList(type).clear();
				getSpawnableList(type).addAll(source.getSpawnableList(type));
			}
		}

		private static BiomeDecorator copyDecorator(BiomeDecorator source) {
			BiomeDecorator copy = new BiomeDecorator();
			copy.clayGen = source.clayGen; copy.sandGen = source.sandGen;
			copy.gravelGen = source.gravelGen; copy.dirtGen = source.dirtGen;
			copy.gravelOreGen = source.gravelOreGen; copy.graniteGen = source.graniteGen;
			copy.dioriteGen = source.dioriteGen; copy.andesiteGen = source.andesiteGen;
			copy.coalGen = source.coalGen; copy.ironGen = source.ironGen;
			copy.goldGen = source.goldGen; copy.redstoneGen = source.redstoneGen;
			copy.diamondGen = source.diamondGen; copy.lapisGen = source.lapisGen;
			copy.flowerGen = source.flowerGen; copy.mushroomBrownGen = source.mushroomBrownGen;
			copy.mushroomRedGen = source.mushroomRedGen; copy.bigMushroomGen = source.bigMushroomGen;
			copy.reedGen = source.reedGen; copy.cactusGen = source.cactusGen;
			copy.waterlilyGen = source.waterlilyGen;
			copy.waterlilyPerChunk = source.waterlilyPerChunk;
			copy.treesPerChunk = source.treesPerChunk; copy.extraTreeChance = source.extraTreeChance;
			copy.flowersPerChunk = source.flowersPerChunk; copy.grassPerChunk = source.grassPerChunk;
			copy.deadBushPerChunk = source.deadBushPerChunk; copy.mushroomsPerChunk = source.mushroomsPerChunk;
			copy.reedsPerChunk = source.reedsPerChunk; copy.cactiPerChunk = source.cactiPerChunk;
			copy.gravelPatchesPerChunk = source.gravelPatchesPerChunk;
			copy.sandPatchesPerChunk = source.sandPatchesPerChunk;
			copy.clayPerChunk = source.clayPerChunk; copy.bigMushroomsPerChunk = source.bigMushroomsPerChunk;
			copy.generateFalls = source.generateFalls;
			return copy;
		}
	}
}
