package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.Lifecycle;
import java.util.EnumSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.DimensionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.MinecraftTestBootstrap;

class ExtendedWorldHeightTest {
	@BeforeAll
	static void bootstrap() {
		MinecraftTestBootstrap.registerVanilla();
	}

	@AfterEach
	void clearDimensionBounds() {
		GeomeConfig.clearWorldHeights();
	}

	@Test
	void configuredRockCanGenerateBelowTheVanillaFloor() {
		BakedGeomeConfig config = bake(-128, -65);
		assertEquals(Blocks.BASALT, config.pickRock(0, RockFamily.METAMORPHIC, -96, 0).getBlock(),
				"the configured -128..-65 range must not be clamped to -64");
		assertEquals(Blocks.STONE, config.pickRock(0, RockFamily.METAMORPHIC, -129, 0).getBlock());
		assertEquals(Blocks.STONE, config.pickRock(0, RockFamily.METAMORPHIC, -64, 0).getBlock());
	}

	@Test
	void configuredRockCanGenerateAboveTheVanillaCeiling() {
		BakedGeomeConfig config = bake(320, 511);
		assertEquals(Blocks.BASALT, config.pickRock(0, RockFamily.METAMORPHIC, 400, 0).getBlock(),
				"the configured 320..511 range must not be clamped to 319");
	}

	@Test
	void actualDimensionHeightIsDetectedAndSurvivesProfileRebaking() {
		BakedGeomeConfig ordinary = bake(-128, 511);
		BakedGeomeConfig extended = GeomeConfig.baked(Level.OVERWORLD,
				LevelHeightAccessor.create(-128, 640));
		assertNotSame(ordinary, extended);
		assertTrue(extended.hasReplacementAt(0, -128));
		assertTrue(extended.hasReplacementAt(0, 511));
		assertFalse(extended.hasReplacementAt(0, -129));
		assertFalse(extended.hasReplacementAt(0, 512));
		assertSame(extended, GeomeConfig.baked(Level.OVERWORLD,
				LevelHeightAccessor.create(-128, 640)));
		assertTrue(bake(-128, 511).hasReplacementAt(0, -128));
		GeomeConfig.clearWorldHeights();
		assertFalse(bake(-128, 511).hasReplacementAt(0, -128),
				"the next server must not inherit the previous world's height cache");
	}

	@Test
	void narrowerDimensionsKeepTheExistingPickerAndCannotChangeTheOverworld() {
		BakedGeomeConfig ordinary = bake(-128, 511);
		assertSame(ordinary, GeomeConfig.baked(Level.OVERWORLD,
				LevelHeightAccessor.create(-64, 384)));
		GeomeConfig.bindWorldHeight(Level.NETHER, -256, 768);
		assertSame(ordinary, GeomeConfig.baked(Level.OVERWORLD));
		assertSame(ordinary, ordinary.withWorldHeight(0, 128));
	}

	@Test
	void everyOriginalHeightKeepsTheSameChoicesWhenTablesAreExtended() {
		JsonObject root = profile(-128, 511);
		JsonObject rocks = root.getAsJsonObject("rocks");
		JsonObject tuff = rocks.getAsJsonObject("minecraft:basalt").deepCopy();
		tuff.addProperty("depth_peak", 80);
		tuff.addProperty("weight", 0.3);
		rocks.add("minecraft:tuff", tuff);
		JsonObject granite = tuff.deepCopy();
		granite.addProperty("family", "igneous_intrusive");
		rocks.add("minecraft:granite", granite);
		GeomeConfig.applyWorldProfile(WorldGeologyProfile.recommended(false).withRoot(root));
		BakedGeomeConfig ordinary = GeomeConfig.baked(Level.OVERWORLD);
		BakedGeomeConfig extended = ordinary.withWorldHeight(-128, 512);
		for (int y = -64; y <= 319; y++) {
			for (int bucket = 0; bucket < 256; bucket++) {
				assertEquals(ordinary.pickFamily(0, y, bucket), extended.pickFamily(0, y, bucket));
				for (RockFamily family : RockFamily.values()) {
					assertEquals(ordinary.pickRock(0, family, y, bucket),
							extended.pickRock(0, family, y, bucket));
				}
			}
		}
	}

