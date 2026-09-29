package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import zone.moddev.mc.orespawn.api.ProviderStatus;
import zone.moddev.mc.orespawn.api.client.WorldSettingsExtension;
import zone.moddev.mc.orespawn.api.client.WorldSettingsExtensionRegistry;
import zone.moddev.mc.orespawn.client.OreSpawnModDirectoryModel.Entry;
import zone.moddev.mc.orespawn.client.OreSpawnModDirectoryModel.LoadedMod;
import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager.ProviderIntegrationInfo;

class OreSpawnModDirectoryModelTest {
	@Test
	void discoversLoadedNativeLegacyUiOnlyInactiveAndRejectedIntegrations() {
		WorldSettingsExtension realisticScreen = WorldSettingsExtensionRegistry.register(
				new ResourceLocation("directory_realistic", "settings"),
				"button.realisticdeposits.settings", parent -> new GuiScreen() { });
		WorldSettingsExtension uiOnlyScreen = WorldSettingsExtensionRegistry.registerConfigScreen(
				"directory_ui_only", parent -> new GuiScreen() { });

		List<LoadedMod> loaded = Arrays.asList(
				new LoadedMod("orespawn", "MMD OreSpawn", "4.1.0.110021"),
				new LoadedMod("directory_realistic", "Realistic Deposits", "0.1.0.110021"),
				new LoadedMod("directory_base", "Base Metals", "2.5.0-beta5"),
				new LoadedMod("directory_mineralogy", "Mineralogy", "6.0.2.110021"),
				new LoadedMod("directory_ui_only", "Client Tweaks", "1.2.3"),
				new LoadedMod("directory_inactive", "Inactive Rocks", "2.0"),
				new LoadedMod("directory_rejected", "Rejected Ores", "3.0"));
		List<ProviderIntegrationInfo> providers = Arrays.asList(
				provider("directory_mineralogy", true, true, 4, 3,
						ProviderStatus.ACTIVE, false),
				provider("directory_base", false, true, 4, 2,
						ProviderStatus.ACTIVE, false, 3),
				provider("directory_realistic", true, true, 4, 1,
						ProviderStatus.ACTIVE, false, 3),
				provider("directory_inactive", true, true, 4, 1,
						ProviderStatus.INACTIVE, false),
				provider("directory_rejected", true, true, -1, -1,
						ProviderStatus.INACTIVE, true),
				provider("directory_unloaded", true, true, 4, 9,
						ProviderStatus.ACTIVE, false),
				provider("orespawn", true, true, 4, 1,
						ProviderStatus.ACTIVE, false));

		List<Entry> result = OreSpawnModDirectoryModel.assemble(loaded, providers,
				Arrays.asList(realisticScreen, uiOnlyScreen));

		assertEquals(Arrays.asList("directory_base", "directory_ui_only", "directory_inactive",
				"directory_mineralogy", "directory_realistic", "directory_rejected"),
				result.stream().map(entry -> entry.modId).collect(java.util.stream.Collectors.toList()));
		assertEquals(6, result.size());
		assertEquals("config_only", find(result, "directory_ui_only").statusKey());
		assertEquals("inactive", find(result, "directory_inactive").statusKey());
		assertEquals("rejected", find(result, "directory_rejected").statusKey());
		assertFalse(find(result, "directory_base").nativeOs4());
		assertEquals(Collections.singletonList(3), find(result, "directory_base").legacyLineages());
		assertTrue(find(result, "directory_realistic").nativeOs4());
		assertTrue(find(result, "directory_realistic").configurable());
		assertThrows(UnsupportedOperationException.class, () -> result.clear());
	}

	@Test
	void deduplicatesByModIdAndSortsEqualNamesByModId() {
		List<LoadedMod> loaded = Arrays.asList(
				new LoadedMod("directory_sort_b", "Same Name", "1"),
				new LoadedMod("directory_sort_a", "Same Name", "2"));
		List<ProviderIntegrationInfo> providers = Arrays.asList(
				provider("directory_sort_b", false, true, 4, 1, ProviderStatus.ACTIVE, false, 1, 2, 3),
				provider("directory_sort_a", true, true, 4, 2, ProviderStatus.PENDING, false));

		List<Entry> result = OreSpawnModDirectoryModel.assemble(loaded, providers,
				Collections.emptyList());

		assertEquals("directory_sort_a", result.get(0).modId);
		assertEquals("directory_sort_b", result.get(1).modId);
		assertEquals(Arrays.asList(1, 2, 3), result.get(1).legacyLineages());
		assertThrows(UnsupportedOperationException.class,
				() -> result.get(1).legacyLineages().add(2));
	}

	@Test
	void paginationRemainsBoundedAtMinimumResolution() {
		assertEquals(3, OreSpawnModsScreen.pageSize(240));
		assertEquals(1, OreSpawnModsScreen.pageCount(0, 3));
		assertEquals(1, OreSpawnModsScreen.pageCount(3, 3));
		assertEquals(2, OreSpawnModsScreen.pageCount(4, 3));
	}

	private static Entry find(List<Entry> entries, String modId) {
		return entries.stream().filter(entry -> modId.equals(entry.modId)).findFirst().orElseThrow(
				() -> new AssertionError("Missing " + modId));
	}

	private static ProviderIntegrationInfo provider(String modId, boolean nativeOs4,
			boolean hasProvider, int schema, int revision, ProviderStatus status,
			boolean rejected, Integer... lineages) {
		return new ProviderIntegrationInfo(modId, nativeOs4, hasProvider, schema, revision,
				status, rejected, Arrays.asList(lineages));
	}
}
