package zone.moddev.mc.orespawn.worldgen;

import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.IFluidState;
import net.minecraft.particles.IParticleData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.EnumLightType;
import net.minecraft.world.ITickList;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.WorldGenRegion;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.minecraft.world.storage.WorldSavedDataStorage;

/** A native decorator's read view. Writes and all other world operations stay real. */
final class NativeAquiferView implements IWorld {
    private final IWorld world;
    private final IBlockState nativeFluid, selectedFluid;
    private final boolean directFluidReads;

    NativeAquiferView(IWorld world, IBlockState nativeFluid, IBlockState selectedFluid) {
        this.world = world;
        this.nativeFluid = nativeFluid;
        this.selectedFluid = selectedFluid;
        this.directFluidReads = world.getClass() == WorldGenRegion.class;
    }

    @Override public IBlockState getBlockState(BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return state.equals(selectedFluid) ? nativeFluid : state;
    }

    @Override public IFluidState getFluidState(BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.equals(selectedFluid)) return nativeFluid.getFluidState();
        // The native region obtains its fluid from this same block state. A custom
        // world view can override that relationship, so keep its own fluid lookup.
        return directFluidReads ? state.getFluidState() : world.getFluidState(pos);
    }

    @Override public TileEntity getTileEntity(BlockPos pos) { return world.getTileEntity(pos); }
    @Override public boolean isAirBlock(BlockPos pos) { return world.isAirBlock(pos); }
    @Override public boolean setBlockState(BlockPos pos, IBlockState state, int flags) { return world.setBlockState(pos, state, flags); }
    @Override public boolean removeBlock(BlockPos pos) { return world.removeBlock(pos); }
    @Override public boolean destroyBlock(BlockPos pos, boolean drops) { return world.destroyBlock(pos, drops); }
    @Override public void setLightFor(EnumLightType type, BlockPos pos, int light) { world.setLightFor(type, pos, light); }
    @Override public boolean spawnEntity(Entity entity) { return world.spawnEntity(entity); }
    @Override public long getSeed() { return world.getSeed(); }
    @Override public ITickList<Block> getPendingBlockTicks() { return world.getPendingBlockTicks(); }
    @Override public ITickList<Fluid> getPendingFluidTicks() { return world.getPendingFluidTicks(); }
    @Override public IChunk getChunk(int x, int z) { return world.getChunk(x, z); }
    @Override public World getWorld() { return world.getWorld(); }
    @Override public WorldInfo getWorldInfo() { return world.getWorldInfo(); }
    @Override public DifficultyInstance getDifficultyForLocation(BlockPos pos) { return world.getDifficultyForLocation(pos); }
    @Override public IChunkProvider getChunkProvider() { return world.getChunkProvider(); }
    @Override public ISaveHandler getSaveHandler() { return world.getSaveHandler(); }
    @Override public Random getRandom() { return world.getRandom(); }
    @Override public void notifyNeighbors(BlockPos pos, Block block) { world.notifyNeighbors(pos, block); }
    @Override public BlockPos getSpawnPoint() { return world.getSpawnPoint(); }
    @Override public void playSound(EntityPlayer player, BlockPos pos, SoundEvent sound, SoundCategory category, float volume, float pitch) {
        world.playSound(player, pos, sound, category, volume, pitch);
    }
    @Override public void spawnParticle(IParticleData particle, double x, double y, double z, double dx, double dy, double dz) {
        world.spawnParticle(particle, x, y, z, dx, dy, dz);
    }
    @Override public Biome getBiome(BlockPos pos) { return world.getBiome(pos); }
    @Override public int getLightFor(EnumLightType type, BlockPos pos) { return world.getLightFor(type, pos); }
    @Override public int getLightSubtracted(BlockPos pos, int amount) { return world.getLightSubtracted(pos, amount); }
    @Override public boolean isChunkLoaded(int x, int z, boolean allowEmpty) { return world.isChunkLoaded(x, z, allowEmpty); }
    @Override public boolean canSeeSky(BlockPos pos) { return world.canSeeSky(pos); }
    @Override public int getHeight(Heightmap.Type type, int x, int z) { return world.getHeight(type, x, z); }
    @Override public EntityPlayer getClosestPlayer(double x, double y, double z, double distance, Predicate<Entity> predicate) {
        return world.getClosestPlayer(x, y, z, distance, predicate);
    }
    @Override public int getSkylightSubtracted() { return world.getSkylightSubtracted(); }
    @Override public WorldBorder getWorldBorder() { return world.getWorldBorder(); }
    @Override public boolean checkNoEntityCollision(Entity entity, VoxelShape shape) { return world.checkNoEntityCollision(entity, shape); }
    @Override public int getStrongPower(BlockPos pos, EnumFacing direction) { return world.getStrongPower(pos, direction); }
    @Override public boolean isRemote() { return world.isRemote(); }
    @Override public int getSeaLevel() { return world.getSeaLevel(); }
    @Override public Dimension getDimension() { return world.getDimension(); }
    @Override public WorldSavedDataStorage getMapStorage() { return world.getMapStorage(); }
}
