package com.mcmoddev.orespawn.api.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FilenameUtils;

import com.mcmoddev.orespawn.api.plugin.IOreSpawnPlugin;
import com.mcmoddev.orespawn.json.OS3Reader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable.ASMData;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public enum PluginLoader {

	INSTANCE;
	
	private class pluginData {
		public final String modId;
		public final String resourcePath;
		public final File modLoc;
		public final IOreSpawnPlugin plugin;
		
		public pluginData(String modId, String resourcePath, File modLoc, IOreSpawnPlugin plugin) {
			this.modId = modId;
			this.resourcePath = resourcePath;
			this.modLoc = modLoc;
			this.plugin = plugin;
		}
	}
	
	private List<pluginData> dataStore = new ArrayList<>();
	
	private String getAnnotationItem(String item, final ASMData asmData) {
		if (asmData.getAnnotationInfo().get(item) != null) {
			return asmData.getAnnotationInfo().get(item).toString();
		} else {
			return "";
		}
	}

	public void load(FMLPreInitializationEvent event) {
		for (final ASMData asmDataItem : event.getAsmData().getAll(OreSpawnPlugin.class.getCanonicalName())) {
			final String modId = getAnnotationItem("modid", asmDataItem);
			final String resourceBase = getAnnotationItem("resourcePath", asmDataItem);
			final String clazz = asmDataItem.getClassName();

			if ( event.getModMetadata().modId.equals(modId) ) {
				IOreSpawnPlugin integration;
				try {
					integration = Class.forName(clazz).asSubclass(IOreSpawnPlugin.class).newInstance();
					pluginData pd = new pluginData( modId, resourceBase, asmDataItem.getCandidate().getModContainer(), integration);
					dataStore.add(pd);
				} catch (final Exception ex) {
					OreSpawn.LOGGER.error("Couldn't load integrations for " + modId, ex);
				}
			}
		}
	}
	
	public void register() {
		dataStore.forEach( pd -> { scanResources(pd); pd.plugin.register(OreSpawn.API); } );
	}

	public void scanResources(pluginData pd) {
		String filePath = "assets/" + pd.resourcePath + "/";
		Path root = pd.modLoc.toPath().resolve("/"+filePath);
		Iterator<Path> pathIter = null;
		
        if (root == null || !Files.exists(root))
            return;

        try {
        	pathIter = Files.walk(root).iterator();
        } catch( IOException e ) {
        	OreSpawn.LOGGER.error("Error searching for configs for mod {}", pd.modId, e);
        }


        while( pathIter != null && pathIter.hasNext() ) {
        	Path currentFile = pathIter.next();

        	if( "json".equals( FilenameUtils.getExtension( currentFile.toString() ) ) ) {
        		BufferedReader reader = null;
        		try {
        			reader = Files.newBufferedReader(currentFile);
        		} catch (IOException e) {
        			OreSpawn.LOGGER.error("Error creating a Buffered Reader to load Json from {} for mod {}",
        					currentFile.toString(), pd.modId, e);
        		} finally {
        			Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        			JsonObject json = GSON.fromJson(reader, JsonObject.class);
        			OS3Reader.loadFromJson(pd.modId, json);
        		}
        	}
        }
        
		return;
	}
}
