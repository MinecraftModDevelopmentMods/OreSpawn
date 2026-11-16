package zone.moddev.mc.orespawn.worldgen;

import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.BlockState;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.IWorld;
import net.minecraft.world.gen.OverworldChunkGenerator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Replaces recorded Overworld terrain fluid at the start of decoration, after
 * native density, surface and cave generation. Matching lighting properties
 * keep the section writes compatible with native height and light data.
 * The mask intersects native density with the fluid still present before decoration,
 * leaving later lakes, springs and decorators outside this pass.
 */
public final class AquiferMaterialSubstitution {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Map<OverworldChunkGenerator, BlockState> NATIVE_FLUIDS =
			Collections.synchronizedMap(new IdentityHashMap<OverworldChunkGenerator, BlockState>());
	private static final Map<ChunkGenerator, Plan> PLANS =
			Collections.synchronizedMap(new IdentityHashMap<ChunkGenerator, Plan>());
	private static final PendingMasks PENDING = new PendingMasks();

    private static final java.util.Set<net.minecraft.util.ResourceLocation> DIMENSIONS = ConcurrentHashMap.newKeySet();
    private static final net.minecraft.util.math.BlockPos LIGHT_POS = new net.minecraft.util.math.BlockPos(0, 0, 0);
    private static final net.minecraft.world.IBlockReader LIGHT_READER = new net.minecraft.world.IBlockReader() {
        public net.minecraft.tileentity.TileEntity getTileEntity(net.minecraft.util.math.BlockPos pos) { return null; }
        public BlockState getBlockState(net.minecraft.util.math.BlockPos pos) { return net.minecraft.block.Blocks.AIR.getDefaultState(); }
        public net.minecraft.fluid.IFluidState getFluidState(net.minecraft.util.math.BlockPos pos) { return net.minecraft.fluid.Fluids.EMPTY.getDefaultState(); }
    };
    private static net.minecraft.world.gen.feature.ConfiguredFeature<?, ?> configured;
    public static void register() {
        configured = new SubstitutionFeature().withConfiguration(new net.minecraft.world.gen.feature.NoFeatureConfig())
                .withPlacement(net.minecraft.world.gen.placement.Placement.NOPE.configure(
                        net.minecraft.world.gen.placement.IPlacementConfig.NO_PLACEMENT_CONFIG));
    }
    static boolean enabledFor(net.minecraft.util.ResourceLocation dimension) { return DIMENSIONS.contains(dimension); }
    static net.minecraft.world.gen.feature.ConfiguredFeature<?, ?> configuredFeature() { return configured; }

    /** Native aquatic decorators still see the fluid used to build the terrain. Writes remain real. */
    static IWorld nativeDecorationView(IWorld world) {
        if (!(world.getWorld() instanceof ServerWorld)) return world;
        ChunkGenerator generator = ((ServerWorld) world.getWorld()).getChunkProvider().getChunkGenerator();
        Plan plan = PLANS.get(generator);
        if (plan == null || !plan.deferred) return world;
        return new NativeAquiferView(world, plan.nativeFluid, plan.selectedFluid);
    }

    private static final class SubstitutionFeature extends ContextFeature<net.minecraft.world.gen.feature.NoFeatureConfig> {
        SubstitutionFeature() { super(net.minecraft.world.gen.feature.NoFeatureConfig::deserialize); }
        @Override boolean place(FeaturePlaceContext<net.minecraft.world.gen.feature.NoFeatureConfig> context) {
            return !WorldgenBenchmark.isVanillaBaseline() && apply(context.level(), context.decorationChunk());
        }
    }

	private AquiferMaterialSubstitution() {
	}

