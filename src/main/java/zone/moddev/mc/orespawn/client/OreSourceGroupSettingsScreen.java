package zone.moddev.mc.orespawn.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.lwjgl.input.Mouse;

import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceCandidate;
import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceGroup;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

/** Infrequent group metadata and placement ownership controls opened by the group cog. */
final class OreSourceGroupSettingsScreen extends OreSpawnScreen {
	private static final int MARGIN = 10;
	private static final int NAME_TOP = 24;
	private static final int ALIAS_LABEL_TOP = 48;
	private static final int ALIAS_TOP = 58;
	private final GuiScreen parent;
	private final GeologyEditorSession session;
	private final String groupKey;
	private String pendingAliasMove;
	private String pendingAliasValue;
	private boolean pendingDissolve;
	private String error;
	private int aliasScroll;
	private int placementScroll;
	private int contentX;
	private int contentWidth;
	private int aliasListHeight;
	private int addAliasY;
	private int placementLabelY;
	private int placementListY;
	private TextFieldWidget nameField;
	private TextFieldWidget aliasField;
	private CompactScrollList aliasList;
	private CompactScrollList placementList;

	OreSourceGroupSettingsScreen(GuiScreen parent, GeologyEditorSession session, String groupKey) {
		super(new TextComponentTranslation("screen.orespawn.ore_source.group_settings"));
		this.parent = parent;
		this.session = session;
		this.groupKey = groupKey;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		OreSourceGroup group = selectedGroup();
		if (group == null) {
			minecraft.displayGuiScreen(parent);
			return;
		}
		contentWidth = Math.max(240, Math.min(600, width - (MARGIN * 2)));
		contentX = (width - contentWidth) / 2;
		boolean hasGroupAction = group.curated || group.canDeleteEmpty() || group.canDissolve();
		String groupActionKey = group.curated ? "button.orespawn.reset"
				: group.canDissolve() ? (pendingDissolve
						? "button.orespawn.ore_source.confirm_dissolve"
						: "button.orespawn.ore_source.dissolve_group")
				: "button.orespawn.ore_source.delete_group";
		int actionWidth = hasGroupAction
				? Math.min(120, Math.max(55, font.getStringWidth(I18n.format(groupActionKey)) + 10)) : 0;
		int nameLabelWidth = font.getStringWidth(
				I18n.format("option.orespawn.ore_source.group_name")) + 8;

		nameField = addButton(new TextFieldWidget(font, contentX + nameLabelWidth, NAME_TOP,
				contentWidth - nameLabelWidth - actionWidth - (hasGroupAction ? 5 : 0), 20,
				new TextComponentTranslation("option.orespawn.ore_source.group_name")));
		nameField.setMaxLength(64);
		nameField.setValue(group.displayName);
		if (hasGroupAction) {
			Button groupAction = addButton(OreSpawnScreenLayout.button(this, font,
					contentX + contentWidth - actionWidth, NAME_TOP, actionWidth, 20,
					new TextComponentTranslation(groupActionKey), button -> handleGroupAction(group)));
			OreSpawnScreenLayout.explain(this, groupAction, group.curated
					? "tooltip.orespawn.ore_source.reset_group"
					: group.canDissolve() ? "tooltip.orespawn.ore_source.dissolve_group"
					: "tooltip.orespawn.ore_source.delete_group");
		}

		aliasListHeight = aliasListHeight(height);
		final List<String> aliases = group.oreDictionaryEntries;
		aliasList = addButton(new CompactScrollList(this, contentX, ALIAS_TOP,
				contentWidth, aliasListHeight) {
			@Override protected int size() { return aliases.size(); }
			@Override protected String rowText(int index) { return aliases.get(index); }
			@Override protected List<String> rowTooltip(int index) {
				return java.util.Collections.singletonList(aliases.get(index));
			}
			@Override protected int rowActionWidth(int index) { return 18; }
			@Override protected void drawRowAction(net.minecraft.client.Minecraft minecraft,
					int index, int x, int y, int actionWidth, int rowHeight, boolean hovered) {
				if (hovered) drawRect(x, y, x + actionWidth, y + rowHeight, 0x805F5F5F);
				drawCenteredString(minecraft.fontRendererObj, "-", x + (actionWidth / 2), y + 3, 0xFFFFFF);
			}
			@Override protected void onRowActionPressed(int index) {
				syncName();
				session.removeOreMaterialAlias(group.material, aliases.get(index));
				pendingAliasMove = null;
				pendingAliasValue = null;
				pendingDissolve = false;
				error = null;
				captureScroll();
				rebuild();
			}
			@Override protected List<String> rowActionTooltip(int index) {
				return java.util.Collections.singletonList(
						I18n.format("tooltip.orespawn.ore_source.remove_alias", aliases.get(index)));
			}
		});
		aliasList.setFirstIndex(aliasScroll);

		addAliasY = ALIAS_TOP + aliasListHeight + 4;
		aliasField = addButton(new TextFieldWidget(font, contentX, addAliasY,
				contentWidth - 46, 20,
				new TextComponentTranslation("option.orespawn.ore_source.alias")));
		aliasField.setMaxLength(128);
		if (pendingAliasMove != null) {
			int split = pendingAliasMove.indexOf('|');
			aliasField.setValue(split < 0 ? pendingAliasMove : pendingAliasMove.substring(0, split));
		} else if (pendingAliasValue != null) {
			aliasField.setValue(pendingAliasValue);
		}
		Button add = addButton(new Button(contentX + contentWidth - 41, addAliasY, 41, 20,
				new TextComponentString(pendingAliasMove == null ? "+"
						: I18n.format("button.orespawn.ore_source.move")), button -> addAlias(group)));
		OreSpawnScreenLayout.explain(this, add, "tooltip.orespawn.ore_source.alias");

		final List<String> channels = placementChannels(group);
		placementLabelY = addAliasY + 27;
		int helpX = contentX + font.getStringWidth(
				I18n.format("label.orespawn.ore_source.placement_rules")) + 8;
		addButton(new Button(helpX, placementLabelY - 4, 16, 16,
				new TextComponentString("?"), button -> { }, (button, mouseX, mouseY) -> {
			List<String> lines = new ArrayList<>();
			lines.add(I18n.format("tooltip.orespawn.ore_source.placement_rules"));
			lines.add(placementStatus(group, channels));
			lines.add(I18n.format("tooltip.orespawn.ore_source.external_excluded"));
			renderStringTooltip(lines, mouseX, mouseY);
		}));
		placementListY = placementLabelY + 14;
		placementList = addButton(new CompactScrollList(this, contentX, placementListY,
				contentWidth, Math.max(16, height - 32 - placementListY)) {
			@Override protected int size() { return channels.size(); }
			@Override protected String rowText(int index) {
				String channel = channels.get(index);
				List<OreSourceCandidate> choices = OreSourceListScreen.placementCandidates(group, channel);
				String selected = group.placements.get(channel);
				int choice = OreSourceListScreen.indexOf(choices, selected);
				String source = choice < 0
						? I18n.format("label.orespawn.ore_source.missing")
						: OreSourceListScreen.candidateLabel(choices.get(choice));
				return channelLabel(channel) + (placementSelectable(group, channel) ? " > " : " = ")
						+ placementSourceLabel(source, choices.size());
			}
			@Override protected boolean rowEnabled(int index) {
				String channel = channels.get(index);
				return placementSelectable(group, channel);
			}
			@Override protected int rowColor(int index) {
				String channel = channels.get(index);
				List<OreSourceCandidate> choices = OreSourceListScreen.placementCandidates(group, channel);
				return choices.isEmpty() ? 0x777777
						: placementSelectable(group, channel) ? 0xFFFFFF : 0xC0C0C0;
			}
			@Override protected void onRowPressed(int index) {
				String channel = channels.get(index);
				List<OreSourceCandidate> choices = OreSourceListScreen.placementCandidates(group, channel);
				if (choices.size() <= 1) return;
				int selected = OreSourceListScreen.indexOf(choices, group.placements.get(channel));
				OreSourceCandidate next = choices.get((selected + 1 + choices.size()) % choices.size());
				syncName();
				session.setOreSourcePlacement(group.key, channel, next.sourceId);
				captureScroll();
				rebuild();
			}
			@Override protected List<String> rowTooltip(int index) {
				String channel = channels.get(index);
				List<OreSourceCandidate> choices = OreSourceListScreen.placementCandidates(group, channel);
				List<String> lines = new ArrayList<>();
				lines.add(channelLabel(channel) + " (" + channel + ")");
				lines.add(I18n.format("tooltip.orespawn.ore_source.placement_rules"));
				if ("keep_separate".equals(group.mode)) {
					lines.add(I18n.format("tooltip.orespawn.ore_source.placement_requires_consolidated"));
				} else if (choices.isEmpty()) {
					lines.add(I18n.format("tooltip.orespawn.ore_source.placement_missing"));
				} else if (choices.size() == 1) {
					lines.add(I18n.format("tooltip.orespawn.ore_source.placement_readonly"));
				} else {
					lines.add(I18n.format("tooltip.orespawn.ore_source.placement_select"));
				}
				lines.add(I18n.format("tooltip.orespawn.ore_source.external_excluded"));
				return lines;
			}
		});
		placementList.setFirstIndex(placementScroll);

		int doneWidth = doneButtonWidth(contentWidth);
		addButton(new Button(contentX + contentWidth - doneWidth, height - 28, doneWidth, 20,
				DialogTexts.GUI_DONE, button -> onClose()));
	}

