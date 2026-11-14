package zone.moddev.mc.orespawn.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkDataEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;

/** Runs only in its named disposable world, using real saves and separate server processes. */
@Mod("parityprobe")
public final class ParityProbe {
    private static final String PHASE = System.getProperty("parity.phase", "Fresh");
    private static final int X = -64, Z = 67;
    private WorldServer world;
    private Chunk chunk;
    private int ticks;
    private int removedCapabilities;
    private boolean ready;
    public ParityProbe() {
        String mode = System.getProperty("parity.mode", "retrogen");
        if (mode.equals("bench")) new BenchmarkProbe();
        else if (mode.equals("fluidbench")) { new GenerationProbe("fluids"); new BenchmarkProbe(); }
        else if (mode.equals("formations") || mode.equals("fluids")) new GenerationProbe(mode);
        else MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void configure(FMLServerAboutToStartEvent event) {
        JsonObject root = WorldGeologyProfileManager.activeProfile().rootCopy();
        root.addProperty("manage_vanilla_ores", false);
        JsonObject ores = new JsonObject(), ore = new JsonObject(), dimensions = new JsonObject(), rule = new JsonObject();
        ore.addProperty("block", "minecraft:gold_block"); ore.addProperty("enabled", true); ore.addProperty("retrogen", true);
        rule.addProperty("enabled", true); rule.addProperty("min_y", 5); rule.addProperty("max_y", 32);
        rule.addProperty("frequency", 8); rule.addProperty("quantity", 32);
        com.google.gson.JsonArray hosts = new com.google.gson.JsonArray();
        hosts.add("minecraft:stone"); rule.add("host_blocks", hosts);
        dimensions.add("minecraft:overworld", rule); ore.add("dimensions", dimensions); ores.add("parityprobe:deposit", ore);
        root.add("ores", ores); root.add("ore_source_policies", new JsonObject());
        JsonObject retrogen = new JsonObject();
        retrogen.addProperty("enabled", !PHASE.equals("Off"));
        retrogen.addProperty("force", PHASE.equals("Force")); retrogen.addProperty("chunks_per_tick", 16);
        retrogen.addProperty("revision", PHASE.equals("Off") ? 999 : newerRevision() ? 124 : 123);
        root.add("retrogen", retrogen);
        WorldGeologyProfileManager.applyBenchmarkProfile(WorldGeologyProfileManager.activeProfile().withRoot(root));
    }

    private boolean newerRevision() {
        return PHASE.equals("Revision") || PHASE.equals("RevisionReload") || PHASE.equals("Force") || PHASE.equals("ForceOff");
    }

    @SubscribeEvent
    public void started(FMLServerStartedEvent event) {
        try {
            world = event.getServer().getWorld(DimensionType.OVERWORLD);
            require(world.getSaveHandler().getWorldDirectory().getName().equals("retrogen-parity-world"), "Not a disposable world");
            for (int x = X - 2; x <= X + 2; x++) for (int z = Z - 2; z <= Z + 2; z++) world.getChunk(x, z);
            chunk = world.getChunk(X, Z);
            world.getGameRules().setOrCreateGameRule("randomTickSpeed", "0", event.getServer());
            if (PHASE.equals("Fresh")) {
                for (int y = 5; y <= 32; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                    put(x, y, z, Blocks.STONE.getDefaultState());
                OreRetrogenManager.clear(); OreRetrogenManager.refreshWorldConfig();
                require(OreRetrogenManager.queueLoadedArea(world, chunk.getPos(), 0) == 1, "Explicit initial queue failed");
            }
            ready = true;
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void tick(TickEvent.ServerTickEvent event) {
        if (!ready || event.phase != TickEvent.Phase.END || ++ticks < 80) return;
        try {
            long count = count();
            boolean shouldPlace = PHASE.equals("Fresh") || PHASE.equals("Revision") || PHASE.equals("Force");
            require(shouldPlace ? count > 0 : count == 0, "Mined ore reappeared or requested retrogen did not run: " + PHASE + " count=" + count);
            NBTTagCompound marker = new NBTTagCompound();
            OreRetrogenManager.onChunkSave(new ChunkDataEvent.Save(chunk, marker));
            int revision = marker.getCompound("OreSpawn").getInt("generation_revision");
            require(revision == (newerRevision() ? 124 : 123), "Saved the configured revision instead of completed work: " + revision);
            if (shouldPlace) {
                // Mine into the same host rock so a repeated pass would demonstrably refill it.
                for (int y = 5; y <= 32; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                    if (chunk.getBlockState(new BlockPos(X * 16 + x, y, Z * 16 + z)).getBlock() == Blocks.GOLD_BLOCK)
                        put(x, y, z, Blocks.STONE.getDefaultState());
            }
            for (int save = 0; save < 3; save++) {
                chunk.markDirty(); world.getChunkProvider().saveChunks(true); world.getChunkProvider().flushToDisk();
            }
            if (PHASE.equals("StripCapability")) require(removedCapabilities >= 3,
                    "Legacy fixture did not save root-only bookkeeping");
            JsonObject result = new JsonObject(); result.addProperty("passed", true); result.addProperty("phase", PHASE);
            result.addProperty("placed_blocks", count); result.addProperty("saved_revision", revision);
            result.addProperty("consecutive_real_saves", 3); result.addProperty("remaining_after_mining", count());
            result.addProperty("legacy_capabilities_removed", removedCapabilities);
            write(Paths.get("retrogen-" + PHASE + ".json"), result);
            ready = false; System.out.println("RETROGEN_PARITY_PASS " + result); world.getServer().initiateShutdown();
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void saveLegacyFixture(ChunkDataEvent.Save event) {
        if (!PHASE.equals("StripCapability") || event.getChunk().getPos().x != X
                || event.getChunk().getPos().z != Z) return;
        NBTTagCompound capabilities = event.getData().getCompound("Level").getCompound("ForgeCaps");
        if (capabilities.contains("orespawn:bookkeeping", 10)) {
            require(event.getData().getCompound("OreSpawn").getInt("generation_revision") == 123,
                    "Legacy root marker was not saved");
            capabilities.removeTag("orespawn:bookkeeping"); removedCapabilities++;
        }
    }

    private long count() {
        long result = 0;
        for (int y = 5; y <= 32; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            if (chunk.getBlockState(new BlockPos(X * 16 + x, y, Z * 16 + z)).getBlock() == Blocks.GOLD_BLOCK) result++;
        return result;
    }
    private void put(int x, int y, int z, net.minecraft.block.state.IBlockState state) {
        ChunkSection[] sections = chunk.getSections();
        if (sections[y >> 4] == null) sections[y >> 4] = new ChunkSection((y >> 4) << 4, true);
        sections[y >> 4].set(x, y & 15, z, state); chunk.markDirty();
    }
    private void fail(Throwable failure) {
        ready = false; failure.printStackTrace();
        try { JsonObject result = new JsonObject(); result.addProperty("failure", failure.toString()); write(Paths.get("retrogen-failed.json"), result); }
        catch (Exception outputFailure) { outputFailure.printStackTrace(); }
        if (world != null) world.getServer().initiateShutdown();
        else throw new AssertionError(failure);
    }
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void write(Path path, JsonObject value) throws Exception {
        Files.write(path, value.toString().getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.CREATE_NEW);
    }
}
