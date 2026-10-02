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
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

/** Client integration test mod, compiled separately and excluded from every release jar. */
@Mod(ClientProbeTestMod.MODID)
@Mod.EventBusSubscriber(modid = ClientProbeTestMod.MODID, value = Dist.CLIENT)
public final class ClientProbeTestMod {
	static final String MODID = "clientprobe";
	private static final String WORLD_DIRECTORY = "client-smoke-world";
	private static volatile ClientProbeTestMod instance;
	private final Set<String> editorRoutes = new HashSet<>();
	private final Set<String> attemptedButtons = new HashSet<>();
	private GuiButton worldSettingsButton;
	private int state;
	private int stateTicks;
	private int firstWorldFrames;
	private int reloadWorldFrames;
	private int editorFrames;
	private boolean worldSettingsOpened;
	private boolean longEditorRoundTrip;
	private boolean oreSourceFixturesVerified;
	private List<GuiButton> worldCreationButtons;

	public ClientProbeTestMod() {
		instance = this;
	}

	@SubscribeEvent
	public static void onScreenInitialized(GuiScreenEvent.InitGuiEvent.Post event) {
		ClientProbeTestMod probe = instance;
		if (probe == null || !Boolean.getBoolean("clientprobe.enabled")) return;
		if (!(event.getGui() instanceof GuiCreateWorld)) return;
		probe.worldCreationButtons = event.getButtonList();
		for (GuiButton button : event.getButtonList()) {
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
					if (minecraft.currentScreen instanceof GuiMainMenu) {
						minecraft.displayGuiScreen(new GuiCreateWorld(minecraft.currentScreen));
						nextState(1);
					}
					break;
				case 1:
					if (worldSettingsButton == null && worldCreationButtons != null) {
						for (GuiButton candidate : worldCreationButtons) {
							if (candidate instanceof Button) worldSettingsButton = candidate;
						}
					}
					if (minecraft.currentScreen instanceof GuiCreateWorld && worldSettingsButton != null) {
						// Forge 25 invokes the OreSpawn button directly.
						// No vanilla Create World action needs cancelling.
						((GuiButton) worldSettingsButton).onClick(0, 0);
						nextState(2);
					}
					break;
				case 2:
					if (minecraft.currentScreen instanceof OreSpawnWorldSettingsScreen && editorFrames >= 2) {
						worldSettingsOpened = true;
						validateCaptions((OreSpawnWorldSettingsScreen) minecraft.currentScreen);
						validateLongEditorRoundTrip(minecraft, minecraft.currentScreen);
						if (Boolean.getBoolean("clientprobe.oreSourcesFixtures")) {
							validateOreSourceFixtures(minecraft, minecraft.currentScreen);
							oreSourceFixturesVerified = true;
						}
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
							GuiScreen before = minecraft.currentScreen;
							((GuiButton) target).onClick(0, 0);
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
						OreSpawnScreen editor = (OreSpawnScreen) minecraft.currentScreen;
						validateCaptions(editor);
						if (editor instanceof GeologyMaterialsScreen) validateOreSourcesRoute(minecraft, editor);
						editor.onClose();
						nextState(3);
					}
					break;
				case 5:
					if (minecraft.currentScreen instanceof GuiCreateWorld) {
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
		for (GuiButton widget : root.qualificationButtons()) {
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
		for (GuiButton widget : screen.qualificationButtons()) {
			if (widget instanceof CogButton || widget instanceof CompactScrollList) continue;
			String caption = TextFormatting.getTextWithoutFormattingCodes(widget.displayString);
			if (caption == null || caption.trim().isEmpty()
					|| caption.contains("options.generic_value")
					|| caption.startsWith("button.orespawn.")
					|| caption.startsWith("option.orespawn.")) {
				throw new IllegalStateException("Invalid client caption on "
						+ screen.getClass().getSimpleName() + '/' + widget.getClass().getSimpleName()
						+ ": " + widget.displayString);
			}
		}
	}

	private static void validateOreSourcesRoute(Minecraft minecraft, OreSpawnScreen materials) {
		Button ores = buttonWithCaption(materials,
				net.minecraft.client.resources.I18n.format("tab.orespawn.ores"));
		if (ores == null) throw new IllegalStateException("Missing ORES tab");
		((GuiButton) ores).onClick(0, 0);
		Button sources = buttonWithCaption(materials,
				net.minecraft.client.resources.I18n.format("button.orespawn.ore_sources"));
		if (sources == null) {
			throw new IllegalStateException("Missing Ore Sources route from ORES");
		}
		((GuiButton) sources).onClick(0, 0);
		if (!(minecraft.currentScreen instanceof OreSourceListScreen)) {
			throw new IllegalStateException("Ore Sources route did not open");
		}
		OreSourceListScreen opened = (OreSourceListScreen) minecraft.currentScreen;
		validateEmptyOreSources(minecraft, materials);
		Button addGroup = buttonWithCaption(opened, "+");
		if (addGroup == null) throw new IllegalStateException("Missing Add Group control");
		((GuiButton) addGroup).onClick(0, 0);
		int lists = 0;
		for (GuiButton widget : opened.qualificationButtons()) {
			if (widget instanceof CompactScrollList) lists++;
		}
		if (lists != 2) throw new IllegalStateException("Ore Sources must show both compact lists: " + lists);
		opened.onClose();
		if (minecraft.currentScreen != materials) {
			throw new IllegalStateException("Ore Sources did not return to ORES");
		}
	}

	private static void validateEmptyOreSources(Minecraft minecraft, GuiScreen parent) {
		for (int[] size : new int[][] { { 426, 265 }, { 320, 240 } }) {
			OreSourceListScreen empty = new OreSourceListScreen(parent,
					new GeologyEditorSession(WorldGeologyProfile.recommended(false)));
			((GuiScreen) empty).setWorldAndResolution(minecraft, size[0], size[1]);
			CompactScrollList groups = null;
			for (GuiButton widget : empty.qualificationButtons()) {
				if (widget instanceof CompactScrollList) {
					groups = (CompactScrollList) widget;
					break;
				}
			}
			if (groups == null) throw new IllegalStateException("Missing empty Groups pane");
			int textWidth = groups.contentRight() - ((GuiButton) groups).x - 12;
			List<String> lines = minecraft.fontRenderer.listFormattedStringToWidth(
					net.minecraft.client.resources.I18n.format("label.orespawn.ore_source.none"), textWidth);
			if (lines.size() < 2) throw new IllegalStateException("Empty message must wrap at " + size[0]);
			for (String line : lines) {
				if (minecraft.fontRenderer.getStringWidth(line) > textWidth) {
					throw new IllegalStateException("Empty message crosses the Groups scrollbar");
				}
			}
			if (10 + lines.size() * 11 > ((GuiButton) groups).height) {
				throw new IllegalStateException("Empty message crosses the Groups footer");
			}
			((GuiScreen) empty).render(0, 0, 0.0F);
		}
	}

	private static void validateOreSourceFixtures(Minecraft minecraft, GuiScreen parent) {
		GeologyEditorSession session = new GeologyEditorSession(
				WorldGeologyProfileManager.pendingNewWorldProfile());
		GeologyEditorSession.OreSourceGroup sulfur = null;
		for (GeologyEditorSession.OreSourceGroup group : session.oreSourceGroups()) {
			if ("orespawn:sulfur|minecraft:overworld".equals(group.key)) sulfur = group;
		}
		if (sulfur == null || sulfur.outputCandidates().size() != 3 || sulfur.outputs.size() != 3
				|| sulfur.channels().size() != 1
				|| !"baseminerals:sulfur".equals(sulfur.placements.get("orespawn:standard"))) {
			throw new IllegalStateException("Dummy providers did not create the expected Sulfur group");
		}
		for (int[] size : new int[][] { { 426, 265 }, { 320, 240 } }) {
			session.restoreOreSourceOriginalMode(sulfur.key);
			OreSourceListScreen screen = new OreSourceListScreen(parent, session);
			((GuiScreen) screen).setWorldAndResolution(minecraft, size[0], size[1]);
			String currentMode = "keep_original";
			for (String nextMode : new String[] { "balanced", "single", "custom", "keep_original" }) {
				String caption = net.minecraft.client.resources.I18n.format(
						"option.orespawn.ore_source.output_mode",
						net.minecraft.client.resources.I18n.format("mode.orespawn.ore_source." + currentMode));
				Button mode = buttonWithCaption(screen, caption);
				if (mode == null) throw new IllegalStateException("Missing dummy-provider mode control");
				((GuiButton) mode).onClick(0, 0);
				int lists = 0;
				for (GuiButton widget : screen.qualificationButtons()) {
					if (widget instanceof CompactScrollList) {
						CompactScrollList list = (CompactScrollList) widget;
						if (list.size() != (lists == 0 ? 1 : 3)) {
							throw new IllegalStateException("Wrong dummy-provider row count at " + size[0]);
						}
						if (lists == 1 && "keep_original".equals(nextMode) && list.rowEnabled(0)) {
							throw new IllegalStateException("Keep Original must disable output editing");
						}
						lists++;
					}
				}
				if (lists != 2) throw new IllegalStateException("Missing dummy-provider pane");
				currentMode = nextMode;
				((GuiScreen) screen).render(0, 0, 0.0F);
			}
		}
	}

	private static Button buttonWithCaption(OreSpawnScreen screen, String caption) {
		for (GuiButton widget : screen.qualificationButtons()) {
			if (widget instanceof Button && caption.equals(
					TextFormatting.getTextWithoutFormattingCodes(widget.displayString))) {
				return (Button) widget;
			}
		}
		return null;
	}

	private void validateLongEditorRoundTrip(Minecraft minecraft, GuiScreen parent) {
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
		// Use a complete profile so this checks the eight long text fields,
		// not the editor adding an optional empty section.
		root.add("geomes", new JsonObject());

		GeologyEditorSession session = new GeologyEditorSession(
				WorldGeologyProfile.recommended(true).withRoot(root));
		String before = session.root().toString();

		OreDimensionScreen oreScreen = new OreDimensionScreen(parent, session,
				"example:long_editor_ore", "minecraft:overworld");
		((GuiScreen) oreScreen).setWorldAndResolution(minecraft, 640, 480);
		pressDone(oreScreen);

		FluidDepositDimensionScreen fluidScreen = new FluidDepositDimensionScreen(parent, session,
				"example:long_editor_deposit", "minecraft:overworld");
		((GuiScreen) fluidScreen).setWorldAndResolution(minecraft, 640, 480);
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
		for (GuiButton widget : screen.qualificationButtons()) {
			if (!(widget instanceof Button)) continue;
			String caption = TextFormatting.getTextWithoutFormattingCodes(((Button) widget).getMessage());
			if ("done".equalsIgnoreCase(caption)) {
				((GuiButton) widget).onClick(0, 0);
				return;
			}
		}
		throw new IllegalStateException("Editor did not expose its Done action: "
				+ screen.getClass().getSimpleName());
	}

	private static void stopIntegratedServer(Minecraft minecraft) {
		// Follow GuiIngameMenu's disconnect path: loadWorld(null) saves and stops the server.
		// Open the replacement screen in the same tick so EntityRenderer never has neither a world nor a screen.
		if (minecraft.world != null) minecraft.world.sendQuittingDisconnectingPacket();
		minecraft.loadWorld(null);
		minecraft.displayGuiScreen(new GuiMainMenu());
	}

	private void writeMarker() throws IOException {
		Properties values = new Properties();
		values.setProperty("world_settings_opened", Boolean.toString(worldSettingsOpened));
		values.setProperty("long_editor_roundtrip", Boolean.toString(longEditorRoundTrip));
		values.setProperty("ore_source_fixtures_verified", Boolean.toString(oreSourceFixturesVerified));
		values.setProperty("editor_routes", Integer.toString(editorRoutes.size()));
		values.setProperty("editor_classes", editorRoutes.toString());
		values.setProperty("first_world_rendered", Boolean.toString(firstWorldFrames >= 8));
		values.setProperty("reload_rendered", Boolean.toString(reloadWorldFrames >= 8));
		values.setProperty("world_directory", WORLD_DIRECTORY);
		try (FileOutputStream output = new FileOutputStream(new File("client-smoke-pass.properties"))) {
			values.store(output, "OreSpawn Forge 1.13.2 client integration gate");
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
