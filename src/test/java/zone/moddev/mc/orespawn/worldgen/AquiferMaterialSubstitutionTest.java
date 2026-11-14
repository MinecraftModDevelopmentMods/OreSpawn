package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.BitSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.chunk.ChunkSection;
import zone.moddev.mc.orespawn.test.Forge25TestBootstrap;

class AquiferMaterialSubstitutionTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Forge25TestBootstrap.registerVanilla();
	}

	@Test
	void capturesOnlyExactNativeFluidBelowSeaLevel() {
		ChunkPrimer primer = new ChunkPrimer(0, 0, null);
		primer.setBlockState(new net.minecraft.util.math.BlockPos(1, 10, 2), Blocks.WATER.getDefaultState(), false);
		primer.setBlockState(new net.minecraft.util.math.BlockPos(3, 63, 4), Blocks.WATER.getDefaultState(), false);
		primer.setBlockState(new net.minecraft.util.math.BlockPos(5, 64, 6), Blocks.WATER.getDefaultState(), false);
		primer.setBlockState(new net.minecraft.util.math.BlockPos(7, 20, 8), Blocks.LAVA.getDefaultState(), false);

		BitSet mask = AquiferMaterialSubstitution.captureMask(
				primer, Blocks.WATER.getDefaultState(), 64);

		assertEquals(2, mask.cardinality());
		assertTrue(mask.get(AquiferMaterialSubstitution.index(1, 10, 2)));
		assertTrue(mask.get(AquiferMaterialSubstitution.index(3, 63, 4)));
		assertFalse(mask.get(AquiferMaterialSubstitution.index(5, 64, 6)));
		assertFalse(mask.get(AquiferMaterialSubstitution.index(7, 20, 8)));
	}

	@Test
	void primerIndexRoundTripsEveryLocalCoordinate() {
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y : new int[] { 0, 1, 63, 127, 255 }) {
					int index = AquiferMaterialSubstitution.index(x, y, z);
					assertEquals(x, AquiferMaterialSubstitution.localX(index));
					assertEquals(y, AquiferMaterialSubstitution.localY(index));
					assertEquals(z, AquiferMaterialSubstitution.localZ(index));
				}
			}
		}
	}

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 17, 63, 256, 300})
    void sectionCaptureMatchesExactChunkReadsAndKeepsBelowSeaMasksCompact(int seaLevel) {
        ChunkPrimer primer = new ChunkPrimer(-3, -9, null);
        java.util.Random random = new java.util.Random(417);
        net.minecraft.block.state.IBlockState[] states = {Blocks.AIR.getDefaultState(), Blocks.STONE.getDefaultState(),
                Blocks.WATER.getDefaultState(), Blocks.WATER.getDefaultState().with(net.minecraft.state.properties.BlockStateProperties.LEVEL_0_15, 1)};
        net.minecraft.util.math.BlockPos.MutableBlockPos cursor = new net.minecraft.util.math.BlockPos.MutableBlockPos();
        for (int y = 0; y < 80; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            primer.setBlockState(cursor.setPos(x, y, z), states[random.nextInt(states.length)], false);
        int maximumY = Math.max(0, Math.min(256, seaLevel));
        BitSet expected = new BitSet();
        for (int y = 0; y < maximumY; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            if (primer.getBlockState(cursor.setPos(x, y, z)).equals(Blocks.WATER.getDefaultState()))
                expected.set(AquiferMaterialSubstitution.index(x, y, z));
        BitSet actual = AquiferMaterialSubstitution.captureMask(primer, Blocks.WATER.getDefaultState(), seaLevel);
        assertEquals(expected, actual);
        assertTrue(actual.size() <= maximumY * 256);
    }

    @Test
    void unusualNativeAirStillIncludesAbsentSections() {
        BitSet mask = AquiferMaterialSubstitution.captureMask(new ChunkPrimer(-1, -2, null), Blocks.AIR.getDefaultState(), 32);
        assertEquals(32 * 256, mask.cardinality());
    }

    @Test
    void customChunkViewsUseTheirOwnBlockReads() {
        net.minecraft.world.chunk.IChunk view = (net.minecraft.world.chunk.IChunk) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{net.minecraft.world.chunk.IChunk.class}, (proxy, method, arguments) -> {
                    if (!method.getName().equals("getBlockState")) throw new AssertionError("Unexpected chunk access: " + method);
                    net.minecraft.util.math.BlockPos position = (net.minecraft.util.math.BlockPos) arguments[0];
                    return position.getX() == 1 && position.getY() == 2 && position.getZ() == 3
                            ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
                });
        BitSet mask = AquiferMaterialSubstitution.captureMask(view, Blocks.WATER.getDefaultState(), 4);
        assertEquals(1, mask.cardinality());
        assertTrue(mask.get(AquiferMaterialSubstitution.index(1, 2, 3)));
    }

	@Test
	void pendingMasksAreIdentityScopedAndConsumedOnceAtNegativeCoordinates() {
		AquiferMaterialSubstitution.PendingMasks masks =
				new AquiferMaterialSubstitution.PendingMasks();
		IChunkGenerator overworld = generator();
		IChunkGenerator otherDimension = generator();
		BitSet bits = new BitSet();
		bits.set(AquiferMaterialSubstitution.index(2, 30, 9));
		AquiferMaterialSubstitution.Pending pending = new AquiferMaterialSubstitution.Pending(
				Blocks.WATER.getDefaultState(), Blocks.WATER.getDefaultState().with(net.minecraft.state.properties.BlockStateProperties.LEVEL_0_15, 1), bits);

		masks.put(overworld, -12, -34, pending);
		assertNull(masks.take(otherDimension, -12, -34));
		assertNotNull(masks.take(overworld, -12, -34));
		assertNull(masks.take(overworld, -12, -34));
		assertEquals(0, masks.size());
	}

	@Test
	void pendingMasksCanBeRemovedPerDimensionOrClearedAtShutdown() {
		AquiferMaterialSubstitution.PendingMasks masks =
				new AquiferMaterialSubstitution.PendingMasks();
		IChunkGenerator overworld = generator();
		IChunkGenerator nether = generator();
		AquiferMaterialSubstitution.Pending pending = new AquiferMaterialSubstitution.Pending(
				Blocks.WATER.getDefaultState(), Blocks.WATER.getDefaultState().with(net.minecraft.state.properties.BlockStateProperties.LEVEL_0_15, 1), new BitSet());
		masks.put(overworld, 0, 0, pending);
		masks.put(nether, 0, 0, pending);

		masks.remove(overworld);
		assertNull(masks.take(overworld, 0, 0));
		assertNotNull(masks.take(nether, 0, 0));
		masks.put(nether, 1, 1, pending);
		masks.clear();
		assertEquals(0, masks.size());
	}

	@Test
	void lightingCompatibilitySelectsTheSafeFastPath() {
		assertTrue(AquiferMaterialSubstitution.lightCompatible(
				Blocks.WATER.getDefaultState(), Blocks.WATER.getDefaultState().with(net.minecraft.state.properties.BlockStateProperties.LEVEL_0_15, 1)));
		assertFalse(AquiferMaterialSubstitution.lightCompatible(
				Blocks.WATER.getDefaultState(), Blocks.LAVA.getDefaultState()));
	}

	@Test
	void substitutionTouchesOnlyRecordedCellsThatStillContainNativeFluid() {
		ChunkSection[] sections = new ChunkSection[16];
		sections[1] = new ChunkSection(16, true);
		sections[1].set(1, 4, 2, Blocks.WATER.getDefaultState());
		sections[1].set(3, 5, 4, Blocks.LAVA.getDefaultState());
		sections[1].set(5, 6, 6, Blocks.WATER.getDefaultState());

		BitSet bits = new BitSet();
		bits.set(AquiferMaterialSubstitution.index(1, 20, 2));
		bits.set(AquiferMaterialSubstitution.index(3, 21, 4));
		AquiferMaterialSubstitution.Pending pending = new AquiferMaterialSubstitution.Pending(
				Blocks.WATER.getDefaultState(), Blocks.WATER.getDefaultState().with(net.minecraft.state.properties.BlockStateProperties.LEVEL_0_15, 1), bits);

		assertEquals(1, AquiferMaterialSubstitution.applyToSections(pending, sections));
		assertEquals(Blocks.WATER.getDefaultState().with(net.minecraft.state.properties.BlockStateProperties.LEVEL_0_15, 1), sections[1].get(1, 4, 2));
		assertEquals(Blocks.LAVA.getDefaultState(), sections[1].get(3, 5, 4),
				"a recorded cell changed by caves or another pass must be preserved");
		assertEquals(Blocks.WATER.getDefaultState(), sections[1].get(5, 6, 6),
				"a later lake or spring outside the mask must be preserved");
	}

	@Test
    void passRunsBeforeLakesAndLeavesGenerationRandomnessAndNotificationsAlone() throws Exception {
        String installer = new String(Files.readAllBytes(Paths.get("src/main/java/zone/moddev/mc/orespawn/worldgen/BiomeFeatureInstaller.java")), StandardCharsets.UTF_8);
        assertTrue(installer.contains("GenerationStage.Decoration.RAW_GENERATION"));
        String substitution = new String(Files.readAllBytes(Paths.get("src/main/java/zone/moddev/mc/orespawn/worldgen/AquiferMaterialSubstitution.java")), StandardCharsets.UTF_8);
        assertTrue(substitution.contains("section.set(x, y & 15, z, pending.selectedFluid)"));
        assertFalse(substitution.contains("world.setBlockState"));
        assertFalse(substitution.contains("scheduleUpdate"));
        assertFalse(substitution.contains("nextInt("));
        assertTrue(substitution.indexOf("generator.defaultFluid = nativeFluid")
                < substitution.indexOf("if (lightCompatible(nativeFluid, selected))"));
        assertTrue(substitution.contains("entry.getKey().defaultFluid = entry.getValue()"));
        assertTrue(substitution.contains("WorldgenBenchmark.isVanillaBaseline()"));
    }

	private static IChunkGenerator generator() {
		return (IChunkGenerator) Proxy.newProxyInstance(
				AquiferMaterialSubstitutionTest.class.getClassLoader(),
				new Class<?>[] { IChunkGenerator.class },
				(proxy, method, arguments) -> {
					Class<?> type = method.getReturnType();
					if (type == boolean.class) return false;
					if (type == int.class) return 0;
					return null;
				});
	}
}
