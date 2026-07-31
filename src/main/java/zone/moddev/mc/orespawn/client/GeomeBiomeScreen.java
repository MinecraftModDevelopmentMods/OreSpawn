package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class GeomeBiomeScreen extends OreSpawnScreen {
	private enum Tab { GEOMES, BIOMES, DICTIONARY }
	private final Screen parent;
	private final GeologyEditorSession session;
	private Tab tab = Tab.GEOMES;
	private int page;
	private String searchText = "";
	private EditBox search;
	private EditBox newId;

	GeomeBiomeScreen(Screen parent, GeologyEditorSession session) {
		super(Component.translatable("screen.orespawn.geomes"));
		this.parent = parent;
		this.session = session;
	}

	@Override
	protected void init() {
		int left = width / 2 - 155;
		for (int i = 0; i < Tab.values().length; i++) {
			Tab value = Tab.values()[i];
			Button button = addRenderableWidget(OreSpawnScreenLayout.plainButton(left + (i * 105), 26, 100, 20,
					Component.translatable("tab.orespawn." + value.name().toLowerCase(Locale.ROOT)),
					selected -> { tab = value; page = 0; rebuildWidgets(); }));
			button.active = value != tab;
			OreSpawnScreenLayout.explain(button,
					"tooltip.orespawn.geome.tab." + value.name().toLowerCase(Locale.ROOT));
		}
		search = addRenderableWidget(new EditBox(font, left, 52, 230, 20,
				Component.translatable("option.orespawn.search")));
		search.setValue(searchText);
		addRenderableWidget(OreSpawnScreenLayout.plainButton(left + 235, 52, 75, 20,
				Component.translatable("button.orespawn.search"), button -> {
					searchText = search.getValue(); page = 0; rebuildWidgets();
				}));

		List<String> ids = ids();
		int listTop = 80;
		int controlsY = height - 52;
		int pageSize = Math.max(1, (controlsY - listTop) / 24);
		int pageCount = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * pageSize;
		for (int i = 0; i < pageSize && start + i < ids.size(); i++) {
			String id = ids.get(start + i);
			addRenderableWidget(OreSpawnScreenLayout.plainButton(left, listTop + (i * 24), 310, 20,
					Component.literal(label(id)), button -> open(id)));
		}
		Button previous = addRenderableWidget(OreSpawnScreenLayout.plainButton(left, controlsY, 45, 20,
				Component.literal("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addRenderableWidget(OreSpawnScreenLayout.plainButton(left + 50, controlsY, 45, 20,
				Component.literal(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;
		newId = addRenderableWidget(new EditBox(font, left + 100, controlsY, 150, 20,
				Component.translatable("option.orespawn.registry_id")));
		newId.setMaxLength(128);
		OreSpawnScreenLayout.explain(newId,
				"tooltip.orespawn.geome.new_id." + tab.name().toLowerCase(Locale.ROOT));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(left + 255, controlsY, 55, 20,
				Component.translatable("button.orespawn.add"), button -> add()));
		addRenderableWidget(OreSpawnScreenLayout.plainButton(width / 2 - 75, height - 28, 150, 20,
				CommonComponents.GUI_DONE, button -> onClose()));
	}

	private List<String> ids() {
		List<String> source;
		if (tab == Tab.GEOMES) source = session.geomeIds();
		else if (tab == Tab.BIOMES) source = session.configuredBiomeIds();
		else source = session.dictionaryIds();
		if (searchText.isEmpty()) return source;
		List<String> filtered = new ArrayList<>();
		String query = searchText.toLowerCase(Locale.ROOT);
		for (String id : source) if (id.toLowerCase(Locale.ROOT).contains(query)) filtered.add(id);
		return filtered;
	}

	protected void rebuildWidgets() {
		clearWidgets();
		init();
	}

	private String label(String id) {
		if (tab == Tab.BIOMES && !session.section("biomes").has(id)) return id + "  [installed]";
		return id;
	}

	private void open(String id) {
		if (tab == Tab.GEOMES) {
			minecraft.setScreen(new GeomeEntryScreen(this, session, id));
			return;
		}
		String section = tab == Tab.BIOMES ? "biomes" : "biome_dictionary";
		JsonObject weights = session.weightMap(section, id);
		minecraft.setScreen(new WeightMapScreen(this, Component.literal(id), weights,
				session.geomeIds(), 0.0D, () -> session.section(section).remove(id)));
	}

	private void add() {
		String id = newId.getValue().trim();
		if (id.isEmpty()) return;
		if (tab == Tab.GEOMES) {
			session.addGeome(id);
			minecraft.setScreen(new GeomeEntryScreen(this, session, id.toLowerCase(Locale.ROOT)));
		} else {
			if (tab == Tab.BIOMES) {
				try { new ResourceLocation(id); }
				catch (RuntimeException e) { return; }
			}
			open(id);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.drawCenteredString(font, title, width / 2, 10, OreSpawnScreenLayout.TEXT_PRIMARY);
	}
}
