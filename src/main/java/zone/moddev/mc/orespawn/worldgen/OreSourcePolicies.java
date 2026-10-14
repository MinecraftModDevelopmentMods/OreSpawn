package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import zone.moddev.mc.orespawn.util.JsonCopies;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/** Bake-time discovery and persistence for equivalent managed ore sources. */
final class OreSourcePolicies {
	static final ResourceLocation STANDARD = new ResourceLocation("orespawn", "standard");
	static final String SECTION = "ore_source_policies";
	private static final String MODE_CONSOLIDATED = "consolidated";
	private static final String MODE_SEPARATE = "keep_separate";
	private static final String OUTPUT_BALANCED = "balanced";
	private static final String OUTPUT_SINGLE = "single";
	private static final String OUTPUT_CUSTOM = "custom";

	private OreSourcePolicies() { }

	/**
	 * Discovers currently available sources and creates only missing policies.
	 * Existing selections are never discarded, which lets missing mods return.
	 */
	static boolean initialize(JsonObject root, boolean existingWorld) {
		boolean changed = OreMaterialGroups.initialize(root);
		boolean hadPolicies = root.has(SECTION) && root.get(SECTION).isJsonObject();
		JsonObject policies = object(root, SECTION);
		Map<String, Group> groups = discover(root);
		for (Group group : groups.values()) {
			Set<String> names = new LinkedHashSet<>();
			for (Candidate candidate : group.candidates) names.addAll(candidate.blockTags);
			changed |= OreMaterialGroups.ensureDefinition(root, group.material, names);
		}
		changed |= !hadPolicies;
		Set<String> refreshedKeys = new LinkedHashSet<>();
		for (Group group : groups.values()) {
			String key = key(group.material, group.domain);
			JsonObject previous = policies.has(key) && policies.get(key).isJsonObject()
					? policies.getAsJsonObject(key) : null;
			JsonObject refreshed = previous == null
					? createPolicy(group, existingWorld) : refreshPolicy(previous, group);
			refreshed.remove("dormant");
			if (previous == null || !previous.toString().equals(refreshed.toString())) {
				policies.add(key, refreshed);
				changed = true;
			}
			refreshedKeys.add(key);
		}
		for (Entry<String, JsonElement> entry : new ArrayList<>(policies.entrySet())) {
			if (refreshedKeys.contains(entry.getKey()) || !entry.getValue().isJsonObject()) continue;
			JsonObject previous = entry.getValue().getAsJsonObject();
			ResourceLocation material = resource(string(previous, "material", ""));
			ResourceLocation domain = resource(string(previous, "domain", ""));
			if (material == null || domain == null) continue;
			Group group = groups.get(key(material, domain));
			if (group == null) group = new Group(material, domain);
			JsonObject refreshed = refreshPolicy(previous, group);
			if (candidatesWereReassigned(root, previous, material)) {
				refreshed.addProperty("dormant", true);
			}
			if (!previous.toString().equals(refreshed.toString())) {
				policies.add(entry.getKey(), refreshed);
				changed = true;
			}
		}
		root.add(SECTION, policies);
		return changed;
	}

	/**
	 * A retained policy is missing from discovery either because its mod is absent or
	 * because the user moved its inferred aliases into another material group. Missing
	 * mods must remain visible; reassigned policies must remain stored but dormant.
	 */
	private static boolean candidatesWereReassigned(JsonObject root, JsonObject policy,
			ResourceLocation material) {
		if (!policy.has("candidates") || !policy.get("candidates").isJsonArray()) return false;
		boolean foundInferredCandidate = false;
		for (JsonElement element : policy.getAsJsonArray("candidates")) {
			if (!element.isJsonObject()) continue;
			JsonObject candidate = element.getAsJsonObject();
			if (bool(candidate, "material_declared", false)) return false;
			List<String> names = strings(candidate.get("block_tags"));
			if (names.isEmpty()) return false;
			OreMaterialGroups.Inference inferred = OreMaterialGroups.infer(root, names);
			if (inferred.material == null || material.equals(inferred.material)) return false;
			foundInferredCandidate = true;
		}
		return foundInferredCandidate;
	}