	private void handleGroupAction(OreSourceGroup group) {
		syncName();
		pendingAliasMove = null;
		pendingAliasValue = null;
		if (group.curated) {
			session.resetOreMaterialGroup(group.material);
			pendingDissolve = false;
			error = null;
			rebuild();
			return;
		}
		if (group.canDeleteEmpty()) {
			if (session.deleteEmptyOreMaterialGroup(group.material)) minecraft.displayGuiScreen(parent);
			return;
		}
		if (!group.canDissolve()) return;
		if (!pendingDissolve) {
			pendingDissolve = true;
			error = I18n.format("error.orespawn.ore_source.confirm_dissolve");
			rebuild();
			return;
		}
		if (session.dissolveOreMaterialGroup(group.material)) minecraft.displayGuiScreen(parent);
	}

	private void addAlias(OreSourceGroup group) {
		syncName();
		String alias = aliasField == null ? "" : aliasField.getValue().trim();
		String owner = session.oreDictionaryOwner(alias);
		String move = owner == null || owner.equals(group.material) ? null : alias + '|' + owner;
		if (move != null && session.vanillaOreManagementRequiredForAliasMove(group.material, alias)) {
			pendingDissolve = false;
			pendingAliasMove = null;
			pendingAliasValue = alias;
			error = I18n.format("error.orespawn.ore_source.manage_vanilla_first");
			rebuild();
			return;
		}
		if (move != null && !move.equals(pendingAliasMove)) {
			pendingDissolve = false;
			pendingAliasMove = move;
			pendingAliasValue = alias;
			error = I18n.format("error.orespawn.ore_source.alias_owned",
					OreSourceListScreen.display(owner));
			rebuild();
			return;
		}
		if (!session.addOreMaterialAlias(group.material, alias, move != null)) {
			error = I18n.format("error.orespawn.ore_source.alias");
			return;
		}
		pendingAliasMove = null;
		pendingAliasValue = null;
		pendingDissolve = false;
		error = null;
		rebuild();
	}

