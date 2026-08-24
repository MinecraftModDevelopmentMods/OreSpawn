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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;

/** Build-only client probe. It is compiled and packaged outside every release artifact. */
@Mod(modid = ClientProbeTestMod.MODID, name = "OreSpawn Client Probe", version = "1",
		acceptedMinecraftVersions = "[1.10.2]", dependencies = "required-after:orespawn")
public final class ClientProbeTestMod {
	static final String MODID = "clientprobe";
	private static final String WORLD_DIRECTORY = "client-smoke-world";
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

	@Mod.EventHandler
	public void initialize(FMLInitializationEvent event) {
		if (!Boolean.getBoolean("clientprobe.enabled")) return;
		MinecraftForge.EVENT_BUS.register(this);
	}

	@SubscribeEvent
	public void onScreenInitialized(GuiScreenEvent.InitGuiEvent.Post event) {
		if (!(event.getGui() instanceof GuiCreateWorld)) return;
		for (GuiButton button : event.getButtonList()) {
			if (button.id == 0x4F53) worldSettingsButton = button;
		}
	}

	@SubscribeEvent
	public void onScreenDrawn(GuiScreenEvent.DrawScreenEvent.Post event) {
		if (event.getGui() instanceof OreSpawnScreen) editorFrames++;
	}

	@SubscribeEvent
	public void onWorldRendered(RenderWorldLastEvent event) {
		if (state == 6) firstWorldFrames++;
		if (state == 8) reloadWorldFrames++;
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !Boolean.getBoolean("clientprobe.enabled")) return;
		Minecraft minecraft = Minecraft.getMinecraft();
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
					if (minecraft.currentScreen instanceof GuiCreateWorld && worldSettingsButton != null) {
						GuiScreenEvent.ActionPerformedEvent.Pre press =
								new GuiScreenEvent.ActionPerformedEvent.Pre(minecraft.currentScreen,
										worldSettingsButton, java.util.Collections.singletonList(worldSettingsButton));
						if (!MinecraftForge.EVENT_BUS.post(press) || !press.isCanceled()) {
							fail(minecraft, "OreSpawn world-settings action was not canceled");
						}
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
							GuiScreen before = minecraft.currentScreen;
							target.press();
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
		for (GuiButton widget : root.buttons) {
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
		for (GuiButton widget : screen.buttons) {
			String caption = TextFormatting.getTextWithoutFormattingCodes(widget.displayString);
			if (caption == null || caption.trim().isEmpty()
					|| caption.contains("options.generic_value")
					|| caption.startsWith("button.orespawn.")
					|| caption.startsWith("option.orespawn.")) {
				throw new IllegalStateException("Invalid client caption: " + widget.displayString);
			}
		}
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
		// Keep the synthetic profile in the editor's canonical shape so this
		// assertion is about preservation of the eight long text fields rather
		// than the session adding an unrelated optional empty section.
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
		for (GuiButton widget : screen.buttons) {
			if (!(widget instanceof Button)) continue;
			String caption = TextFormatting.getTextWithoutFormattingCodes(((Button) widget).getMessage());
			if ("done".equalsIgnoreCase(caption)) {
				((Button) widget).press();
				return;
			}
		}
		throw new IllegalStateException("Editor did not expose its Done action: "
				+ screen.getClass().getSimpleName());
	}

	private static void stopIntegratedServer(Minecraft minecraft) {
		// Match GuiIngameMenu's target-native disconnect path. loadWorld(null)
		// coordinates the integrated-server save/stop; installing the replacement
		// screen in the same tick prevents EntityRenderer from seeing no world and
		// no screen between frames.
		if (minecraft.world != null) minecraft.world.sendQuittingDisconnectingPacket();
		minecraft.loadWorld(null);
		minecraft.displayGuiScreen(new GuiMainMenu());
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
			values.store(output, "OreSpawn Forge 1.10.2 client integration gate");
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
