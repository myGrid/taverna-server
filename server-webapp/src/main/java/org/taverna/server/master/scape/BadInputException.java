package org.taverna.server.master.scape;

@SuppressWarnings("serial")
public class BadInputException extends RuntimeException {
	public BadInputException(String message) {
		super(message);
	}

	public BadInputException(String message, Throwable t) {
		super(message, t);
	}
}