package org.taverna.server.master.rest;

import static javax.ws.rs.core.Response.Status.FORBIDDEN;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.taverna.server.master.exceptions.NoUpdateException;

@Provider
public class NoUpdateHandler extends HandlerCore implements
		ExceptionMapper<NoUpdateException> {
	@Override
	public Response toResponse(NoUpdateException exn) {
		return respond(FORBIDDEN, exn);
	}
}