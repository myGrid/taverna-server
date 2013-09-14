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
import org.taverna.server.master.utils.InvocationCounter.CallCounted;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * RESTful interface to a single workflow run's security settings.
 * 
 * @author Donal Fellows
 */
@SuppressWarnings("null")
class RunSecurityREST implements TavernaServerSecurityREST, SecurityBean {
	private TavernaServerSupport support;
	private TavernaSecurityContext context;
	private TavernaRun run;

	@Override
	public void setSupport(TavernaServerSupport support) {
		this.support = support;
	}

	@Override
	@NonNull
	public RunSecurityREST connect(@NonNull TavernaSecurityContext context,
			@NonNull TavernaRun run) {
		this.context = context;
		this.run = run;
		return this;
	}

	@Override
	@CallCounted
	@NonNull
	public Descriptor describe(@NonNull UriInfo ui) {
		return new Descriptor(secure(ui).path("{element}"), context.getOwner()
				.getName(), context.getCredentials(), context.getTrusted());
	}

	@Override
	@CallCounted
	@NonNull
	public String getOwner() {
		return context.getOwner().getName();
	}

	@Override
	@CallCounted
	@NonNull
	public CredentialList listCredentials() {
		return new CredentialList(context.getCredentials());
	}

	@Override
	@CallCounted
	@NonNull
	public CredentialHolder getParticularCredential(@NonNull String id)
			throws NoCredentialException {
		for (Credential c : context.getCredentials())
			if (c.id.equals(id))
				return new CredentialHolder(c);
		throw new NoCredentialException();
	}

	@Override
	@CallCounted
	@NonNull
	public CredentialHolder setParticularCredential(@NonNull String id,
			@NonNull CredentialHolder cred, @NonNull UriInfo ui)
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
	@NonNull
	public Response addCredential(@NonNull CredentialHolder cred,
			@NonNull UriInfo ui) throws InvalidCredentialException,
			BadStateChangeException {
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
	@NonNull
	public Response deleteAllCredentials(@NonNull UriInfo ui)
			throws BadStateChangeException {
		if (run.getStatus() != Initialized)
			throw new BadStateChangeException();
		for (Credential c : context.getCredentials())
			context.deleteCredential(c);
		return noContent().build();
	}

	@Override
	@CallCounted
	@NonNull
	public Response deleteCredential(@NonNull String id, @NonNull UriInfo ui)
			throws BadStateChangeException {
		if (run.getStatus() != Initialized)
			throw new BadStateChangeException();
		context.deleteCredential(new Credential.Dummy(id));
		return noContent().build();
	}

	@Override
	@CallCounted
	@NonNull
	public TrustList listTrusted() {
		return new TrustList(context.getTrusted());
	}

	@Override
	@CallCounted
	@NonNull
	public Trust getParticularTrust(@NonNull String id)
			throws NoCredentialException {
		for (Trust t : context.getTrusted())
			if (t.id.equals(id))
				return t;
		throw new NoCredentialException();
	}

	@Override
	@CallCounted
	@NonNull
	public Trust setParticularTrust(@NonNull String id, @NonNull Trust t,
			@NonNull UriInfo ui) throws InvalidCredentialException,
			BadStateChangeException {
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
	@NonNull
	public Response addTrust(@NonNull Trust t, @NonNull UriInfo ui)
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
	@NonNull
	public Response deleteAllTrusts(@NonNull UriInfo ui)
			throws BadStateChangeException {
		if (run.getStatus() != Initialized)
			throw new BadStateChangeException();
		for (Trust t : context.getTrusted())
			context.deleteTrusted(t);
		return noContent().build();
	}

	@Override
	@CallCounted
	@NonNull
	public Response deleteTrust(@NonNull String id, @NonNull UriInfo ui)
			throws BadStateChangeException {
		if (run.getStatus() != Initialized)
			throw new BadStateChangeException();
		Trust toDelete = new Trust();
		toDelete.id = id;
		context.deleteTrusted(toDelete);
		return noContent().build();
	}

	@Override
	@NonNull
	@CallCounted
	public PermissionsDescription describePermissions(@NonNull UriInfo ui) {
		Map<String, Permission> perm = support.getPermissionMap(context);
		return new PermissionsDescription(secure(ui).path("{id}"), perm);
	}

	@Override
	@CallCounted
	@NonNull
	public Permission describePermission(@NonNull String id) {
		return support.getPermission(context, id);
	}

	@Override
	@CallCounted
	@NonNull
	public Permission setPermission(@NonNull String id, @NonNull Permission perm) {
		support.setPermission(context, id, perm);
		return support.getPermission(context, id);
	}

	@Override
	@CallCounted
	@NonNull
	public Response deletePermission(@NonNull String id, @NonNull UriInfo ui) {
		support.setPermission(context, id, Permission.None);
		return noContent().build();
	}

	@Override
	@CallCounted
	@NonNull
	public Response makePermission(@NonNull PermissionDescription desc,
			@NonNull UriInfo ui) {
		support.setPermission(context, desc.userName, desc.permission);
		return created(secure(ui).path("{user}").build(desc.userName)).build();
	}

	@Override
	@CallCounted
	@NonNull
	public Response descriptionOptions() {
		return opt();
	}

	@Override
	@CallCounted
	@NonNull
	public Response ownerOptions() {
		return opt();
	}

	@Override
	@CallCounted
	@NonNull
	public Response credentialsOptions() {
		return opt("POST", "DELETE");
	}

	@Override
	@NonNull
	@CallCounted
	public Response credentialOptions(String id) {
		return opt("PUT", "DELETE");
	}

	@Override
	@CallCounted
	@NonNull
	public Response trustsOptions() {
		return opt("POST", "DELETE");
	}

	@Override
	@CallCounted
	@NonNull
	public Response trustOptions(String id) {
		return opt("PUT", "DELETE");
	}

	@Override
	@CallCounted
	@NonNull
	public Response permissionsOptions() {
		return opt("POST");
	}

	@Override
	@CallCounted
	@NonNull
	public Response permissionOptions(String id) {
		return opt("PUT", "DELETE");
	}
}
