/*
 * Copyright (C) 2010-2013 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.master.interfaces;

import java.net.URI;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.ws.rs.core.UriBuilder;

/**
 * How to manufacture URIs to workflow runs.
 * 
 * @author Donal Fellows
 */
public interface UriBuilderFactory {
	/**
	 * Given a run, get a factory for RESTful URIs to resources associated with
	 * it.
	 * 
	 * @param run
	 *            The run in question.
	 * @return The {@link URI} factory.
	 */
	@Nonnull
	UriBuilder getRunUriBuilder(@Nonnull TavernaRun run);

	/**
	 * @return a URI factory that is preconfigured to point to the base of the
	 *         webapp.
	 */
	@Nonnull
	UriBuilder getBaseUriBuilder();

	/**
	 * Resolves a URI with respect to the base URI of the factory.
	 * 
	 * @param uri
	 *            The URI to resolve, or <tt>null</tt>.
	 * @return The resolved URI, or <tt>null</tt> if <b>uri</b> is <tt>null</tt>
	 *         .
	 */
	@Nullable
	String resolve(@Nullable String uri);
}