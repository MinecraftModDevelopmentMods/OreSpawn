package zone.moddev.mc.orespawn.api;

import zone.moddev.mc.orespawn.util.JsonCopies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.util.ResourceLocation;

/**
 * Immutable declarative world-generation contribution submitted to OreSpawn.
 * All registry objects are represented by IDs and resolved only when OreSpawn
 * freezes and bakes the active provider set.
 */
public final class WorldgenProvider {
	private final String modId;
	private final int revision;
	private final JsonObject definition;

	private WorldgenProvider(String modId, int revision, JsonObject definition) {
		this.modId = modId;
		this.revision = revision;
		this.definition = JsonCopies.copy(definition);
	}

	public static Builder builder(String modId, int revision) {
		return new Builder(modId, revision);
	}

	public String modId() {
		return modId;
	}

	public int revision() {
		return revision;
	}

	/** Returns a defensive JSON representation matching provider schema 4. */
	public JsonObject toJson() {
		return JsonCopies.copy(definition);
	}

	public static final class Builder {
		private final String modId;
		private final int revision;
		private final LinkedHashMap<ResourceLocation, RockDefinition> rocks = new LinkedHashMap<>();
		private final LinkedHashMap<ResourceLocation, OreDefinition> ores = new LinkedHashMap<>();
		private final LinkedHashMap<ResourceLocation, FluidDepositDefinition> fluidDeposits =
				new LinkedHashMap<>();
		private final LinkedHashMap<ResourceLocation, GeomeDefinition> geomes = new LinkedHashMap<>();
		private final LinkedHashMap<ResourceLocation, BiomeRule> biomeRules = new LinkedHashMap<>();
		private final LinkedHashMap<ResourceLocation, TerrainDimensionDefinition> terrainDimensions =
				new LinkedHashMap<>();
		private final LinkedHashMap<ResourceLocation, BiomePaletteDefinition> biomePalettes =
				new LinkedHashMap<>();
		private final LinkedHashMap<ResourceLocation, DimensionMaterialsDefinition> dimensionMaterials =
				new LinkedHashMap<>();
		private final LinkedHashMap<ResourceLocation, GeologyTemplate> templates = new LinkedHashMap<>();

		private Builder(String modId, int revision) {
			this.modId = requireModId(modId);
			if (revision < 1) {
				throw new IllegalArgumentException("Provider revision must be at least 1");
			}
			this.revision = revision;
		}

		public Builder rock(RockDefinition rock) {
			putUnique(rocks, rock.id(), rock, "rock");
			return this;
		}

		public Builder rock(ResourceLocation block, GeologyFamily family, Consumer<RockDefinition.Builder> edit) {
			RockDefinition.Builder builder = RockDefinition.builder(ownedId("rock", block), block, family);
			edit.accept(builder);
			return rock(builder.build());
		}

		public Builder rock(ResourceLocation id, ResourceLocation block, GeologyFamily family,
				Consumer<RockDefinition.Builder> edit) {
			RockDefinition.Builder builder = RockDefinition.builder(id, block, family);
			edit.accept(builder);
			return rock(builder.build());
		}

		public Builder ore(OreDefinition ore) {
			putUnique(ores, ore.id(), ore, "ore");
			return this;
		}

		public Builder ore(ResourceLocation block, Consumer<OreDefinition.Builder> edit) {
			OreDefinition.Builder builder = OreDefinition.builder(ownedId("ore", block), block);
			edit.accept(builder);
			return ore(builder.build());
		}

		public Builder ore(ResourceLocation id, ResourceLocation block,
				Consumer<OreDefinition.Builder> edit) {
			OreDefinition.Builder builder = OreDefinition.builder(id, block);
			edit.accept(builder);
			return ore(builder.build());
		}

		public Builder fluidDeposit(FluidDepositDefinition deposit) {
			putUnique(fluidDeposits, deposit.id(), deposit, "fluid deposit");
			return this;
		}

		public Builder fluidDeposit(ResourceLocation id, ResourceLocation block,
				Consumer<FluidDepositDefinition.Builder> edit) {
			FluidDepositDefinition.Builder builder = FluidDepositDefinition.builder(id, block);
			edit.accept(builder);
			return fluidDeposit(builder.build());
		}

		public Builder geome(GeomeDefinition geome) {
			putUnique(geomes, geome.id(), geome, "geome");
			return this;
		}

		public Builder geome(ResourceLocation id, Consumer<GeomeDefinition.Builder> edit) {
			GeomeDefinition.Builder builder = GeomeDefinition.builder(id);
			edit.accept(builder);
			return geome(builder.build());
		}

		public Builder biome(BiomeRule biome) {
			putUnique(biomeRules, biome.biome(), biome, "biome rule");
			return this;
		}

		public Builder biome(ResourceLocation biome, Map<ResourceLocation, Double> geomeWeights) {
			return biome(new BiomeRule(biome, geomeWeights));
		}

		public Builder terrainDimension(TerrainDimensionDefinition dimension) {
			putUnique(terrainDimensions, dimension.dimension(), dimension, "terrain dimension");
			return this;
		}

		public Builder terrainDimension(ResourceLocation dimension,
				Consumer<TerrainDimensionDefinition.Builder> edit) {
			TerrainDimensionDefinition.Builder builder = TerrainDimensionDefinition.builder(dimension);
			edit.accept(builder);
			return terrainDimension(builder.build());
		}

		public Builder biomePalette(BiomePaletteDefinition palette) {
			putUnique(biomePalettes, palette.id(), palette, "biome palette");
			return this;
		}

		public Builder biomePalette(ResourceLocation id, ResourceLocation dimension,
				Consumer<BiomePaletteDefinition.Builder> edit) {
			BiomePaletteDefinition.Builder builder = BiomePaletteDefinition.builder(id, dimension);
			edit.accept(builder);
			return biomePalette(builder.build());
		}

		public Builder dimensionMaterials(DimensionMaterialsDefinition materials) {
			putUnique(dimensionMaterials, materials.id(), materials, "dimension materials");
			return this;
		}

		public Builder dimensionMaterials(ResourceLocation id, ResourceLocation dimension,
				Consumer<DimensionMaterialsDefinition.Builder> edit) {
			DimensionMaterialsDefinition.Builder builder =
					DimensionMaterialsDefinition.builder(id, dimension);
			edit.accept(builder);
			return dimensionMaterials(builder.build());
		}

		public Builder template(GeologyTemplate template) {
			putUnique(templates, template.id(), template, "template");
			return this;
		}

		public Builder template(ResourceLocation id, Consumer<GeologyTemplate.Builder> edit) {
			GeologyTemplate.Builder builder = GeologyTemplate.builder(id);
			edit.accept(builder);
			return template(builder.build());
		}

		public WorldgenProvider build() {
			if (rocks.isEmpty() && ores.isEmpty() && fluidDeposits.isEmpty()
					&& geomes.isEmpty() && biomeRules.isEmpty()
					&& terrainDimensions.isEmpty() && biomePalettes.isEmpty()
					&& dimensionMaterials.isEmpty() && templates.isEmpty()) {
				throw new IllegalStateException("A provider must declare at least one contribution");
			}
			requireOwned(rocks.keySet(), "rock");
			requireOwned(ores.keySet(), "ore");
			requireOwned(fluidDeposits.keySet(), "fluid deposit");
			requireOwned(geomes.keySet(), "geome");
			// Terrain-dimension keys identify Minecraft dimensions. Unlike provider-owned
			// rocks, ores, palettes and templates, they are intentionally not namespaced
			// to the provider (for example minecraft:overworld or minecraft:the_end).
			requireOwned(biomePalettes.keySet(), "biome palette");
			requireOwned(dimensionMaterials.keySet(), "dimension materials");
			requireOwned(templates.keySet(), "template");
			JsonObject root = new JsonObject();
			root.addProperty("schema_version", 4);
			root.addProperty("provider_modid", modId);
			root.addProperty("provider_revision", revision);
			root.add("rocks", object(rocks));
			root.add("ores", object(ores));
			root.add("fluid_deposits", object(fluidDeposits));
			root.add("geomes", object(geomes));
			root.add("biome_rules", object(biomeRules));
			root.add("terrain_dimensions", object(terrainDimensions));
			root.add("biome_palettes", object(biomePalettes));
			root.add("dimension_materials", object(dimensionMaterials));
			root.add("templates", object(templates));
			return new WorldgenProvider(modId, revision, root);
		}

		private void requireOwned(Collection<ResourceLocation> ids, String type) {
			for (ResourceLocation id : ids) {
				if (!modId.equals(id.getResourceDomain())) {
					throw new IllegalStateException("Provider " + modId + " does not own " + type + " " + id);
				}
			}
		}

		private ResourceLocation ownedId(String kind, ResourceLocation output) {
			return new ResourceLocation(modId, kind + "/" + output.getResourceDomain() + "/" + output.getResourcePath());
		}
	}

