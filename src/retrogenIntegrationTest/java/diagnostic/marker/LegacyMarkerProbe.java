package diagnostic.marker;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.orespawn.worldgen.OreRetrogenManager;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

/** Observe real saves in disposable flat worlds without changing retrogen bookkeeping. */
@Mod(modid="markeraudit", name="Retrogen Persistence Audit", version="1", dependencies="required-after:orespawn")
public final class LegacyMarkerProbe {
    private final JsonObject result = new JsonObject();
    private final JsonArray events = new JsonArray();
    private final List<Chunk> chunks = new ArrayList<>();
    private MinecraftServer server;
    private WorldServer level;
    private int ticks;
    private boolean finished;

    @EventHandler
    public void setup(FMLPreInitializationEvent event) { MinecraftForge.EVENT_BUS.register(this); }

    @EventHandler
    public void start(FMLServerStartedEvent event) {
        server = FMLCommonHandler.instance().getMinecraftServerInstance();
        level = server.getWorld(0);
        try {
            for (String rule : new String[] {"doMobSpawning", "doDaylightCycle", "doWeatherCycle", "doFireTick"}) {
                level.getGameRules().setOrCreateGameRule(rule, "false");
            }
            level.getGameRules().setOrCreateGameRule("randomTickSpeed", "0");
            chunks.add(level.getChunkFromChunkCoords(-1, 0));
            chunks.add(level.getChunkFromChunkCoords(0, -1));
            result.addProperty("ore_count_at_start", count());
            result.addProperty("queued_at_start", OreRetrogenManager.queuedCount());
            result.addProperty("revision", WorldGeologyProfileManager.activeProfile().generationRevision());
            result.addProperty("force", WorldGeologyProfileManager.activeProfile().forceOreRetrogen());
            result.addProperty("enabled", WorldGeologyProfileManager.activeProfile().oreRetrogenEnabled());
            System.out.println("LEGACY_MARKER_STARTED " + result);
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void load(ChunkDataEvent.Load event) { observe("load", event); }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void save(ChunkDataEvent.Save event) { observe("save", event); }

    private void observe(String action, ChunkDataEvent event) {
        Chunk chunk = event.getChunk();
        if (chunk.getWorld().provider.getDimension() != 0 || !((chunk.x == -1 && chunk.z == 0)
                || (chunk.x == 0 && chunk.z == -1))) return;
        JsonObject row = new JsonObject();
        row.addProperty("action", action);
        row.addProperty("x", chunk.x);
        row.addProperty("z", chunk.z);
        NBTTagCompound root = event.getData().getCompoundTag("OreSpawn");
        row.addProperty("marker_present", root.hasKey("generation_revision", 3));
        row.addProperty("revision", root.getInteger("generation_revision"));
        events.add(row);
    }

    private long count() {
        long total = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Chunk chunk : chunks) for (int y=0; y<256; y++) for (int z=0; z<16; z++) for (int x=0; x<16; x++) {
            if (chunk.getBlockState(pos.setPos(chunk.x * 16 + x, y, chunk.z * 16 + z)).getBlock() == Blocks.EMERALD_BLOCK) total++;
        }
        return total;
    }

    private String blocksHash() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Chunk chunk : chunks) for (int y=0; y<256; y++) for (int z=0; z<16; z++) for (int x=0; x<16; x++) {
            String state = chunk.getBlockState(pos.setPos(chunk.x * 16 + x, y, chunk.z * 16 + z)).toString();
            digest.update(state.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        StringBuilder text = new StringBuilder();
        for (byte value : digest.digest()) text.append(String.format("%02x", value & 255));
        return text.toString();
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null || finished || ++ticks < 100
                || OreRetrogenManager.queuedCount() != 0) return;
        finished = true;
        try {
            result.addProperty("ore_count_after_queue", count());
            result.addProperty("blocks_after_queue", blocksHash());
            int removed = 0;
            if (Boolean.getBoolean("marker.mine")) {
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (Chunk chunk : chunks) {
                    int perChunk = 0;
                    for (int y=0; y<64 && perChunk<10; y++) for (int z=0; z<16 && perChunk<10; z++) for (int x=0; x<16 && perChunk<10; x++) {
                        pos.setPos(chunk.x * 16 + x, y, chunk.z * 16 + z);
                        if (chunk.getBlockState(pos).getBlock() == Blocks.EMERALD_BLOCK) {
                            level.setBlockState(pos, Blocks.STONE.getDefaultState(), 2);
                            perChunk++;
                            removed++;
                        }
                    }
                }
            }
            result.addProperty("removed", removed);
            result.addProperty("ore_count_before_save", count());
            result.addProperty("blocks_before_save", blocksHash());
            for (Chunk chunk : chunks) chunk.setModified(true);
            server.saveAllWorlds(true);
            result.add("events", events);
            Path output = Paths.get(System.getProperty("marker.output"));
            Files.createDirectories(output.getParent());
            Files.write(output, new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));
            System.out.println("LEGACY_MARKER_RESULT " + result);
            server.initiateShutdown();
        } catch (Throwable failure) { fail(failure); }
    }

    private void fail(Throwable failure) {
        finished = true;
        failure.printStackTrace();
        if (server != null) server.initiateShutdown();
    }
}
