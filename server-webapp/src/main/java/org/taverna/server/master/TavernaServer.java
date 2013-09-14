/*
 * Copyright (C) 2010-2011 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.master;

import static java.lang.Math.min;
import static java.util.Collections.emptyMap;
import static java.util.Collections.sort;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.UriBuilder.fromUri;
import static javax.xml.ws.handler.MessageContext.HTTP_REQUEST_HEADERS;
import static javax.xml.ws.handler.MessageContext.PATH_INFO;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.taverna.server.master.common.DirEntryReference.newInstance;
import static org.taverna.server.master.common.Namespaces.SERVER_SOAP;
import static org.taverna.server.master.common.Roles.ADMIN;
import static org.taverna.server.master.common.Roles.USER;
import static org.taverna.server.master.common.Status.Initialized;
import static org.taverna.server.master.common.Uri.secure;
import static org.taverna.server.master.soap.DirEntry.convert;
import static org.taverna.server.master.utils.RestUtils.opt;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.annotation.security.DeclareRoles;
import javax.jws.WebService;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBException;
import javax.xml.ws.WebServiceContext;

import org.apache.commons.logging.Log;
import org.apache.cxf.annotations.WSDLDocumentation;
import org.ogf.usage.JobUsageRecord;
import org.springframework.beans.factory.annotation.Required;
import org.taverna.server.master.api.SupportAware;
import org.taverna.server.master.api.TavernaServerBean;
import org.taverna.server.master.common.Credential;
import org.taverna.server.master.common.InputDescription;
import org.taverna.server.master.common.Permission;
import org.taverna.server.master.common.RunReference;
import org.taverna.server.master.common.Status;
import org.taverna.server.master.common.Trust;
import org.taverna.server.master.common.Workflow;
import org.taverna.server.master.common.version.Version;
import org.taverna.server.master.exceptions.BadStateChangeException;
import org.taverna.server.master.exceptions.FilesystemAccessException;
import org.taverna.server.master.exceptions.InvalidCredentialException;
import org.taverna.server.master.exceptions.NoCreateException;
import org.taverna.server.master.exceptions.NoCredentialException;
import org.taverna.server.master.exceptions.NoDestroyException;
import org.taverna.server.master.exceptions.NoDirectoryEntryException;
import org.taverna.server.master.exceptions.NoListenerException;
import org.taverna.server.master.exceptions.NoUpdateException;
import org.taverna.server.master.exceptions.NotOwnerException;
import org.taverna.server.master.exceptions.OverloadedException;
import org.taverna.server.master.exceptions.UnknownRunException;
import org.taverna.server.master.factories.ListenerFactory;
import org.taverna.server.master.interfaces.Directory;
import org.taverna.server.master.interfaces.DirectoryEntry;
import org.taverna.server.master.interfaces.File;
import org.taverna.server.master.interfaces.Input;
import org.taverna.server.master.interfaces.Listener;
import org.taverna.server.master.interfaces.Policy;
import org.taverna.server.master.interfaces.RunStore;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.interfaces.TavernaSecurityContext;
import org.taverna.server.master.notification.NotificationEngine;
import org.taverna.server.master.notification.atom.EventDAO;
import org.taverna.server.master.rest.TavernaServerREST;
import org.taverna.server.master.rest.TavernaServerREST.EnabledNotificationFabrics;
import org.taverna.server.master.rest.TavernaServerREST.PermittedListeners;
import org.taverna.server.master.rest.TavernaServerREST.PermittedWorkflows;
import org.taverna.server.master.rest.TavernaServerREST.PolicyView;
import org.taverna.server.master.rest.TavernaServerRunREST;
import org.taverna.server.master.soap.DirEntry;
import org.taverna.server.master.soap.FileContents;
import org.taverna.server.master.soap.PermissionList;
import org.taverna.server.master.soap.TavernaServerSOAP;
import org.taverna.server.master.soap.ZippedDirectory;
import org.taverna.server.master.utils.FilenameUtils;
import org.taverna.server.master.utils.InvocationCounter.CallCounted;
import org.taverna.server.port_description.OutputDescription;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;

/**
 * The core implementation of the web application.
 * 
 * @author Donal Fellows
 */
