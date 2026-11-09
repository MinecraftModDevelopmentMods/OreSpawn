package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import zone.moddev.mc.orespawn.client.OreSpawnModDirectoryModel.Entry;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

/** Paginated directory of loaded mods known to OreSpawn. */
final class OreSpawnModsScreen extends OreSpawnScreen {
	private static final int ROW_HEIGHT = 42;
	private final GuiScreen parent;
	private final List<Entry> entries;
	private int page;
	private String error;
	private int contentLeft;
	private int contentWidth;
	private int listTop;
	private int pageSize;

	OreSpawnModsScreen(GuiScreen parent) {
		this(parent, OreSpawnModDirectoryModel.snapshot());
	}

	OreSpawnModsScreen(GuiScreen parent, List<Entry> entries) {
		super(new TextComponentTranslation("screen.orespawn.mods"));
		this.parent = parent;
		this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
	}

	@Override
	protected void init() {
		contentWidth = Math.min(390, Math.max(280, width - 24));
		contentLeft = (width - contentWidth) / 2;
		listTop = 38;
		int controlsY = height - 52;
		pageSize = pageSize(height);
		int pageCount = pageCount(entries.size(), pageSize);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * pageSize;
		for (int index = 0; index < pageSize && start + index < entries.size(); index++) {
			Entry entry = entries.get(start + index);
			CogButton cog = addButton(new CogButton(contentLeft + contentWidth - 26,
					listTop + index * ROW_HEIGHT + 9, button -> open(entry),
					(button, mouseX, mouseY) -> renderStringTooltip(tooltip(entry), mouseX, mouseY)));
			cog.enabled = entry.configurable();
		}

		Button previous = addButton(new Button(contentLeft, controlsY, 80, 20,
				new TextComponentTranslation("button.orespawn.previous"),
				button -> { page--; rebuildWidgets(); }));
		Button next = addButton(new Button(contentLeft + contentWidth - 80, controlsY, 80, 20,
				new TextComponentTranslation("button.orespawn.next"),
				button -> { page++; rebuildWidgets(); }));
		previous.enabled = page > 0;
		next.enabled = page + 1 < pageCount;
		addButton(new Button(width / 2 - 75, height - 28, 150, 20,
				DialogTexts.GUI_DONE, button -> onClose()));
	}

	private void open(Entry entry) {
		if (!entry.configurable()) return;
		try {
			GuiScreen screen = entry.extension.createScreen(this);
			if (screen == null) {
				error = I18n.format("error.orespawn.mod.screen_missing", entry.modId);
				return;
			}
			WorldSettingsExtensionNavigation.open(this, screen);
		} catch (RuntimeException failure) {
			error = I18n.format("error.orespawn.mod.screen_failed", entry.modId,
					failure.getClass().getSimpleName());
		}
	}

	private List<String> tooltip(Entry entry) {
		List<String> lines = new ArrayList<>();
		lines.add(I18n.format(entry.configurable()
				? "tooltip.orespawn.mod.configure" : "tooltip.orespawn.mod.unavailable",
				entry.displayName));
		lines.add(I18n.format("label.orespawn.mod.id_version", entry.modId, entry.version));
		lines.add(integration(entry));
		for (Integer lineage : entry.legacyLineages()) {
			if (!entry.nativeOs4() && lineage.intValue() == primaryLegacy(entry)) continue;
			lines.add(I18n.format("label.orespawn.mod.additional_lineage", lineage));
		}
		lines.add(I18n.format("label.orespawn.mod.status",
				I18n.format("status.orespawn.mod." + entry.statusKey())));
		return lines;
	}

	private String integration(Entry entry) {
		if (entry.nativeOs4()) {
			if (entry.schemaVersion() > 0 && entry.providerRevision() > 0) {
				return I18n.format("label.orespawn.mod.os4_version",
						entry.schemaVersion(), entry.providerRevision());
			}
			return I18n.format("label.orespawn.mod.os4");
		}
		int legacy = primaryLegacy(entry);
		if (legacy > 0) {
			if (entry.schemaVersion() > 0 && entry.providerRevision() > 0) {
				return I18n.format("label.orespawn.mod.legacy_version", legacy,
						entry.schemaVersion(), entry.providerRevision());
			}
			return I18n.format("label.orespawn.mod.legacy", legacy);
		}
		return I18n.format("label.orespawn.mod.config_only");
	}

	private static int primaryLegacy(Entry entry) {
		List<Integer> lineages = entry.legacyLineages();
		return lineages.isEmpty() ? 0 : lineages.get(lineages.size() - 1);
	}

	private void rebuildWidgets() {
		buttons.clear();
		children.clear();
		init();
	}

	static int pageSize(int height) {
		return Math.max(1, ((height - 52) - 38) / ROW_HEIGHT);
	}

	static int pageCount(int entries, int pageSize) {
		return Math.max(1, (Math.max(0, entries) + Math.max(1, pageSize) - 1)
				/ Math.max(1, pageSize));
	}

	@Override
	public void onClose() {
		minecraft.displayGuiScreen(parent);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
		if (entries.isEmpty()) {
			drawCenteredString(font, new TextComponentTranslation("label.orespawn.mod.none"),
					width / 2, height / 2 - 6, 0xA0A0A0);
		}
		int start = page * pageSize;
		for (int index = 0; index < pageSize && start + index < entries.size(); index++) {
			drawEntry(entries.get(start + index), listTop + index * ROW_HEIGHT);
		}
		int pages = pageCount(entries.size(), pageSize);
		drawCenteredString(font, new TextComponentString((page + 1) + " / " + pages),
				width / 2, height - 46, 0xA0A0A0);
		if (error != null) {
			drawCenteredString(font, new TextComponentString(error), width / 2, 27, 0xFF5555);
		}
		super.render(mouseX, mouseY, partialTick);
	}

	private void drawEntry(Entry entry, int y) {
		drawRect(contentLeft, y, contentLeft + contentWidth, y + 38, 0x70000000);
		int textWidth = contentWidth - 36;
		drawString(font, new TextComponentString(trim(entry.displayName, textWidth)),
				contentLeft + 6, y + 3, 0xFFFFFF);
		drawString(font, new TextComponentString(trim(
				I18n.format("label.orespawn.mod.id_version", entry.modId, entry.version), textWidth)),
				contentLeft + 6, y + 14, 0xA0A0A0);
		String details = I18n.format("label.orespawn.mod.integration_status", integration(entry),
				I18n.format("status.orespawn.mod." + entry.statusKey()));
		drawString(font, new TextComponentString(trim(details, textWidth)),
				contentLeft + 6, y + 25, statusColor(entry.statusKey()));
	}

	private String trim(String value, int maximumWidth) {
		if (font.getStringWidth(value) <= maximumWidth) return value;
		String suffix = "...";
		int width = maximumWidth - font.getStringWidth(suffix);
		return font.trimStringToWidth(value, Math.max(0, width)) + suffix;
	}

	private static int statusColor(String status) {
		if ("active".equals(status)) return 0x55FF55;
		if ("pending".equals(status)) return 0xFFFF55;
		if ("rejected".equals(status)) return 0xFF5555;
		return 0xA0A0A0;
	}
}
