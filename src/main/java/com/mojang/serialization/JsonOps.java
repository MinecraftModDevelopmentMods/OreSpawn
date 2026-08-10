package com.mojang.serialization;

/** JSON operations marker used by the 1.14 OreSpawn codec adapter. */
public final class JsonOps {
	public static final JsonOps INSTANCE = new JsonOps();

	private JsonOps() {
	}
}
