package zone.moddev.mc.orespawn.worldgen;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IWorld;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import zone.moddev.mc.orespawn.commands.admin.AdminLighting;

/** Forge 25 saves chunk capabilities but never posts its chunk-data load event. */
public final class ChunkBookkeeping {
    private static final ResourceLocation ID = new ResourceLocation("orespawn", "bookkeeping");
    private static final Map<Chunk, State> STATES = Collections.synchronizedMap(new WeakHashMap<>());
    private ChunkBookkeeping() { }

    public static void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(Chunk.class, ChunkBookkeeping::attach);
        MinecraftForge.EVENT_BUS.addListener(ChunkBookkeeping::loaded);
        MinecraftForge.EVENT_BUS.addListener(ChunkBookkeeping::dataLoaded);
    }

    public static void attach(AttachCapabilitiesEvent<Chunk> event) {
        Chunk chunk = event.getObject();
        State state = new State();
        STATES.put(chunk, state);
        event.addCapability(ID, new ICapabilitySerializable<NBTTagCompound>() {
            @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, EnumFacing side) {
                return LazyOptional.empty();
            }
            @Override public NBTTagCompound serializeNBT() { return snapshot(chunk); }
            @Override public void deserializeNBT(NBTTagCompound data) {
                state.data = data.copy();
                state.restored = true;
            }
        });
    }

    public static void loaded(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof Chunk) || !(event.getWorld() instanceof WorldServer)) return;
        Chunk chunk = (Chunk) event.getChunk();
        State state = STATES.get(chunk);
        if (state != null && state.handled) return;
        NBTTagCompound data = state != null && state.restored ? state.data : legacyData(chunk);
        if (data == null) return;
        accept(chunk, data);
    }

    // Also accept the advertised event if a later Forge build restores it.
    public static void dataLoaded(ChunkDataEvent.Load event) {
        if (!(event.getChunk() instanceof Chunk)) return;
        accept((Chunk) event.getChunk(), event.getData());
    }

    private static void accept(Chunk chunk, NBTTagCompound data) {
        State state = STATES.get(chunk);
        if (state != null) {
            if (state.handled) return;
            state.handled = true;
        }
        ChunkDataEvent.Load event = new ChunkDataEvent.Load(chunk, data);
        OreRetrogenManager.onChunkLoad(event);
        AdminLighting.onLoad(event);
    }

    static NBTTagCompound snapshot(Chunk chunk) {
        NBTTagCompound data = new NBTTagCompound();
        data.setInt("format", 1);
        ChunkDataEvent.Save event = new ChunkDataEvent.Save(chunk, data);
        OreRetrogenManager.onChunkSave(event);
        AdminLighting.onSave(event);
        return data;
    }

    private static NBTTagCompound legacyData(Chunk chunk) {
        WorldServer world = (WorldServer) chunk.getWorldForge();
        if (OreRetrogenManager.generatedThisSession(world, chunk.getPos())) return snapshot(chunk);
        if (!(world.getChunkProvider().chunkLoader instanceof AnvilChunkLoader)) {
            // Custom loaders can still supply ChunkDataEvent.Load or persist the attached capability.
            return new NBTTagCompound();
        }
        try {
            // Read the loader's pending-save cache before disk. Do not reload a Chunk or its entities.
            NBTTagCompound root = (NBTTagCompound) LegacyReader.READ.invoke(
                    world.getChunkProvider().chunkLoader, world, chunk.x, chunk.z);
            return root == null ? new NBTTagCompound() : root;
        } catch (ReflectiveOperationException exception) {
            org.apache.logging.log4j.LogManager.getLogger().error(
                    "Cannot recover OreSpawn bookkeeping for {}; retrogen was not queued", chunk.getPos(), exception);
            return null;
        }
    }

    public static void clear() { STATES.clear(); }

    private static final class State {
        NBTTagCompound data;
        boolean restored, handled;
    }
    private static final class LegacyReader {
        static final Method READ = ObfuscationReflectionHelper.findMethod(AnvilChunkLoader.class,
                "func_208030_a", IWorld.class, int.class, int.class);
    }
}
