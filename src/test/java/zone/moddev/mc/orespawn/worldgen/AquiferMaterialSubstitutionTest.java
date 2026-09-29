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

import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;

class AquiferMaterialSubstitutionTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Forge12TestBootstrap.registerVanilla();
	}

	@Test
	void capturesOnlyExactNativeFluidBelowSeaLevel() {
		ChunkPrimer primer = new ChunkPrimer();
		primer.setBlockState(1, 10, 2, Blocks.WATER.getDefaultState());
		primer.setBlockState(3, 63, 4, Blocks.WATER.getDefaultState());
		primer.setBlockState(5, 64, 6, Blocks.WATER.getDefaultState());
		primer.setBlockState(7, 20, 8, Blocks.LAVA.getDefaultState());

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

	@Test
	void pendingMasksAreIdentityScopedAndConsumedOnceAtNegativeCoordinates() {
		AquiferMaterialSubstitution.PendingMasks masks =
				new AquiferMaterialSubstitution.PendingMasks();
		IChunkGenerator overworld = generator();
		IChunkGenerator otherDimension = generator();
		BitSet bits = new BitSet();
		bits.set(AquiferMaterialSubstitution.index(2, 30, 9));
		AquiferMaterialSubstitution.Pending pending = new AquiferMaterialSubstitution.Pending(
				Blocks.WATER.getDefaultState(), Blocks.FLOWING_WATER.getDefaultState(), bits);

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
				Blocks.WATER.getDefaultState(), Blocks.FLOWING_WATER.getDefaultState(), new BitSet());
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
				Blocks.WATER.getDefaultState(), Blocks.FLOWING_WATER.getDefaultState()));
		assertFalse(AquiferMaterialSubstitution.lightCompatible(
				Blocks.WATER.getDefaultState(), Blocks.LAVA.getDefaultState()));
	}

	@Test
	void substitutionTouchesOnlyRecordedCellsThatStillContainNativeFluid() {
		ExtendedBlockStorage[] sections = new ExtendedBlockStorage[16];
		sections[1] = new ExtendedBlockStorage(16, true);
		sections[1].set(1, 4, 2, Blocks.WATER.getDefaultState());
		sections[1].set(3, 5, 4, Blocks.LAVA.getDefaultState());
		sections[1].set(5, 6, 6, Blocks.WATER.getDefaultState());

		BitSet bits = new BitSet();
		bits.set(AquiferMaterialSubstitution.index(1, 20, 2));
		bits.set(AquiferMaterialSubstitution.index(3, 21, 4));
		AquiferMaterialSubstitution.Pending pending = new AquiferMaterialSubstitution.Pending(
				Blocks.WATER.getDefaultState(), Blocks.FLOWING_WATER.getDefaultState(), bits);

		assertEquals(1, AquiferMaterialSubstitution.applyToSections(pending, sections));
		assertEquals(Blocks.FLOWING_WATER.getDefaultState(), sections[1].get(1, 4, 2));
		assertEquals(Blocks.LAVA.getDefaultState(), sections[1].get(3, 5, 4),
				"a recorded cell changed by caves or another pass must be preserved");
		assertEquals(Blocks.WATER.getDefaultState(), sections[1].get(5, 6, 6),
				"a later lake or spring outside the mask must be preserved");
	}

	@Test
	void terrainHookRunsBeforeEveryOtherOreSpawnLocalModification() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone",
				"moddev", "mc", "orespawn", "worldgen", "OreSpawnWorldGenerator.java")),
				StandardCharsets.UTF_8);
		assertTrue(source.contains("captureNativeAquifer(ChunkGeneratorEvent.ReplaceBiomeBlocks event)"));
		assertTrue(source.indexOf("AquiferMaterialSubstitution.apply(world, chunk)")
				< source.indexOf("StoneReplacer.FEATURE.generate(world, chunk, random)"));
		assertTrue(source.contains("priority = EventPriority.LOWEST"));
		String substitution = new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone",
				"moddev", "mc", "orespawn", "worldgen", "AquiferMaterialSubstitution.java")),
				StandardCharsets.UTF_8);
		assertTrue(substitution.contains("section.set(x, y & 15, z, pending.selectedFluid)"));
		assertFalse(substitution.contains("world.setBlockState"));
		assertFalse(substitution.contains("scheduleUpdate"));
		assertTrue(substitution.indexOf("generator.oceanBlock = nativeFluid")
				< substitution.indexOf("if (lightCompatible(nativeFluid, selected))"),
				"the fast path must leave the generator on its native fluid");
		assertTrue(substitution.contains("entry.getKey().oceanBlock = entry.getValue()"),
				"shutdown must restore compatibility-path generator state");
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
