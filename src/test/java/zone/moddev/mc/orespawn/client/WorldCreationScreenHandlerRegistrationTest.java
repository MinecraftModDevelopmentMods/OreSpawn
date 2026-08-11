package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Protects the Forge 1.12-specific static subscriber registration contract. */
class WorldCreationScreenHandlerRegistrationTest {
	private static int calls;

	@Test
	void forge14RequiresAClassObjectForStaticSubscribers() {
		calls = 0;
		EventBus instanceRegistration = new EventBus();
		instanceRegistration.register(new StaticSubscriber());
		instanceRegistration.post(new ProbeEvent());
		assertEquals(0, calls,
				"Forge 1.12 deliberately skips static subscriber methods on instance registrations");

		EventBus classRegistration = new EventBus();
		classRegistration.register(StaticSubscriber.class);
		classRegistration.post(new ProbeEvent());
		assertEquals(1, calls,
				"Registering the class object must activate Forge 1.12 static subscribers");
	}

	@Test
	void clientSetupRegistersTheStaticWorldCreationHandlerAsAClass() throws Exception {
		Path sourcePath = Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"orespawn", "client", "ClientSetup.java");
		String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
		assertTrue(source.contains("register(WorldCreationScreenHandler.class)"),
				"The OreSpawn button requires Forge 1.12 class registration for static handlers");
		assertFalse(source.contains("register(new WorldCreationScreenHandler())"),
				"Instance registration silently ignores WorldCreationScreenHandler's static methods");
	}

	@Test
	void injectedButtonCancelsVanillaWorldCreationAndRunsItsOwnCallback() throws Exception {
		Method clickHandler = null;
		for (Method method : WorldCreationScreenHandler.class.getDeclaredMethods()) {
			if (method.getParameterTypes().length == 1
					&& method.getParameterTypes()[0] == GuiScreenEvent.ActionPerformedEvent.Pre.class) {
				clickHandler = method;
				break;
			}
		}
		assertNotNull(clickHandler,
				"The injected button needs a Forge Pre handler before GuiCreateWorld sees its ID");

		Field idField = WorldCreationScreenHandler.class.getDeclaredField("WORLD_SETTINGS_BUTTON_ID");
		idField.setAccessible(true);
		int buttonId = idField.getInt(null);
		assertTrue(buttonId != 0, "ID 0 is vanilla's Create New World action");

		AtomicInteger presses = new AtomicInteger();
		Button button = new Button(buttonId, 0, 0, 100, 20,
				new TextComponentString("OreSpawn"), ignored -> presses.incrementAndGet());
		GuiScreenEvent.ActionPerformedEvent.Pre event =
				new GuiScreenEvent.ActionPerformedEvent.Pre(uninitializedCreateWorld(), button,
						Collections.singletonList(button)) {
					@Override
					public boolean isCancelable() {
						// Forge's runtime event transformer supplies this override for
						// @Cancelable events; plain JUnit deliberately has no transformer.
						return true;
					}
				};
		clickHandler.invoke(null, event);

		assertTrue(event.isCanceled(),
				"Canceling the Forge Pre event must prevent GuiCreateWorld from starting a world");
		assertEquals(1, presses.get(), "The OreSpawn callback must run exactly once");

		String handlerSource = new String(Files.readAllBytes(Paths.get("src", "main", "java",
				"zone", "moddev", "mc", "orespawn", "client", "WorldCreationScreenHandler.java")),
				StandardCharsets.UTF_8);
		assertTrue(handlerSource.contains("new Button(WORLD_SETTINGS_BUTTON_ID"),
				"The injected button itself must use the non-vanilla ID as a fail-safe");
	}

	private static GuiCreateWorld uninitializedCreateWorld() throws Exception {
		// GuiCreateWorld's constructor translates vanilla captions, but plain JUnit
		// has no Minecraft language manager. Allocate only the type shell needed by
		// the event handler's instanceof guard without bootstrapping the client.
		Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
		Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		Object unsafe = unsafeField.get(null);
		Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
		return (GuiCreateWorld) allocateInstance.invoke(unsafe, GuiCreateWorld.class);
	}

	public static final class StaticSubscriber {
		@SubscribeEvent
		public static void onProbe(ProbeEvent event) {
			calls++;
		}
	}

	public static final class ProbeEvent extends Event {
		public ProbeEvent() {
		}
	}
}
