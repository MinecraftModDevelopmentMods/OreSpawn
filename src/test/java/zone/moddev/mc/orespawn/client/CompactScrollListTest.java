package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

class CompactScrollListTest {
	@Test
	void nativeClicksReachRowsActionsAndScrollbarThroughTheContainer() {
		ProbeList list = new ProbeList();
		ProbeContainer container = new ProbeContainer(list);
		assertTrue(container.mouseClicked(leftClick(20, 53), false));
		assertEquals(2, list.selected);
		assertTrue(container.mouseClicked(leftClick(95, 21), false));
		assertEquals(0, list.action);
		assertTrue(container.mouseClicked(leftClick(105, 83), false));
		assertEquals(1, list.firstIndex());
		assertFalse(container.mouseClicked(leftClick(2, 2), false));
	}

	@Test
	void nativeScrollbarDragAndReleaseUseTheSameButtonAsTheContainer() {
		ProbeList list = new ProbeList();
		ProbeContainer container = new ProbeContainer(list);
		assertTrue(container.mouseClicked(leftClick(105, 31), false));
		assertTrue(container.mouseDragged(leftClick(105, 75), 0, 44));
		assertTrue(list.firstIndex() > 0);
		assertTrue(container.mouseReleased(leftClick(105, 75)));
		int releasedIndex = list.firstIndex();
		assertFalse(container.mouseDragged(leftClick(105, 35), 0, -40));
		assertEquals(releasedIndex, list.firstIndex());
	}

	@Test
	void nonPrimaryClicksAndUnavailableRowsDoNotSelectAnything() {
		ProbeList list = new ProbeList();
		ProbeContainer container = new ProbeContainer(list);
		for (int button : new int[] {InputConstants.MOUSE_BUTTON_RIGHT, InputConstants.MOUSE_BUTTON_MIDDLE}) {
			MouseButtonEvent event = new MouseButtonEvent(20, 21, new MouseButtonInfo(button, 0));
			assertFalse(list.mouseClicked(event, false));
			// The container consumes an in-bounds click even when its child rejects the button.
			assertTrue(container.mouseClicked(event, false));
			assertFalse(container.isDragging());
			assertEquals(-1, list.selected);
			assertEquals(-1, list.action);
			assertEquals(0, list.firstIndex());
		}
		assertTrue(container.mouseClicked(leftClick(20, 37), false));
		assertEquals(-1, list.selected);
		list.active = false;
		assertFalse(container.mouseClicked(leftClick(20, 21), false));
		list.active = true;
		list.visible = false;
		assertFalse(container.mouseClicked(leftClick(20, 21), false));
		assertEquals(-1, list.selected);
	}

	private static MouseButtonEvent leftClick(double x, double y) {
		return new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
	}

	@Test
	void enabledAndDisabledRowsStayVisible() {
		ProbeList list = new ProbeList();
		assertEquals(0xFFFFFFFF, list.rowColor(0));
		assertEquals(0xFF777777, list.rowColor(1));
	}

	@Test
	void bothSupportedSizesKeepRowsAndSideScrollbars() {
		assertEquals(13, CompactScrollList.visibleRows(209, 16));
		assertEquals(11, CompactScrollList.visibleRows(184, 16));
		assertEquals(5, CompactScrollList.maxFirst(18, 13));
		assertEquals(7, CompactScrollList.maxFirst(18, 11));
		assertEquals(137, CompactScrollList.thumbHeight(18, 13, 191));
		assertEquals(3, CompactScrollList.firstFromThumb(27, 54, 5));
	}

	@Test
	void wheelArrowsTrackThumbAndRowTargetsUseOnlyTheirOwnList() {
		ProbeList list = new ProbeList();
		assertTrue(list.mouseWheel(20, 30, -120));
		assertEquals(3, list.firstIndex());
		assertFalse(list.mouseWheel(2, 2, -120));

		list.mouseClicked(105, 83, InputConstants.MOUSE_BUTTON_LEFT); // Lower scrollbar arrow.
		assertEquals(4, list.firstIndex());
		list.mouseClicked(105, 65, InputConstants.MOUSE_BUTTON_LEFT); // Track below the thumb.
		assertEquals(8, list.firstIndex());

		list.setFirstIndex(0);
		list.mouseClicked(20, 53, InputConstants.MOUSE_BUTTON_LEFT);
		assertEquals(2, list.selected);
		list.mouseClicked(95, 21, InputConstants.MOUSE_BUTTON_LEFT);
		assertEquals(0, list.action);
		list.setFirstIndex(20);
		list.ensureVisible(5);
		assertEquals(5, list.firstIndex());
		list.ensureVisible(12);
		assertEquals(9, list.firstIndex());

		list.setFirstIndex(0);
		list.mouseClicked(105, 31, InputConstants.MOUSE_BUTTON_LEFT); // Grab the scrollbar thumb.
		list.mouseDragged(105, 75, InputConstants.MOUSE_BUTTON_LEFT, 0, 44);
		assertTrue(list.firstIndex() > 0);
		list.mouseReleased(105, 75, InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static final class ProbeContainer extends AbstractContainerEventHandler {
		private final ProbeList list;
		ProbeContainer(ProbeList list) { this.list = list; }
		@Override public java.util.List<? extends GuiEventListener> children() { return java.util.List.of(list); }
	}

	private static final class ProbeList extends CompactScrollList {
		int selected = -1;
		int action = -1;

		ProbeList() { super(null, 10, 20, 100, 64); }
		@Override protected int size() { return 30; }
		@Override protected String rowText(int index) { return Integer.toString(index); }
		@Override protected boolean rowEnabled(int index) { return index % 2 == 0; }
		@Override protected void onRowPressed(int index) { selected = index; }
		@Override protected int rowActionWidth(int index) { return 10; }
		@Override protected void onRowActionPressed(int index) { action = index; }
	}
}
