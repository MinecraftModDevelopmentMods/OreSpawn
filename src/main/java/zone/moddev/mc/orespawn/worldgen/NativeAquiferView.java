package zone.moddev.mc.orespawn.worldgen;

import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.IFluidState;
import net.minecraft.particles.IParticleData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.LightType;
import net.minecraft.world.ITickList;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.AbstractChunkProvider;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.WorldGenRegion;
import net.minecraft.world.storage.WorldInfo;

/** A native decorator's read view. Writes and all other world operations stay real. */
final class NativeAquiferView implements IWorld {
    private final IWorld world;
    private final BlockState nativeFluid, selectedFluid;
    private final boolean directFluidReads;

    NativeAquiferView(IWorld world, BlockState nativeFluid, BlockState selectedFluid) {
        this.world = world;
        this.nativeFluid = nativeFluid;
        this.selectedFluid = selectedFluid;
        this.directFluidReads = world.getClass() == WorldGenRegion.class;
    }

    @Override public BlockState getBlockState(BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.equals(selectedFluid) ? nativeFluid : state;
    }

    @Override public IFluidState getFluidState(BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.equals(selectedFluid)) return nativeFluid.getFluidState();
        // The native region obtains its fluid from this same block state. A custom
        // world view can override that relationship, so keep its own fluid lookup.
        return directFluidReads ? state.getFluidState() : world.getFluidState(pos);
    }

    @Override public net.minecraft.world.biome.BiomeManager getBiomeManager() { return world.getBiomeManager(); }
    @Override public Biome getNoiseBiomeRaw(int x, int y, int z) { return world.getNoiseBiomeRaw(x, y, z); }
    @Override public net.minecraft.world.lighting.WorldLightManager getLightManager() { return world.getLightManager(); }
    @Override public TileEntity getTileEntity(BlockPos pos) { return world.getTileEntity(pos); }
    @Override public boolean isAirBlock(BlockPos pos) { return world.isAirBlock(pos); }
    @Override public boolean setBlockState(BlockPos pos, BlockState state, int flags) { return world.setBlockState(pos, state, flags); }
    @Override public boolean removeBlock(BlockPos pos, boolean moving) { return world.removeBlock(pos, moving); }
    @Override public boolean destroyBlock(BlockPos pos, boolean drops) { return world.destroyBlock(pos, drops); }
    @Override public boolean destroyBlock(BlockPos pos, boolean drops, Entity entity) { return world.destroyBlock(pos, drops, entity); }
    @Override public boolean addEntity(Entity entity) { return world.addEntity(entity); }
    @Override public long getSeed() { return world.getSeed(); }
    @Override public ITickList<Block> getPendingBlockTicks() { return world.getPendingBlockTicks(); }
    @Override public ITickList<Fluid> getPendingFluidTicks() { return world.getPendingFluidTicks(); }
    @Override public IChunk getChunk(int x, int z) { return world.getChunk(x, z); }
    @Override public World getWorld() { return world.getWorld(); }
    @Override public WorldInfo getWorldInfo() { return world.getWorldInfo(); }
    @Override public DifficultyInstance getDifficultyForLocation(BlockPos pos) { return world.getDifficultyForLocation(pos); }
    @Override public AbstractChunkProvider getChunkProvider() { return world.getChunkProvider(); }
    @Override public Random getRandom() { return world.getRandom(); }
    @Override public void notifyNeighbors(BlockPos pos, Block block) { world.notifyNeighbors(pos, block); }
    @Override public BlockPos getSpawnPoint() { return world.getSpawnPoint(); }
    @Override public void playSound(PlayerEntity player, BlockPos pos, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        world.playSound(player, pos, sound, category, volume, pitch);
    }
    @Override public void addParticle(IParticleData particle, double x, double y, double z, double dx, double dy, double dz) {
        world.addParticle(particle, x, y, z, dx, dy, dz);
    }
    @Override public Biome getBiome(BlockPos pos) { return world.getBiome(pos); }
    @Override public int getLightFor(LightType type, BlockPos pos) { return world.getLightFor(type, pos); }
    @Override public int getLightSubtracted(BlockPos pos, int amount) { return world.getLightSubtracted(pos, amount); }
    @Override public boolean chunkExists(int x, int z) { return world.chunkExists(x, z); }
    @Override public boolean canBlockSeeSky(BlockPos pos) { return world.canBlockSeeSky(pos); }
    @Override public IChunk getChunk(int x, int z, ChunkStatus status, boolean load) { return world.getChunk(x, z, status, load); }
    @Override public BlockPos getHeight(Heightmap.Type type, BlockPos pos) { return world.getHeight(type, pos); }
    @Override public boolean hasBlockState(BlockPos pos, Predicate<BlockState> predicate) { return predicate.test(getBlockState(pos)); }
    @Override public void playEvent(PlayerEntity player, int event, BlockPos pos, int data) { world.playEvent(player, event, pos, data); }
    @Override public java.util.List<? extends PlayerEntity> getPlayers() { return world.getPlayers(); }
    @Override public java.util.List<Entity> getEntitiesInAABBexcluding(Entity entity, net.minecraft.util.math.AxisAlignedBB box,
            Predicate<? super Entity> predicate) { return world.getEntitiesInAABBexcluding(entity, box, predicate); }
    @Override public <T extends Entity> java.util.List<T> getEntitiesWithinAABB(Class<? extends T> type,
            net.minecraft.util.math.AxisAlignedBB box, Predicate<? super T> predicate) { return world.getEntitiesWithinAABB(type, box, predicate); }
    @Override public int getHeight(Heightmap.Type type, int x, int z) { return world.getHeight(type, x, z); }
    @Override public PlayerEntity getClosestPlayer(double x, double y, double z, double distance, Predicate<Entity> predicate) {
        return world.getClosestPlayer(x, y, z, distance, predicate);
    }
    @Override public int getSkylightSubtracted() { return world.getSkylightSubtracted(); }
    @Override public WorldBorder getWorldBorder() { return world.getWorldBorder(); }
    @Override public boolean checkNoEntityCollision(Entity entity, VoxelShape shape) { return world.checkNoEntityCollision(entity, shape); }
    @Override public int getStrongPower(BlockPos pos, Direction direction) { return world.getStrongPower(pos, direction); }
    @Override public boolean isRemote() { return world.isRemote(); }
    @Override public int getSeaLevel() { return world.getSeaLevel(); }
    @Override public Dimension getDimension() { return world.getDimension(); }
}
