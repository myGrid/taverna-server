/*
 * Copyright (C) 2012 The University of Manchester
 * 
 * See the file "LICENSE" for license terms.
 */
package org.taverna.server.master.factories;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Interface to run factories for the purpose of configuration.
 * 
 * @author Donal Fellows
 */
public interface ConfigurableRunFactory extends RunFactory {
	/** Where is the registry? Getter */
	@NonNull
	String getRegistryHost();

	/** Where is the registry? Setter */
	void setRegistryHost(@NonNull String host);

	/** Where is the registry? Getter */
	int getRegistryPort();

	/** Where is the registry? Setter */
	void setRegistryPort(int port);

	/** How much can be done at once? Getter */
	int getMaxRuns();

	/** How much can be done at once? Setter */
	void setMaxRuns(int maxRuns);

	/** How long will things live? Getter */
	int getDefaultLifetime();

	/** How long will things live? Setter */
	void setDefaultLifetime(int defaultLifetime);

	/** How often do we probe for info? Getter */
	int getSleepTime();

	/** How often do we probe for info? Setter */
	void setSleepTime(int sleepTime);

	/** How long do we allow for actions? Getter */
	int getWaitSeconds();

	/** How long do we allow for actions? Setter */
	void setWaitSeconds(int seconds);

	/** How do we start the workflow engine? Getter */
	@NonNull
	String getExecuteWorkflowScript();

	/** How do we start the workflow engine? Setter */
	void setExecuteWorkflowScript(@NonNull String executeWorkflowScript);

	/** How do we start the file system access process? Getter */
	@NonNull
	String getServerWorkerJar();

	/** How do we start the file system access process? Setter */
	void setServerWorkerJar(@NonNull String serverWorkerJar);

	/**
	 * How do we start the file system access process? Extra arguments to pass.
	 * Getter
	 */
	@NonNull
	String[] getExtraArguments();

	/**
	 * How do we start the file system access process? Extra arguments to pass.
	 * Setter
	 */
	void setExtraArguments(@NonNull String[] firstArguments);

	/** Where is Java? Getter */
	@NonNull
	String getJavaBinary();

	/** Where is Java? Setter */
	void setJavaBinary(@NonNull String javaBinary);

	/** Where do we get passwords from? Getter */
	@Nullable
	String getPasswordFile();

	/** Where do we get passwords from? Setter */
	void setPasswordFile(@Nullable String newValue);

	/** How do we switch users? Getter */
	@NonNull
	String getServerForkerJar();

	/** How do we switch users? Setter */
	void setServerForkerJar(@NonNull String newValue);

	/** How many runs have there been? */
	int getTotalRuns();

	/** How long did the last subprocess startup take? */
	int getLastStartupCheckCount();

	/** What are the current runs? */
	@NonNull
	String[] getCurrentRunNames();

	/** What is the RMI ID of the factory process? */
	String getFactoryProcessName();

	/** What was the last observed exit code? */
	@Nullable
	Integer getLastExitCode();

	/** What factory process to use for a particular user? */
	@NonNull
	String[] getFactoryProcessMapping();

	/** How many runs can be operating at once? Setter */
	void setOperatingLimit(int operatingLimit);

	/** How many runs can be operating at once? Getter */
	int getOperatingLimit();

	/**
	 * How many runs are actually operating?
	 * 
	 * @throws Exception
	 *             if anything goes wrong
	 */
	int getOperatingCount() throws Exception;

	/** How do we start the RMI registry process? Getter */
	@NonNull
	String getRmiRegistryJar();

	/** How do we start the RMI registry process? Setter */
	void setRmiRegistryJar(@NonNull String rmiRegistryJar);
}
