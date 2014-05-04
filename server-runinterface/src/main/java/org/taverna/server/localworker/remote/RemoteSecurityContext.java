/*
 * Copyright (C) 2010-2012 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.localworker.remote;

import java.net.URI;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

import javax.annotation.Nonnull;

/**
 * Outline of the security context for a workflow run.
 * 
 * @author Donal Fellows
 */
public interface RemoteSecurityContext extends Remote {
	/**
	 * Sets the content of the keystore for a run's security configuration.
	 * @param keystore The serialized bytes of the keystore.
	 * @throws RemoteException If anything goes wrong with the communications.
	 * @throws ImplementationException If anything goes wrong with the operation.
	 */
	void setKeystore(@Nonnull byte[] keystore) throws RemoteException,
			ImplementationException;

	/**
	 * Sets the password to use to unlock the keystore for a run's security configuration.
	 * @param password The characters of the password.
	 * @throws RemoteException If anything goes wrong with the communications.
	 * @throws ImplementationException If anything goes wrong with the operation.
	 */
	void setPassword(@Nonnull char[] password) throws RemoteException,
			ImplementationException;

	/**
	 * Sets the content of the truststore for a run's security configuration.
	 * @param truststore The serialized bytes of the truststore.
	 * @throws RemoteException If anything goes wrong with the communications.
	 * @throws ImplementationException If anything goes wrong with the operation.
	 */
	void setTruststore(@Nonnull byte[] truststore) throws RemoteException,
			ImplementationException;

	/**
	 * Sets the mapping from URIs to string aliases to use. <i>Probably not used.</i>
	 * @param keystore The mapping.
	 * @throws RemoteException If anything goes wrong with the communications.
	 * @throws ImplementationException If anything goes wrong with the operation.
	 */
	void setUriToAliasMap(@Nonnull Map<URI, String> uriToAliasMap)
			throws RemoteException, ImplementationException;

	// Undocumented hack
	void setHelioToken(@Nonnull String helioToken) throws RemoteException;
}
