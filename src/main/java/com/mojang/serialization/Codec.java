package com.mojang.serialization;

import java.util.Objects;
import java.util.function.Function;

import com.google.gson.JsonElement;

/**
 * Supplies the JSON decoding needed by OreSpawn's public pattern API.
 * Minecraft 1.13 has no Mojang serialization package, so this is only the subset used by profiles.
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
