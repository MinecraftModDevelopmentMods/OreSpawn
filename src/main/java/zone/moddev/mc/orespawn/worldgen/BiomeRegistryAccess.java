package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

/**
 * Holds the active datapack biome registry outside worldgen hot loops.
 *
 * <p>NeoForge 21.1 does not expose dynamic biomes through a
 * static registry. The registry is bound from world creation or server
 * lifecycle state and all geology tables are baked after that binding.</p>
 */
public final class BiomeRegistryAccess {
	private static volatile Registry<Biome> active;

	private BiomeRegistryAccess() {
	}

	public static void bind(RegistryAccess access) {
		active = access.registryOrThrow(Registries.BIOME);
	}

	public static void clear() {
		active = null;
	}

	public static boolean isBound() {
		return active != null;
	}

	public static List<Biome> values() {
		Registry<Biome> registry = active;
		return registry == null ? Collections.emptyList() : registry.stream().toList();
	}

	public static Set<ResourceLocation> keys() {
		Registry<Biome> registry = active;
		return registry == null ? Collections.emptySet() : registry.keySet();
	}

	public static boolean contains(ResourceLocation id) {
		Registry<Biome> registry = active;
		return registry != null && registry.containsKey(id);
	}

	public static Biome get(ResourceLocation id) {
		Registry<Biome> registry = active;
		return registry == null ? null : registry.get(id);
	}

	public static ResourceLocation id(Biome biome) {
		Registry<Biome> registry = active;
		return registry == null ? null : registry.getKey(biome);
	}

	public static Optional<Holder.Reference<Biome>> holder(ResourceKey<Biome> key) {
		Registry<Biome> registry = active;
		return registry == null ? Optional.empty() : registry.getHolder(key);
	}

	public static Holder<Biome> holder(Biome biome) {
		Registry<Biome> registry = active;
		return registry == null ? Holder.direct(biome) : registry.wrapAsHolder(biome);
	}
}