	public static final class RockDefinition implements JsonDefinition {
		private final ResourceLocation id;
		private final ResourceLocation block;
		private final boolean enabled;
		private final GeologyFamily family;
		private final int depthPeak;
		private final int depthSpread;
		private final int minY;
		private final int maxY;
		private final double weight;
		private final boolean oreReplaceable;
		private final Map<ResourceLocation, Double> geomes;
		private final Set<ResourceLocation> dimensions;

		private RockDefinition(Builder builder) {
			id = builder.id;
			block = builder.block;
			enabled = builder.enabled;
			family = builder.family;
			depthPeak = builder.depthPeak;
			depthSpread = builder.depthSpread;
			minY = builder.minY;
			maxY = builder.maxY;
			weight = builder.weight;
			oreReplaceable = builder.oreReplaceable;
			geomes = immutableMap(builder.geomes);
			dimensions = immutableSet(builder.dimensions);
		}

		public static Builder builder(ResourceLocation block, GeologyFamily family) {
			return new Builder(block, block, family);
		}

		public static Builder builder(ResourceLocation id, ResourceLocation block, GeologyFamily family) {
			return new Builder(id, block, family);
		}

		public ResourceLocation id() { return id; }
		public ResourceLocation block() { return block; }
		public boolean enabled() { return enabled; }
		public GeologyFamily family() { return family; }
		public int depthPeak() { return depthPeak; }
		public int depthSpread() { return depthSpread; }
		public int minY() { return minY; }
		public int maxY() { return maxY; }
		public double weight() { return weight; }
		public boolean oreReplaceable() { return oreReplaceable; }
		public Map<ResourceLocation, Double> geomes() { return geomes; }
		public Set<ResourceLocation> dimensions() { return dimensions; }

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("block", block.toString());
			json.addProperty("enabled", enabled);
			json.addProperty("family", family.configName());
			json.addProperty("depth_peak", depthPeak);
			json.addProperty("depth_spread", depthSpread);
			json.addProperty("min_y", minY);
			json.addProperty("max_y", maxY);
			json.addProperty("weight", weight);
			json.addProperty("ore_replaceable", oreReplaceable);
			json.add("geomes", weights(geomes));
			json.add("dimensions", ids(dimensions));
			return json;
		}

		public static final class Builder {
			private final ResourceLocation id;
			private final ResourceLocation block;
			private final GeologyFamily family;
			private boolean enabled = true;
			private int depthPeak = 48;
			private int depthSpread = 40;
			private int minY = 0;
			private int maxY = 255;
			private double weight = 1.0D;
			private boolean oreReplaceable = true;
			private final Map<ResourceLocation, Double> geomes = new LinkedHashMap<>();
			private final Set<ResourceLocation> dimensions = new LinkedHashSet<>();

			private Builder(ResourceLocation id, ResourceLocation block, GeologyFamily family) {
				this.id = Objects.requireNonNull(id, "id");
				this.block = Objects.requireNonNull(block, "block");
				this.family = Objects.requireNonNull(family, "family");
				dimensions.add(new ResourceLocation("minecraft", "overworld"));
			}

			public Builder enabled(boolean value) { enabled = value; return this; }
			public Builder depth(int peak, int spread) { depthPeak = peak; depthSpread = spread; return this; }
			public Builder yRange(int min, int max) { minY = min; maxY = max; return this; }
			public Builder weight(double value) { weight = value; return this; }
			public Builder oreReplaceable(boolean value) { oreReplaceable = value; return this; }
			public Builder geomeWeight(ResourceLocation geome, double value) { geomes.put(geome, value); return this; }
			public Builder dimensions(Collection<ResourceLocation> values) { dimensions.clear(); dimensions.addAll(values); return this; }
			public Builder dimension(ResourceLocation value) { dimensions.add(value); return this; }

