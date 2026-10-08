package zone.moddev.mc.orespawn.testmod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Inspects generation caches without adding public API or sharing a mod's Java package. */
final class NativeOreProbeBridge {
	private NativeOreProbeBridge() { }

	static boolean controlsQuartz() {
		try {
			Class<?> generation = Class.forName("zone.moddev.mc.orespawn.worldgen.OreSpawnOreGeneration");
			Method takeover = generation.getDeclaredMethod("takesOverVanillaOre", ResourceKey.class, Block.class);
			takeover.setAccessible(true);
			return (Boolean) takeover.invoke(null, Level.NETHER, Blocks.NETHER_QUARTZ_ORE);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not inspect quartz startup", failure);
		}
	}

	static boolean controlsFluid() {
		try {
			Class<?> generation = Class.forName("zone.moddev.mc.orespawn.worldgen.FluidDepositFeature");
			Field field = generation.getDeclaredField("depositsByDimension");
			field.setAccessible(true);
			Object[] deposits = (Object[]) ((Map<?, ?>) field.get(null)).get(Level.NETHER);
			return deposits != null && deposits.length == 1;
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not inspect fluid startup", failure);
		}
	}
}
