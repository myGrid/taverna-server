package org.taverna.server.master.scape;

import static at.ac.tuwien.ifs.dp.plato.ExecutablePlanType.T_2_FLOW;
import static java.lang.String.format;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.serverError;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.UriBuilder.fromUri;
import static javax.xml.bind.DatatypeConverter.printBase64Binary;
import static javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION;
import static javax.xml.transform.OutputKeys.STANDALONE;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.taverna.server.master.common.Roles.ADMIN;
import static org.taverna.server.master.common.Roles.USER;
import static org.taverna.server.master.common.Status.Finished;
import static org.taverna.server.master.common.Status.Operating;
import static org.taverna.server.master.scape.ScapeSplicingEngine.Model.One2OneNoSchema;
import static org.taverna.server.master.scape.ScapeSplicingEngine.Model.One2OneSchema;
import static org.taverna.server.master.utils.RestUtils.opt;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.CharBuffer;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

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
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.ws.Holder;

import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Required;
import org.taverna.server.master.ContentsDescriptorBuilder;
import org.taverna.server.master.TavernaServerSupport;
import org.taverna.server.master.common.Namespaces;
import org.taverna.server.master.common.Roles;
import org.taverna.server.master.common.Uri;
import org.taverna.server.master.common.Workflow;
import org.taverna.server.master.exceptions.BadStateChangeException;
import org.taverna.server.master.exceptions.NoCreateException;
import org.taverna.server.master.exceptions.NoDestroyException;
import org.taverna.server.master.exceptions.NoUpdateException;
import org.taverna.server.master.exceptions.UnknownRunException;
import org.taverna.server.master.interfaces.Policy;
import org.taverna.server.master.interfaces.RunStore;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.rest.scape.ExecutionStateChange;
import org.taverna.server.master.rest.scape.ExecutionStateChange.State;
import org.taverna.server.master.rest.scape.ScapeExecutionService;
import org.taverna.server.master.scape.ScapeSplicingEngine.Model;
import org.taverna.server.master.utils.CallTimeLogger.PerfLogged;
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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

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
	private ContentsDescriptorBuilder cb;

	public ScapeExecutor() throws JAXBException {
		context = JAXBContext.newInstance(ExecutionStateChange.class);
		log = getLog("Taverna.Server.Webapp.SCAPE");
	}

	@Required
	public void setSupport(TavernaServerSupport support) {
		this.support = support;
	}

	@Required
	public void setRunStore(RunStore runStore) {
		this.runStore = runStore;
	}

	@Required
	public void setPolicy(Policy policy) {
		this.policy = policy;
	}

	@Required
	public void setSplicer(ScapeSplicingEngine splicer) {
		this.splicer = splicer;
	}

	@Required
	public void setContentsDescriptorBuilder(ContentsDescriptorBuilder cb) {
		this.cb = cb;
	}

	@Required
	public void setDao(ScapeJobDAO dao) {
		this.dao = dao;
	}

	public void setNotifyUser(String user) {
		this.notifyUser = user;
	}

	public void setNotifyPassword(String pass) {
		this.notifyPass = pass;
	}

	public void setNotifyService(String serviceURL) {
		this.notifyService = serviceURL;
		if (serviceURL == null) {
			this.notifyUser = null;
			this.notifyPass = null;
		}
	}

	@NonNull
	private Policy policy() {
		Policy p = policy;
		if (p == null)
			throw new WebApplicationException(serverError().entity(
					"failed to configure policy").build());
		return p;
	}

	@NonNull
	private TavernaRun run(@NonNull String id) throws UnknownRunException {
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
	@NonNull
	public Jobs listJobs(@NonNull UriInfo ui) {
		@NonNull
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
	@NonNull
	public Response startJob(@Nullable JobRequest jobRequest,
			@NonNull UriInfo ui) throws NoCreateException {
		if (jobRequest == null)
			throw new BadInputException("what?");
		String planId = jobRequest.planId;
		PreservationActionPlan plan = jobRequest.preservationActionPlan;
		if (planId == null || plan == null)
			throw new BadInputException("what?");

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

		@NonNull
		String id;
		try {
			Model model = pickExecutionModel(plan);
			id = submitAndStart(splicer.constructWorkflow(workflow, model),
					planId, objectList, qld == null ? null : qld.getAny(), ui);
		} catch (NoCreateException e) {
			throw e;
		} catch (Exception e) {
			throw new NoCreateException("failed to construct workflow", e);
		}
		return created(ui.getRequestUriBuilder().path("{id}").build(id))
				.build();
	}

	@NonNull
	private Model pickExecutionModel(PreservationActionPlan plan) {
		// TODO Find a better way of picking which model workflow to use
		QualityLevelDescription qld = plan.getQualityLevelDescription();
		if (qld == null)
			return One2OneNoSchema;
		return qld.getAny() != null ? One2OneSchema : One2OneNoSchema;
	}

	@Override
	@CallCounted
	@PerfLogged
	@RolesAllowed(USER)
	@NonNull
	public Job getStatus(@NonNull String id, @NonNull UriInfo ui)
			throws UnknownRunException {
		if (id == null || id.isEmpty())
			throw new BadInputException("what?");
		@NonNull
		TavernaRun r = run(id);
		if (!isScapeRun(r))
			throw new UnknownRunException();
		Job job = new Job();
		job.status = r.getStatus();
		job.serverJob = new Uri(ui.getBaseUriBuilder(), "rest/runs/{id}", id);
		job.enactedWorkflow = new Uri(ui.getBaseUriBuilder(),
				"rest/runs/{id}/workflow", id);
		// TODO Consider doing output by a special URL
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
	@NonNull
	public Response deleteJob(@NonNull String id) throws UnknownRunException,
			NoDestroyException {
		if (id == null || id.isEmpty())
			throw new BadInputException("what?");
		TavernaRun r = run(id);
		if (!isScapeRun(r))
			throw new UnknownRunException();
		support.unregisterRun(id, null);
		return Response.noContent().build();
	}

	private boolean isScapeRun(@Nullable TavernaRun run) {
		if (run == null)
			return false;
		return dao.isScapeJob(run.getId());
	}

	@NonNull
	private String submitAndStart(@NonNull Workflow w,
			@NonNull final String planId, @NonNull final List<Object> objs,
			@Nullable final Element schematron, @NonNull UriInfo ui)
			throws NoCreateException, UnknownRunException {
		String jobId = support.buildWorkflow(w);
		dao.setScapeJob(jobId, planId);
		if (notifyService != null) {
			dao.setNotify(jobId, notifyService);
		}
		final TavernaRun run = run(jobId);
		final InputDescription inDesc = cb.makeInputDescriptor(run, ui);
		Thread worker = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Set<String> inputs = new HashSet<String>();
					for (InputPort o : inDesc.input)
						inputs.add(o.name);
					if (inputs.contains("planId"))
						initPlanID(run, planId);
					run.setGenerateProvenance(true);
					initObjects(run, objs);
					if (schematron != null)
						initSLA(run, schematron);
					setExecuting(run);
					notifyStart(planId);
				} catch (Exception e) {
					log.warn("failed to initialize SCAPE workflow", e);
				}
			}
		});
		worker.setDaemon(true);
		worker.start();
		return jobId;
	}

	protected void initPlanID(@NonNull TavernaRun run, @NonNull String planId)
			throws BadStateChangeException {
		run.makeInput("planId").setValue(planId);
	}

	protected void initObjects(@NonNull TavernaRun run,
			@NonNull List<Object> objs) throws BadStateChangeException {
		StringBuffer sb = new StringBuffer();
		for (Object object : objs)
			sb.append(object.getUid()).append('\n');
		run.makeInput("objects").setValue(sb.toString());
	}

	protected void initSLA(@NonNull TavernaRun run, @NonNull Element schematron)
			throws BadStateChangeException, TransformerException {
		run.makeInput("sla").setValue(serializeXml(schematron));
	}

	@NonNull
	private String serializeXml(@NonNull Node node) throws TransformerException {
		Transformer writer = TransformerFactory.newInstance().newTransformer();
		writer.setOutputProperty(OMIT_XML_DECLARATION, "yes");
		writer.setOutputProperty(STANDALONE, "yes");
		StringWriter sw = new StringWriter();
		writer.transform(new DOMSource(node), new StreamResult(sw));
		return sw.toString();
	}

	protected void setExecuting(@NonNull TavernaRun run)
			throws BadStateChangeException {
		while (run.setStatus(Operating) != null)
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				throw new BadStateChangeException(
						"interrupted while trying to start");
			}
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
					doNotify(r, j.getPlanId());
			} catch (Exception e) {
				log.warn("failure in notification", e);
			}
			dao.setNotify(id, null);
		}
	}

	private String getNotifyMessage(ExecutionStateChange.State state,
			String content, Holder<String> contentType)
			throws DatatypeConfigurationException, JAXBException {
		ExecutionStateChange message = new ExecutionStateChange();
		GregorianCalendar c = new GregorianCalendar();
		message.timestamp = DatatypeFactory.newInstance()
				.newXMLGregorianCalendar(c);
		message.state = state;
		message.contents = content;
		StringWriter sw = new StringWriter();
		context.createMarshaller().marshal(message, sw);
		contentType.value = "application/xml";
		return sw.toString();
	}

	private String getNotifyPayload(TavernaRun run, String planId,
			ExecutionStateChange.State state, Holder<String> contentType)
			throws DatatypeConfigurationException, JAXBException {
		// FIXME read file contents from run
		String message = run.getName();
		return getNotifyMessage(state, message, contentType);
	}

	// TODO Refactor this so I don't have to repeat the majority of this code
	private void notifyStart(String planId) {
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
			Holder<String> contentType = new Holder<String>();
			String payload = getNotifyMessage(State.InProgress,
					"Commenced execution", contentType);

			HttpURLConnection conn = (HttpURLConnection) u.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			if (notifyUser != null && notifyPass != null) {
				String token = notifyUser + ":" + notifyPass;
				token = printBase64Binary(token.getBytes("UTF-8"));
				conn.setRequestProperty("Authorization", token);
			}
			if (contentType.value != null)
				conn.setRequestProperty("Content-Type", contentType.value);

			new OutputStreamWriter(conn.getOutputStream()).write(payload);
			CharBuffer cb = CharBuffer.allocate(4096);
			new InputStreamReader(conn.getInputStream()).read(cb);
			conn.getInputStream().close();
			log.info("notification response: " + cb);
		} catch (IOException | DatatypeConfigurationException | JAXBException e) {
			log.warn("failed to do notification to " + u, e);
		}
	}

	private void doNotify(TavernaRun r, String planId) {
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
			HttpURLConnection conn = (HttpURLConnection) u.openConnection();
			conn.setDoOutput(true);
			conn.setRequestMethod("POST");
			if (notifyUser != null && notifyPass != null) {
				String token = notifyUser + ":" + notifyPass;
				token = printBase64Binary(token.getBytes("UTF-8"));
				conn.setRequestProperty("Authorization", token);
			}
			Holder<String> contentType = new Holder<String>();
			String payload = getNotifyPayload(r, planId, State.Success,
					contentType);
			if (contentType.value != null)
				conn.setRequestProperty("Content-Type", contentType.value);

			new OutputStreamWriter(conn.getOutputStream()).write(payload);
			CharBuffer cb = CharBuffer.allocate(4096);
			new InputStreamReader(conn.getInputStream()).read(cb);
			conn.getInputStream().close();
			log.info("notification response: " + cb);
		} catch (IOException | DatatypeConfigurationException | JAXBException e) {
			log.warn("failed to do notification to " + u, e);
		}
	}

	@SuppressWarnings("serial")
	public static class BadInputException extends WebApplicationException {
		public BadInputException(String message) {
			super(status(400).entity(message).build());
		}

		public BadInputException(String message, Throwable t) {
			super(t, status(400).entity(message).build());
		}
	}

	@RolesAllowed(USER)
	@PerfLogged
	@Override
	public String getNotification(String id) throws UnknownRunException {
		if (id == null || id.isEmpty())
			throw new BadInputException("what?");
		run(id);
		if (!dao.isScapeJob(id))
			throw new UnknownRunException();
		String addr = dao.getNotify(id);
		return (addr == null ? "" : addr);
	}

	@RolesAllowed(ADMIN)
	@PerfLogged
	@Override
	public String setNotification(String id, String newValue)
			throws UnknownRunException, NoUpdateException {
		if (id == null || id.isEmpty())
			throw new BadInputException("what?");
		run(id);
		if (!dao.isScapeJob(id))
			throw new UnknownRunException();
		String addr = dao.updateNotify(id, newValue);
		return (addr == null ? "" : addr);
	}

	@Override
	public Response rootOpt() {
		return opt("POST");
	}

	@Override
	public Response jobOpt(String id) throws UnknownRunException {
		if (id == null || id.isEmpty())
			throw new BadInputException("what?");
		run(id);
		return opt("DELETE");
	}

	@Override
	public Response notifyOpt(String id) throws UnknownRunException {
		if (id == null || id.isEmpty())
			throw new BadInputException("what?");
		run(id);
		return opt("PUT");
	}
}
