/*
 * Copyright (C) 2011 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.master.admin;

import static java.util.Arrays.asList;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.taverna.server.master.common.Roles.ADMIN;
import static org.taverna.server.master.common.Uri.secure;
import static org.taverna.server.master.utils.RestUtils.opt;

import java.io.FileNotFoundException;
import java.io.IOException;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Required;
import org.taverna.server.master.ManagementModel;
import org.taverna.server.master.exceptions.GeneralFailureException;
import org.taverna.server.master.factories.ConfigurableRunFactory;
import org.taverna.server.master.identity.User;
import org.taverna.server.master.identity.UserStore;
import org.taverna.server.master.usage.UsageRecordRecorder;
import org.taverna.server.master.utils.InvocationCounter;
import org.taverna.server.master.worker.RunDBSupport;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * The administration interface to Taverna Server.
 * 
 * @author Donal Fellows
 */
public class AdminBean implements Admin {
	@Required
	public void setState(ManagementModel state) {
		this.state = state;
	}

	@Required
	public void setCounter(InvocationCounter counter) {
		this.counter = counter;
	}

	@Required
	public void setRunDB(RunDBSupport runDB) {
		this.runDB = runDB;
	}

	@Required
	public void setFactory(ConfigurableRunFactory factory) {
		this.factory = factory;
	}

	@Required
	public void setUsageRecords(UsageRecordRecorder usageRecords) {
		this.usageRecords = usageRecords;
	}

	@Required
	public void setUserStore(UserStore userStore) {
		this.userStore = userStore;
	}

	public void setAdminHtmlFile(String filename) {
		this.adminHtmlFile = filename;
	}

	public void setResourceRoot(String root) {
		this.resourceRoot = root;
	}

	protected byte[] getResource(String name) throws IOException {
		if (AdminBean.class.getResource(name) == null)
			throw new FileNotFoundException(name);
		return IOUtils.toByteArray(AdminBean.class.getResourceAsStream(name));
	}

	private ManagementModel state;
	private InvocationCounter counter;
	private RunDBSupport runDB;
	private ConfigurableRunFactory factory;
	private UsageRecordRecorder usageRecords;
	private UserStore userStore;
	private String adminHtmlFile = "/admin.html";
	private String resourceRoot = "/static/";

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	Response getUserInterface() throws IOException {
		return Response.ok(getResource(adminHtmlFile), "text/html").build();
	}

