package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;

class LegacyMineralogyGeologyParityTest {
	@BeforeAll
	static void bootstrapMinecraftRegistries() {
		Forge12TestBootstrap.registerVanilla();
	}

	@Test
	void os4CyanoSamplerMatchesThePublishedMineralogyThreeEngineExactly() {
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

			for (long seed : new long[] { 0L, -4965128775892001975L }) {
				cyano.mineralogy.worldgen.Geology published =
						new cyano.mineralogy.worldgen.Geology(seed, 144.0D, 41.5D, true);
				Geology os4 = new Geology(seed, 144.0D, 41.5D, 11, true,
						states(igneous), states(metamorphic), states(sedimentary));

				for (int x : new int[] { -1025, -257, -1, 0, 1, 255, 1024 }) {
					for (int z : new int[] { -769, -16, 0, 15, 513 }) {
						for (int y = 0; y < 256; y += 7) {
							assertEquals(published.getStoneAt(x, y, z), os4.getStoneAt(x, y, z),
									"Cyano mismatch at seed=" + seed + " x=" + x + " y=" + y + " z=" + z);
						}
						assertArrayEquals(published.getStoneColumn(x, z, 256), os4.getStoneColumn(x, z, 256),
								"Cyano column mismatch at seed=" + seed + " x=" + x + " z=" + z);
					}
				}
			}
		} finally {
			cyano.mineralogy.Mineralogy.igneousStones.clear();
			cyano.mineralogy.Mineralogy.igneousStones.addAll(originalIgneous);
			cyano.mineralogy.Mineralogy.metamorphicStones.clear();
			cyano.mineralogy.Mineralogy.metamorphicStones.addAll(originalMetamorphic);
			cyano.mineralogy.Mineralogy.sedimentaryStones.clear();
			cyano.mineralogy.Mineralogy.sedimentaryStones.addAll(originalSedimentary);
			cyano.mineralogy.Mineralogy.GEOM_LAYER_THICKNESS = originalThickness;
		}
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
