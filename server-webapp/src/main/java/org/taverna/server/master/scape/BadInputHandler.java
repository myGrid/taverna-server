/*
 * Copyright (C) 2011 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.master.scape;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import org.taverna.server.master.rest.handler.HandlerCore;

public class BadInputHandler extends HandlerCore implements
		ExceptionMapper<BadInputException> {
	@Override
	public Response toResponse(BadInputException exn) {
		return respond(BAD_REQUEST, exn);
	}
}
