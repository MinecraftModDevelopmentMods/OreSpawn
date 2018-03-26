package com.mcmoddev.orespawn.api.exceptions;

public class BadValueException extends Exception {
	private static final long serialVersionUID = 1143938140559149506L;
	private String keyName;
	private String keyValue;
	
	public BadValueException(String keyName, String keyValue) {
		super();
		this.keyName = keyName;
		this.keyValue = keyValue;
	}
	
	@Override
	public String getMessage() {
		String baseMessage = super.getMessage();
		String fullMessage = String.format("Unknown value %s for key %s\n%s", this.keyValue, this.keyName, baseMessage);
		return fullMessage;
	}
}
