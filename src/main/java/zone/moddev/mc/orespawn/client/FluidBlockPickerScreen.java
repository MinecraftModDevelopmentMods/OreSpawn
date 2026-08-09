package zone.moddev.mc.orespawn.client;

import java.util.List;

import com.mojang.blaze3d.matrix.MatrixStack;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DialogTexts;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

final class FluidBlockPickerScreen extends OreSpawnScreen {
	private final Screen parent;
	private final GeologyEditorSession session;
	private TextFieldWidget search;
	private String searchText = "";
	private int page;

	FluidBlockPickerScreen(Screen parent, GeologyEditorSession session) {
		super(new TranslationTextComponent("screen.orespawn.choose_block"));
		this.parent = parent;
		this.session = session;
	}

	@Override
	protected void init() {
		int contentWidth = Math.min(390, Math.max(260, width - 24));
		int left = (width - contentWidth) / 2;
		int searchButtonWidth = 70;
		search = addButton(new TextFieldWidget(font, left, 40,
				contentWidth - searchButtonWidth - 5, 20,
				new TranslationTextComponent("option.orespawn.search")));
		search.setValue(searchText);
		addButton(OreSpawnScreenLayout.button(this, font,
				left + contentWidth - searchButtonWidth, 40, searchButtonWidth, 20,
				new TranslationTextComponent("button.orespawn.search"), button -> {
					searchText = search.getValue();
					page = 0;
					rebuildWidgets();
				}));

		List<String> ids = session.availableFluidBlockIds(searchText);
		int listTop = 68;
		int controlsY = height - 52;
		int pageSize = Math.max(1, (controlsY - listTop) / 24);
		int pageCount = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
		page = Math.max(0, Math.min(page, pageCount - 1));
		int start = page * pageSize;
		for (int i = 0; i < pageSize && start + i < ids.size(); i++) {
			String id = ids.get(start + i);
			addButton(new Button(left, listTop + (i * 24), contentWidth, 20,
					OreSpawnScreenLayout.fit(font, fluidName(id), contentWidth - 8),
					button -> choose(id),
					(button, poseStack, mouseX, mouseY) -> renderComponentTooltip(
							poseStack, java.util.Collections.singletonList(new StringTextComponent(id)),
							mouseX, mouseY)));
		}

		Button previous = addButton(new Button(left, controlsY, 45, 20,
				new StringTextComponent("<"), button -> { page--; rebuildWidgets(); }));
		Button next = addButton(new Button(left + 50, controlsY, 45, 20,
				new StringTextComponent(">"), button -> { page++; rebuildWidgets(); }));
		previous.active = page > 0;
		next.active = page + 1 < pageCount;
		addButton(new Button(width / 2 - 75, height - 28, 150, 20,
				DialogTexts.GUI_CANCEL, button -> onClose()));
	}

	private void choose(String blockId) {
		String ruleId = session.assignFluidDeposit(blockId);
		if (ruleId != null) {
			minecraft.setScreen(new FluidDepositEntryScreen(parent, session, ruleId));
		}
	}

	private ITextComponent fluidName(String id) {
		try {
			Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
			return block == null ? new StringTextComponent(id) : new TranslationTextComponent(block.getTranslationKey());
		} catch (RuntimeException ignored) {
			return new StringTextComponent(id);
		}
	}

	private void rebuildWidgets() {
		buttons.clear(); children.clear();
		init();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void render(MatrixStack poseStack, int mouseX, int mouseY, float partialTick) {
		renderBackground(poseStack);
		drawCenteredString(poseStack, font, title, width / 2, 18, 0xFFFFFF);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}
}
