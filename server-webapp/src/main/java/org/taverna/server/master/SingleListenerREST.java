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

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.taverna.server.master.exceptions.NoListenerException;
import org.taverna.server.master.interfaces.Listener;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.rest.TavernaServerListenersREST;
import org.taverna.server.master.rest.TavernaServerListenersREST.ListenerDescription;
import org.taverna.server.master.rest.TavernaServerListenersREST.TavernaServerListenerREST;
import org.taverna.server.master.utils.InvocationCounter.CallCounted;

import edu.umd.cs.findbugs.annotations.NonNull;

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
	@NonNull
	public SingleListenerREST connect(Listener listen, TavernaRun run) {
		this.listen = listen;
		this.run = run;
		return this;
	}

	@Override
	@NonNull
	@CallCounted
	public String getConfiguration() {
		return listen.getConfiguration();
	}

	@Override
	@NonNull
	@CallCounted
	public ListenerDescription getDescription(@NonNull UriInfo ui) {
		return new ListenerDescription(listen, secure(ui));
	}

	@Override
	@NonNull
	@CallCounted
	public TavernaServerListenersREST.Properties getProperties(
			@NonNull UriInfo ui) {
		return new TavernaServerListenersREST.Properties(secure(ui).path(
				"{prop}"), listen.listProperties());
	}

	@Override
	@NonNull
	@CallCounted
	public TavernaServerListenersREST.Property getProperty(
			@NonNull final String propertyName) throws NoListenerException {
		List<String> p = asList(listen.listProperties());
		if (p.contains(propertyName)) {
			return makePropertyInterface().connect(listen, run, propertyName);
		}
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

/**
 * Description of properties supported by {@link InputREST}.
 * 
 * @author Donal Fellows
 */
interface OneListenerBean {
	@NonNull
	SingleListenerREST connect(Listener listen, TavernaRun run);
}
