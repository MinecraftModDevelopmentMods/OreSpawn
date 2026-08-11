package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.event.terraingen.DecorateBiomeEvent;
import net.minecraftforge.event.terraingen.OreGenEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import com.mcmoddev.orespawn.compat.LegacyOs3Bridge;

/**
 * Ordered Forge 1.12 generation coordinator. Terrain events provide the normal
 * early path; IWorldGenerator is a deduplicated fallback for custom generators
 * which omit those Forge hooks.
 */
public final class OreSpawnWorldGenerator implements IWorldGenerator {
	public static final OreSpawnWorldGenerator INSTANCE = new OreSpawnWorldGenerator();

	private final Set<ChunkKey> earlyComplete = concurrentSet();
	private final Set<ChunkKey> oreComplete = concurrentSet();
	private final Set<ChunkKey> legacyComplete = concurrentSet();

	private OreSpawnWorldGenerator() {
	}

	@SubscribeEvent
	public void beforeBiomeDecoration(DecorateBiomeEvent.Pre event) {
		if (WorldgenBenchmark.isVanillaBaseline()) return;
		ChunkPos pos = event.getChunkPos();
		generateEarly(event.getWorld(), pos.x, pos.z, event.getRand());
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void replaceVanillaSprings(DecorateBiomeEvent.Decorate event) {
		if (WorldgenBenchmark.isVanillaBaseline()) return;
		VanillaSpringCompatibility.replaceVanillaSpringPass(event);
	}

	@SubscribeEvent
	public void beforeVanillaOres(OreGenEvent.Pre event) {
		if (WorldgenBenchmark.isVanillaBaseline()) return;
		ChunkPos pos = new ChunkPos(event.getPos());
		generateOres(event.getWorld(), pos.x, pos.z, event.getRand());
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void filterVanillaOre(OreGenEvent.GenerateMinable event) {
		if (WorldgenBenchmark.isVanillaBaseline()) return;
		ResourceLocation dimension = WorldIds.dimension(event.getWorld());
		if (WorldGeologyProfileManager.activeProfile().suppressAllOreFeatures()) {
			event.setResult(Event.Result.DENY);
			return;
		}
		Block output = vanillaOutput(event.getType());
		if (output != null && OreSpawnOreGeneration.takesOverVanillaOre(dimension, output)) {
			event.setResult(Event.Result.DENY);
		}
	}

	@Override
	public void generate(Random random, int chunkX, int chunkZ, World world,
			IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
		if (WorldgenBenchmark.isVanillaBaseline()) return;
		generateEarly(world, chunkX, chunkZ, random);
		generateOres(world, chunkX, chunkZ, random);
		ChunkKey key = new ChunkKey(WorldIds.dimension(world), chunkX, chunkZ);
		if (legacyComplete.add(key)) {
			LegacyOs3Bridge.generate(random, chunkX, chunkZ, world, chunkGenerator, chunkProvider);
		}
	}

	private void generateEarly(World world, int chunkX, int chunkZ, Random random) {
		ChunkKey key = new ChunkKey(WorldIds.dimension(world), chunkX, chunkZ);
		if (!earlyComplete.add(key)) return;
		Chunk chunk = world.getChunkProvider().provideChunk(chunkX, chunkZ);
		// Explicit order is the 1.12 equivalent of LOCAL_MODIFICATIONS.
		StoneReplacer.FEATURE.generate(world, chunk, random);
		BiomeSurfaceFeature.FEATURE.generate(world, chunk, random);
		FluidDepositFeature.FEATURE.generate(world, chunk, random);
		if (world instanceof net.minecraft.world.WorldServer) {
			FlatBedrockFeature.FEATURE.generate(world, chunk, random);
		}
	}

	private void generateOres(World world, int chunkX, int chunkZ, Random random) {
		ChunkKey key = new ChunkKey(WorldIds.dimension(world), chunkX, chunkZ);
		if (!oreComplete.add(key)) return;
		Chunk chunk = world.getChunkProvider().provideChunk(chunkX, chunkZ);
		OreSpawnOreGeneration.FEATURE.generate(world, chunk, random);
	}

	public void clear() {
		earlyComplete.clear();
		oreComplete.clear();
		legacyComplete.clear();
	}

	private static Set<ChunkKey> concurrentSet() {
		return Collections.newSetFromMap(new ConcurrentHashMap<ChunkKey, Boolean>());
	}

	private static Block vanillaOutput(OreGenEvent.GenerateMinable.EventType type) {
		switch (type) {
		case COAL: return Blocks.COAL_ORE;
		case DIAMOND: return Blocks.DIAMOND_ORE;
		case GOLD: return Blocks.GOLD_ORE;
		case IRON: return Blocks.IRON_ORE;
		case LAPIS: return Blocks.LAPIS_ORE;
		case REDSTONE: return Blocks.REDSTONE_ORE;
		case QUARTZ: return Blocks.QUARTZ_ORE;
		case EMERALD: return Blocks.EMERALD_ORE;
		default: return null;
		}
	}

	private static final class ChunkKey {
		final ResourceLocation dimension;
		final int x;
		final int z;

		ChunkKey(ResourceLocation dimension, int x, int z) {
			this.dimension = dimension;
			this.x = x;
			this.z = z;
		}

		@Override public int hashCode() {
			return ((31 * dimension.hashCode()) + x) * 31 + z;
		}
		@Override public boolean equals(Object value) {
			if (this == value) return true;
			if (!(value instanceof ChunkKey)) return false;
			ChunkKey other = (ChunkKey) value;
			return x == other.x && z == other.z && dimension.equals(other.dimension);
		}
	}
}
