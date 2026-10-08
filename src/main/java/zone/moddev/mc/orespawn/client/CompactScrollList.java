package zone.moddev.mc.orespawn.client;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * Compact text list with independent row and scrollbar input.
 * It deliberately avoids GuiSlot, whose legacy coordinate handling assumes a
 * full-width screen and is unsuitable for two adjacent panes.
 */
abstract class CompactScrollList extends Button {
	static final int DEFAULT_ROW_HEIGHT = 16;
	private static final int SCROLLBAR_WIDTH = 10;
	private static final int ARROW_HEIGHT = 9;
	private static final int MIN_THUMB_HEIGHT = 8;

	private final OreSpawnScreen owner;
	private final int rowHeight;
	private int firstIndex;
	private boolean dragging;
	private int dragOffset;
	private int hoveredIndex = -1;
	private boolean hoveredAction;
	private boolean pointerInside;

	CompactScrollList(OreSpawnScreen owner, int x, int y, int width, int height) {
		this(owner, x, y, width, height, DEFAULT_ROW_HEIGHT);
	}

	CompactScrollList(OreSpawnScreen owner, int x, int y, int width, int height, int rowHeight) {
		super(x, y, width, height, "", null);
		this.owner = owner;
		this.rowHeight = Math.max(12, rowHeight);
	}

	protected abstract int size();

	protected abstract String rowText(int index);

	protected void onRowPressed(int index) { }

	protected boolean rowEnabled(int index) {
		return true;
	}

	protected boolean rowSelected(int index) {
		return false;
	}

	protected int rowColor(int index) {
		return rowEnabled(index) ? 0xFFFFFF : 0x777777;
	}

	protected List<String> rowTooltip(int index) {
		return Collections.singletonList(rowText(index));
	}

	protected int rowActionWidth(int index) {
		return 0;
	}

	protected void drawRowAction(Minecraft minecraft, int index, int x, int y,
			int width, int height, boolean hovered) { }

	protected void onRowActionPressed(int index) { }

	protected List<String> rowActionTooltip(int index) {
		return rowTooltip(index);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTicks) {
		if (!visible) return;
		Minecraft minecraft = Minecraft.getInstance();
		clampFirstIndex();
		pointerInside = contains(mouseX, mouseY);

		fill(x, y, x + width, y + height, 0x76000000);
		hoveredIndex = rowAt(mouseX, mouseY);
		hoveredAction = hoveredIndex >= 0 && isInRowAction(hoveredIndex, mouseX);
		Font font = minecraft.font;
		int rows = visibleRows();
		int contentRight = contentRight();
		for (int row = 0; row < rows; row++) {
			int index = firstIndex + row;
			if (index >= size()) break;
			int rowY = y + (row * rowHeight);
			boolean hovered = index == hoveredIndex;
			if (rowSelected(index)) {
				fill(x + 1, rowY, contentRight, rowY + rowHeight - 1, 0xA052648C);
			} else if (hovered) {
				fill(x + 1, rowY, contentRight, rowY + rowHeight - 1, 0x80404040);
			}
			int actionWidth = Math.max(0, rowActionWidth(index));
			int textWidth = Math.max(0, contentRight - x - actionWidth - 8);
			String text = fit(font, rowText(index), textWidth);
			font.drawShadow(currentPoseStack(), text, x + 4,
					rowY + Math.max(2, (rowHeight - 8) / 2), rowColor(index));
			if (actionWidth > 0) {
				int actionX = contentRight - actionWidth;
				drawRowAction(minecraft, index, actionX, rowY,
						actionWidth, rowHeight - 1, hovered && mouseX >= actionX);
			}
		}
		drawScrollbar(minecraft, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !visible || !active || !contains((int) mouseX, (int) mouseY)) return false;
		int x = (int) mouseX;
		int y = (int) mouseY;
		if (x >= contentRight()) {
			pressScrollbar(y);
			return true;
		}
		int index = rowAt(x, y);
		if (index < 0) return true;
		if (isInRowAction(index, x)) onRowActionPressed(index);
		else if (rowEnabled(index)) onRowPressed(index);
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
		return button == 0 && contains((int) mouseX, (int) mouseY);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button,
			double deltaX, double deltaY) {
		if (!dragging || button != 0) return false;
		dragTo((int) mouseY - dragOffset);
		return true;
	}

	void renderTooltip(int mouseX, int mouseY) {
		if (!visible || hoveredIndex < 0 || !contains(mouseX, mouseY)) return;
		List<String> lines = hoveredAction ? rowActionTooltip(hoveredIndex)
				: rowTooltip(hoveredIndex);
		if (lines != null && !lines.isEmpty()) owner.renderStringTooltip(lines, mouseX, mouseY);
	}

	boolean mouseWheel(int mouseX, int mouseY, int delta) {
		if (!visible || delta == 0 || !contains(mouseX, mouseY)) return false;
		scrollBy(delta > 0 ? -3 : 3);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (!visible || !active || !contains((int) mouseX, (int) mouseY) || delta == 0.0D) return false;
		scrollBy(delta > 0.0D ? -3 : 3);
		return true;
	}

