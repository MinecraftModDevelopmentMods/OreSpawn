package zone.moddev.mc.orespawn.clientprobe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.util.FormattedCharSequence;

/** Checks the text Minecraft actually queued, not just the labels stored by a widget. */
final class EditorTextRenderProbe {
	private static final Set<String> verifiedLayouts = new HashSet<>();
	private static int biomeStage;
	private static int originalWidth;
	private static int originalHeight;
	private static boolean biomeInputVerified;
	private static boolean oreSourceInputVerified;

	private EditorTextRenderProbe() { }

	static void verify(Screen screen, GuiGraphicsExtractor graphics) throws ReflectiveOperationException {
		GuiRenderState state = (GuiRenderState) field(graphics, "guiRenderState");
		List<Text> texts = new ArrayList<>();
		state.forEachText(text -> {
			try {
				StringBuilder label = new StringBuilder();
				((FormattedCharSequence) field(text, "text")).accept((index, style, codepoint) -> {
					label.appendCodePoint(codepoint);
					return true;
				});
				texts.add(new Text(label.toString(), (int) field(text, "x"), (int) field(text, "y"),
						(int) field(text, "color"), text.bounds() != null));
			} catch (ReflectiveOperationException exception) {
				throw new IllegalStateException("Cannot inspect the native GUI text state", exception);
			}
		});
		Class<?> listType = Class.forName("zone.moddev.mc.orespawn.client.CompactScrollList");
		int checkedRows = 0;
		for (var child : screen.children()) {
			if (!listType.isInstance(child) || !((AbstractWidget) child).visible) continue;
			AbstractWidget widget = (AbstractWidget) child;
			int first = (int) invoke(listType, child, "firstIndex");
			int end = Math.min((int) invoke(listType, child, "size"),
					first + (int) invoke(listType, child, "visibleRows"));
			for (int index = first; index < end; index++) {
				String label = (String) invoke(listType, child, "rowText", index);
				int textWidth = Math.max(0, (int) invoke(listType, child, "contentRight") - widget.getX()
						- Math.max(0, (int) invoke(listType, child, "rowActionWidth", index)) - 8);
				var font = Minecraft.getInstance().font;
				if (font.width(label) > textWidth) {
					label = font.plainSubstrByWidth(label, Math.max(0, textWidth - font.width("..."))) + "...";
				}
				int y = (int) invoke(listType, child, "rowY", index)
						+ Math.max(2, ((int) field(child, listType, "rowHeight") - 8) / 2);
				int color = (int) invoke(listType, child, "rowColor", index);
				requireText(texts, label, widget.getX() + 4, y, color);
				checkedRows++;
			}
			int scrollbarX = (int) invoke(listType, child, "contentRight") + 5;
			for (String arrow : List.of("^", "v")) {
				int y = widget.getY() + (arrow.equals("^") ? 0 : widget.getHeight() - 9);
				requireText(texts, arrow, scrollbarX - Minecraft.getInstance().font.width(arrow) / 2,
						y, 0xFFDDDDDD);
			}
		}
		String name = screen.getClass().getSimpleName();
		if (name.equals("BiomeWorldMaterialsScreen")) {
			boolean detail = screen.width >= 400 || (boolean) field(screen, "compactDetail");
			if (detail && field(screen, "selectedId") != null) {
				int statusY = (screen.width < 400 ? 34 : 24) + 22;
				boolean statusVisible = List.of("unmanaged", "provider_managed", "disabled", "missing",
						"layered", "user_replaced").stream().anyMatch(status -> {
					String label = Component.translatable("value.orespawn.biome.status." + status).getString();
					return texts.stream().anyMatch(text -> text.label.equals(label) && text.y == statusY
							&& text.visible && (text.color >>> 24) == 255);
				});
				if (!statusVisible) throw new IllegalStateException("Selected biome status was not rendered");
			}
			if (checkedRows > 0 || (screen.width < 400 && detail)) {
				String key = screen.width + "x" + screen.height + (screen.width < 400 && detail ? ":detail" : "");
				if (verifiedLayouts.add(key)) System.out.println("EDITOR_TEXT_RENDER_PASS " + key + " rows=" + checkedRows);
			}
		}
	}