	@Test
	void depthPreferencesAreEvaluatedAtTheRealExtendedHeight() {
		JsonObject root = profile(-128, 511);
		JsonObject basalt = root.getAsJsonObject("rocks").getAsJsonObject("minecraft:basalt");
		basalt.addProperty("depth_peak", -112);
		basalt.addProperty("depth_spread", 8);
		JsonObject tuff = basalt.deepCopy();
		tuff.addProperty("depth_peak", -16);
		root.getAsJsonObject("rocks").add("minecraft:tuff", tuff);
		GeomeConfig.applyWorldProfile(WorldGeologyProfile.recommended(false).withRoot(root));
		BakedGeomeConfig config = GeomeConfig.baked(Level.OVERWORLD, LevelHeightAccessor.create(-128, 640));
		int deepBasalt = 0;
		int upperTuff = 0;
		for (int bucket = 0; bucket < 256; bucket++) {
			if (config.pickRock(0, RockFamily.METAMORPHIC, -112, bucket).is(Blocks.BASALT)) deepBasalt++;
			if (config.pickRock(0, RockFamily.METAMORPHIC, -16, bucket).is(Blocks.TUFF)) upperTuff++;
		}
		assertTrue(deepBasalt > 210, "deep preference was flattened at vanilla's floor: " + deepBasalt);
		assertTrue(upperTuff > 210, "the shallower preference must remain independent: " + upperTuff);
	}

	@Test
	void uncoveredDepthKeepsNativeDeepslateWithoutMarkingTheChunkChanged() {
		BakedGeomeConfig config = bake(-64, 319).withWorldHeight(-128, 512);
		ProtoChunk chunk = chunkWithDeepslate(-96);
		replace(config, chunk);
		assertTrue(chunk.getBlockState(new BlockPos(-32, -96, 48)).is(Blocks.DEEPSLATE));
		assertFalse(chunk.isUnsaved());
		Biome biome = chunk.getNoiseBiome(-8, -24, 12).value();
		assertNull(new GeomeGeology(0L, config)
				.sampleColumn(biome, Biomes.PLAINS.location(), -32, 48).familyAt(-96));
	}

	@Test
	void realChunkGenerationAndColumnSamplingAgreeBelowAndAboveVanillaHeights() {
		for (int y : new int[] { -128, -96, 400, 511 }) {
			BakedGeomeConfig config = bake(-128, 511).withWorldHeight(-128, 512);
			ProtoChunk chunk = chunkWithDeepslate(y);
			replace(config, chunk);
			assertTrue(chunk.getBlockState(new BlockPos(-32, y, 48)).is(Blocks.BASALT), "Y=" + y);
			GeomeGeology geology = new GeomeGeology(0L, config);
			Biome biome = chunk.getNoiseBiome(-8, Math.floorDiv(y, 4), 12).value();
			assertTrue(geology.sampleColumn(biome, Biomes.PLAINS.location(), -32, 48).rockAt(y).is(Blocks.BASALT));
			assertTrue(chunk.getBlockState(new BlockPos(-31, y, 48)).is(Blocks.WATER));
			assertTrue(chunk.getBlockState(new BlockPos(-30, y, 48)).is(Blocks.BEDROCK));
		}
	}

	@Test
	void originalSmoothSkyAlgorithmAlsoUsesExtendedBounds() {
		JsonObject root = profile(-128, -65);
		root.getAsJsonObject("formations").addProperty("algorithm", "sky_v1");
		GeomeConfig.applyWorldProfile(WorldGeologyProfile.recommended(false).withRoot(root));
		BakedGeomeConfig config = GeomeConfig.baked(Level.OVERWORLD, LevelHeightAccessor.create(-128, 640));
		ProtoChunk chunk = chunkWithDeepslate(-65);
		replace(config, chunk);
		assertTrue(chunk.getBlockState(new BlockPos(-32, -65, 48)).is(Blocks.BASALT));
	}

