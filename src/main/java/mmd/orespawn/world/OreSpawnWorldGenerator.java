package mmd.orespawn.world;

import com.google.common.base.Predicate;
import mmd.orespawn.api.OreSpawnAPI;
import mmd.orespawn.api.SpawnEntry;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.oredict.OreDictionary;

import java.util.*;
import java.util.stream.Collectors;

public class OreSpawnWorldGenerator implements IWorldGenerator {
    private static final Vec3i[] NORMAL_OFFSET = {
            new Vec3i(-1, -1, -1), new Vec3i(0, -1, -1), new Vec3i(1, -1, -1),
            new Vec3i(-1, 0, -1), new Vec3i(0, 0, -1), new Vec3i(1, 0, -1),
            new Vec3i(-1, 1, -1), new Vec3i(0, 1, -1), new Vec3i(1, 1, -1),

            new Vec3i(-1, -1, 0), new Vec3i(0, -1, 0), new Vec3i(1, -1, 0),
            new Vec3i(-1, 0, 0), new Vec3i(0, 0, 0), new Vec3i(1, 0, 0),
            new Vec3i(-1, 1, 0), new Vec3i(0, 1, 0), new Vec3i(1, 1, 0),

            new Vec3i(-1, -1, 1), new Vec3i(0, -1, 1), new Vec3i(1, -1, 1),
            new Vec3i(-1, 0, 1), new Vec3i(0, 0, 1), new Vec3i(1, 0, 1),
            new Vec3i(-1, 1, 1), new Vec3i(0, 1, 1), new Vec3i(1, 1, 1)
    };
    private static final Vec3i[] SMALL_OFFSET = {
            new Vec3i(0, 0, 0), new Vec3i(1, 0, 0),
            new Vec3i(0, 1, 0), new Vec3i(1, 1, 0),

            new Vec3i(0, 0, 1), new Vec3i(1, 0, 1),
            new Vec3i(0, 1, 1), new Vec3i(1, 1, 1)
    };

    private static final int[] NORMAL_OFFSET_INDEX = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
    private static final int[] SMALL_OFFSET_INDEX = {0, 1, 2, 3, 4, 5, 6, 7};

    private static final int MAX_CACHE_SIZE = 1024;
    private static final Map<Vec3i, Map<BlockPos, IBlockState>> OVERFLOW_CACHE = new HashMap<>(MAX_CACHE_SIZE);
    private static final Deque<Vec3i> CACHE_ORDER = new LinkedList<>();
    private static final HashSet<Block> SPAWN_BLOCKS = new HashSet<>();
    private static final Set<Integer> KNOWN_DIMENSIONS = new HashSet<>();

    private static final Predicate<IBlockState> STONE_PREDICATE = input -> {
        if (input == null) {
            return false;
        }

        Block block = input.getBlock();
        return block != Blocks.AIR && OreSpawnWorldGenerator.SPAWN_BLOCKS.contains(block);
    };

    private final long hash;
    private final int dimension;

    private final SpawnEntry spawnEntry;

    public OreSpawnWorldGenerator(SpawnEntry spawnEntry, int dimension, long hash) {
        this.spawnEntry = spawnEntry;
        this.hash = hash;
        this.dimension = dimension;

        if (dimension != OreSpawnAPI.DIMENSION_WILDCARD) {
            OreSpawnWorldGenerator.KNOWN_DIMENSIONS.add(dimension);
        }
    }

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (OreSpawnWorldGenerator.SPAWN_BLOCKS.isEmpty()) {
            OreSpawnWorldGenerator.SPAWN_BLOCKS.add(Blocks.STONE);
            OreSpawnWorldGenerator.SPAWN_BLOCKS.add(Blocks.NETHERRACK);
            OreSpawnWorldGenerator.SPAWN_BLOCKS.add(Blocks.END_STONE);
            OreSpawnWorldGenerator.SPAWN_BLOCKS.addAll(OreDictionary.getOres("stone").stream().filter(stack -> stack.getItem() instanceof ItemBlock).map(stack -> ((ItemBlock) stack.getItem()).getBlock()).collect(Collectors.toList()));
        }