	static boolean exerciseBiomeDirectory(Screen screen, Consumer<Button> press) throws ReflectiveOperationException {
		switch (biomeStage) {
		case 0:
			originalWidth = screen.width;
			originalHeight = screen.height;
			for (var child : screen.children()) {
				if (child instanceof Button button && button.getMessage().getString().equals("Show All")) {
					press.accept(button);
					break;
				}
			}
			screen.resize(426, 265);
			biomeStage++;
			return false;
		case 1:
			if (!verifiedLayouts.contains("426x265")) return false;
			clickBiomeRow(screen, 1);
			exerciseBiomeScrollbar(screen);
			screen.resize(320, 240);
			biomeStage++;
			return false;
		case 2:
			if (!verifiedLayouts.contains("320x240")) return false;
			clickBiomeRow(screen, 2);
			if (!(boolean) field(screen, "compactDetail")) throw new IllegalStateException("Biome click did not open compact details");
			biomeInputVerified = true;
			biomeStage++;
			return false;
		case 3:
			if (!verifiedLayouts.contains("320x240:detail")) return false;
			screen.resize(originalWidth, originalHeight);
			biomeStage++;
			return true;
		default:
			return true;
		}
	}

	static boolean completed() {
		return biomeStage == 4 && biomeInputVerified
				&& verifiedLayouts.containsAll(List.of("426x265", "320x240", "320x240:detail"));
	}

	static boolean oreSourceInputVerified() { return oreSourceInputVerified; }

	static void exerciseOreSources(Screen screen) throws ReflectiveOperationException {
		Class<?> listType = Class.forName("zone.moddev.mc.orespawn.client.CompactScrollList");
		Object groups = field(screen, "groupList");
		if ((int) invoke(listType, groups, "size") == 0) return;
		clickListRow(screen, groups, listType, (int) invoke(listType, groups, "firstIndex"));
		Object outputs = field(screen, "outputList");
		if (outputs != null && (int) invoke(listType, outputs, "size") > 1
				&& !(boolean) invoke(listType, outputs, "rowEnabled", 0)) {
			// Keep Original intentionally locks its outputs. Select Balanced through the UI first.
			for (var child : screen.children()) {
				if (child instanceof Button button && button.getMessage().getString().startsWith("Output:")) {
					click(screen, button.getX() + 4, button.getY() + 8);
					break;
				}
			}
			outputs = field(screen, "outputList");
		}
		System.out.println("ORE_SOURCE_INPUT_SETUP groups=" + invoke(listType, groups, "size")
				+ " outputs=" + (outputs == null ? 0 : invoke(listType, outputs, "size"))
				+ " editable=" + (outputs != null && (int) invoke(listType, outputs, "size") > 0
						&& (boolean) invoke(listType, outputs, "rowEnabled", 0)));
		if (outputs != null && (int) invoke(listType, outputs, "size") > 0
				&& (boolean) invoke(listType, outputs, "rowEnabled", 0)) {
			String before = (String) invoke(listType, outputs, "rowText", 0);
			clickListRow(screen, outputs, listType, 0);
			outputs = field(screen, "outputList");
			if (before.equals(invoke(listType, outputs, "rowText", 0))) {
				throw new IllegalStateException("Ore output checkbox did not change after a native click");
			}
			clickListRow(screen, outputs, listType, 0);
			if (!before.equals(invoke(listType, field(screen, "outputList"), "rowText", 0))) {
				throw new IllegalStateException("Ore output checkbox did not restore its original selection");
			}
			oreSourceInputVerified = true;
			System.out.println("ORE_SOURCE_INPUT_PASS native_group_and_checkbox_clicks");
		}
	}

