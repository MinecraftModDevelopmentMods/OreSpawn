package zone.moddev.mc.orespawn.clientprobe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import zone.moddev.mc.orespawn.client.OreSpawnWorldSettingsScreen;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

/** Checks the actual menu widgets against Minecraft's tab area and footer. */
final class MainMenuLayoutProbe {
	private static boolean completed;

	private MainMenuLayoutProbe() { }

	static boolean completed() { return completed; }

	static void verify(CreateWorldScreen parent) {
		try {
			verifyLayouts(parent);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not inspect the main-menu layout", failure);
		}
	}

	private static void verifyLayouts(CreateWorldScreen parent) throws ReflectiveOperationException {
		String pendingBefore = WorldGeologyProfileManager.pendingNewWorldProfile().rootCopy().toString();
		Class<?> tabType = Class.forName("zone.moddev.mc.orespawn.client.OreSpawnWorldCreationTab");
		Constructor<?> constructor = tabType.getDeclaredConstructor(CreateWorldScreen.class, WorldGeologyProfile.class, List.class);
		constructor.setAccessible(true);
		HeaderAndFooterLayout footer = ObfuscationReflectionHelper.getPrivateValue(CreateWorldScreen.class, parent, "layout");
		TabNavigationBar navigation = ObfuscationReflectionHelper.getPrivateValue(CreateWorldScreen.class, parent, "tabNavigationBar");
		int footerInset = footer.getFooterHeight();
		int tabTop = navigation.getRectangle().bottom();
		int cases = 0;
		for (int[] size : new int[][] {{426, 240}, {426, 265}, {320, 240}}) {
			for (boolean terrain : new boolean[] {true, false}) {
				for (boolean template : new boolean[] {false, true}) {
					for (boolean fluids : new boolean[] {false, true}) {
						WorldGeologyProfile profile = profile(terrain, template, fluids);
						GridLayoutTab tab = (GridLayoutTab) constructor.newInstance(parent, profile, List.of("minecraft:overworld"));
						ScreenRectangle area = new ScreenRectangle(0, tabTop, size[0], size[1] - footerInset - tabTop);
						tab.doLayout(area);
						List<AbstractWidget> controls = new ArrayList<>();
						tab.visitChildren(controls::add);
						assertFits(controls, 0, tabTop, size[0], area.bottom() - 2);
						assertHeader(controls);
						// Reuse the tab after resizing to catch stale widths and clipped labels.
						tab.doLayout(new ScreenRectangle(0, tabTop, 320, 240 - footerInset - tabTop));
						tab.doLayout(area);
						assertFits(controls, 0, tabTop, size[0], area.bottom() - 2);
						assertHeader(controls);

						Screen fallback = new OreSpawnWorldSettingsScreen(parent, profile);
						fallback.init(size[0], size[1]);
						controls = fallback.children().stream().filter(AbstractWidget.class::isInstance)
								.map(AbstractWidget.class::cast).filter(widget -> widget.getY() < size[1] - 28).toList();
						assertFits(controls, 0, 0, size[0], size[1] - 32);
						assertHeader(controls);
						cases++;
					}
				}
			}
		}
		if (!pendingBefore.equals(WorldGeologyProfileManager.pendingNewWorldProfile().rootCopy().toString())) {
			throw new IllegalStateException("Layout checks changed the pending world profile");
		}
		completed = true;
		System.out.println("MAIN_MENU_LAYOUT_PASS cases=" + cases + " native_and_fallback=true header_pair=true footer_clear=true");
	}

	private static WorldGeologyProfile profile(boolean terrain, boolean template, boolean fluids) throws ReflectiveOperationException {
		Class<?> type = Class.forName("zone.moddev.mc.orespawn.client.GeologyEditorSession");
		Constructor<?> constructor = type.getDeclaredConstructor(WorldGeologyProfile.class);
		constructor.setAccessible(true);
		Object session = constructor.newInstance(WorldGeologyProfile.recommended(fluids));
		if (terrain) {
			Method configure = type.getDeclaredMethod("configureDefaultVanillaStrata");
			configure.setAccessible(true);
			configure.invoke(session);
		}
		Method currentProfile = type.getDeclaredMethod("profile");
		currentProfile.setAccessible(true);
		WorldGeologyProfile profile = (WorldGeologyProfile) currentProfile.invoke(session);
		JsonObject root = profile.rootCopy();
		if (template) root.addProperty("selected_template", "orespawn:layout_probe");
		if (fluids) {
			JsonObject dimension = new JsonObject();
			dimension.addProperty("enabled", true);
			dimension.addProperty("min_y", 16);
			dimension.addProperty("max_y", 32);
			dimension.addProperty("frequency", 0.05);
			dimension.addProperty("min_radius", 3);
			dimension.addProperty("max_radius", 5);
			dimension.addProperty("min_vertical_radius", 2);
			dimension.addProperty("max_vertical_radius", 3);
			dimension.addProperty("max_lobes", 1);
			dimension.addProperty("min_solid_cover", 1);
			JsonArray hosts = new JsonArray();
			hosts.add("minecraft:stone");
			dimension.add("host_blocks", hosts);
			JsonObject dimensions = new JsonObject();
			dimensions.add("minecraft:overworld", dimension);
			JsonObject fluid = new JsonObject();
			fluid.addProperty("enabled", true);
			fluid.addProperty("block", "minecraft:water");
			fluid.add("dimensions", dimensions);
			JsonObject deposits = new JsonObject();
			deposits.add("orespawn:layout_water", fluid);
			root.add("fluid_deposits", deposits);
		}
		return profile.withRoot(root);
	}

	private static void assertHeader(List<AbstractWidget> controls) {
		AbstractWidget recommended = named(controls, "Recommended Defaults");
		AbstractWidget help = named(controls, "Help & Guide");
		if (recommended.getY() != help.getY() || recommended.getX() + recommended.getWidth() > help.getX()) {
			throw new IllegalStateException("Help and Recommended Defaults do not share a clear header row");
		}
	}

	private static AbstractWidget named(List<AbstractWidget> controls, String label) {
		List<AbstractWidget> matches = controls.stream()
				.filter(widget -> widget.visible && widget.getMessage().getString().equals(label)).toList();
		if (matches.size() != 1) throw new IllegalStateException("Expected one main-menu action: " + label);
		return matches.get(0);
	}

	private static void assertFits(List<AbstractWidget> controls, int left, int top, int right, int bottom) {
		List<AbstractWidget> visible = controls.stream().filter(widget -> widget.visible).toList();
		for (int index = 0; index < visible.size(); index++) {
			AbstractWidget widget = visible.get(index);
			if (widget.getX() < left || widget.getY() < top || widget.getX() + widget.getWidth() > right
					|| widget.getY() + widget.getHeight() > bottom) {
				throw new IllegalStateException("Main control crosses the content boundary: " + widget.getMessage().getString()
						+ " at " + widget.getRectangle() + " limit=" + left + "," + top + "," + right + "," + bottom);
			}
			for (int other = index + 1; other < visible.size(); other++) {
				AbstractWidget peer = visible.get(other);
				if (widget.getX() < peer.getX() + peer.getWidth() && widget.getX() + widget.getWidth() > peer.getX()
						&& widget.getY() < peer.getY() + peer.getHeight() && widget.getY() + widget.getHeight() > peer.getY()) {
					throw new IllegalStateException("Main controls overlap: " + widget.getMessage().getString() + " / " + peer.getMessage().getString());
				}
			}
		}
	}
}