	static Snapshot snapshot(JsonObject root) {
		Map<String, Group> discovered = discover(root);
		JsonObject policies = object(root, SECTION);
		List<GroupView> views = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (Entry<String, JsonElement> entry : policies.entrySet()) {
			if (!entry.getValue().isJsonObject()) continue;
			JsonObject policy = entry.getValue().getAsJsonObject();
			if (bool(policy, "dormant", false)) continue;
			ResourceLocation material = resource(string(policy, "material", ""));
			ResourceLocation domain = resource(string(policy, "domain", ""));
			if (material == null || domain == null) continue;
			Group current = discovered.get(key(material, domain));
			List<Candidate> candidates = readCandidates(policy, current);
			String mode = string(policy, "mode", MODE_SEPARATE);
			OreMaterialGroups.Definition definition = OreMaterialGroups.definition(root, material);
			views.add(new GroupView(entry.getKey(), material, domain, definition.displayName,
					definition.blockTagEntries, definition.curated,
					mode, outputMode(policy), status(policy, candidates), candidates,
					decimalMap(policy, "outputs"), stringMap(policy, "placement_sources")));
			seen.add(entry.getKey());
		}
		for (Group group : discovered.values()) {
			String key = key(group.material, group.domain);
			if (seen.contains(key)) continue;
			JsonObject policy = createPolicy(group, true);
			OreMaterialGroups.Definition definition = OreMaterialGroups.definition(root, group.material);
			views.add(new GroupView(key, group.material, group.domain, definition.displayName,
					definition.blockTagEntries, definition.curated, MODE_SEPARATE, OUTPUT_CUSTOM,
					status(policy, group.candidates), immutableCandidates(group.candidates),
					decimalMap(policy, "outputs"), stringMap(policy, "placement_sources")));
		}
		views.sort(Comparator.comparing((GroupView view) -> view.material.toString())
				.thenComparing(view -> view.domain.toString()));
		return new Snapshot(views);
	}

	static ResourceLocation material(JsonObject ore, Block output) {
		ResourceLocation explicit = resource(string(ore, "material", ""));
		if (explicit != null) return explicit;
		return inferMaterial(blockTags(output)).material;
	}

	static ResourceLocation material(JsonObject root, JsonObject ore, Block output) {
		ResourceLocation explicit = resource(string(ore, "material", ""));
		if (explicit != null) return explicit;
		return inferMaterial(root, blockTags(output)).material;
	}

	static ResourceLocation placementChannel(JsonObject rule) {
		ResourceLocation explicit = resource(string(rule, "placement_channel", ""));
		if (explicit != null) return explicit;
		JsonObject pattern = rule.has("pattern") && rule.get("pattern").isJsonObject()
				? rule.getAsJsonObject("pattern") : null;
		if (pattern != null) {
			ResourceLocation type = resource(string(pattern, "type", ""));
			if (type != null) return type;
		}
		String legacy = string(rule, "pattern", "");
		ResourceLocation type = legacy.indexOf(':') >= 0 ? resource(legacy) : null;
		return type != null ? type : STANDARD;
	}

	static Inference inferMaterial(Iterable<String> blockTags) {
		JsonObject root = new JsonObject();
		root.add(OreMaterialGroups.SECTION, OreMaterialGroups.defaults());
		return inferMaterial(root, blockTags);
	}

	static Inference inferMaterial(JsonObject root, Iterable<String> blockTags) {
		OreMaterialGroups.Inference inferred = OreMaterialGroups.infer(root, blockTags);
		return new Inference(inferred.material, inferred.reviewRequired, inferred.names);
	}

	static String key(ResourceLocation material, ResourceLocation domain) {
		return material.toString() + "|" + domain.toString();
	}

