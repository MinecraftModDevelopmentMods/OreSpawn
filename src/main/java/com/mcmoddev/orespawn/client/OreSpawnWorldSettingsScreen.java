package com.mcmoddev.orespawn.client;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.mcmoddev.orespawn.OreSpawnConfig;
import com.mcmoddev.orespawn.OreSpawnConfig.GeologyMode;
import com.mcmoddev.orespawn.worldgen.FormationSettings.Preset;
import com.mcmoddev.orespawn.worldgen.WorldGeologyProfile;
import com.mcmoddev.orespawn.worldgen.WorldGeologyProfileManager;
import com.mcmoddev.orespawn.integration.WorldgenIntegrationManager;
import com.mcmoddev.orespawn.integration.WorldgenIntegrationManager.TemplateDefinition;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public final class OreSpawnWorldSettingsScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;

	private final Screen parent;
	private final GeologyEditorSession session;
	private GeologyMode geologyMode;
	private Preset horizontalSize;
	private Preset verticalThickness;
	private Preset waviness;
	private Preset edgeIrregularity;
	private Preset formationContinuity;
	private boolean placeCrudeOil;
	private boolean manageVanillaOres;
	private ResourceLocation selectedTemplate;
	private final List<TemplateChoice> templateChoices;

	private CycleButton<GeologyMode> geologyModeButton;
	private CycleButton<Boolean> crudeOilButton;
	private CycleButton<Boolean> vanillaOresButton;
	private CycleButton<Preset> horizontalSizeButton;
	private CycleButton<Preset> verticalThicknessButton;
	private CycleButton<Preset> wavinessButton;
	private CycleButton<Preset> edgeIrregularityButton;
	private CycleButton<Preset> formationContinuityButton;
	private Component validationError;

	public OreSpawnWorldSettingsScreen(Screen parent, WorldGeologyProfile profile) {
		this(parent, profile, Collections.emptyList());
	}

	OreSpawnWorldSettingsScreen(Screen parent, WorldGeologyProfile profile, List<String> availableDimensions) {
		super(new TranslatableComponent("screen.orespawn.world_settings"));
		this.parent = parent;
		this.session = new GeologyEditorSession(profile, availableDimensions);
		templateChoices = availableTemplates(profile);
		setProfile(profile);
	}

	@Override
	protected void init() {
		int left = this.width / 2 - 155;
		int right = this.width / 2 + 5;
		int top = OreSpawnScreenLayout.mainTop(this.height);
		int row = OreSpawnScreenLayout.mainRowSpacing(this.height);

		TemplateChoice initialTemplate = templateChoice(selectedTemplate);
		addRenderableWidget(CycleButton.builder(TemplateChoice::label)
				.withValues(templateChoices)
				.withInitialValue(initialTemplate)
				.create(left, top, 310, BUTTON_HEIGHT,
						new TranslatableComponent("option.orespawn.template"),
						(button, value) -> selectTemplate(value.id)));
		addRenderableWidget(new Button(left, top + row, 310, BUTTON_HEIGHT,
				new TranslatableComponent("button.orespawn.recommended"), button -> resetRecommended()));

		geologyModeButton = addRenderableWidget(CycleButton
				.builder(this::geologyModeName)
				.withValues(Arrays.asList(GeologyMode.GEOME, GeologyMode.LEGACY))
				.withInitialValue(geologyMode)
				.withTooltip(value -> tooltip("tooltip.orespawn.geology_mode"))
				.create(left, top + (row * 2), BUTTON_WIDTH, BUTTON_HEIGHT,
						new TranslatableComponent("option.orespawn.geology_mode"),
						(button, value) -> {
							geologyMode = value;
							updateFormationControls();
						}));

		crudeOilButton = addRenderableWidget(CycleButton.onOffBuilder(placeCrudeOil)
				.withTooltip(value -> tooltip("tooltip.orespawn.crude_oil"))
				.create(right, top + (row * 2), BUTTON_WIDTH, BUTTON_HEIGHT,
						new TranslatableComponent("option.orespawn.crude_oil"),
						(button, value) -> placeCrudeOil = value));

		horizontalSizeButton = addPresetButton(left, top + (row * 3),
				"option.orespawn.horizontal_size", "tooltip.orespawn.horizontal_size",
				horizontalSize, value -> horizontalSize = value);
		verticalThicknessButton = addPresetButton(right, top + (row * 3),
				"option.orespawn.vertical_thickness", "tooltip.orespawn.vertical_thickness",
				verticalThickness, value -> verticalThickness = value);
		wavinessButton = addPresetButton(left, top + (row * 4),
				"option.orespawn.waviness", "tooltip.orespawn.waviness",
				waviness, value -> waviness = value);
		edgeIrregularityButton = addPresetButton(right, top + (row * 4),
				"option.orespawn.edge_irregularity", "tooltip.orespawn.edge_irregularity",
				edgeIrregularity, value -> edgeIrregularity = value);
		formationContinuityButton = addPresetButton(left, top + (row * 5),
				"option.orespawn.formation_continuity", "tooltip.orespawn.formation_continuity",
				formationContinuity, value -> formationContinuity = value);

		vanillaOresButton = addRenderableWidget(CycleButton.onOffBuilder(manageVanillaOres)
				.withTooltip(value -> tooltip("tooltip.orespawn.manage_vanilla_ores"))
				.create(right, top + (row * 5), BUTTON_WIDTH, BUTTON_HEIGHT,
						new TranslatableComponent("option.orespawn.manage_vanilla_ores"),
						(button, value) -> manageVanillaOres = value));
		addRenderableWidget(new Button(left, top + (row * 6), BUTTON_WIDTH, BUTTON_HEIGHT,
				new TranslatableComponent("button.orespawn.materials"), button -> openMaterials()));
		addRenderableWidget(new Button(right, top + (row * 6), BUTTON_WIDTH, BUTTON_HEIGHT,
				new TranslatableComponent("button.orespawn.geomes"), button -> openGeomes()));
		addRenderableWidget(new Button(left, top + (row * 7), BUTTON_WIDTH, BUTTON_HEIGHT,
				new TranslatableComponent("button.orespawn.advanced"), button -> openAdvanced()));
		addRenderableWidget(new Button(right, top + (row * 7), BUTTON_WIDTH, BUTTON_HEIGHT,
				new TranslatableComponent("button.orespawn.help"), button -> openHelp()));
		addRenderableWidget(new Button(left, this.height - 28, BUTTON_WIDTH, BUTTON_HEIGHT,
				CommonComponents.GUI_DONE, button -> saveAndClose()));
		addRenderableWidget(new Button(right, this.height - 28, BUTTON_WIDTH, BUTTON_HEIGHT,
				CommonComponents.GUI_CANCEL, button -> onClose()));
		updateFormationControls();
	}

	private CycleButton<Preset> addPresetButton(int x, int y, String labelKey, String tooltipKey,
			Preset initialValue, PresetConsumer consumer) {
		return addRenderableWidget(CycleButton.builder(this::presetName)
				.withValues(Arrays.asList(Preset.values()))
				.withInitialValue(initialValue)
				.withTooltip(value -> tooltip(tooltipKey))
				.create(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, new TranslatableComponent(labelKey),
						(button, value) -> consumer.accept(value)));
	}

	private void updateFormationControls() {
		boolean enabled = geologyMode == GeologyMode.GEOME;
		if (horizontalSizeButton != null) {
			horizontalSizeButton.active = enabled;
			verticalThicknessButton.active = enabled;
			wavinessButton.active = enabled;
			edgeIrregularityButton.active = enabled;
			formationContinuityButton.active = enabled;
		}
	}

	private void resetRecommended() {
		WorldGeologyProfile recommended = session.profile().withSelection(GeologyMode.GEOME,
				Preset.AVERAGE, Preset.AVERAGE, Preset.AVERAGE, Preset.AVERAGE, Preset.AVERAGE,
				OreSpawnConfig.placeCrudeOil());
		session.applyProfile(recommended);
		setProfile(recommended);
		geologyModeButton.setValue(geologyMode);
		crudeOilButton.setValue(placeCrudeOil);
		vanillaOresButton.setValue(manageVanillaOres);
		horizontalSizeButton.setValue(horizontalSize);
		verticalThicknessButton.setValue(verticalThickness);
		wavinessButton.setValue(waviness);
		edgeIrregularityButton.setValue(edgeIrregularity);
		formationContinuityButton.setValue(formationContinuity);
		updateFormationControls();
	}

	private void setProfile(WorldGeologyProfile profile) {
		geologyMode = profile.geologyMode();
		horizontalSize = profile.horizontalSize();
		verticalThickness = profile.verticalThickness();
		waviness = profile.waviness();
		edgeIrregularity = profile.edgeIrregularity();
		formationContinuity = profile.formationContinuity();
		placeCrudeOil = profile.placeCrudeOil();
		manageVanillaOres = profile.manageVanillaOres();
		selectedTemplate = profile.selectedTemplate().orElse(null);
	}

	private void selectTemplate(ResourceLocation templateId) {
		if (Objects.equals(selectedTemplate, templateId)) {
			return;
		}
		syncSession();
		WorldGeologyProfile profile = templateId == null
				? session.profile().withoutTemplate() : session.profile().withTemplate(templateId);
		session.applyProfile(profile);
		setProfile(profile);
		geologyModeButton.setValue(geologyMode);
		crudeOilButton.setValue(placeCrudeOil);
		vanillaOresButton.setValue(manageVanillaOres);
		horizontalSizeButton.setValue(horizontalSize);
		verticalThicknessButton.setValue(verticalThickness);
		wavinessButton.setValue(waviness);
		edgeIrregularityButton.setValue(edgeIrregularity);
		formationContinuityButton.setValue(formationContinuity);
		updateFormationControls();
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
		result.add(new TemplateChoice(null, new TranslatableComponent("value.orespawn.template.none")));
		for (TemplateDefinition template : WorldgenIntegrationManager.templates()) {
			if (template.available()) {
				result.add(new TemplateChoice(template.id(), new TranslatableComponent(template.nameKey())));
			}
		}
		ResourceLocation selected = profile.selectedTemplate().orElse(null);
		if (selected != null && result.stream().noneMatch(choice -> selected.equals(choice.id))) {
			result.add(new TemplateChoice(selected, new TextComponent(selected.toString())));
		}
		return result;
	}

	private void saveAndClose() {
		syncSession();
		List<String> errors = session.validate();
		if (!errors.isEmpty()) {
			validationError = new net.minecraft.network.chat.TextComponent(errors.get(0));
			return;
		}
		WorldGeologyProfileManager.setPendingNewWorldProfile(session.profile());
		minecraft.setScreen(parent);
	}

	private void syncSession() {
		WorldGeologyProfile selected = session.profile().withSelection(
				geologyMode, horizontalSize, verticalThickness, waviness,
				edgeIrregularity, formationContinuity, placeCrudeOil);
		com.google.gson.JsonObject root = selected.rootCopy();
		root.addProperty("manage_vanilla_ores", manageVanillaOres);
		session.applyProfile(selected.withRoot(root));
	}

	private void openMaterials() {
		syncSession();
		minecraft.setScreen(new GeologyMaterialsScreen(this, session));
	}

	private void openGeomes() {
		syncSession();
		minecraft.setScreen(new GeomeBiomeScreen(this, session));
	}

	private void openAdvanced() {
		syncSession();
		minecraft.setScreen(new AdvancedGeologySettingsScreen(this, session));
	}

	private void openHelp() {
		minecraft.setScreen(new OreSpawnGuideScreen(this));
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2,
				OreSpawnScreenLayout.mainTitleY(this.height), 0xFFFFFF);
		if (validationError != null) {
			drawCenteredString(poseStack, font, validationError, width / 2,
					OreSpawnScreenLayout.mainErrorY(this.height), 0xFF5555);
		}
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	private Component geologyModeName(GeologyMode mode) {
		return new TranslatableComponent("value.orespawn.geology_mode." + mode.name().toLowerCase(java.util.Locale.ROOT));
	}

	private Component presetName(Preset preset) {
		return new TranslatableComponent("value.orespawn.preset." + preset.configName());
	}

	private List<FormattedCharSequence> tooltip(String key) {
		return font.split(new TranslatableComponent(key), 240);
	}

	@FunctionalInterface
	private interface PresetConsumer {
		void accept(Preset preset);
	}

	private static final class TemplateChoice {
		final ResourceLocation id;
		final Component label;
		TemplateChoice(ResourceLocation id, Component label) {
			this.id = id;
			this.label = label;
		}
		Component label() { return label; }
	}
}
