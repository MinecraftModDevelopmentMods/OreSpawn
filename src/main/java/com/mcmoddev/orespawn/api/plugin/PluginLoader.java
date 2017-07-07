package com.mcmoddev.orespawn.api.plugin;

import java.io.IOException;
import java.io.InputStream;
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
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FileUtils;

import com.mcmoddev.orespawn.api.plugin.IOreSpawnPlugin;
import com.mcmoddev.orespawn.data.Constants;
import com.mcmoddev.orespawn.OreSpawn;

import net.minecraft.crash.CrashReport;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.discovery.ASMDataTable.ASMData;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public enum PluginLoader {

	INSTANCE;

	private class PluginData {
		public final String modId;
		public final String resourcePath;
		public final IOreSpawnPlugin plugin;

		public PluginData(String modId, String resourcePath, IOreSpawnPlugin plugin) {
			this.modId = modId;
			this.resourcePath = resourcePath;
			this.plugin = plugin;
		}
	}

	private static List<PluginData> dataStore = new ArrayList<>();

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
			IOreSpawnPlugin integration;
			try {
				integration = Class.forName(clazz).asSubclass(IOreSpawnPlugin.class).newInstance();
				PluginData pd = new PluginData( modId, resourceBase, integration);
				dataStore.add(pd);
			} catch (final Exception ex) {
				OreSpawn.LOGGER.error("Couldn't load integrations for " + modId, ex);
			}
		}
	}

	public void register() {
		dataStore.forEach( pd -> { scanResources(pd); pd.plugin.register(OreSpawn.API); });
	}

	public void scanResources(PluginData pd) {
		String base = String.format("assets/%s/%s", pd.modId, pd.resourcePath);
		URL resURL = getClass().getClassLoader().getResource(base);
		
		URI uri;
		try {
			uri = resURL.toURI();
		} catch (URISyntaxException ex) {
			CrashReport report = CrashReport.makeCrashReport(ex, String.format("Failed to get URI for %s", (new ResourceLocation(pd.modId,pd.resourcePath)).toString()));
			report.getCategory().addCrashSection("OreSpawn Version", Constants.VERSION);
			return;
		}

		Path myPath = null;
		FileSystem fileSystem = null;
		String tName = null;
		try {
			if (uri.getScheme().equals("jar")) {
				fileSystem = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap());
				myPath = fileSystem.getPath(base);
			} else {
				myPath = Paths.get(uri);
			}

			Stream<Path> walk = Files.walk(myPath, 1);
			for (Iterator<Path> it = walk.iterator(); it.hasNext();){
				Path p = it.next();
				String name = p.getFileName().toString();

				if( "json".equals(FilenameUtils.getExtension(name)) ) {
					InputStream reader = null;
					Path target = Paths.get(".","orespawn","os3",String.format("%s.json", pd.modId));
					tName = String.format("%s.json", pd.modId);
					if( target.toFile().exists() ) {
						// the file we were going to copy out to already exists!
						walk.close();
						return;
					}

					reader = Files.newInputStream(p);
					FileUtils.copyInputStreamToFile(reader, target.toFile());
					IOUtils.closeQuietly(reader);
				}
			}
			walk.close();
		} catch( IOException exc ) {
			String resName = (new ResourceLocation(pd.modId,
					String.format("%s/%s", pd.resourcePath, 
							FilenameUtils.getBaseName(uri.getPath())))).toString();
			CrashReport report = CrashReport.makeCrashReport(exc, 
					String.format("Failed in copying out config %s to %s", resName, tName));
			report.getCategory().addCrashSection("OreSpawn Version", Constants.VERSION);
			OreSpawn.LOGGER.info(report.getCompleteReport());			
		} finally {
			if( fileSystem != null ) {
				IOUtils.closeQuietly(fileSystem);
			}
		}
	}
}
