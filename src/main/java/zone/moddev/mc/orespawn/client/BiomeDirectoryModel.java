package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager.BiomeProviderDefaultsSnapshot;
import zone.moddev.mc.orespawn.util.JsonCopies;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Immutable projection of loaded biomes, profile palettes, and provider defaults. */
final class BiomeDirectoryModel {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final String END = "minecraft:the_end";

	private BiomeDirectoryModel() { }

	static Snapshot snapshot(JsonObject profile, BiomeProviderDefaultsSnapshot defaults,
			Iterable<String> dimensions) {
		List<LoadedBiome> loaded = new ArrayList<>();
		Map<String, ModInfo> mods = new LinkedHashMap<>();
		for (ModContainer mod : Loader.instance().getActiveModList()) {
			mods.put(mod.getModId(), new ModInfo(mod.getModId(), mod.getName(), mod.getVersion()));
		}
		mods.putIfAbsent("minecraft", new ModInfo("minecraft", "Minecraft", "1.10.2"));
		for (Entry<ResourceLocation, Biome> entry : ForgeRegistries.BIOMES.getEntries()) {
			ModInfo owner = mods.get(entry.getKey().getResourceDomain());
			loaded.add(new LoadedBiome(entry.getKey().toString(), entry.getValue().getBiomeName(),
					owner == null ? entry.getKey().getResourceDomain() : owner.name,
					owner == null ? "?" : owner.version, routineDimension(entry.getValue())));
		}
		return assemble(profile, defaults.biomePalettesCopy(),
				defaults.activeProviderIds(), loaded, dimensions);
	}

	static Snapshot assemble(JsonObject profile, JsonObject providerPalettes,
			Set<String> activeProviders, List<LoadedBiome> loadedBiomes,
			Iterable<String> requestedDimensions) {
		Map<String, LoadedBiome> loaded = new LinkedHashMap<>();
		for (LoadedBiome biome : loadedBiomes) loaded.put(biome.id, biome);
		Set<String> dimensions = new LinkedHashSet<>();
		for (String dimension : requestedDimensions) dimensions.add(dimension);
		JsonObject profilePalettes = object(profile, "biome_palettes");
		List<Palette> palettes = new ArrayList<>();
		int order = 0;
		for (Entry<String, JsonElement> entry : profilePalettes.entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject palette = entry.getValue().getAsJsonObject();
			String dimension = string(palette, "dimension", "minecraft:overworld");
			dimensions.add(dimension);
			palettes.add(new Palette(entry.getKey(), dimension,
					string(palette, "source_provider", owner(entry.getKey())),
					order++, bool(palette, "enabled", true),
					BiomeReplacementRules.isOverridePalette(entry.getKey()),
					palette.has("source_provider"), palette));
		}

		Map<String, Set<String>> providerBiomes = providerBiomes(providerPalettes);
		Map<String, Set<String>> declaredDimensions = declaredDimensions(palettes, providerBiomes);
		List<BiomeEntry> entries = new ArrayList<>();
		for (String dimension : dimensions) {
			Map<String, List<Placement>> placements = placements(palettes, dimension);
			Map<String, String> replacements = BiomeReplacementRules.read(profile, dimension);
			Set<String> ids = new LinkedHashSet<>();
			for (LoadedBiome biome : loaded.values()) {
				Set<String> declared = declaredDimensions.getOrDefault(biome.id, Collections.emptySet());
				if (declared.isEmpty() ? dimension.equals(biome.routineDimension)
						: declared.contains(dimension)) ids.add(biome.id);
			}
			ids.addAll(placements.keySet());
			ids.addAll(providerBiomes.getOrDefault(dimension, Collections.emptySet()));
			ids.addAll(replacements.keySet());
			ids.addAll(replacements.values());
			for (String id : ids) {
				LoadedBiome biome = loaded.get(id);
				List<Placement> rules = placements.getOrDefault(id, Collections.emptyList());
				boolean managed = providerBiomes.getOrDefault(dimension, Collections.emptySet()).contains(id);
				boolean enabled = false;
				boolean missingProvider = false;
				for (Placement placement : rules) enabled |= placement.enabled;
				for (Placement placement : rules) missingProvider |= placement.providerOwned
						&& !activeProviders.contains(placement.owner);
				Status status;
				if (biome == null || missingProvider) status = Status.MISSING;
				else if (replacements.containsKey(id)) status = Status.USER_REPLACED;
				else if (rules.size() > 1) status = Status.LAYERED;
				else if (!rules.isEmpty() && !enabled) status = Status.DISABLED;
				else if (managed || !rules.isEmpty()) status = Status.PROVIDER_MANAGED;
				else status = Status.UNMANAGED;
				entries.add(new BiomeEntry(dimension, id,
						biome == null ? friendly(id) : biome.name,
						biome == null ? owner(id) : biome.ownerName,
						biome == null ? "?" : biome.ownerVersion,
						status, rules, replacements.get(id), managed));
			}
		}
		Comparator<BiomeEntry> entryOrder = Comparator
				.comparingInt((BiomeEntry entry) -> entry.status.priority)
				.thenComparing(entry -> entry.name.toLowerCase(Locale.ROOT))
				.thenComparing(entry -> entry.id)
				.thenComparing(entry -> entry.dimension);
		entries.sort(entryOrder);
		palettes.sort(Comparator.comparingInt(palette -> palette.order));
		return new Snapshot(entries, palettes, dimensions, activeProviders);
	}

