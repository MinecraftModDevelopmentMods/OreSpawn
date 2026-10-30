package zone.moddev.mc.orespawn.api.client;

import java.util.Objects;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/** Immutable registered action displayed by OreSpawn's world-settings screen. */
public final class WorldSettingsExtension {
	private final Identifier id;
	private final String buttonTranslationKey;
	private final WorldSettingsScreenFactory screenFactory;

	WorldSettingsExtension(Identifier id, String buttonTranslationKey,
			WorldSettingsScreenFactory screenFactory) {
		this.id = Objects.requireNonNull(id, "id");
		this.buttonTranslationKey = Objects.requireNonNull(buttonTranslationKey,
				"buttonTranslationKey");
		this.screenFactory = Objects.requireNonNull(screenFactory, "screenFactory");
	}

	public Identifier id() { return id; }
	public String buttonTranslationKey() { return buttonTranslationKey; }

	/** Creates the extension screen with OreSpawn's Mods directory as its parent. */
	public Screen createScreen(Screen parent) {
		return screenFactory.create(Objects.requireNonNull(parent, "parent"));
	}
}
