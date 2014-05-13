package org.taverna.server.master.scape;

import static java.lang.Thread.sleep;
import static org.taverna.server.master.common.Status.Operating;
import static org.taverna.server.master.scape.ScapeExecutor.serializeXml;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.ws.rs.core.UriInfo;
import javax.xml.transform.TransformerException;

import org.apache.commons.logging.Log;
import org.taverna.server.master.common.Credential.Password;
import org.taverna.server.master.common.Trust;
import org.taverna.server.master.common.Workflow;
import org.taverna.server.master.exceptions.BadStateChangeException;
import org.taverna.server.master.exceptions.InvalidCredentialException;
import org.taverna.server.master.exceptions.NoCreateException;
import org.taverna.server.master.exceptions.UnknownRunException;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.interfaces.TavernaSecurityContext;
import org.taverna.server.master.rest.scape.ExecutionStateChange.State;
import org.taverna.server.master.utils.CertificateChainFetcher;
import org.taverna.server.port_description.InputDescription;
import org.taverna.server.port_description.InputDescription.InputPort;
import org.w3c.dom.Element;

import at.ac.tuwien.ifs.dp.plato.Object;

/**
 * How to make a SCAPE job actually start executing.
 * 
 * @author Donal Fellows
 */
class ScapeShepherd implements Runnable {
	public static final String SLA_INPUT = "SLA";
	public static final String OBJECT_LIST_INPUT = "digitalObjects";
	public static final String REPOSITORY_DIRECTORY_INPUT = "RepositoryDirectory";
	public static final String DESTINATION_REPOSITORY_INPUT = "DestinationRepository";
	public static final String SOURCE_REPOSITORY_INPUT = "SourceRepository";
	public static final String CREATOR_IDENTITY_INPUT = "CreatorIdentity";

	@Nonnull
	private final ScapeExecutor context;
	private final CertificateChainFetcher ccf;
	private final Log log;
	@Nonnull
	private final String planId;
	@Nonnull
	private final List<Object> objs;
	@Nullable
	private final Element schematron;
	@Nonnull
	private final URI base;
	@Nonnull
	private final String jobId;
	@Nonnull
	private final TavernaRun run;
	@Nonnull
	private final InputDescription inDesc;

	ScapeShepherd(@Nonnull ScapeExecutor context, @Nonnull String planId,
			@Nonnull List<Object> objs, @Nullable Element schematron,
			@Nonnull Workflow w, @Nonnull UriInfo ui) throws NoCreateException,
			UnknownRunException {
		this.context = context;
		this.ccf = context.ccf;
		this.log = context.log;
		this.planId = planId;
		this.objs = objs;
		this.schematron = schematron;
		jobId = context.support.buildWorkflow(w);
		context.initForJob(jobId, planId);
		/* Warning: do not move UriInfo across threads */
		base = ui.getBaseUri();
		run = context.run(jobId);
		inDesc = context.cb.makeInputDescriptor(run, ui);
	}

	String startTask() {
		Thread worker = new Thread(this);
		worker.setDaemon(true);
		worker.setName("Taverna Server: SCAPE job initialization: " + jobId);
		worker.start();
		return jobId;
	}

	@Override
	public void run() {
		try {
			executeWorkflow();
		} catch (Exception e) {
			log.warn("failed to initialize SCAPE workflow", e);
		}
	}

	private void executeWorkflow() throws BadStateChangeException,
			TransformerException, InvalidCredentialException, IOException,
			GeneralSecurityException {
		Set<String> inputs = new HashSet<String>();
		for (InputPort o : inDesc.input)
			inputs.add(o.name);
		if (inputs.contains(CREATOR_IDENTITY_INPUT))
			initCreatorIdentity();
		run.setGenerateProvenance(true);
		initRepositories();
		initObjects();
		if (schematron != null)
			initSLA(schematron);
		initSecurity();

		Date deadline = new Date();
		deadline.setTime(deadline.getTime() + context.timeout);
		run.setExpiry(deadline);

		setExecuting();
		context.notifyPlanService(planId, State.InProgress,
				"Commenced execution using jobID=%s on %s", jobId, base);
	}

	private void initCreatorIdentity() throws BadStateChangeException {
		run.makeInput(CREATOR_IDENTITY_INPUT).setValue(planId);
	}

	private void initRepositories() throws BadStateChangeException {
		run.makeInput(SOURCE_REPOSITORY_INPUT).setValue(context.repository);
		run.makeInput(DESTINATION_REPOSITORY_INPUT)
				.setValue(context.repository);
		run.makeInput(REPOSITORY_DIRECTORY_INPUT).setValue(context.repoDir);
	}

	private void initObjects() throws BadStateChangeException {
		StringBuffer sb = new StringBuffer();
		for (Object object : objs)
			sb.append(object.getUid()).append('\n');
		run.makeInput(OBJECT_LIST_INPUT).setValue(sb.toString());
	}

	private void initSLA(@Nonnull Element schematron)
			throws BadStateChangeException, TransformerException {
		run.makeInput(SLA_INPUT).setValue(serializeXml(schematron));
	}

	private void initSecurity() throws InvalidCredentialException, IOException,
			GeneralSecurityException {
		TavernaSecurityContext ctxt = run.getSecurityContext();

		Password pw = context.synthesizeLoginCredential();
		ctxt.validateCredential(pw);
		ctxt.addCredential(pw);

		Trust t = new Trust();
		t.loadedCertificates = ccf.getTrustsForURI(pw.serviceURI);
		if (t.loadedCertificates != null)
			ctxt.addTrusted(t);
	}

	private void setExecuting() throws BadStateChangeException {
		try {
			while (run.setStatus(Operating) != null)
				sleep(5000);
		} catch (InterruptedException e) {
			throw new BadStateChangeException(
					"interrupted while trying to start");
		}
	}
}