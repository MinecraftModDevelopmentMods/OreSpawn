package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Random;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;

class OreSpawnOreGenerationTest {
	@Test
	void airExposureCheckRejectsCrossChunkReadsBeforeTouchingTheWorld() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone",
				"moddev", "mc", "orespawn", "worldgen", "OreSpawnOreGeneration.java")),
				StandardCharsets.UTF_8);
		int methodStart = source.indexOf("private boolean isAir(int x, int y, int z)");
		int methodEnd = source.indexOf("\n\t\t}", methodStart);
		String method = source.substring(methodStart, methodEnd);
		int boundsCheck = method.indexOf("if (!insideChunk(chunk, x, y, z))");
		int worldRead = method.indexOf("world.getBlockState(airCursor)");

		assertTrue(boundsCheck >= 0, "air exposure must reject positions outside the active chunk");
		assertTrue(boundsCheck < worldRead, "the chunk bound must be checked before any world read");
	}

	@Test
	void chunkBoundsIncludeAllFourEdgesAndRejectEveryNeighbour() {
		ChunkPos chunk = new ChunkPos(7, -4);
		int minX = chunk.getXStart();
		int maxX = chunk.getXEnd();
		int minZ = chunk.getZStart();
		int maxZ = chunk.getZEnd();

		assertTrue(OreSpawnOreGeneration.insideChunk(chunk, minX, 64, minZ));
		assertTrue(OreSpawnOreGeneration.insideChunk(chunk, minX, 64, maxZ));
		assertTrue(OreSpawnOreGeneration.insideChunk(chunk, maxX, 64, minZ));
		assertTrue(OreSpawnOreGeneration.insideChunk(chunk, maxX, 64, maxZ));
		assertFalse(OreSpawnOreGeneration.insideChunk(chunk, minX - 1, 64, minZ));
		assertFalse(OreSpawnOreGeneration.insideChunk(chunk, maxX + 1, 64, minZ));
		assertFalse(OreSpawnOreGeneration.insideChunk(chunk, minX, 64, minZ - 1));
		assertFalse(OreSpawnOreGeneration.insideChunk(chunk, minX, 64, maxZ + 1));
		assertFalse(OreSpawnOreGeneration.insideChunk(chunk, minX, -1, minZ));
		assertFalse(OreSpawnOreGeneration.insideChunk(chunk, minX, 256, minZ));
	}

	@Test
	void managedWritesDoNotLeakReusablePositionsIntoForgeTickData() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone",
				"moddev", "mc", "orespawn", "worldgen", "OreSpawnOreGeneration.java")),
				StandardCharsets.UTF_8);
		assertTrue(source.contains("world.setBlockState(cursor.toImmutable(), output, GENERATION_WRITE_FLAGS)"),
				"Forge 1.10 may retain write positions for scheduled ticks");
		assertFalse(source.contains("world.setBlockState(cursor, output, GENERATION_WRITE_FLAGS)"));
	}

	@Test
	void normalGenerationAndRetrogenShareStableExtendedIdentityInputs() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone",
				"moddev", "mc", "orespawn", "worldgen", "OreSpawnOreGeneration.java")),
				StandardCharsets.UTF_8);
		assertTrue(source.contains("generateChunk(level, world, chunk"));
		assertTrue(source.contains("generateChunk(level, null, chunk"));
		assertTrue(source.contains("dimension, worldSeed,\n\t\t\t\tattemptIndex, geologySampler"));
		assertTrue(source.contains("this.chunkX = chunkPos.chunkXPos"));
		assertTrue(source.contains("this.chunkZ = chunkPos.chunkZPos"));
	}

	@Test
	void wholeBodyOutputSelectionIgnoresChunkOrder() {
		ResourceLocation material = new ResourceLocation("orespawn:sulfur");
		ResourceLocation channel = new ResourceLocation("realisticdeposits:district");
		long body = 0x1234ABCD5678EF90L;
		double first = OreSpawnOreGeneration.outputSelectionSample(42L, WorldIds.OVERWORLD,
				material, channel, body);
		double fromAnotherChunk = OreSpawnOreGeneration.outputSelectionSample(42L, WorldIds.OVERWORLD,
				material, channel, body);
		assertEquals(first, fromAnotherChunk);
	}

	@Test
	void ordinaryAttemptsIncludeChunkRuleChannelAndAttemptIdentity() {
		ResourceLocation rule = new ResourceLocation("examplemod:sulfur");
		ResourceLocation channel = new ResourceLocation("orespawn:standard");
		long first = OreSpawnOreGeneration.ordinaryOutputIdentity(new ChunkPos(1, 2), 0, rule, channel);
		assertEquals(first, OreSpawnOreGeneration.ordinaryOutputIdentity(
				new ChunkPos(1, 2), 0, rule, channel));
		assertFalse(first == OreSpawnOreGeneration.ordinaryOutputIdentity(
				new ChunkPos(2, 1), 0, rule, channel));
		assertFalse(first == OreSpawnOreGeneration.ordinaryOutputIdentity(
				new ChunkPos(1, 2), 1, rule, channel));
	}

	@Test
	void placementChannelsAreArbitratedIndependentlyWithoutFallback() {
		ResourceLocation material = new ResourceLocation("orespawn:iron");
		ResourceLocation standard = new ResourceLocation("orespawn:standard");
		ResourceLocation district = new ResourceLocation("realisticdeposits:district");
		ResourceLocation ordinary = new ResourceLocation("basemetals:iron");
		ResourceLocation large = new ResourceLocation("realisticdeposits:iron_district");
		JsonObject root = new JsonObject();
		JsonObject policies = new JsonObject();
		JsonObject policy = new JsonObject();
		policy.addProperty("mode", "consolidated");
		JsonObject placements = new JsonObject();
		placements.addProperty(standard.toString(), ordinary.toString());
		placements.addProperty(district.toString(), large.toString());
		placements.addProperty("missing:channel", "missing:rule");
		policy.add("placement_sources", placements);
		policies.add(OreSourcePolicies.key(material, WorldIds.OVERWORLD), policy);
		root.add(OreSourcePolicies.SECTION, policies);
		Map<ResourceLocation, ResourceLocation> available = new LinkedHashMap<>();
		available.put(ordinary, standard);
		available.put(large, district);

		Map<ResourceLocation, ResourceLocation> selected = OreSpawnOreGeneration
				.selectedPlacementRules(root, material, WorldIds.OVERWORLD, available);
		assertEquals(2, selected.size());
		assertEquals(ordinary, selected.get(standard));
		assertEquals(large, selected.get(district));
		assertFalse(selected.containsKey(new ResourceLocation("missing:channel")));
	}

	@Test
	void selectorPoliciesRemainKeyedByTheirSelectorDomain() {
		ResourceLocation material = new ResourceLocation("orespawn:copper");
		ResourceLocation standard = new ResourceLocation("orespawn:standard");
		ResourceLocation source = new ResourceLocation("basemetals:copper");
		ResourceLocation selector = new ResourceLocation("orespawn:all_except_nether_end");
		JsonObject root = new JsonObject();
		JsonObject policies = new JsonObject();
		JsonObject policy = new JsonObject();
		policy.addProperty("mode", "consolidated");
		JsonObject placements = new JsonObject();
		placements.addProperty(standard.toString(), source.toString());
		policy.add("placement_sources", placements);
		policies.add(OreSourcePolicies.key(material, selector), policy);
		root.add(OreSourcePolicies.SECTION, policies);
		Map<ResourceLocation, ResourceLocation> available = new LinkedHashMap<>();
		available.put(source, standard);

		assertEquals(source, OreSpawnOreGeneration.selectedPlacementRules(
				root, material, selector, available).get(standard));
		assertEquals(null, OreSpawnOreGeneration.selectedPlacementRules(
				root, material, WorldIds.OVERWORLD, available));
	}

	@Test
	void keepSeparatePolicyLeavesEveryPlacementRuleUntouched() {
		ResourceLocation material = new ResourceLocation("orespawn:sulfur");
		JsonObject root = new JsonObject();
		JsonObject policies = new JsonObject();
		JsonObject policy = new JsonObject();
		policy.addProperty("mode", "keep_separate");
		policies.add(OreSourcePolicies.key(material, WorldIds.OVERWORLD), policy);
		root.add(OreSourcePolicies.SECTION, policies);

		assertEquals(null, OreSpawnOreGeneration.selectedPlacementRules(root, material,
				WorldIds.OVERWORLD, Collections.emptyMap()));
	}

	@Test
	void weightedOutputSelectionUsesPositiveRelativeWeights() {
		assertEquals(0, OreSpawnOreGeneration.weightedIndex(0.0D, 1.0D, 3.0D));
		assertEquals(0, OreSpawnOreGeneration.weightedIndex(0.249999D, 1.0D, 3.0D));
		assertEquals(1, OreSpawnOreGeneration.weightedIndex(0.25D, 1.0D, 3.0D));
		assertEquals(1, OreSpawnOreGeneration.weightedIndex(0.999999D, 1.0D, 3.0D));
		assertEquals(-1, OreSpawnOreGeneration.weightedIndex(0.5D, 0.0D, -1.0D));
	}

	@Test
	void stableOutputSelectionRespectsConfiguredWeightDistribution() {
		ResourceLocation material = new ResourceLocation("orespawn:sulfur");
		ResourceLocation channel = new ResourceLocation("orespawn:standard");
		int first = 0;
		for (long identity = 0; identity < 10000; identity++) {
			double sample = OreSpawnOreGeneration.outputSelectionSample(
					42L, WorldIds.OVERWORLD, material, channel, identity);
			if (OreSpawnOreGeneration.weightedIndex(sample, 1.0D, 3.0D) == 0) first++;
		}
		assertTrue(first > 2200 && first < 2800,
				"one-to-three weights should select the first source about one quarter of the time: " + first);
	}

	@Test
	void fixedQuantityDoesNotConsumeRandomState() {
		CountingRandom random = new CountingRandom(0);
		assertEquals(8, OreSpawnOreGeneration.sampleQuantity(random, 8, 8));
		assertEquals(0, random.calls);
	}

	@Test
	void rangedQuantityReachesBothInclusiveBounds() {
		CountingRandom minimum = new CountingRandom(0);
		CountingRandom maximum = new CountingRandom(7);
		assertEquals(4, OreSpawnOreGeneration.sampleQuantity(minimum, 4, 11));
		assertEquals(11, OreSpawnOreGeneration.sampleQuantity(maximum, 4, 11));
		assertEquals(1, minimum.calls);
		assertEquals(1, maximum.calls);
	}

	@Test
	void broadSelectorNeverLeaksIntoNetherOrEnd() {
		assertTrue(OreSpawnOreGeneration.selectorAllows(WorldIds.OVERWORLD));
		assertTrue(OreSpawnOreGeneration.selectorAllows(new ResourceLocation("examplemod:moon")));
		assertFalse(OreSpawnOreGeneration.selectorAllows(WorldIds.NETHER));
		assertFalse(OreSpawnOreGeneration.selectorAllows(WorldIds.END));
	}

	@Test
	void explicitDimensionRulesOverrideSelectorFallbacks() {
		Map<ResourceLocation, String> explicit = new HashMap<>();
		Set<ResourceLocation> configured = new HashSet<>();
		explicit.put(WorldIds.OVERWORLD, "overworld");
		configured.add(WorldIds.OVERWORLD);
		configured.add(new ResourceLocation("examplemod:disabled"));

		assertEquals("overworld", OreSpawnOreGeneration.selectRule(
				explicit, configured, "selector", WorldIds.OVERWORLD));
		assertEquals("selector", OreSpawnOreGeneration.selectRule(explicit, configured, "selector",
				new ResourceLocation("examplemod:moon")));
		assertEquals(null, OreSpawnOreGeneration.selectRule(explicit, configured, "selector",
				new ResourceLocation("examplemod:disabled")));
		assertEquals(null, OreSpawnOreGeneration.selectRule(explicit, configured, "selector", WorldIds.NETHER));
	}

	@Test
	void metadataOnlyReplacementHostsAreValidOreTargets() {
		Map<Block, Double> blocks = new HashMap<>();
		Map<IBlockState, Double> states = new HashMap<>();
		// The parser stores a metadata-qualified host in the exact-state map,
		// independently of the whole-block map.
		states.put(null, 1.0D);

		assertTrue(OreSpawnOreGeneration.hasHostTargets(blocks, states, 0));
		states.clear();
		assertFalse(OreSpawnOreGeneration.hasHostTargets(blocks, states, 0));
	}

	private static final class CountingRandom extends Random {
		private static final long serialVersionUID = 1L;
		private final int result;
		int calls;

		CountingRandom(int result) {
			this.result = result;
		}

		@Override
		public int nextInt(int bound) {
			calls++;
			return Math.min(result, bound - 1);
		}
	}
}
