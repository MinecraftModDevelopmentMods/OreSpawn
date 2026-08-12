package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge14TestBootstrap;

class LegacyMineralogyGeologyParityTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Forge14TestBootstrap.registerVanilla();
	}

	@Test
	void carried110SamplerMatchesPublishedMineralogyExactly() {
		List<Block> originalIgneous = new ArrayList<>(cyano.mineralogy.Mineralogy.igneousStones);
		List<Block> originalMetamorphic = new ArrayList<>(cyano.mineralogy.Mineralogy.metamorphicStones);
		List<Block> originalSedimentary = new ArrayList<>(cyano.mineralogy.Mineralogy.sedimentaryStones);
		int originalThickness = cyano.mineralogy.Mineralogy.GEOM_LAYER_THICKNESS;
		try {
			Block[] igneous = { Blocks.STONE, Blocks.OBSIDIAN, Blocks.NETHERRACK };
			Block[] metamorphic = { Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE };
			Block[] sedimentary = { Blocks.SANDSTONE, Blocks.GRAVEL, Blocks.COAL_ORE };
			reset(cyano.mineralogy.Mineralogy.igneousStones, igneous);
			reset(cyano.mineralogy.Mineralogy.metamorphicStones, metamorphic);
			reset(cyano.mineralogy.Mineralogy.sedimentaryStones, sedimentary);
			cyano.mineralogy.Mineralogy.GEOM_LAYER_THICKNESS = 11;

			for (long seed : seeds()) {
				cyano.mineralogy.worldgen.Geology published =
						new cyano.mineralogy.worldgen.Geology(seed, 144.0D, 41.5D, true);
				Geology os4 = new Geology(seed, 144.0D, 41.5D, 11, true,
						states(igneous), states(metamorphic), states(sedimentary));
				assertSamplerParity(seed, published, os4);
			}
		} finally {
			reset(cyano.mineralogy.Mineralogy.igneousStones,
					originalIgneous.toArray(new Block[0]));
			reset(cyano.mineralogy.Mineralogy.metamorphicStones,
					originalMetamorphic.toArray(new Block[0]));
			reset(cyano.mineralogy.Mineralogy.sedimentaryStones,
					originalSedimentary.toArray(new Block[0]));
			cyano.mineralogy.Mineralogy.GEOM_LAYER_THICKNESS = originalThickness;
		}
	}

	@Test
	void native112SamplerMatchesPublishedMineralogyExactly() throws Exception {
		List<Block> igneousList = com.mcmoddev.mineralogy.init.MineralogyRegistry.igneousStones;
		List<Block> metamorphicList = com.mcmoddev.mineralogy.init.MineralogyRegistry.metamorphicStones;
		List<Block> sedimentaryList = com.mcmoddev.mineralogy.init.MineralogyRegistry.sedimentaryStones;
		List<Block> originalIgneous = new ArrayList<>(igneousList);
		List<Block> originalMetamorphic = new ArrayList<>(metamorphicList);
		List<Block> originalSedimentary = new ArrayList<>(sedimentaryList);
		Field thickness = com.mcmoddev.mineralogy.MineralogyConfig.class
				.getDeclaredField("geomLayerThickness");
		thickness.setAccessible(true);
		int originalThickness = thickness.getInt(null);
		try {
			Block[] igneous = { Blocks.STONE, Blocks.OBSIDIAN, Blocks.NETHERRACK };
			Block[] metamorphic = { Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE };
			Block[] sedimentary = { Blocks.SANDSTONE, Blocks.GRAVEL, Blocks.COAL_ORE,
					Blocks.SANDSTONE };
			reset(igneousList, igneous);
			reset(metamorphicList, metamorphic);
			reset(sedimentaryList, sedimentary);
			thickness.setInt(null, 9);

			for (long seed : seeds()) {
				com.mcmoddev.mineralogy.worldgen.Geology published =
						new com.mcmoddev.mineralogy.worldgen.Geology(seed, 128.0D, 37.25D);
				Geology os4 = new Geology(seed, 128.0D, 37.25D, 9, false,
						states(igneous), states(metamorphic), states(sedimentary));
				for (int x : coordinates()) {
					for (int z : coordinates()) {
						for (int y = 0; y < 256; y += 7) {
							assertEquals(published.getStoneAt(x, y, z), os4.getStoneAt(x, y, z),
									"1.12 Cyano mismatch seed=" + seed + " x=" + x
									+ " y=" + y + " z=" + z);
						}
						assertArrayEquals(published.getStoneColumn(x, z, 256),
								os4.getStoneColumn(x, z, 256));
					}
				}
			}
		} finally {
			reset(igneousList, originalIgneous.toArray(new Block[0]));
			reset(metamorphicList, originalMetamorphic.toArray(new Block[0]));
			reset(sedimentaryList, originalSedimentary.toArray(new Block[0]));
			thickness.setInt(null, originalThickness);
		}
	}

	private static void assertSamplerParity(long seed,
			cyano.mineralogy.worldgen.Geology published, Geology os4) {
		for (int x : coordinates()) {
			for (int z : coordinates()) {
				for (int y = 0; y < 256; y += 7) {
					assertEquals(published.getStoneAt(x, y, z), os4.getStoneAt(x, y, z),
							"1.10 Cyano mismatch seed=" + seed + " x=" + x
							+ " y=" + y + " z=" + z);
				}
				assertArrayEquals(published.getStoneColumn(x, z, 256),
						os4.getStoneColumn(x, z, 256));
			}
		}
	}

	private static long[] seeds() {
		return new long[] { 0L, -4965128775892001975L };
	}

	private static int[] coordinates() {
		return new int[] { -1025, -257, -1, 0, 1, 255, 1024 };
	}

	private static void reset(List<Block> target, Block[] values) {
		target.clear();
		for (Block value : values) target.add(value);
	}

	private static IBlockState[] states(Block[] blocks) {
		IBlockState[] states = new IBlockState[blocks.length];
		for (int i = 0; i < blocks.length; i++) states[i] = blocks[i].getDefaultState();
		return states;
	}
}
