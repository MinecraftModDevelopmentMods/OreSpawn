package com.mcmoddev.orespawn.utils;

import com.google.common.base.Joiner;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import com.mcmoddev.orespawn.OreSpawn;
import com.mcmoddev.orespawn.data.Constants;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.mcmoddev.orespawn.data.Config.COMMON;

public class Loaders {
	protected static List<ResourceLocation> foundFiles = new LinkedList<>();
	private static Map<ResourceLocation, JsonObject> loadedConfigs = new ConcurrentHashMap<>();

	public static void loadConfigs() {
		ModList.get().getModFiles().stream().map(mfi -> iterateFiles(mfi.getFile()))
	  	       .forEach(foundFiles::addAll);
		iterDisk().stream().forEach(foundFiles::add);
		// okay, at this point we have things found, but not loaded - first we need a filter of the thing
		// based on existing configs
		// TODO: Custom config system needed here - this is before the Forge config stuff is ready
		// TODO: Custom Config should be an extended JSON - see about doing it like some older stuff written in NodeJS
		//       and possibly some custom filters that can take basic expressions
		if (COMMON.ignoreResources.get())
			foundFiles = foundFiles.stream().filter( rl -> rl.getNamespace() == "orespawn").collect(Collectors.toList());
		if (COMMON.ignoreDisk.get())
			foundFiles = foundFiles.stream().filter( rl -> rl.getNamespace() != "orespawn").collect(Collectors.toList());
		if (!foundFiles.isEmpty()) {
			foundFiles = foundFiles.stream().filter( Loaders::runConfiguredFilters ).collect(Collectors.toList());
		}
		// now that filtering is done, we can actually loop and load as needed
		doActualLoad();
	}

	private static void doActualLoad() {
		// doing this as a parallel run and using the 'ifAbsent' form to try and add some speed to the process
		foundFiles.parallelStream().forEach( rl -> {
			if (rl.getNamespace() == "orespawn") // on disk file
				loadedConfigs.putIfAbsent(rl, loadFromDisk(rl));
			else // assume a resource from a mod
				loadedConfigs.putIfAbsent(rl, loadAsResource(rl));
		});
	}

	private static JsonObject loadFromDisk(final ResourceLocation loc) {
		Path p = Constants.JSONPATH.resolve(String.format("{}.json", loc.getPath()));
		JsonParser parser = new JsonParser();
		try (FileInputStream baseInput = new FileInputStream(p.toFile());
			 BufferedInputStream dataInput = new BufferedInputStream(baseInput);
			 InputStreamReader theReader = new InputStreamReader(dataInput)) {
			return parser.parse(new JsonReader(theReader)).getAsJsonObject();
		} catch(IOException ex) {
			OreSpawn.LOGGER.error("Unable to load known configs file: {}", ex.getMessage());
			ex.printStackTrace();
			return new JsonObject();
		}
	}

	private static JsonObject loadAsResource(final ResourceLocation loc) {
		ModFile mf = ModList.get().getModFileById(loc.getNamespace()).getFile();
		Path p = mf.getLocator().findPath(mf, "assets", "orespawn4-data", loc.getPath()+".json");
		try {
			String fileData = FileUtils.readFileToString(p.toFile(), "UTF-8");
			JsonParser parser = new JsonParser();
			return parser.parse(fileData).getAsJsonObject();
		} catch(IOException ex) {
			OreSpawn.LOGGER.error("Unable to load known configs file: {}", ex.getMessage());
			ex.printStackTrace();
			return new JsonObject();
		}
	}

	private static boolean runConfiguredFilters(ResourceLocation resourceLocation) {
		// TODO: Custom Config filters here
		// TODO: Basic Whitelist/Blacklist here
		return true; // placeholder/stub return
	}

	private static List<ResourceLocation> iterDisk() {
		try {
			return Files.walk(Constants.JSONPATH)
				.map( path -> Constants.JSONPATH.relativize(path.toAbsolutePath()))
				.filter(path -> path.getNameCount() <= 64) // Make sure the depth is within bounds
				.filter( path -> path.toString().endsWith(".json"))
				.filter( path -> !path.toString().contains(Constants.SYSCONF.toString()))
				.map( path -> Joiner.on('/').join(path))
				.map( path -> new ResourceLocation("orespawn", path))
				.collect(Collectors.toList());
		} catch (IOException e) {
			return Collections.emptyList();
		}
	}

	private static List<ResourceLocation> iterateFiles(ModFile modFile) {
		try {
			Path root = modFile.getLocator().findPath(modFile, Constants.FileBits.RESOURCE_PATH).toAbsolutePath();

			return Files.walk(root).map(path -> root.relativize(path.toAbsolutePath()))
				.filter(path -> path.getNameCount() <= 64) // Make sure the depth is within bounds
				.filter(path -> path.toString().endsWith(".json")) // check extension
				.map(path -> Joiner.on('/').join(path))
				.map(path -> path.substring(0, path.length() - 5))
				.map(path -> new ResourceLocation(modFile.getModInfos().get(0).getModId(), path))
				.collect(Collectors.toList());
		} catch (IOException e) {
			return Collections.emptyList();
		}
	}
}
