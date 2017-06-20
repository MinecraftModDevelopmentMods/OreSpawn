package com.mcmoddev.orespawn;

import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.impl.OreSpawnImpl;
import com.mcmoddev.orespawn.json.OS2Reader;
import com.mcmoddev.orespawn.json.OS2Writer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mcmoddev.orespawn.api.OreSpawnAPI;
import com.mcmoddev.orespawn.commands.ClearChunkCommand;
import com.mcmoddev.orespawn.data.Config;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

/**
 * Main entry point for the mod, everything runs through this
 *
 * @author DShadowWolf &lt;dshadowwolf@gmail.com&gt;
 */

@Mod( modid = Constants.MODID,
      name = Constants.NAME,
      version = Constants.VERSION,
      acceptedMinecraftVersions = "[1.11.2,)" )

public class OreSpawn {
    @Instance
    public static OreSpawn INSTANCE = null;
    public static Logger LOGGER = LogManager.getFormatterLogger(Constants.MODID);
    public final static OreSpawnAPI API = new OreSpawnImpl();
    public static final OS2Reader reader = new OS2Reader();
    public static final OS2Writer writer = new OS2Writer();
    
    // TODO: add some form of storage for JSON here
    // TODO: add config loading -- partially done

    @EventHandler
    public void preInit(FMLPreInitializationEvent ev) {
    	Config.loadConfig();
    	
    	if( Config.getBoolean(Constants.RETROGEN_KEY) ) {
    		// TODO: setup stuff for retrogen
    	}
    	
    	reader.loadEntries();
    	// TODO: Bind stuff for standard gen regardless
    }

    @EventHandler
    public void init(FMLInitializationEvent ev) {
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent ev) {
    	// TODO: OS2 does a data-write here, we should too
    	writer.writeSpawnEntries();
    }
    
    @EventHandler
    public void onIMC(FMLInterModComms.IMCEvent ev) {
    	// TODO: Handle IMC
    }

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent ev) {
    	// TODO: Register Commands
    	ev.registerServerCommand(new ClearChunkCommand());
    }
}
