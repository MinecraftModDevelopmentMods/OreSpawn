package zone.moddev.mc.orespawn.api.client;

import java.util.Objects;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** An add-on's registered entry in OreSpawn's Mods directory. */
@OnlyIn(Dist.CLIENT)
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
	public Screen createScreen(Screen parent) {
		return screenFactory.create(Objects.requireNonNull(parent, "parent"));
	}
}
