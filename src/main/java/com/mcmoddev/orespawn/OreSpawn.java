package com.mcmoddev.orespawn;

import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.data.FeatureRegistry;
import com.mcmoddev.orespawn.impl.os3.OS3APIImpl;
import com.mcmoddev.orespawn.json.OS1Reader;
import com.mcmoddev.orespawn.json.OS2Reader;
import com.mcmoddev.orespawn.json.OS3Reader;
import com.mcmoddev.orespawn.json.OS3Writer;
import com.mcmoddev.orespawn.commands.AddOreCommand;
import com.mcmoddev.orespawn.commands.ClearChunkCommand;
import com.mcmoddev.orespawn.commands.WriteConfigsCommand;
import com.mcmoddev.orespawn.commands.DumpBiomesCommand;
import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.api.os3.OS3API;
import com.mcmoddev.orespawn.api.os3.SpawnBuilder;
import com.mcmoddev.orespawn.api.plugin.PluginLoader;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
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
      acceptedMinecraftVersions = "[1.12,)" )

public class OreSpawn {
    @Instance
    public static OreSpawn instance = null;
    public static final Logger LOGGER = LogManager.getFormatterLogger(Constants.MODID);
    public static final OS3API API = new OS3APIImpl();
    public static final OS3Writer writer = new OS3Writer();
    public static final EventHandlers eventHandlers = new EventHandlers();
    public static final FeatureRegistry FEATURES = new FeatureRegistry();
    private String os1ConfigPath;
    protected static final Map<Integer, List<SpawnBuilder>> spawns = new HashMap<>();
    
    public static Map<Integer, List<SpawnBuilder>> getSpawns() {
    	return spawns;
    }
    
    @EventHandler
    public void preInit(FMLPreInitializationEvent ev) {
    	Config.loadConfig();
    	
    	PluginLoader.INSTANCE.load(ev);
    	
    	if( Config.getBoolean(Constants.RETROGEN_KEY) ) {
    		MinecraftForge.EVENT_BUS.register(eventHandlers);
    	}
    	
    	if( Config.getBoolean(Constants.REPLACE_VANILLA_OREGEN) ) {
    		MinecraftForge.ORE_GEN_BUS.register(eventHandlers);
    	}
    	
    	this.os1ConfigPath = Paths.get(ev.getSuggestedConfigurationFile().toPath().getParent().toString(),"orespawn").toString();
    }

    @EventHandler
    public void init(FMLInitializationEvent ev) {
    	PluginLoader.INSTANCE.register();
    	// we prefer the OS3 version of files
    	// but will take OS2 and OS1 versions - in that order
    	OS3Reader.loadEntries();
    	OS2Reader.loadEntries();
    	OS1Reader.loadEntries(Paths.get(os1ConfigPath));
    	API.registerSpawns();
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent ev) {
    	writer.writeSpawnEntries();
    	Config.saveConfig();
    }
        
    @EventHandler
    public void onServerStarting(FMLServerStartingEvent ev) {
    	ev.registerServerCommand(new ClearChunkCommand());
    	ev.registerServerCommand(new DumpBiomesCommand());
    	ev.registerServerCommand(new AddOreCommand());
    	ev.registerServerCommand(new WriteConfigsCommand());
    }
}
