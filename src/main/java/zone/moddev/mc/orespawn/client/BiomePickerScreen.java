package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

/** Registry-backed biome chooser. */
final class BiomePickerScreen extends Screen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private final Consumer<String> select;
	private String searchText = "";
	private int page;
	private EditBox search;

	BiomePickerScreen(Screen parent, GeologyEditorSession session, Consumer<String> select) {
		super(new TranslatableComponent("screen.orespawn.choose_biome"));
		this.parent = parent;
		this.session = session;
		this.select = select;
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
					searchText = search.getValue();
					page = 0;
					rebuildWidgets();
				}));
		List<String> ids = filtered();
		int listTop = 64;
		int controlsY = height - 52;
		int pageSize = Math.max(1, (controlsY - listTop) / 24);
		int pageCount = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * pageSize;
		for (int i = 0; i < pageSize && start + i < ids.size(); i++) {
			String id = ids.get(start + i);
			addRenderableWidget(OreSpawnScreenLayout.button(this, font, left,
					listTop + i * 24, contentWidth, 20, new TextComponent(id),
					button -> select.accept(id)));
		}
		Button previous = addRenderableWidget(new Button(left, controlsY, 45, 20,
				new TextComponent("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addRenderableWidget(new Button(left + 50, controlsY, 45, 20,
				new TextComponent(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;
		addRenderableWidget(new Button(width / 2 - 75, height - 28, 150, 20,
				CommonComponents.GUI_CANCEL, button -> onClose()));
	}

	private List<String> filtered() {
		if (searchText.trim().isEmpty()) return session.installedBiomeIds();
		String query = searchText.trim().toLowerCase(Locale.ROOT);
		List<String> result = new ArrayList<>();
		for (String id : session.installedBiomeIds()) {
			if (id.toLowerCase(Locale.ROOT).contains(query)) result.add(id);
		}
		return result;
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
