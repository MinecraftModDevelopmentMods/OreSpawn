package com.mcmoddev.orespawn.utils;

import com.google.common.base.Joiner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.mcmoddev.orespawn.data.Constants;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class ResourceLoader {
	public ResourceLoader() {
		List<ResourceLocation> foundFiles = new LinkedList<>();

		// get the files we're interested in
		ModList.get().getModFiles().stream().map(mfi -> iterateFiles(mfi.getFile()))
				.forEach(foundFiles::addAll);

		// iterate and load!
		foundFiles.parallelStream().forEach( (resLoc) -> {
			Path filePath = resourceLocationToPath(resLoc);
			try (InputStream dataStream = Files.newInputStream(filePath);
				 BufferedInputStream bis = new BufferedInputStream(dataStream);
				 InputStreamReader baseReader = new InputStreamReader(bis);
				 JsonReader dataReader = new JsonReader(baseReader)) {
				loadFeature((new JsonParser()).parse(dataReader));
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	private void loadFeature(JsonElement parse) {
	}

	private Path resourceLocationToPath(ResourceLocation location) {
		ModFile modFile = ModList.get().getModFileById(location.getNamespace()).getFile();
		return modFile.getLocator().findPath(modFile, "assets", "orespawn4-data", String.format("{rl.getPath()}.json"));
	}
	private List<ResourceLocation> iterateFiles(ModFile modFile) {
		try {
			Path root = modFile.getLocator().findPath(modFile, Constants.FileBits.RESOURCE_PATH).toAbsolutePath();

			return Files.walk(root).map(path -> root.relativize(path.toAbsolutePath()))
				.filter(path -> path.getNameCount() <= 64). // Make sure the depth is within bounds
					filter(path -> path.toString().endsWith(".json")).map(path -> Joiner.on('/').join(path))
				.map(path -> path.substring(0, path.length() - 5))
				.map(path -> new ResourceLocation(modFile.getModInfos().get(0).getModId(), path))
				.collect(Collectors.toList());
		} catch (IOException e) {
			return Collections.emptyList();
		}
	}
}
