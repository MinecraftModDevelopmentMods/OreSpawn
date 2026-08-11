package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import net.minecraft.util.text.TextComponentString;

/** Protects the distinction between explicit close and vanilla screen removal. */
class OreSpawnScreenLifecycleTest {
	@Test
	void vanillaRemovalMustNotInvokeTheNavigationCallback() {
		ProbeScreen screen = new ProbeScreen();

		screen.onGuiClosed();

		assertEquals(0, screen.closeCalls,
				"displayGuiScreen already chose the next screen; closing again recurses forever");
	}

	@Test
	void explicitEscapeInvokesTheNavigationCallbackExactlyOnce() throws Exception {
		ProbeScreen screen = new ProbeScreen();

		screen.keyTyped('\0', Keyboard.KEY_ESCAPE);

		assertEquals(1, screen.closeCalls,
				"Escape should retain the later-screen onClose contract without using onGuiClosed");
	}

	private static final class ProbeScreen extends OreSpawnScreen {
		private int closeCalls;

		private ProbeScreen() {
			super(new TextComponentString("Lifecycle probe"));
		}

		@Override
		public void onClose() {
			closeCalls++;
		}
	}
}
