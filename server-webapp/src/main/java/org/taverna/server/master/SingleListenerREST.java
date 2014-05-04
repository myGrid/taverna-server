/*
 * Copyright (C) 2010-2011 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.master;

import static java.util.Arrays.asList;
import static org.taverna.server.master.common.Uri.secure;
import static org.taverna.server.master.utils.RestUtils.opt;

import java.util.List;

import javax.annotation.Nonnull;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.taverna.server.master.api.OneListenerBean;
import org.taverna.server.master.exceptions.NoListenerException;
import org.taverna.server.master.interfaces.Listener;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.rest.TavernaServerListenersREST;
import org.taverna.server.master.rest.TavernaServerListenersREST.ListenerDescription;
import org.taverna.server.master.rest.TavernaServerListenersREST.TavernaServerListenerREST;
import org.taverna.server.master.utils.CallTimeLogger.PerfLogged;
import org.taverna.server.master.utils.InvocationCounter.CallCounted;

/**
 * RESTful interface to a single listener attached to a workflow run.
 * 
 * @author Donal Fellows
 */
abstract class SingleListenerREST implements TavernaServerListenerREST,
		OneListenerBean {
	private Listener listen;
	private TavernaRun run;

	@Override
	@Nonnull
	public SingleListenerREST connect(@Nonnull Listener listen, @Nonnull TavernaRun run) {
		this.listen = listen;
		this.run = run;
		return this;
	}

	@Override
	@Nonnull
	@CallCounted
	@PerfLogged
	public String getConfiguration() {
		return listen.getConfiguration();
	}

	@Override
	@Nonnull
	@CallCounted
	@PerfLogged
	public ListenerDescription getDescription(@Nonnull UriInfo ui) {
		return new ListenerDescription(listen, secure(ui));
	}

	@Override
	@Nonnull
	@CallCounted
	@PerfLogged
	public TavernaServerListenersREST.Properties getProperties(@Nonnull UriInfo ui) {
		return new TavernaServerListenersREST.Properties(secure(ui).path(
				"{prop}"), listen.listProperties());
	}

	@Override
	@Nonnull
	@CallCounted
	@PerfLogged
	public TavernaServerListenersREST.Property getProperty(
			@Nonnull final String propertyName) throws NoListenerException {
		List<String> p = asList(listen.listProperties());
		if (p.contains(propertyName))
			return makePropertyInterface().connect(listen, run, propertyName);
		throw new NoListenerException("no such property");
	}

	protected abstract ListenerPropertyREST makePropertyInterface();

	@Override
	@CallCounted
	public Response listenerOptions() {
		return opt();
	}

	@Override
	@CallCounted
	public Response configurationOptions() {
		return opt();
	}

	@Override
	@CallCounted
	public Response propertiesOptions() {
		return opt();
	}
}
