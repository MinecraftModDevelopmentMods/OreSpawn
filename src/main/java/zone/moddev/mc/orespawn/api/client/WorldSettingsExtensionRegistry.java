package zone.moddev.mc.orespawn.api.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client-only registry for add-on screens in OreSpawn's Mods directory. */
@OnlyIn(Dist.CLIENT)
public final class WorldSettingsExtensionRegistry {
	private static final Pattern MOD_ID = Pattern.compile("^[a-z][a-z0-9_.-]{1,63}$");
	private static final Map<String, WorldSettingsExtension> EXTENSIONS =
			new LinkedHashMap<>();

	private WorldSettingsExtensionRegistry() {
	}

	/**
	 * Registers an installed mod's configuration screen in OreSpawn.
	 * Each mod may register one screen. Call during client initialization.
	 *
	 * @throws IllegalArgumentException if the mod identifier is not canonical
	 * @throws IllegalStateException if the mod already registered a screen
	 */
	public static synchronized WorldSettingsExtension registerConfigScreen(String modId,
			WorldSettingsScreenFactory screenFactory) {
		validateModId(modId);
		return registerOwned(modId, new ResourceLocation(modId, "configuration"),
				"button.orespawn.mod.configure", screenFactory);
	}

	/**
	 * Registers an add-on screen using the original translated-label overload.
	 * The identifier's namespace owns the entry. Call during client initialization.
	 *
	 * @throws IllegalStateException if the identifier is already registered
	 */
	public static synchronized WorldSettingsExtension register(ResourceLocation id,
			String buttonTranslationKey, WorldSettingsScreenFactory screenFactory) {
		Objects.requireNonNull(id, "id");
		if (buttonTranslationKey == null || buttonTranslationKey.trim().isEmpty()) {
			throw new IllegalArgumentException("buttonTranslationKey cannot be blank");
		}
		String modId = id.getNamespace();
		validateModId(modId);
		return registerOwned(modId, id, buttonTranslationKey, screenFactory);
	}

	private static WorldSettingsExtension registerOwned(String modId, ResourceLocation id,
			String buttonTranslationKey, WorldSettingsScreenFactory screenFactory) {
		Objects.requireNonNull(screenFactory, "screenFactory");
		if (EXTENSIONS.containsKey(modId)) {
			throw new IllegalStateException("Duplicate OreSpawn configuration screen for mod: " + modId);
		}
		WorldSettingsExtension extension = new WorldSettingsExtension(id, buttonTranslationKey,
				screenFactory);
		EXTENSIONS.put(modId, extension);
		return extension;
	}

	private static void validateModId(String modId) {
		if (modId == null || !MOD_ID.matcher(modId).matches()) {
			throw new IllegalArgumentException("modId must be a canonical lower-case mod identifier");
		}
	}

	/** Returns a read-only snapshot in registration order. */
	public static synchronized List<WorldSettingsExtension> extensions() {
		return Collections.unmodifiableList(new ArrayList<>(EXTENSIONS.values()));
	}
}