        if (this.dimension == OreSpawnAPI.DIMENSION_WILDCARD ? OreSpawnWorldGenerator.KNOWN_DIMENSIONS.contains(world.provider.getDimension()) : world.provider.getDimension() != this.dimension) {
            return;
        }

        BlockPos pos = new BlockPos((chunkX << 4) & 0x08, 64, (chunkZ << 4) & 0x08);

        if (this.spawnEntry.getBiomes().length != 0) {
            Biome biome = world.getBiome(pos);
            boolean flag = false;

            for (Biome b : this.spawnEntry.getBiomes()) {
                if (b == biome) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                return;
            }
        }

        Vec3i chunkCoord = new Vec3i(chunkX, chunkZ, world.provider.getDimension());

        Map<BlockPos, IBlockState> cache = this.retrieveCache(chunkCoord);
        for (BlockPos cachedPos : cache.keySet()) {
            this.replaceStone(cache.get(cachedPos), world, cachedPos, world.provider.getDimension(), false);
        }

        random.setSeed(random.nextLong() ^ this.hash);
        random.nextInt();

        if (this.spawnEntry.getFrequency() >= 1) {
            for (int i = 0; i < this.spawnEntry.getFrequency(); i++) {
                int x = (chunkX << 4) + random.nextInt(16);
                int y = random.nextInt(this.spawnEntry.getMaxHeight() - this.spawnEntry.getMinHeight()) + this.spawnEntry.getMinHeight();
                int z = (chunkZ << 4) + random.nextInt(16);
                int r = 0;
                if (this.spawnEntry.getVariation() > 0) {
                    r = random.nextInt(2 * this.spawnEntry.getVariation()) - this.spawnEntry.getVariation();
                }
                this.spawnOre(new BlockPos(x, y, z), this.spawnEntry.getState(), this.spawnEntry.getSize() + r, world, random);
            }
        } else if (random.nextFloat() < this.spawnEntry.getFrequency()) {
            int x = (chunkX << 4) + random.nextInt(16);
            int y = random.nextInt(this.spawnEntry.getMaxHeight() - this.spawnEntry.getMinHeight()) + this.spawnEntry.getMinHeight();
            int z = (chunkZ << 4) + random.nextInt(16);
            int r = 0;
            if (this.spawnEntry.getVariation() > 0) {
                r = random.nextInt(2 * this.spawnEntry.getVariation()) - this.spawnEntry.getVariation();
            }
            this.spawnOre(new BlockPos(x, y, z), this.spawnEntry.getState(), this.spawnEntry.getSize() + r, world, random);
        }
    }

    private void spawnOre(BlockPos blockPos, IBlockState state, int quantity, World world, Random random) {
        int count = quantity;

        if (quantity <= 8) {
            int[] scrambledLUT = new int[OreSpawnWorldGenerator.SMALL_OFFSET_INDEX.length];
            System.arraycopy(OreSpawnWorldGenerator.SMALL_OFFSET_INDEX, 0, scrambledLUT, 0, scrambledLUT.length);
            scramble(scrambledLUT, random);
            while (count > 0) {
                this.replaceStone(state, world, blockPos.add(OreSpawnWorldGenerator.SMALL_OFFSET[scrambledLUT[--count]]), world.provider.getDimension(), true);
            }
            return;
        } else if (quantity <= 26) {
            int[] scrambledLUT = new int[OreSpawnWorldGenerator.NORMAL_OFFSET_INDEX.length];
            System.arraycopy(OreSpawnWorldGenerator.NORMAL_OFFSET_INDEX, 0, scrambledLUT, 0, scrambledLUT.length);
            scramble(scrambledLUT, random);
            while (count > 0) {
                this.replaceStone(state, world, blockPos.add(OreSpawnWorldGenerator.NORMAL_OFFSET[scrambledLUT[--count]]), world.provider.getDimension(), true);
            }
            return;
        }

        double radius = Math.pow(quantity, 1.0D / 3.0D) * (3.0D / 4.0D / Math.PI) + 2.0D;
        int rSqr = (int) (radius * radius);

        fill:
        {
            if (random.nextBoolean()) {
                for (int dy = (int) (-1 * radius); dy < radius; dy++) {
                    for (int dz = (int) (-1 * radius); dz < radius; dz++) {
                        for (int dx = (int) (-1 * radius); dx < radius; dx++) {
                            if ((dx * dx + dy * dy + dz * dz) <= rSqr) {
                                replaceStone(state, world, blockPos.add(dx, dy, dz), world.provider.getDimension(), true);
                                count--;
                            }
                            if (count <= 0) {
                                break fill;
                            }
                        }
                    }
                }
            } else {
                for (int dy = (int) (-1 * radius); dy < radius; dy++) {
                    for (int dx = (int) (radius); dx >= (int) (-1 * radius); dx--) {
                        for (int dz = (int) (radius); dz >= (int) (-1 * radius); dz--) {
                            if ((dx * dx + dy * dy + dz * dz) <= rSqr) {
                                replaceStone(state, world, blockPos.add(dx, dy, dz), world.provider.getDimension(), true);
                                count--;
                            }
                            if (count <= 0) {
                                break fill;
                            }
                        }
                    }
                }
            }
        }
    }

    private void replaceStone(IBlockState state, World world, BlockPos pos, int dimension, boolean cacheOverflow) {
        if (pos.getY() < 0 || pos.getY() >= world.getHeight()) {
            return;
        }
        if (world.isAreaLoaded(pos, 0)) {
            if (world.isAirBlock(pos)) {
                return;
            }
            IBlockState bs = world.getBlockState(pos);
            if (bs.getBlock().isReplaceableOreGen(bs, world, pos, OreSpawnWorldGenerator.STONE_PREDICATE) || OreSpawnWorldGenerator.SPAWN_BLOCKS.contains(bs.getBlock())) {
                world.setBlockState(pos, state, 2);
            }
        } else if (cacheOverflow) {
            this.cacheOverflowBlock(state, pos, dimension);
        }
    }

    private void cacheOverflowBlock(IBlockState state, BlockPos pos, int dimension) {
        BlockPos chunkCoord = new BlockPos(pos.getX() >> 4, pos.getY() >> 4, dimension);
        if (!OreSpawnWorldGenerator.OVERFLOW_CACHE.containsKey(chunkCoord)) {
            OreSpawnWorldGenerator.CACHE_ORDER.addLast(chunkCoord);
            if (OreSpawnWorldGenerator.CACHE_ORDER.size() > OreSpawnWorldGenerator.MAX_CACHE_SIZE) {
                Vec3i drop = OreSpawnWorldGenerator.CACHE_ORDER.removeFirst();
                OreSpawnWorldGenerator.OVERFLOW_CACHE.get(drop).clear();
                OreSpawnWorldGenerator.OVERFLOW_CACHE.remove(drop);
            }
            OreSpawnWorldGenerator.OVERFLOW_CACHE.put(chunkCoord, new HashMap<>());
        }
        Map<BlockPos, IBlockState> cache = OreSpawnWorldGenerator.OVERFLOW_CACHE.get(chunkCoord);
        cache.put(pos, state);
    }

    private Map<BlockPos, IBlockState> retrieveCache(Vec3i chunkCoord) {
        if (OreSpawnWorldGenerator.OVERFLOW_CACHE.containsKey(chunkCoord)) {
            Map<BlockPos, IBlockState> cache = OreSpawnWorldGenerator.OVERFLOW_CACHE.get(chunkCoord);
            OreSpawnWorldGenerator.CACHE_ORDER.remove(chunkCoord);
            OreSpawnWorldGenerator.OVERFLOW_CACHE.remove(chunkCoord);
            return cache;
        } else {
            return Collections.emptyMap();
        }
    }

    private void scramble(int[] target, Random random) {
        for (int i = target.length - 1; i > 0; i--) {
            int n = random.nextInt(i);
            int temp = target[i];
            target[i] = target[n];
            target[n] = temp;
        }
    }
}