	private OreSourceGroup selectedGroup() {
		for (OreSourceGroup group : session.oreSourceGroups()) {
			if (groupKey.equals(group.key)) return group;
		}
		return null;
	}

	private void syncName() {
		OreSourceGroup group = selectedGroup();
		if (group != null && nameField != null) {
			session.renameOreMaterialGroup(group.material, nameField.getValue());
		}
	}

	private void captureScroll() {
		if (aliasList != null) aliasScroll = aliasList.firstIndex();
		if (placementList != null) placementScroll = placementList.firstIndex();
	}

	private void rebuild() {
		buttons.clear();
		children.clear();
		init();
	}

	static int aliasListHeight(int height) {
		return height >= 260 ? 80 : 64;
	}

	static int doneButtonWidth(int contentWidth) {
		return Math.min(120, Math.max(80, contentWidth / 4));
	}

	static int validationMessageWidth(int contentWidth) {
		return Math.max(40, contentWidth - doneButtonWidth(contentWidth) - 8);
	}

	static List<String> placementChannels(OreSourceGroup group) {
		Set<String> channels = new TreeSet<>(group.channels());
		channels.addAll(group.placements.keySet());
		return new ArrayList<>(channels);
	}

	static String channelLabel(String channel) {
		return "orespawn:standard".equals(channel)
				? I18n.format("label.orespawn.ore_source.standard_veins")
				: OreSourceListScreen.display(channel);
	}

