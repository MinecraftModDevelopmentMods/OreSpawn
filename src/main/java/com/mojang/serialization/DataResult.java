package com.mojang.serialization;

import java.util.Optional;

/** Minimal result type paired with the 1.12 OreSpawn codec adapter. */
public final class DataResult<A> {
	private final A value;
	private final PartialResult<A> error;

	private DataResult(A value, PartialResult<A> error) {
		this.value = value;
		this.error = error;
	}

	public static <A> DataResult<A> success(A value) {
		return new DataResult<>(value, null);
	}

	public static <A> DataResult<A> error(String message) {
		return new DataResult<>(null, new PartialResult<>(message));
	}

	public Optional<A> result() {
		return Optional.ofNullable(value);
	}

	public Optional<PartialResult<A>> error() {
		return Optional.ofNullable(error);
	}

	public static final class PartialResult<A> {
		private final String message;

		private PartialResult(String message) {
			this.message = message == null ? "unknown codec error" : message;
		}

		@Override
		public String toString() {
			return message;
		}
	}
}