	private static Map<String, Group> discover(JsonObject root) {
		Map<String, Group> groups = new LinkedHashMap<>();
		boolean manageVanillaOres = bool(root, "manage_vanilla_ores", false);
		JsonObject ores = object(root, "ores");
		for (Entry<String, JsonElement> oreEntry : ores.entrySet()) {
			if (!oreEntry.getValue().isJsonObject()) continue;
			JsonObject ore = oreEntry.getValue().getAsJsonObject();
			boolean oreActive = bool(ore, "enabled", true)
					&& (!bool(ore, "native_generation", false) || manageVanillaOres);
			ResourceLocation blockId = resource(string(ore, "block", oreEntry.getKey()));
			Block block = blockId == null ? null : ForgeRegistries.BLOCKS.getValue(blockId);
			if (block == null || block == Blocks.AIR) continue;
			int metadata = integer(ore, "metadata", 0);
			Inference inferred = inferMaterial(root, blockTags(block));
			ResourceLocation material = resource(string(ore, "material", ""));
			boolean materialDeclared = material != null;
			if (material == null) material = inferred.material;
			if (material == null) continue;
			String owner = owner(ore, blockId);
			Role role = role(owner);
			if (role == Role.NONE) continue;
			for (Entry<String, JsonElement> dimension : object(ore, "dimensions").entrySet()) {
				addConfigured(groups, oreEntry.getKey(), owner, blockId, metadata, material,
						resource(dimension.getKey()), dimension.getValue(), inferred, role,
						oreActive, materialDeclared);
			}
			for (Entry<String, JsonElement> selector : object(ore, "dimension_selectors").entrySet()) {
				addConfigured(groups, oreEntry.getKey(), owner, blockId, metadata, material,
						resource(selector.getKey()), selector.getValue(), inferred, role,
						oreActive, materialDeclared);
			}
		}
		addExternalTagCandidates(groups, root);
		return groups;
	}

	private static void addConfigured(Map<String, Group> groups, String sourceId, String owner,
			ResourceLocation blockId, int metadata, ResourceLocation material, ResourceLocation domain,
			JsonElement ruleElement, Inference inferred, Role role, boolean oreActive,
			boolean materialDeclared) {
		if (domain == null || !ruleElement.isJsonObject()) return;
		if (role == Role.NETHER && !"minecraft:the_nether".equals(domain.toString())) return;
		if (role == Role.END && !"minecraft:the_end".equals(domain.toString())) return;
		JsonObject rule = ruleElement.getAsJsonObject();
		boolean active = oreActive && bool(rule, "enabled", true);
		Candidate candidate = new Candidate(sourceId, owner, modName(owner), modVersion(owner), blockId,
				metadata, material, domain, placementChannel(rule), inferred.names, true, active,
				false, role == Role.ENRICHMENT, inferred.reviewRequired, materialDeclared);
		group(groups, material, domain).add(candidate);
	}

	private static void addExternalTagCandidates(Map<String, Group> groups, JsonObject root) {
		if (groups.isEmpty()) return;
		Map<ResourceLocation, List<String>> namesByMaterial = new HashMap<>();
		for (String name : safeBlockTagNames()) {
			Inference inference = inferMaterial(root, Collections.singletonList(name));
			if (inference.material != null && !inference.reviewRequired) {
				namesByMaterial.computeIfAbsent(inference.material, ignored -> new ArrayList<>()).add(name);
			}
		}
		for (Group group : groups.values()) {
			List<String> names = namesByMaterial.get(group.material);
			if (names == null) continue;
			for (String tagName : names) {
				for (Block block : safeTaggedBlocks(tagName)) {
					ResourceLocation id = block == null ? null : ForgeRegistries.BLOCKS.getKey(block);
					if (id == null || block == Blocks.AIR || group.hasBlock(id, 0)) continue;
					String owner = id.getNamespace();
					Role role = role(owner);
					if (role == Role.NONE || role == Role.ENRICHMENT) continue;
					if (role == Role.NETHER && !"minecraft:the_nether".equals(group.domain.toString())) continue;
					if (role == Role.END && !"minecraft:the_end".equals(group.domain.toString())) continue;
					String sourceId = "external/" + id.toString();
					group.add(new Candidate(sourceId, owner, modName(owner), modVersion(owner), id,
							0, group.material, group.domain, STANDARD,
							Collections.singletonList(tagName), loaded(owner), false, true, false, false,
							false));
				}
			}
		}
	}

