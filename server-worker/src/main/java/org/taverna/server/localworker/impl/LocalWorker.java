/*
 * Copyright (C) 2010-2012 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.localworker.impl;

import static java.lang.Runtime.getRuntime;
import static java.lang.System.getProperty;
import static java.lang.System.out;
import static java.lang.management.ManagementFactory.getRuntimeMXBean;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.UUID.randomUUID;
import static org.apache.commons.io.FileUtils.forceDelete;
import static org.apache.commons.io.FileUtils.forceMkdir;
import static org.apache.commons.io.FileUtils.writeByteArrayToFile;
import static org.apache.commons.io.FileUtils.writeLines;
import static org.taverna.server.localworker.api.Constants.HELIO_TOKEN_NAME;
import static org.taverna.server.localworker.api.Constants.KEYSTORE_FILE;
import static org.taverna.server.localworker.api.Constants.KEYSTORE_PASSWORD;
import static org.taverna.server.localworker.api.Constants.SECURITY_DIR_NAME;
import static org.taverna.server.localworker.api.Constants.SHARED_DIR_PROP;
import static org.taverna.server.localworker.api.Constants.SUBDIR_LIST;
import static org.taverna.server.localworker.api.Constants.SYSTEM_ENCODING;
import static org.taverna.server.localworker.api.Constants.TRUSTSTORE_FILE;
import static org.taverna.server.localworker.impl.utils.FilenameVerifier.getValidatedFile;
import static org.taverna.server.localworker.remote.RemoteStatus.Finished;
import static org.taverna.server.localworker.remote.RemoteStatus.Initialized;
import static org.taverna.server.localworker.remote.RemoteStatus.Operating;
import static org.taverna.server.localworker.remote.RemoteStatus.Stopped;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.taverna.server.localworker.api.Worker;
import org.taverna.server.localworker.api.WorkerFactory;
import org.taverna.server.localworker.remote.IllegalStateTransitionException;
import org.taverna.server.localworker.remote.ImplementationException;
import org.taverna.server.localworker.remote.RemoteDirectory;
import org.taverna.server.localworker.remote.RemoteInput;
import org.taverna.server.localworker.remote.RemoteListener;
import org.taverna.server.localworker.remote.RemoteSecurityContext;
import org.taverna.server.localworker.remote.RemoteSingleRun;
import org.taverna.server.localworker.remote.RemoteStatus;
import org.taverna.server.localworker.remote.StillWorkingOnItException;
import org.taverna.server.localworker.server.UsageRecordReceiver;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;

/**
 * This class implements one side of the connection between the Taverna Server
 * master server and this process. It delegates to a {@link Worker} instance the
 * handling of actually running a workflow.
 * 
 * @author Donal Fellows
 * @see DirectoryDelegate
 * @see FileDelegate
 * @see WorkerCore
 */
@SuppressWarnings({ "SE_BAD_FIELD", "SE_NO_SERIALVERSIONID" })
@java.lang.SuppressWarnings("serial")
public class LocalWorker extends UnicastRemoteObject implements RemoteSingleRun {
	// ----------------------- CONSTANTS -----------------------

	/** Handle to the directory containing the security info. */
	static final File SECURITY_DIR;
	static final String SLASHTEMP;
	static {
		SLASHTEMP = getProperty("java.io.tmpdir");
		File home = new File(getProperty("user.home"));
		// If we can't write to $HOME (i.e., we're in an odd deployment) use
		// the official version of /tmp/$PID as a fallback.
		if (!home.canWrite())
			home = new File(SLASHTEMP, getRuntimeMXBean().getName());
		SECURITY_DIR = new File(home, SECURITY_DIR_NAME);
	}

	// ----------------------- VARIABLES -----------------------

	/**
	 * Magic flag used to turn off problematic code when testing inside CI
	 * environment.
	 */
	static boolean DO_MKDIR = true;