	static void configure(ServerWorld world, BlockState selected) {
		DIMENSIONS.remove(WorldIds.dimension(world));
        ChunkGenerator raw = world.getChunkProvider().getChunkGenerator();
		if (raw == null || raw.getClass() != OverworldChunkGenerator.class) {
			PLANS.remove(raw);
			PENDING.remove(raw);
			return;
		}

		OverworldChunkGenerator generator = (OverworldChunkGenerator) raw;
		BlockState nativeFluid;
		synchronized (NATIVE_FLUIDS) {
			nativeFluid = NATIVE_FLUIDS.get(generator);
			if (nativeFluid == null) {
				nativeFluid = generator.defaultFluid;
				NATIVE_FLUIDS.put(generator, nativeFluid);
			}
		}
		generator.defaultFluid = nativeFluid;
		PLANS.remove(raw);
		PENDING.remove(raw);

		if (selected == null || sameState(selected, nativeFluid)) return;
		if (lightCompatible(nativeFluid, selected)) {
			PLANS.put(raw, new Plan(nativeFluid, selected, true));
            DIMENSIONS.add(WorldIds.dimension(world));
			return;
		}

		generator.defaultFluid = selected;
		PLANS.put(raw, new Plan(nativeFluid, selected, false));
		LOGGER.warn("Dimension '{}' uses aquifer fluid '{}' with opacity/light {}/{} instead of "
				+ "native '{}' at {}/{}; using the compatible generator path, which may generate more slowly",
				WorldIds.dimension(world), selected, selected.getOpacity(LIGHT_READER, LIGHT_POS), selected.getLightValue(),
				nativeFluid, nativeFluid.getOpacity(LIGHT_READER, LIGHT_POS), nativeFluid.getLightValue());
	}

    private static final Map<IChunk, Boolean> CAPTURED =
            Collections.synchronizedMap(new java.util.WeakHashMap<IChunk, Boolean>());

    static void capture(ChunkGenerator generator, IChunk chunk) {
        Plan plan = PLANS.get(generator);
        if (plan == null || !plan.deferred || generator.getClass() != OverworldChunkGenerator.class) return;
        // Do not reinterpret later lake water if a decorated chunk is visited again.
        if (CAPTURED.put(chunk, Boolean.TRUE) != null) return;
        OverworldChunkGenerator nativeGenerator = (OverworldChunkGenerator) generator;
        BitSet mask = captureMask(chunk, plan.nativeFluid, nativeGenerator.getSeaLevel());
        NativeDensityTerrain.Data density = NativeDensityTerrain.chunk(nativeGenerator, chunk.getPos().x, chunk.getPos().z);
        for (int index = mask.nextSetBit(0); index >= 0; index = mask.nextSetBit(index + 1))
            if (!density.nativeFluid(localX(index), localY(index), localZ(index))) mask.clear(index);
        if (!mask.isEmpty()) PENDING.put(generator, chunk.getPos().x, chunk.getPos().z,
                new Pending(plan.nativeFluid, plan.selectedFluid, mask));
    }

	static boolean apply(IWorld region, IChunk chunk) {
		if (!(region.getWorld() instanceof ServerWorld)) return false;
        ServerWorld world = (ServerWorld) region.getWorld();
		ChunkGenerator generator = ((ServerWorld) world).getChunkProvider().getChunkGenerator();
		capture(generator, chunk);
		Pending pending = PENDING.take(generator, chunk.getPos().x, chunk.getPos().z);
		if (pending == null) return false;

		ChunkSection[] sections = chunk.getSections();
		int changed = applyToSections(pending, sections);
		if (changed > 0 && chunk instanceof Chunk) ((Chunk) chunk).setModified(true);
        else if (changed > 0 && chunk instanceof ChunkPrimer) ((ChunkPrimer) chunk).setModified(true);
		return changed > 0;
	}

	static int applyToSections(Pending pending, ChunkSection[] sections) {
		int changed = 0;
		for (int index = pending.mask.nextSetBit(0); index >= 0;
				index = pending.mask.nextSetBit(index + 1)) {
			int y = localY(index);
			ChunkSection section = sections[y >> 4];
			if (section == null) continue;
			int x = localX(index);
			int z = localZ(index);
			BlockState current = section.getBlockState(x, y & 15, z);
			if (!sameState(current, pending.nativeFluid)) continue;
			section.setBlockState(x, y & 15, z, pending.selectedFluid);
			changed++;
		}
		return changed;
	}

