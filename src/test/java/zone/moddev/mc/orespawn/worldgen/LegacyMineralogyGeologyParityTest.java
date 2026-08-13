package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
	void carried110SamplerMatchesPublishedMineralogyExactly() throws Exception {
		try (PublishedMineralogy published = PublishedMineralogy.open(
				"orespawn.mineralogy110Oracle", "cyano.mineralogy.worldgen.Geology")) {
			Class<?> mineralogy = published.load("cyano.mineralogy.Mineralogy");
			List<Block> igneousList = published.blockList(mineralogy, "igneousStones");
			List<Block> metamorphicList = published.blockList(mineralogy, "metamorphicStones");
			List<Block> sedimentaryList = published.blockList(mineralogy, "sedimentaryStones");
			List<Block> originalIgneous = new ArrayList<>(igneousList);
			List<Block> originalMetamorphic = new ArrayList<>(metamorphicList);
			List<Block> originalSedimentary = new ArrayList<>(sedimentaryList);
			Field thickness = mineralogy.getField("GEOM_LAYER_THICKNESS");
			int originalThickness = thickness.getInt(null);
			try {
				Block[] igneous = { Blocks.STONE, Blocks.OBSIDIAN, Blocks.NETHERRACK };
				Block[] metamorphic = { Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE };
				Block[] sedimentary = { Blocks.SANDSTONE, Blocks.GRAVEL, Blocks.COAL_ORE };
				reset(igneousList, igneous);
				reset(metamorphicList, metamorphic);
				reset(sedimentaryList, sedimentary);
				thickness.setInt(null, 11);

				for (long seed : seeds()) {
					PublishedSampler sampler = published.newSampler(
							new Class<?>[] { long.class, double.class, double.class, boolean.class },
							seed, 144.0D, 41.5D, true);
					Geology os4 = new Geology(seed, 144.0D, 41.5D, 11, true,
							states(igneous), states(metamorphic), states(sedimentary));
					assertSamplerParity("1.10 Cyano", seed, sampler, os4);
				}
			} finally {
				reset(igneousList, originalIgneous.toArray(new Block[0]));
				reset(metamorphicList, originalMetamorphic.toArray(new Block[0]));
				reset(sedimentaryList, originalSedimentary.toArray(new Block[0]));
				thickness.setInt(null, originalThickness);
			}
		}
	}

	@Test
	void native112SamplerMatchesPublishedMineralogyExactly() throws Exception {
		try (PublishedMineralogy published = PublishedMineralogy.open(
				"orespawn.mineralogy112Oracle", "com.mcmoddev.mineralogy.worldgen.Geology")) {
			Class<?> registry = published.load("com.mcmoddev.mineralogy.init.MineralogyRegistry");
			List<Block> igneousList = published.blockList(registry, "igneousStones");
			List<Block> metamorphicList = published.blockList(registry, "metamorphicStones");
			List<Block> sedimentaryList = published.blockList(registry, "sedimentaryStones");
			List<Block> originalIgneous = new ArrayList<>(igneousList);
			List<Block> originalMetamorphic = new ArrayList<>(metamorphicList);
			List<Block> originalSedimentary = new ArrayList<>(sedimentaryList);
			Class<?> config = published.load("com.mcmoddev.mineralogy.MineralogyConfig");
			Field thickness = config.getDeclaredField("geomLayerThickness");
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
					PublishedSampler sampler = published.newSampler(
							new Class<?>[] { long.class, double.class, double.class },
							seed, 128.0D, 37.25D);
					Geology os4 = new Geology(seed, 128.0D, 37.25D, 9, false,
							states(igneous), states(metamorphic), states(sedimentary));
					assertSamplerParity("1.12 Cyano", seed, sampler, os4);
				}
			} finally {
				reset(igneousList, originalIgneous.toArray(new Block[0]));
				reset(metamorphicList, originalMetamorphic.toArray(new Block[0]));
				reset(sedimentaryList, originalSedimentary.toArray(new Block[0]));
				thickness.setInt(null, originalThickness);
			}
		}
	}

	private static void assertSamplerParity(String label, long seed,
			PublishedSampler published, Geology os4) throws Exception {
		for (int x : coordinates()) {
			for (int z : coordinates()) {
				for (int y = 0; y < 256; y += 7) {
					assertEquals(published.getStoneAt(x, y, z), os4.getStoneAt(x, y, z),
							label + " mismatch seed=" + seed + " x=" + x
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

	private static final class PublishedMineralogy implements AutoCloseable {
		private final URLClassLoader loader;
		private final Class<?> geologyClass;

		private PublishedMineralogy(URLClassLoader loader, Class<?> geologyClass) {
			this.loader = loader;
			this.geologyClass = geologyClass;
		}

		static PublishedMineralogy open(String property, String geologyClassName) throws Exception {
			String configuredPath = System.getProperty(property);
			assertTrue(configuredPath != null && !configuredPath.trim().isEmpty(),
					"Missing published Mineralogy oracle system property: " + property);
			Path jar = Paths.get(configuredPath);
			assertTrue(Files.isRegularFile(jar), "Published Mineralogy oracle is missing: " + jar);
			URLClassLoader loader = new URLClassLoader(new URL[] { jar.toUri().toURL() },
					LegacyMineralogyGeologyParityTest.class.getClassLoader());
			try {
				return new PublishedMineralogy(loader,
						Class.forName(geologyClassName, true, loader));
			} catch (Throwable failure) {
				loader.close();
				throw failure;
			}
		}

		Class<?> load(String name) throws ClassNotFoundException {
			return Class.forName(name, true, loader);
		}

		@SuppressWarnings("unchecked")
		List<Block> blockList(Class<?> owner, String fieldName) throws Exception {
			return (List<Block>) owner.getField(fieldName).get(null);
		}

		PublishedSampler newSampler(Class<?>[] parameterTypes, Object... arguments)
				throws Exception {
			Constructor<?> constructor = geologyClass.getConstructor(parameterTypes);
			return new PublishedSampler(constructor.newInstance(arguments),
					geologyClass.getMethod("getStoneAt", int.class, int.class, int.class),
					geologyClass.getMethod("getStoneColumn", int.class, int.class, int.class));
		}

		@Override
		public void close() throws Exception {
			loader.close();
		}
	}

	private static final class PublishedSampler {
		private final Object delegate;
		private final Method getStoneAt;
		private final Method getStoneColumn;

		private PublishedSampler(Object delegate, Method getStoneAt, Method getStoneColumn) {
			this.delegate = delegate;
			this.getStoneAt = getStoneAt;
			this.getStoneColumn = getStoneColumn;
		}

		Block getStoneAt(int x, int y, int z) throws Exception {
			return (Block) getStoneAt.invoke(delegate, x, y, z);
		}

		Block[] getStoneColumn(int x, int z, int height) throws Exception {
			return (Block[]) getStoneColumn.invoke(delegate, x, z, height);
		}
	}
}
