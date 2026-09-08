package zone.moddev.mc.orespawn.os1abiprobe;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import cyano.orespawn.init.WorldGen;
import cyano.orespawn.worldgen.OreSpawnData;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

/** Binary fixture compiled only against the published OreSpawn 1.1 JAR. */
@Mod(modid = Os1AbiProbe.MODID, name = "OreSpawn 1 ABI Probe", version = "1", acceptableRemoteVersions = "*")
public final class Os1AbiProbe {
    public static final String MODID = "os1abiprobe";

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        WorldGen.addOreSpawner(new OreSpawnData(Blocks.DIAMOND_ORE, 0, 4, 2,
                0.25F, 4, 32, Collections.<String>emptyList()), Integer.valueOf(0), 0x110L);
    }

    @EventHandler
    public void started(FMLServerStartedEvent event) throws Exception {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        File run = server.getEntityWorld().getSaveHandler().getWorldDirectory().getParentFile();
        File provider = new File(run, "config/orespawn-orespawn.json");
        File report = new File(run, "config/orespawn-os3-migration-report.json");
        String providerText = new String(Files.readAllBytes(provider.toPath()), StandardCharsets.UTF_8);
        String reportText = new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8);
        if (!providerText.contains("minecraft:diamond_ore")
                || !reportText.contains("provider_translated=minecraft:owner=orespawn:ores=1")) {
            throw new IllegalStateException("OreSpawn 1 declaration did not reach the OS4 scheduler");
        }
        File marker = new File(server.getEntityWorld().getSaveHandler().getWorldDirectory(),
                "os1-abi-probe.properties");
        try (FileOutputStream output = new FileOutputStream(marker)) {
            output.write("loaded=true\nregistered=true\n".getBytes(StandardCharsets.UTF_8));
        }
        server.initiateShutdown();
    }
}
