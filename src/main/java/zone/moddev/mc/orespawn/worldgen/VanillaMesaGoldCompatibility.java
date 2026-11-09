package zone.moddev.mc.orespawn.worldgen;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeDecorator;
import net.minecraft.world.biome.BiomeMesa;
import net.minecraft.world.gen.feature.WorldGenerator;

/** Applies the normal gold decision to Mesa's extra, unhooked gold pass. */
final class VanillaMesaGoldCompatibility {

	private static final WorldGenerator SKIPPED = new WorldGenerator() {
		@Override
		public boolean generate(World world, Random random, BlockPos pos) {
			return false;
		}
	};

	private static final Map<BiomeDecorator, SuppressedPass> suppressed = new IdentityHashMap<>();

	private VanillaMesaGoldCompatibility() {
	}

	static void suppress(WorldGenerator generator, World world, BlockPos origin) {
		if (generator == null || generator == SKIPPED) return;

		// Mesa calls the same generator again after Forge's gold event. Match it
		// by identity, not by a nearby biome, so chunk borders cannot pick the
		// wrong decorator. No other ore generators or biome settings are changed.
		for (Biome biome : Biome.REGISTRY) {
			if (!(biome instanceof BiomeMesa)) continue;

			BiomeDecorator decorator = biome.decorator;
			if (decorator.goldGen == generator) {
				suppressed.put(decorator, new SuppressedPass(generator, world, origin));
				decorator.goldGen = SKIPPED;
			}
		}
	}

	static void restore(World world, BlockPos origin) {
		Iterator<Map.Entry<BiomeDecorator, SuppressedPass>> iterator = suppressed.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<BiomeDecorator, SuppressedPass> entry = iterator.next();
			SuppressedPass pass = entry.getValue();

			if (pass.world == world && pass.origin.equals(origin)) {
				restore(entry.getKey(), pass);
				iterator.remove();
			}
		}
	}

	static void clear() {
		for (Map.Entry<BiomeDecorator, SuppressedPass> entry : suppressed.entrySet()) {
			restore(entry.getKey(), entry.getValue());
		}

		suppressed.clear();
	}

	private static void restore(BiomeDecorator decorator, SuppressedPass pass) {
		// Leave a replacement installed by another mod alone.
		if (decorator.goldGen == SKIPPED) {
			decorator.goldGen = pass.generator;
		}
	}

	private static final class SuppressedPass {
		final WorldGenerator generator;
		final World world;
		final BlockPos origin;

		SuppressedPass(WorldGenerator generator, World world, BlockPos origin) {
			this.generator = generator;
			this.world = world;
			this.origin = origin.toImmutable();
		}
	}
}
