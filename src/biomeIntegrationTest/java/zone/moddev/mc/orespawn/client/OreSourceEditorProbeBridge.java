package zone.moddev.mc.orespawn.client;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonObject;

import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;

/** Test-fixture bridge that drives the real pending editor model on a server. */
public final class OreSourceEditorProbeBridge {
	private static final String DOMAIN = "minecraft:overworld";
	private static final String CHANNEL = "orespawn:standard";

	private OreSourceEditorProbeBridge() { }

	public static JsonObject configure(JsonObject root) {
		WorldGeologyProfile profile = WorldGeologyProfile.fromJson(root,
				WorldGeologyProfile.recommended(false));
		GeologyEditorSession session = new GeologyEditorSession(profile);

		configureBalanced(session, "surfaceprobe:balanced", "surfaceprobe:balanced_a");
		configureSingle(session, "surfaceprobe:single", "surfaceprobe:single_b",
				"surfaceprobe:single_a");
		configureCustom(session, "surfaceprobe:custom", "surfaceprobe:custom_a",
				"surfaceprobe:custom_b");
		configureBalanced(session, "surfaceprobe:body", "surfaceprobe:body_a");

		GeologyEditorSession.OreSourceGroup original = group(session, "surfaceprobe:original");
		session.acceptOreSourcePolicy(original.key);
		assertGroup(group(session, "surfaceprobe:original"), "keep_separate", 2,
				map("surfaceprobe:original_a", 1.0D, "surfaceprobe:original_b", 1.0D));
		return session.profile().rootCopy();
	}

	public static JsonObject reset(JsonObject root) {
		WorldGeologyProfile profile = WorldGeologyProfile.fromJson(root,
				WorldGeologyProfile.recommended(false));
		GeologyEditorSession session = new GeologyEditorSession(profile);
		session.resetOreSourcesToDefaults();
		for (String material : new String[] { "surfaceprobe:balanced", "surfaceprobe:single",
				"surfaceprobe:custom", "surfaceprobe:original", "surfaceprobe:body" }) {
			GeologyEditorSession.OreSourceGroup group = group(session, material);
			if (!"keep_separate".equals(group.mode) || group.outputCandidates().size() != 2) {
				throw new IllegalStateException("Reset All did not restore Keep Original for "
						+ material + ": mode=" + group.mode + ", candidates="
						+ group.outputCandidates().size());
			}
		}
		return session.profile().rootCopy();
	}

	private static void configureBalanced(GeologyEditorSession session, String material,
			String placement) {
		GeologyEditorSession.OreSourceGroup group = group(session, material);
		session.setOreSourceOutputMode(group.key, "balanced");
		session.setOreSourcePlacement(group.key, CHANNEL, placement);
		assertGroup(group(session, material), "consolidated", 2,
				map(material + "_a", 1.0D, material + "_b", 1.0D));
	}

	private static void configureSingle(GeologyEditorSession session, String material,
			String selected, String placement) {
		GeologyEditorSession.OreSourceGroup group = group(session, material);
		session.setOreSourceOutputMode(group.key, "single");
		session.setOreSourceOutput(group.key, selected, true, 1.0D);
		session.setOreSourcePlacement(group.key, CHANNEL, placement);
		assertGroup(group(session, material), "consolidated", 1,
				map(selected, 1.0D));
	}

	private static void configureCustom(GeologyEditorSession session, String material,
			String first, String second) {
		GeologyEditorSession.OreSourceGroup group = group(session, material);
		session.setOreSourceOutputMode(group.key, "custom");
		session.setOreSourceOutput(group.key, first, true, 1.0D);
		session.setOreSourceOutput(group.key, second, true, 3.0D);
		session.setOreSourcePlacement(group.key, CHANNEL, first);
		assertGroup(group(session, material), "consolidated", 2,
				map(first, 1.0D, second, 3.0D));
	}

	private static void assertGroup(GeologyEditorSession.OreSourceGroup group, String mode,
			int outputCount, Map<String, Double> outputs) {
		if (!mode.equals(group.mode) || group.outputs.size() != outputCount
				|| !outputs.equals(group.outputs)) {
			throw new IllegalStateException("Unexpected editor policy for " + group.material
					+ ": mode=" + group.mode + ", outputs=" + group.outputs);
		}
		if ("consolidated".equals(mode)
				&& !group.placements.containsKey(CHANNEL)) {
			throw new IllegalStateException("Missing Standard veins placement for " + group.material);
		}
	}

	private static GeologyEditorSession.OreSourceGroup group(GeologyEditorSession session,
			String material) {
		for (GeologyEditorSession.OreSourceGroup group : session.oreSourceGroups()) {
			if (material.equals(group.material) && DOMAIN.equals(group.domain)) return group;
		}
		throw new IllegalStateException("Missing editor material group " + material);
	}

	private static Map<String, Double> map(String first, double firstWeight,
			String second, double secondWeight) {
		Map<String, Double> result = map(first, firstWeight);
		result.put(second, Double.valueOf(secondWeight));
		return result;
	}

	private static Map<String, Double> map(String source, double weight) {
		Map<String, Double> result = new LinkedHashMap<>();
		result.put(source, Double.valueOf(weight));
		return result;
	}
}
