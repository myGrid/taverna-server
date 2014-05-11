package org.taverna.server.master.scape;

import static at.ac.tuwien.ifs.dp.plato.ExecutablePlanType.T_2_FLOW;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.serverError;
import static javax.ws.rs.core.UriBuilder.fromUri;
import static javax.xml.bind.DatatypeConverter.printBase64Binary;
import static javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION;
import static javax.xml.transform.OutputKeys.STANDALONE;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.taverna.server.master.common.Roles.USER;
import static org.taverna.server.master.common.Status.Finished;
import static org.taverna.server.master.common.Status.Operating;
import static org.taverna.server.master.scape.ScapeSplicingEngine.Model.One2OneNoSchema;
import static org.taverna.server.master.scape.ScapeSplicingEngine.Model.One2OneSchema;
import static org.taverna.server.master.utils.RestUtils.opt;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;
import javax.ws.rs.core.UriInfo;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Required;
import org.taverna.server.master.ContentsDescriptorBuilder;
import org.taverna.server.master.TavernaServerSupport;
import org.taverna.server.master.common.Credential;
import org.taverna.server.master.common.Namespaces;
import org.taverna.server.master.common.Trust;
import org.taverna.server.master.common.Uri;
import org.taverna.server.master.common.Workflow;
import org.taverna.server.master.exceptions.BadStateChangeException;
import org.taverna.server.master.exceptions.FilesystemAccessException;
import org.taverna.server.master.exceptions.InvalidCredentialException;
import org.taverna.server.master.exceptions.NoCreateException;
import org.taverna.server.master.exceptions.NoDestroyException;
import org.taverna.server.master.exceptions.NoDirectoryEntryException;
import org.taverna.server.master.exceptions.NoListenerException;
import org.taverna.server.master.exceptions.NoUpdateException;
import org.taverna.server.master.exceptions.UnknownRunException;
import org.taverna.server.master.interfaces.Directory;
import org.taverna.server.master.interfaces.File;
import org.taverna.server.master.interfaces.Policy;
import org.taverna.server.master.interfaces.RunStore;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.interfaces.TavernaSecurityContext;
import org.taverna.server.master.rest.scape.ExecutionStateChange;
import org.taverna.server.master.rest.scape.ExecutionStateChange.State;
import org.taverna.server.master.rest.scape.ScapeExecutionService;
import org.taverna.server.master.scape.ScapeSplicingEngine.Model;
import org.taverna.server.master.utils.CallTimeLogger.PerfLogged;
import org.taverna.server.master.utils.CertificateChainFetcher;
import org.taverna.server.master.utils.FilenameUtils;
import org.taverna.server.master.utils.InvocationCounter.CallCounted;
import org.taverna.server.port_description.InputDescription;
import org.taverna.server.port_description.InputDescription.InputPort;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import at.ac.tuwien.ifs.dp.plato.ExecutablePlan;
import at.ac.tuwien.ifs.dp.plato.Object;
import at.ac.tuwien.ifs.dp.plato.Objects;
import at.ac.tuwien.ifs.dp.plato.PreservationActionPlan;
import at.ac.tuwien.ifs.dp.plato.QualityLevelDescription;

/**
 * SCAPE execution service implementation.
 * 
 * @author Donal Fellows
 */
@Path("/")
public class ScapeExecutor implements ScapeExecutionService {
	final JAXBContext context;
	final Log log;
	private TavernaServerSupport support;
	private RunStore runStore;
	private Policy policy;
	private ScapeSplicingEngine splicer;
	private ScapeJobDAO dao;
	private String notifyService;
	private String notifyUser;
	private String notifyPass;
	private FilenameUtils fileUtils;
	private ContentsDescriptorBuilder cb;
	private URI serviceUri;
	private long timeout;
	private String repository;
	private String repoDir;
	private CertificateChainFetcher ccf;

	public ScapeExecutor() throws JAXBException {
		context = JAXBContext.newInstance(ExecutionStateChange.class);
		log = getLog("Taverna.Server.Webapp.SCAPE");
	}

