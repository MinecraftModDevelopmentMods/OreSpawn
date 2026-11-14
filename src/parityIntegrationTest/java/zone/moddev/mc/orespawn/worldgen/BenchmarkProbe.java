package zone.moddev.mc.orespawn.worldgen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.security.MessageDigest;
import java.util.Locale;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;

/** The same fixture runs unchanged against the frozen and candidate player jars. */
public final class BenchmarkProbe {
    BenchmarkProbe() { MinecraftForge.EVENT_BUS.register(this); }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void worldLoaded(net.minecraftforge.event.world.WorldEvent.Load event) {
        if (event.getWorld() instanceof WorldServer) { ProbeTags.stabilizeCorals(); ProbeTags.stabilizeFossils(); }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void run(FMLServerStartedEvent event) {
        try {
            WorldServer world = event.getServer().getWorld(DimensionType.OVERWORLD);
            ParityProbe.require(world.getSaveHandler().getWorldDirectory().getName().equals("benchmark-parity-world"), "Not a disposable benchmark");
            ProbeTags.stabilizeCorals();
            ThreadMXBean cpu = ManagementFactory.getThreadMXBean();
            ParityProbe.require(cpu.isCurrentThreadCpuTimeSupported(), "No generation-thread CPU clock");
            cpu.setThreadCpuTimeEnabled(true);
            if (net.minecraftforge.fml.ModList.get().isLoaded("modernmetals"))
                square(world, 6, -20, 2); // The seed/chunk that previously crashed air-exposure checks.
            net.minecraft.block.Block substitute = System.getProperty("parity.mode", "").equals("fluidbench")
                    ? net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.util.ResourceLocation("parityprobe:water")) : null;
            for (int warmup = 0; warmup < 5; warmup++) square(world, -128 - warmup * 64, 128, 5);
            JsonArray passes = new JsonArray();
            for (int pass = 0; pass < 5; pass++) {
                int cx = -256 - pass * 32, cz = 256;
                long compilation = ManagementFactory.getCompilationMXBean().getTotalCompilationTime();
                long processCpu = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime();
                long cpuStarted = generationCpu(cpu), started = System.nanoTime();
                square(world, cx, cz, 5);
                long nanos = System.nanoTime() - started, cpuNanos = generationCpu(cpu) - cpuStarted;
                JsonObject result = new JsonObject(); result.addProperty("pass", pass);
                result.addProperty("elapsed_ns", nanos); result.addProperty("generation_thread_cpu_ns", cpuNanos);
                result.addProperty("whole_jvm_cpu_ns_diagnostic", ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getProcessCpuTime() - processCpu);
                result.addProperty("jit_compilation_ms_diagnostic", ManagementFactory.getCompilationMXBean().getTotalCompilationTime() - compilation);
                result.addProperty("generated_chunks", 121); result.addProperty("measured_chunks", 81);
                MessageDigest blocks = MessageDigest.getInstance("SHA-256"), biomes = MessageDigest.getInstance("SHA-256");
                java.util.Map<net.minecraft.block.state.IBlockState, byte[]> stateKeys = new java.util.IdentityHashMap<>();
                long structureReferences = 0;
                BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
                for (int z = cz - 4; z <= cz + 4; z++) for (int x = cx - 4; x <= cx + 4; x++) {
                    Chunk chunk = world.getChunk(x, z);
                    for (it.unimi.dsi.fastutil.longs.LongSet references : chunk.getStructureReferences().values())
                        structureReferences += references.size();
                    for (int y = 0; y < 256; y++) for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
                        net.minecraft.block.state.IBlockState state = chunk.getBlockState(position.setPos(x * 16 + lx, y, z * 16 + lz));
                        if (state.getBlock() == substitute) state = net.minecraft.init.Blocks.WATER.getDefaultState();
                        blocks.update(stateKeys.computeIfAbsent(state, value -> net.minecraft.command.arguments.BlockStateParser
                                .toString(value, null).getBytes(java.nio.charset.StandardCharsets.UTF_8))); blocks.update((byte) 0);
                    }
                    for (net.minecraft.world.biome.Biome biome : chunk.getBiomes()) {
                        biomes.update(biome.getRegistryName().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); biomes.update((byte) 0);
                    }
                }
                result.addProperty("blocks_sha256", hex(blocks.digest())); result.addProperty("biomes_sha256", hex(biomes.digest()));
                result.addProperty("structure_references", structureReferences);
                passes.add(result); System.out.println("PARITY_BENCHMARK_PASS " + result);
            }
            JsonObject report = new JsonObject(); report.add("passes", passes); report.addProperty("passed", true);
            report.addProperty("java", System.getProperty("java.version")); report.addProperty("processors", Runtime.getRuntime().availableProcessors());
            report.addProperty("cpu_scope", "Server thread plus WorldGen-Worker and WorldGen-Scheduler threads; excludes GC, JIT, I/O and modloading workers");
            report.addProperty("fixture_stabilization", "Ordered coral tags and seeded native fossils' otherwise unseeded region RNG; identical fixture in both controls and candidates");
            report.addProperty("warmup_chunks", 605);
            report.add("profile", WorldGeologyProfileManager.activeProfile().toJson());
            ParityProbe.write(java.nio.file.Paths.get("benchmark.json"), report);
            event.getServer().initiateShutdown();
        } catch (Throwable failure) { failure.printStackTrace(); event.getServer().initiateShutdown(); }
    }
    private static void square(WorldServer world, int cx, int cz, int radius) {
        for (int z = cz - radius; z <= cz + radius; z++) for (int x = cx - radius; x <= cx + radius; x++) world.getChunk(x, z);
    }
    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(); for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255)); return result.toString();
    }
    private static long generationCpu(ThreadMXBean clock) {
        long total = 0;
        for (long id : clock.getAllThreadIds()) {
            java.lang.management.ThreadInfo thread = clock.getThreadInfo(id);
            if (thread == null) continue;
            String name = thread.getThreadName();
            if (name.equals("Server thread") || name.startsWith("WorldGen-Worker-") || name.startsWith("WorldGen-Scheduler-"))
                total += Math.max(0, clock.getThreadCpuTime(id));
        }
        return total;
    }
}