	/** What to use to run a workflow engine. */
	@NonNull
	private final String executeWorkflowCommand;
	/** What workflow to run. */
	@NonNull
	private final String workflow;
	/** The remote access object for the working directory. */
	@NonNull
	private final DirectoryDelegate baseDir;
	/** What inputs to pass as files. */
	@NonNull
	final Map<String, String> inputFiles;
	/** What inputs to pass as files (as file refs). */
	@NonNull
	final Map<String, File> inputRealFiles;
	/** What inputs to pass as direct values. */
	@NonNull
	final Map<String, String> inputValues;
	/** The interface to the workflow engine subprocess. */
	@NonNull
	private final Worker core;
	/** Our descriptor token (UUID). */
	@NonNull
	private final String masterToken;
	/**
	 * The root working directory for a workflow run, or <tt>null</tt> if it has
	 * been deleted.
	 */
	@Nullable
	private File base;
	/**
	 * When did this workflow start running, or <tt>null</tt> for
	 * "never/not yet".
	 */
	@Nullable
	private Date start;
	/**
	 * When did this workflow finish running, or <tt>null</tt> for
	 * "never/not yet".
	 */
	@Nullable
	private Date finish;
	/** The cached status of the workflow run. */
	@NonNull
	RemoteStatus status;
	/**
	 * The name of the input Baclava document, or <tt>null</tt> to not do it
	 * that way.
	 */
	@Nullable
	String inputBaclava;
	/**
	 * The name of the output Baclava document, or <tt>null</tt> to not do it
	 * that way.
	 */
	@Nullable
	String outputBaclava;
	/**
	 * The file containing the input Baclava document, or <tt>null</tt> to not
	 * do it that way.
	 */
	@Nullable
	private File inputBaclavaFile;
	/**
	 * The file containing the output Baclava document, or <tt>null</tt> to not
	 * do it that way.
	 */
	@Nullable
	private File outputBaclavaFile;
	/**
	 * Registered shutdown hook so that we clean up when this process is killed
	 * off, or <tt>null</tt> if that is no longer necessary.
	 */
	@Nullable
	Thread shutdownHook;
	/** Location for security information to be written to. */
	File securityDirectory;
	/**
	 * Password to use to encrypt security information.
	 */
	char[] keystorePassword = KEYSTORE_PASSWORD;
	/** Additional server-specified environment settings. */
	@NonNull
	final Map<String, String> environment = new HashMap<String, String>();
	/** Additional server-specified java runtime settings. */
	@NonNull
	final List<String> runtimeSettings = new ArrayList<String>();
	URL interactionFeedURL;
	URL webdavURL;

	// ----------------------- METHODS -----------------------

	/**
	 * @param executeWorkflowCommand
	 *            The script used to execute workflows.
	 * @param workflow
	 *            The workflow to execute.
	 * @param workerClass
	 *            The class to instantiate as our local representative of the
	 *            run.
	 * @param urReceiver
	 *            The remote class to report the generated usage record(s) to.
	 * @param id
	 *            The UUID to use, or <tt>null</tt> if we are to invent one.
	 * @param seedEnvironment
	 *            The key/value pairs to seed the worker subprocess environment
	 *            with.
	 * @param javaParams
	 *            Parameters to pass to the worker subprocess java runtime
	 *            itself.
	 * @param workerFactory
	 *            How to make instances of the low-level worker objects.
	 * @throws RemoteException
	 *             If registration of the worker fails.
	 * @throws ImplementationException
	 *             If something goes wrong during local setup.
	 */
	protected LocalWorker(@NonNull String executeWorkflowCommand,
			@NonNull String workflow, @NonNull UsageRecordReceiver urReceiver,
			@Nullable UUID id, @NonNull Map<String, String> seedEnvironment,
			@NonNull List<String> javaParams,
			@NonNull WorkerFactory workerFactory) throws RemoteException,
			ImplementationException {
		super();
		if (id == null)
			masterToken = randomUUID().toString();
		else
			masterToken = id.toString();
		this.workflow = workflow;
		this.executeWorkflowCommand = executeWorkflowCommand;
		String sharedDir = getProperty(SHARED_DIR_PROP, SLASHTEMP);
		File b = base = new File(sharedDir, masterToken);
		out.println("about to create " + b);
		try {
			forceMkdir(b);
			for (String subdir : SUBDIR_LIST) {
				new File(b, subdir).mkdir();
			}
		} catch (IOException e) {
			throw new ImplementationException(
					"problem creating run working directory", e);
		}
		baseDir = new DirectoryDelegate(b);
		inputFiles = new HashMap<String, String>();
		inputRealFiles = new HashMap<String, File>();
		inputValues = new HashMap<String, String>();
		environment.putAll(seedEnvironment);
		runtimeSettings.addAll(javaParams);
		try {
			core = workerFactory.makeInstance();
		} catch (Exception e) {
			out.println("problem when creating core worker implementation");
			e.printStackTrace(out);
			throw new ImplementationException(
					"problem when creating core worker implementation", e);
		}
		core.setURReceiver(urReceiver);
		Thread t = new Thread(new Runnable() {
			/**
			 * Kill off the worker launched by the core.
			 */
			@Override
			public void run() {
				try {
					shutdownHook = null;
					destroy();
				} catch (ImplementationException e) {
					// Absolutely nothing we can do here
				}
			}
		});
		getRuntime().addShutdownHook(t);
		shutdownHook = t;
		status = Initialized;
	}

