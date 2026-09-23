package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.IdentityHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.util.ResourceLocation;

class BackgroundGenerationScaleTest {
	private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
	private static final ResourceLocation IRON = new ResourceLocation("minecraft", "iron_ore");

	@Test
	void controllerRuleRemainsUnscaledWhileBackgroundRulesAreScaled() {
		assertEquals(8.0D, OreSpawnOreGeneration.scaledManagedFrequency(8.0D, true, 0.25D));
		assertEquals(2.0D, OreSpawnOreGeneration.scaledManagedFrequency(8.0D, false, 0.25D));
	}

	@Test
	void lowestControllerScaleWinsForTheSamePrimaryResource() {
		Map<Block, Double> scales = new IdentityHashMap<>();
		Block output = new Block(Material.ROCK);
		OreSpawnOreGeneration.mergeBackgroundScale(scales, output, 0.75D);
		OreSpawnOreGeneration.mergeBackgroundScale(scales, output, 0.25D);
		OreSpawnOreGeneration.mergeBackgroundScale(scales, output, 0.5D);
		assertEquals(0.25D, scales.get(output).doubleValue());
	}

	@Test
	void vanillaScaleGateIsStableByWorldDimensionChunkAndResource() {
		assertFalse(OreSpawnOreGeneration.passesBackgroundScale(
				OVERWORLD, IRON, 42L, -3, 7, 0.0D));
		assertTrue(OreSpawnOreGeneration.passesBackgroundScale(
				OVERWORLD, IRON, 42L, -3, 7, 1.0D));
		boolean first = OreSpawnOreGeneration.passesBackgroundScale(
				OVERWORLD, IRON, 42L, -3, 7, 0.25D);
		assertEquals(first, OreSpawnOreGeneration.passesBackgroundScale(
				OVERWORLD, IRON, 42L, -3, 7, 0.25D));

		int accepted = 0;
		for (int chunk = -5000; chunk < 5000; chunk++) {
			if (OreSpawnOreGeneration.passesBackgroundScale(
					OVERWORLD, IRON, 42L, chunk, -chunk, 0.25D)) accepted++;
		}
		assertTrue(accepted > 2200 && accepted < 2800,
				"quarter-scale gate should retain approximately one quarter of chunks: " + accepted);
	}
}