	static JsonObject createPolicy(Group group, boolean existingWorld) {
		JsonObject policy = new JsonObject();
		policy.addProperty("material", group.material.toString());
		policy.addProperty("domain", group.domain.toString());
		Candidate preferred = preferred(group);
		boolean automatic = !existingWorld && preferred != null && highConfidence(group);
		policy.addProperty("mode", automatic ? MODE_CONSOLIDATED : MODE_SEPARATE);
		policy.addProperty("output_mode", automatic ? OUTPUT_BALANCED : OUTPUT_CUSTOM);
		JsonObject outputs = new JsonObject();
		for (Candidate candidate : group.outputs()) {
			if (candidate.external) continue;
			outputs.addProperty(candidate.sourceId, 1.0D);
		}
		policy.add("outputs", outputs);
		JsonObject placements = new JsonObject();
		for (ResourceLocation channel : group.channels()) {
			Candidate selected = preferred(group, channel);
			if (selected != null) placements.addProperty(channel.toString(), selected.sourceId);
		}
		policy.add("placement_sources", placements);
		policy.addProperty("review_required", !automatic && group.reviewRequired());
		policy.addProperty("status", automatic ? "consolidated"
				: group.hasExternal() ? "external_generation"
				: group.reviewRequired() ? "review_required" : "separate");
		policy.add("candidates", candidates(group.candidates));
		return policy;
	}

	private static JsonObject refreshPolicy(JsonObject previous, Group group) {
		JsonObject policy = JsonCopies.copy(previous);
		policy.addProperty("material", group.material.toString());
		policy.addProperty("domain", group.domain.toString());
		if (!MODE_CONSOLIDATED.equals(string(policy, "mode", MODE_SEPARATE))) {
			policy.addProperty("mode", MODE_SEPARATE);
		}
		policy.addProperty("output_mode", outputMode(policy));
		if (!policy.has("outputs") || !policy.get("outputs").isJsonObject()) {
			JsonObject outputs = new JsonObject();
			for (Candidate candidate : group.outputs()) if (!candidate.external) {
				outputs.addProperty(candidate.sourceId, 1.0D);
			}
			policy.add("outputs", outputs);
		}
		if (!policy.has("placement_sources") || !policy.get("placement_sources").isJsonObject()) {
			policy.add("placement_sources", new JsonObject());
		}
		List<Candidate> merged = readCandidates(previous, group);
		policy.add("candidates", candidates(merged));
		String status = status(policy, merged);
		policy.addProperty("status", status);
		policy.addProperty("review_required", "review_required".equals(status)
				|| bool(previous, "review_required", false));
		return policy;
	}

	private static String outputMode(JsonObject policy) {
		String configured = string(policy, "output_mode", "");
		if (OUTPUT_BALANCED.equals(configured) || OUTPUT_SINGLE.equals(configured)
				|| OUTPUT_CUSTOM.equals(configured)) return configured;
		Map<String, Double> outputs = decimalMap(policy, "outputs");
		if (outputs.size() <= 1) return OUTPUT_SINGLE;
		double first = outputs.values().iterator().next();
		for (double value : outputs.values()) {
			if (Double.compare(first, value) != 0) return OUTPUT_CUSTOM;
		}
		return OUTPUT_BALANCED;
	}

	private static String status(JsonObject policy, List<Candidate> candidates) {
		Map<String, Candidate> current = new HashMap<>();
		boolean external = false;
		boolean review = false;
		for (Candidate candidate : candidates) {
			current.put(candidate.sourceId, candidate);
			external |= candidate.external && candidate.loaded;
			review |= candidate.reviewRequired;
		}
		Map<String, String> outputs = stringMap(policy, "outputs");
		Map<String, String> placements = stringMap(policy, "placement_sources");
		if (MODE_CONSOLIDATED.equals(string(policy, "mode", MODE_SEPARATE))
				&& (outputs.isEmpty() || placements.isEmpty())) return "missing_source";
		for (String selected : outputs.keySet()) {
			Candidate candidate = current.get(selected);
			if (candidate == null || !candidate.loaded) return "missing_source";
		}
		for (String selected : placements.values()) {
			Candidate candidate = current.get(selected);
			if (candidate == null || !candidate.loaded || !candidate.active) return "missing_source";
		}
		if (external) return "external_generation";
		if (review || bool(policy, "review_required", false)) return "review_required";
		return MODE_CONSOLIDATED.equals(string(policy, "mode", MODE_SEPARATE))
				? "consolidated" : "separate";
	}