@Path("/")
@DeclareRoles({ USER, ADMIN })
@WebService(endpointInterface = "org.taverna.server.master.soap.TavernaServerSOAP", serviceName = "TavernaServer", targetNamespace = SERVER_SOAP)
@WSDLDocumentation("An instance of Taverna " + Version.JAVA + " Server.")
public abstract class TavernaServer implements TavernaServerSOAP,
		TavernaServerREST, TavernaServerBean {
	/**
	 * The root of descriptions of the server in JMX.
	 */
	public static final String JMX_ROOT = "Taverna:group=Server-"
			+ Version.JAVA + ",name=";

	/** The logger for the server framework. */
	public static Log log = getLog("Taverna.Server.Webapp");

	@PreDestroy
	void closeLog() {
		log = null;
	}

	// -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
	// CONNECTIONS TO JMX, SPRING AND CXF

	@Resource
	WebServiceContext jaxws;
	@Context
	@SuppressWarnings("UWF_UNWRITTEN_FIELD")
	private HttpHeaders jaxrsHeaders;

	// -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
	// STATE VARIABLES AND SPRING SETTERS

	/**
	 * For building descriptions of the expected inputs and actual outputs of a
	 * workflow.
	 */
	private ContentsDescriptorBuilder cdBuilder;
	/**
	 * Utilities for accessing files on the local-worker.
	 */
	private FilenameUtils fileUtils;
	/** How notifications are dispatched. */
	private NotificationEngine notificationEngine;
	/** Main support class. */
	private TavernaServerSupport support;
	/** A storage facility for workflow runs. */
	private RunStore runStore;
	/** Encapsulates the policies applied by this server. */
	private Policy policy;
	/** Where Atom events come from. */
	EventDAO eventSource;
	/** Reference to the main interaction feed. */
	private String interactionFeed;

	@Override
	@Required
	public void setFileUtils(@NonNull FilenameUtils converter) {
		this.fileUtils = converter;
	}

	@Override
	@Required
	public void setContentsDescriptorBuilder(
			@NonNull ContentsDescriptorBuilder cdBuilder) {
		this.cdBuilder = cdBuilder;
	}

	@Override
	@Required
	public void setNotificationEngine(
			@NonNull NotificationEngine notificationEngine) {
		this.notificationEngine = notificationEngine;
	}

	/**
	 * @param support
	 *            the support to set
	 */
	@Override
	@Required
	public void setSupport(@NonNull TavernaServerSupport support) {
		this.support = support;
	}

	@Override
	@Required
	public void setRunStore(@NonNull RunStore runStore) {
		this.runStore = runStore;
	}

	@Override
	@Required
	public void setPolicy(@NonNull Policy policy) {
		this.policy = policy;
	}

	@Override
	@Required
	public void setEventSource(@NonNull EventDAO eventSource) {
		this.eventSource = eventSource;
	}

	/**
	 * The location of a service-wide interaction feed, derived from a
	 * properties file. Expected to be <i>actually</i> not set (to a real
	 * value).
	 * 
	 * @param interactionFeed
	 *            The URL, which will be resolved relative to the location of
	 *            the webapp, or the string "<tt>none</tt>" (which corresponds
	 *            to a <tt>null</tt>).
	 */
	public void setInteractionFeed(String interactionFeed) {
		if ("none".equals(interactionFeed))
			interactionFeed = null;
		else if (interactionFeed != null && interactionFeed.startsWith("${"))
			interactionFeed = null;
		this.interactionFeed = interactionFeed;
	}

	// -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
	// REST INTERFACE

	@Override
	@CallCounted
	@NonNull
	public ServerDescription describeService(@NonNull UriInfo ui) {
		jaxrsUriInfo.set(new WeakReference<UriInfo>(ui));
		return new ServerDescription(ui, resolve(interactionFeed));
	}

	@Override
	@CallCounted
	@NonNull
	public RunList listUsersRuns(@NonNull UriInfo ui) {
		jaxrsUriInfo.set(new WeakReference<UriInfo>(ui));
		return new RunList(runs(), secure(ui).path("{name}"));
	}

	@java.lang.SuppressWarnings("null")
	@Override
	@CallCounted
	@NonNull
	public Response submitWorkflow(@NonNull Workflow workflow,
			@NonNull UriInfo ui) throws NoUpdateException {
		jaxrsUriInfo.set(new WeakReference<UriInfo>(ui));
		checkCreatePolicy(workflow);
		String name = support.buildWorkflow(workflow);
		return created(secure(ui).path("{uuid}").build(name)).build();
	}

	@java.lang.SuppressWarnings("null")
	@Override
	@CallCounted
	@NonNull
	public Response submitWorkflowByURL(@NonNull List<URI> referenceList,
			@NonNull UriInfo ui) throws NoCreateException {
		jaxrsUriInfo.set(new WeakReference<UriInfo>(ui));
		if (referenceList == null || referenceList.size() == 0)
			throw new NoCreateException("no workflow URI supplied");
		URI workflowURI = referenceList.get(0);
		checkCreatePolicy(workflowURI);
		Workflow workflow;
		try {
			workflow = support.getWorkflowDocumentFromURI(workflowURI);
		} catch (IOException e) {
			throw new NoCreateException("could not read workflow", e);
		}
		String name = support.buildWorkflow(workflow);
		return created(secure(ui).path("{uuid}").build(name)).build();
	}

	@Override
	@CallCounted
	public int getMaxSimultaneousRuns() {
		return support.getMaxSimultaneousRuns();
	}

	@Override
	@CallCounted
	@NonNull
	public TavernaServerRunREST getRunResource(final @NonNull String runName,
			@NonNull UriInfo ui) throws UnknownRunException {
		jaxrsUriInfo.set(new WeakReference<UriInfo>(ui));
		RunREST rr = makeRunInterface();
		rr.setRun(support.getRun(runName));
		rr.setRunName(runName);
		return rr;
	}

	private ThreadLocal<Reference<UriInfo>> jaxrsUriInfo = new InheritableThreadLocal<Reference<UriInfo>>();

	@Nullable
	private UriInfo getUriInfo() {
		if (jaxrsUriInfo.get() == null)
			return null;
		return jaxrsUriInfo.get().get();
	}

	@Override
	@CallCounted
	@NonNull
	public abstract PolicyView getPolicyDescription();

	@Override
	@CallCounted
	public Response serviceOptions() {
		return opt();
	}

	@Override
	@CallCounted
	public Response runsOptions() {
		return opt("POST");
	}

	/**
	 * Construct a RESTful interface to a run.
	 * 
	 * @return The handle to the interface, as decorated by Spring.
	 */
	protected abstract RunREST makeRunInterface();

	// -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
	// SOAP INTERFACE

	@Override
	@CallCounted
	public RunReference[] listRuns() {
		ArrayList<RunReference> ws = new ArrayList<RunReference>();
		UriBuilder ub = getRunUriBuilder();
		for (String runName : runs().keySet())
			ws.add(new RunReference(runName, ub));
		return ws.toArray(new RunReference[ws.size()]);
	}

	private void checkCreatePolicy(Workflow workflow) throws NoCreateException {
		List<URI> pwu = policy
				.listPermittedWorkflowURIs(support.getPrincipal());
		if (pwu == null || pwu.size() == 0)
			return;
		throw new NoCreateException("server policy: will only start "
				+ "workflows sourced from permitted URI list");
	}

	private void checkCreatePolicy(URI workflowURI) throws NoCreateException {
		List<URI> pwu = policy
				.listPermittedWorkflowURIs(support.getPrincipal());
		if (pwu == null || pwu.size() == 0 || pwu.contains(workflowURI))
			return;
		throw new NoCreateException("workflow URI not on permitted list");
	}

	@Override
	@CallCounted
	public RunReference submitWorkflow(Workflow workflow)
			throws NoUpdateException {
		checkCreatePolicy(workflow);
		String name = support.buildWorkflow(workflow);
		return new RunReference(name, getRunUriBuilder());
	}

	@Override
	@CallCounted
	public RunReference submitWorkflowByURI(URI workflowURI)
			throws NoCreateException {
		checkCreatePolicy(workflowURI);
		Workflow workflow;
		try {
			workflow = support.getWorkflowDocumentFromURI(workflowURI);
		} catch (IOException e) {
			throw new NoCreateException("could not read workflow", e);
		}
		String name = support.buildWorkflow(workflow);
		return new RunReference(name, getRunUriBuilder());
	}

	@Override
	@CallCounted
	public URI[] getAllowedWorkflows() {
		return support.getPermittedWorkflowURIs();
	}

	@Override
	@CallCounted
	public String[] getAllowedListeners() {
		List<String> types = support.getListenerTypes();
		return types.toArray(new String[types.size()]);
	}

	@Override
	@CallCounted
	public String[] getEnabledNotifiers() {
		List<String> dispatchers = notificationEngine
				.listAvailableDispatchers();
		return dispatchers.toArray(new String[dispatchers.size()]);
	}

	@Override
	@CallCounted
	public void destroyRun(String runName) throws UnknownRunException,
			NoUpdateException {
		if (runName == null)
			throw new NoDestroyException();
		support.unregisterRun(runName, null);
	}

	@Override
	@CallCounted
	public String getRunDescriptiveName(String runName)
			throws UnknownRunException {
		return support.getRun(runName).getName();
	}

	@Override
	@CallCounted
	public void setRunDescriptiveName(String runName, String descriptiveName)
			throws UnknownRunException, NoUpdateException {
		TavernaRun run = support.getRun(runName);
		support.permitUpdate(run);
		run.setName(descriptiveName == null ? "" : descriptiveName);
	}

	@Override
	@CallCounted
	public Workflow getRunWorkflow(String runName) throws UnknownRunException {
		return support.getRun(runName).getWorkflow();
	}

	@Override
	@CallCounted
	public Date getRunExpiry(String runName) throws UnknownRunException {
		return support.getRun(runName).getExpiry();
	}

	@Override
	@CallCounted
	public void setRunExpiry(String runName, Date d)
			throws UnknownRunException, NoUpdateException {
		if (d == null)
			throw new NoUpdateException("real date required");
		support.updateExpiry(support.getRun(runName), d);
	}

	@Override
	@CallCounted
	public Date getRunCreationTime(String runName) throws UnknownRunException {
		return support.getRun(runName).getCreationTimestamp();
	}

	@Override
	@CallCounted
	public Date getRunFinishTime(String runName) throws UnknownRunException {
		return support.getRun(runName).getFinishTimestamp();
	}

	@Override
	@CallCounted
	public Date getRunStartTime(String runName) throws UnknownRunException {
		return support.getRun(runName).getStartTimestamp();
	}

	@Override
	@CallCounted
	public Status getRunStatus(String runName) throws UnknownRunException {
		return support.getRun(runName).getStatus();
	}

	@Override
	@CallCounted
	public String setRunStatus(String runName, Status s)
			throws UnknownRunException, NoUpdateException {
		TavernaRun w = support.getRun(runName);
		support.permitUpdate(w);
		if (s == Status.Operating && w.getStatus() == Status.Initialized) {
			if (!support.getAllowStartWorkflowRuns())
				throw new OverloadedException();
			try {
				String issue = w.setStatus(s);
				if (issue == null)
					return "";
				if (issue.isEmpty())
					return "unknown reason for partial change";
				return issue;
			} catch (RuntimeException re) {
				log.info("failed to start run " + runName, re);
				throw re;
			} catch (NoUpdateException nue) {
				log.info("failed to start run " + runName, nue);
				throw nue;
			}
		} else {
			w.setStatus(s);
			return "";
		}
	}

	@Override
	@CallCounted
	public String getRunStdout(String runName) throws UnknownRunException {
		try {
			return support.getProperty(runName, "io", "stdout");
		} catch (NoListenerException e) {
			return "";
		}
	}

	@Override
	@CallCounted
	public String getRunStderr(String runName) throws UnknownRunException {
		try {
			return support.getProperty(runName, "io", "stderr");
		} catch (NoListenerException e) {
			return "";
		}
	}

	@Override
	@CallCounted
	public JobUsageRecord getRunUsageRecord(String runName)
			throws UnknownRunException {
		try {
			String ur = support.getProperty(runName, "io", "usageRecord");
			if (ur.isEmpty())
				return null;
			return JobUsageRecord.unmarshal(ur);
		} catch (NoListenerException e) {
			return null;
		} catch (JAXBException e) {
			log.info("failed to deserialize non-empty usage record", e);
			return null;
		}
	}

	@Override
	@CallCounted
	public String getRunLog(String runName) throws UnknownRunException {
		try {
			File f = fileUtils.getFile(support.getRun(runName),
					"logs/detail.log");
			return new String(f.getContents(0, -1), "UTF-8");
		} catch (FilesystemAccessException e) {
			// Ignore this; normal during some parts of lifecycle
			return "";
		} catch (NoDirectoryEntryException e) {
			// Ignore this; normal during some parts of lifecycle
			return "";
		} catch (UnsupportedEncodingException e) {
			log.warn("unexpected encoding problem", e);
			return "";
		}
	}

	// -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
	// SOAP INTERFACE - Security

	@Override
	@CallCounted
	public String getRunOwner(String runName) throws UnknownRunException {
		return support.getRun(runName).getSecurityContext().getOwner()
				.getName();
	}

	/**
	 * Look up a security context, applying access control rules for access to
	 * the parts of the context that are only open to the owner.
	 * 
	 * @param runName
	 *            The name of the workflow run.
	 * @param initialOnly
	 *            Whether to check if we're in the initial state.
	 * @return The security context. Never <tt>null</tt>.
	 * @throws UnknownRunException
	 * @throws NotOwnerException
	 * @throws BadStateChangeException
	 */
	private TavernaSecurityContext getRunSecurityContext(String runName,
			boolean initialOnly) throws UnknownRunException, NotOwnerException,
			BadStateChangeException {
		TavernaRun run = support.getRun(runName);
		TavernaSecurityContext c = run.getSecurityContext();
		if (!c.getOwner().equals(support.getPrincipal()))
			throw new NotOwnerException();
		if (initialOnly && run.getStatus() != Initialized)
			throw new BadStateChangeException();
		return c;
	}

	@Override
	@CallCounted
	public Credential[] getRunCredentials(String runName)
			throws UnknownRunException, NotOwnerException {
		try {
			return getRunSecurityContext(runName, false).getCredentials();
		} catch (BadStateChangeException e) {
			Error e2 = new Error("impossible");
			e2.initCause(e);
			throw e2;
		}
	}

	private Credential findCredential(TavernaSecurityContext c, String id)
			throws NoCredentialException {
		for (Credential t : c.getCredentials())
			if (t.id.equals(id))
				return t;
		throw new NoCredentialException();
	}

	private Trust findTrust(TavernaSecurityContext c, String id)
			throws NoCredentialException {
		for (Trust t : c.getTrusted())
			if (t.id.equals(id))
				return t;
		throw new NoCredentialException();
	}

	@Override
	@CallCounted
	public String setRunCredential(String runName, String credentialID,
			Credential credential) throws UnknownRunException,
			NotOwnerException, InvalidCredentialException,
			NoCredentialException, BadStateChangeException {
		TavernaSecurityContext c = getRunSecurityContext(runName, true);
		if (credentialID == null || credentialID.isEmpty()) {
			credential.id = randomUUID().toString();
		} else {
			credential.id = findCredential(c, credentialID).id;
		}
		URI uri = getRunUriBuilder().path("security/credentials/{credid}")
				.build(runName, credential.id);
		credential.href = uri.toString();
		c.validateCredential(credential);
		c.addCredential(credential);
		return credential.id;
	}

	@Override
	@CallCounted
	public void deleteRunCredential(String runName, String credentialID)
			throws UnknownRunException, NotOwnerException,
			NoCredentialException, BadStateChangeException {
		getRunSecurityContext(runName, true).deleteCredential(
				new Credential.Dummy(credentialID));
	}

	@Override
	@CallCounted
	public Trust[] getRunCertificates(String runName)
			throws UnknownRunException, NotOwnerException {
		try {
			return getRunSecurityContext(runName, false).getTrusted();
		} catch (BadStateChangeException e) {
			Error e2 = new Error("impossible");
			e2.initCause(e);
			throw e2;
		}
	}

	@Override
	@CallCounted
	public String setRunCertificates(String runName, String certificateID,
			Trust certificate) throws UnknownRunException, NotOwnerException,
			InvalidCredentialException, NoCredentialException,
			BadStateChangeException {
		TavernaSecurityContext c = getRunSecurityContext(runName, true);
		if (certificateID == null || certificateID.isEmpty()) {
			certificate.id = randomUUID().toString();
		} else {
			certificate.id = findTrust(c, certificateID).id;
		}
		URI uri = getRunUriBuilder().path("security/trusts/{certid}").build(
				runName, certificate.id);
		certificate.href = uri.toString();
		c.validateTrusted(certificate);
		c.addTrusted(certificate);
		return certificate.id;
	}

	@Override
	@CallCounted
	public void deleteRunCertificates(String runName, String certificateID)
			throws UnknownRunException, NotOwnerException,
			NoCredentialException, BadStateChangeException {
		TavernaSecurityContext c = getRunSecurityContext(runName, true);
		Trust toDelete = new Trust();
		toDelete.id = certificateID;
		c.deleteTrusted(toDelete);
	}

	@Override
	@CallCounted
	public PermissionList listRunPermissions(String runName)
			throws UnknownRunException, NotOwnerException {
		PermissionList pl = new PermissionList();
		pl.permission = new ArrayList<PermissionList.SinglePermissionMapping>();
		Map<String, Permission> perm;
		try {
			perm = support.getPermissionMap(getRunSecurityContext(runName,
					false));
		} catch (BadStateChangeException e) {
			log.error("unexpected error from internal API", e);
			perm = emptyMap();
		}
		List<String> users = new ArrayList<String>(perm.keySet());
		sort(users);
		for (String user : users)
			pl.permission.add(new PermissionList.SinglePermissionMapping(user,
					perm.get(user)));
		return pl;
	}

	@Override
	@CallCounted
	public void setRunPermission(String runName, String userName,
			Permission permission) throws UnknownRunException,
			NotOwnerException {
		try {
			support.setPermission(getRunSecurityContext(runName, false),
					userName, permission);
		} catch (BadStateChangeException e) {
			log.error("unexpected error from internal API", e);
		}
	}

	// -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
	// SOAP INTERFACE - Filesystem connection

	@Override
	@CallCounted
	public OutputDescription getRunOutputDescription(String runName)
			throws UnknownRunException, BadStateChangeException,
			FilesystemAccessException, NoDirectoryEntryException {
		TavernaRun run = support.getRun(runName);
		if (run.getStatus() == Initialized) {
			throw new BadStateChangeException(
					"may not get output description in initial state");
		}
		return cdBuilder.makeOutputDescriptor(run, null);
	}

	@Override
	@CallCounted
	public DirEntry[] getRunDirectoryContents(String runName, DirEntry d)
			throws UnknownRunException, FilesystemAccessException,
			NoDirectoryEntryException {
		List<DirEntry> result = new ArrayList<DirEntry>();
		for (DirectoryEntry e : fileUtils.getDirectory(support.getRun(runName),
				convert(d)).getContents())
			result.add(convert(newInstance(null, e)));
		return result.toArray(new DirEntry[result.size()]);
	}

	@Override
	@CallCounted
	public byte[] getRunDirectoryAsZip(String runName, DirEntry d)
			throws UnknownRunException, FilesystemAccessException,
			NoDirectoryEntryException {
		try {
			return toByteArray(fileUtils.getDirectory(support.getRun(runName),
					convert(d)).getContentsAsZip());
		} catch (IOException e) {
			throw new FilesystemAccessException("problem serializing ZIP data",
					e);
		}
	}

	@Override
	@CallCounted
	public ZippedDirectory getRunDirectoryAsZipMTOM(String runName, DirEntry d)
			throws UnknownRunException, FilesystemAccessException,
			NoDirectoryEntryException {
		return new ZippedDirectory(fileUtils.getDirectory(
				support.getRun(runName), convert(d)));
	}

	@Override
	@CallCounted
	public DirEntry makeRunDirectory(String runName, DirEntry parent,
			String name) throws UnknownRunException, NoUpdateException,
			FilesystemAccessException, NoDirectoryEntryException {
		TavernaRun w = support.getRun(runName);
		support.permitUpdate(w);
		Directory dir = fileUtils.getDirectory(w, convert(parent))
				.makeSubdirectory(support.getPrincipal(),
						name == null ? "" : name);
		return convert(newInstance(null, dir));
	}

	@Override
	@CallCounted
	public DirEntry makeRunFile(String runName, DirEntry parent, String name)
			throws UnknownRunException, NoUpdateException,
			FilesystemAccessException, NoDirectoryEntryException {
		TavernaRun w = support.getRun(runName);
		support.permitUpdate(w);
		File f = fileUtils.getDirectory(w, convert(parent)).makeEmptyFile(
				support.getPrincipal(), name == null ? "" : name);
		return convert(newInstance(null, f));
	}

	@Override
	@CallCounted
	public void destroyRunDirectoryEntry(String runName, DirEntry d)
			throws UnknownRunException, NoUpdateException,
			FilesystemAccessException, NoDirectoryEntryException {
		TavernaRun w = support.getRun(runName);
		support.permitUpdate(w);
		fileUtils.getDirEntry(w, convert(d)).destroy();
	}

	@Override
	@CallCounted
	public byte[] getRunFileContents(String runName, DirEntry d)
			throws UnknownRunException, FilesystemAccessException,
			NoDirectoryEntryException {
		File f = fileUtils.getFile(support.getRun(runName), convert(d));
		return f.getContents(0, -1);
	}

	@Override
	@CallCounted
	public void setRunFileContents(String runName, DirEntry d,
			byte[] newContents) throws UnknownRunException, NoUpdateException,
			FilesystemAccessException, NoDirectoryEntryException {
		TavernaRun w = support.getRun(runName);
		support.permitUpdate(w);
		fileUtils.getFile(w, convert(d)).setContents(
				newContents == null ? new byte[0] : newContents);
	}

	@Override
	@CallCounted
	public FileContents getRunFileContentsMTOM(String runName, DirEntry d)
			throws UnknownRunException, FilesystemAccessException,
			NoDirectoryEntryException {
		File f = fileUtils.getFile(support.getRun(runName), convert(d));
		FileContents fc = new FileContents();
		fc.setFile(f, support.getEstimatedContentType(f));
		return fc;
	}

	@Override
	@CallCounted
	public void setRunFileContentsMTOM(String runName, FileContents newContents)
			throws UnknownRunException, NoUpdateException,
			FilesystemAccessException, NoDirectoryEntryException {
		TavernaRun run = support.getRun(runName);
		support.permitUpdate(run);
		String name = newContents.name;
		File f = fileUtils.getFile(run, name == null ? "" : name);
		f.setContents(new byte[0]);
		support.copyDataToFile(newContents.fileData, f);
	}

	@Override
	@CallCounted
	public String getRunFileType(String runName, DirEntry d)
			throws UnknownRunException, FilesystemAccessException,
			NoDirectoryEntryException {
		return support.getEstimatedContentType(fileUtils.getFile(
				support.getRun(runName), convert(d)));
	}

	@Override
	@CallCounted
	public long getRunFileLength(String runName, DirEntry d)
			throws UnknownRunException, FilesystemAccessException,
			NoDirectoryEntryException {
		return fileUtils.getFile(support.getRun(runName), convert(d)).getSize();
	}

	@Override
	@CallCounted
	public Date getRunFileModified(String runName, DirEntry d)
			throws UnknownRunException, FilesystemAccessException,
			NoDirectoryEntryException {
		return fileUtils.getFile(support.getRun(runName), convert(d))
				.getModificationDate();
	}

	// -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
	// SOAP INTERFACE - Run listeners

	@Override
	@CallCounted
	public String[] getRunListeners(String runName) throws UnknownRunException {
		TavernaRun w = support.getRun(runName);
		List<String> result = new ArrayList<String>();
		for (Listener l : w.getListeners())
			result.add(l.getName());
		return result.toArray(new String[result.size()]);
	}

	@Override
	@CallCounted
	public String addRunListener(String runName, String listenerType,
			String configuration) throws UnknownRunException,
			NoUpdateException, NoListenerException {
		return support.makeListener(support.getRun(runName),
				listenerType == null ? "" : listenerType,
				configuration == null ? "" : configuration).getName();
	}

	@Override
	@CallCounted
	public String getRunListenerConfiguration(String runName,
			String listenerName) throws UnknownRunException,
			NoListenerException {
		return support.getListener(runName, listenerName).getConfiguration();
	}

	@Override
	@CallCounted
	public String[] getRunListenerProperties(String runName, String listenerName)
			throws UnknownRunException, NoListenerException {
		return support.getListener(runName, listenerName).listProperties()
				.clone();
	}

	@Override
	@CallCounted
	public String getRunListenerProperty(String runName, String listenerName,
			String propName) throws UnknownRunException, NoListenerException {
		if (propName == null)
			throw new NoListenerException("problem getting property");
		return support.getListener(runName, listenerName).getProperty(propName);
	}

	@Override
	@CallCounted
	public void setRunListenerProperty(String runName, String listenerName,
			String propName, String value) throws UnknownRunException,
			NoUpdateException, NoListenerException {
		TavernaRun w = support.getRun(runName);
		support.permitUpdate(w);
		Listener l = support.getListener(w, listenerName);
		if (propName == null || value == null)
			throw new NoListenerException("problem setting property");
		try {
			l.getProperty(propName); // sanity check!
			l.setProperty(propName, value);
		} catch (RuntimeException e) {
			throw new NoListenerException("problem setting property: "
					+ e.getMessage(), e);
		}
	}

	@Override
	@CallCounted
	public InputDescription getRunInputs(String runName)
			throws UnknownRunException {
		return new InputDescription(support.getRun(runName));
	}

	@Override
	@CallCounted
	public String getRunOutputBaclavaFile(String runName)
			throws UnknownRunException {
		return support.getRun(runName).getOutputBaclavaFile();
	}

	@Override
	@CallCounted
	public void setRunInputBaclavaFile(String runName, String fileName)
			throws UnknownRunException, NoUpdateException,
			FilesystemAccessException, BadStateChangeException {
		TavernaRun w = support.getRun(runName);
		support.permitUpdate(w);
		w.setInputBaclavaFile(fileName);
	}

	@Override
	@CallCounted
	public void setRunInputPortFile(String runName, String portName,
			String portFilename) throws UnknownRunException, NoUpdateException,
			FilesystemAccessException, BadStateChangeException {
		TavernaRun w = support.getRun(runName);
		support.permitUpdate(w);
		Input i = support.getInput(w, portName);
		if (i == null)
			i = w.makeInput(portName);
		i.setFile(portFilename == null ? "" : portFilename);
	}

	@Override
	@CallCounted
	public void setRunInputPortValue(String runName, String portName,
			String portValue) throws UnknownRunException, NoUpdateException,
			BadStateChangeException {
		TavernaRun w = support.getRun(runName);
		support.permitUpdate(w);
		Input i = support.getInput(w, portName);
		if (i == null)
			i = w.makeInput(portName);
		i.setValue(portValue == null ? "" : portValue);
	}

	@Override
	@CallCounted
	public void setRunOutputBaclavaFile(String runName, String outputFile)
			throws UnknownRunException, NoUpdateException,
			FilesystemAccessException, BadStateChangeException {
		TavernaRun w = support.getRun(runName);
		support.permitUpdate(w);
		w.setOutputBaclavaFile(outputFile);
	}

	@Override
	@CallCounted
	public org.taverna.server.port_description.InputDescription getRunInputDescriptor(
			String runName) throws UnknownRunException {
		return cdBuilder.makeInputDescriptor(support.getRun(runName), null);
	}

	@Override
	@CallCounted
	public String getStatus() {
		return support.getAllowNewWorkflowRuns() ? "operational" : "suspended";
	}

	// -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
	// SUPPORT METHODS

	@Override
	public boolean initObsoleteSOAPSecurity(@NonNull TavernaSecurityContext c) {
		try {
			javax.xml.ws.handler.MessageContext msgCtxt = (jaxws == null ? null
					: jaxws.getMessageContext());
			if (msgCtxt == null)
				return true;
			c.initializeSecurityFromSOAPContext(msgCtxt);
			return false;
		} catch (IllegalStateException e) {
			/* ignore; not much we can do */
			return true;
		}
	}

	@java.lang.SuppressWarnings("null")
	@Override
	public boolean initObsoleteRESTSecurity(@NonNull TavernaSecurityContext c) {
		if (jaxrsHeaders == null)
			return true;
		c.initializeSecurityFromRESTContext(jaxrsHeaders);
		return false;
	}

	/**
	 * A creator of substitute {@link URI} builders.
	 * 
	 * @return A URI builder configured so that it takes a path parameter that
	 *         corresponds to the run ID (but with no such ID applied).
	 */
	@java.lang.SuppressWarnings("null")
	@NonNull
	UriBuilder getRunUriBuilder() {
		return getBaseUriBuilder().path("runs/{uuid}");
	}

	@java.lang.SuppressWarnings("null")
	@Override
	@NonNull
	public UriBuilder getRunUriBuilder(@NonNull TavernaRun run) {
		return fromUri(getRunUriBuilder().build(run.getId()));
	}

	private final String DEFAULT_HOST = "localhost:8080"; // Crappy default

	private String getHostLocation() {
		@java.lang.SuppressWarnings("unchecked")
		Map<String, List<String>> headers = (Map<String, List<String>>) jaxws
				.getMessageContext().get(HTTP_REQUEST_HEADERS);
		if (headers != null) {
			List<String> host = headers.get("HOST");
			if (host != null && !host.isEmpty())
				return host.get(0);
		}
		return DEFAULT_HOST;
	}

	@java.lang.SuppressWarnings("null")
	@NonNull
	private URI getPossiblyInsecureBaseUri() {
		// See if JAX-RS can supply the info
		UriInfo ui = getUriInfo();
		if (ui != null && ui.getBaseUri() != null)
			return ui.getBaseUri();
		// See if JAX-WS *cannot* supply the info
		if (jaxws == null || jaxws.getMessageContext() == null)
			// Hack to make the test suite work
			return URI.create("http://" + DEFAULT_HOST
					+ "/taverna-server/rest/");
		String pathInfo = (String) jaxws.getMessageContext().get(PATH_INFO);
		pathInfo = pathInfo.replaceFirst("/soap$", "/rest/");
		pathInfo = pathInfo.replaceFirst("/rest/.+$", "/rest/");
		return URI.create("http://" + getHostLocation() + pathInfo);
	}

	@java.lang.SuppressWarnings("null")
	@Override
	@NonNull
	public UriBuilder getBaseUriBuilder() {
		return secure(fromUri(getPossiblyInsecureBaseUri()));
	}

	@Override
	@Nullable
	public String resolve(@Nullable String uri) {
		if (uri == null)
			return null;
		return secure(getPossiblyInsecureBaseUri(), uri).toString();
	}

	@java.lang.SuppressWarnings("null")
	@NonNull
	private Map<String, TavernaRun> runs() {
		return runStore.listRuns(support.getPrincipal(), policy);
	}
}

