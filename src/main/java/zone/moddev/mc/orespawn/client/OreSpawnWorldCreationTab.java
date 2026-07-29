package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.google.gson.JsonObject;
import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;
import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager;
import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager.TemplateDefinition;
import zone.moddev.mc.orespawn.worldgen.FormationSettings.Preset;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class OreSpawnWorldCreationTab extends GridLayoutTab {
	private static final int BUTTON_HEIGHT = 20;
	private static final int COLUMN_WIDTH = 190;
	private static final int FULL_WIDTH = (COLUMN_WIDTH * 2) + 5;

	private final CreateWorldScreen worldScreen;
	private final GeologyEditorSession session;
	private final List<TemplateChoice> templateChoices;
	private final List<AbstractWidget> formationControls = new ArrayList<>();
	private final List<AbstractWidget> compactControls = new ArrayList<>();
	private final List<AbstractWidget> terrainControls = new ArrayList<>();
	private final List<CycleButton<Boolean>> vanillaOresButtons = new ArrayList<>();
	private final List<Button> fluidEditorButtons = new ArrayList<>();

	private GeologyMode geologyMode;
	private Preset horizontalSize;
	private Preset verticalThickness;
	private Preset waviness;
	private Preset edgeIrregularity;
	private Preset formationContinuity;
	private boolean placeFluidDeposits;
	private boolean manageVanillaOres;
	private ResourceLocation selectedTemplate;

	OreSpawnWorldCreationTab(CreateWorldScreen worldScreen, WorldGeologyProfile profile,
			List<String> availableDimensions) {
		super(Component.literal("OreSpawn"));
		this.worldScreen = worldScreen;
		this.session = new GeologyEditorSession(profile, availableDimensions);
		this.templateChoices = availableTemplates(profile);
		setProfile(profile);
		buildControls();
	}

	private void buildControls() {
		layout.rowSpacing(4).columnSpacing(5);
		int row = 0;
		if (templateChoices.size() > 1) {
			TemplateChoice initialTemplate = templateChoice(selectedTemplate);
			addFull(CycleButton.builder(TemplateChoice::label)
					.withValues(templateChoices)
					.withInitialValue(initialTemplate)
					.withTooltip(value -> tooltip("guide.orespawn.world.1"))
					.create(0, 0, FULL_WIDTH, BUTTON_HEIGHT,
							Component.translatable("option.orespawn.template"),
							(button, value) -> selectTemplate(value.id)), row++);
		}
		addFull(button(Component.translatable("button.orespawn.recommended"), FULL_WIDTH,
				this::resetRecommended, "guide.orespawn.welcome.3"), row++);
		buildStandaloneControls(row);
		buildTerrainControls(row);
		refreshControlState();
	}

	private void buildStandaloneControls(int firstRow) {
		add(vanillaOresButton(), firstRow, 0, compactControls);
		add(button(Component.translatable("button.orespawn.materials"), COLUMN_WIDTH,
				this::openMaterials, "guide.orespawn.rocks.2"), firstRow, 1, compactControls);
		addFull(button(Component.translatable("button.orespawn.configure_strata"), FULL_WIDTH,
				this::configureRockStrata, "guide.orespawn.rocks.3"), firstRow + 1, compactControls);
		addFull(fluidEditorButton(FULL_WIDTH), firstRow + 2, compactControls);
		addFull(button(Component.translatable("button.orespawn.biomes_world_materials"),
				FULL_WIDTH, this::openBiomeWorldMaterials, "guide.orespawn.biomes.1"),
				firstRow + 3, compactControls);
		addFull(button(Component.translatable("button.orespawn.help"), FULL_WIDTH,
				this::openHelp, "guide.orespawn.welcome.1"), firstRow + 4, compactControls);
	}

	private void buildTerrainControls(int firstRow) {
		CycleButton<GeologyMode> geologyButton = CycleButton.builder(this::geologyModeName)
				.withValues(Arrays.asList(GeologyMode.GEOME, GeologyMode.LEGACY))
				.withInitialValue(geologyMode)
				.withTooltip(value -> tooltip("tooltip.orespawn.geology_mode"))
				.create(0, 0, COLUMN_WIDTH, BUTTON_HEIGHT,
						Component.translatable("option.orespawn.geology_mode"),
						(button, value) -> {
							geologyMode = value;
							commit();
							updateFormationControls();
						});
		add(geologyButton, firstRow, 0, terrainControls);
		add(vanillaOresButton(), firstRow, 1, terrainControls);

		add(presetButton("option.orespawn.horizontal_size",
				"tooltip.orespawn.horizontal_size", horizontalSize,
				value -> horizontalSize = value), firstRow + 1, 0, terrainControls);
		add(presetButton("option.orespawn.vertical_thickness",
				"tooltip.orespawn.vertical_thickness", verticalThickness,
				value -> verticalThickness = value), firstRow + 1, 1, terrainControls);
		add(presetButton("option.orespawn.waviness",
				"tooltip.orespawn.waviness", waviness, value -> waviness = value),
				firstRow + 2, 0, terrainControls);
		add(presetButton("option.orespawn.edge_irregularity",
				"tooltip.orespawn.edge_irregularity", edgeIrregularity,
				value -> edgeIrregularity = value), firstRow + 2, 1, terrainControls);
		add(presetButton("option.orespawn.formation_continuity",
				"tooltip.orespawn.formation_continuity", formationContinuity,
				value -> formationContinuity = value), firstRow + 3, 0, terrainControls);
		add(fluidEditorButton(COLUMN_WIDTH), firstRow + 3, 1, terrainControls);

		add(button(Component.translatable("button.orespawn.manage_strata"), COLUMN_WIDTH,
				this::openMaterials, "tooltip.orespawn.manage_strata"), firstRow + 4, 0, terrainControls);
		add(button(Component.translatable("button.orespawn.biomes_world_materials"),
				COLUMN_WIDTH, this::openBiomeWorldMaterials, "guide.orespawn.biomes.1"),
				firstRow + 4, 1, terrainControls);
		add(button(Component.translatable("button.orespawn.advanced"), COLUMN_WIDTH,
				this::openAdvanced, "guide.orespawn.world.3"), firstRow + 5, 0, terrainControls);
		add(button(Component.translatable("button.orespawn.help"), COLUMN_WIDTH,
				this::openHelp, "guide.orespawn.welcome.1"), firstRow + 5, 1, terrainControls);
	}

	private CycleButton<Boolean> vanillaOresButton() {
		CycleButton<Boolean> button = CycleButton.onOffBuilder(manageVanillaOres)
				.withTooltip(value -> tooltip("tooltip.orespawn.manage_vanilla_ores"))
				.create(0, 0, COLUMN_WIDTH, BUTTON_HEIGHT,
						Component.translatable("option.orespawn.manage_vanilla_ores"),
						(widget, value) -> {
							manageVanillaOres = value;
							commit();
						});
		vanillaOresButtons.add(button);
		return button;
	}

	private Button fluidEditorButton(int width) {
		Button button = button(fluidEditorLabel(), width, this::openFluidDeposits,
				"guide.orespawn.fluids.1");
		fluidEditorButtons.add(button);
		return button;
	}

	private CycleButton<Preset> presetButton(String labelKey, String tooltipKey,
			Preset initialValue, PresetConsumer consumer) {
		CycleButton<Preset> button = CycleButton.builder(this::presetName)
				.withValues(Arrays.asList(Preset.values()))
				.withInitialValue(initialValue)
				.withTooltip(value -> tooltip(tooltipKey))
				.create(0, 0, COLUMN_WIDTH, BUTTON_HEIGHT, Component.translatable(labelKey),
						(widget, value) -> {
							consumer.accept(value);
							commit();
						});
		formationControls.add(button);
		return button;
	}

	private Button button(Component label, int width, Runnable action) {
		Component fitted = OreSpawnScreenLayout.fit(Minecraft.getInstance().font, label, width - 8);
		Button button = Button.builder(fitted, selected -> action.run()).width(width).build();
		if (!fitted.getString().equals(label.getString())) {
			button.setTooltip(Tooltip.create(label));
		}
		return button;
	}

	private Button button(Component label, int width, Runnable action, String tooltipKey) {
		Button button = button(label, width, action);
		button.setTooltip(tooltip(tooltipKey));
		return button;
	}

	private void add(AbstractWidget control, int row, int column, List<AbstractWidget> group) {
		layout.addChild(control, row, column);
		group.add(control);
	}

	private void addFull(AbstractWidget control, int row) {
		layout.addChild(control, row, 0, 1, 2);
	}

	private void addFull(AbstractWidget control, int row, List<AbstractWidget> group) {
		addFull(control, row);
		group.add(control);
	}

	private void refreshControlState() {
		setProfile(session.profile());
		boolean terrain = session.hasTerrainRules();
		setVisible(compactControls, !terrain);
		setVisible(terrainControls, terrain);
		for (CycleButton<Boolean> button : vanillaOresButtons) {
			button.setValue(manageVanillaOres);
		}
		for (Button button : fluidEditorButtons) {
			Component label = fluidEditorLabel();
			button.setMessage(OreSpawnScreenLayout.fit(
					Minecraft.getInstance().font, label, button.getWidth() - 8));
			button.setTooltip(tooltip("guide.orespawn.fluids.1"));
		}
		updateFormationControls();
	}

	private static void setVisible(List<AbstractWidget> controls, boolean visible) {
		for (AbstractWidget control : controls) {
			control.visible = visible;
		}
	}

	private void updateFormationControls() {
		boolean enabled = geologyMode == GeologyMode.GEOME;
		for (AbstractWidget control : formationControls) {
			control.active = enabled;
		}
	}

	private void resetRecommended() {
		WorldGeologyProfile recommended = session.profile().withSelection(GeologyMode.GEOME,
				Preset.AVERAGE, Preset.AVERAGE, Preset.AVERAGE, Preset.AVERAGE,
				Preset.AVERAGE, placeFluidDeposits);
		session.applyProfile(recommended);
		setProfile(recommended);
		commit();
		refreshControlState();
	}

	private void selectTemplate(ResourceLocation templateId) {
		if (Objects.equals(selectedTemplate, templateId)) {
			return;
		}
		commit();
		WorldGeologyProfile profile = templateId == null
				? session.profile().withoutTemplate()
				: session.profile().withTemplate(templateId);
		session.applyProfile(profile);
		setProfile(profile);
		commit();
		refreshControlState();
	}

	private void setProfile(WorldGeologyProfile profile) {
		geologyMode = profile.geologyMode();
		horizontalSize = profile.horizontalSize();
		verticalThickness = profile.verticalThickness();
		waviness = profile.waviness();
		edgeIrregularity = profile.edgeIrregularity();
		formationContinuity = profile.formationContinuity();
		placeFluidDeposits = profile.placeFluidDeposits();
		manageVanillaOres = profile.manageVanillaOres();
		selectedTemplate = profile.selectedTemplate().orElse(null);
	}

	private void commit() {
		WorldGeologyProfile selected = session.profile().withSelection(
				geologyMode, horizontalSize, verticalThickness, waviness,
				edgeIrregularity, formationContinuity, placeFluidDeposits);
		JsonObject root = selected.rootCopy();
		root.addProperty("manage_vanilla_ores", manageVanillaOres);
		session.applyProfile(selected.withRoot(root));
		WorldGeologyProfileManager.setPendingNewWorldProfile(session.profile());
	}

	private void configureRockStrata() {
		commit();
		session.configureDefaultVanillaStrata();
		commit();
		refreshControlState();
		openMaterials();
	}

	private void openMaterials() {
		commit();
		Minecraft.getInstance().setScreen(new GeologyMaterialsScreen(returnScreen(), session));
	}

	private void openBiomeWorldMaterials() {
		commit();
		Minecraft.getInstance().setScreen(new BiomeWorldMaterialsScreen(returnScreen(), session));
	}

	private void openAdvanced() {
		commit();
		Minecraft.getInstance().setScreen(new AdvancedGeologySettingsScreen(returnScreen(), session));
	}

	private void openFluidDeposits() {
		commit();
		Minecraft.getInstance().setScreen(new FluidDepositListScreen(returnScreen(), session));
	}

	private void openHelp() {
		Minecraft.getInstance().setScreen(new OreSpawnGuideScreen(returnScreen()));
	}

	private Screen returnScreen() {
		return new ReturnToWorldCreationScreen(worldScreen, () -> {
			refreshControlState();
			commit();
		});
	}

	private Component geologyModeName(GeologyMode mode) {
		return Component.translatable("value.orespawn.geology_mode."
				+ mode.name().toLowerCase(Locale.ROOT));
	}

	private Component presetName(Preset preset) {
		return Component.translatable("value.orespawn.preset." + preset.configName());
	}

	private Component fluidEditorLabel() {
		if (session.fluidDepositIds().isEmpty()) {
			return Component.translatable("button.orespawn.fluid_deposit_details");
		}
		return Component.translatable("button.orespawn.fluid_deposits_count",
				session.enabledFluidDepositCount(), session.fluidDepositIds().size());
	}

	private Tooltip tooltip(String key) {
		return Tooltip.create(Component.translatable(key));
	}

	private TemplateChoice templateChoice(ResourceLocation id) {
		for (TemplateChoice choice : templateChoices) {
			if (Objects.equals(choice.id, id)) {
				return choice;
			}
		}
		return templateChoices.get(0);
	}

	private static List<TemplateChoice> availableTemplates(WorldGeologyProfile profile) {
		List<TemplateChoice> result = new ArrayList<>();
		result.add(new TemplateChoice(null,
				Component.translatable("value.orespawn.template.none")));
		for (TemplateDefinition template : WorldgenIntegrationManager.templates()) {
			if (template.available()) {
				result.add(new TemplateChoice(template.id(),
						Component.translatable(template.nameKey())));
			}
		}
		ResourceLocation selected = profile.selectedTemplate().orElse(null);
		if (selected != null && result.stream().noneMatch(choice -> selected.equals(choice.id))) {
			result.add(new TemplateChoice(selected, Component.literal(selected.toString())));
		}
		return result;
	}

	@FunctionalInterface
	private interface PresetConsumer {
		void accept(Preset preset);
	}

	private static final class TemplateChoice {
		private final ResourceLocation id;
		private final Component label;

		private TemplateChoice(ResourceLocation id, Component label) {
			this.id = id;
			this.label = label;
		}

		private Component label() {
			return label;
		}
	}

	private static final class ReturnToWorldCreationScreen extends Screen {
		private final Screen destination;
		private final Runnable beforeReturn;

		private ReturnToWorldCreationScreen(Screen destination, Runnable beforeReturn) {
			super(Component.empty());
			this.destination = destination;
			this.beforeReturn = beforeReturn;
		}

		@Override
		protected void init() {
			beforeReturn.run();
			minecraft.execute(() -> minecraft.setScreen(destination));
		}
	}
}
