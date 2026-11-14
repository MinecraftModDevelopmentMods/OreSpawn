package zone.moddev.mc.orespawn.worldgen;

import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.IWorld;
import net.minecraft.world.gen.ChunkGeneratorOverworld;
import net.minecraftforge.event.terraingen.ChunkGeneratorEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Replaces recorded Overworld terrain fluid at the start of decoration, after
 * native density, surface and cave generation. Matching lighting properties
 * keep the section writes compatible with native height and light data.
 * The primer mask leaves later lakes, springs and decorators outside this pass.
 */
public final class AquiferMaterialSubstitution {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Map<ChunkGeneratorOverworld, IBlockState> NATIVE_FLUIDS =
			Collections.synchronizedMap(new IdentityHashMap<ChunkGeneratorOverworld, IBlockState>());
	private static final Map<IChunkGenerator, Plan> PLANS =
			Collections.synchronizedMap(new IdentityHashMap<IChunkGenerator, Plan>());
	private static final PendingMasks PENDING = new PendingMasks();

    private static final java.util.Set<net.minecraft.util.ResourceLocation> DIMENSIONS = ConcurrentHashMap.newKeySet();
    private static final net.minecraft.util.math.BlockPos LIGHT_POS = new net.minecraft.util.math.BlockPos(0, 0, 0);
    private static final net.minecraft.world.IBlockReader LIGHT_READER = new net.minecraft.world.IBlockReader() {
        public net.minecraft.tileentity.TileEntity getTileEntity(net.minecraft.util.math.BlockPos pos) { return null; }
        public IBlockState getBlockState(net.minecraft.util.math.BlockPos pos) { return net.minecraft.init.Blocks.AIR.getDefaultState(); }
        public net.minecraft.fluid.IFluidState getFluidState(net.minecraft.util.math.BlockPos pos) { return net.minecraft.init.Fluids.EMPTY.getDefaultState(); }
    };
    private static net.minecraft.world.gen.feature.CompositeFeature<?, ?> configured;
    public static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                net.minecraftforge.eventbus.api.EventPriority.LOWEST, AquiferMaterialSubstitution::capture);
        configured = net.minecraft.world.biome.Biome.createCompositeFeature(
                new SubstitutionFeature(), new net.minecraft.world.gen.feature.NoFeatureConfig(),
                net.minecraft.world.biome.Biome.PASSTHROUGH,
                net.minecraft.world.gen.placement.IPlacementConfig.NO_PLACEMENT_CONFIG);
    }
    static boolean enabledFor(net.minecraft.util.ResourceLocation dimension) { return DIMENSIONS.contains(dimension); }
    static net.minecraft.world.gen.feature.CompositeFeature<?, ?> configuredFeature() { return configured; }

    /** Native aquatic decorators still see the fluid used to build the terrain. Writes remain real. */
    static IWorld nativeDecorationView(IWorld world) {
        if (!(world.getWorld() instanceof WorldServer)) return world;
        IChunkGenerator generator = ((WorldServer) world.getWorld()).getChunkProvider().getChunkGenerator();
        Plan plan = PLANS.get(generator);
        if (plan == null || !plan.deferred) return world;
        return new NativeAquiferView(world, plan.nativeFluid, plan.selectedFluid);
    }

    private static final class SubstitutionFeature extends ContextFeature<net.minecraft.world.gen.feature.NoFeatureConfig> {
        @Override boolean place(FeaturePlaceContext<net.minecraft.world.gen.feature.NoFeatureConfig> context) {
            return !WorldgenBenchmark.isVanillaBaseline() && apply(context.level(), context.decorationChunk());
        }
    }

	private AquiferMaterialSubstitution() {
	}

	static void configure(WorldServer world, IBlockState selected) {
		DIMENSIONS.remove(WorldIds.dimension(world));
        IChunkGenerator raw = world.getChunkProvider().getChunkGenerator();
		if (raw == null || raw.getClass() != ChunkGeneratorOverworld.class) {
			PLANS.remove(raw);
			PENDING.remove(raw);
			return;
		}

		ChunkGeneratorOverworld generator = (ChunkGeneratorOverworld) raw;
		IBlockState nativeFluid;
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

	static void capture(ChunkGeneratorEvent.ReplaceBiomeBlocks event) {
		if (WorldgenBenchmark.isVanillaBaseline() || event.getWorld() == null) return;
		Plan plan = PLANS.get(event.getGenerator());
		if (plan == null || !plan.deferred) return;
		BitSet mask = captureMask(event.getChunk(), plan.nativeFluid,
				event.getWorld().getSeaLevel());
		if (!mask.isEmpty()) {
			PENDING.put(event.getGenerator(), event.getChunk().getPos().x, event.getChunk().getPos().z,
					new Pending(plan.nativeFluid, plan.selectedFluid, mask));
		}
	}

	static boolean apply(IWorld region, IChunk chunk) {
		if (!(region.getWorld() instanceof WorldServer)) return false;
        WorldServer world = (WorldServer) region.getWorld();
		IChunkGenerator generator = ((WorldServer) world).getChunkProvider().getChunkGenerator();
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
			IBlockState current = section.get(x, y & 15, z);
			if (!sameState(current, pending.nativeFluid)) continue;
			section.set(x, y & 15, z, pending.selectedFluid);
			changed++;
		}
		return changed;
	}

	static BitSet captureMask(IChunk primer, IBlockState nativeFluid, int seaLevel) {
        int maximumY = Math.max(0, Math.min(256, seaLevel));
		BitSet result = new BitSet(16 * 16 * maximumY);
        if (!nativeFluid.isAir() && (primer.getClass() == ChunkPrimer.class || primer.getClass() == Chunk.class)) {
            ChunkSection[] sections = primer.getSections();
            for (int base = 0; base < maximumY; base += 16) {
                ChunkSection section = sections[base >> 4];
                if (section == null) continue;
                int height = Math.min(16, maximumY - base);
                for (int y = 0; y < height; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    if (sameState(section.get(x, y, z), nativeFluid)) result.set(index(x, base + y, z));
                }
            }
            return result;
        }
        // Custom chunk views may reinterpret reads; do not bypass their implementation.
		net.minecraft.util.math.BlockPos.MutableBlockPos cursor = new net.minecraft.util.math.BlockPos.MutableBlockPos();
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

	static boolean lightCompatible(IBlockState nativeFluid, IBlockState selectedFluid) {
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
			for (Map.Entry<ChunkGeneratorOverworld, IBlockState> entry : NATIVE_FLUIDS.entrySet()) {
				entry.getKey().defaultFluid = entry.getValue();
			}
			NATIVE_FLUIDS.clear();
		}
		DIMENSIONS.clear();
        PLANS.clear();
		PENDING.clear();
	}

	static int pendingCount() { return PENDING.size(); }

	private static boolean sameState(IBlockState first, IBlockState second) {
		return first == second || (first != null && first.equals(second));
	}

	private static final class Plan {
		final IBlockState nativeFluid;
		final IBlockState selectedFluid;
		final boolean deferred;

		Plan(IBlockState nativeFluid, IBlockState selectedFluid, boolean deferred) {
			this.nativeFluid = nativeFluid;
			this.selectedFluid = selectedFluid;
			this.deferred = deferred;
		}
	}

	static final class Pending {
		final IBlockState nativeFluid;
		final IBlockState selectedFluid;
		final BitSet mask;

		Pending(IBlockState nativeFluid, IBlockState selectedFluid, BitSet mask) {
			this.nativeFluid = nativeFluid;
			this.selectedFluid = selectedFluid;
			this.mask = (BitSet) mask.clone();
		}
	}

	static final class PendingMasks {
		private final Map<ChunkKey, Pending> masks = new ConcurrentHashMap<>();

		void put(IChunkGenerator generator, int x, int z, Pending pending) {
			masks.put(new ChunkKey(generator, x, z), pending);
		}

		Pending take(IChunkGenerator generator, int x, int z) {
			return masks.remove(new ChunkKey(generator, x, z));
		}

		void remove(IChunkGenerator generator) {
			for (ChunkKey key : masks.keySet()) {
				if (key.generator == generator) masks.remove(key);
			}
		}

		void clear() { masks.clear(); }
		int size() { return masks.size(); }
	}

	private static final class ChunkKey {
		final IChunkGenerator generator;
		final int x;
		final int z;

		ChunkKey(IChunkGenerator generator, int x, int z) {
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
