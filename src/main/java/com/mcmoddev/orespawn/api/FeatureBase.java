package com.mcmoddev.orespawn.api;

import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mcmoddev.orespawn.api.os3.ISpawnEntry;
import com.mcmoddev.orespawn.util.OreList;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

/** Allocation-light compatibility base retaining both published OS3 ABIs. */
public class FeatureBase {
	private static final int GENERATION_WRITE_FLAGS = 2;
	protected Random random;
	protected static final Vec3i[] offsets_small = offsets(2);
	protected static final Vec3i[] offsets = offsets(4);
	protected static final int[] offsetIndexRef = indexes(offsets.length);
	protected static final int[] offsetIndexRef_small = indexes(offsets_small.length);

	public FeatureBase(Random random) { this.random = random; }

	public boolean isValidBlock(IBlockState state) {
		return state != null && state.getBlock() != Blocks.AIR;
	}

	protected void runCache(int chunkX, int chunkZ, World world, ISpawnEntry spawn) { }
	protected void runCache(int chunkX, int chunkZ, World world, List<IBlockState> replacements) { }

	protected boolean spawn(IBlockState ore, World world, BlockPos pos, int dimension,
			boolean cacheOverflow, ISpawnEntry spawn) {
		if (!world.isBlockLoaded(pos, false) || !isValidBlock(ore) || spawn == null || !spawn.dimensionAllowed(dimension)
				|| !spawn.biomeAllowed(world.getBiome(pos)) || !spawn.getMatcher().test(world.getBlockState(pos))) {
			return false;
		}
		IBlockState output = spawn.getBlocks().getRandomBlock(random);
		return output != null && world.setBlockState(immutable(pos), output, GENERATION_WRITE_FLAGS);
	}

	protected boolean spawn(IBlockState ore, World world, BlockPos pos, int dimension,
			boolean cacheOverflow, List<IBlockState> replacements, BiomeLocation biomes) {
		if (!world.isBlockLoaded(pos, false) || !isValidBlock(ore)
				|| (biomes != null && !biomes.matches(world.getBiome(pos)))) return false;
		IBlockState current = world.getBlockState(pos);
		if (replacements != null && !replacements.isEmpty() && !replacements.contains(current)) return false;
		return world.setBlockState(immutable(pos), ore, GENERATION_WRITE_FLAGS);
	}

	protected void scramble(int[] values, Random rand) {
		for (int i = values.length - 1; i > 0; i--) {
			int j = rand.nextInt(i + 1); int value = values[i]; values[i] = values[j]; values[j] = value;
		}
	}

	protected static void mergeDefaults(JsonObject target, JsonObject defaults) {
		for (Entry<String, JsonElement> entry : defaults.entrySet()) {
			if (!target.has(entry.getKey())) target.add(entry.getKey(), new JsonParser().parse(entry.getValue().toString()));
		}
	}

	protected int getPoint(int center, int radius, int spread) {
		return center + random.nextInt(Math.max(1, spread * 2 + 1)) - spread;
	}

	protected void spawnMungeInner(Random rand, int quantity, int dimension, Vec3i offset,
			ISpawnEntry spawn, World world, BlockPos origin) {
		for (int i = 0; i < quantity; i++) {
			BlockPos target = origin.add(offset);
			if (world.isBlockLoaded(target, false)) {
				spawn(world.getBlockState(target), world, target, dimension, false, spawn);
			}
		}
	}

	protected void spawnMungeSW(World world, BlockPos origin, int quantity, double variation,
			ISpawnEntry spawn, int dimension) { spawnMungeInner(random, quantity, dimension, Vec3i.NULL_VECTOR, spawn, world, origin); }
	protected void spawnMungeNE(World world, BlockPos origin, int quantity, double variation,
			ISpawnEntry spawn, int dimension) { spawnMungeInner(random, quantity, dimension, Vec3i.NULL_VECTOR, spawn, world, origin); }
	protected void spawnMungeSW(World world, BlockPos origin, int quantity, double variation,
			List<IBlockState> replacements, int dimension, OreList ores) { spawnLegacy(world, origin, quantity, dimension, replacements, ores); }
	protected void spawnMungeNE(World world, BlockPos origin, int quantity, double variation,
			List<IBlockState> replacements, int dimension, OreList ores) { spawnLegacy(world, origin, quantity, dimension, replacements, ores); }

	private void spawnLegacy(World world, BlockPos origin, int quantity, int dimension,
			List<IBlockState> replacements, OreList ores) {
		for (int i = 0; i < quantity; i++) {
			com.mcmoddev.orespawn.api.os3.OreBuilder ore = ores.getRandomOre(random);
			if (ore != null && world.isBlockLoaded(origin, false)) {
				spawn(ore.getOre(), world, origin, dimension, false, replacements, null);
			}
		}
	}

	protected int getABC(int a, int b, int c) { return Math.max(a, Math.max(b, c)); }
	protected int countItem(int value, boolean small) { return Math.max(0, value); }
	protected boolean endCheck(boolean reverse, int value, double limit) { return reverse ? value <= limit : value >= limit; }
	protected int getStart(boolean reverse, double value) { return (int) Math.floor(value); }

	private static BlockPos immutable(BlockPos pos) {
		return pos instanceof BlockPos.MutableBlockPos
				? ((BlockPos.MutableBlockPos) pos).toImmutable() : pos;
	}

	private static Vec3i[] offsets(int radius) {
		java.util.ArrayList<Vec3i> result = new java.util.ArrayList<>();
		for (int y = -radius; y <= radius; y++) for (int z = -radius; z <= radius; z++)
			for (int x = -radius; x <= radius; x++) result.add(new Vec3i(x, y, z));
		return result.toArray(new Vec3i[result.size()]);
	}
	private static int[] indexes(int size) { int[] result = new int[size]; for (int i = 0; i < size; i++) result[i] = i; return result; }
}
