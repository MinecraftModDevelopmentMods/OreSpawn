package zone.moddev.mc.orespawn.clientprobe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.FormattedCharSequence;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

/** Checks that the native renderer receives the text, not just empty list widgets. */
final class EditorTextRenderProbe {
	// These values are private in 26.3; inspect them without widening the game's access rules.
	private static final Field TEXT = textField("text");
	private static final Field Y = textField("y");
	private static final Field COLOR = textField("color");
	private static boolean completed;

	private EditorTextRenderProbe() { }

	static boolean completed() { return completed; }

	static void verify(Screen parent) {
		try {
			String pending = WorldGeologyProfileManager.pendingNewWorldProfile().rootCopy().toString();
			for (int[] size : new int[][] {{426, 265}, {320, 240}}) verifyDirectory(parent, size[0], size[1]);
			if (!pending.equals(WorldGeologyProfileManager.pendingNewWorldProfile().rootCopy().toString())) {
				throw new IllegalStateException("Text rendering checks changed the pending profile");
			}
			completed = true;
			System.out.println("EDITOR_TEXT_RENDER_PASS sizes=426x265,320x240 rows=true status=true arrows=true native_click=true");
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not check editor text rendering", failure);
		}
	}

	private static void verifyDirectory(Screen parent, int width, int height) throws ReflectiveOperationException {
		Class<?> sessionType = Class.forName("zone.moddev.mc.orespawn.client.GeologyEditorSession");
		Constructor<?> sessionConstructor = sessionType.getDeclaredConstructor(
				WorldGeologyProfile.class, Iterable.class, Registry.class);
		sessionConstructor.setAccessible(true);
		Object session = sessionConstructor.newInstance(WorldGeologyProfile.recommended(false),
				List.of("minecraft:overworld"), ((CreateWorldScreen) parent).getUiState().getSettings()
						.worldgenLoadContext().lookupOrThrow(Registries.BIOME));
		Method rootMethod = sessionType.getDeclaredMethod("root");
		rootMethod.setAccessible(true);
		JsonObject root = (JsonObject) rootMethod.invoke(session);
		JsonObject palettes = new JsonObject();
		for (boolean enabled : new boolean[] {true, false}) {
			JsonObject palette = new JsonObject();
			palette.addProperty("dimension", "minecraft:overworld");
			palette.addProperty("source_provider", "orespawn");
			palette.addProperty("enabled", enabled);
			JsonObject entry = new JsonObject();
			entry.addProperty("enabled", true);
			entry.addProperty("weight", 1.0);
			JsonObject biomes = new JsonObject();
			biomes.add("minecraft:badlands", entry);
			palette.add("biomes", biomes);
			palettes.add("orespawn:text_probe_" + enabled, palette);
		}
		root.add("biome_palettes", palettes);
		root.add("dimension_materials", new JsonObject());
		String before = root.toString();
		Class<?> directoryType = Class.forName("zone.moddev.mc.orespawn.client.BiomeWorldMaterialsScreen");
		Constructor<?> constructor = directoryType.getDeclaredConstructor(Screen.class, sessionType);
		constructor.setAccessible(true);
		Screen screen = (Screen) constructor.newInstance(parent, session);
		field(screen, "showAll").setBoolean(screen, true);
		field(screen, "selectedId").set(screen, "minecraft:badlands");
		screen.init(width, height);
		Object list = field(screen, "biomeList").get(screen);
		List<GuiTextRenderState> text = render(screen);
		assertRows(list, text);
		assertText(text, "^");
		assertText(text, "v");

		AbstractWidget widget = (AbstractWidget) list;
		MouseButtonEvent click = new MouseButtonEvent(widget.getX() + 8, widget.getY() + 20,
				new MouseButtonInfo(0, 0));
		boolean consumed = widget.mouseClicked(click, false);
		if (!consumed || "minecraft:badlands".equals(field(screen, "selectedId").get(screen))) {
			throw new IllegalStateException("The native mouse event did not select another biome: consumed="
					+ consumed + " at=" + click.x() + "," + click.y() + " selected="
					+ field(screen, "selectedId").get(screen) + " row0=" + listMethod("rowText", int.class).invoke(list, 0)
					+ " row1=" + listMethod("rowText", int.class).invoke(list, 1));
		}

		field(screen, "selectedId").set(screen, "minecraft:badlands");
		field(screen, "compactDetail").setBoolean(screen, width < 400);
		screen.init(width, height);
		text = render(screen);
		Object rules = field(screen, "ruleList").get(screen);
		if ((int) listMethod("size").invoke(rules) != 2) {
			throw new IllegalStateException("The text fixture needs enabled and disabled placement rows");
		}
		assertRows(rules, text);
		boolean statusVisible = false;
		for (Object status : Class.forName("zone.moddev.mc.orespawn.client.BiomeDirectoryModel$Status").getEnumConstants()) {
			String caption = I18n.get("value.orespawn.biome.status." + status.toString().toLowerCase(Locale.ROOT));
			statusVisible |= text.stream().anyMatch(row -> plain(row).equals(caption) && (integer(COLOR, row) >>> 24) == 255);
		}
		if (!statusVisible) throw new IllegalStateException("The selected biome's status text is invisible");
		if (!before.equals(root.toString())) throw new IllegalStateException("Rendering changed the editor session");
	}

