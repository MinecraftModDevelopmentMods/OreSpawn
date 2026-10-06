package zone.moddev.mc.orespawn.api.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;

class WorldSettingsExtensionRegistryTest {
	@Test
	void preservesRegistrationOrderAndReturnsAnImmutableSnapshot() {
		WorldSettingsExtension first = register("order_first", "settings", parent -> new Screen(new StringTextComponent("test")) { });
		WorldSettingsExtension second = register("order_second", "settings", parent -> new Screen(new StringTextComponent("test")) { });
		List<WorldSettingsExtension> extensions = WorldSettingsExtensionRegistry.extensions();

		assertTrue(extensions.indexOf(first) < extensions.indexOf(second));
		assertThrows(UnsupportedOperationException.class, () -> extensions.add(first));
	}

	@Test
	void rejectsDuplicateOwnershipAndInvalidRegistrations() {
		ResourceLocation id = id("duplicate_owner", "first");
		WorldSettingsExtensionRegistry.register(id, "button.realisticdeposits.settings",
				parent -> new Screen(new StringTextComponent("test")) { });
		assertThrows(IllegalStateException.class, () -> WorldSettingsExtensionRegistry.register(
				id("duplicate_owner", "second"),
				"button.realisticdeposits.other", parent -> new Screen(new StringTextComponent("test")) { }));
		assertThrows(IllegalArgumentException.class, () -> WorldSettingsExtensionRegistry.register(
				id("blank_key", "settings"), "  ", parent -> new Screen(new StringTextComponent("test")) { }));
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
		assertEquals(new ResourceLocation("preferred_form", "configuration"), extension.id());
		assertEquals("button.orespawn.mod.configure", extension.buttonTranslationKey());
		assertThrows(IllegalStateException.class, () -> WorldSettingsExtensionRegistry.register(
				id("preferred_form", "legacy_form"), "button.example.settings", parent -> parent));
	}

	@Test
	void passesTheCurrentOreSpawnScreenToTheFactory() {
		AtomicReference<Screen> receivedParent = new AtomicReference<>();
		Screen expectedParent = new Screen(new StringTextComponent("test")) { };
		Screen expectedChild = new Screen(new StringTextComponent("test")) { };
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
		Screen parent = new Screen(new StringTextComponent("test")) { };
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
				ResourceLocation.class, String.class, WorldSettingsScreenFactory.class));
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
		assertTrue(source.contains("new TranslationTextComponent(\"button.orespawn.mods\")"));
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

	private static WorldSettingsExtension register(String modId, String path,
			WorldSettingsScreenFactory factory) {
		return WorldSettingsExtensionRegistry.register(id(modId, path),
				"button.realisticdeposits.settings", factory);
	}

	private static ResourceLocation id(String modId, String path) {
		return new ResourceLocation(modId, path);
	}
}
