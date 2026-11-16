package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.WorldGenRegion;
import zone.moddev.mc.orespawn.test.Forge31TestBootstrap;
import zone.moddev.mc.orespawn.test.TestObjects;

class OreAirRegionBoundaryTest {

	@BeforeAll
	static void bootstrap() {
		Forge31TestBootstrap.registerVanilla();
	}

	@ParameterizedTest
	@CsvSource({
		"3, -22, 47, 24", "3, -22, 0, 24",
		"3, -22, 24, 47", "3, -22, 24, 0",
		"-3, -3, 47, 24", "-3, -3, 0, 24",
		"-3, -3, 24, 47", "-3, -3, 24, 0"
	})
	void exposureChecksDoNotReadBeyondAnyRegionEdge(int minChunkX, int minChunkZ,
			int offsetX, int offsetZ) throws Exception {
		WorldGenRegion region = region(minChunkX, minChunkZ);
		Object context = context(region);
		int x = (minChunkX << 4) + offsetX;
		int z = (minChunkZ << 4) + offsetZ;
		int[][] neighbours = {{x + 1, 64, z}, {x - 1, 64, z},
				{x, 65, z}, {x, 63, z}, {x, 64, z + 1}, {x, 64, z - 1}};
		for (int[] position : neighbours) {
			if (region.chunkExists(position[0] >> 4, position[2] >> 4)) {
				region.getChunk(position[0] >> 4, position[2] >> 4).setBlockState(
						new BlockPos(position[0], position[1], position[2]),
						Blocks.STONE.getDefaultState(), false);
			}
		}

		// An unavailable neighbour is not air and must never request another chunk.
		assertFalse(invoke(context, "isAdjacentToAir", x, 64, z));

		int inwardX = x + (offsetX == 0 ? 1 : offsetX == 47 ? -1 : 0);
		int inwardZ = z + (offsetZ == 0 ? 1 : offsetZ == 47 ? -1 : 0);
		region.getChunk(inwardX >> 4, inwardZ >> 4).setBlockState(
				new BlockPos(inwardX, 64, inwardZ), Blocks.AIR.getDefaultState(), false);
		assertTrue(invoke(context, "isAdjacentToAir", x, 64, z),
				"Available air must still trigger the exposure rule at a region edge");
	}

	@Test
	void verticalLimitsAndAvailableAirKeepTheirExistingMeaning() throws Exception {
		Object context = context(region(-1, -1));
		assertFalse(invoke(context, "isAir", 0, -1, 0));
		assertFalse(invoke(context, "isAir", 0, 256, 0));
		assertTrue(invoke(context, "isAir", 0, 0, 0));
		assertTrue(invoke(context, "isAir", 0, 255, 0));
	}

	@Test
	void nativeCurrentChunkReadsUseTheHeldChunkIncludingNegativeCoordinates() throws Exception {
		WorldGenRegion region = region(-3, -3);
		Object context = context(region);
		ChunkPrimer held = new ChunkPrimer(new net.minecraft.util.math.ChunkPos(-2, -2), net.minecraft.util.palette.UpgradeData.EMPTY);
		BlockPos position = new BlockPos(-17, 64, -17);
		held.setBlockState(position, Blocks.GOLD_ORE.getDefaultState(), false);
		set(context, context.getClass(), "chunk", held);
		set(context, context.getClass(), "chunkX", -2);
		set(context, context.getClass(), "chunkZ", -2);
		set(context, context.getClass(), "nativeChunkReads", true);
		assertSame(Blocks.GOLD_ORE.getDefaultState(), stateAt(context, position));

		BlockPos neighbour = new BlockPos(-16, 64, -17);
		region.getChunk(-1, -2).setBlockState(neighbour, Blocks.IRON_ORE.getDefaultState(), false);
		assertSame(Blocks.IRON_ORE.getDefaultState(), stateAt(context, neighbour));
	}

