/*
 * Copyright (C) 2010 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.master.factories;

import java.util.List;

import javax.annotation.Nonnull;

import org.taverna.server.master.exceptions.NoListenerException;
import org.taverna.server.master.interfaces.Listener;
import org.taverna.server.master.interfaces.TavernaRun;

/**
 * How to make event listeners of various types that are attached to a workflow
 * instance.
 * 
 * @author Donal Fellows
 */
public interface ListenerFactory {
	/**
	 * Make an event listener.
	 * 
	 * @param run
	 *            The workflow instance to attach the event listener to.
	 * @param listenerType
	 *            The type of event listener to create. Must be one of the
	 *            strings returned by {@link #getSupportedListenerTypes()}.
	 * @param configuration
	 *            A configuration document to pass to the listener.
	 * @return The event listener that was created.
	 * @throws NoListenerException
	 *             If the <b>listenerType</b> is unrecognized or the
	 *             <b>configuration</b> is bad in some way.
	 */
	@Nonnull
	public Listener makeListener(@Nonnull TavernaRun run,
			@Nonnull String listenerType, @Nonnull String configuration)
			throws NoListenerException;

	/**
	 * What types of listener are supported? Note that we assume that the list
	 * of types is the same for all users and all workflow instances.
	 * 
	 * @return A list of supported listener types.
	 */
	@Nonnull
	public List<String> getSupportedListenerTypes();
}
