package zone.moddev.mc.orespawn.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.input.Mouse;

import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceCandidate;
import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceGroup;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

/** Two-pane material-group editor backed by compact independently scrolling lists. */
final class OreSourceListScreen extends OreSpawnScreen {
	private static final ResourceLocation COG =
			new ResourceLocation("orespawn", "textures/gui/cog.png");
	private static final int HORIZONTAL_MARGIN = 10;
	private static final int PANE_GAP = 6;
	private static final int CONTENT_TOP = 24;
	private final GuiScreen parent;
	private final GeologyEditorSession session;
	private final Map<String, TextFieldWidget> weights = new LinkedHashMap<>();
	private String selectedKey;
	private String error;
	private boolean showAll;
	private boolean resetArmed;
	private int groupScroll;
	private int outputScroll;
	private int leftPaneX;
	private int leftPaneWidth;
	private int rightPaneX;
	private int rightPaneWidth;
	private int paneBottom;
	private CompactScrollList groupList;
	private CompactScrollList outputList;
	private List<OreSourceCandidate> visibleCandidates = java.util.Collections.emptyList();

	OreSourceListScreen(GuiScreen parent, GeologyEditorSession session) {
		super(new TextComponentTranslation("screen.orespawn.ore_sources"));
		this.parent = parent;
		this.session = session;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		weights.clear();
		List<OreSourceGroup> groups = orderedGroups();
		if (selected(groups) == null && !groups.isEmpty()) selectedKey = groups.get(0).key;

		int contentWidth = contentWidth(width);
		leftPaneX = (width - contentWidth) / 2;
		leftPaneWidth = leftPaneWidth(width);
		rightPaneX = leftPaneX + leftPaneWidth + PANE_GAP;
		rightPaneWidth = contentWidth - leftPaneWidth - PANE_GAP;
		paneBottom = height - 32;

		initGroups(groups);
		initOutputs(selected(groups));
		String resetLabel = I18n.format(resetArmed
				? "button.orespawn.ore_source.confirm_reset_all"
				: "button.orespawn.ore_source.reset_all");
		int resetWidth = Math.min(leftPaneWidth, Math.max(54, font.getStringWidth(resetLabel) + 10));
		Button reset = addButton(OreSpawnScreenLayout.button(this, font,
				leftPaneX, height - 28, resetWidth, 20,
				new TextComponentString(resetLabel), button -> resetAll()));
		OreSpawnScreenLayout.explain(this, reset, "tooltip.orespawn.ore_source.reset_all");
		int doneWidth = Math.min(150, rightPaneWidth);
		addButton(new Button(rightPaneX + rightPaneWidth - doneWidth, height - 28, doneWidth, 20,
				DialogTexts.GUI_DONE, button -> onClose()));
	}

	private void resetAll() {
		if (!resetArmed) {
			resetArmed = true;
			error = null;
			rebuild();
			return;
		}
		session.resetOreSourcesToDefaults();
		selectedKey = null;
		groupScroll = 0;
		outputScroll = 0;
		showAll = false;
		resetArmed = false;
		error = null;
		rebuild();
	}