	@Override
	public void destroy() throws ImplementationException {
		killWorkflowSubprocess();
		removeFromShutdownHooks();
		// Is this it?
		deleteWorkingDirectory();
		deleteSecurityManagerDirectory();
		core.deleteLocalResources();
	}

	private void killWorkflowSubprocess() {
		if (status != Finished && status != Initialized)
			try {
				core.killWorker();
				if (finish == null)
					finish = new Date();
			} catch (Exception e) {
				out.println("problem when killing worker");
				e.printStackTrace(out);
			}
	}

	private void removeFromShutdownHooks() throws ImplementationException {
		try {
			if (shutdownHook != null)
				getRuntime().removeShutdownHook(shutdownHook);
		} catch (RuntimeException e) {
			throw new ImplementationException("problem removing shutdownHook",
					e);
		} finally {
			shutdownHook = null;
		}
	}

	private void deleteWorkingDirectory() throws ImplementationException {
		try {
			if (base != null)
				forceDelete(base);
		} catch (IOException e) {
			out.println("problem deleting working directory");
			e.printStackTrace(out);
			throw new ImplementationException(
					"problem deleting working directory", e);
		} finally {
			base = null;
		}
	}

	private void deleteSecurityManagerDirectory()
			throws ImplementationException {
		try {
			if (securityDirectory != null)
				forceDelete(securityDirectory);
		} catch (IOException e) {
			out.println("problem deleting security directory");
			e.printStackTrace(out);
			throw new ImplementationException(
					"problem deleting security directory", e);
		} finally {
			securityDirectory = null;
		}
	}

	@Override
	public void addListener(RemoteListener listener) throws RemoteException,
			ImplementationException {
		throw new ImplementationException("not implemented");
	}

	@Override
	public String getInputBaclavaFile() {
		return inputBaclava;
	}

	@Override
	public List<RemoteInput> getInputs() throws RemoteException {
		ArrayList<RemoteInput> result = new ArrayList<RemoteInput>();
		for (String name : inputFiles.keySet())
			if (name != null)
				result.add(new InputDelegate(name));
		return result;
	}

	@Override
	public List<String> getListenerTypes() {
		return emptyList();
	}

	@Override
	public List<RemoteListener> getListeners() {
		return singletonList(core.getDefaultListener());
	}

	@Override
	public String getOutputBaclavaFile() {
		return outputBaclava;
	}