	private static List<Candidate> readCandidates(JsonObject policy, Group current) {
		Map<String, Candidate> merged = new LinkedHashMap<>();
		if (policy.has("candidates") && policy.get("candidates").isJsonArray()) {
			for (JsonElement element : policy.getAsJsonArray("candidates")) {
				Candidate candidate = candidate(element, false);
				if (candidate != null) merged.put(candidate.identity(), candidate);
			}
		}
		if (current != null) {
			for (Candidate candidate : current.candidates) merged.put(candidate.identity(), candidate);
		}
		List<Candidate> result = new ArrayList<>();
		Set<String> currentIds = new LinkedHashSet<>();
		if (current != null) for (Candidate candidate : current.candidates) currentIds.add(candidate.identity());
		for (Candidate candidate : merged.values()) {
			result.add(currentIds.contains(candidate.identity()) ? candidate : candidate.missing());
		}
		result.sort(CANDIDATE_ORDER);
		return Collections.unmodifiableList(result);
	}

	private static JsonArray candidates(List<Candidate> values) {
		JsonArray result = new JsonArray();
		List<Candidate> sorted = new ArrayList<>(values);
		sorted.sort(CANDIDATE_ORDER);
		for (Candidate candidate : sorted) result.add(candidate.toJson());
		return result;
	}

	private static List<Candidate> immutableCandidates(List<Candidate> values) {
		List<Candidate> copy = new ArrayList<>(values);
		copy.sort(CANDIDATE_ORDER);
		return Collections.unmodifiableList(copy);
	}

	private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator
			.comparing((Candidate candidate) -> candidate.owner)
			.thenComparing(candidate -> candidate.sourceId)
			.thenComparing(candidate -> candidate.channel.toString());

	private static Candidate candidate(JsonElement element, boolean defaultLoaded) {
		if (element == null || !element.isJsonObject()) return null;
		JsonObject json = element.getAsJsonObject();
		ResourceLocation block = resource(string(json, "registry_id", ""));
		ResourceLocation material = resource(string(json, "material", ""));
		ResourceLocation domain = resource(string(json, "domain", ""));
		ResourceLocation channel = resource(string(json, "placement_channel", ""));
		if (block == null || material == null || domain == null || channel == null) return null;
		List<String> names = strings(json.get("block_tags"));
		return new Candidate(string(json, "source_id", ""), string(json, "owner", block.getNamespace()),
				string(json, "owner_name", ""), string(json, "owner_version", ""), block,
				integer(json, "metadata", 0), material, domain, channel, names,
				bool(json, "loaded", defaultLoaded), bool(json, "placement_active", defaultLoaded),
				bool(json, "external", false), bool(json, "enrichment", false),
				bool(json, "review_required", false), bool(json, "material_declared", false));
	}

	private static boolean highConfidence(Group group) {
		Map<String, Set<String>> tagsByOwner = new HashMap<>();
		for (Candidate candidate : group.configured()) {
			if (!candidate.loaded || candidate.enrichment) continue;
			tagsByOwner.computeIfAbsent(candidate.owner, ignored -> new LinkedHashSet<>())
					.addAll(candidate.blockTags);
		}
		return MmdOreConflictCatalog.automaticallyBalance(tagsByOwner);
	}

	private static Candidate preferred(Group group) {
		for (String owner : MmdOreConflictCatalog.priority(group.material.getPath())) {
			for (Candidate candidate : group.configured()) if (owner.equals(candidate.owner)) return candidate;
		}
		for (Candidate candidate : group.configured()) {
			if (MmdOreConflictCatalog.ordinary(candidate.owner)) return candidate;
		}
		return group.configured().isEmpty() ? null : group.configured().get(0);
	}

	private static Candidate preferred(Group group, ResourceLocation channel) {
		Candidate preferred = preferred(group);
		if (preferred != null && channel.equals(preferred.channel)) return preferred;
		for (Candidate candidate : group.configured()) if (channel.equals(candidate.channel)) return candidate;
		return null;
	}