	private void initGroups(final List<OreSourceGroup> groups) {
		int addX = leftPaneX + font.getStringWidth(I18n.format("label.orespawn.ore_source.groups")) + 8;
		Button add = addButton(new Button(addX, 4, 16, 16,
				new TextComponentString("+"), button -> {
			if (!syncWeights()) return;
			captureScroll();
			selectedKey = session.addOreMaterialGroup();
			outputScroll = 0;
			error = null;
			rebuild();
		}));
		OreSpawnScreenLayout.explain(this, add, "button.orespawn.ore_source.add_group");
		int showX = addX + 20;
		String showLabel = I18n.format(showAll
				? "button.orespawn.ore_source.hide_single"
				: "button.orespawn.show_all");
		int showWidth = compactFilterWidth(leftPaneX + leftPaneWidth - showX,
				font.getStringWidth(showLabel));
		Button show = addButton(OreSpawnScreenLayout.button(this, font,
				showX, 4, showWidth, 16,
				new TextComponentString(showLabel), button -> {
			if (!syncWeights()) return;
			captureScroll();
			showAll = !showAll;
			groupScroll = 0;
			error = null;
			rebuild();
		}));
		OreSpawnScreenLayout.explain(this, show, "tooltip.orespawn.ore_source.group_colours");

		groupList = addButton(new CompactScrollList(this, leftPaneX, CONTENT_TOP,
				leftPaneWidth, paneBottom - CONTENT_TOP) {
			@Override protected int size() { return groups.size(); }

			@Override protected String rowText(int index) {
				OreSourceGroup group = groups.get(index);
				return group.displayName + " - " + display(group.domain);
			}

			@Override protected int rowColor(int index) {
				return groupRowColor(groups.get(index));
			}

			@Override protected boolean rowSelected(int index) {
				return groups.get(index).key.equals(selectedKey);
			}

			@Override protected void onRowPressed(int index) {
				if (!syncWeights()) return;
				captureScroll();
				selectedKey = groups.get(index).key;
				outputScroll = 0;
				error = null;
				rebuild();
			}

			@Override protected List<String> rowTooltip(int index) {
				return groupTooltip(groups.get(index));
			}

			@Override protected int rowActionWidth(int index) { return 16; }

			@Override protected void drawRowAction(Minecraft minecraft, int index,
					int x, int y, int actionWidth, int rowHeight, boolean hovered) {
				drawCompactCog(minecraft, x, y, actionWidth, rowHeight, hovered);
			}

			@Override protected void onRowActionPressed(int index) {
				if (!syncWeights()) return;
				captureScroll();
				selectedKey = groups.get(index).key;
				minecraft.displayGuiScreen(new OreSourceGroupSettingsScreen(
						OreSourceListScreen.this, session, selectedKey));
			}

			@Override protected List<String> rowActionTooltip(int index) {
				return java.util.Collections.singletonList(
						I18n.format("tooltip.orespawn.ore_source.group_settings"));
			}
		});
		groupList.setFirstIndex(groupScroll);
		int selected = groupIndexOf(groups, selectedKey);
		if (selected >= 0) groupList.ensureVisible(selected);
	}