	static String placementSourceLabel(String source, int choices) {
		return choices > 1
				? I18n.format("label.orespawn.ore_source.placement_choice_count", source, choices)
				: source;
	}

	static boolean placementSelectable(OreSourceGroup group, String channel) {
		return !"keep_separate".equals(group.mode)
				&& OreSourceListScreen.placementCandidates(group, channel).size() > 1;
	}

	static String placementStatus(OreSourceGroup group, List<String> channels) {
		if (channels.isEmpty()) return I18n.format("label.orespawn.ore_source.no_placement_rules");
		if ("keep_separate".equals(group.mode)) {
			return I18n.format("tooltip.orespawn.ore_source.placement_requires_consolidated");
		}
		boolean selectable = false;
		for (String channel : channels) {
			int choices = OreSourceListScreen.placementCandidates(group, channel).size();
			if (choices == 0) return I18n.format("tooltip.orespawn.ore_source.placement_missing");
			selectable |= choices > 1;
		}
		return I18n.format(selectable ? "tooltip.orespawn.ore_source.placement_select"
				: "tooltip.orespawn.ore_source.placement_readonly");
	}

	@Override
	public void handleMouseInput() throws IOException {
		int mouseX = Mouse.getEventX() * width / minecraft.displayWidth;
		int mouseY = height - Mouse.getEventY() * height / minecraft.displayHeight - 1;
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			if (aliasList != null) aliasList.mouseWheel(mouseX, mouseY, wheel);
			if (placementList != null) placementList.mouseWheel(mouseX, mouseY, wheel);
			captureScroll();
		}
		super.handleMouseInput();
	}

	@Override
	public void onClose() {
		syncName();
		captureScroll();
		minecraft.displayGuiScreen(parent);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
		drawString(font, new TextComponentTranslation("option.orespawn.ore_source.group_name"),
				contentX + 2, NAME_TOP + 6, 0xA0A0A0);
		drawString(font, new TextComponentTranslation("label.orespawn.ore_source.aliases"),
				contentX + 2, ALIAS_LABEL_TOP, 0xA0A0A0);
		drawString(font, new TextComponentTranslation("label.orespawn.ore_source.placement_rules"),
				contentX + 2, placementLabelY, 0xFFFFFF);
		OreSourceGroup group = selectedGroup();
		if (group != null && group.oreDictionaryEntries.isEmpty()) {
			drawCenteredString(font, new TextComponentTranslation("label.orespawn.ore_source.no_aliases"),
					contentX + (contentWidth / 2), ALIAS_TOP + 4, 0x777777);
		}
		super.render(mouseX, mouseY, partialTick);
		if (error != null) {
			int messageWidth = validationMessageWidth(contentWidth);
			TextComponentString message = new TextComponentString(error);
			drawString(font, new TextComponentString(OreSpawnScreenLayout.fit(font,
					message, messageWidth)), contentX + 2, height - 22, 0xFF5555);
		}
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
		if (error != null) {
			int messageWidth = validationMessageWidth(contentWidth);
			if (mouseX >= contentX && mouseX < contentX + messageWidth
					&& mouseY >= height - 26 && mouseY < height - 8) {
				renderStringTooltip(java.util.Collections.singletonList(error), mouseX, mouseY);
			}
		}
	}
}
