package org.taverna.server.master.rest;

import static javax.ws.rs.core.Response.Status.FORBIDDEN;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.taverna.server.master.exceptions.BadStateChangeException;

@Provider
public class BadStateChangeHandler extends HandlerCore implements
		ExceptionMapper<BadStateChangeException> {
	@Override
	public Response toResponse(BadStateChangeException exn) {
		return respond(FORBIDDEN, exn);
	}
}