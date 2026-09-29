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
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkProviderOverworld;
import net.minecraftforge.event.terraingen.ChunkGeneratorEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Replaces the native Overworld aquifer fluid after vanilla has built and lit
 * the chunk. The primer mask keeps the substitution limited to terrain fluid;
 * lakes, springs, and later decorators are deliberately outside it.
 */
final class AquiferMaterialSubstitution {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Map<ChunkProviderOverworld, IBlockState> NATIVE_FLUIDS =
			Collections.synchronizedMap(new IdentityHashMap<ChunkProviderOverworld, IBlockState>());
	private static final Map<IChunkGenerator, Plan> PLANS =
			Collections.synchronizedMap(new IdentityHashMap<IChunkGenerator, Plan>());
	private static final PendingMasks PENDING = new PendingMasks();

	private AquiferMaterialSubstitution() {
	}

	static void configure(WorldServer world, IBlockState selected) {
		IChunkGenerator raw = world.getChunkProvider().chunkGenerator;
		if (!(raw instanceof ChunkProviderOverworld)) {
			PLANS.remove(raw);
			PENDING.remove(raw);
			return;
		}

		ChunkProviderOverworld generator = (ChunkProviderOverworld) raw;
		IBlockState nativeFluid;
		synchronized (NATIVE_FLUIDS) {
			nativeFluid = NATIVE_FLUIDS.get(generator);
			if (nativeFluid == null) {
				nativeFluid = generator.oceanBlock;
				NATIVE_FLUIDS.put(generator, nativeFluid);
			}
		}
		generator.oceanBlock = nativeFluid;
		PLANS.remove(raw);
		PENDING.remove(raw);

		if (selected == null || sameState(selected, nativeFluid)) return;
		if (lightCompatible(nativeFluid, selected)) {
			PLANS.put(raw, new Plan(nativeFluid, selected, true));
			return;
		}

		generator.oceanBlock = selected;
		PLANS.put(raw, new Plan(nativeFluid, selected, false));
		LOGGER.warn("Dimension '{}' uses aquifer fluid '{}' with opacity/light {}/{} instead of "
				+ "native '{}' at {}/{}; using the compatible generator path, which may generate more slowly",
				WorldIds.dimension(world), selected, selected.getLightOpacity(), selected.getLightValue(),
				nativeFluid, nativeFluid.getLightOpacity(), nativeFluid.getLightValue());
	}

	static void capture(ChunkGeneratorEvent.ReplaceBiomeBlocks event) {
		if (WorldgenBenchmark.isVanillaBaseline() || event.getWorld() == null) return;
		Plan plan = PLANS.get(event.getGenerator());
		if (plan == null || !plan.deferred) return;
		BitSet mask = captureMask(event.getPrimer(), plan.nativeFluid,
				event.getWorld().getSeaLevel());
		if (!mask.isEmpty()) {
			PENDING.put(event.getGenerator(), event.getX(), event.getZ(),
					new Pending(plan.nativeFluid, plan.selectedFluid, mask));
		}
	}

	static boolean apply(World world, Chunk chunk) {
		if (!(world instanceof WorldServer)) return false;
		IChunkGenerator generator = ((WorldServer) world).getChunkProvider().chunkGenerator;
		Pending pending = PENDING.take(generator, chunk.xPosition, chunk.zPosition);
		if (pending == null) return false;

		ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
		int changed = applyToSections(pending, sections);
		if (changed > 0) ChunkAccessCompat.markChanged(chunk);
		return changed > 0;
	}

	static int applyToSections(Pending pending, ExtendedBlockStorage[] sections) {
		int changed = 0;
		for (int index = pending.mask.nextSetBit(0); index >= 0;
				index = pending.mask.nextSetBit(index + 1)) {
			int y = localY(index);
			ExtendedBlockStorage section = sections[y >> 4];
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

	static BitSet captureMask(ChunkPrimer primer, IBlockState nativeFluid, int seaLevel) {
		BitSet result = new BitSet(16 * 16 * 256);
		int maximumY = Math.max(0, Math.min(256, seaLevel));
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = 0; y < maximumY; y++) {
					if (sameState(primer.getBlockState(x, y, z), nativeFluid)) {
						result.set(index(x, y, z));
					}
				}
			}
		}
		return result;
	}

	static boolean lightCompatible(IBlockState nativeFluid, IBlockState selectedFluid) {
		return nativeFluid.getLightOpacity() == selectedFluid.getLightOpacity()
				&& nativeFluid.getLightValue() == selectedFluid.getLightValue();
	}

	static int index(int x, int y, int z) {
		return (x << 12) | (z << 8) | y;
	}

	static int localX(int index) { return (index >> 12) & 15; }
	static int localY(int index) { return index & 255; }
	static int localZ(int index) { return (index >> 8) & 15; }

	static void clear() {
		synchronized (NATIVE_FLUIDS) {
			for (Map.Entry<ChunkProviderOverworld, IBlockState> entry : NATIVE_FLUIDS.entrySet()) {
				entry.getKey().oceanBlock = entry.getValue();
			}
			NATIVE_FLUIDS.clear();
		}
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
