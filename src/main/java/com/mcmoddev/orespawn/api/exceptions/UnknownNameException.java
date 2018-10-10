package com.mcmoddev.orespawn.api.exceptions;

import java.util.Locale;

public class UnknownNameException extends Exception {

	private static final long serialVersionUID = -3426121906665390773L;
	private final String fieldName;
	private final String fieldValue;

	public UnknownNameException(final String fieldName, final String fieldValue) {
		super();
		this.fieldName = fieldName;
		this.fieldValue = fieldValue;
	}

	@Override
	public String getMessage() {
		final String baseMessage = super.getMessage();
		return String.format(Locale.ENGLISH, "Unknown %s name %s%n%s", this.fieldName, this.fieldValue,
				baseMessage);
	}
}