	@SuppressWarnings("SE_INNER_CLASS")
	class SecurityDelegate extends UnicastRemoteObject implements
			RemoteSecurityContext {
		private void setPrivatePerms(File dir) {
			if (!dir.setReadable(false, false) || !dir.setReadable(true, true)
					|| !dir.setExecutable(false, false)
					|| !dir.setExecutable(true, true)
					|| !dir.setWritable(false, false)
					|| !dir.setWritable(true, true)) {
				out.println("warning: "
						+ "failed to set permissions on security context directory");
			}
		}

		protected SecurityDelegate(String token) throws IOException {
			super();
			if (DO_MKDIR) {
				securityDirectory = new File(SECURITY_DIR, token);
				forceMkdir(securityDirectory);
				setPrivatePerms(securityDirectory);
			}
		}

		/**
		 * Write some data to a given file in the context directory.
		 * 
		 * @param name
		 *            The name of the file to write.
		 * @param data
		 *            The bytes to put in the file.
		 * @throws RemoteException
		 *             If anything goes wrong.
		 * @throws ImplementationException
		 */
		protected void write(String name, byte[] data) throws RemoteException,
				ImplementationException {
			try {
				File f = new File(securityDirectory, name);
				writeByteArrayToFile(f, data);
			} catch (IOException e) {
				throw new ImplementationException("problem writing " + name, e);
			}
		}

		/**
		 * Write some data to a given file in the context directory.
		 * 
		 * @param name
		 *            The name of the file to write.
		 * @param data
		 *            The lines to put in the file. The
		 *            {@linkplain LocalWorker#SYSTEM_ENCODING system encoding}
		 *            will be used to do the writing.
		 * @throws RemoteException
		 *             If anything goes wrong.
		 * @throws ImplementationException
		 */
		protected void write(String name, Collection<String> data)
				throws RemoteException, ImplementationException {
			try {
				File f = new File(securityDirectory, name);
				writeLines(f, SYSTEM_ENCODING, data);
			} catch (IOException e) {
				throw new ImplementationException("problem writing " + name, e);
			}
		}

		/**
		 * Write some data to a given file in the context directory.
		 * 
		 * @param name
		 *            The name of the file to write.
		 * @param data
		 *            The line to put in the file. The
		 *            {@linkplain LocalWorker#SYSTEM_ENCODING system encoding}
		 *            will be used to do the writing.
		 * @throws RemoteException
		 *             If anything goes wrong.
		 * @throws ImplementationException
		 */
		protected void write(String name, char[] data) throws RemoteException,
				ImplementationException {
			try {
				File f = new File(securityDirectory, name);
				writeLines(f, SYSTEM_ENCODING, asList(new String(data)));
			} catch (IOException e) {
				throw new ImplementationException("problem writing " + name, e);
			}
		}

		@Override
		public void setKeystore(@Nullable byte[] keystore)
				throws RemoteException, ImplementationException {
			if (status != Initialized)
				throw new RemoteException("not initializing");
			if (keystore == null)
				throw new IllegalArgumentException("keystore may not be null");
			write(KEYSTORE_FILE, keystore);
		}

		@Override
		public void setPassword(@Nullable char[] password)
				throws RemoteException {
			if (status != Initialized)
				throw new RemoteException("not initializing");
			if (password == null)
				throw new IllegalArgumentException("password may not be null");
			keystorePassword = password.clone();
		}

		@Override
		public void setTruststore(@Nullable byte[] truststore)
				throws RemoteException, ImplementationException {
			if (status != Initialized)
				throw new RemoteException("not initializing");
			if (truststore == null)
				throw new IllegalArgumentException("truststore may not be null");
			write(TRUSTSTORE_FILE, truststore);
		}

		@Override
		public void setUriToAliasMap(
				@Nullable HashMap<URI, String> uriToAliasMap)
				throws RemoteException {
			if (status != Initialized)
				throw new RemoteException("not initializing");
			if (uriToAliasMap == null)
				return;
			ArrayList<String> lines = new ArrayList<String>();
			for (Entry<URI, String> site : uriToAliasMap.entrySet())
				lines.add(site.getKey().toASCIIString() + " " + site.getValue());
			// write(URI_ALIAS_MAP, lines);
		}

		@Override
		public void setHelioToken(String helioToken) throws RemoteException {
			if (status != Initialized)
				throw new RemoteException("not initializing");
			out.println("registering HELIO CIS token for export");
			environment.put(HELIO_TOKEN_NAME, helioToken);
		}
	}

	@Override
	public RemoteSecurityContext getSecurityContext() throws RemoteException,
			ImplementationException {
		try {
			return new SecurityDelegate(masterToken);
		} catch (RemoteException e) {
			if (e.getCause() != null)
				throw new ImplementationException(
						"problem initializing security context", e.getCause());
			throw e;
		} catch (IOException e) {
			throw new ImplementationException(
					"problem initializing security context", e);
		}
	}

	@Override
	public RemoteStatus getStatus() {
		// only state that can spontaneously change to another
		if (status == Operating) {
			status = core.getWorkerStatus();
			if (status == Finished && finish == null)
				finish = new Date();
		}
		return status;
	}

	@Override
	public RemoteDirectory getWorkingDirectory() {
		return baseDir;
	}

	File validateFilename(String filename) throws RemoteException {
		if (filename == null)
			throw new IllegalArgumentException("filename must be non-null");
		try {
			return getValidatedFile(base, filename.split("/"));
		} catch (IOException e) {
			throw new IllegalArgumentException("failed to validate filename", e);
		}
	}

	@SuppressWarnings("SE_INNER_CLASS")
	class InputDelegate extends UnicastRemoteObject implements RemoteInput {
		@NonNull
		private String name;

		InputDelegate(@NonNull String name) throws RemoteException {
			super();
			this.name = name;
			if (!inputFiles.containsKey(name)) {
				if (status != Initialized)
					throw new IllegalStateException("not initializing");
				inputFiles.put(name, null);
				inputRealFiles.put(name, null);
				inputValues.put(name, null);
			}
		}

