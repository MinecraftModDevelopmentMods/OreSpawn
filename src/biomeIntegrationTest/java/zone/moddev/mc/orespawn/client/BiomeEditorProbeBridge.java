package zone.moddev.mc.orespawn.client;

import com.google.gson.JsonObject;

import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;

/** Test-fixture bridge that persists an exact replacement through the real editor model. */
public final class BiomeEditorProbeBridge {
	private BiomeEditorProbeBridge() { }

	public static JsonObject replace(JsonObject root, String dimension,
			String source, String target, String unmanaged) {
		WorldGeologyProfile profile = WorldGeologyProfile.fromJson(root,
				WorldGeologyProfile.recommended(false));
		GeologyEditorSession session = new GeologyEditorSession(profile);
		BiomeDirectoryModel.Snapshot before = session.biomeDirectory();
		long providerPalettes = before.palettes(dimension).stream()
				.filter(palette -> palette.providerOwned).count();
		BiomeDirectoryModel.BiomeEntry external = before.entries(dimension, true).stream()
				.filter(entry -> unmanaged.equals(entry.id)).findFirst()
				.orElseThrow(() -> new IllegalStateException("Missing unmanaged fixture biome " + unmanaged));
		if (providerPalettes != 2 || external.status != BiomeDirectoryModel.Status.UNMANAGED) {
			throw new IllegalStateException("Biome directory did not expose two provider palettes "
					+ "and one unmanaged biome");
		}
		session.replaceBiome(dimension, source, target);
		JsonObject saved = session.profile().rootCopy();
		String actual = new GeologyEditorSession(WorldGeologyProfile.fromJson(saved, profile))
				.biomeReplacements(dimension).get(source);
		if (!target.equals(actual)) {
			throw new IllegalStateException("Biome editor replacement did not round-trip: "
					+ source + " -> " + actual);
		}
		BiomeDirectoryModel.BiomeEntry replaced = new GeologyEditorSession(
				WorldGeologyProfile.fromJson(saved, profile)).biomeDirectory()
				.entries(dimension, true).stream().filter(entry -> source.equals(entry.id))
				.findFirst().orElseThrow(() -> new IllegalStateException("Missing replaced biome " + source));
		if (replaced.status != BiomeDirectoryModel.Status.USER_REPLACED) {
			throw new IllegalStateException("Biome directory did not classify the replacement");
		}
		return saved;
	}
}
