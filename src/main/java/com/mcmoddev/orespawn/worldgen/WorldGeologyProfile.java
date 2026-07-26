package com.mcmoddev.orespawn.worldgen;

import java.util.Locale;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawnConfig.GeologyMode;
import com.mcmoddev.orespawn.worldgen.FormationSettings.Algorithm;
import com.mcmoddev.orespawn.worldgen.FormationSettings.Preset;
import com.mcmoddev.orespawn.integration.WorldgenIntegrationManager;

import net.minecraft.resources.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** A complete, self-contained snapshot of the geology settings for one world. */
public final class WorldGeologyProfile {
	public static final int SCHEMA_VERSION = 5;

	private static final Logger LOGGER = LogManager.getLogger();

	private final JsonObject root;
	private final GeologyMode geologyMode;
	private final Algorithm algorithm;
	private final Preset horizontalSize;
	private final Preset verticalThickness;
	private final Preset waviness;
	private final Preset edgeIrregularity;
	private final Preset formationContinuity;
	private final boolean placeFluidDeposits;

	private WorldGeologyProfile(JsonObject root, GeologyMode fallbackMode, boolean fallbackFluidDeposits) {
		this.root = root.deepCopy();
		FluidDepositMigration.normalize(this.root);
		this.root.addProperty("schema_version", SCHEMA_VERSION);
		geologyMode = enumValue(this.root, "geology_mode", GeologyMode.class, fallbackMode);
		placeFluidDeposits = booleanValue(this.root, "place_fluid_deposits", fallbackFluidDeposits);
		this.root.addProperty("geology_mode", geologyMode.name().toLowerCase(Locale.ROOT));
		this.root.addProperty("place_fluid_deposits", placeFluidDeposits);

		JsonObject formations = object(this.root, "formations", recommendedFormationJson());
		algorithm = namedValue(formations, "algorithm", Algorithm.STABLE_LAYERS, Algorithm::fromConfigName);
		horizontalSize = namedValue(formations, "horizontal_size", Preset.AVERAGE, Preset::fromConfigName);
		verticalThickness = namedValue(formations, "vertical_thickness", Preset.AVERAGE, Preset::fromConfigName);
		waviness = namedValue(formations, "waviness", Preset.AVERAGE, Preset::fromConfigName);
		edgeIrregularity = namedValue(formations, "edge_irregularity", Preset.AVERAGE, Preset::fromConfigName);
		formationContinuity = namedValue(formations, "formation_continuity", Preset.AVERAGE,
				Preset::fromConfigName);
		this.root.add("formations", normalizedFormationJson(formations));
	}

	public static WorldGeologyProfile recommended(boolean placeFluidDeposits) {
		JsonObject root = new JsonObject();
		root.addProperty("geology_mode", GeologyMode.GEOME.name().toLowerCase(Locale.ROOT));
		root.addProperty("place_fluid_deposits", placeFluidDeposits);
		root.add("formations", recommendedFormationJson());
		return new WorldGeologyProfile(root, GeologyMode.GEOME, placeFluidDeposits);
	}

	public static WorldGeologyProfile fromGlobalConfig(JsonObject globalRoot,
			GeologyMode geologyMode, boolean placeFluidDeposits) {
		JsonObject root = globalRoot.deepCopy();
		if (!root.has("geology_mode")) {
			root.addProperty("geology_mode", geologyMode.name().toLowerCase(Locale.ROOT));
		}
		if (!root.has("place_fluid_deposits") && !root.has("place_crude_oil")) {
			root.addProperty("place_fluid_deposits", placeFluidDeposits);
		}
		return new WorldGeologyProfile(root, geologyMode, placeFluidDeposits);
	}

	public static WorldGeologyProfile fromJson(JsonObject json, WorldGeologyProfile fallback) {
		int schema = intValue(json, "schema_version", 1);
		if (schema >= SCHEMA_VERSION) {
			return new WorldGeologyProfile(json, fallback.geologyMode, fallback.placeFluidDeposits);
		}
		if (schema == 2 || schema == 3 || schema == 4) {
			JsonObject migrated = json.deepCopy();
			for (String key : new String[] { "terrain_dimensions", "providers",
					"biome_palettes", "dimension_materials" }) {
				if (!migrated.has(key) && fallback.root.has(key)) {
					migrated.add(key, fallback.root.get(key).deepCopy());
				}
			}
			return new WorldGeologyProfile(migrated, fallback.geologyMode, fallback.placeFluidDeposits);
		}

		// Schema 1 contained only mode, oil and formations. Overlay those fields on
		// the currently effective pack profile so the resulting profile is complete.
		JsonObject migrated = fallback.rootCopy();
		copyIfPresent(json, migrated, "geology_mode");
		copyIfPresent(json, migrated, "place_crude_oil");
		copyIfPresent(json, migrated, "formations");
		return new WorldGeologyProfile(migrated, fallback.geologyMode, fallback.placeFluidDeposits);
	}

