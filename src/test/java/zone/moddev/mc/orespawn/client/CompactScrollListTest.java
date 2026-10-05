package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CompactScrollListTest {
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

		list.mouseClicked(105, 83, 0); // Lower scrollbar arrow.
		assertEquals(4, list.firstIndex());
		list.mouseClicked(105, 65, 0); // Track below the thumb.
		assertEquals(8, list.firstIndex());

		list.setFirstIndex(0);
		list.mouseClicked(20, 53, 0);
		assertEquals(2, list.selected);
		list.mouseClicked(95, 21, 0);
		assertEquals(0, list.action);
		list.setFirstIndex(20);
		list.ensureVisible(5);
		assertEquals(5, list.firstIndex());
		list.ensureVisible(12);
		assertEquals(9, list.firstIndex());

		list.setFirstIndex(0);
		list.mouseClicked(105, 31, 0); // Grab the scrollbar thumb.
		list.mouseDragged(105, 75, 0, 0, 44);
		assertTrue(list.firstIndex() > 0);
		list.mouseReleased(105, 75, 0);
	}

	private static final class ProbeList extends CompactScrollList {
		int selected = -1;
		int action = -1;

		ProbeList() { super(null, 10, 20, 100, 64); }
		@Override protected int size() { return 30; }
		@Override protected String rowText(int index) { return Integer.toString(index); }
		@Override protected void onRowPressed(int index) { selected = index; }
		@Override protected int rowActionWidth(int index) { return 10; }
		@Override protected void onRowActionPressed(int index) { action = index; }
	}
}