	@Test
	void omittedEndpointsUseTheActualBoundsOfACustomDimension() {
		JsonObject root = profile(-128, 511);
		JsonObject basalt = root.getAsJsonObject("rocks").getAsJsonObject("minecraft:basalt");
		basalt.remove("min_y");
		basalt.remove("max_y");
		ResourceKey<Level> custom = ResourceKey.create(Registries.DIMENSION,
				ResourceLocation.parse("test:very_deep"));
		JsonArray dimensions = new JsonArray();
		dimensions.add("minecraft:overworld");
		dimensions.add(custom.location().toString());
		basalt.add("dimensions", dimensions);
		root.getAsJsonObject("terrain_dimensions").add(custom.location().toString(),
				root.getAsJsonObject("terrain_dimensions").get("minecraft:overworld").deepCopy());
		GeomeConfig.applyWorldProfile(WorldGeologyProfile.recommended(false).withRoot(root));
		BakedGeomeConfig config = GeomeConfig.baked(custom,
				LevelHeightAccessor.create(DimensionType.MIN_Y, 4064));
		assertTrue(config.hasReplacementAt(0, DimensionType.MIN_Y));
		assertTrue(config.hasReplacementAt(0, DimensionType.MAX_Y));
		assertTrue(config.pickRock(0, RockFamily.METAMORPHIC,
				DimensionType.MIN_Y, 7).is(Blocks.BASALT));
		assertFalse(GeomeConfig.baked(Level.OVERWORLD).hasReplacementAt(0, DimensionType.MIN_Y));
	}

	@Test
	void zeroFamilyOrGeomeWeightDoesNotMakeFallbackStoneReplaceNativeRock() {
		for (boolean familyDisabled : new boolean[] { true, false }) {
			JsonObject root = profile(-128, 511);
			if (familyDisabled) {
				root.getAsJsonObject("geomes").getAsJsonObject("test:deep")
						.getAsJsonObject("families").addProperty("metamorphic", 0);
			} else {
				JsonObject weights = new JsonObject();
				weights.addProperty("test:deep", 0);
				root.getAsJsonObject("rocks").getAsJsonObject("minecraft:basalt").add("geomes", weights);
			}
			GeomeConfig.applyWorldProfile(WorldGeologyProfile.recommended(false).withRoot(root));
			BakedGeomeConfig config = GeomeConfig.baked(Level.OVERWORLD,
					LevelHeightAccessor.create(-128, 640));
			assertFalse(config.hasReplacementAt(0, -96));
			ProtoChunk chunk = chunkWithDeepslate(-96);
			replace(config, chunk);
			assertTrue(chunk.getBlockState(new BlockPos(-32, -96, 48)).is(Blocks.DEEPSLATE));
			assertFalse(chunk.isUnsaved());
		}
	}

	@Test
	void parallelFirstUseOnlyPublishesCompleteHeightTables() throws Exception {
		bake(-128, 511);
		CountDownLatch start = new CountDownLatch(1);
		try (var workers = Executors.newFixedThreadPool(8)) {
			var results = new ArrayList<Future<BakedGeomeConfig>>();
			for (int i = 0; i < 32; i++) {
				results.add(workers.submit(() -> {
					assertTrue(start.await(10, TimeUnit.SECONDS));
					return GeomeConfig.baked(Level.OVERWORLD, LevelHeightAccessor.create(-128, 640));
				}));
			}
			start.countDown();
			for (Future<BakedGeomeConfig> result : results) {
				BakedGeomeConfig config = result.get(10, TimeUnit.SECONDS);
				assertTrue(config.hasReplacementAt(0, -128));
				assertTrue(config.hasReplacementAt(0, 511));
			}
		}
	}

