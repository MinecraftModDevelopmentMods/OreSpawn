package zone.moddev.mc.orespawn.clientprobe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import zone.moddev.mc.orespawn.worldgen.GeomeConfig;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;

/** Keeps test access separate from OreSpawn's packages in Forge 40's module layer. */
final class SharedOreClientProbe {
	private SharedOreClientProbe() { }

	static void verify() {
		try {
			Class<?> editor = Class.forName("zone.moddev.mc.orespawn.client.GeologyEditorSession");
			java.lang.reflect.Constructor<?> constructor = editor.getDeclaredConstructor(WorldGeologyProfile.class);
			constructor.setAccessible(true);
			Object session = constructor.newInstance(GeomeConfig.globalProfile());
			Method groups = editor.getDeclaredMethod("oreSourceGroups");
			groups.setAccessible(true);
			for (Object group : (List<?>) groups.invoke(session)) {
				if (!"orespawn:sulfur".equals(field(group, "material"))) continue;
				Set<String> owners = new HashSet<>();
				for (Object candidate : (List<?>) field(group, "candidates")) {
					owners.add((String) field(candidate, "owner"));
				}
				Method outputs = group.getClass().getDeclaredMethod("outputCandidates");
				outputs.setAccessible(true);
				if (!owners.contains("baseminerals") || !owners.contains("electricadvantage")
						|| ((List<?>) outputs.invoke(group)).size() != 3) {
					throw new IllegalStateException("The profile must expose both providers and all three outputs");
				}
				System.out.println("ORE_SOURCES_CLIENT_PROBE providers=2 outputs=3 verified=true");
				return;
			}
			throw new IllegalStateException("Shared Sulfur group is missing");
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException("Could not inspect the shared-ore editor", failure);
		}
	}

	private static Object field(Object object, String name) throws ReflectiveOperationException {
		Field field = object.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(object);
	}
}
