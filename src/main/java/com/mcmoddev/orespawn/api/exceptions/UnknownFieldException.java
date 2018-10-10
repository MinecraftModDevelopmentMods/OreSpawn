package com.mcmoddev.orespawn.api.exceptions;

import java.util.Locale;

public class UnknownFieldException extends Exception {

	private static final long serialVersionUID = 1L;
	private final String message;

	public UnknownFieldException(final String theField) {
		super();
		this.message = String.format(Locale.ENGLISH, "Unkown field %s in config", theField);
	}

	@Override
	public String getMessage() {
		final String baseMessage = super.getMessage();
		return String.format(Locale.ENGLISH, "%s%n%s", this.message, baseMessage);
	}
}
