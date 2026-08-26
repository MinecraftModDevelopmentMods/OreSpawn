package zone.moddev.mc.orespawn.clientprobe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraftforge.client.event.RenderLevelLastEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import zone.moddev.mc.orespawn.client.OreSpawnWorldSettingsScreen;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;

/** Build-only isolated client probe. It is compiled and packaged outside every release artifact. */
@Mod(ClientProbeTestMod.MODID)
@Mod.EventBusSubscriber(modid = ClientProbeTestMod.MODID, value = Dist.CLIENT)
public final class ClientProbeTestMod {
	static final String MODID = "clientprobe";
	private static final String WORLD_DIRECTORY = "client-smoke-world";
	private static volatile ClientProbeTestMod instance;
	private final Set<String> editorRoutes = new HashSet<>();
	private final Set<String> attemptedButtons = new HashSet<>();
	private AbstractWidget worldSettingsButton;
	private int state;
	private int stateTicks;
	private int firstWorldFrames;
	private int reloadWorldFrames;
	private int editorFrames;
	private boolean worldSettingsOpened;
	private boolean longEditorRoundTrip;
	private List<GuiEventListener> worldCreationButtons;

	public ClientProbeTestMod() {
		instance = this;
	}

	@SubscribeEvent
	public static void onScreenInitialized(ScreenEvent.InitScreenEvent.Post event) {
		ClientProbeTestMod probe = instance;
		if (probe == null || !Boolean.getBoolean("clientprobe.enabled")) return;
		if (!(event.getScreen() instanceof CreateWorldScreen)) return;
		probe.worldCreationButtons = event.getListenersList();
		for (GuiEventListener child : event.getListenersList()) {
			if (child instanceof AbstractWidget && isWorldSettingsButton((AbstractWidget) child)) {
				probe.worldSettingsButton = (AbstractWidget) child;
			}
		}
	}

	@SubscribeEvent
	public static void onScreenDrawn(ScreenEvent.DrawScreenEvent.Post event) {
		ClientProbeTestMod probe = instance;
		if (probe != null && Boolean.getBoolean("clientprobe.enabled")
				&& isOreSpawnEditor(event.getScreen())) probe.editorFrames++;
	}

	@SubscribeEvent
	public static void onWorldRendered(RenderLevelLastEvent event) {
		ClientProbeTestMod probe = instance;
		if (probe == null || !Boolean.getBoolean("clientprobe.enabled")) return;
		if (probe.state == 6) probe.firstWorldFrames++;
		if (probe.state == 8) probe.reloadWorldFrames++;
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		ClientProbeTestMod probe = instance;
		if (probe == null || event.phase != TickEvent.Phase.END
				|| !Boolean.getBoolean("clientprobe.enabled")) return;
		probe.handleClientTick();
	}

