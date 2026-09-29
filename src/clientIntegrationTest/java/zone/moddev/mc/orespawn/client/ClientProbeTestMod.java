package zone.moddev.mc.orespawn.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
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
import net.minecraft.client.resources.I18n;
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
import zone.moddev.mc.orespawn.util.JsonCopies;
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
	private boolean modsDirectoryRendered;
	private boolean directoryStackValidated =
			System.getProperty("clientprobe.expectedMods", "").trim().isEmpty();
	private boolean oreSourcesValidated =
			System.getProperty("clientprobe.expectedMods", "").trim().isEmpty();
	private boolean oreSourcesLayoutValidated =
			System.getProperty("clientprobe.expectedMods", "").trim().isEmpty();
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
		if (event.getGui() instanceof OreSpawnModsScreen) modsDirectoryRendered = true;
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
						validateExpectedDirectory(minecraft.currentScreen);
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
					if (stateTicks >= 20) {
						if (minecraft.world != null || minecraft.isIntegratedServerRunning()) {
							minecraft.loadWorld(null);
						}
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
					if (stateTicks >= 20) {
						if (minecraft.world != null || minecraft.isIntegratedServerRunning()) {
							minecraft.loadWorld(null);
						}
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

	private void validateExpectedDirectory(GuiScreen root) {
		String configured = System.getProperty("clientprobe.expectedMods", "").trim();
		if (configured.isEmpty()) return;
		List<String> expected = Arrays.asList(configured.split(","));
		List<OreSpawnModDirectoryModel.Entry> entries = OreSpawnModDirectoryModel.snapshot();
		List<String> actual = new ArrayList<>();
		for (OreSpawnModDirectoryModel.Entry entry : entries) actual.add(entry.modId);
		if (!expected.equals(actual)) {
			throw new IllegalStateException("Unexpected OreSpawn Mods directory order: expected "
					+ expected + " but found " + actual);
		}
		for (OreSpawnModDirectoryModel.Entry entry : entries) {
			if (entry.version == null || entry.version.trim().isEmpty() || "?".equals(entry.version)) {
				throw new IllegalStateException("Missing Forge mod version for " + entry.modId);
			}
			if ("basemetals".equals(entry.modId)) {
				if (entry.nativeOs4() || entry.legacyLineages().isEmpty()) {
					throw new IllegalStateException("Base Metals did not retain its legacy lineage");
				}
			} else if ("mineralogy".equals(entry.modId)) {
				if (!entry.nativeOs4() || entry.schemaVersion() != 4 || entry.providerRevision() != 3) {
					throw new IllegalStateException("Missing Mineralogy OS4 schema 4/provider revision 3: schema="
							+ entry.schemaVersion() + ", revision=" + entry.providerRevision());
				}
			} else if (!entry.nativeOs4() || entry.schemaVersion() != 5
					|| entry.providerRevision() < 1) {
				throw new IllegalStateException("Missing native OS4 schema 5/revision for " + entry.modId
						+ ": schema=" + entry.schemaVersion() + ", revision=" + entry.providerRevision());
			}
			boolean shouldConfigure = "realisticdeposits".equals(entry.modId);
			if (entry.configurable() != shouldConfigure) {
				throw new IllegalStateException("Unexpected cog availability for " + entry.modId);
			}
			if (shouldConfigure) {
				OreSpawnModsScreen directory = new OreSpawnModsScreen(root, entries);
				GuiScreen child = entry.extension.createScreen(directory);
				if (child == null || !child.getClass().getName().endsWith("RealisticDepositsConfigScreen")) {
					throw new IllegalStateException("Realistic Deposits did not create its config screen");
				}
				try {
					Minecraft minecraft = Minecraft.getMinecraft();
					WorldSettingsExtensionNavigation.open(directory, child);
					Method escape = GuiScreen.class.getDeclaredMethod("keyTyped", char.class, int.class);
					escape.setAccessible(true);
					escape.invoke(child, '\0', org.lwjgl.input.Keyboard.KEY_ESCAPE);
					if (minecraft.currentScreen != directory) {
						throw new IllegalStateException("Add-on Escape did not return to the Mods directory");
					}
					minecraft.displayGuiScreen(root);
				} catch (ReflectiveOperationException failure) {
					throw new IllegalStateException("Could not exercise add-on Escape", failure);
				}
			}
		}
		if (expected.contains("mineralogy") && expected.contains("electricadvantage")) {
			validateExpectedOreSources((OreSpawnWorldSettingsScreen) root);
		}
		directoryStackValidated = true;
	}

	private void validateExpectedOreSources(OreSpawnWorldSettingsScreen root) {
		GeologyEditorSession session = editorSession(root);
		GeologyEditorSession.OreSourceGroup sulfur = null;
		for (GeologyEditorSession.OreSourceGroup group : session.oreSourceGroups()) {
			if ("orespawn:sulfur".equals(group.material)
					&& "minecraft:overworld".equals(group.domain)) sulfur = group;
		}
		if (sulfur == null) throw new IllegalStateException("Missing curated Sulfur material group");
		if (!"Sulfur".equals(sulfur.displayName)
				|| !sulfur.oreDictionaryEntries.contains("oreSulfur")
				|| !sulfur.oreDictionaryEntries.contains("oreSulphur")) {
			throw new IllegalStateException("Sulfur aliases were not grouped correctly: "
					+ sulfur.oreDictionaryEntries);
		}
		if (!"consolidated".equals(sulfur.mode) || !"balanced".equals(sulfur.outputMode)) {
			throw new IllegalStateException("Fresh Sulfur policy was not Balanced: mode="
					+ sulfur.mode + ", outputMode=" + sulfur.outputMode);
		}
		if (sulfur.needsAttention() || OreSourceListScreen.groupRowColor(sulfur) != 0xFFFF55) {
			throw new IllegalStateException("Resolved Sulfur policy was not classified yellow: status="
					+ sulfur.status + ", attention=" + sulfur.needsAttention());
		}
		boolean mineralogy = false;
		boolean electricOutputOnly = false;
		for (GeologyEditorSession.OreSourceCandidate candidate : sulfur.outputCandidates()) {
			if ("mineralogy:sulfur_ore".equals(candidate.registryId)) {
				mineralogy = candidate.loaded && candidate.active && !candidate.external;
			}
			if ("electricadvantage:sulfur_ore".equals(candidate.registryId)) {
				electricOutputOnly = candidate.loaded && !candidate.active && !candidate.external;
			}
			if (("mineralogy:sulfur_ore".equals(candidate.registryId)
					|| "electricadvantage:sulfur_ore".equals(candidate.registryId))
					&& !sulfur.outputs.containsKey(candidate.sourceId)) {
				throw new IllegalStateException("Balanced Sulfur output is not selected: "
						+ candidate.sourceId);
			}
		}
		if (!mineralogy || !electricOutputOnly) {
			throw new IllegalStateException("Sulfur outputs were not classified correctly: mineralogy="
					+ mineralogy + ", electricOutputOnly=" + electricOutputOnly);
		}
		validateOreSourcesSingleScreen(root, session);
		oreSourcesValidated = true;
	}

	private void validateOreSourcesSingleScreen(GuiScreen parent, GeologyEditorSession session) {
		Minecraft minecraft = Minecraft.getMinecraft();
		OreSourceListScreen screen = new OreSourceListScreen(parent, session);
		screen.setWorldAndResolution(minecraft, 426, 265);
		int contentWidth = OreSourceListScreen.contentWidth(426);
		int left = (426 - contentWidth) / 2;
		int right = left + OreSourceListScreen.leftPaneWidth(426) + 6;
		boolean groupList = false;
		boolean outputList = false;
		boolean inlineAdministration = false;
		boolean back = false;
		boolean showAll = false;
		int showAllWidth = 0;
		for (GuiButton widget : screen.buttons) {
			String caption = TextFormatting.getTextWithoutFormattingCodes(widget.displayString);
			if (widget instanceof CompactScrollList && widget.xPosition < right) groupList = true;
			if (widget instanceof CompactScrollList && widget.xPosition >= right) outputList = true;
			if (widget instanceof TextFieldWidget && widget.xPosition < right) inlineAdministration = true;
			if ("Back".equals(caption)) back = true;
			if (I18n.format("button.orespawn.show_all").equals(caption)) {
				showAll = true;
				showAllWidth = widget.width;
			}
		}
		if (!groupList || !outputList || inlineAdministration || back || !showAll
				|| showAllWidth >= OreSourceListScreen.leftPaneWidth(426) / 2) {
			throw new IllegalStateException("Ore Sources was not one compact two-pane screen: groups="
					+ groupList + ", outputs=" + outputList + ", inlineAdministration="
					+ inlineAdministration + ", back=" + back + ", showAll=" + showAll
					+ ", showAllWidth=" + showAllWidth
					+ ", left=" + left + ", right=" + right);
		}

		GeologyEditorSession.OreSourceGroup sulfurGroup = null;
		for (GeologyEditorSession.OreSourceGroup group : session.oreSourceGroups()) {
			if ("orespawn:sulfur".equals(group.material)) sulfurGroup = group;
		}
		if (sulfurGroup == null) throw new IllegalStateException("Sulfur group disappeared before settings test");
		OreSourceGroupSettingsScreen settings = new OreSourceGroupSettingsScreen(
				screen, session, sulfurGroup.key);
		settings.setWorldAndResolution(minecraft, 426, 265);
		int compactLists = 0;
		boolean sulfurName = false;
		int lowestListBottom = 0;
		int aliasRows = 0;
		int placementRows = 0;
		for (GuiButton widget : settings.buttons) {
			if (widget instanceof CompactScrollList) {
				compactLists++;
				lowestListBottom = Math.max(lowestListBottom, widget.yPosition + widget.height);
				if (widget.yPosition < 100) aliasRows = CompactScrollList.visibleRows(widget.height, 16);
				else placementRows = CompactScrollList.visibleRows(widget.height, 16);
			}
			if (widget instanceof TextFieldWidget
					&& "Sulfur".equals(((TextFieldWidget) widget).getValue())) sulfurName = true;
		}
		if (compactLists != 2 || !sulfurName || aliasRows < 5 || placementRows < 3
				|| lowestListBottom > 233
				|| !OreSourceGroupSettingsScreen.placementChannels(sulfurGroup)
						.contains("orespawn:standard")) {
			throw new IllegalStateException("Group Settings did not expose aliases and placement rules: lists="
					+ compactLists + ", name=" + sulfurName + ", aliasRows=" + aliasRows
					+ ", placementRows=" + placementRows + ", listBottom=" + lowestListBottom + ", channels="
					+ OreSourceGroupSettingsScreen.placementChannels(sulfurGroup));
		}
		validateKeepOriginalAcceptance(minecraft, parent, session, sulfurGroup.key);
		validateStaleExternalClassification(minecraft, parent, session, sulfurGroup.key);
		validateCustomGroupReassignmentLayout(minecraft, parent, session);
		oreSourcesLayoutValidated = true;
	}

	private void validateCustomGroupReassignmentLayout(Minecraft minecraft, GuiScreen parent,
			GeologyEditorSession source) {
		GeologyEditorSession customSession = new GeologyEditorSession(
				WorldGeologyProfile.fromJson(source.profile().rootCopy(), source.profile()));
		customSession.setManageVanillaOres(false);
		String customKey = customSession.addOreMaterialGroup();
		String material = customKey.substring(0, customKey.indexOf('|'));
		customSession.renameOreMaterialGroup(material, "Precious Stones");
		String originalOwner = customSession.oreDictionaryOwner("oreDiamond");
		if (originalOwner == null || material.equals(originalOwner)) {
			throw new IllegalStateException("Could not establish the oreDiamond reassignment control");
		}
		OreSourceGroupSettingsScreen settings = new OreSourceGroupSettingsScreen(
				parent, customSession, customKey);
		settings.setWorldAndResolution(minecraft, 426, 265);
		TextFieldWidget alias = null;
		Button add = null;
		for (GuiButton widget : settings.buttons) {
			if (widget instanceof TextFieldWidget
					&& ((TextFieldWidget) widget).getValue().isEmpty()) alias = (TextFieldWidget) widget;
			String caption = TextFormatting.getTextWithoutFormattingCodes(widget.displayString);
			if (widget instanceof Button && "+".equals(caption)) add = (Button) widget;
		}
		if (alias == null || add == null) {
			throw new IllegalStateException("Custom Group Settings did not expose alias reassignment controls");
		}
		alias.setValue("oreDiamond");
		add.press();

		Button move = null;
		Button done = null;
		for (GuiButton widget : settings.buttons) {
			String caption = TextFormatting.getTextWithoutFormattingCodes(widget.displayString);
			if (widget instanceof Button
					&& I18n.format("button.orespawn.ore_source.move").equals(caption)) move = (Button) widget;
			if (widget instanceof Button && I18n.format("gui.done").equals(caption)) done = (Button) widget;
		}
		String message;
		try {
			Field error = OreSourceGroupSettingsScreen.class.getDeclaredField("error");
			error.setAccessible(true);
			message = (String) error.get(settings);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Could not inspect Group Settings validation state", exception);
		}
		int contentWidth = 406;
		int expectedDoneX = 10 + contentWidth
				- OreSourceGroupSettingsScreen.doneButtonWidth(contentWidth);
		if (move != null || !I18n.format("error.orespawn.ore_source.manage_vanilla_first").equals(message)
				|| done == null
				|| done.xPosition != expectedDoneX || done.yPosition != 237
				|| OreSourceGroupSettingsScreen.validationMessageWidth(contentWidth) <= 0) {
			throw new IllegalStateException("Disabled vanilla management did not block reassignment: move="
					+ (move != null) + ", message=" + message + ", done="
					+ (done == null ? "missing" : done.xPosition + "," + done.yPosition));
		}
		if (!originalOwner.equals(customSession.oreDictionaryOwner("oreDiamond"))) {
			throw new IllegalStateException("Blocked vanilla alias move changed oreDiamond ownership");
		}

		customSession.setManageVanillaOres(true);
		settings = new OreSourceGroupSettingsScreen(parent, customSession, customKey);
		settings.setWorldAndResolution(minecraft, 426, 265);
		alias = null;
		add = null;
		for (GuiButton widget : settings.buttons) {
			if (widget instanceof TextFieldWidget
					&& ((TextFieldWidget) widget).getValue().isEmpty()) alias = (TextFieldWidget) widget;
			String caption = TextFormatting.getTextWithoutFormattingCodes(widget.displayString);
			if (widget instanceof Button && "+".equals(caption)) add = (Button) widget;
		}
		if (alias == null || add == null) {
			throw new IllegalStateException("Managed vanilla alias controls did not reopen");
		}
		alias.setValue("oreDiamond");
		add.press();
		move = button(settings, I18n.format("button.orespawn.ore_source.move"));
		if (move == null) {
			throw new IllegalStateException("Managed vanilla alias move did not request confirmation");
		}
		move.press();
		if (!material.equals(customSession.oreDictionaryOwner("oreDiamond"))) {
			throw new IllegalStateException("Move did not confirm oreDiamond reassignment");
		}
		if (!customSession.addOreMaterialAlias(material, "oreEmerald", true)) {
			throw new IllegalStateException("Could not construct the managed Precious Stones group");
		}
		GeologyEditorSession.OreSourceGroup customGroup = null;
		for (GeologyEditorSession.OreSourceGroup group : customSession.oreSourceGroups()) {
			if (customKey.equals(group.key)) customGroup = group;
		}
		if (customGroup == null || !customGroup.hasManagedPlacementSource()
				|| !customGroup.needsReview() || customGroup.outputCandidates().size() != 2
				|| customGroup.outputs.size() != 2
				|| OreSourceListScreen.placementCandidates(
						customGroup, "orespawn:standard").size() != 2) {
			throw new IllegalStateException("Managed Precious Stones review was not constructed");
		}
		OreSourceListScreen outputs = new OreSourceListScreen(parent, customSession);
		outputs.setWorldAndResolution(minecraft, 426, 265);
		Button mode = button(outputs, I18n.format("option.orespawn.ore_source.output_mode",
				I18n.format("mode.orespawn.ore_source.keep_original")));
		Button accept = button(outputs, I18n.format("button.orespawn.ore_source.accept"));
		if (mode == null || accept == null) {
			throw new IllegalStateException("Managed vanilla review did not retain Keep Original and Accept");
		}
		OreSourceListScreen minimum = new OreSourceListScreen(parent, customSession);
		minimum.setWorldAndResolution(minecraft, 320, 240);
		if (button(minimum, I18n.format("button.orespawn.ore_source.accept")) == null) {
			throw new IllegalStateException("Accept was not visible at the minimum supported resolution");
		}
		mode.press();
		GeologyEditorSession.OreSourceGroup after = null;
		for (GeologyEditorSession.OreSourceGroup group : customSession.oreSourceGroups()) {
			if (customKey.equals(group.key)) after = group;
		}
		String outputError;
		try {
			Field field = OreSourceListScreen.class.getDeclaredField("error");
			field.setAccessible(true);
			outputError = (String) field.get(outputs);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Could not inspect Ore Sources validation state", exception);
		}
		if (after == null || !"consolidated".equals(after.mode) || after.needsAttention()
				|| after.outputs.size() != 2 || after.placements.size() != 1
				|| OreSourceListScreen.placementCandidates(
						after, "orespawn:standard").size() != 2
				|| outputError != null
				|| button(outputs, I18n.format("button.orespawn.ore_source.accept")) != null) {
			throw new IllegalStateException("Managed vanilla group did not enter Balanced mode: error="
					+ outputError + ", mode=" + (after == null ? "missing" : after.mode));
		}

		OreSourceGroupSettingsScreen dissolve = new OreSourceGroupSettingsScreen(
				parent, customSession, customKey);
		dissolve.setWorldAndResolution(minecraft, 426, 265);
		Button dissolveButton = button(dissolve,
				I18n.format("button.orespawn.ore_source.dissolve_group"));
		if (dissolveButton == null) {
			throw new IllegalStateException("Populated custom group did not expose Dissolve Group");
		}
		dissolveButton.press();
		Button confirm = button(dissolve,
				I18n.format("button.orespawn.ore_source.confirm_dissolve"));
		if (confirm == null || !material.equals(customSession.oreDictionaryOwner("oreDiamond"))) {
			throw new IllegalStateException("Dissolve Group did not require a second confirmation");
		}
		confirm.press();
		if (!"orespawn:diamond".equals(customSession.oreDictionaryOwner("oreDiamond"))
				|| !"orespawn:emerald".equals(customSession.oreDictionaryOwner("oreEmerald"))) {
			throw new IllegalStateException("Confirmed dissolution did not restore inferred groups");
		}
	}

	private static Button button(OreSpawnScreen screen, String caption) {
		for (GuiButton widget : screen.buttons) {
			String text = TextFormatting.getTextWithoutFormattingCodes(widget.displayString);
			if (widget instanceof Button && caption.equals(text)) return (Button) widget;
		}
		return null;
	}

	private void validateKeepOriginalAcceptance(Minecraft minecraft, GuiScreen parent,
			GeologyEditorSession source, String key) {
		JsonObject root = source.profile().rootCopy();
		JsonObject policy = root.getAsJsonObject("ore_source_policies").getAsJsonObject(key);
		policy.addProperty("mode", "keep_separate");
		policy.addProperty("review_required", true);
		policy.addProperty("status", "review_required");
		JsonObject onlyPolicy = new JsonObject();
		onlyPolicy.add(key, policy);
		root.add("ore_source_policies", onlyPolicy);
		GeologyEditorSession reviewSession = new GeologyEditorSession(
				WorldGeologyProfile.fromJson(root, source.profile()));
		GeologyEditorSession.OreSourceGroup before = null;
		for (GeologyEditorSession.OreSourceGroup group : reviewSession.oreSourceGroups()) {
			if (key.equals(group.key)) before = group;
		}
		if (before == null || !before.needsReview()) {
			throw new IllegalStateException("Could not construct pending Keep Original review");
		}
		OreSourceListScreen review = new OreSourceListScreen(parent, reviewSession);
		review.setWorldAndResolution(minecraft, 426, 265);
		Button accept = null;
		for (GuiButton widget : review.buttons) {
			String caption = TextFormatting.getTextWithoutFormattingCodes(widget.displayString);
			if (widget instanceof Button
					&& I18n.format("button.orespawn.ore_source.accept").equals(caption)) {
				accept = (Button) widget;
			}
		}
		if (accept == null) throw new IllegalStateException("Pending Keep Original did not show Accept");
		accept.press();
		GeologyEditorSession.OreSourceGroup after = null;
		for (GeologyEditorSession.OreSourceGroup group : reviewSession.oreSourceGroups()) {
			if (key.equals(group.key)) after = group;
		}
		if (after == null || after.needsReview() || after.needsAttention()
				|| !before.mode.equals(after.mode) || !before.outputs.equals(after.outputs)
				|| !before.placements.equals(after.placements)) {
			throw new IllegalStateException("Accept changed Keep Original instead of only clearing review");
		}
	}

	private void validateStaleExternalClassification(Minecraft minecraft, GuiScreen parent,
			GeologyEditorSession source, String key) {
		JsonObject root = source.profile().rootCopy();
		JsonObject policy = root.getAsJsonObject("ore_source_policies").getAsJsonObject(key);
		JsonObject managed = null;
		for (com.google.gson.JsonElement element : policy.getAsJsonArray("candidates")) {
			JsonObject candidate = element.getAsJsonObject();
			if ("electricadvantage:sulfur_ore".equals(candidate.get("registry_id").getAsString())
					&& !candidate.get("external").getAsBoolean()) managed = candidate;
		}
		if (managed == null) throw new IllegalStateException("Missing managed Electric Advantage output");
		JsonObject external = JsonCopies.copy(managed);
		external.addProperty("source_id", "external/electricadvantage:sulfur_ore/0");
		external.addProperty("loaded", false);
		external.addProperty("placement_active", false);
		external.addProperty("external", true);
		policy.getAsJsonArray("candidates").add(external);
		policy.addProperty("mode", "keep_separate");
		policy.addProperty("output_mode", "single");
		policy.addProperty("review_required", false);
		policy.addProperty("status", "external_generation");
		JsonObject onlyPolicy = new JsonObject();
		onlyPolicy.add(key, policy);
		root.add("ore_source_policies", onlyPolicy);
		GeologyEditorSession staleSession = new GeologyEditorSession(
				WorldGeologyProfile.fromJson(root, source.profile()));
		GeologyEditorSession.OreSourceGroup group = staleSession.oreSourceGroups().get(0);
		if (group.needsAttention() || !"separate".equals(group.status)
				|| OreSourceListScreen.groupRowColor(group) != 0xFFFF55) {
			throw new IllegalStateException("Stale missing external duplicate remained red: status="
					+ group.status + ", attention=" + group.needsAttention());
		}
		OreSourceListScreen screen = new OreSourceListScreen(parent, staleSession);
		screen.setWorldAndResolution(minecraft, 426, 265);
		for (GuiButton widget : screen.buttons) {
			String caption = TextFormatting.getTextWithoutFormattingCodes(widget.displayString);
			if (I18n.format("button.orespawn.ore_source.accept").equals(caption)) {
				throw new IllegalStateException("Resolved stale external duplicate still requested acceptance");
			}
		}
	}

	private static GeologyEditorSession editorSession(OreSpawnWorldSettingsScreen root) {
		try {
			Field field = OreSpawnWorldSettingsScreen.class.getDeclaredField("session");
			field.setAccessible(true);
			return (GeologyEditorSession) field.get(root);
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not inspect the active Ore Sources editor session", failure);
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
			if (widget instanceof CogButton || widget instanceof CompactScrollList) continue;
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
		// Ask the integrated server to stop while retaining the client world until
		// the server and its queued play packets have drained. Clearing the world in
		// this tick races Forge 1.10 packet tasks against a null client world.
		if (minecraft.world != null) minecraft.world.sendQuittingDisconnectingPacket();
		minecraft.displayGuiScreen(new GuiMainMenu());
	}

	private void writeMarker() throws IOException {
		Properties values = new Properties();
		values.setProperty("world_settings_opened", Boolean.toString(worldSettingsOpened));
		values.setProperty("mods_directory_rendered", Boolean.toString(modsDirectoryRendered));
		values.setProperty("directory_stack_validated", Boolean.toString(directoryStackValidated));
		values.setProperty("ore_sources_validated", Boolean.toString(oreSourcesValidated));
		values.setProperty("ore_sources_layout_validated", Boolean.toString(oreSourcesLayoutValidated));
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