	public WorldGeologyProfile withSelection(GeologyMode mode,
			Preset horizontal, Preset thickness, Preset wave,
			Preset irregularity, Preset continuity, boolean fluidDeposits) {
		JsonObject edited = rootCopy();
		edited.addProperty("geology_mode", mode.name().toLowerCase(Locale.ROOT));
		edited.addProperty("place_fluid_deposits", fluidDeposits);
		JsonObject formations = toFormationJson();
		formations.addProperty("algorithm", Algorithm.STABLE_LAYERS.configName());
		formations.addProperty("horizontal_size", horizontal.configName());
		formations.addProperty("vertical_thickness", thickness.configName());
		formations.addProperty("waviness", wave.configName());
		formations.addProperty("edge_irregularity", irregularity.configName());
		formations.addProperty("formation_continuity", continuity.configName());
		edited.add("formations", formations);
		return new WorldGeologyProfile(edited, mode, fluidDeposits);
	}

	public WorldGeologyProfile withRoot(JsonObject editedRoot) {
		return new WorldGeologyProfile(editedRoot, geologyMode, placeFluidDeposits);
	}

	public WorldGeologyProfile withTemplate(ResourceLocation templateId) {
		return new WorldGeologyProfile(WorldgenIntegrationManager.applyTemplate(
				GeomeConfig.globalBaseConfigSnapshot(), templateId),
				geologyMode, placeFluidDeposits);
	}

	public WorldGeologyProfile withoutTemplate() {
		JsonObject edited = GeomeConfig.globalBaseConfigSnapshot();
		edited.remove("selected_template");
		return new WorldGeologyProfile(edited, geologyMode, placeFluidDeposits);
	}

