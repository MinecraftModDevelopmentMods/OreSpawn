package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/** Registry-backed multi-select list for similar-biome references. */
final class BiomeReferenceScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final JsonObject placement;
	private final String key;
	private String searchText = "";
	private int page;
	private EditBox search;

	BiomeReferenceScreen(Screen parent, GeologyEditorSession session,
			JsonObject placement, String key) {
		super(new TranslatableComponent("screen.orespawn.biome_references"));
		this.parent = parent;
		this.session = session;
		this.placement = placement;
		this.key = key;
	}

	@Override
	protected void init() {
		int contentWidth = Math.min(390, Math.max(280, width - 24));
		int left = (width - contentWidth) / 2;
		search = addRenderableWidget(new EditBox(font, left, 36, contentWidth - 75, 20,
				new TranslatableComponent("option.orespawn.search")));
		search.setValue(searchText);
		addRenderableWidget(new Button(left + contentWidth - 70, 36, 70, 20,
				new TranslatableComponent("button.orespawn.search"), button -> {
					searchText = search.getValue(); page = 0; rebuildWidgets();
				}));
		List<String> ids = filtered();
		Set<String> selected = selected();
		int listTop = 64;
		int controlsY = height - 52;
		int pageSize = Math.max(1, (controlsY - listTop) / 24);
		int pageCount = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * pageSize;
		for (int i = 0; i < pageSize && start + i < ids.size(); i++) {
			String id = ids.get(start + i);
			TranslatableComponent label = new TranslatableComponent(
					selected.contains(id) ? "button.orespawn.biome_selected"
							: "button.orespawn.biome_available", id);
			addRenderableWidget(OreSpawnScreenLayout.button(this, font, left,
					listTop + i * 24, contentWidth, 20, label,
					button -> { toggle(id); rebuildWidgets(); }));
		}
		Button previous = addRenderableWidget(new Button(left, controlsY, 45, 20,
				new TextComponent("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addRenderableWidget(new Button(left + 50, controlsY, 45, 20,
				new TextComponent(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;
		addRenderableWidget(new Button(width / 2 - 75, height - 28, 150, 20,
				CommonComponents.GUI_DONE, button -> onClose()));
	}

	private List<String> filtered() {
		String query = searchText.trim().toLowerCase(Locale.ROOT);
		List<String> result = new ArrayList<>();
		for (String id : session.installedBiomeIds()) {
			if (query.isEmpty() || id.toLowerCase(Locale.ROOT).contains(query)) result.add(id);
		}
		return result;
	}

	private Set<String> selected() {
		Set<String> result = new HashSet<>();
		for (JsonElement value : array()) result.add(value.getAsString());
		return result;
	}

	private void toggle(String id) {
		Set<String> selected = selected();
		if (!selected.add(id)) selected.remove(id);
		JsonArray array = new JsonArray();
		selected.stream().sorted().forEach(array::add);
		placement.add(key, array);
	}

	private JsonArray array() {
		if (!placement.has(key) || !placement.get(key).isJsonArray()) placement.add(key, new JsonArray());
		return placement.getAsJsonArray(key);
	}

	private void rebuildWidgets() { clearWidgets(); init(); }
	@Override public void onClose() { minecraft.setScreen(parent); }

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 14, 0xFFFFFF);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}
}
