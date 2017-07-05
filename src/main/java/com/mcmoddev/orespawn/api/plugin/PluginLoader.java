package com.mcmoddev.orespawn.api.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import com.mcmoddev.orespawn.api.plugin.IOreSpawnPlugin;
import com.mcmoddev.orespawn.json.OS3Reader;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mcmoddev.orespawn.OreSpawn;

import net.minecraftforge.fml.common.discovery.ASMDataTable.ASMData;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public enum PluginLoader {

	INSTANCE;
	
	private class PluginData {
		public final String modId;
		public final String resourcePath;
		public final File modLoc;
		public final IOreSpawnPlugin plugin;
		
		public PluginData(String modId, String resourcePath, File modLoc, IOreSpawnPlugin plugin) {
			this.modId = modId;
			this.resourcePath = resourcePath;
			this.modLoc = modLoc;
			this.plugin = plugin;
		}
	}
	
	private List<PluginData> dataStore = new ArrayList<>();
	
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
					PluginData pd = new PluginData( modId, resourceBase, asmDataItem.getCandidate().getModContainer(), integration);
					dataStore.add(pd);
				} catch (final Exception ex) {
					OreSpawn.LOGGER.error("Couldn't load integrations for " + modId, ex);
				}
			}
		}
	}
	
	public void register() {
		dataStore.forEach( pd -> { 
			 try {
				scanResources(pd);
			} catch (IOException | URISyntaxException e) {
				OreSpawn.LOGGER.error("Houston, we have a problem: mod {} apparently registered a path for files and there was an issue.", pd.modId, e);
			}
			 pd.plugin.register(OreSpawn.API); 
		});
	}

	public void scanResources(PluginData pd) throws IOException, URISyntaxException {
		String base = String.format("assets/%s/%s", pd.modId, pd.resourcePath);
		URI uri = getClass().getClassLoader().getResource(base).toURI();
        Path myPath;
        if (uri.getScheme().equals("jar")) {
            FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
            myPath = fileSystem.getPath(base);
        } else {
            myPath = Paths.get(uri);
        }
        Stream<Path> walk = Files.walk(myPath, 1);
        for (Iterator<Path> it = walk.iterator(); it.hasNext();){
        	Path p = it.next();
        	String name = p.getFileName().toString();
        	if( "json".equals(FilenameUtils.getExtension(name)) ) {
        		BufferedReader reader = null;
        		try {
        			reader = Files.newBufferedReader(p);
        			Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        			JsonObject json = gson.fromJson(reader, JsonObject.class);
        			OS3Reader.loadFromJson(pd.modId, json);
        		} catch (IOException e) {
        			OreSpawn.LOGGER.error("Error creating a Buffered Reader to load Json from {} for mod {}",
        					p.toString(), pd.modId, e);
        		} finally {
        			IOUtils.closeQuietly(reader);
        		}
        	}
        }
        walk.close();
	}
}