	static String routineDimension(Biome biome) {
		if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.NETHER)) return NETHER;
		if (BiomeDictionary.isBiomeOfType(biome, BiomeDictionary.Type.END)) return END;
		return OVERWORLD;
	}

	private static Map<String, Set<String>> declaredDimensions(List<Palette> palettes,
			Map<String, Set<String>> providerBiomes) {
		Map<String, Set<String>> result = new LinkedHashMap<>();
		for (Palette palette : palettes) {
			if (palette.override) continue;
			for (String id : JsonCopies.keys(object(palette.definition, "biomes"))) {
				result.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(palette.dimension);
			}
		}
		for (Entry<String, Set<String>> dimension : providerBiomes.entrySet()) {
			for (String id : dimension.getValue()) {
				result.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(dimension.getKey());
			}
		}
		return result;
	}

	private static Map<String, Set<String>> providerBiomes(JsonObject providerPalettes) {
		Map<String, Set<String>> result = new LinkedHashMap<>();
		for (Entry<String, JsonElement> entry : providerPalettes.entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject palette = entry.getValue().getAsJsonObject();
			String dimension = string(palette, "dimension", "");
			Set<String> values = result.computeIfAbsent(dimension, ignored -> new LinkedHashSet<>());
			for (Entry<String, JsonElement> biome : object(palette, "biomes").entrySet()) {
				values.add(biome.getKey());
			}
		}
		return result;
	}

	private static Map<String, List<Placement>> placements(List<Palette> palettes, String dimension) {
		Map<String, List<Placement>> result = new LinkedHashMap<>();
		Map<String, Integer> lastSurface = new LinkedHashMap<>();
		for (Palette palette : palettes) {
			if (!dimension.equals(palette.dimension) || palette.override) continue;
			for (Entry<String, JsonElement> entry : object(palette.definition, "biomes").entrySet()) {
				if (!entry.getValue().isJsonObject()) continue;
				JsonObject placement = entry.getValue().getAsJsonObject();
				List<Placement> list = result.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>());
				boolean hasSurface = placement.has("surface") && placement.get("surface").isJsonObject()
						&& !placement.getAsJsonObject("surface").entrySet().isEmpty();
				list.add(new Placement(palette.id, palette.owner, palette.order,
						palette.enabled && bool(placement, "enabled", true), hasSurface, false,
						palette.providerOwned, placement));
				if (palette.enabled && bool(placement, "enabled", true) && hasSurface) {
					lastSurface.put(entry.getKey(), list.size() - 1);
				}
			}
		}
		for (Entry<String, List<Placement>> entry : result.entrySet()) {
			Integer effective = lastSurface.get(entry.getKey());
			if (effective == null) continue;
			List<Placement> updated = new ArrayList<>();
			for (int i = 0; i < entry.getValue().size(); i++) {
				Placement value = entry.getValue().get(i);
				updated.add(value.withEffectiveSurface(i == effective));
			}
			entry.setValue(updated);
		}
		return result;
	}

	private static String owner(String id) {
		try { return new ResourceLocation(id).getResourceDomain(); }
		catch (RuntimeException ignored) { return "unknown"; }
	}

	private static String friendly(String id) {
		String value;
		try { value = new ResourceLocation(id).getResourcePath(); }
		catch (RuntimeException ignored) { value = id; }
		StringBuilder result = new StringBuilder();
		for (String part : value.replace('/', '_').split("_")) {
			if (part.isEmpty()) continue;
			if (result.length() > 0) result.append(' ');
			result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return result.length() == 0 ? id : result.toString();
	}

	private static JsonObject object(JsonObject root, String key) {
		return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key) : new JsonObject();
	}
	private static String string(JsonObject root, String key, String fallback) {
		return root.has(key) ? root.get(key).getAsString() : fallback;
	}
	private static boolean bool(JsonObject root, String key, boolean fallback) {
		return root.has(key) ? root.get(key).getAsBoolean() : fallback;
	}

	enum Status {
		USER_REPLACED(0), LAYERED(1), DISABLED(2), MISSING(3), PROVIDER_MANAGED(4), UNMANAGED(5);
		final int priority;
		Status(int priority) { this.priority = priority; }
	}

	static final class Snapshot {
		final List<BiomeEntry> entries;
		final List<Palette> palettes;
		final Set<String> dimensions;
		final Set<String> activeProviders;
		Snapshot(List<BiomeEntry> entries, List<Palette> palettes, Set<String> dimensions,
				Set<String> activeProviders) {
			this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
			this.palettes = Collections.unmodifiableList(new ArrayList<>(palettes));
			this.dimensions = Collections.unmodifiableSet(new LinkedHashSet<>(dimensions));
			this.activeProviders = Collections.unmodifiableSet(new LinkedHashSet<>(activeProviders));
		}
		List<BiomeEntry> entries(String dimension, boolean showAll) {
			List<BiomeEntry> result = new ArrayList<>();
			for (BiomeEntry entry : entries) {
				if (dimension.equals(entry.dimension) && (showAll || entry.status != Status.UNMANAGED)) result.add(entry);
			}
			return Collections.unmodifiableList(result);
		}
		List<Palette> palettes(String dimension) {
			List<Palette> result = new ArrayList<>();
			for (Palette palette : palettes) if (dimension.equals(palette.dimension)) result.add(palette);
			return Collections.unmodifiableList(result);
		}
	}

	static final class LoadedBiome {
		final String id, name, ownerName, ownerVersion, routineDimension;
		LoadedBiome(String id, String name, String ownerName, String ownerVersion) {
			this(id, name, ownerName, ownerVersion, OVERWORLD);
		}
		LoadedBiome(String id, String name, String ownerName, String ownerVersion,
				String routineDimension) {
			this.id = id; this.name = name; this.ownerName = ownerName; this.ownerVersion = ownerVersion;
			this.routineDimension = routineDimension;
		}
	}

	static final class BiomeEntry {
		final String dimension, id, name, ownerName, ownerVersion;
		final Status status;
		final List<Placement> placements;
		final String replacementTarget;
		final boolean providerDeclared;
		BiomeEntry(String dimension, String id, String name, String ownerName, String ownerVersion,
				Status status, List<Placement> placements, String replacementTarget,
				boolean providerDeclared) {
			this.dimension = dimension; this.id = id; this.name = name;
			this.ownerName = ownerName; this.ownerVersion = ownerVersion; this.status = status;
			this.placements = Collections.unmodifiableList(new ArrayList<>(placements));
			this.replacementTarget = replacementTarget; this.providerDeclared = providerDeclared;
		}
	}

	static final class Palette {
		final String id, dimension, owner;
		final int order;
		final boolean enabled, override, providerOwned;
		final JsonObject definition;
		Palette(String id, String dimension, String owner, int order, boolean enabled,
				boolean override, boolean providerOwned, JsonObject definition) {
			this.id = id; this.dimension = dimension; this.owner = owner; this.order = order;
			this.enabled = enabled; this.override = override; this.providerOwned = providerOwned;
			this.definition = JsonCopies.copy(definition);
		}
	}

	static final class Placement {
		final String paletteId, owner;
		final int order;
		final boolean enabled, hasSurface, effectiveSurface, providerOwned;
		final JsonObject definition;
		Placement(String paletteId, String owner, int order, boolean enabled,
				boolean hasSurface, boolean effectiveSurface, boolean providerOwned,
				JsonObject definition) {
			this.paletteId = paletteId; this.owner = owner; this.order = order;
			this.enabled = enabled; this.hasSurface = hasSurface;
			this.effectiveSurface = effectiveSurface; this.providerOwned = providerOwned;
			this.definition = JsonCopies.copy(definition);
		}
		Placement withEffectiveSurface(boolean value) {
			return new Placement(paletteId, owner, order, enabled, hasSurface, value,
					providerOwned, definition);
		}
	}

	private static final class ModInfo {
		final String id, name, version;
		ModInfo(String id, String name, String version) { this.id = id; this.name = name; this.version = version; }
	}
}