	/** Support utilities for working with workflow runs. */
	@Required
	public void setSupport(TavernaServerSupport support) {
		this.support = support;
	}

	/** The run storage engine. (A database behind the scenes.) */
	@Required
	public void setRunStore(RunStore runStore) {
		this.runStore = runStore;
	}

	/** The policy manager. */
	@Required
	public void setPolicy(Policy policy) {
		this.policy = policy;
	}

	/** The workflow splicer. */
	@Required
	public void setSplicer(ScapeSplicingEngine splicer) {
		this.splicer = splicer;
	}

	/** Engine for describing the inputs and outputs of a workflow. */
	@Required
	public void setContentsDescriptorBuilder(ContentsDescriptorBuilder cb) {
		this.cb = cb;
	}

	/** Utilities for accessing a workflow run's filesystem. */
	@Required
	public void setFileUtils(FilenameUtils fileUtils) {
		this.fileUtils = fileUtils;
	}

	/** The connection to the database. */
	@Required
	public void setDao(ScapeJobDAO dao) {
		this.dao = dao;
	}

	/** Credentials for the PMS. */
	public void setNotifyUser(String user) {
		this.notifyUser = user;
	}

	/** Credentials for the PMS. */
	public void setNotifyPassword(String pass) {
		this.notifyPass = pass;
	}

	/**
	 * The plan management service that wants to know about execution state
	 * changes.
	 */
	public void setNotifyService(String serviceURL) {
		this.notifyService = serviceURL;
		if (serviceURL == null) {
			this.notifyUser = null;
			this.notifyPass = null;
		}
	}

	/** The place that holds digital objects. */
	@Required
	public void setRepository(String repositoryURL) {
		this.repository = repositoryURL;
	}

	/** The place that holds files that are the data in digital objects. */
	@Required
	public void setRepositoryDirectory(String repositoryDirectory) {
		this.repoDir = repositoryDirectory;
	}

	/** How long an execution is given to run. */
	public void setTimeoutHours(long timeoutHours) {
		timeout = timeoutHours * 1000 * 60 * 60;
	}

	/** Used to establish trust chains for services. */
	@Required
	public void setCertificateChainFetcher(CertificateChainFetcher ccf) {
		this.ccf = ccf;
	}

	@Nonnull
	private Policy policy() {
		Policy p = policy;
		if (p == null)
			throw new WebApplicationException(serverError().entity(
					"failed to configure policy").build());
		return p;
	}

	@Nonnull
	private TavernaRun run(@Nonnull String id) throws UnknownRunException {
		Policy p = policy;
		if (p == null)
			throw new WebApplicationException(serverError().entity(
					"failed to configure policy").build());
		return runStore.getRun(support.getPrincipal(), p, id);
	}

	@Override
	@CallCounted
	@PerfLogged
	@RolesAllowed(USER)
	@Nonnull
	public Jobs listJobs(@Nonnull UriInfo ui) {
		@Nonnull
		Jobs jobs = new Jobs();
		for (Entry<String, TavernaRun> entry : runStore.listRuns(
				support.getPrincipal(), policy()).entrySet())
			if (entry != null && isScapeRun(entry.getValue()))
				jobs.job.add(new Uri(ui, "{id}", entry.getKey()));
		return jobs;
	}

