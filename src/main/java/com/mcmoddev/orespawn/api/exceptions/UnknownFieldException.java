package com.mcmoddev.orespawn.api.exceptions;

public class UnknownFieldException extends Exception {
	private static final long serialVersionUID = 1L;
	private final String message;

	public UnknownFieldException(String theField) {
		super();
		this.message = String.format("Unkown field %s in config", theField);
	}
	
	@Override
	public String getMessage() {
		String baseMessage = super.getMessage();
		return String.format("%s%n%s", this.message, baseMessage);
	}
}
