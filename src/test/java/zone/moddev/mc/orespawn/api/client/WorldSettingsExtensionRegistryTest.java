package zone.moddev.mc.orespawn.api.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import sun.misc.Unsafe;

class WorldSettingsExtensionRegistryTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void preservesRegistrationOrderAndReturnsAnImmutableSnapshot() {
		WorldSettingsExtension first = register("order_first", "settings", parent -> new Screen(Component.literal("test")) { });
		WorldSettingsExtension second = register("order_second", "settings", parent -> new Screen(Component.literal("test")) { });
		List<WorldSettingsExtension> extensions = WorldSettingsExtensionRegistry.extensions();

		assertTrue(extensions.indexOf(first) < extensions.indexOf(second));
		assertThrows(UnsupportedOperationException.class, () -> extensions.add(first));
	}

	@Test
	void rejectsDuplicateOwnershipAndInvalidRegistrations() {
		Identifier id = id("duplicate_owner", "first");
		WorldSettingsExtensionRegistry.register(id, "button.realisticdeposits.settings",
				parent -> new Screen(Component.literal("test")) { });
		assertThrows(IllegalStateException.class, () -> WorldSettingsExtensionRegistry.register(
				id("duplicate_owner", "second"),
				"button.realisticdeposits.other", parent -> new Screen(Component.literal("test")) { }));
		assertThrows(IllegalArgumentException.class, () -> WorldSettingsExtensionRegistry.register(
				id("blank_key", "settings"), "  ", parent -> new Screen(Component.literal("test")) { }));
		assertThrows(IllegalArgumentException.class, () ->
				WorldSettingsExtensionRegistry.registerConfigScreen("Bad Mod", parent -> parent));
		assertThrows(NullPointerException.class, () ->
				WorldSettingsExtensionRegistry.registerConfigScreen("null_factory", null));
	}

	@Test
	void preferredRegistrationOwnsOneConventionalScreenPerMod() {
		WorldSettingsExtension extension = WorldSettingsExtensionRegistry.registerConfigScreen(
				"preferred_form", parent -> parent);

		assertEquals("preferred_form", extension.id().getNamespace());
		assertEquals(Identifier.fromNamespaceAndPath("preferred_form", "configuration"), extension.id());
		assertEquals("button.orespawn.mod.configure", extension.buttonTranslationKey());
		assertThrows(IllegalStateException.class, () -> WorldSettingsExtensionRegistry.register(
				id("preferred_form", "legacy_form"), "button.example.settings", parent -> parent));
	}

	@Test
	void passesTheCurrentOreSpawnScreenToTheFactory() {
		AtomicReference<Screen> receivedParent = new AtomicReference<>();
		Screen expectedParent = bareScreen();
		Screen expectedChild = bareScreen();
		WorldSettingsExtension extension = register("parent_handoff", "settings", parent -> {
			receivedParent.set(parent);
			return expectedChild;
		});

		assertSame(expectedChild, extension.createScreen(expectedParent));
		assertSame(expectedParent, receivedParent.get());
		assertEquals("button.realisticdeposits.settings", extension.buttonTranslationKey());
	}

	@Test
	void screenResultsMayBeNullOrThrowForTheDirectoryToHandle() {
		Screen parent = bareScreen();
		WorldSettingsExtension missing = register("missing_screen", "settings", ignored -> null);
		WorldSettingsExtension failing = register("throwing_screen", "settings", ignored -> {
			throw new IllegalArgumentException("fixture");
		});

		assertSame(null, missing.createScreen(parent));
		assertThrows(IllegalArgumentException.class, () -> failing.createScreen(parent));
	}

	@Test
	void oldAndPreferredJvmDescriptorsRemainAvailable() throws Exception {
		assertNotNull(WorldSettingsExtensionRegistry.class.getDeclaredMethod("register",
				Identifier.class, String.class, WorldSettingsScreenFactory.class));
		assertNotNull(WorldSettingsExtensionRegistry.class.getDeclaredMethod("registerConfigScreen",
				String.class, WorldSettingsScreenFactory.class));
	}

	@Test
	void mainScreenUsesOneBoundedDirectoryEntryPointInsteadOfExtensionRows() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone",
				"moddev", "mc", "orespawn", "client", "OreSpawnWorldSettingsScreen.java")),
				StandardCharsets.UTF_8);
		assertTrue(!source.contains("WorldSettingsExtensionRegistry.extensions()"));
		assertEquals(2, occurrences(source, "button.orespawn.mods"),
				"Each mutually exclusive main-screen layout owns exactly one Mods button");
		assertTrue(source.contains("Component.translatable(\"button.orespawn.mods\")"));
		assertTrue(source.contains("new OreSpawnModsScreen(this)"));
		assertTrue(source.contains("syncSession();"));
	}

	private static int occurrences(String source, String value) {
		int count = 0;
		for (int index = 0; (index = source.indexOf(value, index)) >= 0; index += value.length()) {
			count++;
		}
		return count;
	}

	private static Screen bareScreen() {
		// This test checks identity only. A real Screen constructor needs a running client.
		try {
			Field field = Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			return (Screen) ((Unsafe) field.get(null)).allocateInstance(TestScreen.class);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}

	private static final class TestScreen extends Screen {
		private TestScreen() { super(Component.literal("test")); }
	}

	private static WorldSettingsExtension register(String modId, String path,
			WorldSettingsScreenFactory factory) {
		return WorldSettingsExtensionRegistry.register(id(modId, path),
				"button.realisticdeposits.settings", factory);
	}

	private static Identifier id(String modId, String path) {
		return Identifier.fromNamespaceAndPath(modId, path);
	}
}
