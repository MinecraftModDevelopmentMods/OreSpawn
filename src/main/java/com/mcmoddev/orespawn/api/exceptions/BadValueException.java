package com.mcmoddev.orespawn.api.exceptions;

public class BadValueException extends Exception {

	private static final long serialVersionUID = 1143938140559149506L;
	private final String keyName;
	private final String keyValue;

	public BadValueException(final String keyName, final String keyValue) {
		super();
		this.keyName = keyName;
		this.keyValue = keyValue;
	}

	@Override
	public String getMessage() {
		final String baseMessage = super.getMessage();
		return String.format("Unknown value %s for key %s%n%s", this.keyValue, this.keyName,
				baseMessage);
	}
}