	@Override
	@CallCounted
	@PerfLogged
	@RolesAllowed(USER)
	@Nonnull
	public Response startJob(@Nullable JobRequest jobRequest,
			@Nonnull UriInfo ui) throws NoCreateException {
		if (this.serviceUri == null)
			this.serviceUri = ui.getBaseUri();
		if (jobRequest == null)
			throw new BadInputException("null request received!");
		String planId = jobRequest.planId;
		if (planId == null)
			throw new BadInputException("preservation plan id is missing");
		PreservationActionPlan plan = jobRequest.preservationActionPlan;
		if (planId == null || plan == null)
			throw new BadInputException("preservation action plan is missing");

		Objects objs = plan.getObjects();
		ExecutablePlan ep = plan.getExecutablePlan();
		QualityLevelDescription qld = plan.getQualityLevelDescription();

		if (objs == null)
			throw new BadInputException("no objects to act on");
		List<Object> objectList = objs.getObject();
		if (objectList == null || objectList.size() == 0)
			throw new BadInputException("no objects to act on");
		if (ep == null)
			throw new BadInputException("the executable plan must be present");
		if (ep.getType() != T_2_FLOW)
			throw new BadInputException("that type of plan not supported");
		Element workflow = ep.getAny();
		if (workflow == null)
			throw new BadInputException("the executable plan must be present");
		if (!"workflow".equals(workflow.getLocalName())
				|| Namespaces.T2FLOW.equals(workflow.getNamespaceURI()))
			throw new BadInputException(format(
					"bad content of executable plan: {%s}%s",
					workflow.getNamespaceURI(), workflow.getLocalName()));

		@Nonnull
		String id;
		try {
			Element sla = qld == null ? null : qld.getAny();
			Workflow wf = splicer.constructWorkflow(workflow,
					pickExecutionModel(plan));
			id = new ScapeShepherd(planId, objectList, sla, wf, ui).startTask();
		} catch (NoCreateException e) {
			throw e;
		} catch (Exception e) {
			throw new NoCreateException("failed to construct workflow", e);
		}
		return created(ui.getRequestUriBuilder().path("{id}").build(id))
				.build();
	}

	@Nonnull
	private Model pickExecutionModel(PreservationActionPlan plan) {
		// TODO Find a better way of picking which model workflow to use
		// TODO Allow for characterisation plans
		QualityLevelDescription qld = plan.getQualityLevelDescription();
		if (qld == null || qld.getAny() == null)
			return One2OneNoSchema;
		return One2OneSchema;
	}

	@Override
	@CallCounted
	@PerfLogged
	@RolesAllowed(USER)
	@Nonnull
	public Job getStatus(@Nonnull String id, @Nonnull UriInfo ui)
			throws UnknownRunException {
		TavernaRun r = getScapeRun(id);
		Job job = new Job();
		job.status = r.getStatus();
		job.serverJob = new Uri(ui.getBaseUriBuilder(), "rest/runs/{id}", id);
		job.enactedWorkflow = new Uri(ui.getBaseUriBuilder(),
				"rest/runs/{id}/workflow", id);
		if (job.status == Finished) {
			job.output = new Uri(ui.getBaseUriBuilder(),
					"rest/runs/{id}/wd/out", id);
			job.provenance = new Uri(ui.getBaseUriBuilder(),
					"rest/runs/{id}/run-bundle", id);
		}
		return job;
	}

	@Override
	@CallCounted
	@PerfLogged
	@RolesAllowed(USER)
	@Nonnull
	public Response deleteJob(@Nonnull String id) throws UnknownRunException,
			NoDestroyException {
		getScapeRun(id);
		support.unregisterRun(id, null);
		return Response.noContent().build();
	}

	private boolean isScapeRun(@Nullable TavernaRun run) {
		if (run == null)
			return false;
		return dao.isScapeJob(run.getId());
	}

	/**
	 * @param id
	 *            Job ID
	 * @return Job object
	 * @throws BadInputException
	 *             If the ID is nonsense (e.g., empty).
	 * @throws UnknownRunException
	 *             If the ID is unknown or not for a SCAPE job.
	 */
	@Nonnull
	private TavernaRun getScapeRun(@Nullable String id)
			throws UnknownRunException {
		if (id == null || id.isEmpty())
			throw new BadInputException("missing/bad job ID");
		TavernaRun r = run(id);
		if (!isScapeRun(r))
			throw new UnknownRunException();
		return r;
	}

	@Nonnull
	private static String serializeXml(@Nonnull Node node)
			throws TransformerException {
		Transformer writer = TransformerFactory.newInstance().newTransformer();
		writer.setOutputProperty(OMIT_XML_DECLARATION, "yes");
		writer.setOutputProperty(STANDALONE, "yes");
		StringWriter sw = new StringWriter();
		writer.transform(new DOMSource(node), new StreamResult(sw));
		return sw.toString();
	}

