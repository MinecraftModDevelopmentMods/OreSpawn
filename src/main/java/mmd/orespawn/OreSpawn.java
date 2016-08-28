package mmd.orespawn;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import mmd.orespawn.api.OreSpawnAPI;
import mmd.orespawn.api.SpawnLogic;
import mmd.orespawn.impl.OreSpawnImpl;
import mmd.orespawn.json.OreSpawnReader;
import mmd.orespawn.json.OreSpawnWriter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "orespawn", name = "OreSpawn", version = OreSpawn.VERSION)
public class OreSpawn {
    public static final String VERSION = "2.0.0";

    public static final Logger LOGGER = LogManager.getLogger("OreSpawn");
    public static final OreSpawnImpl API = new OreSpawnImpl();

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        MinecraftForge.ORE_GEN_BUS.register(EventHandler.INSTANCE);
        OreSpawnReader.INSTANCE.readSpawnEntries();
        OreSpawnReader.INSTANCE.convertOldSpawnEntries();

        FMLInterModComms.sendFunctionMessage("orespawn", "api", "mmd.orespawn.VanillaOreSpawn");
    }

    @Mod.EventHandler
    public void onPostInit(FMLPostInitializationEvent event) {
        OreSpawnWriter.INSTANCE.writeSpawnEntries();
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
