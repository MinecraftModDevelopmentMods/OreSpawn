package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

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
