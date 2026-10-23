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

	static void verifyStoppedCaches() {
		try {
			Class<?> gates = Class.forName("zone.moddev.mc.orespawn.worldgen.VanillaOreFeatureGate");
			for (String name : new String[] { "VANILLA_GATES", "SUPPRESSIBLE_GATES" }) {
				Field field = gates.getDeclaredField(name);
				field.setAccessible(true);
				if (!((Map<?, ?>) field.get(null)).isEmpty()) {
					throw new IllegalStateException("Ore wrapper cache survived shutdown: " + name);
				}
			}
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not inspect stopped ore wrappers", failure);
		}
	}

	static void verifyQuartzPasses(boolean managed, boolean imported) {
		try {
			Class<?> generation = Class.forName("zone.moddev.mc.orespawn.worldgen.OreSpawnOreGeneration");
			Field cache = generation.getDeclaredField("oresByDimension");
			cache.setAccessible(true);
			Object[] ores = (Object[]) ((Map<?, ?>) cache.get(null)).get(Level.NETHER);
			int count = ores == null ? 0 : ores.length;
			if (count != (managed ? 1 : 0)) {
				throw new IllegalStateException("Unexpected managed quartz pass count: " + count);
			}
			if (managed) {
				Field id = ores[0].getClass().getDeclaredField("ruleId");
				id.setAccessible(true);
				String expected = imported ? "orespawn:legacy/quartz" : "minecraft:nether_quartz_ore";
				if (!expected.equals(id.get(ores[0]).toString())) {
					throw new IllegalStateException("Wrong quartz placement source: " + id.get(ores[0]));
				}
			}
			System.out.println("NATIVE_ORE_PROBE managed=" + managed + " imported=" + imported + " passes=" + count);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not inspect imported quartz placement", failure);
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