	private static List<GuiTextRenderState> render(Screen screen) {
		GuiRenderState state = new GuiRenderState();
		screen.extractRenderState(new GuiGraphicsExtractor(Minecraft.getInstance(), state, -1, -1), -1, -1, 0.0F);
		List<GuiTextRenderState> text = new ArrayList<>();
		state.forEachText(text::add);
		return text;
	}

	private static void assertRows(Object list, List<GuiTextRenderState> text) throws ReflectiveOperationException {
		int first = (int) listMethod("firstIndex").invoke(list);
		int rows = (int) listMethod("visibleRows").invoke(list);
		int total = (int) listMethod("size").invoke(list);
		if (total == 0) throw new IllegalStateException("The text fixture has no rows");
		for (int index = first; index < Math.min(total, first + rows); index++) {
			String label = ((String) listMethod("rowText", int.class).invoke(list, index)).strip();
			String prefix = label.substring(0, Math.min(8, label.length()));
			int top = (int) listMethod("rowY", int.class).invoke(list, index);
			boolean found = text.stream().anyMatch(row -> integer(Y, row) >= top && integer(Y, row) < top + 16
					&& plain(row).strip().startsWith(prefix) && (integer(COLOR, row) >>> 24) == 255);
			if (!found) throw new IllegalStateException("Invisible list text: " + label);
		}
	}

	private static void assertText(List<GuiTextRenderState> text, String expected) {
		if (text.stream().noneMatch(row -> plain(row).equals(expected) && (integer(COLOR, row) >>> 24) == 255)) {
			throw new IllegalStateException("Invisible editor text: " + expected);
		}
	}

	private static String plain(GuiTextRenderState row) {
		StringBuilder text = new StringBuilder();
		((FormattedCharSequence) value(TEXT, row)).accept((index, style, codePoint) -> { text.appendCodePoint(codePoint); return true; });
		return text.toString();
	}

	private static Field textField(String name) {
		try {
			Field field = GuiTextRenderState.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not inspect native text field: " + name, failure);
		}
	}

	private static Object value(Field field, GuiTextRenderState row) {
		try {
			return field.get(row);
		} catch (IllegalAccessException failure) {
			throw new IllegalStateException("Could not read native text field: " + field.getName(), failure);
		}
	}

	private static int integer(Field field, GuiTextRenderState row) {
		return (Integer) value(field, row);
	}

	private static Field field(Object target, String name) throws NoSuchFieldException {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static Method listMethod(String name, Class<?>... parameters) throws ReflectiveOperationException {
		Method method = Class.forName("zone.moddev.mc.orespawn.client.CompactScrollList").getDeclaredMethod(name, parameters);
		method.setAccessible(true);
		return method;
	}
}
