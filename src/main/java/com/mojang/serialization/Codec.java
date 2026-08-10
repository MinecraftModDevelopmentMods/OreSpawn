package com.mojang.serialization;

import java.util.Objects;
import java.util.function.Function;

import com.google.gson.JsonElement;

/**
 * Small 1.14 compatibility surface for OreSpawn's codec-backed public pattern
 * contract. Minecraft 1.14 predates Mojang's serialization package, so this
 * target supplies only the JSON decode operation used by OreSpawn profiles.
 */
public abstract class Codec<A> {
	public abstract DataResult<A> parse(JsonOps operations, JsonElement input);

	public static <A> Codec<A> of(Function<JsonElement, A> decoder) {
		Objects.requireNonNull(decoder, "decoder");
		return new Codec<A>() {
			@Override
			public DataResult<A> parse(JsonOps operations, JsonElement input) {
				try {
					return DataResult.success(decoder.apply(input));
				} catch (RuntimeException exception) {
					return DataResult.error(exception.getMessage() == null
							? exception.getClass().getSimpleName() : exception.getMessage());
				}
			}
		};
	}
}