	static BitSet captureMask(IChunk primer, BlockState nativeFluid, int seaLevel) {
        int maximumY = Math.max(0, Math.min(256, seaLevel));
		BitSet result = new BitSet(16 * 16 * maximumY);
        if (!nativeFluid.isAir() && (primer.getClass() == ChunkPrimer.class || primer.getClass() == Chunk.class)) {
            ChunkSection[] sections = primer.getSections();
            for (int base = 0; base < maximumY; base += 16) {
                ChunkSection section = sections[base >> 4];
                if (section == null) continue;
                int height = Math.min(16, maximumY - base);
                for (int y = 0; y < height; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    if (sameState(section.getBlockState(x, y, z), nativeFluid)) result.set(index(x, base + y, z));
                }
            }
            return result;
        }
        // Custom chunk views may reinterpret reads; do not bypass their implementation.
		net.minecraft.util.math.BlockPos.Mutable cursor = new net.minecraft.util.math.BlockPos.Mutable();
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = 0; y < maximumY; y++) {
					if (sameState(primer.getBlockState(cursor.setPos(x, y, z)), nativeFluid)) {
						result.set(index(x, y, z));
					}
				}
			}
		}
		return result;
	}

	static boolean lightCompatible(BlockState nativeFluid, BlockState selectedFluid) {
		return !nativeFluid.getBlock().isVariableOpacity() && !selectedFluid.getBlock().isVariableOpacity()
                && nativeFluid.getOpacity(LIGHT_READER, LIGHT_POS) == selectedFluid.getOpacity(LIGHT_READER, LIGHT_POS)
				&& nativeFluid.getLightValue() == selectedFluid.getLightValue();
	}

	static int index(int x, int y, int z) {
		// Y-major order keeps below-sea masks compact and visits section cells contiguously.
		return (y << 8) | (z << 4) | x;
	}

	static int localX(int index) { return index & 15; }
	static int localY(int index) { return index >> 8; }
	static int localZ(int index) { return (index >> 4) & 15; }

	static void clear() {
		synchronized (NATIVE_FLUIDS) {
			for (Map.Entry<OverworldChunkGenerator, BlockState> entry : NATIVE_FLUIDS.entrySet()) {
				entry.getKey().defaultFluid = entry.getValue();
			}
			NATIVE_FLUIDS.clear();
		}
		DIMENSIONS.clear();
        PLANS.clear();
		PENDING.clear();
        CAPTURED.clear();
        NativeDensityTerrain.clear();
	}

	static int pendingCount() { return PENDING.size(); }

	private static boolean sameState(BlockState first, BlockState second) {
		return first == second || (first != null && first.equals(second));
	}

	private static final class Plan {
		final BlockState nativeFluid;
		final BlockState selectedFluid;
		final boolean deferred;

		Plan(BlockState nativeFluid, BlockState selectedFluid, boolean deferred) {
			this.nativeFluid = nativeFluid;
			this.selectedFluid = selectedFluid;
			this.deferred = deferred;
		}
	}

	static final class Pending {
		final BlockState nativeFluid;
		final BlockState selectedFluid;
		final BitSet mask;

		Pending(BlockState nativeFluid, BlockState selectedFluid, BitSet mask) {
			this.nativeFluid = nativeFluid;
			this.selectedFluid = selectedFluid;
			this.mask = (BitSet) mask.clone();
		}
	}

	static final class PendingMasks {
		private final Map<ChunkKey, Pending> masks = new ConcurrentHashMap<>();

		void put(ChunkGenerator generator, int x, int z, Pending pending) {
			masks.put(new ChunkKey(generator, x, z), pending);
		}

		Pending take(ChunkGenerator generator, int x, int z) {
			return masks.remove(new ChunkKey(generator, x, z));
		}

		void remove(ChunkGenerator generator) {
			for (ChunkKey key : masks.keySet()) {
				if (key.generator == generator) masks.remove(key);
			}
		}

		void clear() { masks.clear(); }
		int size() { return masks.size(); }
	}

	private static final class ChunkKey {
		final ChunkGenerator generator;
		final int x;
		final int z;

		ChunkKey(ChunkGenerator generator, int x, int z) {
			this.generator = generator;
			this.x = x;
			this.z = z;
		}

		@Override public int hashCode() {
			return ((31 * System.identityHashCode(generator)) + x) * 31 + z;
		}

		@Override public boolean equals(Object value) {
			if (this == value) return true;
			if (!(value instanceof ChunkKey)) return false;
			ChunkKey other = (ChunkKey) value;
			return generator == other.generator && x == other.x && z == other.z;
		}
	}
}