	private void initOutputs(final OreSourceGroup group) {
		if (group == null) {
			visibleCandidates = java.util.Collections.emptyList();
			return;
		}
		String mode = "keep_separate".equals(group.mode) ? "keep_original" : group.outputMode;
		String acceptLabel = I18n.format("button.orespawn.ore_source.accept");
		int acceptWidth = group.needsReview()
				? Math.min(Math.max(42, font.getStringWidth(acceptLabel) + 10), rightPaneWidth / 3)
				: 0;
		int modeWidth = rightPaneWidth - (acceptWidth == 0 ? 0 : acceptWidth + 4);
		Button modeButton = addButton(OreSpawnScreenLayout.button(this, font,
				rightPaneX, CONTENT_TOP, modeWidth, 20,
				new TextComponentTranslation("option.orespawn.ore_source.output_mode",
						new TextComponentTranslation("mode.orespawn.ore_source." + mode).getFormattedText()),
				button -> {
					if (!syncWeights()) return;
					cycleMode(group);
					outputScroll = 0;
					rebuild();
				}));
		OreSpawnScreenLayout.explain(this, modeButton, "tooltip.orespawn.ore_source.output_mode");
		if (acceptWidth > 0) {
			Button accept = addButton(OreSpawnScreenLayout.button(this, font,
					rightPaneX + modeWidth + 4, CONTENT_TOP, acceptWidth, 20,
					new TextComponentString(acceptLabel), button -> {
						if (!syncWeights()) return;
						captureScroll();
						session.acceptOreSourcePolicy(group.key);
						error = null;
						rebuild();
					}));
			OreSpawnScreenLayout.explain(this, accept, "tooltip.orespawn.ore_source.accept");
		}

		visibleCandidates = group.outputCandidates();
		final List<OreSourceCandidate> candidates = visibleCandidates;
		outputList = addButton(new CompactScrollList(this, rightPaneX, CONTENT_TOP + 24,
				rightPaneWidth, paneBottom - CONTENT_TOP - 24) {
			@Override protected int size() { return candidates.size(); }

			@Override protected String rowText(int index) {
				OreSourceCandidate candidate = candidates.get(index);
				boolean selected = group.outputs.containsKey(candidate.sourceId);
				boolean single = "single".equals(group.outputMode)
						&& !"keep_separate".equals(group.mode);
				String marker = single ? (selected ? "(*) " : "( ) ")
						: (selected ? "[x] " : "[ ] ");
				return marker + candidateLabel(candidate);
			}

			@Override protected boolean rowEnabled(int index) {
				OreSourceCandidate candidate = candidates.get(index);
				return candidate.loaded && !candidate.enrichment
						&& !"keep_separate".equals(group.mode);
			}

			@Override protected boolean rowSelected(int index) {
				return group.outputs.containsKey(candidates.get(index).sourceId);
			}

			@Override protected void onRowPressed(int index) {
				if (!syncWeights()) return;
				OreSourceCandidate candidate = candidates.get(index);
				boolean single = "single".equals(group.outputMode)
						&& !"keep_separate".equals(group.mode);
				session.setOreSourceOutput(group.key, candidate.sourceId,
						single || !group.outputs.containsKey(candidate.sourceId),
						group.outputs.containsKey(candidate.sourceId)
								? group.outputs.get(candidate.sourceId) : 1.0D);
				captureScroll();
				rebuild();
			}

			@Override protected List<String> rowTooltip(int index) {
				return candidateTooltip(candidates.get(index));
			}

			@Override protected int rowActionWidth(int index) {
				OreSourceCandidate candidate = candidates.get(index);
				return "custom".equals(group.outputMode)
						&& !"keep_separate".equals(group.mode)
						&& group.outputs.containsKey(candidate.sourceId) ? 48 : 0;
			}
		});
		outputList.setFirstIndex(outputScroll);

		if ("custom".equals(group.outputMode) && !"keep_separate".equals(group.mode)) {
			for (OreSourceCandidate candidate : candidates) {
				if (!group.outputs.containsKey(candidate.sourceId)) continue;
				TextFieldWidget weight = addButton(new TextFieldWidget(font, 0, 0, 42, 14,
						new TextComponentTranslation("option.orespawn.ore_source.weight")));
				weight.setMaxLength(12);
				weight.setValue(formatWeight(group.outputs.get(candidate.sourceId)));
				weight.enabled = candidate.loaded && !candidate.enrichment;
				weight.visible = false;
				weights.put(candidate.sourceId, weight);
			}
		}
	}

	private void updateWeightBounds() {
		if (outputList == null) return;
		for (int index = 0; index < visibleCandidates.size(); index++) {
			OreSourceCandidate candidate = visibleCandidates.get(index);
			TextFieldWidget weight = weights.get(candidate.sourceId);
			if (weight == null) continue;
			int y = outputList.rowY(index);
			weight.visible = y >= 0;
			if (weight.visible) weight.setBounds(outputList.contentRight() - 45, y + 1, 42, 14);
		}
	}

	private void cycleMode(OreSourceGroup group) {
		resetArmed = false;
		if (!group.hasManagedPlacementSource()) {
			if (!"keep_separate".equals(group.mode)) session.restoreOreSourceOriginalMode(group.key);
			error = I18n.format("label.orespawn.ore_source.no_placement_rules");
			return;
		}
		error = null;
		if ("keep_separate".equals(group.mode)) session.setOreSourceOutputMode(group.key, "balanced");
		else if ("balanced".equals(group.outputMode)) session.setOreSourceOutputMode(group.key, "single");
		else if ("single".equals(group.outputMode)) session.setOreSourceOutputMode(group.key, "custom");
		else session.restoreOreSourceOriginalMode(group.key);
	}

	private boolean syncWeights() {
		OreSourceGroup group = selected(orderedGroups());
		if (group == null || !"custom".equals(group.outputMode)) return true;
		for (Map.Entry<String, TextFieldWidget> entry : weights.entrySet()) {
			if (!group.outputs.containsKey(entry.getKey())) continue;
			try {
				double value = Double.parseDouble(entry.getValue().getValue().trim());
				if (!Double.isFinite(value) || value <= 0.0D) throw new NumberFormatException();
				session.setOreSourceOutput(group.key, entry.getKey(), true, value);
			} catch (NumberFormatException invalid) {
				error = I18n.format("error.orespawn.ore_source.weight");
				return false;
			}
		}
		error = null;
		return true;
	}

