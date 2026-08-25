package zone.moddev.mc.orespawn.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.CreateWorldScreen;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.TickEvent;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;

/** Build-only client probe. It is compiled and packaged outside every release artifact. */
@Mod(ClientProbeTestMod.MODID)
@Mod.EventBusSubscriber(modid = ClientProbeTestMod.MODID, value = Dist.CLIENT)
public final class ClientProbeTestMod {
	static final String MODID = "clientprobe";
	private static final String WORLD_DIRECTORY = "client-smoke-world";
	private static volatile ClientProbeTestMod instance;
	private final Set<String> editorRoutes = new HashSet<>();
	private final Set<String> attemptedButtons = new HashSet<>();
	private Widget worldSettingsButton;
	private int state;
	private int stateTicks;
	private int firstWorldFrames;
	private int reloadWorldFrames;
	private int editorFrames;
	private boolean worldSettingsOpened;
	private boolean longEditorRoundTrip;
	private List<Widget> worldCreationButtons;

	public ClientProbeTestMod() {
		instance = this;
	}

	@SubscribeEvent
	public static void onScreenInitialized(GuiScreenEvent.InitGuiEvent.Post event) {
		ClientProbeTestMod probe = instance;
		if (probe == null || !Boolean.getBoolean("clientprobe.enabled")) return;
		if (!(event.getGui() instanceof CreateWorldScreen)) return;
		probe.worldCreationButtons = event.getWidgetList();
		for (Widget button : event.getWidgetList()) {
			if (button instanceof Button) probe.worldSettingsButton = button;
		}
	}

	@SubscribeEvent
	public static void onScreenDrawn(GuiScreenEvent.DrawScreenEvent.Post event) {
		ClientProbeTestMod probe = instance;
		if (probe != null && Boolean.getBoolean("clientprobe.enabled")
				&& event.getGui() instanceof OreSpawnScreen) probe.editorFrames++;
	}

