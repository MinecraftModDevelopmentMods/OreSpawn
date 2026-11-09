package zone.moddev.mc.orespawn.api.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Client-only registry for add-on screens in OreSpawn's Mods directory. */
@SideOnly(Side.CLIENT)
public final class WorldSettingsExtensionRegistry {
	private static final Pattern MOD_ID = Pattern.compile("^[a-z][a-z0-9_.-]{1,63}$");
	private static final Map<String, WorldSettingsExtension> EXTENSIONS =
			new LinkedHashMap<>();

	private WorldSettingsExtensionRegistry() {
	}

	/**
	 * Registers the one optional OreSpawn-linked configuration screen owned by a
	 * loaded mod. Registration must occur during client initialization.
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
	 * Retains the original translated world-settings registration descriptor for
	 * existing add-ons. The identifier namespace owns the one permitted screen.
	 * Registration must occur during client initialization.
	 *
	 * @throws IllegalStateException if the identifier is already registered
	 */
	public static synchronized WorldSettingsExtension register(ResourceLocation id,
			String buttonTranslationKey, WorldSettingsScreenFactory screenFactory) {
		Objects.requireNonNull(id, "id");
		if (buttonTranslationKey == null || buttonTranslationKey.trim().isEmpty()) {
			throw new IllegalArgumentException("buttonTranslationKey cannot be blank");
		}
		String modId = id.getResourceDomain();
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

	/** Returns an immutable snapshot in deterministic registration order. */
	public static synchronized List<WorldSettingsExtension> extensions() {
		return Collections.unmodifiableList(new ArrayList<>(EXTENSIONS.values()));
	}
}
