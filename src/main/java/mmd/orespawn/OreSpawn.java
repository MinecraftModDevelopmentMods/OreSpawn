package mmd.orespawn;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import mmd.orespawn.api.OreSpawnAPI;
import mmd.orespawn.api.SpawnLogic;
import mmd.orespawn.command.AddOreCommand;
import mmd.orespawn.command.ClearChunkCommand;
import mmd.orespawn.command.DumpBiomesCommand;
import mmd.orespawn.impl.OreSpawnImpl;
import mmd.orespawn.json.OreSpawnReader;
import mmd.orespawn.json.OreSpawnWriter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "orespawn", name = "OreSpawn", version = OreSpawn.VERSION)
public class OreSpawn {
    public static final String VERSION = "2.0.0";

    public static final Logger LOGGER = LogManager.getLogger("OreSpawn");
    public static final OreSpawnImpl API = new OreSpawnImpl();

    public static boolean DO_RETRO_GENERATION = true;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        OreSpawn.DO_RETRO_GENERATION = config.getBoolean("retrogen", Configuration.CATEGORY_GENERAL, true, "Generate ores in pre-existing chunks");
        config.save();

        MinecraftForge.EVENT_BUS.register(EventHandler.INSTANCE);
        MinecraftForge.ORE_GEN_BUS.register(EventHandler.INSTANCE);
        OreSpawnReader.INSTANCE.readSpawnEntries();

        FMLInterModComms.sendFunctionMessage("orespawn", "api", "mmd.orespawn.VanillaOreSpawn");
    }

    @Mod.EventHandler
    public void onPostInit(FMLPostInitializationEvent event) {
        OreSpawnWriter.INSTANCE.writeSpawnEntries();
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new ClearChunkCommand());
        event.registerServerCommand(new AddOreCommand());
        event.registerServerCommand(new DumpBiomesCommand());
    }

    @Mod.EventHandler
    public void onIMC(FMLInterModComms.IMCEvent event) {
        event.getMessages().stream().filter(message -> message.key.equalsIgnoreCase("api")).forEach(message -> {
            Optional<Function<OreSpawnAPI, SpawnLogic>> value = message.getFunctionValue(OreSpawnAPI.class, SpawnLogic.class);
            if (OreSpawn.API.getSpawnLogic(message.getSender()) == null && value.isPresent()) {
                OreSpawn.API.registerSpawnLogic(message.getSender(), value.get().apply(OreSpawn.API));
            }
        });
    }
}