	@Test
	void customWorldReadViewsAreNotBypassed() throws Exception {
		Object context = context(region(0, 0));
		BlockPos position = new BlockPos(1, 64, 1);
		IWorld view = (IWorld) java.lang.reflect.Proxy.newProxyInstance(IWorld.class.getClassLoader(),
				new Class<?>[] {IWorld.class}, (proxy, method, arguments) -> {
					if (method.getName().equals("getBlockState") && position.equals(arguments[0]))
						return Blocks.GOLD_ORE.getDefaultState();
					throw new AssertionError("Unexpected world read: " + method);
				});
		set(context, context.getClass(), "world", view);
		set(context, context.getClass(), "chunk", new ChunkPrimer(new net.minecraft.util.math.ChunkPos(0, 0), net.minecraft.util.palette.UpgradeData.EMPTY));
		assertSame(Blocks.GOLD_ORE.getDefaultState(), stateAt(context, position));
	}

	@Test
	void chunkOnlyRetrogenReadsStillUseTheChunk() throws Exception {
		Object context = context(region(0, 0));
		ChunkPrimer held = new ChunkPrimer(new net.minecraft.util.math.ChunkPos(0, 0), net.minecraft.util.palette.UpgradeData.EMPTY);
		BlockPos position = new BlockPos(1, 64, 1);
		held.setBlockState(position, Blocks.IRON_ORE.getDefaultState(), false);
		set(context, context.getClass(), "world", null);
		set(context, context.getClass(), "chunk", held);
		assertSame(Blocks.IRON_ORE.getDefaultState(), stateAt(context, position));
	}

	@Test
	void nativeFluidReadViewDoesNotChangeTheBackingRegion() throws Exception {
		WorldGenRegion world = region(-3, -3);
		BlockPos position = new BlockPos(-17, 40, -17);
		world.getChunk(-2, -2).setBlockState(position, Blocks.LAVA.getDefaultState(), false);
		NativeAquiferView view = new NativeAquiferView(world, Blocks.WATER.getDefaultState(), Blocks.LAVA.getDefaultState());
		assertSame(Blocks.WATER.getDefaultState(), view.getBlockState(position));
		assertSame(Blocks.WATER.getDefaultState().getFluidState(), view.getFluidState(position));
		assertSame(Blocks.LAVA.getDefaultState(), world.getBlockState(position));
		assertSame(Blocks.AIR.getDefaultState().getFluidState(), view.getFluidState(position.up()));
	}

	private static BlockState stateAt(Object context, BlockPos position) throws Exception {
		Method method = context.getClass().getDeclaredMethod("stateAt", BlockPos.class);
		method.setAccessible(true);
		return (BlockState) method.invoke(context, position);
	}

	private static Object context(WorldGenRegion region) throws Exception {
		Class<?> type = Class.forName(OreSpawnOreGeneration.class.getName() + "$PatternContext");
		Constructor<?> constructor = type.getDeclaredConstructor(BlockPos.Mutable.class);
		constructor.setAccessible(true);
		Object context = constructor.newInstance(new BlockPos.Mutable());
		set(context, type, "world", region);
		return context;
	}

	private static boolean invoke(Object context, String name, int x, int y, int z) throws Exception {
		Method method = context.getClass().getDeclaredMethod(name, int.class, int.class, int.class);
		method.setAccessible(true);
		return (boolean) method.invoke(context, x, y, z);
	}

	private static WorldGenRegion region(int minChunkX, int minChunkZ) throws Exception {
		// Exercise Minecraft's actual bounds and block reads without starting a server.
		// Its constructor requires a live World; these reads need only this chunk array.
		WorldGenRegion region = TestObjects.allocate(WorldGenRegion.class);
		ChunkPrimer[] chunks = new ChunkPrimer[9];
		for (int z = 0; z < 3; z++) {
			for (int x = 0; x < 3; x++) {
				chunks[x + z * 3] = new ChunkPrimer(new net.minecraft.util.math.ChunkPos(minChunkX + x, minChunkZ + z), net.minecraft.util.palette.UpgradeData.EMPTY);
			}
		}
		set(region, WorldGenRegion.class, "chunkPrimers", java.util.Arrays.asList(chunks));
		set(region, WorldGenRegion.class, "field_217380_e", 3);
		set(region, WorldGenRegion.class, "mainChunkX", minChunkX + 1);
		set(region, WorldGenRegion.class, "mainChunkZ", minChunkZ + 1);
		return region;
	}

	private static void set(Object target, Class<?> type, String name, Object value) throws Exception {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}
}
