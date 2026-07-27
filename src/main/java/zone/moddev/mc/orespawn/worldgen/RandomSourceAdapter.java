package zone.moddev.mc.orespawn.worldgen;

import java.util.Random;

import net.minecraft.util.RandomSource;

/**
 * Allocation-free bridge that preserves the API's java.util.Random contract
 * while world generation supplies Minecraft's RandomSource.
 */
final class RandomSourceAdapter extends Random {
	private static final long serialVersionUID = 1L;
	private transient RandomSource source;

	RandomSourceAdapter() {
		super(0L);
	}

	Random wrap(RandomSource value) {
		source = value;
		return this;
	}

	@Override
	protected int next(int bits) {
		return source.nextInt() >>> (32 - bits);
	}

	@Override public int nextInt() { return source.nextInt(); }
	@Override public int nextInt(int bound) { return source.nextInt(bound); }
	@Override public long nextLong() { return source.nextLong(); }
	@Override public boolean nextBoolean() { return source.nextBoolean(); }
	@Override public float nextFloat() { return source.nextFloat(); }
	@Override public double nextDouble() { return source.nextDouble(); }
	@Override public double nextGaussian() { return source.nextGaussian(); }
}