	private static Role role(String owner) {
		if (MmdOreConflictCatalog.enrichment(owner)) return Role.ENRICHMENT;
		if ("basesciences".equals(owner)) return Role.NONE;
		if (MmdOreConflictCatalog.netherOnly(owner)) return Role.NETHER;
		if (MmdOreConflictCatalog.endOnly(owner)) return Role.END;
		if (MmdOreConflictCatalog.ordinary(owner) || "minecraft".equals(owner) || "orespawn".equals(owner)) {
			return Role.ORDINARY;
		}
		return Role.ORDINARY;
	}

	private static Group group(Map<String, Group> groups, ResourceLocation material, ResourceLocation domain) {
		String key = key(material, domain);
		return groups.computeIfAbsent(key, ignored -> new Group(material, domain));
	}

	private static String owner(JsonObject ore, ResourceLocation block) {
		String owner = string(ore, "source_provider", string(ore, "source_mod", block.getNamespace()));
		return owner.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean loaded(String owner) {
		if ("minecraft".equals(owner) || "orespawn".equals(owner)) return true;
		try { return ModList.get().isLoaded(owner); }
		catch (Throwable ignored) { return false; }
	}

	private static String modName(String owner) {
		return owner;
	}

	private static String modVersion(String owner) {
		return "";
	}

	private static List<String> blockTags(Block block) {
		List<String> result = new ArrayList<>();
		for (Entry<ResourceLocation, ?> entry : tagMap().entrySet()) {
			if (OreMaterialGroups.validTag(entry.getKey().toString())
					&& tagMembers(entry.getValue()).contains(block)) result.add(entry.getKey().toString());
		}
		Collections.sort(result);
		return result;
	}

	private static List<String> safeBlockTagNames() {
		List<String> result = new ArrayList<>();
		for (ResourceLocation id : tagMap().keySet()) {
			if (OreMaterialGroups.validTag(id.toString())) result.add(id.toString());
		}
		Collections.sort(result);
		return result;
	}

	private static List<Block> safeTaggedBlocks(String name) {
		Object tag = tagMap().get(new ResourceLocation(name));
		return tag == null ? Collections.emptyList() : new ArrayList<>(tagMembers(tag));
	}

	private static Map<ResourceLocation, TagKey<Block>> tagMap() {
		Map<ResourceLocation, TagKey<Block>> result = new LinkedHashMap<>();
		net.minecraftforge.registries.tags.ITagManager<Block> tags = ForgeRegistries.BLOCKS.tags();
		if (tags != null) tags.getTagNames().forEach(key -> result.put(key.location(), key));
		return result;
	}

	@SuppressWarnings("unchecked")
	private static java.util.Collection<Block> tagMembers(Object tag) {
		net.minecraftforge.registries.tags.ITagManager<Block> tags = ForgeRegistries.BLOCKS.tags();
		if (tags == null) return Collections.emptyList();
		List<Block> result = new ArrayList<>();
		tags.getTag((TagKey<Block>) tag).forEach(result::add);
		return result;
	}

	private static JsonObject object(JsonObject root, String key) {
		if (root.has(key) && root.get(key).isJsonObject()) return root.getAsJsonObject(key);
		JsonObject result = new JsonObject();
		root.add(key, result);
		return result;
	}

	private static ResourceLocation resource(String value) {
		try { return value == null || value.trim().isEmpty() ? null : new ResourceLocation(value); }
		catch (RuntimeException ignored) { return null; }
	}

	private static Map<String, String> stringMap(JsonObject root, String key) {
		if (!root.has(key) || !root.get(key).isJsonObject()) return Collections.emptyMap();
		Map<String, String> result = new LinkedHashMap<>();
		for (Entry<String, JsonElement> entry : root.getAsJsonObject(key).entrySet()) {
			try { result.put(entry.getKey(), entry.getValue().getAsString()); }
			catch (RuntimeException ignored) { }
		}
		return Collections.unmodifiableMap(result);
	}

	private static Map<String, Double> decimalMap(JsonObject root, String key) {
		if (!root.has(key) || !root.get(key).isJsonObject()) return Collections.emptyMap();
		Map<String, Double> result = new LinkedHashMap<>();
		for (Entry<String, JsonElement> entry : root.getAsJsonObject(key).entrySet()) {
			try {
				double value = entry.getValue().getAsDouble();
				if (Double.isFinite(value) && value > 0.0D) result.put(entry.getKey(), value);
			} catch (RuntimeException ignored) { }
		}
		return Collections.unmodifiableMap(result);
	}

	private static List<String> strings(JsonElement element) {
		if (element == null || !element.isJsonArray()) return Collections.emptyList();
		List<String> result = new ArrayList<>();
		for (JsonElement value : element.getAsJsonArray()) {
			try { result.add(value.getAsString()); }
			catch (RuntimeException ignored) { }
		}
		return Collections.unmodifiableList(result);
	}

	private static String string(JsonObject root, String key, String fallback) {
		try { return root.has(key) ? root.get(key).getAsString() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static boolean bool(JsonObject root, String key, boolean fallback) {
		try { return root.has(key) ? root.get(key).getAsBoolean() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private static int integer(JsonObject root, String key, int fallback) {
		try { return root.has(key) ? root.get(key).getAsInt() : fallback; }
		catch (RuntimeException ignored) { return fallback; }
	}

	private enum Role { ORDINARY, NETHER, END, ENRICHMENT, NONE }

	static final class Inference {
		final ResourceLocation material;
		final boolean reviewRequired;
		final List<String> names;

		Inference(ResourceLocation material, boolean reviewRequired, List<String> names) {
			this.material = material;
			this.reviewRequired = reviewRequired;
			this.names = Collections.unmodifiableList(new ArrayList<>(names));
		}
	}

	static final class Snapshot {
		private final List<GroupView> groups;

		Snapshot(List<GroupView> groups) {
			this.groups = Collections.unmodifiableList(new ArrayList<>(groups));
		}

		List<GroupView> groups() { return groups; }
	}

	static final class GroupView {
		final String key;
		final ResourceLocation material;
		final ResourceLocation domain;
		final String displayName;
		final List<String> blockTagEntries;
		final boolean curated;
		final String mode;
		final String outputMode;
		final String status;
		final List<Candidate> candidates;
		final Map<String, Double> outputs;
		final Map<String, String> placements;

		GroupView(String key, ResourceLocation material, ResourceLocation domain,
				String displayName, List<String> blockTagEntries, boolean curated,
				String mode, String outputMode, String status, List<Candidate> candidates, Map<String, Double> outputs,
				Map<String, String> placements) {
			this.key = key;
			this.material = material;
			this.domain = domain;
			this.displayName = displayName;
			this.blockTagEntries = Collections.unmodifiableList(new ArrayList<>(blockTagEntries));
			this.curated = curated;
			this.mode = mode;
			this.outputMode = outputMode;
			this.status = status;
			this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
			this.outputs = Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
			this.placements = Collections.unmodifiableMap(new LinkedHashMap<>(placements));
		}
	}

	static final class Candidate {
		final String sourceId;
		final String owner;
		final String ownerName;
		final String ownerVersion;
		final ResourceLocation block;
		final int metadata;
		final ResourceLocation material;
		final ResourceLocation domain;
		final ResourceLocation channel;
		final List<String> blockTags;
		final boolean loaded;
		final boolean active;
		final boolean external;
		final boolean enrichment;
		final boolean reviewRequired;
		final boolean materialDeclared;

		Candidate(String sourceId, String owner, String ownerName, String ownerVersion,
				ResourceLocation block, int metadata, ResourceLocation material, ResourceLocation domain,
				ResourceLocation channel, List<String> blockTags, boolean loaded, boolean active,
				boolean external, boolean enrichment, boolean reviewRequired, boolean materialDeclared) {
			this.sourceId = sourceId;
			this.owner = owner;
			this.ownerName = ownerName;
			this.ownerVersion = ownerVersion;
			this.block = block;
			this.metadata = metadata;
			this.material = material;
			this.domain = domain;
			this.channel = channel;
			this.blockTags = Collections.unmodifiableList(new ArrayList<>(blockTags));
			this.loaded = loaded;
			this.active = active;
			this.external = external;
			this.enrichment = enrichment;
			this.reviewRequired = reviewRequired;
			this.materialDeclared = materialDeclared;
		}

		String identity() { return sourceId + '|' + channel.toString(); }

		String outputIdentity() { return block.toString() + '|' + metadata; }

		Candidate missing() {
			return new Candidate(sourceId, owner, ownerName, ownerVersion, block, metadata, material,
					domain, channel, blockTags, false, false, external, enrichment, reviewRequired,
					materialDeclared);
		}

		JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("source_id", sourceId);
			json.addProperty("owner", owner);
			json.addProperty("owner_name", ownerName);
			json.addProperty("owner_version", ownerVersion);
			json.addProperty("registry_id", block.toString());
			json.addProperty("metadata", metadata);
			json.addProperty("material", material.toString());
			json.addProperty("domain", domain.toString());
			json.addProperty("placement_channel", channel.toString());
			JsonArray tags = new JsonArray();
			for (String name : blockTags) tags.add(new JsonPrimitive(name));
			json.add("block_tags", tags);
			json.addProperty("loaded", loaded);
			json.addProperty("placement_active", active);
			json.addProperty("external", external);
			json.addProperty("enrichment", enrichment);
			json.addProperty("review_required", reviewRequired);
			json.addProperty("material_declared", materialDeclared);
			return json;
		}
	}

	static final class Group {
		final ResourceLocation material;
		final ResourceLocation domain;
		final List<Candidate> candidates = new ArrayList<>();

		Group(ResourceLocation material, ResourceLocation domain) {
			this.material = material;
			this.domain = domain;
		}

		void add(Candidate candidate) {
			for (Candidate value : candidates) if (value.identity().equals(candidate.identity())) return;
			candidates.add(candidate);
		}

		boolean hasBlock(ResourceLocation block, int metadata) {
			for (Candidate value : candidates) {
				if (value.block.equals(block) && value.metadata == metadata) return true;
			}
			return false;
		}

		List<Candidate> managed() {
			List<Candidate> result = new ArrayList<>();
			for (Candidate value : candidates) if (!value.external && !value.enrichment) result.add(value);
			result.sort(CANDIDATE_ORDER);
			return result;
		}

		List<Candidate> configured() {
			List<Candidate> result = new ArrayList<>();
			for (Candidate value : managed()) if (value.active) result.add(value);
			return result;
		}

		List<Candidate> outputs() {
			Map<String, Candidate> unique = new LinkedHashMap<>();
			for (Candidate value : candidates) {
				if (value.enrichment || !value.loaded) continue;
				String key = value.outputIdentity();
				Candidate previous = unique.get(key);
				if (previous == null || (!previous.active && value.active)
						|| (previous.external && !value.external)) unique.put(key, value);
			}
			List<Candidate> result = new ArrayList<>(unique.values());
			result.sort(CANDIDATE_ORDER);
			return result;
		}

		Set<ResourceLocation> channels() {
			Set<ResourceLocation> result = new LinkedHashSet<>();
			for (Candidate value : configured()) result.add(value.channel);
			return result;
		}

		boolean hasExternal() {
			for (Candidate value : candidates) if (value.external) return true;
			return false;
		}

		boolean reviewRequired() {
			for (Candidate value : candidates) if (value.reviewRequired) return true;
			return hasManagedConflict() && !highConfidence(this);
		}

		private boolean hasManagedConflict() {
			List<Candidate> managed = managed();
			Set<String> outputs = new LinkedHashSet<>();
			Map<ResourceLocation, Set<String>> ownersByChannel = new LinkedHashMap<>();
			for (Candidate value : managed) {
				outputs.add(value.outputIdentity());
				if (value.active) ownersByChannel.computeIfAbsent(value.channel,
						ignored -> new LinkedHashSet<>()).add(value.owner);
			}
			if (outputs.size() > 1) return true;
			for (Set<String> owners : ownersByChannel.values()) if (owners.size() > 1) return true;
			return false;
		}

	}
}
