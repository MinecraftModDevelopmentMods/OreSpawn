package zone.moddev.mc.orespawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.neoforged.fml.config.ModConfig;

class NeoForgeConfigTypeTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void usesTheActualLoadersLocalConfigType() {
		String name = java.util.Arrays.stream(ModConfig.Type.values())
				.anyMatch(type -> type.name().equals("LOCAL")) ? "LOCAL" : "COMMON";
		assertEquals(ModConfig.Type.valueOf(name), NeoForgeConfigType.local());
	}

	@Test
	void legacyLoaderKeepsTheCommonConfigType() throws Exception {
		assertEquals("COMMON", resolveWith("COMMON, CLIENT, SERVER, STARTUP"));
	}

	@Test
	void renamedLoaderUsesTheLocalConfigType() throws Exception {
		assertEquals("LOCAL", resolveWith("LOCAL, CLIENT, SYNCED, STARTUP"));
	}

	@Test
	void prefersLocalIfBothNamesAreAvailable() throws Exception {
		assertEquals("LOCAL", resolveWith("COMMON, LOCAL, CLIENT, SYNCED, STARTUP"));
	}

	@Test
	void doesNotMistakeClientOrSyncedForLocal() {
		InvocationTargetException failure = assertThrows(InvocationTargetException.class,
				() -> resolveWith("CLIENT, SYNCED, STARTUP"));
		assertInstanceOf(IllegalArgumentException.class, failure.getCause());
		assertTrue(failure.getCause().getMessage().contains("COMMON"));
	}

	@Test
	void registrationKeepsTheExistingFileAndSupportsBothLoaderNames() throws Exception {
		String source = Files.readString(Path.of(
				"src/main/java/zone/moddev/mc/orespawn/OreSpawnConfig.java"));
		assertTrue(source.contains(
				"registerConfig(NeoForgeConfigType.local(), SPEC, \"orespawn-common.toml\")"));
	}

	private String resolveWith(String constants) throws Exception {
		Path source = temporaryDirectory.resolve("net/neoforged/fml/config/ModConfig.java");
		Files.createDirectories(source.getParent());
		Files.writeString(source, "package net.neoforged.fml.config; public final class ModConfig {"
				+ " public enum Type { " + constants + " } }");
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		assertEquals(0, compiler.run(null, null, null, "-proc:none", "-d",
				temporaryDirectory.toString(), source.toString()));

		// Run the shipped helper bytecode with each enum shape, without the test JVM's FML classes.
		String helperName = "zone.moddev.mc.orespawn.NeoForgeConfigType";
		Path helper = temporaryDirectory.resolve(helperName.replace('.', '/') + ".class");
		Files.createDirectories(helper.getParent());
		try (InputStream bytes = getClass().getClassLoader().getResourceAsStream(
				helperName.replace('.', '/') + ".class")) {
			Files.copy(bytes, helper);
		}
		try (URLClassLoader isolated = new URLClassLoader(
				new URL[] { temporaryDirectory.toUri().toURL() }, ClassLoader.getPlatformClassLoader())) {
			Method local = isolated.loadClass(helperName).getDeclaredMethod("local");
			local.setAccessible(true);
			return ((Enum<?>) local.invoke(null)).name();
		}
	}
}