		@Override
		public String getFile() {
			return inputFiles.get(name);
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getValue() {
			return inputValues.get(name);
		}

		@Override
		public void setFile(String file) throws RemoteException {
			if (status != Initialized)
				throw new IllegalStateException("not initializing");
			inputRealFiles.put(name, validateFilename(file));
			inputValues.put(name, null);
			inputFiles.put(name, file);
			inputBaclava = null;
		}

		@Override
		public void setValue(String value) throws RemoteException {
			if (status != Initialized)
				throw new IllegalStateException("not initializing");
			inputValues.put(name, value);
			inputFiles.put(name, null);
			inputRealFiles.put(name, null);
			inputBaclava = null;
		}
	}

	@Override
	public RemoteInput makeInput(String name) throws RemoteException {
		return new InputDelegate(name);
	}

	@Override
	public RemoteListener makeListener(String type, String configuration)
			throws RemoteException {
		throw new RemoteException("listener manufacturing unsupported");
	}

	@Override
	public void setInputBaclavaFile(String filename) throws RemoteException {
		if (status != Initialized)
			throw new IllegalStateException("not initializing");
		inputBaclavaFile = validateFilename(filename);
		for (String input : inputFiles.keySet()) {
			inputFiles.put(input, null);
			inputRealFiles.put(input, null);
			inputValues.put(input, null);
		}
		inputBaclava = filename;
	}

	@Override
	public void setOutputBaclavaFile(String filename) throws RemoteException {
		if (status != Initialized)
			throw new IllegalStateException("not initializing");
		if (filename != null)
			outputBaclavaFile = validateFilename(filename);
		else
			outputBaclavaFile = null;
		outputBaclava = filename;
	}

	@Override
	public void setStatus(RemoteStatus newStatus)
			throws IllegalStateTransitionException, RemoteException,
			ImplementationException, StillWorkingOnItException {
		if (status == newStatus)
			return;

		switch (newStatus) {
		case Initialized:
			throw new IllegalStateTransitionException(
					"may not move back to start");
		case Operating:
			switch (status) {
			case Initialized:
				boolean started;
				try {
					started = createWorker();
				} catch (Exception e) {
					throw new ImplementationException(
							"problem creating executing workflow", e);
				}
				if (!started)
					throw new StillWorkingOnItException(
							"workflow start in process");
				break;
			case Stopped:
				try {
					core.startWorker();
				} catch (Exception e) {
					throw new ImplementationException(
							"problem continuing workflow run", e);
				}
				break;
			case Finished:
				throw new IllegalStateTransitionException("already finished");
			default:
				break;
			}
			status = Operating;
			break;
		case Stopped:
			switch (status) {
			case Initialized:
				throw new IllegalStateTransitionException(
						"may only stop from Operating");
			case Operating:
				try {
					core.stopWorker();
				} catch (Exception e) {
					throw new ImplementationException(
							"problem stopping workflow run", e);
				}
				break;
			case Finished:
				throw new IllegalStateTransitionException("already finished");
			default:
				break;
			}
			status = Stopped;
			break;
		case Finished:
			switch (status) {
			case Operating:
			case Stopped:
				try {
					core.killWorker();
					if (finish == null)
						finish = new Date();
				} catch (Exception e) {
					throw new ImplementationException(
							"problem killing workflow run", e);
				}
			default:
				break;
			}
			status = Finished;
			break;
		}
	}

	private boolean createWorker() throws Exception {
		start = new Date();
		char[] pw = keystorePassword;
		if (pw == null)
			throw new IllegalStateException("null keystore password");
		keystorePassword = null;
		File securityDir = securityDirectory;
		if (securityDir == null)
			throw new IllegalStateException(
					"already deleted security directory");
		/*
		 * Do not clear the keystorePassword array here; its ownership is
		 * *transferred* to the worker core which doesn't copy it but *does*
		 * clear it after use.
		 */
		File f = base;
		if (f == null)
			throw new IllegalStateException("base directory deleted");
		return core.initWorker(this, executeWorkflowCommand, workflow, f,
				inputBaclavaFile, inputRealFiles, inputValues,
				outputBaclavaFile, securityDir, pw, environment, masterToken,
				runtimeSettings);
	}

	@Override
	public Date getFinishTimestamp() {
		Date d = finish;
		return d == null ? null : new Date(d.getTime());
	}

	@Override
	public Date getStartTimestamp() {
		Date d = start;
		return d == null ? null : new Date(d.getTime());
	}

	@Override
	public void setInteractionServiceDetails(URL feed, URL webdav) {
		interactionFeedURL = feed;
		webdavURL = webdav;
	}
}