	@Test
	void disablingEveryRockKeepsNativeTerrainAndOnlyRetainsASamplerFallback() {
		for (String algorithm : new String[] { "stable_layers", "sky_v1" }) {
			JsonObject root = profile(-128, 511);
			root.getAsJsonObject("rocks").getAsJsonObject("minecraft:basalt").addProperty("enabled", false);
			root.getAsJsonObject("formations").addProperty("algorithm", algorithm);
			GeomeConfig.applyWorldProfile(WorldGeologyProfile.recommended(false).withRoot(root));
			BakedGeomeConfig config = GeomeConfig.baked(Level.OVERWORLD,
					LevelHeightAccessor.create(-128, 640));
			ProtoChunk chunk = chunkWithDeepslate(-32);
			replace(config, chunk);
			assertTrue(chunk.getBlockState(new BlockPos(-32, -32, 48)).is(Blocks.DEEPSLATE), algorithm);
			assertFalse(chunk.isUnsaved());
			assertTrue(config.pickRock(0, RockFamily.SEDIMENTARY, -32, 0).is(Blocks.STONE));
			assertTrue(config.isOreReplaceable(Blocks.STONE.defaultBlockState()));
		}
	}

	private static ProtoChunk chunkWithDeepslate(int y) {
		MappedRegistry<Biome> biomes = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
		biomes.register(Biomes.PLAINS, VanillaRegistries.createLookup().lookupOrThrow(Registries.BIOME)
				.getOrThrow(Biomes.PLAINS).value(), RegistrationInfo.BUILT_IN);
		ProtoChunk chunk = new ProtoChunk(new ChunkPos(-2, 3), UpgradeData.EMPTY,
				LevelHeightAccessor.create(-128, 640), biomes.freeze(), null);
		chunk.setPersistedStatus(ChunkStatus.BIOMES);
		chunk.setBlockState(new BlockPos(-32, y, 48), Blocks.DEEPSLATE.defaultBlockState(), false);
		chunk.setBlockState(new BlockPos(-31, y, 48), Blocks.WATER.defaultBlockState(), false);
		chunk.setBlockState(new BlockPos(-30, y, 48), Blocks.BEDROCK.defaultBlockState(), false);
		Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG));
		chunk.setUnsaved(false);
		return chunk;
	}

	private static void replace(BakedGeomeConfig config, ProtoChunk chunk) {
		BakedTerrainDimension terrain = new BakedTerrainDimension(Level.OVERWORLD, Set.of(), Set.of(),
				Set.of(Blocks.STONE, Blocks.DEEPSLATE));
		new GeomeGeology(0L, config).replaceStoneInChunk(null, chunk, terrain);
	}

	private static BakedGeomeConfig bake(int min, int max) {
		GeomeConfig.applyWorldProfile(WorldGeologyProfile.recommended(false).withRoot(profile(min, max)));
		return GeomeConfig.baked(Level.OVERWORLD);
	}

	private static JsonObject profile(int min, int max) {
		JsonObject root = WorldGeologyProfile.recommended(false).rootCopy();
		JsonObject geomes = new JsonObject();
		JsonObject geome = new JsonObject();
		geome.addProperty("base", 1.0);
		JsonObject families = new JsonObject();
		families.addProperty("metamorphic", 1.0);
		geome.add("families", families);
		geomes.add("test:deep", geome);
		root.add("geomes", geomes);
		root.add("biomes", new JsonObject());
		root.add("biome_dictionary", new JsonObject());
		JsonObject rocks = new JsonObject();
		JsonObject rock = new JsonObject();
		rock.addProperty("enabled", true);
		rock.addProperty("family", "metamorphic");
		rock.addProperty("min_y", min);
		rock.addProperty("max_y", max);
		rock.addProperty("depth_peak", (min + max) / 2);
		rock.addProperty("weight", 1.0);
		rocks.add("minecraft:basalt", rock);
		root.add("rocks", rocks);
		JsonObject terrain = new JsonObject();
		JsonObject dimension = new JsonObject();
		JsonArray hosts = new JsonArray();
		hosts.add("minecraft:stone");
		hosts.add("minecraft:deepslate");
		dimension.add("host_blocks", hosts);
		terrain.add("minecraft:overworld", dimension);
		root.add("terrain_dimensions", terrain);
		return root;
	}
}