	@SubscribeEvent
	public static void onWorldRendered(RenderWorldLastEvent event) {
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
					if (minecraft.currentScreen instanceof MainMenuScreen) {
						minecraft.displayGuiScreen(new CreateWorldScreen(minecraft.currentScreen));
						nextState(1);
					}
					break;
				case 1:
					if (worldSettingsButton == null && worldCreationButtons != null) {
						for (Widget candidate : worldCreationButtons) {
							if (candidate instanceof Button) worldSettingsButton = candidate;
						}
					}
					if (minecraft.currentScreen instanceof CreateWorldScreen && worldSettingsButton != null) {
						// Forge 28 invokes the target-native OreSpawn button callback directly.
						((Button) worldSettingsButton).onPress();
						nextState(2);
					}
					break;
				case 2:
					if (minecraft.currentScreen instanceof OreSpawnWorldSettingsScreen && editorFrames >= 2) {
						worldSettingsOpened = true;
						validateCaptions((OreSpawnWorldSettingsScreen) minecraft.currentScreen);
						validateLongEditorRoundTrip(minecraft, minecraft.currentScreen);
						nextState(3);
					}
					break;
				case 3:
					if (minecraft.currentScreen instanceof OreSpawnWorldSettingsScreen) {
						OreSpawnWorldSettingsScreen root = (OreSpawnWorldSettingsScreen) minecraft.currentScreen;
						Button target = nextNavigationButton(root);
						if (target == null) {
							if (editorRoutes.size() < 5) fail(minecraft,
									"Only exercised " + editorRoutes.size() + " editor routes: " + editorRoutes);
							root.onClose();
							nextState(5);
						} else {
							Screen before = minecraft.currentScreen;
							target.onPress();
							if (minecraft.currentScreen != before && minecraft.currentScreen instanceof OreSpawnScreen) {
								editorRoutes.add(minecraft.currentScreen.getClass().getSimpleName());
								editorFrames = 0;
								nextState(4);
							}
						}
					}
					break;
				case 4:
					if (minecraft.currentScreen instanceof OreSpawnScreen && editorFrames >= 2) {
						validateCaptions((OreSpawnScreen) minecraft.currentScreen);
						((OreSpawnScreen) minecraft.currentScreen).onClose();
						nextState(3);
					}
					break;
				case 5:
					if (minecraft.currentScreen instanceof CreateWorldScreen) {
						minecraft.launchIntegratedServer(WORLD_DIRECTORY, "OreSpawn Client Smoke",
								new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.DEFAULT));
						nextState(6);
					}
					break;
				case 6:
					if (minecraft.world != null && minecraft.player != null && firstWorldFrames >= 8
							&& stateTicks >= 100) {
						stopIntegratedServer(minecraft);
						nextState(7);
					}
					break;
				case 7:
					if (minecraft.world == null && !minecraft.isIntegratedServerRunning() && stateTicks >= 20) {
						minecraft.launchIntegratedServer(WORLD_DIRECTORY, "OreSpawn Client Smoke",
								new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.DEFAULT));
						nextState(8);
					}
					break;
				case 8:
					if (minecraft.world != null && minecraft.player != null && reloadWorldFrames >= 8
							&& stateTicks >= 100) {
						stopIntegratedServer(minecraft);
						nextState(9);
					}
					break;
				case 9:
					if (minecraft.world == null && !minecraft.isIntegratedServerRunning()) {
						writeMarker();
						minecraft.shutdown();
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
		for (Widget widget : root.qualificationButtons()) {
			if (!(widget instanceof Button) || widget instanceof CycleButton) continue;
			Button button = (Button) widget;
			String caption = TextFormatting.getTextWithoutFormattingCodes(button.getMessage());
			if (!attemptedButtons.add(caption)) continue;
			String lower = caption.toLowerCase(java.util.Locale.ROOT);
			if (lower.equals("done") || lower.equals("cancel") || lower.contains("recommended")) continue;
			return button;
		}
		return null;
	}

	private static void validateCaptions(OreSpawnScreen screen) {
		for (Widget widget : screen.qualificationButtons()) {
			String caption = TextFormatting.getTextWithoutFormattingCodes(widget.getMessage());
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

		GeologyEditorSession session = new GeologyEditorSession(
				WorldGeologyProfile.recommended(true).withRoot(root));
		String before = session.root().toString();

		OreDimensionScreen oreScreen = new OreDimensionScreen(parent, session,
				"example:long_editor_ore", "minecraft:overworld");
		oreScreen.init(minecraft, 640, 480);
		pressDone(oreScreen);

		FluidDepositDimensionScreen fluidScreen = new FluidDepositDimensionScreen(parent, session,
				"example:long_editor_deposit", "minecraft:overworld");
		fluidScreen.init(minecraft, 640, 480);
		pressDone(fluidScreen);

		String after = session.root().toString();
		if (!before.equals(after)) {
			throw new IllegalStateException("Opening and saving long editor values changed profile JSON\nBefore: "
					+ before + "\nAfter: " + after);
		}
		longEditorRoundTrip = true;
	}

	private static JsonArray values(String... entries) {
		JsonArray result = new JsonArray();
		for (String entry : entries) result.add(new JsonPrimitive(entry));
		return result;
	}

	private static void pressDone(OreSpawnScreen screen) {
		for (Widget widget : screen.qualificationButtons()) {
			if (!(widget instanceof Button)) continue;
			String caption = TextFormatting.getTextWithoutFormattingCodes(((Button) widget).getMessage());
			if ("done".equalsIgnoreCase(caption)) {
				((Button) widget).onPress();
				return;
			}
		}
		throw new IllegalStateException("Editor did not expose its Done action: "
				+ screen.getClass().getSimpleName());
	}

	private static void stopIntegratedServer(Minecraft minecraft) {
		// Match Forge 28's target-native disconnect path. func_213231_b clears the
		// integrated-server state as well as the client world; loadWorld(null) only
		// swaps the client world on this target and would leave reload stuck.
		if (minecraft.world != null) minecraft.world.sendQuittingDisconnectingPacket();
		minecraft.func_213231_b(new MainMenuScreen());
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
			values.store(output, "OreSpawn Forge 1.14.4 client integration gate");
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
		minecraft.shutdown();
		throw new IllegalStateException(message);
	}
}