	private static void clickBiomeRow(Screen screen, int rowOffset) throws ReflectiveOperationException {
		Class<?> listType = Class.forName("zone.moddev.mc.orespawn.client.CompactScrollList");
		Object list = field(screen, "biomeList");
		int index = (int) invoke(listType, list, "firstIndex") + rowOffset;
		Object previous = field(screen, "selectedId");
		clickListRow(screen, list, listType, index);
		if (previous.equals(field(screen, "selectedId")) || !(boolean) invoke(listType, list, "rowSelected", index)) {
			throw new IllegalStateException("Native biome click did not select row " + index);
		}
		System.out.println("BIOME_INPUT_PASS " + screen.width + "x" + screen.height + " selected=" + field(screen, "selectedId"));
	}

	private static void exerciseBiomeScrollbar(Screen screen) throws ReflectiveOperationException {
		Class<?> listType = Class.forName("zone.moddev.mc.orespawn.client.CompactScrollList");
		Object list = field(screen, "biomeList");
		AbstractWidget widget = (AbstractWidget) list;
		int first = (int) invoke(listType, list, "firstIndex");
		if (!screen.mouseScrolled(widget.getX() + 4, widget.getY() + 8, 0, -1)
				|| (int) invoke(listType, list, "firstIndex") != first + 3) {
			throw new IllegalStateException("Biome wheel scroll did not reach the list");
		}
		int scrollbarX = (int) invoke(listType, list, "contentRight") + 5;
		click(screen, scrollbarX, widget.getY() + widget.getHeight() - 4);
		int beforeDrag = (int) invoke(listType, list, "firstIndex");
		if (beforeDrag != first + 4) throw new IllegalStateException("Biome scrollbar arrow did not scroll");
		int thumbY = (int) invoke(listType, list, "thumbY") + 2;
		if (!screen.mouseClicked(leftClick(scrollbarX, thumbY), false)) throw new IllegalStateException("Biome scrollbar did not take the drag");
		int endY = widget.getY() + widget.getHeight() - 18;
		if (!screen.mouseDragged(leftClick(scrollbarX, endY), 0, endY - thumbY)
				|| (int) invoke(listType, list, "firstIndex") <= beforeDrag) {
			throw new IllegalStateException("Biome scrollbar thumb did not move");
		}
		screen.mouseReleased(leftClick(scrollbarX, endY));
		if (screen.mouseDragged(leftClick(scrollbarX, thumbY), 0, thumbY - endY)) {
			throw new IllegalStateException("Biome scrollbar kept dragging after release");
		}
		clickBiomeRow(screen, 1);
		System.out.println("BIOME_SCROLLBAR_INPUT_PASS wheel_arrow_drag_release_and_scrolled_selection");
	}

	private static void clickListRow(Screen screen, Object list, Class<?> listType, int index) throws ReflectiveOperationException {
		click(screen, ((AbstractWidget) list).getX() + 4, (int) invoke(listType, list, "rowY", index) + 8);
	}

	private static void click(Screen screen, double x, double y) {
		MouseButtonEvent event = leftClick(x, y);
		if (!screen.mouseClicked(event, false)) throw new IllegalStateException("Screen rejected a native left click at " + x + "," + y);
		screen.mouseReleased(event);
	}

	private static MouseButtonEvent leftClick(double x, double y) {
		return new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
	}

	private static void requireText(List<Text> texts, String label, int x, int y, int color) {
		if ((color >>> 24) != 255 || texts.stream().noneMatch(text -> text.label.equals(label)
				&& text.x == x && text.y == y && text.color == color && text.visible)) {
			throw new IllegalStateException("Editor text was not rendered: '" + label + "' at " + x + "," + y
					+ " colour=" + Integer.toHexString(color));
		}
	}

	private static Object field(Object target, String name) throws ReflectiveOperationException {
		return field(target, target.getClass(), name);
	}

	private static Object field(Object target, Class<?> type, String name) throws ReflectiveOperationException {
		Field field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static Object invoke(Class<?> type, Object target, String name, int... arguments) throws ReflectiveOperationException {
		Method method = type.getDeclaredMethod(name, arguments.length == 0 ? new Class<?>[0] : new Class<?>[] {int.class});
		method.setAccessible(true);
		return arguments.length == 0 ? method.invoke(target) : method.invoke(target, arguments[0]);
	}

	private record Text(String label, int x, int y, int color, boolean visible) { }
}
