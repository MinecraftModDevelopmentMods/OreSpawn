package zone.moddev.mc.orespawn.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.locating.IModFile;

final class DimensionDiscovery {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";
	private static final String END = "minecraft:the_end";

	private DimensionDiscovery() {
	}

	static List<String> availableDimensionIds(CreateWorldScreen screen) {
		Set<String> result = new TreeSet<>();
		result.add(OVERWORLD);
		result.add(NETHER);
		result.add(END);

		try {
			screen.getUiState().getSettings().selectedDimensions().dimensions().keySet()
					.forEach(id -> result.add(id.toString()));
		} catch (RuntimeException ignored) {
			// The vanilla dimensions above keep the picker usable if a custom preset is incomplete.
		}

		ModList.get().forEachModFile(file -> collectModDimensions(file, result));
		return vanillaFirst(result);
	}

	private static List<String> vanillaFirst(Set<String> ids) {
		List<String> result = new ArrayList<>();
		result.add(OVERWORLD);
		result.add(NETHER);
		result.add(END);
		ids.remove(OVERWORLD);
		ids.remove(NETHER);
		ids.remove(END);
		result.addAll(ids);
		return result;
	}

	private static void collectModDimensions(IModFile modFile, Set<String> result) {
		for (Path contentRoot : modFile.getContents().getContentRoots()) {
			try {
				Path dataRoot = contentRoot.resolve("data");
				if (!Files.isDirectory(dataRoot)) continue;
				try (Stream<Path> namespaces = Files.list(dataRoot)) {
					namespaces.filter(Files::isDirectory)
							.forEach(namespace -> collectNamespaceDimensions(namespace, result));
				}
			} catch (IOException | RuntimeException ignored) {
				// A broken optional resource path must not prevent opening the world editor.
			}
		}
	}

	private static void collectNamespaceDimensions(Path namespaceRoot, Set<String> result) {
		Path dimensionRoot = namespaceRoot.resolve("dimension");
		if (!Files.isDirectory(dimensionRoot)) return;
		String namespace = namespaceRoot.getFileName().toString();
		try (Stream<Path> files = Files.walk(dimensionRoot)) {
			files.filter(Files::isRegularFile).forEach(file -> {
				String relative = dimensionRoot.relativize(file).toString().replace('\\', '/');
				if (!relative.endsWith(".json")) return;
				addDimensionId(result, namespace, relative.substring(0, relative.length() - 5));
			});
		} catch (IOException | RuntimeException ignored) {
			// Continue with dimensions discovered from other mods and the active preset.
		}
	}

	static void addDimensionId(Set<String> target, String namespace, String path) {
		try {
			target.add(Identifier.fromNamespaceAndPath(namespace, path).toString());
		} catch (RuntimeException ignored) {
			// Ignore malformed resource paths from third-party jars.
		}
	}
}
