package local.paritybench;

import com.google.gson.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

/** Measures the generation thread in matched disposable worlds, with hashes outside the timing. */
@Mod(modid="paritybench", name="Local generation comparison", version="1", dependencies="required-after:orespawn")
public final class BenchmarkProbe {
    @Mod.EventHandler public void run(FMLServerStartedEvent event) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        try {
            WorldServer world = server.getWorld(0);
            if (!world.getSaveHandler().getWorldDirectory().getName().equals("benchmark-world")) throw new AssertionError("Not a disposable save");
            world.getGameRules().setOrCreateGameRule("randomTickSpeed", "0");
            ThreadMXBean thread = ManagementFactory.getThreadMXBean();
            if (!thread.isCurrentThreadCpuTimeSupported()) throw new AssertionError("Thread CPU timing unavailable");
            thread.setThreadCpuTimeEnabled(true);
            com.sun.management.OperatingSystemMXBean process = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            java.lang.management.CompilationMXBean compiler = ManagementFactory.getCompilationMXBean();
            for (int pass = 0; pass < 20; pass++) generate(world, 256 + pass * 32, -256, 4);
            JsonObject result = new JsonObject(); JsonArray samples = new JsonArray();
            result.addProperty("seed", world.getSeed()); result.addProperty("warmup_chunks", 1620);
            result.addProperty("measured_chunks_per_sample", 289);
            for (int pass = 0; pass < 5; pass++) {
                int cx = 1024 + pass * 32, cz = -512;
                long jit = compiler.getTotalCompilationTime(), cpu = thread.getCurrentThreadCpuTime();
                long jvm = process.getProcessCpuTime(), started = System.nanoTime();
                generate(world, cx, cz, 8);
                long elapsed = System.nanoTime() - started, cpuElapsed = thread.getCurrentThreadCpuTime() - cpu;
                JsonObject row = new JsonObject();
                row.addProperty("wall_ns", elapsed); row.addProperty("thread_cpu_ns", cpuElapsed);
                row.addProperty("whole_jvm_cpu_ns", process.getProcessCpuTime() - jvm);
                row.addProperty("compilation_ms", compiler.getTotalCompilationTime() - jit);
                row.addProperty("center_x", cx); row.addProperty("center_z", cz);
                MessageDigest hash = MessageDigest.getInstance("SHA-256");
                Map<IBlockState, byte[]> keys = new IdentityHashMap<>();
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int z = cz - 8; z <= cz + 8; z++) for (int x = cx - 8; x <= cx + 8; x++) {
                    Chunk chunk = world.getChunkProvider().getLoadedChunk(x, z);
                    hash.update(chunk.getBiomeArray());
                    for (int y = 0; y < 256; y++) for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                        IBlockState state = chunk.getBlockState(pos.setPos(x * 16 + lx, y, z * 16 + lz));
                        hash.update(keys.computeIfAbsent(state, value -> value.toString().getBytes(StandardCharsets.UTF_8)));
                        hash.update((byte) 0);
                    }
                }
                StringBuilder hex = new StringBuilder();
                for (byte value : hash.digest()) hex.append(String.format("%02x", value & 255));
                row.addProperty("block_biome_hash", hex.toString()); samples.add(row);
                System.out.println("PARITY_BENCH_SAMPLE " + row);
            }
            int structures = 0;
            for (String name : new String[]{"Mineshaft", "Village", "Stronghold", "Temple", "Monument", "Mansion"}) {
                net.minecraft.world.gen.structure.MapGenStructureData data = (net.minecraft.world.gen.structure.MapGenStructureData)
                        world.getPerWorldStorage().getOrLoadData(net.minecraft.world.gen.structure.MapGenStructureData.class, name);
                if (data != null) structures += data.getTagCompound().getKeySet().size();
            }
            if (structures == 0) throw new AssertionError("The benchmark must exercise native structures");
            result.addProperty("structure_records", structures);
            result.add("samples", samples); result.addProperty("passed", true);
            Files.write(Paths.get("benchmark.json"), result.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable failure) { failure.printStackTrace(); }
        server.initiateShutdown();
    }

    private static void generate(WorldServer world, int cx, int cz, int radius) {
        for (int z = cz - radius; z <= cz + radius; z++) for (int x = cx - radius; x <= cx + radius; x++)
            world.getChunkProvider().provideChunk(x, z);
    }
}
