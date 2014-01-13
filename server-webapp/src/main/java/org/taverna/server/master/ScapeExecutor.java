package org.taverna.server.master;

import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.status;
import static javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION;
import static javax.xml.transform.OutputKeys.STANDALONE;
import static org.apache.commons.logging.LogFactory.getLog;
import static org.taverna.server.master.common.Roles.USER;
import static org.taverna.server.master.common.Status.Finished;
import static org.taverna.server.master.common.Status.Operating;
import static org.taverna.server.master.rest.scape.PreservationActionPlan.ExecutablePlan.ExecutablePlanType.Taverna2;
import static org.taverna.server.master.scape.ScapeSplicingEngine.Model.One2OneNoSchema;
import static org.taverna.server.master.scape.ScapeSplicingEngine.Model.One2OneSchema;

import java.io.StringWriter;
import java.util.List;
import java.util.Map.Entry;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.taverna.server.master.common.Status;
import org.taverna.server.master.common.Uri;
import org.taverna.server.master.common.Workflow;
import org.taverna.server.master.exceptions.BadStateChangeException;
import org.taverna.server.master.exceptions.NoCreateException;
import org.taverna.server.master.exceptions.NoDestroyException;
import org.taverna.server.master.exceptions.UnknownRunException;
import org.taverna.server.master.interfaces.Policy;
import org.taverna.server.master.interfaces.RunStore;
import org.taverna.server.master.interfaces.TavernaRun;
import org.taverna.server.master.rest.scape.PreservationActionPlan;
import org.taverna.server.master.rest.scape.PreservationActionPlan.DigitalObject;
import org.taverna.server.master.rest.scape.ScapeExecutionService;
import org.taverna.server.master.scape.ScapeSplicingEngine;
import org.taverna.server.master.scape.ScapeSplicingEngine.Model;
import org.taverna.server.master.utils.InvocationCounter.CallCounted;
import org.taverna.server.master.utils.UsernamePrincipal;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

@Path("/")
public class ScapeExecutor implements ScapeExecutionService {
	final Log log = getLog("Taverna.Server.Webapp.SCAPE");
	@Autowired
	private TavernaServerSupport support;
	@Autowired
	private RunStore runStore;
	@Autowired
	private Policy policy;
	@Autowired
	private ScapeSplicingEngine splicer;

	@Override
	@CallCounted
	@RolesAllowed(USER)
	@NonNull
	public Jobs listJobs(@NonNull UriInfo ui) {
		@NonNull
		Jobs jobs = new Jobs();
		for (@NonNull
		Entry<String, TavernaRun> entry : runStore.listRuns(
				support.getPrincipal(), policy).entrySet())
			if (isScapeRun(entry.getValue()))
				jobs.job.add(new Uri(ui, "{id}", entry.getKey()));
		return jobs;
	}

	@Override
	@CallCounted
	@RolesAllowed(USER)
	@NonNull
	public Response startJob(@NonNull PreservationActionPlan plan,
			@NonNull UriInfo ui) throws NoCreateException {
		@NonNull
		List<DigitalObject> objs = plan.object;
		if (objs.size() == 0)
			return status(400).entity("no objects to act on").build();
		if (plan.executablePlan.type != Taverna2)
			return status(400).entity("that type of plan not supported")
					.build();
		@NonNull
		String id;
		try {
			// TODO Find a better way of picking which model workflow to use
			Model model = plan.qualityLevelDescription.schematronDocument != null ? One2OneSchema
					: One2OneNoSchema;
			id = submitAndStart(splicer.constructWorkflow(
					plan.executablePlan.workflowDocument, model), objs,
					plan.qualityLevelDescription.schematronDocument);
		} catch (NoCreateException e) {
			throw e;
		} catch (Exception e) {
			throw new NoCreateException("failed to construct workflow", e);
		}
		return created(ui.getRequestUriBuilder().path("{id}").build(id))
				.build();
	}

	@Override
	@CallCounted
	@RolesAllowed(USER)
	@NonNull
	public Job getStatus(@NonNull String id, @NonNull UriInfo ui)
			throws UnknownRunException {
		@NonNull
		TavernaRun r = runStore.getRun(support.getPrincipal(), policy, id);
		if (!isScapeRun(r))
			throw new UnknownRunException();
		Job job = new Job();
		job.status = r.getStatus();
		job.serverJob = new Uri(ui.getBaseUriBuilder(), "rest/runs/{id}", id);
		// TODO Consider doing output by a special URL
		if (job.status == Finished)
			job.output = new Uri(ui.getBaseUriBuilder(),
					"rest/runs/{id}/wd/out", id);
		return job;
	}

	@Override
	@CallCounted
	@RolesAllowed(USER)
	@NonNull
	public Response deleteJob(@NonNull String id) throws UnknownRunException,
			NoDestroyException {
		TavernaRun r = runStore.getRun(support.getPrincipal(), policy, id);
		if (!isScapeRun(r))
			throw new UnknownRunException();
		support.unregisterRun(id, null);
		return Response.noContent().build();
	}

	private boolean isScapeRun(@NonNull TavernaRun run) {
		// TODO: Filtering by what API created the job
		return true;
	}

	private String submitAndStart(@NonNull Workflow w,
			@NonNull final List<DigitalObject> objs,
			@Nullable final Element schematron) throws NoCreateException {
		@NonNull
		final String id = support.buildWorkflow(w);
		// final UsernamePrincipal principal = support.getPrincipal();
		// TODO Mark as scape run
		Thread worker = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					initObjects(id, objs);
					if (schematron != null)
						initSLA(id, schematron);
					setExecuting(id);
				} catch (Exception e) {
					log.warn("failed to initialize SCAPE workflow", e);
				}
			}
		});
		worker.setDaemon(true);
		worker.start();
		return id;
	}

	protected void initObjects(@NonNull String id,
			@NonNull List<DigitalObject> objs) throws BadStateChangeException,
			UnknownRunException {
		StringBuffer sb = new StringBuffer();
		for (DigitalObject d_o : objs)
			sb.append(d_o.uid).append('\n');
		runStore.getRun(id).makeInput("objects").setValue(sb.toString());
	}

	protected void initSLA(@NonNull String id, @NonNull Element schematron)
			throws BadStateChangeException, UnknownRunException,
			TransformerException {
		runStore.getRun(id).makeInput("sla").setValue(serializeXml(schematron));
	}

	private String serializeXml(@NonNull Node node) throws TransformerException {
		Transformer writer = TransformerFactory.newInstance().newTransformer();
		writer.setOutputProperty(OMIT_XML_DECLARATION, "yes");
		writer.setOutputProperty(STANDALONE, "yes");
		StringWriter sw = new StringWriter();
		writer.transform(new DOMSource(node), new StreamResult(sw));
		return sw.toString();
	}

	protected void setExecuting(@NonNull String id)
			throws BadStateChangeException, UnknownRunException {
		runStore.getRun(id).setStatus(Operating);
	}
}
