package com.mcmoddev.orespawn.utils.codecs;

import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class AllowDenyListBase<T extends RegistryKey> {
	private final List<T> listed = new LinkedList<>();
	private final ResourceLocation type;
	private final T baseKey;

	private static final ResourceLocation whitelist = new ResourceLocation("orespawn4", "allowlist");
	private static final ResourceLocation blacklist = new ResourceLocation("orespawn4", "denylist");
	private static final ResourceLocation denyall = new ResourceLocation("orespawn4", "denyall");
	private static final ResourceLocation allowall = new ResourceLocation("orespawn4", "allowall");

	protected AllowDenyListBase(final ResourceLocation matchType, final T key, List<ResourceLocation> baseList) {
		this.type = matchType;
		this.baseKey = key;
		listed.addAll((List<T>) baseList.stream().map( rl -> (T) RegistryKey.getOrCreateKey(this.baseKey, rl)).collect(Collectors.toList()));
	}

	public boolean matches(final String biomeName) {
		if (type.equals(denyall) || (listed.isEmpty() && type.equals(whitelist))) return false;
		else if (type.equals(allowall) || (listed.isEmpty() && type.equals(blacklist))) return true;
		else return matches(makeResourceLocation(biomeName));
	}

	private ResourceLocation makeResourceLocation(final String name) {
		String namespace = "minecraft";
		String biomeId = name;

		if (name.indexOf(':') > -1) {
			String[] bits = name.split(":");
			namespace = bits[0];
			biomeId = bits[1];
		}

		return new ResourceLocation(namespace, biomeId);
	}

	public boolean matches(final ResourceLocation biomeName) {
		return matches((T)RegistryKey.getOrCreateKey(this.baseKey, biomeName));
	}

	private boolean matches(final T item) {
		if (type == blacklist && listed.contains(item)) return false;
		return listed.contains(item);
	}
}
