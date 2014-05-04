/*
 * Copyright (C) 2010-2012 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.master;

import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.noContent;
import static org.taverna.server.master.common.Status.Initialized;
import static org.taverna.server.master.common.Uri.secure;
import static org.taverna.server.master.utils.RestUtils.opt;

import java.net.URI;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.taverna.server.master.api.SecurityBean;
import org.taverna.server.master.common.Credential;
import org.taverna.server.master.common.Permission;
import org.taverna.server.master.common.Trust;
import org.taverna.server.master.exceptions.BadStateChangeException;
import org.taverna.server.master.exceptions.InvalidCredentialException;
import org.taverna.server.master.exceptions.NoCredentialException;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.interfaces.TavernaSecurityContext;
import org.taverna.server.master.rest.TavernaServerSecurityREST;
import org.taverna.server.master.utils.CallTimeLogger.PerfLogged;
import org.taverna.server.master.utils.InvocationCounter.CallCounted;

/**
 * RESTful interface to a single workflow run's security settings.
 * 
 * @author Donal Fellows
 */
class RunSecurityREST implements TavernaServerSecurityREST, SecurityBean {
	private TavernaServerSupport support;
	private TavernaSecurityContext context;
	private TavernaRun run;

	@Override
	public void setSupport(TavernaServerSupport support) {
		this.support = support;
	}

	@Override
	@Nonnull
	public RunSecurityREST connect(@Nonnull TavernaSecurityContext context,
			@Nonnull TavernaRun run) {
		this.context = context;
		this.run = run;
		return this;
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public Descriptor describe(@Nonnull UriInfo ui) {
		return new Descriptor(secure(ui).path("{element}"), context.getOwner()
				.getName(), context.getCredentials(), context.getTrusted());
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public String getOwner() {
		return context.getOwner().getName();
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public CredentialList listCredentials() {
		return new CredentialList(context.getCredentials());
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public CredentialHolder getParticularCredential(@Nonnull String id)
			throws NoCredentialException {
		for (Credential c : context.getCredentials())
			if (c.id.equals(id))
				return new CredentialHolder(c);
		throw new NoCredentialException();
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public CredentialHolder setParticularCredential(@Nonnull String id,
			@Nonnull CredentialHolder cred, @Nonnull UriInfo ui)
			throws InvalidCredentialException, BadStateChangeException {
		if (run.getStatus() != Initialized)
			throw new BadStateChangeException();
		Credential c = cred.credential;
		c.id = id;
		c.href = ui.getAbsolutePath().toString();
		context.validateCredential(c);
		context.deleteCredential(c);
		context.addCredential(c);
		return new CredentialHolder(c);
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public Response addCredential(@Nonnull CredentialHolder cred, @Nonnull UriInfo ui)
			throws InvalidCredentialException, BadStateChangeException {
		if (run.getStatus() != Initialized)
			throw new BadStateChangeException();
		Credential c = cred.credential;
		c.id = randomUUID().toString();
		URI uri = secure(ui).path("{id}").build(c.id);
		c.href = uri.toString();
		context.validateCredential(c);
		context.addCredential(c);
		return created(uri).build();
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public Response deleteAllCredentials(@Nonnull UriInfo ui)
			throws BadStateChangeException {
		if (run.getStatus() != Initialized)
			throw new BadStateChangeException();
		for (Credential c : context.getCredentials())
			context.deleteCredential(c);
		return noContent().build();
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public Response deleteCredential(@Nonnull String id, @Nonnull UriInfo ui)
			throws BadStateChangeException {
		if (run.getStatus() != Initialized)
			throw new BadStateChangeException();
		context.deleteCredential(new Credential.Dummy(id));
		return noContent().build();
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public TrustList listTrusted() {
		return new TrustList(context.getTrusted());
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public Trust getParticularTrust(@Nonnull String id) throws NoCredentialException {
		for (Trust t : context.getTrusted())
			if (t.id.equals(id))
				return t;
		throw new NoCredentialException();
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public Trust setParticularTrust(@Nonnull String id, @Nonnull Trust t, @Nonnull UriInfo ui)
			throws InvalidCredentialException, BadStateChangeException {
		if (run.getStatus() != Initialized)
			throw new BadStateChangeException();
		t.id = id;
		t.href = ui.getAbsolutePath().toString();
		context.validateTrusted(t);
		context.deleteTrusted(t);
		context.addTrusted(t);
		return t;
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public Response addTrust(@Nonnull Trust t, @Nonnull UriInfo ui)
			throws InvalidCredentialException, BadStateChangeException {
		if (run.getStatus() != Initialized)
			throw new BadStateChangeException();
		t.id = randomUUID().toString();
		URI uri = secure(ui).path("{id}").build(t.id);
		t.href = uri.toString();
		context.validateTrusted(t);
		context.addTrusted(t);
		return created(uri).build();
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public Response deleteAllTrusts(@Nonnull UriInfo ui) throws BadStateChangeException {
		if (run.getStatus() != Initialized)
			throw new BadStateChangeException();
		for (Trust t : context.getTrusted())
			context.deleteTrusted(t);
		return noContent().build();
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public Response deleteTrust(@Nonnull String id, @Nonnull UriInfo ui)
			throws BadStateChangeException {
		if (run.getStatus() != Initialized)
			throw new BadStateChangeException();
		Trust toDelete = new Trust();
		toDelete.id = id;
		context.deleteTrusted(toDelete);
		return noContent().build();
	}

	@Override
	@Nonnull
	@CallCounted
	@PerfLogged
	public PermissionsDescription describePermissions(@Nonnull UriInfo ui) {
		Map<String, Permission> perm = support.getPermissionMap(context);
		return new PermissionsDescription(secure(ui).path("{id}"), perm);
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public Permission describePermission(@Nonnull String id) {
		return support.getPermission(context, id);
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public Permission setPermission(@Nonnull String id, @Nonnull Permission perm) {
		support.setPermission(context, id, perm);
		return support.getPermission(context, id);
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public Response deletePermission(@Nonnull String id, @Nonnull UriInfo ui) {
		support.setPermission(context, id, Permission.None);
		return noContent().build();
	}

	@Override
	@CallCounted
	@Nonnull
	@PerfLogged
	public Response makePermission(@Nonnull PermissionDescription desc, @Nonnull UriInfo ui) {
		support.setPermission(context, desc.userName, desc.permission);
		return created(secure(ui).path("{user}").build(desc.userName)).build();
	}

	@Override
	@CallCounted
	@Nonnull
	public Response descriptionOptions() {
		return opt();
	}

	@Override
	@CallCounted
	@Nonnull
	public Response ownerOptions() {
		return opt();
	}

	@Override
	@CallCounted
	@Nonnull
	public Response credentialsOptions() {
		return opt("POST", "DELETE");
	}

	@Override
	@Nonnull
	@CallCounted
	public Response credentialOptions(String id) {
		return opt("PUT", "DELETE");
	}

	@Override
	@CallCounted
	@Nonnull
	public Response trustsOptions() {
		return opt("POST", "DELETE");
	}

	@Override
	@CallCounted
	@Nonnull
	public Response trustOptions(String id) {
		return opt("PUT", "DELETE");
	}

	@Override
	@CallCounted
	@Nonnull
	public Response permissionsOptions() {
		return opt("POST");
	}

	@Override
	@CallCounted
	@Nonnull
	public Response permissionOptions(String id) {
		return opt("PUT", "DELETE");
	}
}