	private void handleClientTick() {
		Minecraft minecraft = Minecraft.getInstance();
		if (++stateTicks > 3600) fail(minecraft, "Timed out in client probe state " + state);
		try {
			switch (state) {
				case 0:
					if (minecraft.screen instanceof TitleScreen) {
						minecraft.setScreen(CreateWorldScreen.createFresh(minecraft.screen));
						nextState(1);
					}
					break;
				case 1:
					if (worldSettingsButton == null && minecraft.screen instanceof CreateWorldScreen) {
						for (AbstractWidget candidate : widgets(minecraft.screen)) {
							if (isWorldSettingsButton(candidate)) worldSettingsButton = candidate;
						}
					}
					if (worldSettingsButton == null && worldCreationButtons != null) {
						for (GuiEventListener child : worldCreationButtons) {
							if (child instanceof AbstractWidget
									&& isWorldSettingsButton((AbstractWidget) child)) {
								worldSettingsButton = (AbstractWidget) child;
							}
						}
					}
					if (minecraft.screen instanceof CreateWorldScreen && worldSettingsButton != null) {
						// Forge 40 invokes the target-native OreSpawn button callback directly.
						((Button) worldSettingsButton).onPress();
						nextState(2);
					}
					break;
				case 2:
					if (minecraft.screen instanceof OreSpawnWorldSettingsScreen && editorFrames >= 2) {
						worldSettingsOpened = true;
						validateCaptions((OreSpawnWorldSettingsScreen) minecraft.screen);
						validateLongEditorRoundTrip(minecraft, minecraft.screen);
						nextState(3);
					}
					break;
				case 3:
					if (minecraft.screen instanceof OreSpawnWorldSettingsScreen) {
						OreSpawnWorldSettingsScreen root = (OreSpawnWorldSettingsScreen) minecraft.screen;
						Button target = nextNavigationButton(root);
						if (target == null) {
							if (editorRoutes.size() < 5) fail(minecraft,
									"Only exercised " + editorRoutes.size() + " editor routes: " + editorRoutes);
							((Screen) root).onClose();
							nextState(5);
						} else {
							Screen before = minecraft.screen;
							target.onPress();
							if (minecraft.screen != before && isOreSpawnEditor(minecraft.screen)) {
								editorRoutes.add(minecraft.screen.getClass().getSimpleName());
								editorFrames = 0;
								nextState(4);
							}
						}
					}
					break;
				case 4:
					if (isOreSpawnEditor(minecraft.screen) && editorFrames >= 2) {
						validateCaptions(minecraft.screen);
						minecraft.screen.onClose();
						nextState(3);
					}
					break;
				case 5:
					if (minecraft.screen instanceof CreateWorldScreen) {
						createWorld(minecraft);
						nextState(6);
					}
					break;
				case 6:
					if (minecraft.level != null && minecraft.player != null && firstWorldFrames >= 8
							&& stateTicks >= 100) {
						stopIntegratedServer(minecraft);
						nextState(7);
					}
					break;
				case 7:
					if (minecraft.level == null && !minecraft.hasSingleplayerServer() && stateTicks >= 20) {
						minecraft.loadLevel(WORLD_DIRECTORY);
						nextState(8);
					}
					break;
				case 8:
					if (minecraft.level != null && minecraft.player != null && reloadWorldFrames >= 8
							&& stateTicks >= 100) {
						stopIntegratedServer(minecraft);
						nextState(9);
					}
					break;
				case 9:
					if (minecraft.level == null && !minecraft.hasSingleplayerServer()) {
						writeMarker();
						minecraft.stop();
						nextState(10);
					}
					break;
				default:
					break;
			}
		} catch (RuntimeException | IOException failure) {
			fail(minecraft, failure.toString());
		}
	}

	private Button nextNavigationButton(OreSpawnWorldSettingsScreen root) {
		for (AbstractWidget widget : widgets(root)) {
			if (!(widget instanceof Button) || widget instanceof CycleButton) continue;
			Button button = (Button) widget;
			String caption = ChatFormatting.stripFormatting(button.getMessage().getString());
			if (!attemptedButtons.add(caption)) continue;
			String lower = caption.toLowerCase(java.util.Locale.ROOT);
			if (lower.equals("done") || lower.equals("cancel") || lower.contains("recommended")) continue;
			return button;
		}
		return null;
	}

	private static void validateCaptions(Screen screen) {
		for (AbstractWidget widget : widgets(screen)) {
			String caption = ChatFormatting.stripFormatting(widget.getMessage().getString());
			if (caption == null || caption.trim().isEmpty()
					|| caption.contains("options.generic_value")
					|| caption.startsWith("button.orespawn.")
					|| caption.startsWith("option.orespawn.")) {
				throw new IllegalStateException("Invalid client caption: " + widget.getMessage());
			}
		}
	}

