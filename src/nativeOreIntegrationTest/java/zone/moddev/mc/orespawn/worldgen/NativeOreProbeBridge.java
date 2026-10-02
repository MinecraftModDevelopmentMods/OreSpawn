package zone.moddev.mc.orespawn.worldgen;

import net.minecraft.block.Blocks;
import net.minecraft.util.ResourceLocation;

/** Keeps cache inspection in the test fixture, not in OreSpawn's public API. */
public final class NativeOreProbeBridge {
	private NativeOreProbeBridge() { }

	public static boolean controlsQuartz() {
		return OreSpawnOreGeneration.takesOverVanillaOre(
				new ResourceLocation("minecraft:the_nether"), Blocks.NETHER_QUARTZ_ORE);
	}
}