	private void captureScroll() {
		resetArmed = false;
		if (groupList != null) groupScroll = groupList.firstIndex();
		if (outputList != null) outputScroll = outputList.firstIndex();
	}

	private List<OreSourceGroup> orderedGroups() {
		List<OreSourceGroup> result = new ArrayList<>();
		for (OreSourceGroup group : session.oreSourceGroups()) {
			if (showAll || showByDefault(group)) result.add(group);
		}
		result.sort((left, right) -> {
			int leftRank = groupStatusRank(left);
			int rightRank = groupStatusRank(right);
			if (leftRank != rightRank) return Integer.compare(leftRank, rightRank);
			int name = left.displayName.compareToIgnoreCase(right.displayName);
			if (name != 0) return name;
			int material = left.material.compareTo(right.material);
			return material != 0 ? material : left.domain.compareTo(right.domain);
		});
		return result;
	}

	static boolean showByDefault(OreSourceGroup group) {
		return !group.isRoutineSingleSource();
	}

	static int groupRowColor(OreSourceGroup group) {
		return group.needsAttention() ? 0xFF5555
				: group.isRoutineSingleSource() ? 0x55FF55 : 0xFFFF55;
	}

	private static int groupStatusRank(OreSourceGroup group) {
		return group.needsAttention() ? 0 : group.isRoutineSingleSource() ? 2 : 1;
	}

	private OreSourceGroup selected(List<OreSourceGroup> groups) {
		if (selectedKey == null) return null;
		for (OreSourceGroup group : groups) if (selectedKey.equals(group.key)) return group;
		return null;
	}

	static List<OreSourceCandidate> placementCandidates(OreSourceGroup group, String channel) {
		List<OreSourceCandidate> result = new ArrayList<>();
		for (OreSourceCandidate candidate : group.candidates) {
			if (candidate.loaded && candidate.active && !candidate.external && !candidate.enrichment
					&& channel.equals(candidate.channel)) result.add(candidate);
		}
		return result;
	}

	static int indexOf(List<OreSourceCandidate> candidates, String sourceId) {
		for (int index = 0; index < candidates.size(); index++) {
			if (candidates.get(index).sourceId.equals(sourceId)) return index;
		}
		return -1;
	}

	static int groupIndexOf(List<OreSourceGroup> groups, String key) {
		for (int index = 0; index < groups.size(); index++) {
			if (groups.get(index).key.equals(key)) return index;
		}
		return -1;
	}

	static String candidateLabel(OreSourceCandidate candidate) {
		String owner = candidate.ownerName.isEmpty() ? candidate.owner : candidate.ownerName;
		String state = candidate.external ? I18n.format("status.orespawn.ore_source.external_short")
				: !candidate.loaded ? I18n.format("status.orespawn.ore_source.missing_short")
				: !candidate.active ? I18n.format("status.orespawn.ore_source.output_only") : "";
		return owner + " - " + candidate.registryId + (state.isEmpty() ? "" : " (" + state + ")");
	}

	static List<String> candidateTooltip(OreSourceCandidate candidate) {
		List<String> result = new ArrayList<>();
		result.add(I18n.format("label.orespawn.ore_source.owner", candidate.owner,
				candidate.ownerVersion.isEmpty() ? "?" : candidate.ownerVersion));
		result.add(I18n.format("label.orespawn.ore_source.registry", candidate.registryId, candidate.metadata));
		result.add(I18n.format("label.orespawn.ore_source.dictionary",
				candidate.oreDictionary.isEmpty() ? "-" : String.join(", ", candidate.oreDictionary)));
		result.add(I18n.format(candidate.external ? "tooltip.orespawn.ore_source.external"
				: !candidate.loaded ? "tooltip.orespawn.ore_source.missing"
				: !candidate.active ? "tooltip.orespawn.ore_source.output_only"
				: "tooltip.orespawn.ore_source.controlled"));
		return result;
	}