	@RolesAllowed(ADMIN)
	public Response getStaticResource(String file) throws IOException {
		if (file.matches("^[-_.a-zA-Z0-9]+$")) {
			String type = "application/octet-stream";
			if (file.endsWith(".html"))
				type = "text/html";
			else if (file.endsWith(".js"))
				type = "text/javascript";
			else if (file.endsWith(".jpg"))
				type = "image/jpeg";
			else if (file.endsWith(".gif"))
				type = "image/gif";
			else if (file.endsWith(".png"))
				type = "image/png";
			else if (file.endsWith(".svg"))
				type = "image/svg+xml";
			else if (file.endsWith(".css"))
				type = "text/css";
			try {
				return Response.ok(getResource(resourceRoot + file), type)
						.build();
			} catch (IOException e) {
				// ignore; we just treat as 404
			}
		}
		return Response.status(NOT_FOUND).entity("no such resource").build();
	}

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	AdminDescription getDescription(UriInfo ui) {
		return new AdminDescription(ui);
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsRoot() {
		return opt();
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public boolean getAllowNew() {
		return state.getAllowNewWorkflowRuns();
	}

	@RolesAllowed(ADMIN)
	@Override
	public boolean setAllowNew(boolean newValue) {
		state.setAllowNewWorkflowRuns(newValue);
		return state.getAllowNewWorkflowRuns();
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsAllowNew() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public boolean getLogWorkflows() {
		return state.getLogIncomingWorkflows();
	}

	@RolesAllowed(ADMIN)
	@Override
	public boolean setLogWorkflows(boolean newValue) {
		state.setLogIncomingWorkflows(newValue);
		return state.getLogIncomingWorkflows();
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsLogWorkflows() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public boolean getLogFaults() {
		return state.getLogOutgoingExceptions();
	}

	@RolesAllowed(ADMIN)
	@Override
	public boolean setLogFaults(boolean newValue) {
		state.setLogOutgoingExceptions(newValue);
		return state.getLogOutgoingExceptions();
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsLogFaults() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	String getURFile() {
		return state.getUsageRecordLogFile();
	}

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	String setURFile(@NonNull String newValue) {
		state.setUsageRecordLogFile(newValue);
		return state.getUsageRecordLogFile();
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsURFile() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public int invokeCount() {
		return counter.getCount();
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsInvokationCount() {
		return opt();
	}

	@RolesAllowed(ADMIN)
	@Override
	public int runCount() {
		return runDB.countRuns();
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsRunCount() {
		return opt();
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	String getRegistryHost() {
		return factory.getRegistryHost();
	}

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	String setRegistryHost(@NonNull String newValue) {
		factory.setRegistryHost(newValue);
		return factory.getRegistryHost();
	}

	@Override
	public Response optionsRegistryHost() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public int getRegistryPort() {
		return factory.getRegistryPort();
	}

	@RolesAllowed(ADMIN)
	@Override
	public int setRegistryPort(int newValue) {
		factory.setRegistryPort(newValue);
		return factory.getRegistryPort();
	}

	@Override
	public Response optionsRegistryPort() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public int getRunLimit() {
		return factory.getMaxRuns();
	}

	@RolesAllowed(ADMIN)
	@Override
	public int setRunLimit(int newValue) {
		factory.setMaxRuns(newValue);
		return factory.getMaxRuns();
	}

	@Override
	public Response optionsRunLimit() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public int getDefaultLifetime() {
		return factory.getDefaultLifetime();
	}

	@RolesAllowed(ADMIN)
	@Override
	public int setDefaultLifetime(int newValue) {
		factory.setDefaultLifetime(newValue);
		return factory.getDefaultLifetime();
	}

	@Override
	public Response optionsDefaultLifetime() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public StringList currentRuns() {
		StringList result = new StringList();
		result.string = runDB.listRunNames();
		return result;
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsCurrentRuns() {
		return opt();
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	String getJavaBinary() {
		return factory.getJavaBinary();
	}

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	String setJavaBinary(@NonNull String newValue) {
		factory.setJavaBinary(newValue);
		return factory.getJavaBinary();
	}

	@Override
	public Response optionsJavaBinary() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	StringList getExtraArguments() {
		String[] xargs = factory.getExtraArguments();
		StringList result = new StringList();
		result.string = asList(xargs == null ? new String[0] : xargs);
		return result;
	}

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	StringList setExtraArguments(@NonNull StringList newValue) {
		if (newValue == null || newValue.string == null)
			factory.setExtraArguments(new String[0]);
		else
			factory.setExtraArguments(newValue.string
					.toArray(new String[newValue.string.size()]));
		StringList result = new StringList();
		result.string = asList(factory.getExtraArguments());
		return result;
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsExtraArguments() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	String getServerWorkerJar() {
		return factory.getServerWorkerJar();
	}

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	String setServerWorkerJar(@NonNull String newValue) {
		factory.setServerWorkerJar(newValue);
		return factory.getServerWorkerJar();
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsServerWorkerJar() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	String getExecuteWorkflowScript() {
		return factory.getExecuteWorkflowScript();
	}

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	String setExecuteWorkflowScript(@NonNull String newValue) {
		factory.setExecuteWorkflowScript(newValue);
		return factory.getExecuteWorkflowScript();
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsExecuteWorkflowScript() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public int getRegistrationWaitSeconds() {
		return factory.getWaitSeconds();
	}

	@RolesAllowed(ADMIN)
	@Override
	public int setRegistrationWaitSeconds(int newValue) {
		factory.setWaitSeconds(newValue);
		return factory.getWaitSeconds();
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsRegistrationWaitSeconds() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public int getRegistrationPollMillis() {
		return factory.getSleepTime();
	}

	@RolesAllowed(ADMIN)
	@Override
	public int setRegistrationPollMillis(int newValue) {
		factory.setSleepTime(newValue);
		return factory.getSleepTime();
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsRegistrationPollMillis() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	@Nullable
	public String getRunasPasswordFile() {
		return factory.getPasswordFile();
	}

	@RolesAllowed(ADMIN)
	@Override
	@Nullable
	public String setRunasPasswordFile(@NonNull String newValue) {
		factory.setPasswordFile(newValue);
		return factory.getPasswordFile();
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsRunasPasswordFile() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	String getServerForkerJar() {
		return factory.getServerForkerJar();
	}

	@RolesAllowed(ADMIN)
	@Override
	public @NonNull
	String setServerForkerJar(@NonNull String newValue) {
		factory.setServerForkerJar(newValue);
		return factory.getServerForkerJar();
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsServerForkerJar() {
		return opt("PUT");
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public int startupTime() {
		return factory.getLastStartupCheckCount();
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsStartupTime() {
		return opt();
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public Integer lastExitCode() {
		return factory.getLastExitCode();
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsLastExitCode() {
		return opt();
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public StringList factoryProcessMapping() {
		StringList result = new StringList();
		result.string = asList(factory.getFactoryProcessMapping());
		return result;
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsFactoryProcessMapping() {
		return opt();
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public URList usageRecords() {
		URList result = new URList();
		result.usageRecord = usageRecords.getUsageRecords();
		return result;
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsUsageRecords() {
		return opt();
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public UserList users(UriInfo ui) {
		UserList ul = new UserList();
		UriBuilder ub = secure(ui).path("{id}");
		for (String user : userStore.getUserNames())
			ul.user.add(ub.build(user));
		return ul;
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsUsers() {
		return opt("POST");
	}

	@RolesAllowed(ADMIN)
	@Override
	public UserDesc user(String username) {
		UserDesc desc = new UserDesc();
		User u = userStore.getUser(username);
		desc.username = u.getUsername();
		desc.localUserId = u.getLocalUsername();
		desc.admin = u.isAdmin();
		desc.enabled = u.isEnabled();
		return desc;
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsUser(String username) {
		return opt("PUT", "DELETE");
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response useradd(UserDesc userdesc, @NonNull UriInfo ui) {
		if (userdesc.username == null)
			throw new IllegalArgumentException("no user name supplied");
		if (userdesc.password == null)
			userdesc.password = randomUUID().toString();
		userStore.addUser(userdesc.username, userdesc.password, false);
		if (userdesc.localUserId != null)
			userStore.setUserLocalUser(userdesc.username, userdesc.localUserId);
		if (userdesc.admin != null && userdesc.admin)
			userStore.setUserAdmin(userdesc.username, true);
		if (userdesc.enabled != null && userdesc.enabled)
			userStore.setUserEnabled(userdesc.username, true);
		return created(secure(ui).path("{id}").build(userdesc.username))
				.build();
	}

	@RolesAllowed(ADMIN)
	@Override
	public UserDesc userset(String username, UserDesc userdesc) {
		if (userdesc.password != null)
			userStore.setUserPassword(username, userdesc.password);
		if (userdesc.localUserId != null)
			userStore.setUserLocalUser(username, userdesc.localUserId);
		if (userdesc.admin != null)
			userStore.setUserAdmin(username, userdesc.admin);
		if (userdesc.enabled != null)
			userStore.setUserEnabled(username, userdesc.enabled);
		userdesc = null; // Stop reuse!

		UserDesc desc = new UserDesc();
		User u = userStore.getUser(username);
		desc.username = u.getUsername();
		desc.localUserId = u.getLocalUsername();
		desc.admin = u.isAdmin();
		desc.enabled = u.isEnabled();
		return desc;
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response userdel(String username) {
		userStore.deleteUser(username);
		return noContent().build();
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public int operatingCount() {
		try {
			return factory.getOperatingCount();
		} catch (Exception e) {
			throw new GeneralFailureException(e);
		}
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsOperatingCount() {
		return opt();
	}

	// /////////////////////////////////////////////////////

	@RolesAllowed(ADMIN)
	@Override
	public int getOperatingLimit() {
		return factory.getOperatingLimit();
	}

	@RolesAllowed(ADMIN)
	@Override
	public int setOperatingLimit(int operatingLimit) {
		factory.setOperatingLimit(operatingLimit);
		return factory.getOperatingLimit();
	}

	@RolesAllowed(ADMIN)
	@Override
	public Response optionsOperatingLimit() {
		return opt("PUT");
	}
}