	public Optional<ResourceLocation> selectedTemplate() {
		if (!root.has("selected_template")) {
			return Optional.empty();
		}
		try {
			return Optional.of(new ResourceLocation(root.get("selected_template").getAsString()));
		} catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	public WorldGeologyProfile copy() {
		return new WorldGeologyProfile(root, geologyMode, placeFluidDeposits);
	}

	public JsonObject toJson() {
		return rootCopy();
	}

	public JsonObject rootCopy() {
		JsonObject copy = root.deepCopy();
		copy.addProperty("schema_version", SCHEMA_VERSION);
		return copy;
	}

	public JsonObject toGeomeConfigJson() {
		JsonObject copy = rootCopy();
		copy.addProperty("schema_version", GeomeConfig.SCHEMA_VERSION);
		return copy;
	}

	public JsonObject toFormationJson() {
		return root.getAsJsonObject("formations").deepCopy();
	}

	public GeologyMode geologyMode() {
		return geologyMode;
	}

	public Algorithm algorithm() {
		return algorithm;
	}

	public Preset horizontalSize() {
		return horizontalSize;
	}

	public Preset verticalThickness() {
		return verticalThickness;
	}

	public Preset waviness() {
		return waviness;
	}

	public Preset edgeIrregularity() {
		return edgeIrregularity;
	}

	public Preset formationContinuity() {
		return formationContinuity;
	}

	public boolean placeFluidDeposits() {
		return placeFluidDeposits;
	}

	/** @deprecated Use {@link #placeFluidDeposits()}. */
	@Deprecated
	public boolean placeCrudeOil() {
		return placeFluidDeposits;
	}

	public int fluidDepositCount() {
		return sectionSize("fluid_deposits");
	}

	public int enabledFluidDepositCount() {
		if (!root.has("fluid_deposits") || !root.get("fluid_deposits").isJsonObject()) return 0;
		int count = 0;
		for (java.util.Map.Entry<String, JsonElement> entry
				: root.getAsJsonObject("fluid_deposits").entrySet()) {
			if (entry.getValue().isJsonObject()
					&& booleanValue(entry.getValue().getAsJsonObject(), "enabled", true)) count++;
		}
		return count;
	}

	public boolean manageVanillaOres() {
		return booleanValue(root, "manage_vanilla_ores", false);
	}

	public boolean suppressAllOreFeatures() {
		return booleanValue(root, "suppress_all_ore_features", false);
	}

	public boolean oreRetrogenEnabled() {
		return nestedBoolean("retrogen", "enabled", false);
	}

	public boolean forceOreRetrogen() {
		return nestedBoolean("retrogen", "force", false);
	}

	public int retrogenChunksPerTick() {
		return nestedInt("retrogen", "chunks_per_tick", 1, 1, 16);
	}

	public boolean flatBedrockEnabled() {
		return nestedBoolean("flat_bedrock", "enabled", false);
	}

	public boolean flatBedrockRetrogenEnabled() {
		return nestedBoolean("flat_bedrock", "retrogen", false);
	}

	public int flatBedrockLayers() {
		return nestedInt("flat_bedrock", "layers", 1, 1, 5);
	}

	public int generationRevision() {
		int configured = nestedInt("retrogen", "revision", 0, 0, Integer.MAX_VALUE);
		if (configured > 0) {
			return configured;
		}
		int hash = 17;
		for (String key : new String[] { "ores", "fluid_deposits", "flat_bedrock", "providers" }) {
			if (root.has(key)) {
				hash = (31 * hash) + root.get(key).toString().hashCode();
			}
		}
		return hash & Integer.MAX_VALUE;
	}

	public int cyanoGeomeSize() {
		return nestedInt("cyano", "geome_size", 256, 4, Short.MAX_VALUE);
	}

	public double cyanoRockLayerNoise() {
		return nestedDouble("cyano", "rock_layer_noise", 32.0D, 1.0D, Short.MAX_VALUE);
	}

	public int cyanoLayerThickness() {
		return nestedInt("cyano", "rock_layer_thickness", 8, 1, 255);
	}

	private static JsonObject recommendedFormationJson() {
		JsonObject formations = new JsonObject();
		formations.addProperty("algorithm", Algorithm.STABLE_LAYERS.configName());
		formations.addProperty("horizontal_size", Preset.AVERAGE.configName());
		formations.addProperty("vertical_thickness", Preset.AVERAGE.configName());
		formations.addProperty("waviness", Preset.AVERAGE.configName());
		formations.addProperty("edge_irregularity", Preset.AVERAGE.configName());
		formations.addProperty("formation_continuity", Preset.AVERAGE.configName());
		JsonObject custom = new JsonObject();
		custom.addProperty("stratum_wavelength", 256.0D);
		custom.addProperty("family_region_wavelength", 100.0D);
		custom.addProperty("vertical_thickness", 8);
		custom.addProperty("waviness_wavelength", 256.0D);
		custom.addProperty("waviness_amplitude", 48.0D);
		custom.addProperty("edge_wavelength", 64.0D);
		custom.addProperty("edge_amplitude", 12.0D);
		custom.addProperty("edge_octaves", 2);
		custom.addProperty("continuity", 0.85D);
		formations.add("custom", custom);
		return formations;
	}

	private static JsonObject normalizedFormationJson(JsonObject source) {
		JsonObject fallback = recommendedFormationJson();
		JsonObject result = source.deepCopy();
		JsonObject custom = object(result, "custom", fallback.getAsJsonObject("custom"));
		for (String key : new String[] { "stratum_wavelength", "family_region_wavelength",
				"vertical_thickness", "waviness_wavelength", "waviness_amplitude",
				"edge_wavelength", "edge_amplitude", "edge_octaves", "continuity" }) {
			if (!custom.has(key)) {
				custom.add(key, fallback.getAsJsonObject("custom").get(key).deepCopy());
			}
		}
		result.add("custom", custom);
		return result;
	}

	private static void copyIfPresent(JsonObject source, JsonObject target, String key) {
		if (source.has(key)) {
			target.add(key, source.get(key).deepCopy());
		}
	}

	private int nestedInt(String section, String key, int fallback, int min, int max) {
		try {
			JsonObject object = root.has(section) && root.get(section).isJsonObject()
					? root.getAsJsonObject(section) : null;
			int value = object != null && object.has(key) ? object.get(key).getAsInt() : fallback;
			return Math.max(min, Math.min(max, value));
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private int sectionSize(String key) {
		return root.has(key) && root.get(key).isJsonObject() ? root.getAsJsonObject(key).size() : 0;
	}

	private boolean nestedBoolean(String section, String key, boolean fallback) {
		try {
			JsonObject object = root.has(section) && root.get(section).isJsonObject()
					? root.getAsJsonObject(section) : null;
			return object != null && object.has(key) ? object.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private double nestedDouble(String section, String key, double fallback, double min, double max) {
		try {
			JsonObject object = root.has(section) && root.get(section).isJsonObject()
					? root.getAsJsonObject(section) : null;
			double value = object != null && object.has(key) ? object.get(key).getAsDouble() : fallback;
			return Math.max(min, Math.min(max, value));
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static JsonObject object(JsonObject source, String key, JsonObject fallback) {
		JsonElement element = source.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : fallback.deepCopy();
	}

	private static boolean booleanValue(JsonObject source, String key, boolean fallback) {
		try {
			return source.has(key) ? source.get(key).getAsBoolean() : fallback;
		} catch (RuntimeException e) {
			LOGGER.warn("Invalid OreSpawn world geology value for '{}'; using {}", key, fallback);
			return fallback;
		}
	}

	private static int intValue(JsonObject source, String key, int fallback) {
		try {
			return source.has(key) ? source.get(key).getAsInt() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static <T extends Enum<T>> T enumValue(JsonObject source, String key, Class<T> type, T fallback) {
		try {
			return source.has(key)
					? Enum.valueOf(type, source.get(key).getAsString().toUpperCase(Locale.ROOT)) : fallback;
		} catch (RuntimeException e) {
			LOGGER.warn("Invalid OreSpawn world geology value for '{}'; using {}", key, fallback);
			return fallback;
		}
	}

	private static <T> T namedValue(JsonObject source, String key, T fallback, NamedValueParser<T> parser) {
		try {
			return source.has(key) ? parser.parse(source.get(key).getAsString()) : fallback;
		} catch (RuntimeException e) {
			LOGGER.warn("Invalid OreSpawn world geology value for '{}'; using {}", key, fallback);
			return fallback;
		}
	}

	@FunctionalInterface
	private interface NamedValueParser<T> {
		T parse(String value);
	}
}
