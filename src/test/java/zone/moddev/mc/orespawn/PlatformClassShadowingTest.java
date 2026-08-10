package zone.moddev.mc.orespawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** Prevents target adapters from replacing classes supplied by Minecraft or Forge. */
class PlatformClassShadowingTest {
	private static final Path MAIN_JAVA = Paths.get("src", "main", "java");

	@Test
	void productionSourcesDoNotShadowRuntimeClasses() throws Exception {
		List<String> collisions = new ArrayList<>();
		for (Path source : platformNamespaceSources()) {
			String resource = MAIN_JAVA.relativize(source).toString()
					.replace('\\', '/').replaceAll("\\.java$", ".class");
			Enumeration<URL> locations = getClass().getClassLoader().getResources(resource);
			List<String> found = Collections.list(locations).stream()
					.map(URL::toExternalForm).collect(Collectors.toList());
			if (found.size() > 1) {
				collisions.add(resource + " -> " + found);
			}
		}
		assertTrue(collisions.isEmpty(),
				"Production code must use target-native platform classes instead of shadowing them: "
						+ collisions);
	}

	@Test
	void onlyDocumentedPublicCodecAdaptersUsePlatformNamespaces() throws Exception {
		List<String> actual = platformNamespaceSources().stream()
				.map(path -> MAIN_JAVA.relativize(path).toString().replace('\\', '/'))
				.sorted().collect(Collectors.toList());
		List<String> expected = Arrays.asList(
				"com/mojang/serialization/Codec.java",
				"com/mojang/serialization/DataResult.java",
				"com/mojang/serialization/JsonOps.java");
		assertEquals(expected, actual,
				"Forge 1.14 has no Mojang Codec API, so only the API-1 codec adapters are allowed here");
	}

	@Test
	void matrixStackIsAbsentFromTheMatrixFreeForge28Runtime() throws Exception {
		List<String> locations = Collections.list(getClass().getClassLoader().getResources(
				"com/mojang/blaze3d/matrix/MatrixStack.class")).stream()
				.map(URL::toExternalForm).collect(Collectors.toList());
		assertTrue(locations.isEmpty(),
				"Forge 28 uses matrix-free screen rendering and OreSpawn must not supply MatrixStack: "
						+ locations);
	}

	@Test
	void clientScreensUseTheTargetNativeNavigationApi() throws Exception {
		Path clientDirectory = MAIN_JAVA.resolve(Paths.get(
				"zone", "moddev", "mc", "orespawn", "client"));
		StringBuilder combined = new StringBuilder();
		try (Stream<Path> files = Files.walk(clientDirectory)) {
			for (Path source : files.filter(path -> path.getFileName().toString().endsWith(".java"))
					.collect(Collectors.toList())) {
				combined.append(new String(Files.readAllBytes(source), java.nio.charset.StandardCharsets.UTF_8));
			}
		}
		String source = combined.toString();
		assertTrue(!source.contains("LegacyMinecraft"),
				"Do not wrap target-native Minecraft client methods in later-version facades");
		assertTrue(!source.contains("minecraft.setScreen("),
				"Minecraft 1.14 screens must use displayGuiScreen directly");
		assertTrue(source.contains("minecraft.displayGuiScreen("),
				"Screen navigation must use Minecraft 1.14's native displayGuiScreen method");
	}

	private static List<Path> platformNamespaceSources() throws Exception {
		List<Path> sources = new ArrayList<>();
		for (String root : Arrays.asList("com/mojang", "net/minecraft", "net/minecraftforge")) {
			Path directory = MAIN_JAVA.resolve(root);
			if (!Files.isDirectory(directory)) {
				continue;
			}
			try (Stream<Path> files = Files.walk(directory)) {
				files.filter(path -> path.getFileName().toString().endsWith(".java"))
						.forEach(sources::add);
			}
		}
		return sources;
	}
}
