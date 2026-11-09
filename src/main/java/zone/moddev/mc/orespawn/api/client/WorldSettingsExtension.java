package zone.moddev.mc.orespawn.api.client;

import java.util.Objects;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Immutable registered action displayed by OreSpawn's world-settings screen. */
@SideOnly(Side.CLIENT)
public final class WorldSettingsExtension {
	private final ResourceLocation id;
	private final String buttonTranslationKey;
	private final WorldSettingsScreenFactory screenFactory;

	WorldSettingsExtension(ResourceLocation id, String buttonTranslationKey,
			WorldSettingsScreenFactory screenFactory) {
		this.id = Objects.requireNonNull(id, "id");
		this.buttonTranslationKey = Objects.requireNonNull(buttonTranslationKey,
				"buttonTranslationKey");
		this.screenFactory = Objects.requireNonNull(screenFactory, "screenFactory");
	}

	public ResourceLocation id() { return id; }
	public String buttonTranslationKey() { return buttonTranslationKey; }

	/** Creates the extension screen with OreSpawn's Mods directory as its parent. */
	public GuiScreen createScreen(GuiScreen parent) {
		return screenFactory.create(Objects.requireNonNull(parent, "parent"));
	}
}
