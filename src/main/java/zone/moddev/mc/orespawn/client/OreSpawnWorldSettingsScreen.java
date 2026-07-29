package zone.moddev.mc.orespawn.client;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;
import zone.moddev.mc.orespawn.worldgen.FormationSettings.Preset;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;
import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager;
import zone.moddev.mc.orespawn.integration.WorldgenIntegrationManager.TemplateDefinition;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

public final class OreSpawnWorldSettingsScreen extends Screen {
	private static final int BUTTON_HEIGHT = 20;

	private final Screen parent;
	private final GeologyEditorSession session;
	private GeologyMode geologyMode;
	private Preset horizontalSize;
	private Preset verticalThickness;
	private Preset waviness;
	private Preset edgeIrregularity;
	private Preset formationContinuity;
	private boolean placeFluidDeposits;
	private boolean manageVanillaOres;
	private ResourceLocation selectedTemplate;
	private final List<TemplateChoice> templateChoices;

	private CycleButton<GeologyMode> geologyModeButton;
	private CycleButton<Boolean> fluidDepositsButton;
	private CycleButton<Boolean> vanillaOresButton;
	private CycleButton<Preset> horizontalSizeButton;
	private CycleButton<Preset> verticalThicknessButton;
	private CycleButton<Preset> wavinessButton;
	private CycleButton<Preset> edgeIrregularityButton;
	private CycleButton<Preset> formationContinuityButton;
	private Component validationError;
	private int columnWidth = 150;

	public OreSpawnWorldSettingsScreen(Screen parent, WorldGeologyProfile profile) {
		this(parent, profile, Collections.emptyList());
	}

	OreSpawnWorldSettingsScreen(Screen parent, WorldGeologyProfile profile, List<String> availableDimensions) {
		super(Component.translatable("screen.orespawn.world_settings"));
		this.parent = parent;
		this.session = new GeologyEditorSession(profile, availableDimensions);
		templateChoices = availableTemplates(profile);
		setProfile(profile);
	}

