package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeDecorator;
import net.minecraft.world.biome.BiomeMesa;
import net.minecraft.world.gen.feature.WorldGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;

class VanillaMesaGoldCompatibilityTest {

	private static final BlockPos ORIGIN = new BlockPos(-32, 0, 48);

	@BeforeAll
	static void bootstrap() {
		Forge12TestBootstrap.registerVanilla();
	}

	@AfterEach
	void restoreDecorators() {
		VanillaMesaGoldCompatibility.clear();
	}

	@Test
	void deniedGoldSkipsTheExtraPassForEveryMesaVariant() {
		int variants = 0;

		for (Biome biome : Biome.REGISTRY) {
			if (!(biome instanceof BiomeMesa)) continue;

			BiomeDecorator decorator = biome.theBiomeDecorator;
			WorldGenerator previous = decorator.goldGen;
			CountingGenerator gold = new CountingGenerator();

			try {
				decorator.goldGen = gold;
				VanillaMesaGoldCompatibility.suppress(gold, null, ORIGIN);

				assertFalse(decorator.goldGen.generate(null, new Random(1), ORIGIN));
				assertEquals(0, gold.calls);

				VanillaMesaGoldCompatibility.restore(null, ORIGIN);
				assertSame(gold, decorator.goldGen);
				assertTrue(decorator.goldGen.generate(null, new Random(1), ORIGIN));
				assertEquals(1, gold.calls);
				variants++;
			} finally {
				VanillaMesaGoldCompatibility.clear();
				decorator.goldGen = previous;
			}
		}

		assertEquals(6, variants);
	}

	@Test
	void unrelatedGeneratorsAndNonMesaDecoratorsAreUntouched() {
		BiomeDecorator mesa = Biomes.MESA.theBiomeDecorator;
		BiomeDecorator plains = Biomes.PLAINS.theBiomeDecorator;
		WorldGenerator previousMesa = mesa.goldGen;
		WorldGenerator previousPlains = plains.goldGen;
		CountingGenerator gold = new CountingGenerator();

		try {
			mesa.goldGen = gold;
			plains.goldGen = gold;
			VanillaMesaGoldCompatibility.suppress(new CountingGenerator(), null, ORIGIN);
			assertSame(gold, mesa.goldGen);

			VanillaMesaGoldCompatibility.suppress(gold, null, ORIGIN);
			assertSame(gold, plains.goldGen);
		} finally {
			VanillaMesaGoldCompatibility.clear();
			mesa.goldGen = previousMesa;
			plains.goldGen = previousPlains;
		}
	}

	@Test
	void restorationIsScopedAndDoesNotOverwriteAnotherModsReplacement() {
		BiomeDecorator mesa = Biomes.MESA.theBiomeDecorator;
		WorldGenerator previous = mesa.goldGen;
		CountingGenerator gold = new CountingGenerator();
		CountingGenerator replacement = new CountingGenerator();

		try {
			mesa.goldGen = gold;
			BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(ORIGIN.getX(), 0, ORIGIN.getZ());
			VanillaMesaGoldCompatibility.suppress(gold, null, mutable);
			mutable.setPos(100, 0, 100);

			VanillaMesaGoldCompatibility.restore(null, mutable);
			assertNotSame(gold, mesa.goldGen);

			mesa.goldGen = replacement;
			VanillaMesaGoldCompatibility.restore(null, ORIGIN);
			assertSame(replacement, mesa.goldGen);
		} finally {
			VanillaMesaGoldCompatibility.clear();
			mesa.goldGen = previous;
		}
	}

	@Test
	void serverStopRestoresAnInterruptedDecoration() {
		BiomeDecorator mesa = Biomes.MESA.theBiomeDecorator;
		WorldGenerator previous = mesa.goldGen;
		CountingGenerator gold = new CountingGenerator();

		try {
			mesa.goldGen = gold;
			VanillaMesaGoldCompatibility.suppress(gold, null, ORIGIN);
			OreSpawnWorldGenerator.INSTANCE.clear();
			assertSame(gold, mesa.goldGen);
		} finally {
			mesa.goldGen = previous;
		}
	}

	private static final class CountingGenerator extends WorldGenerator {
		int calls;

		@Override
		public boolean generate(World world, Random random, BlockPos pos) {
			calls++;
			return true;
		}
	}
}
