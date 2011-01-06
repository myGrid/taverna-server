package org.taverna.server.master.rest;

import static javax.ws.rs.core.Response.Status.FORBIDDEN;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.taverna.server.master.exceptions.BadPropertyValueException;

@Provider
public class BadPropertyValueHandler extends HandlerCore implements
		ExceptionMapper<BadPropertyValueException> {
	@Override
	public Response toResponse(BadPropertyValueException exn) {
		return respond(FORBIDDEN, exn);
	}
}