	private void validateLongEditorRoundTrip(Minecraft minecraft, Screen parent) {
		JsonObject root = WorldGeologyProfile.recommended(true).rootCopy();
		JsonObject ores = new JsonObject();
		JsonObject ore = new JsonObject();
		ore.addProperty("enabled", true);
		ore.addProperty("block", "minecraft:diamond_ore");
		JsonObject oreDimensions = new JsonObject();
		JsonObject oreRule = new JsonObject();
		oreRule.addProperty("enabled", true);
		oreRule.addProperty("min_y", 0);
		oreRule.addProperty("max_y", 64);
		oreRule.addProperty("frequency", 1.0D);
		oreRule.addProperty("quantity", 8);
		oreRule.addProperty("discard_chance_on_air_exposure", 0.0D);
		oreRule.addProperty("pattern", "vein");
		oreRule.addProperty("height_distribution", "uniform");
		oreRule.addProperty("spread", 8);
		oreRule.addProperty("vertical_spread", 4);
		oreRule.addProperty("node_size", 4);
		oreRule.add("host_families", new JsonArray());
		oreRule.add("host_blocks", values(
				"example:ore_host_block_identifier_longer_than_thirty_two_characters"));
		oreRule.add("host_tags", values(
				"forge:ore_host_tag_identifier_longer_than_thirty_two_characters",
				"forge:second_ore_host_tag_in_the_same_comma_separated_list"));
		oreDimensions.add("minecraft:overworld", oreRule);
		ore.add("dimensions", oreDimensions);
		ores.add("example:long_editor_ore", ore);
		root.add("ores", ores);

		JsonObject deposits = new JsonObject();
		JsonObject deposit = new JsonObject();
		deposit.addProperty("enabled", true);
		deposit.addProperty("block", "minecraft:water");
		JsonObject fluidDimensions = new JsonObject();
		JsonObject fluidRule = new JsonObject();
		fluidRule.addProperty("enabled", true);
		fluidRule.addProperty("min_y", 0);
		fluidRule.addProperty("max_y", 48);
		fluidRule.addProperty("frequency", 0.08D);
		fluidRule.addProperty("min_radius", 5);
		fluidRule.addProperty("max_radius", 12);
		fluidRule.addProperty("min_vertical_radius", 2);
		fluidRule.addProperty("max_vertical_radius", 5);
		fluidRule.addProperty("max_lobes", 4);
		fluidRule.addProperty("min_solid_cover", 2);
		fluidRule.addProperty("min_solid_shell", 1);
		fluidRule.add("host_families", new JsonArray());
		fluidRule.add("host_blocks", values(
				"example:fluid_host_block_identifier_longer_than_thirty_two_characters"));
		fluidRule.add("host_tags", values(
				"forge:fluid_host_tag_identifier_longer_than_thirty_two_characters",
				"forge:second_fluid_host_tag_in_the_same_comma_separated_list"));
		fluidRule.add("biome_ids", values(
				"example:included_biome_identifier_longer_than_thirty_two_characters"));
		fluidRule.add("excluded_biome_ids", values(
				"example:excluded_biome_identifier_longer_than_thirty_two_characters"));
		fluidRule.add("biome_dictionary", values(
				"INCLUDED_DICTIONARY_VALUE_LONGER_THAN_THIRTY_TWO_CHARACTERS",
				"SECOND_INCLUDED_DICTIONARY_VALUE_IN_THE_COMMA_LIST"));
		fluidRule.add("excluded_biome_dictionary", values(
				"EXCLUDED_DICTIONARY_VALUE_LONGER_THAN_THIRTY_TWO_CHARACTERS"));
		fluidRule.add("geomes", new JsonObject());
		fluidDimensions.add("minecraft:overworld", fluidRule);
		deposit.add("dimensions", fluidDimensions);
		deposits.add("example:long_editor_deposit", deposit);
		root.add("fluid_deposits", deposits);
		// Keep the synthetic profile in the editor's canonical shape so this
		// assertion is about preservation of the eight long text fields rather
		// than the session adding an unrelated optional empty section.
		root.add("geomes", new JsonObject());

		Object session = newEditorSession(root);
		String before = editorSessionRoot(session);

		Screen oreScreen = newDimensionScreen("OreDimensionScreen", parent, session,
				"example:long_editor_ore", "minecraft:overworld");
		initializeScreen(oreScreen, minecraft);
		pressDone(oreScreen);

		Screen fluidScreen = newDimensionScreen("FluidDepositDimensionScreen", parent, session,
				"example:long_editor_deposit", "minecraft:overworld");
		initializeScreen(fluidScreen, minecraft);
		pressDone(fluidScreen);

		String after = editorSessionRoot(session);
		if (!before.equals(after)) {
			throw new IllegalStateException("Opening and saving long editor values changed profile JSON\nBefore: "
					+ before + "\nAfter: " + after);
		}
		longEditorRoundTrip = true;
	}

	private static Object newEditorSession(JsonObject root) {
		try {
			Class<?> sessionClass = Class.forName(
					"zone.moddev.mc.orespawn.client.GeologyEditorSession");
			java.lang.reflect.Constructor<?> constructor = sessionClass.getDeclaredConstructor(
					WorldGeologyProfile.class);
			constructor.setAccessible(true);
			return constructor.newInstance(WorldGeologyProfile.recommended(true).withRoot(root));
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not create the target-native editor session", failure);
		}
	}

	private static Screen newDimensionScreen(String simpleName, Screen parent, Object session,
			String ruleId, String dimensionId) {
		try {
			Class<?> sessionClass = session.getClass();
			Class<?> screenClass = Class.forName(
					"zone.moddev.mc.orespawn.client." + simpleName);
			java.lang.reflect.Constructor<?> constructor = screenClass.getDeclaredConstructor(
					Screen.class, sessionClass, String.class, String.class);
			constructor.setAccessible(true);
			return (Screen) constructor.newInstance(parent, session, ruleId, dimensionId);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not create target-native editor " + simpleName, failure);
		}
	}