/**
 * RESTful interface to the policies of a Taverna Server installation.
 * 
 * @author Donal Fellows
 */
class PolicyREST implements PolicyView, SupportAware {
	private TavernaServerSupport support;
	private Policy policy;
	private ListenerFactory listenerFactory;
	private NotificationEngine notificationEngine;

	@Override
	public void setSupport(TavernaServerSupport support) {
		this.support = support;
	}

	@Required
	public void setPolicy(Policy policy) {
		this.policy = policy;
	}

	@Required
	public void setListenerFactory(ListenerFactory listenerFactory) {
		this.listenerFactory = listenerFactory;
	}

	@Required
	public void setNotificationEngine(NotificationEngine notificationEngine) {
		this.notificationEngine = notificationEngine;
	}

	@Override
	@NonNull
	public PolicyDescription getDescription(@NonNull UriInfo ui) {
		return new PolicyDescription(ui);
	}

	@Override
	@CallCounted
	public int getMaxSimultaneousRuns() {
		Integer limit = policy.getMaxRuns(support.getPrincipal());
		if (limit == null)
			return policy.getMaxRuns();
		return min(limit.intValue(), policy.getMaxRuns());
	}

	@Override
	@CallCounted
	@NonNull
	public PermittedListeners getPermittedListeners() {
		return new PermittedListeners(
				listenerFactory.getSupportedListenerTypes());
	}

	@Override
	@CallCounted
	@NonNull
	public PermittedWorkflows getPermittedWorkflows() {
		return new PermittedWorkflows(policy.listPermittedWorkflowURIs(support
				.getPrincipal()));
	}

	@Override
	@CallCounted
	@NonNull
	public EnabledNotificationFabrics getEnabledNotifiers() {
		return new EnabledNotificationFabrics(
				notificationEngine.listAvailableDispatchers());
	}
}
