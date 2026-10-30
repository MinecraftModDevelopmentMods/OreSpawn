package zone.moddev.mc.orespawn.clientprobe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

/** Sends native mouse events through the real biome directory at both layouts. */
final class BiomeListInputProbe {
	private static boolean completed;

	private BiomeListInputProbe() { }

	static boolean completed() { return completed; }

	static void verify(Screen parent) throws ReflectiveOperationException {
		Class<?> sessionType = Class.forName("zone.moddev.mc.orespawn.client.GeologyEditorSession");
		Constructor<?> sessionConstructor = sessionType.getDeclaredConstructor(
				WorldGeologyProfile.class, Iterable.class, Registry.class);
		sessionConstructor.setAccessible(true);
		Class<?> screenType = Class.forName("zone.moddev.mc.orespawn.client.BiomeWorldMaterialsScreen");
		Constructor<?> screenConstructor = screenType.getDeclaredConstructor(Screen.class, sessionType);
		screenConstructor.setAccessible(true);
		Method profileMethod = sessionType.getDeclaredMethod("profile");
		profileMethod.setAccessible(true);
		Field showAll = field(screenType, "showAll");
		Field selected = field(screenType, "selectedId");
		Field listField = field(screenType, "biomeList");
		Field compactDetail = field(screenType, "compactDetail");
		Class<?> listType = Class.forName("zone.moddev.mc.orespawn.client.CompactScrollList");
		Method sizeMethod = listType.getDeclaredMethod("size");
		sizeMethod.setAccessible(true);

		for (int[] size : new int[][] {{426, 265}, {320, 240}}) {
			// The menu has no active level; use the editor's world-creation registry.
			Registry<?> biomes = ((CreateWorldScreen) parent).getUiState().getSettings()
					.worldgenLoadContext().lookupOrThrow(Registries.BIOME);
			Object session = sessionConstructor.newInstance(WorldGeologyProfileManager.pendingNewWorldProfile(),
					java.util.Collections.emptyList(), biomes);
			String before = ((WorldGeologyProfile) profileMethod.invoke(session)).rootCopy().toString();
			Screen screen = (Screen) screenConstructor.newInstance(parent, session);
			showAll.setBoolean(screen, true);
			screen.init(size[0], size[1]);
			AbstractWidget list = (AbstractWidget) listField.get(screen);
			if ((Integer) sizeMethod.invoke(list) < 2) throw new IllegalStateException("Biome list has no second row");
			Object original = selected.get(screen);
			boolean handled = screen.mouseClicked(new MouseButtonEvent(list.getX() + 5,
					list.getY() + 20, new MouseButtonInfo(0, 0)), false);
			if (!handled || original.equals(selected.get(screen))) {
				throw new IllegalStateException("Native biome-list click did not change the selection");
			}
			if (size[0] < 400 && !compactDetail.getBoolean(screen)) {
				throw new IllegalStateException("Compact biome selection did not open its detail page");
			}
			String after = ((WorldGeologyProfile) profileMethod.invoke(session)).rootCopy().toString();
			if (!before.equals(after)) throw new IllegalStateException("Selecting a biome changed the profile");
		}
		completed = true;
		System.out.println("BIOME_LIST_INPUT_PASS layouts=2 native_events=true profile_unchanged=true");
	}

	private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}
}