	private static String editorSessionRoot(Object session) {
		try {
			Method root = session.getClass().getDeclaredMethod("root");
			root.setAccessible(true);
			return root.invoke(session).toString();
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not read the target-native editor session", failure);
		}
	}

	private static JsonArray values(String... entries) {
		JsonArray result = new JsonArray();
		for (String entry : entries) result.add(new JsonPrimitive(entry));
		return result;
	}

	private static void initializeScreen(Screen screen, Minecraft minecraft) {
		for (Method method : Screen.class.getDeclaredMethods()) {
			Class<?>[] parameters = method.getParameterTypes();
			if (parameters.length != 3 || parameters[0] != Minecraft.class
					|| parameters[1] != int.class || parameters[2] != int.class
					|| method.getReturnType() != void.class) continue;
			try {
				method.setAccessible(true);
				method.invoke(screen, minecraft, 640, 480);
				return;
			} catch (ReflectiveOperationException failure) {
				throw new IllegalStateException("Could not initialize target-native editor", failure);
			}
		}
		throw new IllegalStateException("Could not locate Forge 40 Screen initialization method");
	}

	private static void pressDone(Screen screen) {
		for (AbstractWidget widget : widgets(screen)) {
			if (!(widget instanceof Button)) continue;
			String caption = ChatFormatting.stripFormatting(((Button) widget).getMessage().getString());
			if ("done".equalsIgnoreCase(caption)) {
				((Button) widget).onPress();
				return;
			}
		}
		throw new IllegalStateException("Editor did not expose its Done action: "
				+ screen.getClass().getSimpleName());
	}

	private static boolean isOreSpawnEditor(Screen screen) {
		return screen != null && screen.getClass().getName().startsWith(
				"zone.moddev.mc.orespawn.client.");
	}

	private static boolean isWorldSettingsButton(AbstractWidget widget) {
		return widget instanceof Button
				&& ChatFormatting.stripFormatting(widget.getMessage().getString())
						.toLowerCase(java.util.Locale.ROOT).contains("orespawn");
	}

	private static void createWorld(Minecraft minecraft) {
		RegistryAccess.Frozen registries = RegistryAccess.BUILTIN.get();
		WorldGenSettings generator = WorldGenSettings.makeDefault(registries)
				.withSeed(false, OptionalLong.of(0L));
		LevelSettings settings = new LevelSettings("OreSpawn Client Smoke", GameType.CREATIVE,
				false, Difficulty.NORMAL, true, new GameRules(), DataPackConfig.DEFAULT);
		minecraft.createLevel(WORLD_DIRECTORY, settings, registries, generator);
	}

	private static java.util.List<AbstractWidget> widgets(Screen screen) {
		java.util.List<AbstractWidget> result = new java.util.ArrayList<>();
		for (GuiEventListener child : screen.children()) {
			if (child instanceof AbstractWidget) result.add((AbstractWidget) child);
		}
		return result;
	}

	private static void stopIntegratedServer(Minecraft minecraft) {
		// Match Forge 40's target-native disconnect path. clearLevel(Screen) clears
		// the integrated-server state as well as the client world; loadWorld(null) only
		// swaps the client world on this target and would leave reload stuck.
		if (minecraft.level != null) minecraft.level.disconnect();
		minecraft.clearLevel(new TitleScreen());
	}

	private void writeMarker() throws IOException {
		Properties values = new Properties();
		values.setProperty("world_settings_opened", Boolean.toString(worldSettingsOpened));
		values.setProperty("long_editor_roundtrip", Boolean.toString(longEditorRoundTrip));
		values.setProperty("editor_routes", Integer.toString(editorRoutes.size()));
		values.setProperty("editor_classes", editorRoutes.toString());
		values.setProperty("first_world_rendered", Boolean.toString(firstWorldFrames >= 8));
		values.setProperty("reload_rendered", Boolean.toString(reloadWorldFrames >= 8));
		values.setProperty("world_directory", WORLD_DIRECTORY);
		try (FileOutputStream output = new FileOutputStream(new File("client-smoke-pass.properties"))) {
			values.store(output, "OreSpawn Forge 1.18.2 client integration gate");
		}
	}

	private void nextState(int next) {
		state = next;
		stateTicks = 0;
	}

	private static void fail(Minecraft minecraft, String message) {
		try {
			Properties values = new Properties(); values.setProperty("failure", message);
			try (FileOutputStream output = new FileOutputStream(new File("client-smoke-failure.properties"))) {
				values.store(output, "OreSpawn client probe failure");
			}
		} catch (IOException ignored) {
		}
		minecraft.stop();
		throw new IllegalStateException(message);
	}
}