	@PerfLogged
	public void detectCompletion() {
		for (String id : dao.listNotifiableJobs()) {
			TavernaRun r;
			try {
				r = runStore.getRun(id);
			} catch (UnknownRunException e) {
				continue;
			}
			if (r.getStatus() != Finished)
				continue;
			try {
				ScapeJob j = dao.getJobRecord(id);
				if (j != null)
					notifySuccess(r, j.getPlanId());
			} catch (Exception e) {
				log.warn("failure in notification", e);
			}
			dao.setNotify(id, null);
		}
	}

	private void notifyPlanService(String planId,
			ExecutionStateChange stateChange) {
		URL u;
		try {
			u = fromUri(notifyService).path("/plan-execution-state/{id}")
					.build(planId).toURL();
		} catch (MalformedURLException | IllegalArgumentException
				| UriBuilderException e) {
			log.error("failed to construct notification url", e);
			return;
		}
		try {
			String payload = serializeStateChange(stateChange);

			HttpURLConnection conn = (HttpURLConnection) u.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			if (notifyUser != null && notifyPass != null) {
				String token = notifyUser + ":" + notifyPass;
				token = printBase64Binary(token.getBytes("UTF-8"));
				conn.setRequestProperty("Authorization", token);
			}
			conn.setRequestProperty("Content-Type", "application/xml");

			try (Writer out = new OutputStreamWriter(conn.getOutputStream(),
					"UTF-8")) {
				out.write(payload);
			}
			String response = "";
			try (Reader in = new InputStreamReader(conn.getInputStream())) {
				response = IOUtils.toString(in);
			}
			if (conn.getResponseCode() >= 400 && log.isInfoEnabled())
				log.info(String.format("notification response: %s\n%s",
						conn.getResponseMessage(), response));
		} catch (IOException | DatatypeConfigurationException | JAXBException e) {
			log.warn("failed to do notification to " + u, e);
		}
	}

	/**
	 * Convert a state change description to its serialized XML form. Adds in
	 * the timestamp.
	 * 
	 * @param stateChange
	 *            The requested state change.
	 * @return The XML document as a string.
	 */
	private String serializeStateChange(ExecutionStateChange stateChange)
			throws DatatypeConfigurationException, JAXBException {
		GregorianCalendar c = new GregorianCalendar();
		stateChange.timestamp = DatatypeFactory.newInstance()
				.newXMLGregorianCalendar(c);
		StringWriter sw = new StringWriter();
		context.createMarshaller().marshal(stateChange, sw);
		return sw.toString();
	}

	private String addFileInfo(TavernaRun r, ExecutionStateChange change) {
		if (serviceUri == null)
			return null;
		UriBuilder ub = UriBuilder.fromUri(serviceUri).path(
				"rest/runs/{id}/wd/{name}");
		String report = null;
		try {
			Directory out = fileUtils.getDirectory(r, "out");
			change.output = ub.build(r.getId(), out.getFullName()).toString();
			File reportFile = fileUtils.getFile(r, "out/report");
			report = new String(reportFile.getContents(0,
					(int) reportFile.getSize()));
		} catch (FilesystemAccessException | NoDirectoryEntryException e) {
			// Do nothing in this case
		}
		Iterator<File> prov = support.getProv(r).iterator();
		if (prov.hasNext())
			change.provenance = ub.build(r.getId(), prov.next().getFullName())
					.toString();
		return report;
	}

	private void notifySuccess(TavernaRun r, String planId) {
		ExecutionStateChange change = new ExecutionStateChange();
		try {
			String code = support.getListener(r, "io").getProperty("exitcode");
			String report;
			if (code.equals("0")) {
				change.contents = format("job %s has terminated successfully",
						r.getId());
				change.state = State.Success;
				report = addFileInfo(r, change);
			} else {
				change.contents = format(
						"job %s has terminated with catastrophic errors",
						r.getId());
				change.state = State.Fail;
				report = addFileInfo(r, change);
			}
			if (report != null)
				change.contents += "<p>" + report;
		} catch (NoListenerException e) {
			log.error(
					"no such listener or property when looking for io/exitcode",
					e);
			change.contents = format(
					"job %s has no properly defined exit status?", r.getId());
			change.state = State.Fail;
		}
		notifyPlanService(planId, change);
	}

