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
import java.util.Locale;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.data.Config;
import com.mcmoddev.orespawn.data.Constants;

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

		PluginData(final String modId, final String resourcePath, final IOreSpawnPlugin plugin) {
			this.modId = modId;
			this.resourcePath = resourcePath;
			this.plugin = plugin;
		}
	}

	private static List<PluginData> dataStore = new ArrayList<>();

	private String getAnnotationItem(final String item, final ASMData asmData) {
		if (asmData.getAnnotationInfo().get(item) != null) {
			return asmData.getAnnotationInfo().get(item).toString();
		} else {
			return "";
		}
	}

	public void load(final FMLPreInitializationEvent event) {
		for (final ASMData asmDataItem : event.getAsmData()
				.getAll(OreSpawnPlugin.class.getCanonicalName())) {
			final String modId = getAnnotationItem("modid", asmDataItem);
			final String resourceBase = getAnnotationItem("resourcePath", asmDataItem);
			final String clazz = asmDataItem.getClassName();
			IOreSpawnPlugin integration;

			try {
				integration = Class.forName(clazz).asSubclass(IOreSpawnPlugin.class).newInstance();
				final PluginData pd = new PluginData(modId, resourceBase, integration);
				OreSpawn.LOGGER.info("Loading Integration For {}", modId);
				dataStore.add(pd);
			} catch (final Exception ex) {
				OreSpawn.LOGGER.error("Couldn't load integrations for " + modId, ex);
			}
		}
	}

	public void register() {
		dataStore.forEach(pd -> {
			scanResources(pd);
			pd.plugin.register(OreSpawn.API);
		});
	}

	public void scanResources(final PluginData pd) {
		if (Config.getKnownMods().contains(pd.modId)) {
			return;
		}

		final String base = String.format(Locale.ENGLISH, "assets/%s/%s", pd.modId, pd.resourcePath);
		final URL resURL = getClass().getClassLoader().getResource(base);
		
		if (resURL == null) {
			OreSpawn.LOGGER.warn("Unable to access file {}: got 'null' when trying to resolve it",
					base);
			return;
		}

		URI uri;

		try {
			uri = resURL.toURI();
		} catch (URISyntaxException ex) {
			CrashReport report = CrashReport.makeCrashReport(ex,
					String.format(Locale.ENGLISH, "Failed to get URI for %s",
							(new ResourceLocation(pd.modId, pd.resourcePath)).toString()));
			report.getCategory().addCrashSection(Constants.CRASH_SECTION, Constants.VERSION);
			return;
		}

		if (uri.getScheme().equals("jar")) {
			try (FileSystem fileSystem = FileSystems.newFileSystem(uri,
					Collections.<String, Object>emptyMap())) {
				copyout(fileSystem.getPath(base), pd.modId);
			} catch (IOException exc) {
				CrashReport report = CrashReport.makeCrashReport(exc, String.format(
						Locale.ENGLISH, "Failed in getting FileSystem handler set up for %s", uri.getPath()));
				report.getCategory().addCrashSection(Constants.CRASH_SECTION, Constants.VERSION);
				OreSpawn.LOGGER.info(report.getCompleteReport());
			}
		} else {
			copyout(Paths.get(uri), pd.modId);
		}

		Config.addKnownMod(pd.modId);
	}

	private void copyout(final Path myPath, final String modId) {
		try (Stream<Path> walk = Files.walk(myPath, 1)) {
			for (final Iterator<Path> it = walk.iterator(); it.hasNext();) {
				final Path p = it.next();
				final String name = p.getFileName().toString();

				if ("json".equals(FilenameUtils.getExtension(name))) {
					InputStream reader = null;
					Path target;

					if ("_features".equals(FilenameUtils.getBaseName(name))) {
						target = Paths.get(Constants.FileBits.CONFIG_DIR, Constants.FileBits.OS3,
								Constants.FileBits.SYSCONF,
								String.format(Locale.ENGLISH, "features-%s.json", modId));
					} else if ("_replacements".equals(FilenameUtils.getBaseName(name))) {
						target = Paths.get(Constants.FileBits.CONFIG_DIR, Constants.FileBits.OS3,
								Constants.FileBits.SYSCONF,
								String.format(Locale.ENGLISH, "replacements-%s.json", modId));
					} else {
						target = Paths.get(Constants.FileBits.CONFIG_DIR, Constants.FileBits.OS3,
								String.format(Locale.ENGLISH, "%s.json", modId));
					}

					if (!target.toFile().exists()) {
						reader = Files.newInputStream(p);
						FileUtils.copyInputStreamToFile(reader, target.toFile());
						IOUtils.closeQuietly(reader);
					}
				}
			}
		} catch (IOException exc) {
			CrashReport report = CrashReport.makeCrashReport(exc, String.format(
					Locale.ENGLISH, "Faulted while iterating %s for config files or copying them out", myPath));
			report.getCategory().addCrashSection(Constants.CRASH_SECTION, Constants.VERSION);
			OreSpawn.LOGGER.error(report.getCompleteReport());
		}
	}
}