	static List<String> groupTooltip(OreSourceGroup group) {
		List<String> result = new ArrayList<>();
		result.add(group.material);
		result.add(I18n.format("label.orespawn.ore_source.summary", group.outputCandidates().size(),
				I18n.format("status.orespawn.ore_source." + group.status)));
		result.add(group.oreDictionaryEntries.isEmpty() ? "-"
				: String.join(", ", group.oreDictionaryEntries));
		result.add(I18n.format("tooltip.orespawn.ore_source.group_colours"));
		return result;
	}

	static String formatWeight(Double weight) {
		if (weight == null) return "1";
		long whole = weight.longValue();
		return weight.doubleValue() == whole ? Long.toString(whole) : Double.toString(weight.doubleValue());
	}

	static String display(String id) {
		int split = id.indexOf(':');
		String value = (split < 0 ? id : id.substring(split + 1)).replace('_', ' ').replace('/', ' ');
		return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private static void drawCompactCog(Minecraft minecraft, int x, int y,
			int width, int height, boolean hovered) {
		if (hovered) drawRect(x, y, x + width, y + height, 0x805F5F5F);
		minecraft.getTextureManager().bindTexture(COG);
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		GlStateManager.pushMatrix();
		GlStateManager.translate(x + (width / 2.0F), y + (height / 2.0F), 0.0F);
		GlStateManager.scale(0.75F, 0.75F, 1.0F);
		drawModalRectWithCustomSizedTexture(-8, -8, 0.0F, 0.0F, 16, 16, 16.0F, 16.0F);
		drawModalRectWithCustomSizedTexture(-8, -8, 0.0F, 0.0F, 16, 16, 16.0F, 16.0F);
		GlStateManager.popMatrix();
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
	}

	private void rebuild() {
		buttons.clear();
		children.clear();
		init();
	}

	static int contentWidth(int width) {
		return Math.max(240, Math.min(700, width - (HORIZONTAL_MARGIN * 2)));
	}

	static int leftPaneWidth(int width) {
		int content = contentWidth(width);
		return Math.max(104, Math.min(280, (content * 43) / 100));
	}

	static int listHeight(int height) {
		return Math.max(32, (height - 32) - CONTENT_TOP);
	}

	static int compactFilterWidth(int availableWidth, int labelWidth) {
		return Math.min(Math.max(38, labelWidth + 10), Math.max(16, availableWidth));
	}

	@Override
	public void handleMouseInput() throws IOException {
		int mouseX = Mouse.getEventX() * width / minecraft.displayWidth;
		int mouseY = height - Mouse.getEventY() * height / minecraft.displayHeight - 1;
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			if (groupList != null) groupList.mouseWheel(mouseX, mouseY, wheel);
			if (outputList != null) outputList.mouseWheel(mouseX, mouseY, wheel);
			captureScroll();
		}
		super.handleMouseInput();
	}

	@Override
	public void onClose() {
		captureScroll();
		if (syncWeights()) minecraft.displayGuiScreen(parent);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawRect(leftPaneX - 2, CONTENT_TOP - 2,
				leftPaneX + leftPaneWidth + 2, paneBottom + 2, 0x70000000);
		drawRect(rightPaneX - 2, CONTENT_TOP - 2,
				rightPaneX + rightPaneWidth + 2, paneBottom + 2, 0x70000000);
		drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
		drawString(font, new TextComponentTranslation("label.orespawn.ore_source.groups"),
				leftPaneX + 2, 8, 0xFFFFFF);
		if (error != null) {
			drawCenteredString(font, new TextComponentString(OreSpawnScreenLayout.fit(font,
					new TextComponentString(error), width - 24)), width / 2, height - 39, 0xFF5555);
		}
		if (selected(orderedGroups()) == null) {
			drawCenteredString(font, new TextComponentTranslation("label.orespawn.ore_source.none"),
					leftPaneX + (leftPaneWidth / 2), CONTENT_TOP + 10, 0xA0A0A0);
		}
		updateWeightBounds();
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}
}