	/**
	 * How to make a SCAPE job actually start executing.
	 * 
	 * @author Donal Fellows
	 */
	class ScapeShepherd implements Runnable {
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

		ScapeShepherd(@Nonnull String planId, @Nonnull List<Object> objs,
				@Nullable Element schematron, @Nonnull Workflow w,
				@Nonnull UriInfo ui) throws NoCreateException,
				UnknownRunException {
			this.planId = planId;
			this.objs = objs;
			this.schematron = schematron;
			jobId = support.buildWorkflow(w);
			dao.setScapeJob(jobId, planId);
			if (notifyService != null)
				dao.setNotify(jobId, notifyService);
			/* Warning: do not move UriInfo across threads */
			base = ui.getBaseUri();
			run = ScapeExecutor.this.run(jobId);
			inDesc = cb.makeInputDescriptor(run, ui);
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
			if (inputs.contains("CreatorIdentity"))
				initCreatorIdentity();
			run.setGenerateProvenance(true);
			initRepositories();
			initObjects();
			if (schematron != null)
				initSLA(schematron);
			initSecurity();

			Date deadline = new Date();
			deadline.setTime(deadline.getTime() + timeout);
			run.setExpiry(deadline);

			setExecuting();
			if (notifyService != null) {
				String msg = format("Commenced execution using jobID=%s on %s",
						jobId, base);
				notifyPlanService(planId, new ExecutionStateChange(
						State.InProgress, msg));
			}
		}

		private void initCreatorIdentity() throws BadStateChangeException {
			run.makeInput("CreatorIdentity").setValue(planId);
		}

		private void initRepositories() throws BadStateChangeException {
			run.makeInput("SourceRepository").setValue(repository);
			run.makeInput("DestinationRepository").setValue(repository);
			run.makeInput("RepositoryDirectory").setValue(repoDir);
		}

		private void initObjects() throws BadStateChangeException {
			StringBuffer sb = new StringBuffer();
			for (Object object : objs)
				sb.append(object.getUid()).append('\n');
			run.makeInput("digitalObjects").setValue(sb.toString());
		}

		private void initSLA(@Nonnull Element schematron)
				throws BadStateChangeException, TransformerException {
			run.makeInput("SLA").setValue(serializeXml(schematron));
		}

		private void initSecurity() throws InvalidCredentialException,
				IOException, GeneralSecurityException {
			TavernaSecurityContext ctxt = run.getSecurityContext();

			Credential.Password pw = new Credential.Password();
			pw.id = "urn:scape:repository-credential";
			pw.username = notifyUser;
			pw.password = notifyPass;
			pw.serviceURI = URI.create(notifyService);
			ctxt.validateCredential(pw);
			ctxt.addCredential(pw);

			Trust t = new Trust();
			t.loadedCertificates = ccf.getTrustsForURI(pw.serviceURI);
			if (t.loadedCertificates != null)
				ctxt.addTrusted(t);
		}

		private void setExecuting() throws BadStateChangeException {
			while (run.setStatus(Operating) != null)
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					throw new BadStateChangeException(
							"interrupted while trying to start");
				}
		}
	}

	@RolesAllowed(USER)
	@PerfLogged
	@Override
	public String getNotification(String id) throws UnknownRunException {
		getScapeRun(id);
		return Integer.toString(dao.getNotify(id));
	}

	@RolesAllowed(USER)
	@PerfLogged
	@Override
	public String setNotification(String id, String newValue)
			throws UnknownRunException, NoUpdateException {
		getScapeRun(id);
		return Integer.toString(dao.updateNotify(id, newValue));
	}

	@Override
	public Response rootOpt() {
		return opt("POST");
	}

	@Override
	public Response jobOpt(String id) throws UnknownRunException {
		getScapeRun(id);
		return opt("DELETE");
	}

	@Override
	public Response notifyOpt(String id) throws UnknownRunException {
		getScapeRun(id);
		return opt("PUT");
	}
}