	@Override
	protected void init() {
		clearWidgetReferences();
		int contentWidth = Math.min(390, Math.max(310, this.width - 24));
		columnWidth = (contentWidth - 5) / 2;
		int left = (this.width - contentWidth) / 2;
		int right = left + columnWidth + 5;
		boolean terrain = session.hasTerrainRules();
		boolean fluids = !session.fluidDepositIds().isEmpty();
		int top = OreSpawnScreenLayout.mainTop(this.height);
		int rows = (templateChoices.size() > 1 ? 1 : 0) + (terrain ? 8
				: 5 + (fluids ? 1 : 0));
		int available = OreSpawnScreenLayout.footerY(height) - top - BUTTON_HEIGHT - 4;
		int row = Math.min(OreSpawnScreenLayout.mainRowSpacing(this.height),
				rows <= 1 ? 24 : Math.max(BUTTON_HEIGHT, available / (rows - 1)));
		int rowIndex = 0;
		if (templateChoices.size() > 1) {
			TemplateChoice initialTemplate = templateChoice(selectedTemplate);
			addRenderableWidget(CycleButton.builder(TemplateChoice::label)
					.withValues(templateChoices).withInitialValue(initialTemplate)
					.withTooltip(value -> tooltip("guide.orespawn.world.1"))
					.create(left, top + (row * rowIndex++), contentWidth, BUTTON_HEIGHT,
							Component.translatable("option.orespawn.template"),
							(button, value) -> selectTemplate(value.id)));
		}
		addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
				this, font, left, top + (row * rowIndex++),
				contentWidth, BUTTON_HEIGHT, Component.translatable("button.orespawn.recommended"),
				button -> resetRecommended()), "guide.orespawn.welcome.3"));

		if (!terrain) {
			vanillaOresButton = addVanillaOresButton(left, top + (row * rowIndex), columnWidth);
			addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
					this, font, right, top + (row * rowIndex++),
					columnWidth, BUTTON_HEIGHT, Component.translatable("button.orespawn.materials"),
					button -> openMaterials()), "guide.orespawn.rocks.2"));
			addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
					this, font, left, top + (row * rowIndex++),
					contentWidth, BUTTON_HEIGHT, Component.translatable("button.orespawn.configure_strata"),
					button -> configureRockStrata()), "guide.orespawn.rocks.3"));
			if (fluids) {
				fluidDepositsButton = addFluidToggle(left, top + (row * rowIndex), columnWidth);
				addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
						this, font, right, top + (row * rowIndex++),
						columnWidth, BUTTON_HEIGHT, fluidEditorLabel(), button -> openFluidDeposits()),
						"guide.orespawn.fluids.1"));
			}
			addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
					this, font, left, top + (row * rowIndex++),
					contentWidth, BUTTON_HEIGHT,
					Component.translatable("button.orespawn.biomes_world_materials"),
					button -> openBiomeWorldMaterials()), "guide.orespawn.biomes.1"));
			addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
					this, font, left, top + (row * rowIndex),
					contentWidth, BUTTON_HEIGHT, Component.translatable("button.orespawn.help"),
					button -> openHelp()), "guide.orespawn.welcome.1"));
		} else {
			geologyModeButton = addRenderableWidget(CycleButton.builder(this::geologyModeName)
					.withValues(Arrays.asList(GeologyMode.GEOME, GeologyMode.LEGACY))
					.withInitialValue(geologyMode)
					.withTooltip(value -> tooltip("tooltip.orespawn.geology_mode"))
					.create(left, top + (row * rowIndex), columnWidth, BUTTON_HEIGHT,
							Component.translatable("option.orespawn.geology_mode"),
							(button, value) -> { geologyMode = value; updateFormationControls(); }));
			if (fluids) fluidDepositsButton = addFluidToggle(right, top + (row * rowIndex), columnWidth);
			else vanillaOresButton = addVanillaOresButton(right, top + (row * rowIndex), columnWidth);
			rowIndex++;

			horizontalSizeButton = addPresetButton(left, top + (row * rowIndex),
					"option.orespawn.horizontal_size", "tooltip.orespawn.horizontal_size",
					horizontalSize, value -> horizontalSize = value);
			verticalThicknessButton = addPresetButton(right, top + (row * rowIndex++),
					"option.orespawn.vertical_thickness", "tooltip.orespawn.vertical_thickness",
					verticalThickness, value -> verticalThickness = value);
			wavinessButton = addPresetButton(left, top + (row * rowIndex),
					"option.orespawn.waviness", "tooltip.orespawn.waviness",
					waviness, value -> waviness = value);
			edgeIrregularityButton = addPresetButton(right, top + (row * rowIndex++),
					"option.orespawn.edge_irregularity", "tooltip.orespawn.edge_irregularity",
					edgeIrregularity, value -> edgeIrregularity = value);
			formationContinuityButton = addPresetButton(left, top + (row * rowIndex),
					"option.orespawn.formation_continuity", "tooltip.orespawn.formation_continuity",
					formationContinuity, value -> formationContinuity = value);
			if (fluids) vanillaOresButton = addVanillaOresButton(right,
					top + (row * rowIndex), columnWidth);
			rowIndex++;
			addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
					this, font, left, top + (row * rowIndex),
					columnWidth, BUTTON_HEIGHT, Component.translatable("button.orespawn.materials"),
					button -> openMaterials()), "guide.orespawn.rocks.2"));
			addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
					this, font, right, top + (row * rowIndex++),
					columnWidth, BUTTON_HEIGHT,
					Component.translatable("button.orespawn.biomes_world_materials"),
					button -> openBiomeWorldMaterials()), "guide.orespawn.biomes.1"));
			addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
					this, font, left, top + (row * rowIndex),
					columnWidth, BUTTON_HEIGHT, Component.translatable("button.orespawn.advanced"),
					button -> openAdvanced()), "guide.orespawn.world.3"));
			addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
					this, font, right, top + (row * rowIndex++),
					columnWidth, BUTTON_HEIGHT, Component.translatable("button.orespawn.help"),
					button -> openHelp()), "guide.orespawn.welcome.1"));
			addRenderableWidget(OreSpawnScreenLayout.explain(OreSpawnScreenLayout.button(
					this, font, left, top + (row * rowIndex),
					contentWidth, BUTTON_HEIGHT, fluidEditorLabel(), button -> openFluidDeposits()),
					"guide.orespawn.fluids.1"));
		}
		addRenderableWidget(OreSpawnScreenLayout.button(this, font, left, this.height - 28, columnWidth, BUTTON_HEIGHT,
				CommonComponents.GUI_DONE, button -> saveAndClose()));
		addRenderableWidget(OreSpawnScreenLayout.button(this, font, right, this.height - 28, columnWidth, BUTTON_HEIGHT,
				CommonComponents.GUI_CANCEL, button -> onClose()));
		updateFormationControls();
	}

	private void clearWidgetReferences() {
		geologyModeButton = null;
		fluidDepositsButton = null;
		vanillaOresButton = null;
		horizontalSizeButton = null;
		verticalThicknessButton = null;
		wavinessButton = null;
		edgeIrregularityButton = null;
		formationContinuityButton = null;
	}

	private CycleButton<Boolean> addVanillaOresButton(int x, int y, int width) {
		return addRenderableWidget(CycleButton.onOffBuilder(manageVanillaOres)
				.withTooltip(value -> tooltip("tooltip.orespawn.manage_vanilla_ores"))
				.create(x, y, width, BUTTON_HEIGHT,
						Component.translatable("option.orespawn.manage_vanilla_ores"),
						(button, value) -> manageVanillaOres = value));
	}

	private CycleButton<Boolean> addFluidToggle(int x, int y, int width) {
		return addRenderableWidget(CycleButton.onOffBuilder(placeFluidDeposits)
				.withTooltip(value -> tooltip("tooltip.orespawn.fluid_deposits"))
				.create(x, y, width, BUTTON_HEIGHT, fluidControlLabel(),
						(button, value) -> placeFluidDeposits = value));
	}

	private CycleButton<Preset> addPresetButton(int x, int y, String labelKey, String tooltipKey,
			Preset initialValue, PresetConsumer consumer) {
		return addRenderableWidget(CycleButton.builder(this::presetName)
				.withValues(Arrays.asList(Preset.values()))
				.withInitialValue(initialValue)
				.withTooltip(value -> tooltip(tooltipKey))
				.create(x, y, columnWidth, BUTTON_HEIGHT, Component.translatable(labelKey),
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
				placeFluidDeposits);
		session.applyProfile(recommended);
		setProfile(recommended);
		rebuildWidgets();
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

	private void selectTemplate(ResourceLocation templateId) {
		if (Objects.equals(selectedTemplate, templateId)) {
			return;
		}
		syncSession();
		WorldGeologyProfile profile = templateId == null
				? session.profile().withoutTemplate() : session.profile().withTemplate(templateId);
		session.applyProfile(profile);
		setProfile(profile);
		rebuildWidgets();
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
		result.add(new TemplateChoice(null, Component.translatable("value.orespawn.template.none")));
		for (TemplateDefinition template : WorldgenIntegrationManager.templates()) {
			if (template.available()) {
				result.add(new TemplateChoice(template.id(), Component.translatable(template.nameKey())));
			}
		}
		ResourceLocation selected = profile.selectedTemplate().orElse(null);
		if (selected != null && result.stream().noneMatch(choice -> selected.equals(choice.id))) {
			result.add(new TemplateChoice(selected, Component.literal(selected.toString())));
		}
		return result;
	}

	private void saveAndClose() {
		syncSession();
		List<String> errors = session.validate();
		if (!errors.isEmpty()) {
			validationError = Component.literal(errors.get(0));
			return;
		}
		WorldGeologyProfileManager.setPendingNewWorldProfile(session.profile());
		minecraft.setScreen(parent);
	}

	private void syncSession() {
		WorldGeologyProfile selected = session.profile().withSelection(
				geologyMode, horizontalSize, verticalThickness, waviness,
				edgeIrregularity, formationContinuity, placeFluidDeposits);
		com.google.gson.JsonObject root = selected.rootCopy();
		root.addProperty("manage_vanilla_ores", manageVanillaOres);
		session.applyProfile(selected.withRoot(root));
	}

	private void openMaterials() {
		syncSession();
		minecraft.setScreen(new GeologyMaterialsScreen(this, session));
	}

	private void configureRockStrata() {
		syncSession();
		session.configureDefaultVanillaStrata();
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

	private void openBiomeWorldMaterials() {
		syncSession();
		minecraft.setScreen(new BiomeWorldMaterialsScreen(this, session));
	}

	private void openFluidDeposits() {
		syncSession();
		minecraft.setScreen(new FluidDepositListScreen(this, session));
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
		return Component.translatable("value.orespawn.geology_mode." + mode.name().toLowerCase(java.util.Locale.ROOT));
	}

	private Component presetName(Preset preset) {
		return Component.translatable("value.orespawn.preset." + preset.configName());
	}

	private Component fluidControlLabel() {
		List<String> ids = session.fluidDepositIds();
		if (ids.size() == 1) {
			JsonObjectAccess access = new JsonObjectAccess(session.fluidDeposit(ids.get(0)));
			ResourceLocation blockId = access.resource("block");
			Block block = blockId == null ? null : ForgeRegistries.BLOCKS.getValue(blockId);
			if (block != null) return Component.translatable(block.getDescriptionId());
		}
		return Component.translatable("option.orespawn.fluid_deposits");
	}

	private Component fluidEditorLabel() {
		int total = session.fluidDepositIds().size();
		int enabled = session.enabledFluidDepositCount();
		return Component.translatable("button.orespawn.fluid_deposits_count", enabled, total);
	}

	protected void rebuildWidgets() {
		clearWidgets();
		init();
	}

	private static final class JsonObjectAccess {
		private final com.google.gson.JsonObject value;
		JsonObjectAccess(com.google.gson.JsonObject value) { this.value = value; }
		ResourceLocation resource(String key) {
			try { return new ResourceLocation(value.get(key).getAsString()); }
			catch (RuntimeException ignored) { return null; }
		}
	}

	private net.minecraft.client.gui.components.Tooltip tooltip(String key) {
		return net.minecraft.client.gui.components.Tooltip.create(Component.translatable(key));
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
