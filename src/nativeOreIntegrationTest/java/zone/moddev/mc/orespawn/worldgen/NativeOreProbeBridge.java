package zone.moddev.mc.orespawn.worldgen;

import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.block.Blocks;
import net.minecraft.world.World;

/** Inspects private caches from the isolated fixture, without adding public API. */
public final class NativeOreProbeBridge {
	private NativeOreProbeBridge() { }

	public static boolean controlsQuartz() {
		return OreSpawnOreGeneration.takesOverVanillaOre(World.NETHER, Blocks.NETHER_QUARTZ_ORE);
	}

	public static boolean controlsFluid() {
		try {
			Field field = FluidDepositFeature.class.getDeclaredField("depositsByDimension");
			field.setAccessible(true);
			Object[] deposits = (Object[]) ((Map<?, ?>) field.get(null)).get(World.NETHER);
			return deposits != null && deposits.length == 1;
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not inspect fluid startup", failure);
		}
	}

}
