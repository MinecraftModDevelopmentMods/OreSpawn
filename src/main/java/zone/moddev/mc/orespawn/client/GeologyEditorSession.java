package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.worldgen.OreHeightDistribution;
import zone.moddev.mc.orespawn.worldgen.OrePattern;
import zone.moddev.mc.orespawn.worldgen.GeomeConfig;
import zone.moddev.mc.orespawn.worldgen.RockFamily;
import zone.moddev.mc.orespawn.init.OreSpawnPatterns;
import zone.moddev.mc.orespawn.api.OreDimensionSelector;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** Mutable client-side copy used until the Create World settings are accepted. */
final class GeologyEditorSession {
	enum MaterialTab {
		SEDIMENTARY("sedimentary"),
		METAMORPHIC("metamorphic"),
		IGNEOUS("igneous"),
		ORES("ores"),
		UNASSIGNED("unassigned");

		final String key;

		MaterialTab(String key) {
			this.key = key;
		}
	}

	static final Set<String> BUILT_IN_GEOMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
			"stable_craton", "mountain_belt", "volcanic_arc", "sedimentary_basin",
			"coastal_shelf", "arid_basin", "wetland_basin", "glacial_highland")));

	private final WorldGeologyProfile originalProfile;
	private final JsonObject original;
	private final JsonObject root;
	private final Set<String> availableDimensionIds = new TreeSet<>();

	GeologyEditorSession(WorldGeologyProfile profile) {
		this(profile, Collections.emptyList());
	}

	GeologyEditorSession(WorldGeologyProfile profile, Iterable<String> dimensions) {
		originalProfile = profile;
		original = profile.rootCopy();
		root = profile.rootCopy();
		normalizeRegistrySections(original);
		normalizeRegistrySections(root);
		rememberDimension("minecraft:overworld");
		rememberDimension("minecraft:the_nether");
		rememberDimension("minecraft:the_end");
		for (String dimension : dimensions) rememberDimension(dimension);
	}

	JsonObject root() {
		return root;
	}

	WorldGeologyProfile profile() {
		return originalProfile.withRoot(root);
	}

	void applyProfile(WorldGeologyProfile profile) {
		root.entrySet().clear();
		for (Entry<String, JsonElement> entry : profile.rootCopy().entrySet()) {
			root.add(entry.getKey(), entry.getValue().deepCopy());
		}
		normalizeRegistrySections(root);
	}

	JsonObject section(String key) {
		return object(root, key);
	}

	double oreFrequencyBaseline(String oreId, String dimensionId) {
		if (!original.has("ores") || !original.get("ores").isJsonObject()) return 1.0D;
		JsonObject ores = original.getAsJsonObject("ores");
		if (!ores.has(oreId) || !ores.get(oreId).isJsonObject()) return 1.0D;
		JsonObject ore = ores.getAsJsonObject(oreId);
		String section = OreDimensionSelector.ALL_EXCEPT_NETHER_AND_END.id().toString().equals(dimensionId)
				? "dimension_selectors" : "dimensions";
		if (!ore.has(section) || !ore.get(section).isJsonObject()) return 1.0D;
		JsonObject dimensions = ore.getAsJsonObject(section);
		if (!dimensions.has(dimensionId) || !dimensions.get(dimensionId).isJsonObject()) return 1.0D;
		double value = decimal(dimensions.getAsJsonObject(dimensionId), "frequency", 1.0D);
		return Double.isFinite(value) && value >= 0.0D && value <= OreRichnessPreset.MAX_FREQUENCY
				? value : 1.0D;
	}

	List<String> availableDimensionIds() {
		Set<String> result = new TreeSet<>(availableDimensionIds);
		for (Entry<String, JsonElement> oreEntry : section("ores").entrySet()) {
			JsonElement oreElement = oreEntry.getValue();
			if (!oreElement.isJsonObject()) continue;
			JsonObject ore = oreElement.getAsJsonObject();
			if (!ore.has("dimensions") || !ore.get("dimensions").isJsonObject()) continue;
			for (String id : ore.getAsJsonObject("dimensions").keySet()) {
				if (validResource(id)) result.add(ResourceLocation.parse(id).toString());
			}
		}
		List<String> ordered = new ArrayList<>();
		for (String vanilla : Arrays.asList("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")) {
			ordered.add(vanilla);
			result.remove(vanilla);
		}
		ordered.addAll(result);
		return ordered;
	}

	private void rememberDimension(String id) {
		if (validResource(id)) availableDimensionIds.add(ResourceLocation.parse(id).toString());
	}

	List<String> materialIds(MaterialTab tab, String search, boolean showAll) {
		String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<String> result = new ArrayList<>();
		if (tab == MaterialTab.ORES) {
			result.addAll(section("ores").keySet());
		} else if (tab == MaterialTab.UNASSIGNED) {
			return availableBlockIds(query, "", showAll);
		} else {
			for (Entry<String, JsonElement> entry : section("rocks").entrySet()) {
				if (!entry.getValue().isJsonObject()) {
					continue;
				}
				String family = string(entry.getValue().getAsJsonObject(), "family", "");
				if ((tab == MaterialTab.SEDIMENTARY && "sedimentary".equals(family))
						|| (tab == MaterialTab.METAMORPHIC && "metamorphic".equals(family))
						|| (tab == MaterialTab.IGNEOUS && family.startsWith("igneous_"))) {
					result.add(entry.getKey());
				}
			}
		}
		result.removeIf(id -> !query.isEmpty()
				&& !id.toLowerCase(Locale.ROOT).contains(query)
				&& !materialBlockId(tab, id).toLowerCase(Locale.ROOT).contains(query));
		Collections.sort(result);
		return result;
	}

	List<String> availableBlockIds(String search, String namespace, boolean showAll) {
		String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		String mod = namespace == null ? "" : namespace.trim().toLowerCase(Locale.ROOT);
		Set<String> assigned = assignedBlockIds();
		List<String> result = new ArrayList<>();
		for (Block block : registeredBlocks()) {
			ResourceLocation id = blockId(block);
			if (id == null || assigned.contains(id.toString()) || isWorldgenAliasSource(id.toString())
					|| (!mod.isEmpty() && !mod.equals(id.getNamespace()))
					|| (!query.isEmpty() && !id.toString().toLowerCase(Locale.ROOT).contains(query))
					|| !isSelectable(block, showAll)) {
				continue;
			}
			result.add(id.toString());
		}
		Collections.sort(result);
		return result;
	}

	String materialBlockId(MaterialTab tab, String entryId) {
		if (tab == MaterialTab.UNASSIGNED) return entryId;
		JsonObject section = section(tab == MaterialTab.ORES ? "ores" : "rocks");
		if (!section.has(entryId) || !section.get(entryId).isJsonObject()) return entryId;
		return string(section.getAsJsonObject(entryId), "block", entryId);
	}

	private Set<String> assignedBlockIds() {
		Set<String> result = new HashSet<>();
		for (String sectionName : new String[] { "rocks", "ores" }) {
			for (Entry<String, JsonElement> entry : section(sectionName).entrySet()) {
				if (entry.getValue().isJsonObject()) {
					result.add(string(entry.getValue().getAsJsonObject(), "block", entry.getKey()));
				}
			}
		}
		for (Entry<String, JsonElement> depositEntry : section("fluid_deposits").entrySet()) {
			if (!depositEntry.getValue().isJsonObject()) continue;
			JsonObject deposit = depositEntry.getValue().getAsJsonObject();
			if (!deposit.has("dimensions") || !deposit.get("dimensions").isJsonObject()) continue;
			for (String id : deposit.getAsJsonObject("dimensions").keySet()) {
				if (validResource(id)) result.add(ResourceLocation.parse(id).toString());
			}
		}
		return result;
	}

	List<String> installedBlockNamespaces() {
		TreeSet<String> namespaces = new TreeSet<>();
		for (Block block : registeredBlocks()) {
			ResourceLocation id = blockId(block);
			if (id != null && block != Blocks.AIR && block.asItem() != Items.AIR) {
				namespaces.add(id.getNamespace());
			}
		}
		List<String> result = new ArrayList<>();
		result.add("");
		result.addAll(namespaces);
		return result;
	}

	void assignRock(String id, RockFamily family) {
		String canonicalId = canonicalBlockId(id);
		if (canonicalId == null) {
			return;
		}
		JsonObject rock = new JsonObject();
		rock.addProperty("enabled", true);
		rock.addProperty("family", family.configName);
		rock.addProperty("depth_peak", defaultPeak(family));
		rock.addProperty("depth_spread", 40);
		rock.addProperty("min_y", -64);
		rock.addProperty("max_y", 319);
		rock.addProperty("weight", 1.0D);
		rock.addProperty("ore_replaceable", true);
		rock.add("geomes", new JsonObject());
		section("rocks").add(canonicalId, rock);
		disableOrRemoveOre(canonicalId);
		ensureDefaultOverworldTerrain();
	}

	boolean configureDefaultVanillaStrata() {
		if (!section("rocks").entrySet().isEmpty()) {
			ensureDefaultOverworldTerrain();
			return false;
		}
		ensureDefaultGeologyRules();
		addStarterRock("minecraft:stone", RockFamily.SEDIMENTARY,
				64, 96, -64, 319, 5.0D);
		addStarterRock("minecraft:deepslate", RockFamily.METAMORPHIC,
				-32, 40, -64, 48, 3.0D);
		addStarterRock("minecraft:granite", RockFamily.IGNEOUS_INTRUSIVE,
				0, 72, -64, 192, 1.5D);
		addStarterRock("minecraft:diorite", RockFamily.IGNEOUS_INTRUSIVE,
				24, 64, -64, 192, 1.25D);
		addStarterRock("minecraft:andesite", RockFamily.IGNEOUS_VOLCANIC,
				48, 64, -32, 224, 1.5D);
		addStarterRock("minecraft:tuff", RockFamily.IGNEOUS_VOLCANIC,
				-16, 56, -64, 96, 1.0D);
		ensureDefaultOverworldTerrain();
		return true;
	}

	private void ensureDefaultGeologyRules() {
		JsonObject defaults = GeomeConfig.defaultEditorGeology();
		for (String key : new String[] { "geomes", "biomes", "biome_dictionary" }) {
			if (section(key).entrySet().isEmpty()) root.add(key, defaults.get(key).deepCopy());
		}
	}

	private void addStarterRock(String id, RockFamily family, int peak, int spread,
			int minY, int maxY, double weight) {
		String canonicalId = canonicalBlockId(id);
		if (canonicalId == null) return;
		JsonObject rock = new JsonObject();
		rock.addProperty("enabled", true);
		rock.addProperty("family", family.configName);
		rock.addProperty("depth_peak", peak);
		rock.addProperty("depth_spread", spread);
		rock.addProperty("min_y", minY);
		rock.addProperty("max_y", maxY);
		rock.addProperty("weight", weight);
		rock.addProperty("ore_replaceable", true);
		rock.add("geomes", new JsonObject());
		section("rocks").add(canonicalId, rock);
		disableOrRemoveOre(canonicalId);
	}

	boolean hasTerrainRules() {
		for (Entry<String, JsonElement> entry : section("terrain_dimensions").entrySet()) {
			if (entry.getValue().isJsonObject()
					&& bool(entry.getValue().getAsJsonObject(), "enabled", true)) return true;
		}
		return false;
	}

	private void ensureDefaultOverworldTerrain() {
		if (hasTerrainRules()) return;
		JsonObject dimension = new JsonObject();
		dimension.addProperty("enabled", true);
		dimension.add("biome_ids", new JsonArray());
		dimension.add("biome_namespaces", new JsonArray());
		JsonArray hosts = new JsonArray();
		hosts.add("minecraft:stone");
		hosts.add("minecraft:deepslate");
		dimension.add("host_blocks", hosts);
		dimension.add("host_tags", new JsonArray());
		section("terrain_dimensions").add("minecraft:overworld", dimension);
	}

	void assignOre(String id) {
		String canonicalId = canonicalBlockId(id);
		if (canonicalId == null) {
			return;
		}
		section("rocks").remove(canonicalId);
		JsonObject ore = new JsonObject();
		ore.addProperty("enabled", true);
		ResourceLocation blockId = ResourceLocation.parse(canonicalId);
		ore.addProperty("source_mod", blockId.getNamespace());
		JsonObject dimensions = new JsonObject();
		JsonObject overworld = defaultOreDimension();
		dimensions.add("minecraft:overworld", overworld);
		ore.add("dimensions", dimensions);
		section("ores").add(canonicalId, ore);
	}

	void removeRock(String id) {
		section("rocks").remove(id);
	}

	void disableOrRemoveOre(String id) {
		JsonObject ores = section("ores");
		if (!ores.has(id) || !ores.get(id).isJsonObject()) {
			return;
		}
		JsonObject ore = ores.getAsJsonObject(id);
		if (ore.has("source_provider")) {
			ore.addProperty("enabled", false);
			ore.addProperty("unassigned", true);
		} else {
			ores.remove(id);
		}
	}

	void resetEntry(String section, String id) {
		JsonObject originalSection = object(original, section);
		if (originalSection.has(id)) {
			section(section).add(id, originalSection.get(id).deepCopy());
		}
	}

	JsonObject rock(String id) {
		return objectEntry(section("rocks"), id);
	}

	JsonObject ore(String id) {
		return objectEntry(section("ores"), id);
	}

	JsonObject fluidDeposit(String id) {
		return objectEntry(section("fluid_deposits"), id);
	}

	List<String> fluidDepositIds() {
		List<String> result = new ArrayList<>();
		for (Entry<String, JsonElement> entry : section("fluid_deposits").entrySet()) {
			if (entry.getValue().isJsonObject() && validFluidBlock(string(
					entry.getValue().getAsJsonObject(), "block", ""))) result.add(entry.getKey());
		}
		Collections.sort(result);
		return result;
	}

	int enabledFluidDepositCount() {
		int result = 0;
		for (String id : fluidDepositIds()) {
			if (bool(fluidDeposit(id), "enabled", true)) result++;
		}
		return result;
	}

	boolean placeFluidDeposits() {
		return bool(root, "place_fluid_deposits", true);
	}

	void setPlaceFluidDeposits(boolean enabled) {
		root.addProperty("place_fluid_deposits", enabled);
	}

	List<String> installedBiomeIds() {
		List<String> result = new ArrayList<>();
		for (net.minecraft.world.level.biome.Biome biome : ForgeRegistries.BIOMES.getValues()) {
			ResourceLocation id = ForgeRegistries.BIOMES.getKey(biome);
			if (id != null) result.add(id.toString());
		}
		Collections.sort(result);
		return result;
	}

	String biomePaletteId(String dimensionId) {
		for (Entry<String, JsonElement> entry : section("biome_palettes").entrySet()) {
			if (entry.getValue().isJsonObject() && dimensionId.equals(string(
					entry.getValue().getAsJsonObject(), "dimension", ""))) return entry.getKey();
		}
		return null;
	}

	JsonObject biomePalette(String dimensionId, boolean create) {
		String id = biomePaletteId(dimensionId);
		if (id != null) return objectEntry(section("biome_palettes"), id);
		if (!create) return null;
		id = "orespawn:ui/biome_palette/" + safePath(dimensionId);
		JsonObject palette = new JsonObject();
		palette.addProperty("dimension", dimensionId);
		palette.addProperty("enabled", false);
		palette.addProperty("mode", "augment");
		palette.addProperty("scope", "minecraft_only");
		palette.addProperty("region_size", "average");
		palette.addProperty("coverage", 1.0D);
		palette.addProperty("fallback_weight", 1.0D);
		palette.add("include_namespaces", new JsonArray());
		palette.add("exclude_namespaces", new JsonArray());
		palette.add("biomes", new JsonObject());
		section("biome_palettes").add(id, palette);
		return palette;
	}

	List<String> biomePlacementIds(String dimensionId) {
		JsonObject palette = biomePalette(dimensionId, false);
		if (palette == null) return Collections.emptyList();
		List<String> result = new ArrayList<>(object(palette, "biomes").keySet());
		Collections.sort(result);
		return result;
	}

	JsonObject biomePlacement(String dimensionId, String biomeId) {
		return objectEntry(object(biomePalette(dimensionId, true), "biomes"), biomeId);
	}

	void addBiomePlacement(String dimensionId, String biomeId) {
		if (!knownBiome(biomeId)) return;
		JsonObject palette = biomePalette(dimensionId, true);
		JsonObject placement = new JsonObject();
		placement.addProperty("enabled", true);
		placement.addProperty("weight", 1.0D);
		placement.add("similar_biomes", new JsonArray());
		placement.add("required_similar_biomes", new JsonArray());
		placement.addProperty("min_temperature", -2.0D);
		placement.addProperty("max_temperature", 2.0D);
		placement.addProperty("min_downfall", 0.0D);
		placement.addProperty("max_downfall", 1.0D);
		placement.add("surface", new JsonObject());
		object(palette, "biomes").add(biomeId, placement);
		palette.addProperty("enabled", true);
	}

	void removeBiomePlacement(String dimensionId, String biomeId) {
		JsonObject palette = biomePalette(dimensionId, false);
		if (palette == null) return;
		JsonObject biomes = object(palette, "biomes");
		biomes.remove(biomeId);
		if (biomes.size() == 0) palette.addProperty("enabled", false);
	}

	String dimensionMaterialsId(String dimensionId) {
		for (Entry<String, JsonElement> entry : section("dimension_materials").entrySet()) {
			if (entry.getValue().isJsonObject() && dimensionId.equals(string(
					entry.getValue().getAsJsonObject(), "dimension", ""))) return entry.getKey();
		}
		return null;
	}

	JsonObject dimensionMaterials(String dimensionId, boolean create) {
		String id = dimensionMaterialsId(dimensionId);
		if (id != null) return objectEntry(section("dimension_materials"), id);
		if (!create) return null;
		id = "orespawn:ui/dimension_materials/" + safePath(dimensionId);
		JsonObject materials = new JsonObject();
		materials.addProperty("dimension", dimensionId);
		materials.addProperty("enabled", false);
		materials.addProperty("deep_aquifer_max_y", -54);
		section("dimension_materials").add(id, materials);
		return materials;
	}

	void setMaterialBlock(String dimensionId, String key, String blockId, boolean fluid) {
		if (blockId == null) {
			JsonObject materials = dimensionMaterials(dimensionId, false);
			if (materials != null) {
				materials.remove(key);
				if (!hasMaterialOutput(materials)) materials.addProperty("enabled", false);
			}
			return;
		}
		if (!validResource(blockId)) return;
		Block block = registeredBlock(ResourceLocation.parse(blockId));
		if (block == null || block == Blocks.AIR || (fluid && !isFluidBlock(block))) return;
		JsonObject materials = dimensionMaterials(dimensionId, true);
		materials.addProperty(key, blockId);
		materials.addProperty("enabled", true);
	}

	private static boolean hasMaterialOutput(JsonObject materials) {
		return materials.has("default_fluid") || materials.has("deep_aquifer_fluid")
				|| materials.has("snow_block") || materials.has("ice_block");
	}

	List<String> availableMaterialBlockIds(String search, boolean fluidOnly) {
		String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<String> result = new ArrayList<>();
		for (Block block : registeredBlocks()) {
			ResourceLocation id = blockId(block);
			if (id == null || block == Blocks.AIR || (!fluidOnly && block.asItem() == Items.AIR)
					|| (fluidOnly && !isFluidBlock(block))
					|| (!fluidOnly && block instanceof EntityBlock)
					|| (!query.isEmpty() && !id.toString().contains(query))) continue;
			result.add(id.toString());
		}
		Collections.sort(result);
		return result;
	}

	void removeFluidDeposit(String id) {
		JsonObject deposits = section("fluid_deposits");
		if (!deposits.has(id) || !deposits.get(id).isJsonObject()) return;
		JsonObject deposit = deposits.getAsJsonObject(id);
		if (deposit.has("source_provider")) {
			deposit.addProperty("enabled", false);
			deposit.addProperty("unassigned", true);
		} else {
			deposits.remove(id);
		}
	}

	List<String> availableFluidBlockIds(String search) {
		String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		Set<String> configured = new HashSet<>();
		for (String id : fluidDepositIds()) {
			configured.add(string(fluidDeposit(id), "block", ""));
		}
		List<String> result = new ArrayList<>();
		for (Block block : registeredBlocks()) {
			ResourceLocation id = blockId(block);
			if (id != null && isFluidBlock(block)
					&& !configured.contains(id.toString())
					&& (query.isEmpty() || id.toString().contains(query))) result.add(id.toString());
		}
		Collections.sort(result);
		return result;
	}

	String assignFluidDeposit(String blockId) {
		String canonicalId = canonicalBlockId(blockId);
		if (canonicalId == null || !validFluidBlock(canonicalId)) return null;
		for (Entry<String, JsonElement> entry : section("fluid_deposits").entrySet()) {
			if (entry.getValue().isJsonObject() && canonicalId.equals(string(
					entry.getValue().getAsJsonObject(), "block", ""))) return entry.getKey();
		}
		ResourceLocation fluid = ResourceLocation.parse(canonicalId);
		String baseId = "orespawn:fluid_deposit/" + fluid.getNamespace() + "/" + fluid.getPath();
		String ruleId = baseId;
		for (int suffix = 2; section("fluid_deposits").has(ruleId); suffix++) {
			ruleId = baseId + "_" + suffix;
		}
		JsonObject deposit = new JsonObject();
		deposit.addProperty("enabled", true);
		deposit.addProperty("block", canonicalId);
		JsonObject dimensions = new JsonObject();
		dimensions.add("minecraft:overworld", defaultFluidDepositDimension(hasTerrainRules()));
		deposit.add("dimensions", dimensions);
		section("fluid_deposits").add(ruleId, deposit);
		root.addProperty("place_fluid_deposits", true);
		return ruleId;
	}

	private static JsonObject defaultFluidDepositDimension(boolean terrainActive) {
		JsonObject rule = new JsonObject();
		rule.addProperty("enabled", true);
		rule.addProperty("min_y", -48);
		rule.addProperty("max_y", 48);
		rule.addProperty("frequency", 0.08D);
		rule.addProperty("min_radius", 5);
		rule.addProperty("max_radius", 12);
		rule.addProperty("min_vertical_radius", 2);
		rule.addProperty("max_vertical_radius", 5);
		rule.addProperty("max_lobes", 4);
		rule.addProperty("min_solid_cover", 2);
		rule.addProperty("min_solid_shell", 1);
		JsonArray families = new JsonArray();
		if (terrainActive) {
			for (RockFamily family : RockFamily.values()) families.add(family.configName);
		}
		rule.add("host_families", families);
		rule.add("host_blocks", new JsonArray());
		JsonArray tags = new JsonArray();
		tags.add("minecraft:stone_ore_replaceables");
		tags.add("minecraft:deepslate_ore_replaceables");
		rule.add("host_tags", tags);
		rule.add("biome_ids", new JsonArray());
		rule.add("excluded_biome_ids", new JsonArray());
		rule.add("biome_dictionary", new JsonArray());
		rule.add("excluded_biome_dictionary", new JsonArray());
		rule.add("geomes", new JsonObject());
		return rule;
	}

	List<String> geomeIds() {
		List<String> ids = new ArrayList<>(section("geomes").keySet());
		Collections.sort(ids);
		return ids;
	}

	List<String> configuredBiomeIds() {
		TreeSet<String> ids = new TreeSet<>(section("biomes").keySet());
		for (net.minecraft.world.level.biome.Biome biome : ForgeRegistries.BIOMES.getValues()) {
			ResourceLocation id = ForgeRegistries.BIOMES.getKey(biome);
			if (id != null) {
				ids.add(id.toString());
			}
		}
		return new ArrayList<>(ids);
	}

	List<String> dictionaryIds() {
		List<String> ids = new ArrayList<>(section("biome_dictionary").keySet());
		Collections.sort(ids);
		return ids;
	}

	JsonObject weightMap(String section, String id) {
		JsonObject parent = section(section);
		return objectEntry(parent, id);
	}

	void addGeome(String id) {
		String normalized = id.trim().toLowerCase(Locale.ROOT);
		if (!normalized.matches("[a-z0-9_.-]+") || section("geomes").has(normalized)) {
			return;
		}
		JsonObject geome = new JsonObject();
		geome.addProperty("base", 1.0D);
		JsonObject families = new JsonObject();
		for (RockFamily family : RockFamily.values()) {
			families.addProperty(family.configName, 1.0D);
		}
		geome.add("families", families);
		section("geomes").add(normalized, geome);
	}

	void removeGeome(String id) {
		if (BUILT_IN_GEOMES.contains(id)) {
			return;
		}
		section("geomes").remove(id);
		removeWeightKey(section("biomes"), id);
		removeWeightKey(section("biome_dictionary"), id);
		for (Entry<String, JsonElement> rock : section("rocks").entrySet()) {
			if (rock.getValue().isJsonObject() && rock.getValue().getAsJsonObject().has("geomes")) {
				rock.getValue().getAsJsonObject().getAsJsonObject("geomes").remove(id);
			}
		}
		for (Entry<String, JsonElement> ore : section("ores").entrySet()) {
			if (!ore.getValue().isJsonObject()) continue;
			JsonObject oreObject = ore.getValue().getAsJsonObject();
			for (String section : Arrays.asList("dimensions", "dimension_selectors")) {
				if (!oreObject.has(section) || !oreObject.get(section).isJsonObject()) continue;
				for (Entry<String, JsonElement> dimension : oreObject.getAsJsonObject(section).entrySet()) {
					if (dimension.getValue().isJsonObject()
							&& dimension.getValue().getAsJsonObject().has("geomes")) {
						dimension.getValue().getAsJsonObject().getAsJsonObject("geomes").remove(id);
					}
				}
			}
		}
		for (Entry<String, JsonElement> deposit : section("fluid_deposits").entrySet()) {
			if (!deposit.getValue().isJsonObject()) continue;
			JsonObject definition = deposit.getValue().getAsJsonObject();
			if (!definition.has("dimensions") || !definition.get("dimensions").isJsonObject()) continue;
			for (Entry<String, JsonElement> dimension
					: definition.getAsJsonObject("dimensions").entrySet()) {
				if (dimension.getValue().isJsonObject() && dimension.getValue().getAsJsonObject().has("geomes")) {
					dimension.getValue().getAsJsonObject().getAsJsonObject("geomes").remove(id);
				}
			}
		}
	}

	List<String> validate() {
		List<String> errors = new ArrayList<>();
		JsonObject geomes = section("geomes");
		boolean terrainActive = hasTerrainRules();
		if (terrainActive && geomes.entrySet().isEmpty()) {
			errors.add("At least one geome is required.");
		}
		for (Entry<String, JsonElement> entry : terrainActive
				? geomes.entrySet() : Collections.<Entry<String, JsonElement>>emptySet()) {
			if (!entry.getKey().matches("[a-z0-9_.-]+") || !entry.getValue().isJsonObject()) {
				errors.add("Invalid geome: " + entry.getKey());
				continue;
			}
			JsonObject definition = entry.getValue().getAsJsonObject();
			JsonObject families = definition.has("families") && definition.get("families").isJsonObject()
					? definition.getAsJsonObject("families") : new JsonObject();
			double total = 0.0D;
			for (RockFamily family : RockFamily.values()) {
				double value = decimal(families, family.configName, 0.0D);
				if (value < 0.0D || !Double.isFinite(value)) errors.add("Invalid family weight in " + entry.getKey());
				total += Math.max(0.0D, value);
			}
			if (total <= 0.0D) errors.add("Geome has no available rock families: " + entry.getKey());
		}
		int sedimentary = 0;
		int metamorphic = 0;
		int igneous = 0;
		for (Entry<String, JsonElement> entry : section("rocks").entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				errors.add("Invalid rock block: " + entry.getKey());
				continue;
			}
			JsonObject rock = entry.getValue().getAsJsonObject();
			if (!validBlock(string(rock, "block", entry.getKey()))) {
				errors.add("Invalid rock block: " + string(rock, "block", entry.getKey()));
				continue;
			}
			if (!bool(rock, "enabled", true) || decimal(rock, "weight", 1.0D) <= 0.0D) {
				continue;
			}
			String family = string(rock, "family", "");
			try {
				RockFamily.fromConfigName(family);
			} catch (RuntimeException e) {
				errors.add("Invalid family for " + entry.getKey());
				continue;
			}
			if (integer(rock, "min_y", -64) > integer(rock, "max_y", 319)) {
				errors.add("Minimum Y is above maximum Y for " + entry.getKey());
			}
			if (rock.has("dimensions") && !validIdArray(rock.get("dimensions"))) {
				errors.add("Invalid or empty terrain dimension list for " + entry.getKey());
			}
			validateGeomeWeights(errors, entry.getKey(), rock.get("geomes"), geomes);
			if ("sedimentary".equals(family)) sedimentary++;
			else if ("metamorphic".equals(family)) metamorphic++;
			else igneous++;
		}
		if (hasTerrainRules() && sedimentary + metamorphic + igneous == 0) {
			errors.add("Active terrain replacement needs at least one enabled rock.");
		}

		for (Entry<String, JsonElement> entry : section("ores").entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				errors.add("Invalid ore block: " + entry.getKey());
				continue;
			}
			JsonObject ore = entry.getValue().getAsJsonObject();
			if (!validBlock(string(ore, "block", entry.getKey()))) {
				errors.add("Invalid ore block: " + string(ore, "block", entry.getKey()));
				continue;
			}
			if (!bool(ore, "enabled", true)) {
				continue;
			}
			if (ore.has("deep_output") && !validBlock(string(ore, "deep_output", ""))) {
				errors.add("Invalid deep ore block: " + string(ore, "deep_output", ""));
			}
			JsonObject dimensions = ore.has("dimensions") && ore.get("dimensions").isJsonObject()
					? ore.getAsJsonObject("dimensions") : new JsonObject();
			JsonObject selectors = ore.has("dimension_selectors") && ore.get("dimension_selectors").isJsonObject()
					? ore.getAsJsonObject("dimension_selectors") : new JsonObject();
			if (dimensions.size() == 0 && selectors.size() == 0) {
				errors.add("Ore has no dimension rules: " + entry.getKey());
				continue;
			}
			JsonObject allRules = dimensions.deepCopy();
			for (Entry<String, JsonElement> selector : selectors.entrySet()) {
				if (!OreDimensionSelector.ALL_EXCEPT_NETHER_AND_END.id().toString().equals(selector.getKey())) {
					errors.add("Invalid dimension selector for " + entry.getKey());
					continue;
				}
				allRules.add(selector.getKey(), selector.getValue().deepCopy());
			}
			for (Entry<String, JsonElement> dimension : allRules.entrySet()) {
				if (!validResource(dimension.getKey()) || !dimension.getValue().isJsonObject()) {
					errors.add("Invalid dimension for " + entry.getKey());
					continue;
				}
				JsonObject rule = dimension.getValue().getAsJsonObject();
				boolean hasMinQuantity = rule.has("min_quantity");
				boolean hasMaxQuantity = rule.has("max_quantity");
				int minQuantity = hasMinQuantity ? integer(rule, "min_quantity", 0)
						: integer(rule, "quantity", 0);
				int maxQuantity = hasMaxQuantity ? integer(rule, "max_quantity", 0) : minQuantity;
				if (integer(rule, "min_y", -64) > integer(rule, "max_y", 320)
						|| decimal(rule, "frequency", 0.0D) < 0.0D
						|| decimal(rule, "frequency", 0.0D) > 64.0D
						|| decimal(rule, "discard_chance_on_air_exposure", 0.0D) < 0.0D
						|| decimal(rule, "discard_chance_on_air_exposure", 0.0D) > 1.0D
						|| hasMinQuantity != hasMaxQuantity || minQuantity < 1
						|| minQuantity > maxQuantity || maxQuantity > 64
						|| integer(rule, "spread", 8) < 0 || integer(rule, "spread", 8) > 64
						|| integer(rule, "vertical_spread", 4) < 0
						|| integer(rule, "vertical_spread", 4) > 64
						|| integer(rule, "node_size", 4) < 1 || integer(rule, "node_size", 4) > 32) {
					errors.add("Invalid placement values for " + entry.getKey() + " in " + dimension.getKey());
				}
				try {
					validateOrePattern(rule);
					OreHeightDistribution.fromConfigName(string(rule, "height_distribution", "uniform"));
				} catch (RuntimeException e) {
					errors.add("Invalid ore pattern for " + entry.getKey() + " in " + dimension.getKey());
				}
				if (bool(rule, "enabled", true)) {
					boolean hosts = validBlockArray(rule.get("host_blocks"), errors, entry.getKey())
							|| validIdArray(rule.get("host_tags"));
					if (rule.has("host_families") && rule.get("host_families").isJsonArray()) {
						for (JsonElement family : rule.getAsJsonArray("host_families")) {
							try { RockFamily.fromConfigName(family.getAsString()); hosts = true; }
							catch (RuntimeException e) { errors.add("Invalid host family for " + entry.getKey()); }
						}
					}
					if (!hosts) errors.add("Enabled ore has no hosts: " + entry.getKey());
				}
				validateGeomeWeights(errors, entry.getKey(), rule.get("geomes"), geomes);
			}
		}
		validateRuleGeomes(errors, section("biomes"), geomes, "biome");
		validateRuleGeomes(errors, section("biome_dictionary"), geomes, "biome type");
		for (Entry<String, JsonElement> entry : section("terrain_dimensions").entrySet()) {
			if (!validResource(entry.getKey()) || !entry.getValue().isJsonObject()) {
				errors.add("Invalid terrain dimension: " + entry.getKey());
				continue;
			}
			JsonObject dimension = entry.getValue().getAsJsonObject();
			if (!bool(dimension, "enabled", true)) continue;
			boolean hosts = validBlockArray(dimension.get("host_blocks"), errors, entry.getKey())
					|| validIdArray(dimension.get("host_tags"));
			if (!hosts) errors.add("Enabled terrain dimension has no valid hosts: " + entry.getKey());
			if (dimension.has("biome_ids")) {
				JsonElement biomeIds = dimension.get("biome_ids");
				if (!biomeIds.isJsonArray()
						|| (biomeIds.getAsJsonArray().size() > 0 && !validIdArray(biomeIds))) {
					errors.add("Invalid biome IDs for terrain dimension " + entry.getKey());
				}
			}
		}
		for (Entry<String, JsonElement> entry : section("fluid_deposits").entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				errors.add("Invalid fluid deposit: " + entry.getKey());
				continue;
			}
			JsonObject deposit = entry.getValue().getAsJsonObject();
			if (!validFluidBlock(string(deposit, "block", ""))) {
				errors.add("Fluid deposit output is not a fluid block: " + entry.getKey());
				continue;
			}
			if (!bool(deposit, "enabled", true)) continue;
			if (!deposit.has("dimensions") || !deposit.get("dimensions").isJsonObject()
					|| deposit.getAsJsonObject("dimensions").size() == 0) {
				errors.add("Fluid deposit has no dimension rules: " + entry.getKey());
				continue;
			}
			for (Entry<String, JsonElement> dimension : deposit.getAsJsonObject("dimensions").entrySet()) {
				if (!validResource(dimension.getKey()) || !dimension.getValue().isJsonObject()) {
					errors.add("Invalid fluid dimension for " + entry.getKey());
					continue;
				}
				JsonObject rule = dimension.getValue().getAsJsonObject();
				if (!bool(rule, "enabled", true)) continue;
				int minRadius = integer(rule, "min_radius", 0);
				int maxRadius = integer(rule, "max_radius", 0);
				int minVertical = integer(rule, "min_vertical_radius", 0);
				int maxVertical = integer(rule, "max_vertical_radius", 0);
				if (integer(rule, "min_y", -64) > integer(rule, "max_y", 320)
						|| decimal(rule, "frequency", -1.0D) < 0.0D
						|| decimal(rule, "frequency", -1.0D) > 64.0D
						|| minRadius < 1 || minRadius > maxRadius || maxRadius > 64
						|| minVertical < 1 || minVertical > maxVertical || maxVertical > 64
						|| integer(rule, "max_lobes", 0) < 1 || integer(rule, "max_lobes", 0) > 16
						|| integer(rule, "min_solid_cover", -1) < 0
						|| integer(rule, "min_solid_cover", -1) > 64
						|| integer(rule, "min_solid_shell", 1) < 0
						|| integer(rule, "min_solid_shell", 1) > 64) {
					errors.add("Invalid fluid placement values for " + entry.getKey()
							+ " in " + dimension.getKey());
				}
				boolean hosts = validBlockArray(rule.get("host_blocks"), errors, entry.getKey())
						|| validIdArray(rule.get("host_tags"));
				if (rule.has("host_families") && rule.get("host_families").isJsonArray()) {
					for (JsonElement family : rule.getAsJsonArray("host_families")) {
						try { RockFamily.fromConfigName(family.getAsString()); hosts = true; }
						catch (RuntimeException e) { errors.add("Invalid fluid host family for " + entry.getKey()); }
					}
				}
				if (!hosts) errors.add("Enabled fluid deposit has no hosts: " + entry.getKey());
				validateGeomeWeights(errors, entry.getKey(), rule.get("geomes"), geomes);
			}
		}
		for (Entry<String, JsonElement> entry : section("biome_palettes").entrySet()) {
			if (!validResource(entry.getKey()) || !entry.getValue().isJsonObject()) {
				errors.add("Invalid biome palette: " + entry.getKey());
				continue;
			}
			JsonObject palette = entry.getValue().getAsJsonObject();
			if (!validResource(string(palette, "dimension", ""))) {
				errors.add("Invalid biome palette dimension: " + entry.getKey());
			}
			if (!bool(palette, "enabled", true)) continue;
			JsonObject biomes = palette.has("biomes") && palette.get("biomes").isJsonObject()
					? palette.getAsJsonObject("biomes") : new JsonObject();
			if (biomes.size() == 0) errors.add("Enabled biome palette has no biomes: " + entry.getKey());
			for (Entry<String, JsonElement> biome : biomes.entrySet()) {
				if (!knownBiome(biome.getKey())
						|| !biome.getValue().isJsonObject()) {
					errors.add("Invalid biome placement: " + biome.getKey());
					continue;
				}
				JsonObject placement = biome.getValue().getAsJsonObject();
				if (decimal(placement, "weight", 1.0D) < 0.0D
						|| decimal(placement, "min_temperature", -2.0D)
								> decimal(placement, "max_temperature", 2.0D)
						|| decimal(placement, "min_downfall", 0.0D)
								> decimal(placement, "max_downfall", 1.0D)) {
					errors.add("Invalid biome placement values: " + biome.getKey());
				}
			}
		}
		for (Entry<String, JsonElement> entry : section("dimension_materials").entrySet()) {
			if (!validResource(entry.getKey()) || !entry.getValue().isJsonObject()) {
				errors.add("Invalid dimension materials: " + entry.getKey());
				continue;
			}
			JsonObject materials = entry.getValue().getAsJsonObject();
			if (!validResource(string(materials, "dimension", ""))) {
				errors.add("Invalid dimension materials target: " + entry.getKey());
			}
			for (String key : new String[] { "default_fluid", "deep_aquifer_fluid" }) {
				if (materials.has(key) && !validFluidBlock(string(materials, key, ""))) {
					errors.add("Invalid fluid material in " + entry.getKey());
				}
			}
			for (String key : new String[] { "snow_block", "ice_block" }) {
				if (materials.has(key) && !validBlock(string(materials, key, ""))) {
					errors.add("Invalid weather material in " + entry.getKey());
				}
			}
		}
		return errors;
	}

	private static void validateOrePattern(JsonObject rule) {
		if (!OreSpawnPatterns.isBuiltIn(rule)) {
			OreSpawnPatterns.decode(rule);
			return;
		}
		JsonElement configured = rule.get("pattern");
		String value = configured != null && configured.isJsonObject()
				? string(configured.getAsJsonObject(), "type", "orespawn:vein")
				: string(rule, "pattern", "vein");
		ResourceLocation id = value.indexOf(':') >= 0 ? ResourceLocation.parse(value)
				: ResourceLocation.fromNamespaceAndPath("orespawn", value);
		OrePattern.fromConfigName(id.getPath());
	}

	private static void validateRuleGeomes(List<String> errors, JsonObject rules, JsonObject geomes, String label) {
		for (Entry<String, JsonElement> entry : rules.entrySet()) {
			validateGeomeWeights(errors, label + " " + entry.getKey(), entry.getValue(), geomes);
		}
	}

	private static void validateGeomeWeights(List<String> errors, String owner, JsonElement element,
			JsonObject geomes) {
		if (element == null) return;
		if (!element.isJsonObject()) {
			errors.add("Invalid geome weights for " + owner);
			return;
		}
		for (Entry<String, JsonElement> weight : element.getAsJsonObject().entrySet()) {
			if (!geomes.has(weight.getKey())) {
				errors.add("Unknown geome '" + weight.getKey() + "' in " + owner);
				continue;
			}
			try {
				double value = weight.getValue().getAsDouble();
				if (!Double.isFinite(value) || value < 0.0D) throw new NumberFormatException();
			} catch (RuntimeException e) {
				errors.add("Invalid geome weight in " + owner);
			}
		}
	}

	private static boolean validIdArray(JsonElement element) {
		if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() == 0) return false;
		boolean found = false;
		for (JsonElement value : element.getAsJsonArray()) {
			String id;
			try {
				id = value.isJsonObject() ? string(value.getAsJsonObject(), "tag", "") : value.getAsString();
			} catch (RuntimeException e) {
				return false;
			}
			if (!validResource(id)) return false;
			found = true;
		}
		return found;
	}

	private static boolean validBlockArray(JsonElement element, List<String> errors, String oreId) {
		if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() == 0) return false;
		boolean found = false;
		for (JsonElement value : element.getAsJsonArray()) {
			String id;
			try {
				id = value.isJsonObject() ? string(value.getAsJsonObject(), "block", "") : value.getAsString();
			} catch (RuntimeException e) {
				errors.add("Invalid host block for " + oreId);
				continue;
			}
			if (!validBlock(id)) {
				errors.add("Unknown host block '" + id + "' for " + oreId);
				continue;
			}
			found = true;
		}
		return found;
	}

	static JsonObject defaultOreDimension() {
		JsonObject dimension = new JsonObject();
		dimension.addProperty("enabled", true);
		dimension.addProperty("min_y", -64);
		dimension.addProperty("max_y", 64);
		dimension.addProperty("frequency", 1.0D);
		dimension.addProperty("quantity", 8);
		dimension.addProperty("pattern", "vein");
		dimension.addProperty("height_distribution", "uniform");
		dimension.addProperty("discard_chance_on_air_exposure", 0.0D);
		dimension.addProperty("spread", 8);
		dimension.addProperty("vertical_spread", 4);
		dimension.addProperty("node_size", 4);
		JsonArray families = new JsonArray();
		for (RockFamily family : RockFamily.values()) {
			families.add(family.configName);
		}
		dimension.add("host_families", families);
		JsonArray tags = new JsonArray();
		tags.add("minecraft:stone_ore_replaceables");
		tags.add("minecraft:deepslate_ore_replaceables");
		dimension.add("host_tags", tags);
		return dimension;
	}

	private static boolean isSelectable(Block block, boolean showAll) {
		if (block == Blocks.AIR || block.asItem() == Items.AIR || block instanceof LiquidBlock) {
			return false;
		}
		if (showAll) {
			return true;
		}
		return !(block instanceof EntityBlock)
				&& block.defaultBlockState().blocksMotion()
				&& Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(
						EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
	}

	String canonicalBlockId(String id) {
		try {
			Block block = registeredBlock(ResourceLocation.parse(id));
			ResourceLocation canonical = block == null ? null : blockId(block);
			return block == Blocks.AIR || canonical == null ? null : canonical.toString();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private void normalizeRegistrySections(JsonObject profileRoot) {
		normalizeRegistrySection(profileRoot, "rocks", true);
		normalizeRegistrySection(profileRoot, "ores", false);
	}

	private void normalizeRegistrySection(JsonObject profileRoot, String sectionName, boolean applyAliases) {
		JsonObject source = object(profileRoot, sectionName);
		Map<String, JsonElement> normalized = new LinkedHashMap<>();
		JsonObject aliases = profileRoot.has("worldgen_aliases")
				&& profileRoot.get("worldgen_aliases").isJsonObject()
				? profileRoot.getAsJsonObject("worldgen_aliases") : new JsonObject();
		for (Entry<String, JsonElement> entry : source.entrySet()) {
			String id = canonicalBlockId(entry.getKey());
			if (id == null) id = entry.getKey();
			if (applyAliases && aliases.has(id)) {
				try {
					String target = canonicalBlockId(aliases.get(id).getAsString());
					if (target != null) id = target;
				} catch (RuntimeException ignored) { }
			}
			normalized.putIfAbsent(id, entry.getValue().deepCopy());
		}
		JsonObject replacement = new JsonObject();
		for (Entry<String, JsonElement> entry : normalized.entrySet()) {
			replacement.add(entry.getKey(), entry.getValue());
		}
		profileRoot.add(sectionName, replacement);
	}

	private boolean isWorldgenAliasSource(String id) {
		JsonObject aliases = section("worldgen_aliases");
		if (!aliases.has(id)) return false;
		try {
			return !id.equals(ResourceLocation.parse(aliases.get(id).getAsString()).toString());
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static int defaultPeak(RockFamily family) {
		switch (family) {
		case SEDIMENTARY: return 60;
		case METAMORPHIC: return 28;
		case IGNEOUS_INTRUSIVE: return 24;
		case IGNEOUS_VOLCANIC: return 72;
		default: return 48;
		}
	}

	private static void removeWeightKey(JsonObject rules, String geome) {
		for (Entry<String, JsonElement> rule : rules.entrySet()) {
			if (rule.getValue().isJsonObject()) {
				rule.getValue().getAsJsonObject().remove(geome);
			}
		}
	}

	private static JsonObject object(JsonObject parent, String key) {
		if (!parent.has(key) || !parent.get(key).isJsonObject()) {
			JsonObject result = new JsonObject();
			parent.add(key, result);
			return result;
		}
		return parent.getAsJsonObject(key);
	}

	private static JsonObject objectEntry(JsonObject parent, String key) {
		if (!parent.has(key) || !parent.get(key).isJsonObject()) {
			JsonObject result = new JsonObject();
			parent.add(key, result);
			return result;
		}
		return parent.getAsJsonObject(key);
	}

	private static boolean validBlock(String id) {
		if (!validResource(id)) return false;
		Block block = registeredBlock(ResourceLocation.parse(id));
		return block != null && block != Blocks.AIR;
	}

	private static String safePath(String registryId) {
		return registryId.toLowerCase(Locale.ROOT).replace(':', '/')
				.replaceAll("[^a-z0-9_./-]", "_");
	}

	private static boolean validFluidBlock(String id) {
		if (!validResource(id)) return false;
		Block block = registeredBlock(ResourceLocation.parse(id));
		return isFluidBlock(block);
	}

	private static boolean isFluidBlock(Block block) {
		return block != null && block != Blocks.AIR
				&& (block instanceof LiquidBlock || !block.defaultBlockState().getFluidState().isEmpty());
	}

	private static Iterable<Block> registeredBlocks() {
		return ForgeRegistries.BLOCKS.containsKey(ResourceLocation.fromNamespaceAndPath("minecraft", "stone"))
				? ForgeRegistries.BLOCKS.getValues() : BuiltInRegistries.BLOCK;
	}

	private static Block registeredBlock(ResourceLocation id) {
		Block builtIn = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
		if (builtIn != null) {
			return builtIn;
		}
		return ForgeRegistries.BLOCKS.containsKey(id) ? ForgeRegistries.BLOCKS.getValue(id) : null;
	}

	private static ResourceLocation blockId(Block block) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
		return id != null ? id : ForgeRegistries.BLOCKS.getKey(block);
	}

	private static boolean knownBiome(String id) {
		if (!validResource(id)) return false;
		// Biomes are dynamic in 1.20.6. Before the live registry is available,
		// retain syntactically valid provider IDs for server-side resolution.
		return ForgeRegistries.BIOMES.getKeys().isEmpty()
				|| ForgeRegistries.BIOMES.containsKey(ResourceLocation.parse(id));
	}

	private static boolean validResource(String id) {
		try {
			ResourceLocation.parse(id);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	static String string(JsonObject json, String key, String fallback) {
		try { return json.has(key) ? json.get(key).getAsString() : fallback; }
		catch (RuntimeException e) { return fallback; }
	}

	static boolean bool(JsonObject json, String key, boolean fallback) {
		try { return json.has(key) ? json.get(key).getAsBoolean() : fallback; }
		catch (RuntimeException e) { return fallback; }
	}

	static int integer(JsonObject json, String key, int fallback) {
		try { return json.has(key) ? json.get(key).getAsInt() : fallback; }
		catch (RuntimeException e) { return fallback; }
	}

	static double decimal(JsonObject json, String key, double fallback) {
		try { return json.has(key) ? json.get(key).getAsDouble() : fallback; }
		catch (RuntimeException e) { return fallback; }
	}
}