			public RockDefinition build() {
				requireRange(minY, maxY, "rock Y range");
				if (depthSpread < 1 || weight < 0.0D || !Double.isFinite(weight) || dimensions.isEmpty()) {
					throw new IllegalStateException("Invalid rock depth, weight, or dimensions for " + block);
				}
				return new RockDefinition(this);
			}
		}
	}

	public static final class OreDefinition implements JsonDefinition {
		private final ResourceLocation id;
		private final ResourceLocation block;
		private final boolean enabled;
		private final boolean nativeGeneration;
		private final ResourceLocation deepOutput;
		private final int deepOutputMaxY;
		private final List<OreOutputDefinition> outputs;
		private final boolean suppressVanilla;
		private final boolean retrogen;
		private final Map<ResourceLocation, OreDimensionDefinition> dimensions;
		private final Map<OreDimensionSelector, OreDimensionDefinition> dimensionSelectors;

		private OreDefinition(Builder builder) {
			id = builder.id;
			block = builder.block;
			enabled = builder.enabled;
			nativeGeneration = builder.nativeGeneration;
			deepOutput = builder.deepOutput;
			deepOutputMaxY = builder.deepOutputMaxY;
			outputs = Collections.unmodifiableList(new ArrayList<>(builder.outputs));
			suppressVanilla = builder.suppressVanilla;
			retrogen = builder.retrogen;
			dimensions = Collections.unmodifiableMap(new LinkedHashMap<>(builder.dimensions));
			dimensionSelectors = Collections.unmodifiableMap(new LinkedHashMap<>(builder.dimensionSelectors));
		}

		public static Builder builder(ResourceLocation block) { return new Builder(block, block); }
		public static Builder builder(ResourceLocation id, ResourceLocation block) { return new Builder(id, block); }
		public ResourceLocation id() { return id; }
		public ResourceLocation block() { return block; }
		public boolean enabled() { return enabled; }
		public List<OreOutputDefinition> outputs() { return outputs; }
		public boolean suppressVanilla() { return suppressVanilla; }
		public boolean retrogen() { return retrogen; }
		public Map<ResourceLocation, OreDimensionDefinition> dimensions() { return dimensions; }
		public Map<OreDimensionSelector, OreDimensionDefinition> dimensionSelectors() { return dimensionSelectors; }

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("block", block.toString());
			json.addProperty("enabled", enabled);
			json.addProperty("native_generation", nativeGeneration);
			json.addProperty("suppress_vanilla", suppressVanilla);
			json.addProperty("retrogen", retrogen);
			if (!outputs.isEmpty()) {
				JsonArray values = new JsonArray();
				for (OreOutputDefinition output : outputs) values.add(output.toJson());
				json.add("outputs", values);
			}
			if (deepOutput != null) {
				json.addProperty("deep_output", deepOutput.toString());
				json.addProperty("deep_output_max_y", deepOutputMaxY);
			}
			if (!dimensions.isEmpty()) json.add("dimensions", object(dimensions));
			JsonObject selectors = new JsonObject();
			for (Entry<OreDimensionSelector, OreDimensionDefinition> entry : dimensionSelectors.entrySet()) {
				selectors.add(entry.getKey().id().toString(), entry.getValue().toJson());
			}
			if (selectors.entrySet().size() > 0) json.add("dimension_selectors", selectors);
			return json;
		}

		public static final class Builder {
			private final ResourceLocation id;
			private final ResourceLocation block;
			private boolean enabled = true;
			private boolean nativeGeneration;
			private ResourceLocation deepOutput;
			private int deepOutputMaxY = -1;
			private final List<OreOutputDefinition> outputs = new ArrayList<>();
			private boolean suppressVanilla;
			private boolean retrogen = true;
			private final LinkedHashMap<ResourceLocation, OreDimensionDefinition> dimensions = new LinkedHashMap<>();
			private final LinkedHashMap<OreDimensionSelector, OreDimensionDefinition> dimensionSelectors =
					new LinkedHashMap<>();

			private Builder(ResourceLocation id, ResourceLocation block) {
				this.id = Objects.requireNonNull(id, "id");
				this.block = Objects.requireNonNull(block, "block");
			}
			public Builder enabled(boolean value) { enabled = value; return this; }
			public Builder nativeGeneration(boolean value) { nativeGeneration = value; return this; }
			public Builder suppressVanilla(boolean value) { suppressVanilla = value; return this; }
			public Builder retrogen(boolean value) { retrogen = value; return this; }
			public Builder deepOutput(ResourceLocation value, int maxY) { deepOutput = value; deepOutputMaxY = maxY; return this; }
			public Builder output(ResourceLocation value, double weight) {
				return output(value, weight, -2048, 2048);
			}
			public Builder output(ResourceLocation value, double weight, int minY, int maxY) {
				outputs.add(new OreOutputDefinition(value, weight, minY, maxY));
				return this;
			}
			public Builder dimension(OreDimensionDefinition value) {
				putUnique(dimensions, value.dimension(), value, "ore dimension");
				return this;
			}
			public Builder dimension(ResourceLocation id, Consumer<OreDimensionDefinition.Builder> edit) {
				OreDimensionDefinition.Builder builder = OreDimensionDefinition.builder(id);
				edit.accept(builder);
				return dimension(builder.build());
			}
			public Builder dimensionSelector(OreDimensionSelector selector, OreDimensionDefinition value) {
				Objects.requireNonNull(selector, "selector");
				if (!selector.id().equals(value.dimension())) {
					throw new IllegalArgumentException("Selector rule ID does not match " + selector.id());
				}
				if (dimensionSelectors.putIfAbsent(selector, value) != null) {
					throw new IllegalStateException("Duplicate ore dimension selector: " + selector.id());
				}
				return this;
			}
			/** Adds a built-in fallback policy used when no explicit dimension rule exists. */
			public Builder dimensionSelector(OreDimensionSelector selector,
					Consumer<OreDimensionDefinition.Builder> edit) {
				OreDimensionDefinition.Builder builder = OreDimensionDefinition.builder(selector.id());
				edit.accept(builder);
				return dimensionSelector(selector, builder.build());
			}

			public OreDefinition build() {
				if (dimensions.isEmpty() && dimensionSelectors.isEmpty()) {
					throw new IllegalStateException("Ore has no dimensions or dimension selectors: " + block);
				}
				return new OreDefinition(this);
			}
		}
	}

	/** One weighted output choice for an ore rule, optionally restricted by Y. */
	public static final class OreOutputDefinition implements JsonDefinition {
		private final ResourceLocation block;
		private final double weight;
		private final int minY;
		private final int maxY;

		private OreOutputDefinition(ResourceLocation block, double weight, int minY, int maxY) {
			this.block = Objects.requireNonNull(block, "block");
			if (!Double.isFinite(weight) || weight <= 0.0D) {
				throw new IllegalArgumentException("Output weight must be positive for " + block);
			}
			requireRange(minY, maxY, "output Y range");
			this.weight = weight;
			this.minY = minY;
			this.maxY = maxY;
		}

		public ResourceLocation block() { return block; }
		public double weight() { return weight; }
		public int minY() { return minY; }
		public int maxY() { return maxY; }

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("block", block.toString());
			json.addProperty("weight", weight);
			json.addProperty("min_y", minY);
			json.addProperty("max_y", maxY);
			return json;
		}
	}

	public static final class OreDimensionDefinition implements JsonDefinition {
		private final ResourceLocation dimension;
		private final boolean enabled;
		private final int minY;
		private final int maxY;
		private final double frequency;
		private final int minQuantity;
		private final int maxQuantity;
		private final OrePattern pattern;
		private final ResourceLocation patternType;
		private final JsonObject patternSettings;
		private final OreHeightDistribution heightDistribution;
		private final double discardChanceOnAirExposure;
		private final int spread;
		private final int verticalSpread;
		private final int nodeSize;
		private final Set<GeologyFamily> hostFamilies;
		private final Map<ResourceLocation, Double> geomes;
		private final Set<ResourceLocation> hostBlocks;
		private final Set<ResourceLocation> hostTags;
		private final Map<ResourceLocation, Double> hostBlockWeights;
		private final Map<ResourceLocation, Double> hostTagWeights;

		private OreDimensionDefinition(Builder builder) {
			dimension = builder.dimension;
			enabled = builder.enabled;
			minY = builder.minY;
			maxY = builder.maxY;
			frequency = builder.frequency;
			minQuantity = builder.minQuantity;
			maxQuantity = builder.maxQuantity;
			pattern = builder.pattern;
			patternType = builder.patternType;
			patternSettings = JsonCopies.copy(builder.patternSettings);
			heightDistribution = builder.heightDistribution;
			discardChanceOnAirExposure = builder.discardChanceOnAirExposure;
			spread = builder.spread;
			verticalSpread = builder.verticalSpread;
			nodeSize = builder.nodeSize;
			hostFamilies = Collections.unmodifiableSet(new LinkedHashSet<>(builder.hostFamilies));
			geomes = immutableMap(builder.geomes);
			hostBlocks = immutableSet(builder.hostBlocks);
			hostTags = immutableSet(builder.hostTags);
			hostBlockWeights = immutableMap(builder.hostBlockWeights);
			hostTagWeights = immutableMap(builder.hostTagWeights);
		}

		public static Builder builder(ResourceLocation dimension) { return new Builder(dimension); }
		public ResourceLocation dimension() { return dimension; }
		public boolean enabled() { return enabled; }
		public int minY() { return minY; }
		public int maxY() { return maxY; }
		public double attempts() { return frequency; }
		/** Compatibility value for consumers that only understand a fixed quantity. */
		public int quantity() { return (minQuantity + maxQuantity + 1) / 2; }
		public int minQuantity() { return minQuantity; }
		public int maxQuantity() { return maxQuantity; }
		public OrePattern pattern() { return pattern; }
		public ResourceLocation patternType() { return patternType; }
		public JsonObject patternSettings() { return JsonCopies.copy(patternSettings); }
		public OreHeightDistribution heightDistribution() { return heightDistribution; }
		public double discardChanceOnAirExposure() { return discardChanceOnAirExposure; }
		public int spread() { return spread; }
		public int verticalSpread() { return verticalSpread; }
		public int nodeSize() { return nodeSize; }
		public Set<GeologyFamily> hostFamilies() { return hostFamilies; }
		public Map<ResourceLocation, Double> geomes() { return geomes; }
		public Set<ResourceLocation> hostBlocks() { return hostBlocks; }
		public Set<ResourceLocation> hostTags() { return hostTags; }
		public Map<ResourceLocation, Double> hostBlockWeights() { return hostBlockWeights; }
		public Map<ResourceLocation, Double> hostTagWeights() { return hostTagWeights; }

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("enabled", enabled);
			json.addProperty("min_y", minY);
			json.addProperty("max_y", maxY);
			json.addProperty("frequency", frequency);
			if (minQuantity == maxQuantity) {
				json.addProperty("quantity", minQuantity);
			} else {
				json.addProperty("min_quantity", minQuantity);
				json.addProperty("max_quantity", maxQuantity);
			}
			if (patternType == null) {
				json.addProperty("pattern", pattern.configName());
			} else {
				JsonObject configuredPattern = new JsonObject();
				configuredPattern.addProperty("type", patternType.toString());
				configuredPattern.add("settings", JsonCopies.copy(patternSettings));
				json.add("pattern", configuredPattern);
			}
			json.addProperty("height_distribution", heightDistribution.configName());
			json.addProperty("discard_chance_on_air_exposure", discardChanceOnAirExposure);
			json.addProperty("spread", spread);
			json.addProperty("vertical_spread", verticalSpread);
			json.addProperty("node_size", nodeSize);
			JsonArray families = new JsonArray();
			for (GeologyFamily family : hostFamilies) { families.add(new JsonPrimitive(family.configName())); }
			json.add("host_families", families);
			json.add("geomes", weights(geomes));
			json.add("host_blocks", weightedIds(hostBlocks, hostBlockWeights, "block"));
			json.add("host_tags", weightedIds(hostTags, hostTagWeights, "tag"));
			return json;
		}

		public static final class Builder {
			private final ResourceLocation dimension;
			private boolean enabled = true;
			private int minY = 0;
			private int maxY = 255;
			private double frequency = 1.0D;
			private int minQuantity = 8;
			private int maxQuantity = 8;
			private OrePattern pattern = OrePattern.VEIN;
			private ResourceLocation patternType;
			private JsonObject patternSettings = new JsonObject();
			private OreHeightDistribution heightDistribution = OreHeightDistribution.UNIFORM;
			private double discardChanceOnAirExposure;
			private int spread = 8;
			private int verticalSpread = 4;
			private int nodeSize = 4;
			private final Set<GeologyFamily> hostFamilies = new LinkedHashSet<>();
			private final Map<ResourceLocation, Double> geomes = new LinkedHashMap<>();
			private final Set<ResourceLocation> hostBlocks = new LinkedHashSet<>();
			private final Set<ResourceLocation> hostTags = new LinkedHashSet<>();
			private final Map<ResourceLocation, Double> hostBlockWeights = new LinkedHashMap<>();
			private final Map<ResourceLocation, Double> hostTagWeights = new LinkedHashMap<>();

			private Builder(ResourceLocation dimension) { this.dimension = Objects.requireNonNull(dimension, "dimension"); }
			public Builder enabled(boolean value) { enabled = value; return this; }
			public Builder yRange(int min, int max) { minY = min; maxY = max; return this; }
			public Builder attempts(double value) { frequency = value; return this; }
			public Builder quantity(int value) { minQuantity = value; maxQuantity = value; return this; }
			/** Selects an inclusive random block budget for each placement attempt. */
			public Builder quantityRange(int min, int max) { minQuantity = min; maxQuantity = max; return this; }
			public Builder pattern(OrePattern value) {
				pattern = Objects.requireNonNull(value);
				patternType = null;
				patternSettings = new JsonObject();
				return this;
			}
			/** Uses a codec-backed pattern registered through {@link OreSpawnPatternRegistry}. */
			public Builder pattern(ResourceLocation type, JsonObject settings) {
				patternType = Objects.requireNonNull(type, "type");
				patternSettings = JsonCopies.copy(Objects.requireNonNull(settings, "settings"));
				return this;
			}
			public Builder heightDistribution(OreHeightDistribution value) { heightDistribution = Objects.requireNonNull(value); return this; }
			public Builder discardChanceOnAirExposure(double value) { discardChanceOnAirExposure = value; return this; }
			public Builder spread(int horizontal, int vertical) { spread = horizontal; verticalSpread = vertical; return this; }
			public Builder nodeSize(int value) { nodeSize = value; return this; }
			public Builder hostFamily(GeologyFamily value) { hostFamilies.add(value); return this; }
			public Builder geomeWeight(ResourceLocation geome, double value) { geomes.put(geome, value); return this; }
			public Builder hostBlock(ResourceLocation value) { hostBlocks.add(value); return this; }
			public Builder hostTag(ResourceLocation value) { hostTags.add(value); return this; }
			public Builder hostBlock(ResourceLocation value, double weight) {
				hostBlocks.add(value);
				hostBlockWeights.put(value, replacementWeight(weight));
				return this;
			}
			public Builder hostTag(ResourceLocation value, double weight) {
				hostTags.add(value);
				hostTagWeights.put(value, replacementWeight(weight));
				return this;
			}

			public OreDimensionDefinition build() {
				requireRange(minY, maxY, "ore Y range");
				if (frequency < 0.0D || frequency > 64.0D
						|| minQuantity < 1 || minQuantity > maxQuantity || maxQuantity > 64
						|| !Double.isFinite(discardChanceOnAirExposure)
						|| discardChanceOnAirExposure < 0.0D || discardChanceOnAirExposure > 1.0D
						|| spread < 0 || spread > 64 || verticalSpread < 0 || verticalSpread > 64
						|| nodeSize < 1 || nodeSize > 32) {
					throw new IllegalStateException("Invalid ore placement values for " + dimension);
				}
				if (enabled && hostFamilies.isEmpty() && hostBlocks.isEmpty() && hostTags.isEmpty()) {
					throw new IllegalStateException("Enabled ore dimension has no hosts: " + dimension);
				}
				return new OreDimensionDefinition(this);
			}
		}
	}

	/** A provider-owned underground deposit made from a registered fluid block. */
	public static final class FluidDepositDefinition implements JsonDefinition {
		private final ResourceLocation id;
		private final ResourceLocation block;
		private final boolean enabled;
		private final Map<ResourceLocation, FluidDepositDimensionDefinition> dimensions;

		private FluidDepositDefinition(Builder builder) {
			id = builder.id;
			block = builder.block;
			enabled = builder.enabled;
			dimensions = Collections.unmodifiableMap(new LinkedHashMap<>(builder.dimensions));
		}

		public static Builder builder(ResourceLocation id, ResourceLocation block) {
			return new Builder(id, block);
		}

		public ResourceLocation id() { return id; }
		public ResourceLocation block() { return block; }
		public boolean enabled() { return enabled; }
		public Map<ResourceLocation, FluidDepositDimensionDefinition> dimensions() { return dimensions; }

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("block", block.toString());
			json.addProperty("enabled", enabled);
			json.add("dimensions", object(dimensions));
			return json;
		}

		public static final class Builder {
			private final ResourceLocation id;
			private final ResourceLocation block;
			private boolean enabled = true;
			private final LinkedHashMap<ResourceLocation, FluidDepositDimensionDefinition> dimensions =
					new LinkedHashMap<>();

			private Builder(ResourceLocation id, ResourceLocation block) {
				this.id = Objects.requireNonNull(id, "id");
				this.block = Objects.requireNonNull(block, "block");
			}

			public Builder enabled(boolean value) { enabled = value; return this; }
			public Builder dimension(FluidDepositDimensionDefinition value) {
				putUnique(dimensions, value.dimension(), value, "fluid deposit dimension");
				return this;
			}
			public Builder dimension(ResourceLocation id,
					Consumer<FluidDepositDimensionDefinition.Builder> edit) {
				FluidDepositDimensionDefinition.Builder builder = FluidDepositDimensionDefinition.builder(id);
				edit.accept(builder);
				return dimension(builder.build());
			}

			public FluidDepositDefinition build() {
				if (dimensions.isEmpty()) {
					throw new IllegalStateException("Fluid deposit has no dimensions: " + id);
				}
				return new FluidDepositDefinition(this);
			}
		}
	}

	/** Placement and host rules for one fluid deposit in one dimension. */
	public static final class FluidDepositDimensionDefinition implements JsonDefinition {
		private final ResourceLocation dimension;
		private final boolean enabled;
		private final int minY;
		private final int maxY;
		private final double frequency;
		private final int minRadius;
		private final int maxRadius;
		private final int minVerticalRadius;
		private final int maxVerticalRadius;
		private final int maxLobes;
		private final int minSolidCover;
		private final int minSolidShell;
		private final Set<GeologyFamily> hostFamilies;
		private final Set<ResourceLocation> hostBlocks;
		private final Set<ResourceLocation> hostTags;
		private final Set<ResourceLocation> biomeIds;
		private final Set<ResourceLocation> excludedBiomeIds;
		private final Set<String> biomeDictionary;
		private final Set<String> excludedBiomeDictionary;
		private final Map<ResourceLocation, Double> geomes;

		private FluidDepositDimensionDefinition(Builder builder) {
			dimension = builder.dimension;
			enabled = builder.enabled;
			minY = builder.minY;
			maxY = builder.maxY;
			frequency = builder.frequency;
			minRadius = builder.minRadius;
			maxRadius = builder.maxRadius;
			minVerticalRadius = builder.minVerticalRadius;
			maxVerticalRadius = builder.maxVerticalRadius;
			maxLobes = builder.maxLobes;
			minSolidCover = builder.minSolidCover;
			minSolidShell = builder.minSolidShell;
			hostFamilies = Collections.unmodifiableSet(new LinkedHashSet<>(builder.hostFamilies));
			hostBlocks = immutableSet(builder.hostBlocks);
			hostTags = immutableSet(builder.hostTags);
			biomeIds = immutableSet(builder.biomeIds);
			excludedBiomeIds = immutableSet(builder.excludedBiomeIds);
			biomeDictionary = Collections.unmodifiableSet(new LinkedHashSet<>(builder.biomeDictionary));
			excludedBiomeDictionary = Collections.unmodifiableSet(
					new LinkedHashSet<>(builder.excludedBiomeDictionary));
			geomes = immutableMap(builder.geomes);
		}

		public static Builder builder(ResourceLocation dimension) { return new Builder(dimension); }
		public ResourceLocation dimension() { return dimension; }
		public boolean enabled() { return enabled; }
		public int minY() { return minY; }
		public int maxY() { return maxY; }
		public double attempts() { return frequency; }
		public int minRadius() { return minRadius; }
		public int maxRadius() { return maxRadius; }
		public int minVerticalRadius() { return minVerticalRadius; }
		public int maxVerticalRadius() { return maxVerticalRadius; }
		public int maxLobes() { return maxLobes; }
		public int minSolidCover() { return minSolidCover; }
		public int minSolidShell() { return minSolidShell; }
		public Set<GeologyFamily> hostFamilies() { return hostFamilies; }
		public Set<ResourceLocation> hostBlocks() { return hostBlocks; }
		public Set<ResourceLocation> hostTags() { return hostTags; }
		public Set<ResourceLocation> biomeIds() { return biomeIds; }
		public Set<ResourceLocation> excludedBiomeIds() { return excludedBiomeIds; }
		public Set<String> biomeDictionary() { return biomeDictionary; }
		public Set<String> excludedBiomeDictionary() { return excludedBiomeDictionary; }
		public Map<ResourceLocation, Double> geomes() { return geomes; }

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("enabled", enabled);
			json.addProperty("min_y", minY);
			json.addProperty("max_y", maxY);
			json.addProperty("frequency", frequency);
			json.addProperty("min_radius", minRadius);
			json.addProperty("max_radius", maxRadius);
			json.addProperty("min_vertical_radius", minVerticalRadius);
			json.addProperty("max_vertical_radius", maxVerticalRadius);
			json.addProperty("max_lobes", maxLobes);
			json.addProperty("min_solid_cover", minSolidCover);
			json.addProperty("min_solid_shell", minSolidShell);
			JsonArray families = new JsonArray();
			for (GeologyFamily family : hostFamilies) families.add(new JsonPrimitive(family.configName()));
			json.add("host_families", families);
			json.add("host_blocks", ids(hostBlocks));
			json.add("host_tags", ids(hostTags));
			json.add("biome_ids", ids(biomeIds));
			json.add("excluded_biome_ids", ids(excludedBiomeIds));
			json.add("biome_dictionary", strings(biomeDictionary));
			json.add("excluded_biome_dictionary", strings(excludedBiomeDictionary));
			json.add("geomes", weights(geomes));
			return json;
		}

		public static final class Builder {
			private final ResourceLocation dimension;
			private boolean enabled = true;
			private int minY = 0;
			private int maxY = 48;
			private double frequency = 0.08D;
			private int minRadius = 5;
			private int maxRadius = 12;
			private int minVerticalRadius = 2;
			private int maxVerticalRadius = 5;
			private int maxLobes = 4;
			private int minSolidCover = 2;
			private int minSolidShell = 1;
			private final Set<GeologyFamily> hostFamilies = new LinkedHashSet<>();
			private final Set<ResourceLocation> hostBlocks = new LinkedHashSet<>();
			private final Set<ResourceLocation> hostTags = new LinkedHashSet<>();
			private final Set<ResourceLocation> biomeIds = new LinkedHashSet<>();
			private final Set<ResourceLocation> excludedBiomeIds = new LinkedHashSet<>();
			private final Set<String> biomeDictionary = new LinkedHashSet<>();
			private final Set<String> excludedBiomeDictionary = new LinkedHashSet<>();
			private final Map<ResourceLocation, Double> geomes = new LinkedHashMap<>();

			private Builder(ResourceLocation dimension) {
				this.dimension = Objects.requireNonNull(dimension, "dimension");
			}

			public Builder enabled(boolean value) { enabled = value; return this; }
			public Builder yRange(int min, int max) { minY = min; maxY = max; return this; }
			public Builder attempts(double value) { frequency = value; return this; }
			public Builder radius(int min, int max) { minRadius = min; maxRadius = max; return this; }
			public Builder verticalRadius(int min, int max) {
				minVerticalRadius = min; maxVerticalRadius = max; return this;
			}
			public Builder maxLobes(int value) { maxLobes = value; return this; }
			public Builder minSolidCover(int value) { minSolidCover = value; return this; }
			public Builder minSolidShell(int value) { minSolidShell = value; return this; }
			public Builder hostFamily(GeologyFamily value) { hostFamilies.add(value); return this; }
			public Builder hostBlock(ResourceLocation value) { hostBlocks.add(value); return this; }
			public Builder hostTag(ResourceLocation value) { hostTags.add(value); return this; }
			public Builder biome(ResourceLocation value) { biomeIds.add(value); return this; }
			public Builder excludeBiome(ResourceLocation value) { excludedBiomeIds.add(value); return this; }
			public Builder biomeDictionary(String value) { biomeDictionary.add(nonBlank(value)); return this; }
			public Builder excludeBiomeDictionary(String value) {
				excludedBiomeDictionary.add(nonBlank(value)); return this;
			}
			public Builder geomeWeight(ResourceLocation geome, double value) {
				if (!Double.isFinite(value) || value < 0.0D) {
					throw new IllegalArgumentException("Geome weight must be finite and non-negative");
				}
				geomes.put(geome, value);
				return this;
			}

			public FluidDepositDimensionDefinition build() {
				requireRange(minY, maxY, "fluid deposit Y range");
				if (!Double.isFinite(frequency) || frequency < 0.0D || frequency > 64.0D
						|| minRadius < 1 || minRadius > maxRadius || maxRadius > 64
						|| minVerticalRadius < 1 || minVerticalRadius > maxVerticalRadius
						|| maxVerticalRadius > 64 || maxLobes < 1 || maxLobes > 16
						|| minSolidCover < 0 || minSolidCover > 64
						|| minSolidShell < 0 || minSolidShell > 64) {
					throw new IllegalStateException("Invalid fluid deposit placement values for " + dimension);
				}
				if (enabled && hostFamilies.isEmpty() && hostBlocks.isEmpty() && hostTags.isEmpty()) {
					throw new IllegalStateException("Enabled fluid deposit dimension has no hosts: " + dimension);
				}
				return new FluidDepositDimensionDefinition(this);
			}
		}
	}

	public static final class GeomeDefinition implements JsonDefinition {
		private final ResourceLocation id;
		private final double baseWeight;
		private final Map<GeologyFamily, Double> familyWeights;

		private GeomeDefinition(Builder builder) {
			id = builder.id;
			baseWeight = builder.baseWeight;
			familyWeights = Collections.unmodifiableMap(new LinkedHashMap<>(builder.familyWeights));
		}

		public static Builder builder(ResourceLocation id) { return new Builder(id); }
		public ResourceLocation id() { return id; }
		public double baseWeight() { return baseWeight; }
		public Map<GeologyFamily, Double> familyWeights() { return familyWeights; }

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("base", baseWeight);
			for (GeologyFamily family : GeologyFamily.values()) {
				json.addProperty(family.configName(), familyWeights.getOrDefault(family, 1.0D));
			}
			return json;
		}

		public static final class Builder {
			private final ResourceLocation id;
			private double baseWeight = 1.0D;
			private final Map<GeologyFamily, Double> familyWeights = new LinkedHashMap<>();
			private Builder(ResourceLocation id) { this.id = Objects.requireNonNull(id, "id"); }
			public Builder baseWeight(double value) { baseWeight = value; return this; }
			public Builder familyWeight(GeologyFamily family, double value) { familyWeights.put(family, value); return this; }
			public GeomeDefinition build() {
				if (!Double.isFinite(baseWeight) || baseWeight < 0.0D) {
					throw new IllegalStateException("Invalid geome base weight for " + id);
				}
				return new GeomeDefinition(this);
			}
		}
	}

	public static final class BiomeRule implements JsonDefinition {
		private final ResourceLocation biome;
		private final Map<ResourceLocation, Double> geomeWeights;
		public BiomeRule(ResourceLocation biome, Map<ResourceLocation, Double> geomeWeights) {
			this.biome = Objects.requireNonNull(biome, "biome");
			this.geomeWeights = immutableMap(geomeWeights);
		}
		public ResourceLocation biome() { return biome; }
		public Map<ResourceLocation, Double> geomeWeights() { return geomeWeights; }
		@Override public JsonObject toJson() { return weights(geomeWeights); }
	}

	public static final class TerrainDimensionDefinition implements JsonDefinition {
		private final ResourceLocation dimension;
		private final boolean enabled;
		private final Set<ResourceLocation> biomeIds;
		private final Set<String> biomeNamespaces;
		private final Set<ResourceLocation> hostBlocks;
		private final Set<ResourceLocation> hostTags;

		private TerrainDimensionDefinition(Builder builder) {
			dimension = builder.dimension;
			enabled = builder.enabled;
			biomeIds = immutableSet(builder.biomeIds);
			biomeNamespaces = Collections.unmodifiableSet(new LinkedHashSet<>(builder.biomeNamespaces));
			hostBlocks = immutableSet(builder.hostBlocks);
			hostTags = immutableSet(builder.hostTags);
		}

		public static Builder builder(ResourceLocation id) { return new Builder(id); }
		public ResourceLocation dimension() { return dimension; }
		public boolean enabled() { return enabled; }
		public Set<ResourceLocation> biomeIds() { return biomeIds; }
		public Set<String> biomeNamespaces() { return biomeNamespaces; }
		public Set<ResourceLocation> hostBlocks() { return hostBlocks; }
		public Set<ResourceLocation> hostTags() { return hostTags; }
		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("enabled", enabled);
			json.add("biome_ids", ids(biomeIds));
			JsonArray namespaces = new JsonArray();
			for (String namespace : biomeNamespaces) { namespaces.add(new JsonPrimitive(namespace)); }
			json.add("biome_namespaces", namespaces);
			json.add("host_blocks", ids(hostBlocks));
			json.add("host_tags", ids(hostTags));
			return json;
		}

		public static final class Builder {
			private final ResourceLocation dimension;
			private boolean enabled = true;
			private final Set<ResourceLocation> biomeIds = new LinkedHashSet<>();
			private final Set<String> biomeNamespaces = new LinkedHashSet<>();
			private final Set<ResourceLocation> hostBlocks = new LinkedHashSet<>();
			private final Set<ResourceLocation> hostTags = new LinkedHashSet<>();
			private Builder(ResourceLocation id) { dimension = Objects.requireNonNull(id, "dimension"); }
			public Builder enabled(boolean value) { enabled = value; return this; }
			public Builder biome(ResourceLocation value) { biomeIds.add(value); return this; }
			public Builder biomeNamespace(String value) { biomeNamespaces.add(requireModId(value)); return this; }
			public Builder hostBlock(ResourceLocation value) { hostBlocks.add(value); return this; }
			public Builder hostTag(ResourceLocation value) { hostTags.add(value); return this; }
			public TerrainDimensionDefinition build() {
				if (enabled && hostBlocks.isEmpty() && hostTags.isEmpty()) {
					throw new IllegalStateException("Enabled terrain dimension has no replacement hosts: " + dimension);
				}
				return new TerrainDimensionDefinition(this);
			}
		}
	}

	public static final class FormationDefinition implements JsonDefinition {
		private final GeologyAlgorithm algorithm;
		private final FormationPreset horizontal;
		private final FormationPreset thickness;
		private final FormationPreset waviness;
		private final FormationPreset edge;
		private final FormationPreset continuity;
		private final JsonObject custom;

		public FormationDefinition(GeologyAlgorithm algorithm, FormationPreset horizontal,
				FormationPreset thickness, FormationPreset waviness, FormationPreset edge,
				FormationPreset continuity, JsonObject custom) {
			this.algorithm = Objects.requireNonNull(algorithm);
			this.horizontal = Objects.requireNonNull(horizontal);
			this.thickness = Objects.requireNonNull(thickness);
			this.waviness = Objects.requireNonNull(waviness);
			this.edge = Objects.requireNonNull(edge);
			this.continuity = Objects.requireNonNull(continuity);
			this.custom = custom == null ? new JsonObject() : JsonCopies.copy(custom);
		}

		public static Builder builder() { return new Builder(); }

		public GeologyAlgorithm algorithm() { return algorithm; }
		public FormationPreset horizontalSize() { return horizontal; }
		public FormationPreset verticalThickness() { return thickness; }
		public FormationPreset waviness() { return waviness; }
		public FormationPreset edgeIrregularity() { return edge; }
		public FormationPreset continuity() { return continuity; }
		public JsonObject customValues() { return JsonCopies.copy(custom); }

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("algorithm", algorithm.configName());
			json.addProperty("horizontal_size", horizontal.configName());
			json.addProperty("vertical_thickness", thickness.configName());
			json.addProperty("waviness", waviness.configName());
			json.addProperty("edge_irregularity", edge.configName());
			json.addProperty("formation_continuity", continuity.configName());
			json.add("custom", JsonCopies.copy(custom));
			return json;
		}

		public static final class Builder {
			private GeologyAlgorithm algorithm = GeologyAlgorithm.STABLE_LAYERS;
			private FormationPreset horizontal = FormationPreset.AVERAGE;
			private FormationPreset thickness = FormationPreset.AVERAGE;
			private FormationPreset waviness = FormationPreset.AVERAGE;
			private FormationPreset edge = FormationPreset.AVERAGE;
			private FormationPreset continuity = FormationPreset.AVERAGE;
			private final JsonObject custom = new JsonObject();

			private Builder() { }
			public Builder algorithm(GeologyAlgorithm value) { algorithm = Objects.requireNonNull(value); return this; }
			public Builder horizontalSize(FormationPreset value) { horizontal = Objects.requireNonNull(value); return this; }
			public Builder verticalThickness(FormationPreset value) { thickness = Objects.requireNonNull(value); return this; }
			public Builder waviness(FormationPreset value) { waviness = Objects.requireNonNull(value); return this; }
			public Builder edgeIrregularity(FormationPreset value) { edge = Objects.requireNonNull(value); return this; }
			public Builder continuity(FormationPreset value) { continuity = Objects.requireNonNull(value); return this; }
			public Builder customValue(String key, double value) {
				if (key == null || key.trim().isEmpty() || !Double.isFinite(value)) {
					throw new IllegalArgumentException("Invalid custom formation value");
				}
				custom.addProperty(key, value);
				return this;
			}
			public Builder customValues(JsonObject values) {
				custom.entrySet().clear();
				if (values != null) {
					values.entrySet().forEach(entry -> custom.add(entry.getKey(), JsonCopies.copy(entry.getValue())));
				}
				return this;
			}
			public FormationDefinition build() {
				return new FormationDefinition(algorithm, horizontal, thickness, waviness, edge, continuity, custom);
			}
		}
	}

	/** @deprecated Use {@link FluidDepositDefinition}. */
	@Deprecated
	public static final class OilDefinition implements JsonDefinition {
		private final int minY;
		private final int maxY;
		private final double frequency;
		private final int minRadius;
		private final int maxRadius;
		private final int minVerticalRadius;
		private final int maxVerticalRadius;
		private final int maxLobes;
		private final int minSolidCover;

		public OilDefinition(int minY, int maxY, double frequency, int minRadius, int maxRadius,
				int minVerticalRadius, int maxVerticalRadius, int maxLobes, int minSolidCover) {
			requireRange(minY, maxY, "oil Y range");
			if (!Double.isFinite(frequency) || frequency < 0.0D || frequency > 64.0D
					|| minRadius < 1 || minRadius > maxRadius
					|| minVerticalRadius < 1 || minVerticalRadius > maxVerticalRadius
					|| maxLobes < 1 || minSolidCover < 0) {
				throw new IllegalArgumentException("Invalid oil placement values");
			}
			this.minY = minY;
			this.maxY = maxY;
			this.frequency = frequency;
			this.minRadius = minRadius;
			this.maxRadius = maxRadius;
			this.minVerticalRadius = minVerticalRadius;
			this.maxVerticalRadius = maxVerticalRadius;
			this.maxLobes = maxLobes;
			this.minSolidCover = minSolidCover;
		}

		public static Builder builder() { return new Builder(); }

		public int minY() { return minY; }
		public int maxY() { return maxY; }
		public double frequency() { return frequency; }
		public int minRadius() { return minRadius; }
		public int maxRadius() { return maxRadius; }
		public int minVerticalRadius() { return minVerticalRadius; }
		public int maxVerticalRadius() { return maxVerticalRadius; }
		public int maxLobes() { return maxLobes; }
		public int minSolidCover() { return minSolidCover; }

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("min_y", minY);
			json.addProperty("max_y", maxY);
			json.addProperty("frequency", frequency);
			json.addProperty("min_radius", minRadius);
			json.addProperty("max_radius", maxRadius);
			json.addProperty("min_vertical_radius", minVerticalRadius);
			json.addProperty("max_vertical_radius", maxVerticalRadius);
			json.addProperty("max_lobes", maxLobes);
			json.addProperty("min_solid_cover", minSolidCover);
			return json;
		}

		public static final class Builder {
			private int minY = 0;
			private int maxY = 40;
			private double frequency = 0.035D;
			private int minRadius = 8;
			private int maxRadius = 16;
			private int minVerticalRadius = 3;
			private int maxVerticalRadius = 7;
			private int maxLobes = 4;
			private int minSolidCover = 2;

			private Builder() { }
			public Builder yRange(int min, int max) { minY = min; maxY = max; return this; }
			public Builder attempts(double value) { frequency = value; return this; }
			public Builder radius(int min, int max) { minRadius = min; maxRadius = max; return this; }
			public Builder verticalRadius(int min, int max) { minVerticalRadius = min; maxVerticalRadius = max; return this; }
			public Builder maxLobes(int value) { maxLobes = value; return this; }
			public Builder minSolidCover(int value) { minSolidCover = value; return this; }
			public OilDefinition build() {
				return new OilDefinition(minY, maxY, frequency, minRadius, maxRadius,
						minVerticalRadius, maxVerticalRadius, maxLobes, minSolidCover);
			}
		}
	}

	public static final class GeologyTemplate implements JsonDefinition {
		private final ResourceLocation id;
		private final String nameKey;
		private final String descriptionKey;
		private final Set<String> requiredMods;
		private final boolean autoSelect;
		private final int autoSelectPriority;
		private final JsonObject profile;

		private GeologyTemplate(Builder builder) {
			id = builder.id;
			nameKey = builder.nameKey;
			descriptionKey = builder.descriptionKey;
			requiredMods = Collections.unmodifiableSet(new LinkedHashSet<>(builder.requiredMods));
			autoSelect = builder.autoSelect;
			autoSelectPriority = builder.autoSelectPriority;
			profile = JsonCopies.copy(builder.profile);
		}

		public static Builder builder(ResourceLocation id) { return new Builder(id); }
		public ResourceLocation id() { return id; }
		public String nameKey() { return nameKey; }
		public String descriptionKey() { return descriptionKey; }
		public Set<String> requiredMods() { return requiredMods; }
		public boolean autoSelect() { return autoSelect; }
		public int autoSelectPriority() { return autoSelectPriority; }
		public JsonObject profile() { return JsonCopies.copy(profile); }
		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("name_key", nameKey);
			json.addProperty("description_key", descriptionKey);
			JsonArray mods = new JsonArray();
			for (String mod : requiredMods) { mods.add(new JsonPrimitive(mod)); }
			json.add("required_mods", mods);
			json.addProperty("auto_select", autoSelect);
			json.addProperty("auto_select_priority", autoSelectPriority);
			json.add("profile", JsonCopies.copy(profile));
			return json;
		}

		public static final class Builder {
			private final ResourceLocation id;
			private String nameKey;
			private String descriptionKey;
			private final Set<String> requiredMods = new LinkedHashSet<>();
			private boolean autoSelect;
			private int autoSelectPriority;
			private final JsonObject profile = new JsonObject();
			private Builder(ResourceLocation id) {
				this.id = Objects.requireNonNull(id, "id");
				nameKey = "orespawn.template." + id.getResourceDomain() + "." + id.getResourcePath();
				descriptionKey = nameKey + ".description";
			}
			public Builder translationKeys(String name, String description) { nameKey = name; descriptionKey = description; return this; }
			public Builder requiresMod(String value) { requiredMods.add(requireModId(value)); return this; }
			public Builder autoSelect(boolean value) { autoSelect = value; return this; }
			public Builder autoSelectPriority(int value) { autoSelectPriority = value; return this; }
			public Builder profile(JsonObject value) { profile.entrySet().clear(); value.entrySet().forEach(e -> profile.add(e.getKey(), JsonCopies.copy(e.getValue()))); return this; }
			public Builder formations(FormationDefinition value) { profile.add("formations", value.toJson()); return this; }
			public Builder fluidDeposit(FluidDepositDefinition value) {
				JsonObject deposits = profile.has("fluid_deposits") && profile.get("fluid_deposits").isJsonObject()
						? profile.getAsJsonObject("fluid_deposits") : new JsonObject();
				deposits.add(value.id().toString(), value.toJson());
				profile.add("fluid_deposits", deposits);
				profile.addProperty("place_fluid_deposits", true);
				return this;
			}
			/** @deprecated Use {@code fluidDeposit(FluidDepositDefinition)}. */
			@Deprecated
			public Builder oil(OilDefinition value) {
				profile.add("oil", value.toJson());
				profile.addProperty("place_crude_oil", true);
				return this;
			}
			public Builder geologyMode(String value) { profile.addProperty("geology_mode", value); return this; }
			public Builder manageVanillaOres(boolean value) { profile.addProperty("manage_vanilla_ores", value); return this; }
			public GeologyTemplate build() {
				if (profile.entrySet().isEmpty()) { throw new IllegalStateException("Template profile is empty: " + id); }
				return new GeologyTemplate(this);
			}
		}
	}

	private interface JsonDefinition {
		JsonObject toJson();
	}

	private static <T extends JsonDefinition> JsonObject object(Map<ResourceLocation, T> values) {
		JsonObject json = new JsonObject();
		for (Map.Entry<ResourceLocation, T> entry : values.entrySet()) {
			json.add(entry.getKey().toString(), entry.getValue().toJson());
		}
		return json;
	}

	private static JsonObject weights(Map<ResourceLocation, Double> values) {
		JsonObject json = new JsonObject();
		for (Map.Entry<ResourceLocation, Double> entry : values.entrySet()) {
			json.addProperty(entry.getKey().toString(), entry.getValue());
		}
		return json;
	}

	private static JsonArray ids(Collection<ResourceLocation> values) {
		JsonArray json = new JsonArray();
		for (ResourceLocation value : values) { json.add(new JsonPrimitive(value.toString())); }
		return json;
	}

	/**
	 * A provider-owned set of biomes which can augment or replace an existing
	 * dimension biome source without depending on a particular biome framework.
	 */
	public static final class BiomePaletteDefinition implements JsonDefinition {
		private final ResourceLocation id;
		private final ResourceLocation dimension;
		private final boolean enabled;
		private final BiomePlacementMode mode;
		private final BiomeReplacementScope scope;
		private final BiomeRegionSize regionSize;
		private final double coverage;
		private final double fallbackWeight;
		private final Set<String> includedNamespaces;
		private final Set<String> excludedNamespaces;
		private final Map<ResourceLocation, BiomePlacementDefinition> biomes;

		private BiomePaletteDefinition(Builder builder) {
			id = builder.id;
			dimension = builder.dimension;
			enabled = builder.enabled;
			mode = builder.mode;
			scope = builder.scope;
			regionSize = builder.regionSize;
			coverage = builder.coverage;
			fallbackWeight = builder.fallbackWeight;
			includedNamespaces = Collections.unmodifiableSet(
					new LinkedHashSet<>(builder.includedNamespaces));
			excludedNamespaces = Collections.unmodifiableSet(
					new LinkedHashSet<>(builder.excludedNamespaces));
			biomes = Collections.unmodifiableMap(new LinkedHashMap<>(builder.biomes));
		}

		public static Builder builder(ResourceLocation id, ResourceLocation dimension) {
			return new Builder(id, dimension);
		}

		public ResourceLocation id() { return id; }
		public ResourceLocation dimension() { return dimension; }
		public boolean enabled() { return enabled; }
		public BiomePlacementMode mode() { return mode; }
		public BiomeReplacementScope scope() { return scope; }
		public BiomeRegionSize regionSize() { return regionSize; }
		public double coverage() { return coverage; }
		public double fallbackWeight() { return fallbackWeight; }
		public Set<String> includedNamespaces() { return includedNamespaces; }
		public Set<String> excludedNamespaces() { return excludedNamespaces; }
		public Map<ResourceLocation, BiomePlacementDefinition> biomes() { return biomes; }

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("dimension", dimension.toString());
			json.addProperty("enabled", enabled);
			json.addProperty("mode", mode.configName());
			json.addProperty("scope", scope.configName());
			json.addProperty("region_size", regionSize.configName());
			json.addProperty("coverage", coverage);
			json.addProperty("fallback_weight", fallbackWeight);
			json.add("include_namespaces", strings(includedNamespaces));
			json.add("exclude_namespaces", strings(excludedNamespaces));
			json.add("biomes", object(biomes));
			return json;
		}

		public static final class Builder {
			private final ResourceLocation id;
			private final ResourceLocation dimension;
			private boolean enabled = true;
			private BiomePlacementMode mode = BiomePlacementMode.AUGMENT;
			private BiomeReplacementScope scope = BiomeReplacementScope.MINECRAFT_ONLY;
			private BiomeRegionSize regionSize = BiomeRegionSize.AVERAGE;
			private double coverage = 1.0D;
			private double fallbackWeight = 1.0D;
			private final Set<String> includedNamespaces = new LinkedHashSet<>();
			private final Set<String> excludedNamespaces = new LinkedHashSet<>();
			private final Map<ResourceLocation, BiomePlacementDefinition> biomes =
					new LinkedHashMap<>();

			private Builder(ResourceLocation id, ResourceLocation dimension) {
				this.id = Objects.requireNonNull(id, "id");
				this.dimension = Objects.requireNonNull(dimension, "dimension");
			}

			public Builder enabled(boolean value) { enabled = value; return this; }
			public Builder mode(BiomePlacementMode value) { mode = Objects.requireNonNull(value); return this; }
			public Builder scope(BiomeReplacementScope value) { scope = Objects.requireNonNull(value); return this; }
			public Builder regionSize(BiomeRegionSize value) { regionSize = Objects.requireNonNull(value); return this; }
			public Builder coverage(double value) { coverage = value; return this; }
			public Builder fallbackWeight(double value) { fallbackWeight = value; return this; }
			public Builder includeNamespace(String value) { includedNamespaces.add(requireModId(value)); return this; }
			public Builder excludeNamespace(String value) { excludedNamespaces.add(requireModId(value)); return this; }
			public Builder biome(BiomePlacementDefinition value) {
				putUnique(biomes, value.biome(), value, "biome placement");
				return this;
			}
			public Builder biome(ResourceLocation biome,
					Consumer<BiomePlacementDefinition.Builder> edit) {
				BiomePlacementDefinition.Builder builder = BiomePlacementDefinition.builder(biome);
				edit.accept(builder);
				return biome(builder.build());
			}

			public BiomePaletteDefinition build() {
				if (!Double.isFinite(coverage) || coverage < 0.0D || coverage > 1.0D
						|| !Double.isFinite(fallbackWeight) || fallbackWeight < 0.0D) {
					throw new IllegalStateException("Invalid biome palette coverage or fallback weight: " + id);
				}
				if (enabled && biomes.isEmpty()) {
					throw new IllegalStateException("Enabled biome palette has no biomes: " + id);
				}
				if (scope == BiomeReplacementScope.SELECTED_NAMESPACES
						&& includedNamespaces.isEmpty()) {
					throw new IllegalStateException("Selected-namespace biome palette has no namespaces: " + id);
				}
				return new BiomePaletteDefinition(this);
			}
		}
	}

	/** Placement and optional surface settings for one registered biome. */
	public static final class BiomePlacementDefinition implements JsonDefinition {
		private final ResourceLocation biome;
		private final boolean enabled;
		private final double weight;
		private final Set<ResourceLocation> similarBiomes;
		private final Set<ResourceLocation> requiredSimilarBiomes;
		private final double minTemperature;
		private final double maxTemperature;
		private final double minDownfall;
		private final double maxDownfall;
		private final BiomeSurfaceDefinition surface;

		private BiomePlacementDefinition(Builder builder) {
			biome = builder.biome;
			enabled = builder.enabled;
			weight = builder.weight;
			similarBiomes = immutableSet(builder.similarBiomes);
			requiredSimilarBiomes = immutableSet(builder.requiredSimilarBiomes);
			minTemperature = builder.minTemperature;
			maxTemperature = builder.maxTemperature;
			minDownfall = builder.minDownfall;
			maxDownfall = builder.maxDownfall;
			surface = builder.surface;
		}

		public static Builder builder(ResourceLocation biome) { return new Builder(biome); }
		public ResourceLocation biome() { return biome; }
		public boolean enabled() { return enabled; }
		public double weight() { return weight; }
		public Set<ResourceLocation> similarBiomes() { return similarBiomes; }
		public Set<ResourceLocation> requiredSimilarBiomes() { return requiredSimilarBiomes; }
		public BiomeSurfaceDefinition surface() { return surface; }

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("enabled", enabled);
			json.addProperty("weight", weight);
			json.add("similar_biomes", ids(similarBiomes));
			json.add("required_similar_biomes", ids(requiredSimilarBiomes));
			json.addProperty("min_temperature", minTemperature);
			json.addProperty("max_temperature", maxTemperature);
			json.addProperty("min_downfall", minDownfall);
			json.addProperty("max_downfall", maxDownfall);
			if (surface != null) json.add("surface", surface.toJson());
			return json;
		}

		public static final class Builder {
			private final ResourceLocation biome;
			private boolean enabled = true;
			private double weight = 1.0D;
			private final Set<ResourceLocation> similarBiomes = new LinkedHashSet<>();
			private final Set<ResourceLocation> requiredSimilarBiomes = new LinkedHashSet<>();
			private double minTemperature = -2.0D;
			private double maxTemperature = 2.0D;
			private double minDownfall = 0.0D;
			private double maxDownfall = 1.0D;
			private BiomeSurfaceDefinition surface;

			private Builder(ResourceLocation biome) {
				this.biome = Objects.requireNonNull(biome, "biome");
			}

			public Builder enabled(boolean value) { enabled = value; return this; }
			public Builder weight(double value) { weight = value; return this; }
			public Builder similarBiome(ResourceLocation value) { similarBiomes.add(value); return this; }
			public Builder requiredSimilarBiome(ResourceLocation value) {
				requiredSimilarBiomes.add(value);
				return this;
			}
			public Builder temperature(double min, double max) {
				minTemperature = min;
				maxTemperature = max;
				return this;
			}
			public Builder downfall(double min, double max) {
				minDownfall = min;
				maxDownfall = max;
				return this;
			}
			public Builder surface(BiomeSurfaceDefinition value) { surface = value; return this; }

			public BiomePlacementDefinition build() {
				if (!Double.isFinite(weight) || weight < 0.0D
						|| minTemperature > maxTemperature || minDownfall > maxDownfall
						|| minDownfall < 0.0D || maxDownfall > 1.0D) {
					throw new IllegalStateException("Invalid biome placement: " + biome);
				}
				return new BiomePlacementDefinition(this);
			}
		}
	}

	/** Surface block choices applied only to columns using this biome. */
	public static final class BiomeSurfaceDefinition implements JsonDefinition {
		private final ResourceLocation topBlock;
		private final ResourceLocation fillerBlock;
		private final ResourceLocation underwaterBlock;
		private final ResourceLocation ceilingBlock;
		private final int fillerDepth;

		private BiomeSurfaceDefinition(Builder builder) {
			topBlock = builder.topBlock;
			fillerBlock = builder.fillerBlock;
			underwaterBlock = builder.underwaterBlock;
			ceilingBlock = builder.ceilingBlock;
			fillerDepth = builder.fillerDepth;
		}

		public static Builder builder() { return new Builder(); }

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			if (topBlock != null) json.addProperty("top_block", topBlock.toString());
			if (fillerBlock != null) json.addProperty("filler_block", fillerBlock.toString());
			if (underwaterBlock != null) json.addProperty("underwater_block", underwaterBlock.toString());
			if (ceilingBlock != null) json.addProperty("ceiling_block", ceilingBlock.toString());
			json.addProperty("filler_depth", fillerDepth);
			return json;
		}

		public static final class Builder {
			private ResourceLocation topBlock;
			private ResourceLocation fillerBlock;
			private ResourceLocation underwaterBlock;
			private ResourceLocation ceilingBlock;
			private int fillerDepth = 3;

			private Builder() { }
			public Builder topBlock(ResourceLocation value) { topBlock = value; return this; }
			public Builder fillerBlock(ResourceLocation value) { fillerBlock = value; return this; }
			public Builder underwaterBlock(ResourceLocation value) { underwaterBlock = value; return this; }
			public Builder ceilingBlock(ResourceLocation value) { ceilingBlock = value; return this; }
			public Builder fillerDepth(int value) { fillerDepth = value; return this; }
			public BiomeSurfaceDefinition build() {
				if (fillerDepth < 0 || fillerDepth > 16) {
					throw new IllegalStateException("Biome surface filler depth must be within 0..16");
				}
				return new BiomeSurfaceDefinition(this);
			}
		}
	}

	/** Dimension-wide aquifer and weather materials. */
	public static final class DimensionMaterialsDefinition implements JsonDefinition {
		private final ResourceLocation id;
		private final ResourceLocation dimension;
		private final boolean enabled;
		private final ResourceLocation defaultFluid;
		private final ResourceLocation deepAquiferFluid;
		private final int deepAquiferMaxY;
		private final ResourceLocation snowBlock;
		private final ResourceLocation iceBlock;

		private DimensionMaterialsDefinition(Builder builder) {
			id = builder.id;
			dimension = builder.dimension;
			enabled = builder.enabled;
			defaultFluid = builder.defaultFluid;
			deepAquiferFluid = builder.deepAquiferFluid;
			deepAquiferMaxY = builder.deepAquiferMaxY;
			snowBlock = builder.snowBlock;
			iceBlock = builder.iceBlock;
		}

		public static Builder builder(ResourceLocation id, ResourceLocation dimension) {
			return new Builder(id, dimension);
		}
		public ResourceLocation id() { return id; }
		public ResourceLocation dimension() { return dimension; }

		@Override
		public JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("dimension", dimension.toString());
			json.addProperty("enabled", enabled);
			if (defaultFluid != null) json.addProperty("default_fluid", defaultFluid.toString());
			if (deepAquiferFluid != null) json.addProperty("deep_aquifer_fluid", deepAquiferFluid.toString());
			json.addProperty("deep_aquifer_max_y", deepAquiferMaxY);
			if (snowBlock != null) json.addProperty("snow_block", snowBlock.toString());
			if (iceBlock != null) json.addProperty("ice_block", iceBlock.toString());
			return json;
		}

		public static final class Builder {
			private final ResourceLocation id;
			private final ResourceLocation dimension;
			private boolean enabled = true;
			private ResourceLocation defaultFluid;
			private ResourceLocation deepAquiferFluid;
			private int deepAquiferMaxY = -54;
			private ResourceLocation snowBlock;
			private ResourceLocation iceBlock;

			private Builder(ResourceLocation id, ResourceLocation dimension) {
				this.id = Objects.requireNonNull(id, "id");
				this.dimension = Objects.requireNonNull(dimension, "dimension");
			}
			public Builder enabled(boolean value) { enabled = value; return this; }
			public Builder defaultFluid(ResourceLocation value) { defaultFluid = value; return this; }
			public Builder deepAquiferFluid(ResourceLocation value, int maxY) {
				deepAquiferFluid = value;
				deepAquiferMaxY = maxY;
				return this;
			}
			public Builder snowBlock(ResourceLocation value) { snowBlock = value; return this; }
			public Builder iceBlock(ResourceLocation value) { iceBlock = value; return this; }
			public DimensionMaterialsDefinition build() {
				if (enabled && defaultFluid == null && deepAquiferFluid == null
						&& snowBlock == null && iceBlock == null) {
					throw new IllegalStateException("Enabled dimension materials are empty: " + id);
				}
				return new DimensionMaterialsDefinition(this);
			}
		}
	}

	private static JsonArray strings(Collection<String> values) {
		JsonArray json = new JsonArray();
		for (String value : values) { json.add(new JsonPrimitive(value)); }
		return json;
	}

	private static JsonArray weightedIds(Collection<ResourceLocation> values,
			Map<ResourceLocation, Double> weights, String idKey) {
		JsonArray json = new JsonArray();
		for (ResourceLocation value : values) {
			Double weight = weights.get(value);
			if (weight == null || weight.doubleValue() == 1.0D) {
				json.add(new JsonPrimitive(value.toString()));
			} else {
				JsonObject entry = new JsonObject();
				entry.addProperty(idKey, value.toString());
				entry.addProperty("weight", weight);
				json.add(entry);
			}
		}
		return json;
	}

	private static double replacementWeight(double value) {
		if (!Double.isFinite(value) || value < 0.0D || value > 1.0D) {
			throw new IllegalArgumentException("Replacement weight must be between 0 and 1: " + value);
		}
		return value;
	}

	private static <K, V> void putUnique(Map<K, V> values, K key, V value, String type) {
		if (values.putIfAbsent(key, value) != null) {
			throw new IllegalArgumentException("Duplicate " + type + ": " + key);
		}
	}

	private static String requireModId(String value) {
		Objects.requireNonNull(value, "modId");
		ResourceLocation probe = new ResourceLocation(value, "provider");
		if (!probe.getResourceDomain().equals(value)) {
			throw new IllegalArgumentException("Invalid mod ID: " + value);
		}
		return value;
	}

	private static String nonBlank(String value) {
		Objects.requireNonNull(value, "value");
		String normalized = value.trim();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Value must not be blank");
		}
		return normalized;
	}

	private static void requireRange(int min, int max, String name) {
		if (min < -2048 || max > 2048 || min > max) {
			throw new IllegalArgumentException("Invalid " + name + ": " + min + ".." + max);
		}
	}

	private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(values));
	}

	private static <T> Set<T> immutableSet(Set<T> values) {
		return Collections.unmodifiableSet(new LinkedHashSet<>(values));
	}
}
