package com.mcmoddev.orespawn.api.exceptions;

public class UnknownNameException extends Exception {

	private static final long serialVersionUID = -3426121906665390773L;
	private final String fieldName;
	private final String fieldValue;

	public UnknownNameException(String fieldName, String fieldValue) {
		super();
		this.fieldName = fieldName;
		this.fieldValue = fieldValue;
	}

	@Override
	public String getMessage() {
		String baseMessage = super.getMessage();
		return String.format("Unknown %s name %s%n%s", this.fieldName, this.fieldValue,
				baseMessage);
	}
}
