package zone.moddev.mc.orespawn.os3abiprobe;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import com.mcmoddev.orespawn.api.os3.OS3API;
import com.mcmoddev.orespawn.api.plugin.IOreSpawnPlugin;
import com.mcmoddev.orespawn.api.plugin.OreSpawnPlugin;

import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

/** Binary fixture compiled only against the published OreSpawn 3.2.2.104 JAR. */
@Mod(modid = Os3AbiProbe.MODID, name = "OreSpawn 3 ABI Probe", version = "1", acceptableRemoteVersions = "*")
@OreSpawnPlugin(modid = Os3AbiProbe.MODID, resourcePath = "orespawn")
public final class Os3AbiProbe implements IOreSpawnPlugin {
    public static final String MODID = "os3abiprobe";
    private static boolean registered;

    @Override
    public void register(OS3API api) {
        api.registerReplacementBlock("os3abiprobe_stone", Blocks.STONE);
        api.registerSpawns();
        registered = true;
    }

    @EventHandler
    public void started(FMLServerStartedEvent event) throws Exception {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (!registered) {
            throw new IllegalStateException("OreSpawn 3 plugin did not reach the OS4 scheduler");
        }
        File marker = new File(server.getEntityWorld().getSaveHandler().getWorldDirectory(),
                "os3-abi-probe.properties");
        try (FileOutputStream output = new FileOutputStream(marker)) {
            output.write("loaded=true\nregistered=true\n".getBytes(StandardCharsets.UTF_8));
        }
        server.initiateShutdown();
    }
}
