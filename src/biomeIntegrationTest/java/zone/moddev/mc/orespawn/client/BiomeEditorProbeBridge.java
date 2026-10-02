package zone.moddev.mc.orespawn.client;

import com.google.gson.JsonObject;

import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;

/** Runs the saved-world biome edit through the same session used by the GUI. */
public final class BiomeEditorProbeBridge {
	private BiomeEditorProbeBridge() { }

	public static JsonObject replace(JsonObject root, String dimension,
			String source, String target) {
		WorldGeologyProfile profile = WorldGeologyProfile.fromJson(root,
				WorldGeologyProfile.recommended(false));
		GeologyEditorSession session = new GeologyEditorSession(profile);
		if (session.biomeDirectory().palettes(dimension).isEmpty()) {
			throw new IllegalStateException("The fixture's biome palette is missing");
		}
		session.replaceBiome(dimension, source, target);
		JsonObject saved = session.profile().rootCopy();
		GeologyEditorSession reopened = new GeologyEditorSession(
				WorldGeologyProfile.fromJson(saved, profile));
		if (!target.equals(reopened.biomeReplacements(dimension).get(source))) {
			throw new IllegalStateException("Biome replacement did not survive the editor round trip");
		}
		boolean replaced = reopened.biomeDirectory().entries(dimension, true).stream()
				.anyMatch(entry -> source.equals(entry.id)
						&& entry.status == BiomeDirectoryModel.Status.USER_REPLACED);
		if (!replaced) throw new IllegalStateException("Biome directory did not show the replacement");
		return saved;
	}
}