	void scrollBy(int rows) {
		setFirstIndex(firstIndex + rows);
	}

	int firstIndex() {
		return firstIndex;
	}

	void setFirstIndex(int index) {
		firstIndex = Math.max(0, Math.min(index, maxFirst(size(), visibleRows())));
	}

	void ensureVisible(int index) {
		if (index < firstIndex) setFirstIndex(index);
		else if (index >= firstIndex + visibleRows()) setFirstIndex(index - visibleRows() + 1);
	}

	int visibleRows() {
		return visibleRows(height, rowHeight);
	}

	int rowY(int index) {
		return index < firstIndex || index >= firstIndex + visibleRows()
				? -1 : y + ((index - firstIndex) * rowHeight);
	}

	int contentRight() {
		return x + width - SCROLLBAR_WIDTH;
	}

	private boolean contains(int mouseX, int mouseY) {
		return mouseX >= x && mouseX < x + width
				&& mouseY >= y && mouseY < y + height;
	}

	private int rowAt(int mouseX, int mouseY) {
		if (!contains(mouseX, mouseY) || mouseX >= contentRight()) return -1;
		int index = firstIndex + ((mouseY - y) / rowHeight);
		return index >= 0 && index < size() ? index : -1;
	}

	private boolean isInRowAction(int index, int mouseX) {
		int actionWidth = Math.max(0, rowActionWidth(index));
		return actionWidth > 0 && mouseX >= contentRight() - actionWidth;
	}

	private void pressScrollbar(int mouseY) {
		if (mouseY < y + ARROW_HEIGHT) {
			scrollBy(-1);
			return;
		}
		if (mouseY >= y + height - ARROW_HEIGHT) {
			scrollBy(1);
			return;
		}
		int thumbY = thumbY();
		int thumbHeight = thumbHeight(size(), visibleRows(), trackHeight());
		if (mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
			dragging = true;
			dragOffset = mouseY - thumbY;
		} else {
			scrollBy(mouseY < thumbY ? -visibleRows() : visibleRows());
		}
	}

	private void dragTo(int thumbTop) {
		int maximum = maxFirst(size(), visibleRows());
		int track = trackHeight();
		int thumb = thumbHeight(size(), visibleRows(), track);
		int travel = Math.max(1, track - thumb);
		int relative = Math.max(0, Math.min(travel, thumbTop - (y + ARROW_HEIGHT)));
		setFirstIndex(firstFromThumb(relative, travel, maximum));
	}

	private int trackHeight() {
		return Math.max(1, height - (ARROW_HEIGHT * 2));
	}

	private int thumbY() {
		int maximum = maxFirst(size(), visibleRows());
		int track = trackHeight();
		int thumb = thumbHeight(size(), visibleRows(), track);
		int travel = Math.max(0, track - thumb);
		return y + ARROW_HEIGHT + (maximum == 0 ? 0
				: Math.round((float) firstIndex * travel / maximum));
	}

	private void drawScrollbar(Minecraft minecraft, int mouseX, int mouseY) {
		int left = contentRight();
		int right = x + width;
		fill(left, y, right, y + height, 0xB0202020);
		Font font = minecraft.font;
		drawCenteredString(currentPoseStack(), font, "^", left + (SCROLLBAR_WIDTH / 2), y, 0xDDDDDD);
		drawCenteredString(currentPoseStack(), font, "v", left + (SCROLLBAR_WIDTH / 2),
				y + height - ARROW_HEIGHT, 0xDDDDDD);
		int thumbY = thumbY();
		int thumbHeight = thumbHeight(size(), visibleRows(), trackHeight());
		fill(left + 1, thumbY, right - 1, thumbY + thumbHeight,
				mouseX >= left && mouseX < right && mouseY >= thumbY
						&& mouseY < thumbY + thumbHeight ? 0xFFE0E0E0 : 0xFF909090);
	}

	private void clampFirstIndex() {
		setFirstIndex(firstIndex);
	}

	private static String fit(Font font, String text, int width) {
		if (width <= 0) return "";
		if (font.width(text) <= width) return text;
		String suffix = "...";
		return font.plainSubstrByWidth(text,
				Math.max(0, width - font.width(suffix))) + suffix;
	}

	static int visibleRows(int height, int rowHeight) {
		return Math.max(1, Math.max(1, height) / Math.max(1, rowHeight));
	}

	static int maxFirst(int entries, int visibleRows) {
		return Math.max(0, Math.max(0, entries) - Math.max(1, visibleRows));
	}

	static int thumbHeight(int entries, int visibleRows, int trackHeight) {
		if (entries <= visibleRows) return Math.max(1, trackHeight);
		return Math.max(MIN_THUMB_HEIGHT,
				Math.min(trackHeight, (trackHeight * Math.max(1, visibleRows)) / Math.max(1, entries)));
	}

	static int firstFromThumb(int relative, int travel, int maximum) {
		if (travel <= 0 || maximum <= 0) return 0;
		return Math.max(0, Math.min(maximum,
				Math.round((float) relative * maximum / travel)));
	}
}
