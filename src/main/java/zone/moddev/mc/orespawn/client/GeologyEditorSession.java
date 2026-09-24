package zone.moddev.mc.orespawn.client;

import zone.moddev.mc.orespawn.util.JsonCopies;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import com.google.gson.JsonPrimitive;
import zone.moddev.mc.orespawn.worldgen.OreHeightDistribution;
import zone.moddev.mc.orespawn.worldgen.OrePattern;
import zone.moddev.mc.orespawn.worldgen.GeomeConfig;
import zone.moddev.mc.orespawn.worldgen.RockFamily;
import zone.moddev.mc.orespawn.init.OreSpawnPatterns;
import zone.moddev.mc.orespawn.api.OreDimensionSelector;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;
import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager;
import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager.BiomeProviderDefaultsSnapshot;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

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
	private JsonObject oreSourceOriginal;
	private final BiomeProviderDefaultsSnapshot biomeProviderDefaults;
	private final Set<String> availableDimensionIds = new TreeSet<>();

	GeologyEditorSession(WorldGeologyProfile profile) {
		this(profile, Collections.emptyList());
	}

	GeologyEditorSession(WorldGeologyProfile profile, Iterable<String> dimensions) {
		this(profile, dimensions, WorldgenIntegrationManager.biomeProviderDefaults());
	}

	GeologyEditorSession(WorldGeologyProfile profile, Iterable<String> dimensions,
			BiomeProviderDefaultsSnapshot defaults) {
		originalProfile = profile;
		biomeProviderDefaults = defaults;
		original = profile.rootCopy();
		root = profile.rootCopy();
		normalizeRegistrySections(original);
		normalizeRegistrySections(root);
		oreSourceOriginal = profile.rootCopy();
		normalizeRegistrySections(oreSourceOriginal);
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
			root.add(entry.getKey(), JsonCopies.copy(entry.getValue()));
		}
		normalizeRegistrySections(root);
	}

	void setManageVanillaOres(boolean enabled) {
		WorldGeologyProfile current = profile();
		JsonObject refreshed = current.rootCopy();
		refreshed.addProperty("manage_vanilla_ores", enabled);
		applyProfile(WorldGeologyProfile.fromGlobalConfig(refreshed,
				current.geologyMode(), current.placeFluidDeposits()));
		synchronizeVanillaPlacementSources(enabled);
	}

	private void synchronizeVanillaPlacementSources(boolean enabled) {
		JsonObject ores = section("ores");
		for (Entry<String, JsonElement> entry : section("ore_source_policies").entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject policy = entry.getValue().getAsJsonObject();
			JsonObject placements = objectEntry(policy, "placement_sources");
			if (!policy.has("candidates") || !policy.get("candidates").isJsonArray()) continue;
			for (JsonElement element : policy.getAsJsonArray("candidates")) {
				if (!element.isJsonObject()) continue;
				JsonObject candidate = element.getAsJsonObject();
				String sourceId = string(candidate, "source_id", "");
				if (!ores.has(sourceId) || !ores.get(sourceId).isJsonObject()
						|| !bool(ores.getAsJsonObject(sourceId), "native_generation", false)) continue;
				String channel = string(candidate, "placement_channel", "orespawn:standard");
				if (enabled && bool(candidate, "loaded", false)
						&& bool(candidate, "placement_active", false)
						&& !bool(candidate, "external", false)
						&& !bool(candidate, "enrichment", false)
						&& !placements.has(channel)) {
					placements.addProperty(channel, sourceId);
				} else if (!enabled && placements.has(channel)
						&& sourceId.equals(string(placements, channel, ""))) {
					placements.remove(channel);
				}
			}
			refreshOreSourceStatus(policy);
		}
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
			for (String id : JsonCopies.keys(ore.getAsJsonObject("dimensions"))) {
				if (validResource(id)) result.add(new ResourceLocation(id).toString());
			}
		}
		for (String sectionName : Arrays.asList("biome_palettes", "dimension_materials")) {
			for (Entry<String, JsonElement> entry : section(sectionName).entrySet()) {
				if (!entry.getValue().isJsonObject()) continue;
				String id = string(entry.getValue().getAsJsonObject(), "dimension", "");
				if (validResource(id)) result.add(new ResourceLocation(id).toString());
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
		if (validResource(id)) availableDimensionIds.add(new ResourceLocation(id).toString());
	}

	List<String> materialIds(MaterialTab tab, String search, boolean showAll) {
		String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<String> result = new ArrayList<>();
		if (tab == MaterialTab.ORES) {
			result.addAll(JsonCopies.keys(section("ores")));
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
		for (Block block : ForgeRegistries.BLOCKS.getValues()) {
			ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
			if (id == null || assigned.contains(id.toString()) || isWorldgenAliasSource(id.toString())
					|| (!mod.isEmpty() && !mod.equals(id.getResourceDomain()))
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
			for (String id : JsonCopies.keys(deposit.getAsJsonObject("dimensions"))) {
				if (validResource(id)) result.add(new ResourceLocation(id).toString());
			}
		}
		return result;
	}

	List<String> installedBlockNamespaces() {
		TreeSet<String> namespaces = new TreeSet<>();
		for (Block block : ForgeRegistries.BLOCKS.getValues()) {
			ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
			if (id != null && block != Blocks.AIR && Item.getItemFromBlock(block) != null) {
				namespaces.add(id.getResourceDomain());
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
		rock.addProperty("min_y", 0);
		rock.addProperty("max_y", 255);
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
		addStarterRock("minecraft:stone", "minecraft:stone", 0, RockFamily.SEDIMENTARY,
				64, 96, 0, 255, 5.0D);
		addStarterRock("orespawn:vanilla_granite", "minecraft:stone", 1, RockFamily.IGNEOUS_INTRUSIVE,
				0, 72, 0, 192, 1.5D);
		addStarterRock("orespawn:vanilla_diorite", "minecraft:stone", 3, RockFamily.IGNEOUS_INTRUSIVE,
				24, 64, 0, 192, 1.25D);
		addStarterRock("orespawn:vanilla_andesite", "minecraft:stone", 5, RockFamily.IGNEOUS_VOLCANIC,
				48, 64, 0, 224, 1.5D);
		ensureDefaultOverworldTerrain();
		return true;
	}

	private void ensureDefaultGeologyRules() {
		JsonObject defaults = GeomeConfig.defaultEditorGeology();
		for (String key : new String[] { "geomes", "biomes", "biome_dictionary" }) {
			if (section(key).entrySet().isEmpty()) root.add(key, JsonCopies.copy(defaults.get(key)));
		}
	}

	private void addStarterRock(String ruleId, String blockId, int metadata,
			RockFamily family, int peak, int spread,
			int minY, int maxY, double weight) {
		String canonicalId = canonicalBlockId(blockId);
		if (canonicalId == null || !validResource(ruleId)) return;
		JsonObject rock = new JsonObject();
		rock.addProperty("block", canonicalId);
		rock.addProperty("metadata", metadata);
		rock.addProperty("enabled", true);
		rock.addProperty("family", family.configName);
		rock.addProperty("depth_peak", peak);
		rock.addProperty("depth_spread", spread);
		rock.addProperty("min_y", minY);
		rock.addProperty("max_y", maxY);
		rock.addProperty("weight", weight);
		rock.addProperty("ore_replaceable", true);
		rock.add("geomes", new JsonObject());
		section("rocks").add(ruleId, rock);
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
		hosts.add(new JsonPrimitive("minecraft:stone"));
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
		ResourceLocation blockId = new ResourceLocation(canonicalId);
		ore.addProperty("source_mod", blockId.getResourceDomain());
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

	List<OreSourceGroup> oreSourceGroups() {
		List<OreSourceGroup> result = new ArrayList<>();
		for (Entry<String, JsonElement> entry : section("ore_source_policies").entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject policy = entry.getValue().getAsJsonObject();
			String material = string(policy, "material", "");
			String domain = string(policy, "domain", "");
			if (!validResource(material) || !validResource(domain)) continue;
			JsonObject groupDefinitions = section("ore_material_groups");
			JsonObject groupDefinition = groupDefinitions.has(material)
					&& groupDefinitions.get(material).isJsonObject()
					? groupDefinitions.getAsJsonObject(material) : new JsonObject();
			String displayName = string(groupDefinition, "display_name", displayName(material));
			List<String> groupDictionary = stringList(groupDefinition, "ore_dictionary_entries");
			boolean curated = "orespawn:sulfur".equals(material) || "orespawn:aluminum".equals(material);
			List<OreSourceCandidate> candidates = new ArrayList<>();
			if (policy.has("candidates") && policy.get("candidates").isJsonArray()) {
				for (JsonElement element : policy.getAsJsonArray("candidates")) {
					if (!element.isJsonObject()) continue;
					JsonObject candidate = element.getAsJsonObject();
					String sourceId = string(candidate, "source_id", "");
					String channel = string(candidate, "placement_channel", "orespawn:standard");
					String registryId = string(candidate, "registry_id", "");
					if (sourceId.isEmpty() || !validResource(channel) || !validResource(registryId)) continue;
					List<String> dictionary = new ArrayList<>();
					if (candidate.has("ore_dictionary") && candidate.get("ore_dictionary").isJsonArray()) {
						for (JsonElement name : candidate.getAsJsonArray("ore_dictionary")) {
							try { dictionary.add(name.getAsString()); }
							catch (RuntimeException ignored) { }
						}
					}
					candidates.add(new OreSourceCandidate(sourceId,
							string(candidate, "owner", ""), string(candidate, "owner_name", ""),
							string(candidate, "owner_version", ""), registryId,
							integer(candidate, "metadata", 0), channel, dictionary,
							bool(candidate, "loaded", false), bool(candidate, "placement_active", true),
							bool(candidate, "external", false),
							bool(candidate, "enrichment", false),
							bool(candidate, "review_required", false)));
				}
			}
			Collections.sort(candidates, (left, right) -> {
				int owner = left.owner.compareTo(right.owner);
				return owner != 0 ? owner : left.sourceId.compareTo(right.sourceId);
			});
			JsonObject viewPolicy = JsonCopies.copy(policy);
			refreshOreSourceStatus(viewPolicy);
			OreSourceGroup group = new OreSourceGroup(entry.getKey(), material, domain,
					displayName, groupDictionary, curated,
					string(policy, "mode", "keep_separate"), outputMode(policy),
					string(viewPolicy, "status", "review_required"),
					candidates, decimalMap(policy, "outputs"), stringMap(policy, "placement_sources"));
			if (!bool(policy, "dormant", false)) result.add(group);
		}
		Collections.sort(result, (left, right) -> {
			int name = left.displayName.compareToIgnoreCase(right.displayName);
			if (name != 0) return name;
			int material = left.material.compareTo(right.material);
			return material != 0 ? material : left.domain.compareTo(right.domain);
		});
		return Collections.unmodifiableList(result);
	}

	String addOreMaterialGroup() {
		JsonObject groups = section("ore_material_groups");
		JsonObject policies = section("ore_source_policies");
		String base = "orespawn:custom/custom_group";
		String id = base;
		for (int suffix = 2; groups.has(id)
				|| policies.has(id + "|minecraft:overworld"); suffix++) id = base + '_' + suffix;
		JsonObject definition = new JsonObject();
		definition.addProperty("display_name", "Custom Group");
		definition.add("ore_dictionary_entries", new JsonArray());
		groups.add(id, definition);
		String key = id + "|minecraft:overworld";
		JsonObject policy = objectEntry(policies, key);
		policy.remove("dormant");
		policy.addProperty("material", id);
		policy.addProperty("domain", "minecraft:overworld");
		policy.addProperty("mode", "keep_separate");
		policy.addProperty("output_mode", "custom");
		policy.addProperty("status", "separate");
		policy.addProperty("review_required", false);
		policy.add("outputs", new JsonObject());
		policy.add("placement_sources", new JsonObject());
		policy.add("candidates", new JsonArray());
		return key;
	}

	void renameOreMaterialGroup(String material, String displayName) {
		if (!validResource(material)) return;
		String value = displayName == null ? "" : displayName.trim();
		if (value.isEmpty()) return;
		objectEntry(section("ore_material_groups"), material)
				.addProperty("display_name", value.length() > 64 ? value.substring(0, 64) : value);
	}

	String oreDictionaryOwner(String alias) {
		for (Entry<String, JsonElement> entry : section("ore_material_groups").entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			if (stringList(entry.getValue().getAsJsonObject(), "ore_dictionary_entries").contains(alias)) {
				return entry.getKey();
			}
		}
		return null;
	}

	boolean vanillaOreManagementRequiredForAliasMove(String material, String alias) {
		if (bool(root, "manage_vanilla_ores", false)) return false;
		String owner = oreDictionaryOwner(alias);
		if (owner == null || owner.equals(material)) return false;
		JsonObject ores = section("ores");
		for (OreSourceGroup group : oreSourceGroups()) {
			if (!owner.equals(group.material)) continue;
			for (OreSourceCandidate candidate : group.candidates) {
				if (!candidate.loaded || candidate.external || !candidate.oreDictionary.contains(alias)
						|| !ores.has(candidate.sourceId) || !ores.get(candidate.sourceId).isJsonObject()) continue;
				if (bool(ores.getAsJsonObject(candidate.sourceId), "native_generation", false)) return true;
			}
		}
		return false;
	}

	boolean addOreMaterialAlias(String material, String alias, boolean moveExisting) {
		if (!validResource(material) || !validOreDictionaryName(alias)) return false;
		String owner = oreDictionaryOwner(alias);
		if (owner != null && !owner.equals(material)) {
			if (!moveExisting) return false;
			if (vanillaOreManagementRequiredForAliasMove(material, alias)) return false;
			removeOreMaterialAlias(owner, alias);
		}
		JsonObject definition = objectEntry(section("ore_material_groups"), material);
		List<String> names = stringList(definition, "ore_dictionary_entries");
		if (!names.contains(alias)) names.add(alias);
		Collections.sort(names);
		definition.add("ore_dictionary_entries", strings(names));
		rebuildOreSourcePolicies();
		return true;
	}

	void removeOreMaterialAlias(String material, String alias) {
		if (!validResource(material)) return;
		JsonObject definition = objectEntry(section("ore_material_groups"), material);
		List<String> names = stringList(definition, "ore_dictionary_entries");
		if (names.remove(alias)) {
			definition.add("ore_dictionary_entries", strings(names));
			rebuildOreSourcePolicies();
		}
	}

	void resetOreMaterialGroup(String material) {
		if ("orespawn:sulfur".equals(material)) {
			setOreMaterialGroup(material, "Sulfur", Arrays.asList("oreSulfur", "oreSulphur"));
		} else if ("orespawn:aluminum".equals(material)) {
			setOreMaterialGroup(material, "Aluminum", Arrays.asList("oreAluminum", "oreAluminium"));
		}
		rebuildOreSourcePolicies();
	}

	boolean deleteEmptyOreMaterialGroup(String material) {
		if (!isCustomMaterial(material)) return false;
		JsonObject groups = section("ore_material_groups");
		if (!groups.has(material) || !groups.get(material).isJsonObject()
				|| !stringList(groups.getAsJsonObject(material), "ore_dictionary_entries").isEmpty()) {
			return false;
		}
		groups.remove(material);
		markMaterialPoliciesDormant(material);
		rebuildOreSourcePolicies();
		return true;
	}

	boolean dissolveOreMaterialGroup(String material) {
		if (!isCustomMaterial(material)) return false;
		JsonObject groups = section("ore_material_groups");
		if (!groups.has(material) || !groups.get(material).isJsonObject()) return false;
		List<String> aliases = stringList(groups.getAsJsonObject(material), "ore_dictionary_entries");
		if (aliases.isEmpty()) return false;
		groups.remove(material);
		for (String alias : aliases) {
			String restoredMaterial = inferredMaterialForAlias(alias);
			JsonObject definition = objectEntry(groups, restoredMaterial);
			if (!definition.has("display_name")) {
				definition.addProperty("display_name", displayName(restoredMaterial));
			}
			List<String> restoredAliases = stringList(definition, "ore_dictionary_entries");
			if (!restoredAliases.contains(alias)) restoredAliases.add(alias);
			Collections.sort(restoredAliases);
			definition.add("ore_dictionary_entries", strings(restoredAliases));
		}
		markMaterialPoliciesDormant(material);
		rebuildOreSourcePolicies();
		return true;
	}

	private void markMaterialPoliciesDormant(String material) {
		for (Entry<String, JsonElement> entry : section("ore_source_policies").entrySet()) {
			if (entry.getValue().isJsonObject()
					&& material.equals(string(entry.getValue().getAsJsonObject(), "material", ""))) {
				entry.getValue().getAsJsonObject().addProperty("dormant", true);
			}
		}
	}

	boolean oreMaterialGroupsChanged() {
		return !sectionCopy(original, "ore_material_groups").equals(sectionCopy(root, "ore_material_groups"));
	}

	JsonObject oreMaterialGroupsCopy() {
		return sectionCopy(root, "ore_material_groups");
	}

	void resetOreSourcesToDefaults() {
		WorldGeologyProfile current = profile();
		JsonObject reset = current.rootCopy();
		reset.remove("ore_material_groups");
		reset.remove("ore_source_policies");
		WorldGeologyProfile defaults = WorldGeologyProfile.fromGlobalConfig(reset,
				current.geologyMode(), current.placeFluidDeposits());
		applyProfile(defaults);
		oreSourceOriginal = defaults.rootCopy();
		normalizeRegistrySections(oreSourceOriginal);
	}

	private void setOreMaterialGroup(String material, String displayName, List<String> aliases) {
		JsonObject definition = objectEntry(section("ore_material_groups"), material);
		definition.addProperty("display_name", displayName);
		definition.add("ore_dictionary_entries", strings(aliases));
	}

	private void rebuildOreSourcePolicies() {
		JsonObject policies = section("ore_source_policies");
		Map<String, Set<String>> previousMembers = new LinkedHashMap<>();
		Map<String, JsonObject> candidates = new LinkedHashMap<>();
		for (Entry<String, JsonElement> entry : policies.entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject policy = entry.getValue().getAsJsonObject();
			Set<String> members = new LinkedHashSet<>();
			if (!policy.has("candidates") || !policy.get("candidates").isJsonArray()) continue;
			boolean hasCandidates = policy.getAsJsonArray("candidates").size() > 0;
			for (JsonElement element : policy.getAsJsonArray("candidates")) {
				if (!element.isJsonObject()) continue;
				JsonObject candidate = JsonCopies.copy(element.getAsJsonObject());
				String identity = candidateIdentity(candidate, string(policy, "domain", ""));
				members.add(identity);
				candidates.put(identity, candidate);
			}
			previousMembers.put(entry.getKey(), members);
			String material = string(policy, "material", "");
			if (!hasCandidates && section("ore_material_groups").has(material)) {
				policy.remove("dormant");
			} else {
				policy.addProperty("dormant", true);
			}
		}
		Map<String, List<JsonObject>> regrouped = new LinkedHashMap<>();
		for (JsonObject candidate : candidates.values()) {
			String material = string(candidate, "material", "");
			if (!bool(candidate, "material_declared", false)) {
				material = materialForAliases(stringList(candidate, "ore_dictionary"));
				if (!material.isEmpty()) candidate.addProperty("material", material);
			}
			String domain = string(candidate, "domain", "minecraft:overworld");
			if (!validResource(material) || !validResource(domain)) continue;
			regrouped.computeIfAbsent(material + '|' + domain, ignored -> new ArrayList<>()).add(candidate);
		}
		for (Entry<String, List<JsonObject>> entry : regrouped.entrySet()) {
			int split = entry.getKey().indexOf('|');
			String material = entry.getKey().substring(0, split);
			String domain = entry.getKey().substring(split + 1);
			JsonObject policy = policies.has(entry.getKey()) && policies.get(entry.getKey()).isJsonObject()
					? policies.getAsJsonObject(entry.getKey()) : new JsonObject();
			policy.remove("dormant");
			policy.addProperty("material", material);
			policy.addProperty("domain", domain);
			if (!policy.has("mode")) policy.addProperty("mode", "keep_separate");
			if (!policy.has("output_mode")) policy.addProperty("output_mode", "custom");
			JsonArray array = new JsonArray();
			Set<String> currentMembers = new LinkedHashSet<>();
			for (JsonObject candidate : entry.getValue()) {
				array.add(candidate);
				currentMembers.add(candidateIdentity(candidate, domain));
			}
			policy.add("candidates", array);
			reconcileOreSourceOutputs(policy);
			reconcileOreSourcePlacements(policy);
			Set<String> previous = previousMembers.get(entry.getKey());
			boolean membershipChanged = previous == null || !previous.equals(currentMembers);
			if (membershipChanged) {
				policy.addProperty("review_required", outputCandidateJson(policy).size() > 1);
			}
			refreshOreSourceStatus(policy);
			policies.add(entry.getKey(), policy);
		}
	}

	private static void reconcileOreSourceOutputs(JsonObject policy) {
		List<JsonObject> candidates = outputCandidateJson(policy);
		Set<String> candidateIds = allOutputCandidateIds(policy);
		JsonObject outputs = objectEntry(policy, "outputs");
		for (String sourceId : new ArrayList<>(JsonCopies.keys(outputs))) {
			if (!candidateIds.contains(sourceId)) outputs.remove(sourceId);
		}
		String mode = string(policy, "mode", "keep_separate");
		String outputMode = outputMode(policy);
		if ("keep_separate".equals(mode) || "balanced".equals(outputMode)) {
			for (JsonObject candidate : candidates) {
				String sourceId = string(candidate, "source_id", "");
				if (!sourceId.isEmpty() && bool(candidate, "loaded", false)) {
					outputs.addProperty(sourceId, 1.0D);
				}
			}
		} else if ("single".equals(outputMode)) {
			String selected = "";
			for (String sourceId : JsonCopies.keys(outputs)) {
				if (candidateIds.contains(sourceId)) {
					selected = sourceId;
					break;
				}
			}
			if (selected.isEmpty()) {
				selected = candidates.isEmpty() ? "" : string(candidates.get(0), "source_id", "");
			}
			outputs.entrySet().clear();
			if (!selected.isEmpty()) outputs.addProperty(selected, 1.0D);
		}
	}

	private static void reconcileOreSourcePlacements(JsonObject policy) {
		Map<String, Set<String>> retainedChoices = new LinkedHashMap<>();
		Map<String, List<JsonObject>> loadedChoices = new LinkedHashMap<>();
		if (policy.has("candidates") && policy.get("candidates").isJsonArray()) {
			for (JsonElement element : policy.getAsJsonArray("candidates")) {
				if (!element.isJsonObject()) continue;
				JsonObject candidate = element.getAsJsonObject();
				if (bool(candidate, "external", false) || bool(candidate, "enrichment", false)) continue;
				String channel = string(candidate, "placement_channel", "orespawn:standard");
				String sourceId = string(candidate, "source_id", "");
				if (!sourceId.isEmpty()) {
					retainedChoices.computeIfAbsent(channel, ignored -> new LinkedHashSet<>()).add(sourceId);
				}
				if (bool(candidate, "loaded", false) && bool(candidate, "placement_active", false)) {
					loadedChoices.computeIfAbsent(channel, ignored -> new ArrayList<>()).add(candidate);
				}
			}
		}
		JsonObject placements = objectEntry(policy, "placement_sources");
		for (String channel : new ArrayList<>(JsonCopies.keys(placements))) {
			String selected = string(placements, channel, "");
			if (!retainedChoices.getOrDefault(channel, Collections.emptySet()).contains(selected)) {
				placements.remove(channel);
			}
		}
		for (Entry<String, List<JsonObject>> entry : loadedChoices.entrySet()) {
			if (!placements.has(entry.getKey()) && !entry.getValue().isEmpty()) {
				placements.addProperty(entry.getKey(), string(entry.getValue().get(0), "source_id", ""));
			}
		}
	}

	private static Set<String> allOutputCandidateIds(JsonObject policy) {
		Set<String> result = new LinkedHashSet<>();
		if (policy.has("candidates") && policy.get("candidates").isJsonArray()) {
			for (JsonElement element : policy.getAsJsonArray("candidates")) {
				if (!element.isJsonObject()) continue;
				JsonObject candidate = element.getAsJsonObject();
				if (bool(candidate, "enrichment", false)) continue;
				String sourceId = string(candidate, "source_id", "");
				if (!sourceId.isEmpty()) result.add(sourceId);
			}
		}
		return result;
	}

	private static String candidateIdentity(JsonObject candidate, String fallbackDomain) {
		return string(candidate, "source_id", "") + '|'
				+ string(candidate, "placement_channel", "orespawn:standard") + '|'
				+ string(candidate, "domain", fallbackDomain);
	}

	private String materialForAliases(List<String> aliases) {
		Set<String> materials = new LinkedHashSet<>();
		for (String alias : aliases) {
			String owner = oreDictionaryOwner(alias);
			if (owner != null) materials.add(owner);
			else if (validOreDictionaryName(alias)) {
				materials.add("orespawn:" + alias.substring(3).toLowerCase(Locale.ROOT));
			}
		}
		return materials.size() == 1 ? materials.iterator().next()
				: materials.size() > 1 ? provisionalMaterialForAliases(aliases) : "";
	}

	private static String provisionalMaterialForAliases(List<String> aliases) {
		Set<String> unique = new LinkedHashSet<>();
		for (String alias : aliases) if (validOreDictionaryName(alias)) unique.add(alias);
		List<String> exact = new ArrayList<>(unique);
		Collections.sort(exact);
		String token = exact.isEmpty() ? "unknown"
				: exact.get(0).substring(3).toLowerCase(Locale.ROOT);
		long hash = 0xcbf29ce484222325L;
		for (String name : exact) {
			for (int index = 0; index < name.length(); index++) {
				hash ^= name.charAt(index);
				hash *= 0x100000001b3L;
			}
			hash ^= 0xffL;
			hash *= 0x100000001b3L;
		}
		String value = Long.toHexString(hash);
		StringBuilder padded = new StringBuilder(16);
		for (int index = value.length(); index < 16; index++) padded.append('0');
		return "orespawn:review/" + token + '-' + padded.append(value).toString();
	}

	private static boolean isCustomMaterial(String material) {
		return material != null && material.startsWith("orespawn:custom/") && validResource(material);
	}

	private static String inferredMaterialForAlias(String alias) {
		if ("oreSulfur".equals(alias) || "oreSulphur".equals(alias)) return "orespawn:sulfur";
		if ("oreAluminum".equals(alias) || "oreAluminium".equals(alias)) return "orespawn:aluminum";
		return "orespawn:" + alias.substring(3).toLowerCase(Locale.ROOT);
	}

	void setOreSourceMode(String key, boolean consolidated) {
		JsonObject policy = objectEntry(section("ore_source_policies"), key);
		policy.addProperty("mode", consolidated ? "consolidated" : "keep_separate");
		policy.addProperty("review_required", false);
		refreshOreSourceStatus(policy);
	}

	void acceptOreSourcePolicy(String key) {
		JsonObject policy = objectEntry(section("ore_source_policies"), key);
		policy.addProperty("review_required", false);
		refreshOreSourceStatus(policy);
	}

	void restoreOreSourceOriginalMode(String key) {
		JsonObject policy = objectEntry(section("ore_source_policies"), key);
		JsonObject originalPolicies = object(oreSourceOriginal, "ore_source_policies");
		JsonObject originalPolicy = originalPolicies.has(key) && originalPolicies.get(key).isJsonObject()
				? originalPolicies.getAsJsonObject(key) : null;
		policy.addProperty("mode", "keep_separate");
		if (originalPolicy != null) {
			policy.addProperty("output_mode", outputMode(originalPolicy));
			policy.add("outputs", sectionCopy(originalPolicy, "outputs"));
			policy.add("placement_sources", sectionCopy(originalPolicy, "placement_sources"));
		} else {
			JsonObject outputs = new JsonObject();
			JsonObject placements = new JsonObject();
			if (policy.has("candidates") && policy.get("candidates").isJsonArray()) {
				for (JsonElement element : policy.getAsJsonArray("candidates")) {
					if (!element.isJsonObject()) continue;
					JsonObject candidate = element.getAsJsonObject();
					if (!bool(candidate, "loaded", false)
							|| !bool(candidate, "placement_active", false)
							|| bool(candidate, "external", false)
							|| bool(candidate, "enrichment", false)) continue;
					String sourceId = string(candidate, "source_id", "");
					String channel = string(candidate, "placement_channel", "orespawn:standard");
					if (!sourceId.isEmpty()) outputs.addProperty(sourceId, 1.0D);
					if (!sourceId.isEmpty() && !placements.has(channel)) {
						placements.addProperty(channel, sourceId);
					}
				}
			}
			policy.addProperty("output_mode", "custom");
			policy.add("outputs", outputs);
			policy.add("placement_sources", placements);
		}
		policy.addProperty("review_required", false);
		refreshOreSourceStatus(policy);
	}

	void setOreSourceOutputMode(String key, String outputMode) {
		if (!"balanced".equals(outputMode) && !"single".equals(outputMode)
				&& !"custom".equals(outputMode)) return;
		JsonObject policy = objectEntry(section("ore_source_policies"), key);
		if (!hasManagedPlacementSource(policy)) return;
		policy.addProperty("mode", "consolidated");
		policy.addProperty("output_mode", outputMode);
		JsonObject outputs = objectEntry(policy, "outputs");
		if ("balanced".equals(outputMode)) {
			if (outputs.entrySet().isEmpty()) {
				for (JsonObject candidate : outputCandidateJson(policy)) {
					outputs.addProperty(string(candidate, "source_id", ""), 1.0D);
				}
			} else {
				for (String sourceId : new ArrayList<>(JsonCopies.keys(outputs))) {
					outputs.addProperty(sourceId, 1.0D);
				}
			}
		} else if ("single".equals(outputMode)) {
			String selected = preferredSingleOutput(policy, outputs);
			if (!loadedOutput(policy, selected)) {
				List<JsonObject> candidates = outputCandidateJson(policy);
				selected = candidates.isEmpty() ? "" : string(candidates.get(0), "source_id", "");
			}
			outputs.entrySet().clear();
			if (!selected.isEmpty()) outputs.addProperty(selected, 1.0D);
		}
		policy.addProperty("review_required", false);
		refreshOreSourceStatus(policy);
	}

	private static String preferredSingleOutput(JsonObject policy, JsonObject outputs) {
		JsonObject placements = objectEntry(policy, "placement_sources");
		for (Entry<String, JsonElement> entry : placements.entrySet()) {
			try {
				String sourceId = entry.getValue().getAsString();
				if (outputs.has(sourceId) && loadedOutput(policy, sourceId)) return sourceId;
			} catch (RuntimeException ignored) { }
		}
		return outputs.entrySet().isEmpty() ? "" : outputs.entrySet().iterator().next().getKey();
	}

	void setOreSourcePlacement(String key, String channel, String sourceId) {
		if (!validResource(channel)) return;
		JsonObject policy = objectEntry(section("ore_source_policies"), key);
		objectEntry(policy, "placement_sources").addProperty(channel, sourceId);
		refreshOreSourceStatus(policy);
	}

	void setOreSourceOutput(String key, String sourceId, boolean enabled, double weight) {
		JsonObject policy = objectEntry(section("ore_source_policies"), key);
		JsonObject outputs = objectEntry(policy, "outputs");
		String outputMode = outputMode(policy);
		if (!enabled) outputs.remove(sourceId);
		else if ("single".equals(outputMode)) {
			outputs.entrySet().clear();
			outputs.addProperty(sourceId, 1.0D);
		} else outputs.addProperty(sourceId, "balanced".equals(outputMode) ? 1.0D
				: Math.max(0.001D, Math.min(1000000.0D, weight)));
		refreshOreSourceStatus(policy);
	}

	private static List<JsonObject> outputCandidateJson(JsonObject policy) {
		Map<String, JsonObject> unique = new LinkedHashMap<>();
		if (policy.has("candidates") && policy.get("candidates").isJsonArray()) {
			for (JsonElement element : policy.getAsJsonArray("candidates")) {
				if (!element.isJsonObject()) continue;
				JsonObject candidate = element.getAsJsonObject();
				if (!bool(candidate, "loaded", false) || bool(candidate, "enrichment", false)) continue;
				String identity = string(candidate, "registry_id", "") + '|'
						+ integer(candidate, "metadata", 0);
				JsonObject previous = unique.get(identity);
				if (previous == null || (!bool(previous, "placement_active", false)
						&& bool(candidate, "placement_active", false))
						|| (bool(previous, "external", false) && !bool(candidate, "external", false))) {
					unique.put(identity, candidate);
				}
			}
		}
		return new ArrayList<>(unique.values());
	}

	private static boolean loadedOutput(JsonObject policy, String sourceId) {
		if (sourceId == null || sourceId.isEmpty()) return false;
		for (JsonObject candidate : outputCandidateJson(policy)) {
			if (sourceId.equals(string(candidate, "source_id", ""))) return true;
		}
		return false;
	}

	private static boolean hasManagedPlacementSource(JsonObject policy) {
		if (!policy.has("candidates") || !policy.get("candidates").isJsonArray()) return false;
		for (JsonElement element : policy.getAsJsonArray("candidates")) {
			if (!element.isJsonObject()) continue;
			JsonObject candidate = element.getAsJsonObject();
			if (bool(candidate, "loaded", false) && bool(candidate, "placement_active", false)
					&& !bool(candidate, "external", false)
					&& !bool(candidate, "enrichment", false)) return true;
		}
		return false;
	}

	private static void refreshOreSourceStatus(JsonObject policy) {
		Set<String> missing = new HashSet<>();
		Set<String> inactivePlacements = new HashSet<>();
		boolean external = false;
		boolean review = bool(policy, "review_required", false);
		if (policy.has("candidates") && policy.get("candidates").isJsonArray()) {
			for (JsonElement element : policy.getAsJsonArray("candidates")) {
				if (!element.isJsonObject()) continue;
				JsonObject candidate = element.getAsJsonObject();
				String sourceId = string(candidate, "source_id", "");
				if (!bool(candidate, "loaded", false)) missing.add(sourceId);
				if (!bool(candidate, "placement_active", false)) inactivePlacements.add(sourceId);
				external |= bool(candidate, "external", false) && bool(candidate, "loaded", false);
			}
		}
		Map<String, Double> outputs = decimalMap(policy, "outputs");
		Map<String, String> placements = stringMap(policy, "placement_sources");
		if ("consolidated".equals(string(policy, "mode", "keep_separate"))
				&& (outputs.isEmpty() || placements.isEmpty())) {
			policy.addProperty("status", "missing_source");
			return;
		}
		for (String id : outputs.keySet()) {
			if (missing.contains(id)) { policy.addProperty("status", "missing_source"); return; }
		}
		for (String id : placements.values()) {
			if (missing.contains(id) || inactivePlacements.contains(id)) {
				policy.addProperty("status", "missing_source"); return;
			}
		}
		policy.addProperty("status", external ? "external_generation" : review ? "review_required"
				: "consolidated".equals(string(policy, "mode", "keep_separate"))
						? "consolidated" : "separate");
	}

	private static Map<String, Double> decimalMap(JsonObject parent, String key) {
		if (!parent.has(key) || !parent.get(key).isJsonObject()) return Collections.emptyMap();
		Map<String, Double> result = new LinkedHashMap<>();
		for (Entry<String, JsonElement> entry : parent.getAsJsonObject(key).entrySet()) {
			try {
				double value = entry.getValue().getAsDouble();
				if (Double.isFinite(value) && value > 0.0D) result.put(entry.getKey(), value);
			} catch (RuntimeException ignored) { }
		}
		return Collections.unmodifiableMap(result);
	}

	private static Map<String, String> stringMap(JsonObject parent, String key) {
		if (!parent.has(key) || !parent.get(key).isJsonObject()) return Collections.emptyMap();
		Map<String, String> result = new LinkedHashMap<>();
		for (Entry<String, JsonElement> entry : parent.getAsJsonObject(key).entrySet()) {
			try { result.put(entry.getKey(), entry.getValue().getAsString()); }
			catch (RuntimeException ignored) { }
		}
		return Collections.unmodifiableMap(result);
	}

	static final class OreSourceGroup {
		final String key;
		final String material;
		final String domain;
		final String displayName;
		final List<String> oreDictionaryEntries;
		final boolean curated;
		final String mode;
		final String outputMode;
		final String status;
		final List<OreSourceCandidate> candidates;
		final Map<String, Double> outputs;
		final Map<String, String> placements;

		OreSourceGroup(String key, String material, String domain, String displayName,
				List<String> oreDictionaryEntries, boolean curated, String mode, String outputMode, String status,
				List<OreSourceCandidate> candidates, Map<String, Double> outputs,
				Map<String, String> placements) {
			this.key = key;
			this.material = material;
			this.domain = domain;
			this.displayName = displayName;
			this.oreDictionaryEntries = Collections.unmodifiableList(new ArrayList<>(oreDictionaryEntries));
			this.curated = curated;
			this.mode = mode;
			this.outputMode = outputMode;
			this.status = status;
			this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
			this.outputs = outputs;
			this.placements = placements;
		}

		List<String> channels() {
			Set<String> result = new TreeSet<>();
			for (OreSourceCandidate candidate : candidates) {
				if (!candidate.external && candidate.active) result.add(candidate.channel);
			}
			return Collections.unmodifiableList(new ArrayList<>(result));
		}

		List<OreSourceCandidate> outputCandidates() {
			Map<String, OreSourceCandidate> unique = new LinkedHashMap<>();
			for (OreSourceCandidate candidate : candidates) {
				if (candidate.enrichment) continue;
				String identity = candidate.registryId + '|' + candidate.metadata;
				OreSourceCandidate previous = unique.get(identity);
				if (previous == null || (!previous.active && candidate.active)
						|| (previous.external && !candidate.external)) unique.put(identity, candidate);
			}
			return Collections.unmodifiableList(new ArrayList<>(unique.values()));
		}

		boolean needsAttention() {
			return needsReview() || "missing_source".equals(status)
					|| "external_generation".equals(status) || outputCandidates().isEmpty();
		}

		boolean needsReview() {
			return "review_required".equals(status);
		}

		boolean hasManagedPlacementSource() {
			for (OreSourceCandidate candidate : candidates) {
				if (candidate.loaded && candidate.active && !candidate.external
						&& !candidate.enrichment) return true;
			}
			return false;
		}

		boolean isRoutineSingleSource() {
			return !needsAttention() && oreDictionaryEntries.size() == 1
					&& outputCandidates().size() == 1;
		}

		boolean isCustom() {
			return isCustomMaterial(material);
		}

		boolean canDeleteEmpty() {
			return isCustom() && oreDictionaryEntries.isEmpty();
		}

		boolean canDissolve() {
			return isCustom() && !oreDictionaryEntries.isEmpty();
		}

		boolean isRedundantSeparatePolicy() {
			if (!"keep_separate".equals(mode) || candidates.isEmpty()) return false;
			Set<String> outputs = new HashSet<>();
			Map<String, Set<String>> ownersByChannel = new HashMap<>();
			for (OreSourceCandidate candidate : candidates) {
				if (!candidate.loaded || candidate.external || candidate.enrichment
						|| candidate.reviewRequired) return false;
				outputs.add(candidate.registryId + '|' + candidate.metadata);
				ownersByChannel.computeIfAbsent(candidate.channel, ignored -> new HashSet<>())
						.add(candidate.owner);
			}
			if (outputs.size() > 1) return false;
			for (Set<String> owners : ownersByChannel.values()) if (owners.size() > 1) return false;
			return true;
		}
	}

	static final class OreSourceCandidate {
		final String sourceId;
		final String owner;
		final String ownerName;
		final String ownerVersion;
		final String registryId;
		final int metadata;
		final String channel;
		final List<String> oreDictionary;
		final boolean loaded;
		final boolean active;
		final boolean external;
		final boolean enrichment;
		final boolean reviewRequired;

		OreSourceCandidate(String sourceId, String owner, String ownerName, String ownerVersion,
				String registryId, int metadata, String channel, List<String> oreDictionary,
				boolean loaded, boolean active, boolean external, boolean enrichment, boolean reviewRequired) {
			this.sourceId = sourceId;
			this.owner = owner;
			this.ownerName = ownerName;
			this.ownerVersion = ownerVersion;
			this.registryId = registryId;
			this.metadata = metadata;
			this.channel = channel;
			this.oreDictionary = Collections.unmodifiableList(new ArrayList<>(oreDictionary));
			this.loaded = loaded;
			this.active = active;
			this.external = external;
			this.enrichment = enrichment;
			this.reviewRequired = reviewRequired;
		}
	}

	void resetEntry(String section, String id) {
		JsonObject originalSection = object(original, section);
		if (originalSection.has(id)) {
			section(section).add(id, JsonCopies.copy(originalSection.get(id)));
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

	List<String> installedBiomeIds() {
		List<String> result = new ArrayList<>();
		for (net.minecraft.world.biome.Biome biome : ForgeRegistries.BIOMES.getValues()) {
			ResourceLocation id = ForgeRegistries.BIOMES.getKey(biome);
			if (id != null) result.add(id.toString());
		}
		Collections.sort(result);
		return result;
	}

	BiomeDirectoryModel.Snapshot biomeDirectory() {
		return BiomeDirectoryModel.snapshot(root, biomeProviderDefaults, availableDimensionIds());
	}

	List<String> biomePaletteIds(String dimensionId) {
		List<String> result = new ArrayList<>();
		for (Entry<String, JsonElement> entry : section("biome_palettes").entrySet()) {
			if (!entry.getValue().isJsonObject()
					|| BiomeReplacementRules.isOverridePalette(entry.getKey())) continue;
			if (dimensionId.equals(string(entry.getValue().getAsJsonObject(), "dimension", ""))) {
				result.add(entry.getKey());
			}
		}
		return Collections.unmodifiableList(result);
	}

	JsonObject biomePaletteById(String paletteId) {
		JsonObject palettes = section("biome_palettes");
		return palettes.has(paletteId) && palettes.get(paletteId).isJsonObject()
				? palettes.getAsJsonObject(paletteId) : null;
	}

	List<String> biomePlacementIdsForPalette(String paletteId) {
		JsonObject palette = biomePaletteById(paletteId);
		if (palette == null) return Collections.emptyList();
		List<String> result = new ArrayList<>(JsonCopies.keys(object(palette, "biomes")));
		Collections.sort(result);
		return Collections.unmodifiableList(result);
	}

	JsonObject biomePlacementByPalette(String paletteId, String biomeId) {
		JsonObject palette = biomePaletteById(paletteId);
		if (palette == null) throw new IllegalArgumentException("Unknown biome palette " + paletteId);
		return objectEntry(object(palette, "biomes"), biomeId);
	}

	void removeBiomePlacementByPalette(String paletteId, String biomeId) {
		JsonObject palette = biomePaletteById(paletteId);
		if (palette != null) object(palette, "biomes").remove(biomeId);
	}

	Map<String, String> biomeReplacements(String dimensionId) {
		return BiomeReplacementRules.read(root, dimensionId);
	}

	void replaceBiome(String dimensionId, String sourceId, String targetId) {
		if (!loadedBiome(sourceId) || !loadedBiome(targetId)) {
			throw new IllegalArgumentException("Both replacement biomes must be loaded");
		}
		BiomeReplacementRules.set(root, dimensionId, sourceId, targetId);
	}

	void leaveBiomeOriginal(String dimensionId, String sourceId) {
		BiomeReplacementRules.remove(root, dimensionId, sourceId);
	}

	void resetBiome(String dimensionId, String biomeId) {
		leaveBiomeOriginal(dimensionId, biomeId);
		JsonObject defaults = biomeProviderDefaults.biomePalettesCopy();
		Set<String> active = biomeProviderDefaults.activeProviderIds();
		JsonObject palettes = section("biome_palettes");
		for (String id : new ArrayList<>(JsonCopies.keys(palettes))) {
			if (BiomeReplacementRules.isOverridePalette(id) || !palettes.get(id).isJsonObject()) continue;
			JsonObject palette = palettes.getAsJsonObject(id);
			if (!dimensionId.equals(string(palette, "dimension", ""))) continue;
			String owner = string(palette, "source_provider", "");
			if (!owner.isEmpty() && !active.contains(owner)) continue;
			JsonObject entries = object(palette, "biomes");
			JsonObject defaultPalette = defaults.has(id) && defaults.get(id).isJsonObject()
					? defaults.getAsJsonObject(id) : null;
			JsonObject defaultEntries = defaultPalette == null ? new JsonObject()
					: object(defaultPalette, "biomes");
			if (defaultEntries.has(biomeId)) {
				entries.add(biomeId, JsonCopies.copy(defaultEntries.get(biomeId)));
			} else {
				entries.remove(biomeId);
			}
		}
		for (Entry<String, JsonElement> entry : defaults.entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject definition = entry.getValue().getAsJsonObject();
			if (!dimensionId.equals(string(definition, "dimension", ""))
					|| !object(definition, "biomes").has(biomeId)) continue;
			if (!palettes.has(entry.getKey())) {
				palettes.add(entry.getKey(), JsonCopies.copy(definition));
			} else if (palettes.get(entry.getKey()).isJsonObject()) {
				object(palettes.getAsJsonObject(entry.getKey()), "biomes").add(biomeId,
						JsonCopies.copy(object(definition, "biomes").get(biomeId)));
			}
		}
	}

	void resetBiomePalette(String paletteId) {
		JsonObject palettes = section("biome_palettes");
		if (!palettes.has(paletteId) || !palettes.get(paletteId).isJsonObject()) return;
		JsonObject current = palettes.getAsJsonObject(paletteId);
		String owner = string(current, "source_provider", "");
		if (!owner.isEmpty() && !biomeProviderDefaults.activeProviderIds().contains(owner)) return;
		JsonObject defaults = biomeProviderDefaults.biomePalettesCopy();
		if (defaults.has(paletteId)) palettes.add(paletteId, JsonCopies.copy(defaults.get(paletteId)));
		else palettes.remove(paletteId);
	}

	void resetBiomeDimension(String dimensionId) {
		resetBiomeSections(dimensionId);
		BiomeReplacementRules.clearDimension(root, dimensionId);
	}

	void resetAllBiomeManagement() {
		resetBiomeSections(null);
		for (String dimension : new ArrayList<>(availableDimensionIds())) {
			BiomeReplacementRules.clearDimension(root, dimension);
		}
	}

	private void resetBiomeSections(String dimensionId) {
		resetProviderSection("biome_palettes", biomeProviderDefaults.biomePalettesCopy(), dimensionId);
		resetProviderSection("dimension_materials", biomeProviderDefaults.dimensionMaterialsCopy(), dimensionId);
	}

	private void resetProviderSection(String sectionName, JsonObject defaults, String dimensionId) {
		JsonObject current = section(sectionName);
		Set<String> active = biomeProviderDefaults.activeProviderIds();
		for (String id : new ArrayList<>(JsonCopies.keys(current))) {
			if (!current.get(id).isJsonObject()) continue;
			JsonObject definition = current.getAsJsonObject(id);
			if (dimensionId != null && !dimensionId.equals(string(definition, "dimension", ""))) continue;
			String owner = string(definition, "source_provider", "");
			if (!owner.isEmpty() && !active.contains(owner)) continue;
			if (defaults.has(id)) current.add(id, JsonCopies.copy(defaults.get(id)));
			else current.remove(id);
		}
		for (Entry<String, JsonElement> entry : defaults.entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject definition = entry.getValue().getAsJsonObject();
			if (dimensionId == null || dimensionId.equals(string(definition, "dimension", ""))) {
				current.add(entry.getKey(), JsonCopies.copy(definition));
			}
		}
	}

	boolean biomeTargetProviderDeclared(String dimensionId, String biomeId) {
		for (BiomeDirectoryModel.BiomeEntry entry : biomeDirectory().entries(dimensionId, true)) {
			if (biomeId.equals(entry.id)) return entry.providerDeclared;
		}
		return false;
	}

	private static boolean loadedBiome(String id) {
		try { return ForgeRegistries.BIOMES.getValue(new ResourceLocation(id)) != null; }
		catch (RuntimeException ignored) { return false; }
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
		List<String> result = new ArrayList<>(JsonCopies.keys(object(palette, "biomes")));
		Collections.sort(result);
		return result;
	}

	JsonObject biomePlacement(String dimensionId, String biomeId) {
		return objectEntry(object(biomePalette(dimensionId, true), "biomes"), biomeId);
	}

	void addBiomePlacement(String dimensionId, String biomeId) {
		if (!validResource(biomeId)
				|| ForgeRegistries.BIOMES.getValue(new ResourceLocation(biomeId)) == null) return;
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
		if (biomes.entrySet().size() == 0) palette.addProperty("enabled", false);
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
		Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
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
		for (Block block : ForgeRegistries.BLOCKS.getValues()) {
			ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
			if (id == null || block == Blocks.AIR
					|| (!fluidOnly && Item.getItemFromBlock(block) == null)
					|| (fluidOnly && !isFluidBlock(block))
					|| (!fluidOnly && block.hasTileEntity(block.getDefaultState()))
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
		for (Block block : ForgeRegistries.BLOCKS.getValues()) {
			ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
			if (id != null && block != Blocks.AIR && isFluidBlock(block)
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
		ResourceLocation fluid = new ResourceLocation(canonicalId);
		String baseId = "orespawn:fluid_deposit/" + fluid.getResourceDomain() + "/" + fluid.getResourcePath();
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
		rule.addProperty("min_y", 0);
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
			for (RockFamily family : RockFamily.values()) families.add(new JsonPrimitive(family.configName));
		}
		rule.add("host_families", families);
		rule.add("host_blocks", new JsonArray());
		JsonArray tags = new JsonArray();
		tags.add(new JsonPrimitive("forge:stone"));
		rule.add("host_tags", tags);
		rule.add("biome_ids", new JsonArray());
		rule.add("excluded_biome_ids", new JsonArray());
		rule.add("biome_dictionary", new JsonArray());
		rule.add("excluded_biome_dictionary", new JsonArray());
		rule.add("geomes", new JsonObject());
		return rule;
	}

	List<String> geomeIds() {
		List<String> ids = new ArrayList<>(JsonCopies.keys(section("geomes")));
		Collections.sort(ids);
		return ids;
	}

	List<String> configuredBiomeIds() {
		TreeSet<String> ids = new TreeSet<>(JsonCopies.keys(section("biomes")));
		for (net.minecraft.world.biome.Biome biome : ForgeRegistries.BIOMES.getValues()) {
			ResourceLocation id = ForgeRegistries.BIOMES.getKey(biome);
			if (id != null) {
				ids.add(id.toString());
			}
		}
		return new ArrayList<>(ids);
	}

	List<String> dictionaryIds() {
		List<String> ids = new ArrayList<>(JsonCopies.keys(section("biome_dictionary")));
		Collections.sort(ids);
		return ids;
	}

	JsonObject weightMap(String section, String id) {
		JsonObject parent = section(section);
		return objectEntry(parent, id);
	}

	void addGeome(String id) {
		String normalized = id.trim();
		if (!validGeomeId(normalized) || section("geomes").has(normalized)) {
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
			if (!validGeomeId(entry.getKey()) || !entry.getValue().isJsonObject()) {
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
			int rockMinY = integer(rock, "min_y", 0);
			int rockMaxY = integer(rock, "max_y", 255);
			if (rockMinY < 0 || rockMaxY > 255 || rockMinY > rockMaxY) {
				errors.add("Rock Y range must stay within 0..255 for " + entry.getKey());
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
			if (dimensions.entrySet().size() == 0 && selectors.entrySet().size() == 0) {
				errors.add("Ore has no dimension rules: " + entry.getKey());
				continue;
			}
			JsonObject allRules = JsonCopies.copy(dimensions);
			for (Entry<String, JsonElement> selector : selectors.entrySet()) {
				if (!OreDimensionSelector.ALL_EXCEPT_NETHER_AND_END.id().toString().equals(selector.getKey())) {
					errors.add("Invalid dimension selector for " + entry.getKey());
					continue;
				}
				allRules.add(selector.getKey(), JsonCopies.copy(selector.getValue()));
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
				int oreMinY = integer(rule, "min_y", 0);
				int oreMaxY = integer(rule, "max_y", 255);
				if (oreMinY < 0 || oreMaxY > 255 || oreMinY > oreMaxY
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
		validateOreMaterialGroups(errors);
		validateOreSourcePolicies(errors);
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
					|| deposit.getAsJsonObject("dimensions").entrySet().size() == 0) {
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
				int fluidMinY = integer(rule, "min_y", 0);
				int fluidMaxY = integer(rule, "max_y", 255);
				if (fluidMinY < 0 || fluidMaxY > 255 || fluidMinY > fluidMaxY
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
			boolean replacementPalette = BiomeReplacementRules.isOverridePalette(entry.getKey());
			if (!validResource(string(palette, "dimension", ""))) {
				errors.add("Invalid biome palette dimension: " + entry.getKey());
			}
			if (!bool(palette, "enabled", true)) continue;
			JsonObject biomes = palette.has("biomes") && palette.get("biomes").isJsonObject()
					? palette.getAsJsonObject("biomes") : new JsonObject();
			if (biomes.entrySet().size() == 0) errors.add("Enabled biome palette has no biomes: " + entry.getKey());
			for (Entry<String, JsonElement> biome : biomes.entrySet()) {
				if (!validResource(biome.getKey())
						|| (!replacementPalette
								&& ForgeRegistries.BIOMES.getValue(new ResourceLocation(biome.getKey())) == null)
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

	private void validateOreMaterialGroups(List<String> errors) {
		Set<String> aliases = new HashSet<>();
		for (Entry<String, JsonElement> entry : section("ore_material_groups").entrySet()) {
			if (!validResource(entry.getKey()) || !entry.getValue().isJsonObject()) {
				errors.add("Invalid ore material group: " + entry.getKey());
				continue;
			}
			JsonObject group = entry.getValue().getAsJsonObject();
			if (string(group, "display_name", "").trim().isEmpty()) {
				errors.add("Ore material group has no display name: " + entry.getKey());
			}
			for (String alias : stringList(group, "ore_dictionary_entries")) {
				if (!validOreDictionaryName(alias)) {
					errors.add("Invalid Ore Dictionary entry in " + entry.getKey() + ": " + alias);
				} else if (!aliases.add(alias)) {
					errors.add("Ore Dictionary entry belongs to more than one material group: " + alias);
				}
			}
		}
	}

	private void validateOreSourcePolicies(List<String> errors) {
		for (Entry<String, JsonElement> entry : section("ore_source_policies").entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				errors.add("Invalid ore source policy: " + entry.getKey());
				continue;
			}
			JsonObject policy = entry.getValue().getAsJsonObject();
			if (bool(policy, "dormant", false)
					|| !"consolidated".equals(string(policy, "mode", "keep_separate"))) continue;
			JsonObject outputs = policy.has("outputs") && policy.get("outputs").isJsonObject()
					? policy.getAsJsonObject("outputs") : new JsonObject();
			JsonObject placements = policy.has("placement_sources")
					&& policy.get("placement_sources").isJsonObject()
					? policy.getAsJsonObject("placement_sources") : new JsonObject();
			String outputMode = outputMode(policy);
			if (outputs.entrySet().isEmpty()) {
				errors.add("Consolidated ore source policy has no selected outputs: " + entry.getKey());
			}
			if (placements.entrySet().isEmpty()) {
				errors.add("Consolidated ore source policy has no placement source: " + entry.getKey());
			}
			if ("single".equals(outputMode) && outputs.entrySet().size() != 1) {
				errors.add("Single-output ore source policy must select exactly one output: " + entry.getKey());
			}
			for (Entry<String, JsonElement> output : outputs.entrySet()) {
				try {
					double value = output.getValue().getAsDouble();
					if (!Double.isFinite(value) || value <= 0.0D) throw new NumberFormatException();
				} catch (RuntimeException invalid) {
					errors.add("Ore source output weight must be positive: " + entry.getKey());
				}
			}
		}
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
		ResourceLocation id = value.indexOf(':') >= 0 ? new ResourceLocation(value)
				: new ResourceLocation("orespawn", value);
		OrePattern.fromConfigName(id.getResourcePath());
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
		dimension.addProperty("min_y", 0);
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
			families.add(new JsonPrimitive(family.configName));
		}
		dimension.add("host_families", families);
		JsonArray tags = new JsonArray();
		tags.add(new JsonPrimitive("forge:stone"));
		dimension.add("host_tags", tags);
		return dimension;
	}

	private static boolean isSelectable(Block block, boolean showAll) {
		if (block == Blocks.AIR || Item.getItemFromBlock(block) == null || isFluidBlock(block)) {
			return false;
		}
		if (showAll) {
			return true;
		}
		return !block.hasTileEntity(block.getDefaultState())
				&& block.getDefaultState().getMaterial().blocksMovement()
				&& block.getDefaultState().isFullCube();
	}

	String canonicalBlockId(String id) {
		try {
			Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
			ResourceLocation canonical = block == null ? null : ForgeRegistries.BLOCKS.getKey(block);
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
			normalized.putIfAbsent(id, JsonCopies.copy(entry.getValue()));
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
			return !id.equals(new ResourceLocation(aliases.get(id).getAsString()).toString());
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
		Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
		return block != null && block != Blocks.AIR;
	}

	private static String safePath(String registryId) {
		return registryId.toLowerCase(Locale.ROOT).replace(':', '/')
				.replaceAll("[^a-z0-9_./-]", "_");
	}

	private static boolean validFluidBlock(String id) {
		if (!validResource(id)) return false;
		Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
		return block != null && block != Blocks.AIR && isFluidBlock(block);
	}

	static boolean isFluidBlock(Block block) {
		return block instanceof BlockLiquid || block instanceof IFluidBlock
				|| FluidRegistry.lookupFluidForBlock(block) != null;
	}

	private static boolean validResource(String id) {
		if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) return false;
		try {
			new ResourceLocation(id);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private static boolean validOreDictionaryName(String name) {
		return name != null && name.length() > 3 && name.length() <= 128
				&& name.startsWith("ore") && Character.isUpperCase(name.charAt(3))
				&& name.matches("[A-Za-z0-9_]+");
	}

	private static JsonObject sectionCopy(JsonObject parent, String key) {
		return parent.has(key) && parent.get(key).isJsonObject()
				? JsonCopies.copy(parent.getAsJsonObject(key)) : new JsonObject();
	}

	private static List<String> stringList(JsonObject parent, String key) {
		List<String> result = new ArrayList<>();
		if (parent.has(key) && parent.get(key).isJsonArray()) {
			for (JsonElement element : parent.getAsJsonArray(key)) {
				try {
					String value = element.getAsString();
					if (!result.contains(value)) result.add(value);
				} catch (RuntimeException ignored) { }
			}
		}
		return result;
	}

	private static JsonArray strings(Iterable<String> values) {
		JsonArray result = new JsonArray();
		for (String value : values) result.add(new JsonPrimitive(value));
		return result;
	}

	private static String displayName(String id) {
		int split = id.indexOf(':');
		String value = (split < 0 ? id : id.substring(split + 1)).replace('_', ' ').replace('/', ' ');
		return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private static String outputMode(JsonObject policy) {
		String configured = string(policy, "output_mode", "");
		if ("balanced".equals(configured) || "single".equals(configured)
				|| "custom".equals(configured)) return configured;
		Map<String, Double> outputs = decimalMap(policy, "outputs");
		if (outputs.size() <= 1) return "single";
		double first = outputs.values().iterator().next();
		for (double value : outputs.values()) if (Double.compare(first, value) != 0) return "custom";
		return "balanced";
	}

	private static boolean validGeomeId(String id) {
		if (id == null || id.isEmpty()) return false;
		if (id.indexOf(':') < 0) return id.matches("[a-z0-9_.-]+");
		if (!validResource(id)) return false;
		return id.equals(new ResourceLocation(id).toString());
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
